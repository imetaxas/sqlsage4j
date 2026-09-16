package io.github.imetaxas.sqlsage4j.pipeline;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.QueryResponse;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.prompt.SqlPromptBuilder;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import io.github.imetaxas.sqlsage4j.training.TrainingService.RetrievedContext;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The core RAG pipeline: retrieve context → assemble prompt → call LLM → extract SQL → validate →
 * optionally retry. Mirrors Vanna's generate_sql() with an added self-correction step.
 *
 * <p>For small training datasets (below {@link #DETERMINISTIC_QA_THRESHOLD}), Q&amp;A pairs are
 * included in a fixed alphabetical order to enable LLM prompt prefix caching. For larger datasets,
 * RAG-selected top-N pairs are used.
 */
public final class SqlGenerationPipeline {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int MAX_RETRIES = 1;
  private static final int MAX_FEW_SHOT_EXAMPLES = 5;

  /**
   * Below this threshold, all Q&A pairs are included in deterministic order (enabling prompt prefix
   * caching). Above it, RAG selects the top-N most relevant.
   */
  static final int DETERMINISTIC_QA_THRESHOLD = 20;

  private final LLMClient llmClient;
  private final TrainingService trainingService;
  private final String dialect;
  private final int maxTokens;
  @Nullable private final String initialPrompt;
  @Nullable private final DatabaseConnector databaseConnector;
  private final PipelineListener listener;

  public SqlGenerationPipeline(
      LLMClient llmClient,
      TrainingService trainingService,
      String dialect,
      int maxTokens,
      @Nullable String initialPrompt,
      @Nullable DatabaseConnector databaseConnector,
      PipelineListener listener) {
    this.llmClient = llmClient;
    this.trainingService = trainingService;
    this.dialect = dialect;
    this.maxTokens = maxTokens;
    this.initialPrompt = initialPrompt;
    this.databaseConnector = databaseConnector;
    this.listener = listener;
  }

  public SqlGenerationPipeline(
      LLMClient llmClient,
      TrainingService trainingService,
      String dialect,
      int maxTokens,
      @Nullable String initialPrompt,
      @Nullable DatabaseConnector databaseConnector) {
    this(
        llmClient,
        trainingService,
        dialect,
        maxTokens,
        initialPrompt,
        databaseConnector,
        PipelineListener.NOOP);
  }

  public SqlGenerationPipeline(
      LLMClient llmClient,
      TrainingService trainingService,
      String dialect,
      int maxTokens,
      @Nullable String initialPrompt) {
    this(
        llmClient, trainingService, dialect, maxTokens, initialPrompt, null, PipelineListener.NOOP);
  }

  public QueryResponse generateSql(String question) {
    String id = UUID.randomUUID().toString();
    long startTime = System.currentTimeMillis();
    try {
      logger.info("Generating SQL for question: {}", question);

      RetrievedContext ctx = trainingService.retrieveAllContext(question);
      List<QuestionAnswer> qaForPrompt = selectQA(ctx.similarQA());

      listener.onContextRetrieved(
          qaForPrompt.size(), ctx.relatedDdl().size(), ctx.relatedDocs().size());

      logger.debug(
          "Retrieved {} Q&A pairs (using {}), {} DDLs, {} docs",
          ctx.similarQA().size(),
          qaForPrompt.size(),
          ctx.relatedDdl().size(),
          ctx.relatedDocs().size());

      List<ChatMessage> prompt =
          SqlPromptBuilder.build(
              initialPrompt,
              question,
              qaForPrompt,
              ctx.relatedDdl(),
              addStaticContext(ctx.relatedDocs()),
              dialect,
              maxTokens);

      int estimatedTokens = prompt.stream().mapToInt(m -> m.content().length() / 4).sum();
      listener.onPromptAssembled(prompt, estimatedTokens);

      long llmStart = System.currentTimeMillis();
      String llmResponse = llmClient.submitPrompt(prompt);
      listener.onLLMResponse(llmResponse, System.currentTimeMillis() - llmStart);
      logger.debug("LLM response: {}", llmResponse);

      String sql = SqlExtractor.extract(llmResponse);
      if (sql != null) {
        listener.onSqlExtracted(sql);
      }

      boolean validated = false;
      if (databaseConnector != null && sql != null && looksLikeSql(sql)) {
        sql = validateAndRetry(sql, prompt);
        validated = databaseConnector.isValidSql(sql);
      }

      double confidence = computeConfidence(ctx.topSimilarityScore(), validated, sql);
      listener.onPipelineComplete(question, true, System.currentTimeMillis() - startTime);
      return QueryResponse.success(id, question, sql, llmResponse, confidence);
    } catch (Exception e) {
      logger.error("SQL generation failed for question: {}", question, e);
      listener.onPipelineComplete(question, false, System.currentTimeMillis() - startTime);
      return QueryResponse.error(id, question, e.getMessage());
    }
  }

  /**
   * For small datasets, return ALL Q&A in deterministic alphabetical order so the prompt prefix is
   * identical across questions (enabling Ollama KV cache reuse). For larger datasets, use
   * RAG-selected top-N.
   */
  private List<QuestionAnswer> selectQA(List<QuestionAnswer> ragResults) {
    int totalQA = trainingService.totalQuestionSqlCount();

    if (totalQA <= DETERMINISTIC_QA_THRESHOLD) {
      List<QuestionAnswer> all = trainingService.getAllQuestionSql();
      List<QuestionAnswer> sorted = new ArrayList<>(all);
      sorted.sort(Comparator.comparing(QuestionAnswer::question));
      return sorted;
    }

    return ragResults.size() > MAX_FEW_SHOT_EXAMPLES
        ? ragResults.subList(0, MAX_FEW_SHOT_EXAMPLES)
        : ragResults;
  }

  /**
   * Build the full prompt for a question without executing it. Used by streaming to separate prompt
   * construction from LLM invocation.
   */
  public List<ChatMessage> buildPrompt(String question) {
    RetrievedContext ctx = trainingService.retrieveAllContext(question);
    List<QuestionAnswer> qaForPrompt = selectQA(ctx.similarQA());
    return SqlPromptBuilder.build(
        initialPrompt,
        question,
        qaForPrompt,
        ctx.relatedDdl(),
        addStaticContext(ctx.relatedDocs()),
        dialect,
        maxTokens);
  }

  /**
   * Extract SQL from an LLM response. Delegates to {@link SqlExtractor}. Returns null if no SQL
   * could be extracted.
   */
  @Nullable
  public String extractSql(String llmResponse) {
    return SqlExtractor.extract(llmResponse);
  }

  private String validateAndRetry(String sql, List<ChatMessage> originalPrompt) {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      if (databaseConnector.isValidSql(sql)) {
        return sql;
      }

      String validationError = getValidationError(sql);
      if (validationError == null) {
        return sql;
      }

      logger.info("SQL validation failed (attempt {}): {}", attempt + 1, validationError);
      listener.onValidationRetry(sql, validationError, attempt + 1);

      List<ChatMessage> retryPrompt = new ArrayList<>(originalPrompt);
      retryPrompt.add(ChatMessage.assistant(sql));
      retryPrompt.add(
          ChatMessage.user(
              "The SQL above has an error: "
                  + validationError
                  + "\nPlease fix the SQL to be valid "
                  + dialect
                  + " and return only the corrected query."));

      try {
        String retryResponse = llmClient.submitPrompt(retryPrompt);
        String retrySql = SqlExtractor.extract(retryResponse);
        if (retrySql != null && !retrySql.isBlank()) {
          logger.info("Self-corrected SQL: {}", retrySql);
          sql = retrySql;
        }
      } catch (Exception e) {
        logger.warn("Retry failed: {}", e.getMessage());
        break;
      }
    }
    return sql;
  }

  @Nullable
  private String getValidationError(String sql) {
    try {
      databaseConnector.runSql("EXPLAIN " + sql);
      return null;
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  private static boolean looksLikeSql(String text) {
    String upper = text.trim().toUpperCase();
    return upper.startsWith("SELECT")
        || upper.startsWith("WITH")
        || upper.startsWith("INSERT")
        || upper.startsWith("UPDATE")
        || upper.startsWith("DELETE");
  }

  /**
   * Computes a confidence score (0.0–1.0) from retrieval similarity and SQL validation signals.
   *
   * <ul>
   *   <li>60% weight: retrieval similarity (clamped to [0, 1])
   *   <li>20% weight: SQL validation passed
   *   <li>20% weight: response looks like SQL (starts with SELECT/WITH)
   * </ul>
   */
  private double computeConfidence(double topSimilarityScore, boolean validated, String sql) {
    double retrievalScore = Math.max(0.0, Math.min(1.0, topSimilarityScore));
    double validationBoost = validated ? 1.0 : 0.0;
    double structureBoost = (sql != null && looksLikeSql(sql)) ? 1.0 : 0.0;
    return 0.6 * retrievalScore + 0.2 * validationBoost + 0.2 * structureBoost;
  }

  private List<String> addStaticContext(List<String> docs) {
    return new ArrayList<>(docs);
  }
}
