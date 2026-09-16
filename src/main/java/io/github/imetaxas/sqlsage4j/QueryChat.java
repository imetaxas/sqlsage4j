package io.github.imetaxas.sqlsage4j;

import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.client.StreamToken;
import io.github.imetaxas.sqlsage4j.client.StreamingLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.db.SchemaIntrospector;
import io.github.imetaxas.sqlsage4j.export.ExportType;
import io.github.imetaxas.sqlsage4j.export.Exporter;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.prompt.PromptAssembler;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Full-featured query chat — the primary user-facing class for text-to-SQL. Mirrors the full Vanna
 * ask/run/plot/export/train workflow.
 */
public final class QueryChat {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LLMClient llmClient;
  private final SqlGenerationPipeline pipeline;
  private final TrainingService trainingService;
  private final ConversationHistory history;
  @Nullable private final DatabaseConnector databaseConnector;
  private final SqlGuard sqlGuard;

  public QueryChat(
      LLMClient llmClient,
      SqlGenerationPipeline pipeline,
      TrainingService trainingService,
      @Nullable DatabaseConnector databaseConnector) {
    this(llmClient, pipeline, trainingService, databaseConnector, SqlGuard.ALLOW_ALL);
  }

  public QueryChat(
      LLMClient llmClient,
      SqlGenerationPipeline pipeline,
      TrainingService trainingService,
      @Nullable DatabaseConnector databaseConnector,
      SqlGuard sqlGuard) {
    this.llmClient = llmClient;
    this.pipeline = pipeline;
    this.trainingService = trainingService;
    this.databaseConnector = databaseConnector;
    this.sqlGuard = sqlGuard;
    this.history = new ConversationHistory();
  }

  /** Generate SQL for a question. Handles multi-turn question rewriting. */
  public QueryResponse ask(String question) {
    String lastQuestion = history.getLastQuestion();
    String resolvedQuestion = question;
    if (lastQuestion != null) {
      List<ChatMessage> rewritePrompt = PromptAssembler.rewriteQuestion(lastQuestion, question);
      resolvedQuestion = llmClient.submitPrompt(rewritePrompt);
      logger.info("Rewritten question: {}", resolvedQuestion);
    }

    QueryResponse response = pipeline.generateSql(resolvedQuestion);
    history.set(response.id(), resolvedQuestion, response.sql(), null);
    return response;
  }

  /**
   * Stream the SQL generation token by token. Requires the configured LLMClient to implement {@link
   * StreamingLLMClient}. Returns a Stream that must be closed (use try-with-resources).
   *
   * <pre>{@code
   * try (Stream<StreamToken> tokens = chat.askStreaming("Top customers?")) {
   *     tokens.forEach(t -> System.out.print(t.text()));
   * }
   * }</pre>
   *
   * @throws UnsupportedOperationException if the LLM client doesn't support streaming
   */
  public Stream<StreamToken> askStreaming(String question) {
    StreamingLLMClient streamingClient = requireStreamingClient();
    List<ChatMessage> prompt = pipeline.buildPrompt(question);
    return streamingClient.streamPrompt(prompt);
  }

  /**
   * Stream the SQL generation with a per-token callback. Blocks until the full response is
   * assembled, then returns a {@link QueryResponse} with the complete SQL.
   *
   * <pre>{@code
   * QueryResponse response = chat.askStreaming("Revenue last month?", token -> {
   *     System.out.print(token.text());  // real-time output
   * });
   * DataFrame df = chat.run(response);
   * }</pre>
   *
   * @throws UnsupportedOperationException if the LLM client doesn't support streaming
   */
  public QueryResponse askStreaming(String question, Consumer<StreamToken> onToken) {
    StreamingLLMClient streamingClient = requireStreamingClient();

    String resolvedQuestion = question;
    String lastQuestion = history.getLastQuestion();
    if (lastQuestion != null) {
      List<ChatMessage> rewritePrompt = PromptAssembler.rewriteQuestion(lastQuestion, question);
      resolvedQuestion = streamingClient.submitPrompt(rewritePrompt);
    }

    List<ChatMessage> prompt = pipeline.buildPrompt(resolvedQuestion);
    String fullResponse = streamingClient.streamPrompt(prompt, onToken);
    String sql = pipeline.extractSql(fullResponse);

    QueryResponse response =
        sql != null
            ? QueryResponse.success(
                java.util.UUID.randomUUID().toString(), resolvedQuestion, sql, fullResponse)
            : QueryResponse.error(
                java.util.UUID.randomUUID().toString(),
                resolvedQuestion,
                "Could not extract SQL from response");
    history.set(response.id(), resolvedQuestion, response.sql(), null);
    return response;
  }

  /**
   * Returns true if the configured LLM client supports streaming.
   *
   * @see StreamingLLMClient
   */
  public boolean supportsStreaming() {
    return llmClient instanceof StreamingLLMClient;
  }

  private StreamingLLMClient requireStreamingClient() {
    if (!(llmClient instanceof StreamingLLMClient streaming)) {
      throw new UnsupportedOperationException(
          "Streaming requires a StreamingLLMClient. Use OllamaStreamingClient or "
              + "OpenAIStreamingClient instead of "
              + llmClient.getClass().getSimpleName());
    }
    return streaming;
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Async API
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Asynchronously generate SQL for a question using the common ForkJoinPool.
   *
   * <pre>{@code
   * chat.askAsync("Top customers?")
   *     .thenApply(chat::run)
   *     .thenAccept(df -> System.out.println(df));
   * }</pre>
   */
  public CompletableFuture<QueryResponse> askAsync(String question) {
    return CompletableFuture.supplyAsync(() -> ask(question));
  }

  /** Asynchronously generate SQL using a custom executor. */
  public CompletableFuture<QueryResponse> askAsync(String question, Executor executor) {
    return CompletableFuture.supplyAsync(() -> ask(question), executor);
  }

  /** Asynchronously execute SQL and return the results. */
  public CompletableFuture<DataFrame> runAsync(QueryResponse response) {
    return CompletableFuture.supplyAsync(() -> run(response));
  }

  /** Asynchronously execute SQL using a custom executor. */
  public CompletableFuture<DataFrame> runAsync(QueryResponse response, Executor executor) {
    return CompletableFuture.supplyAsync(() -> run(response), executor);
  }

  /** Execute the generated SQL against the connected database. Applies safety guard first. */
  public DataFrame runSql(String sql) {
    SqlGuard.Result guardResult = sqlGuard.check(sql);
    if (guardResult.blocked()) {
      throw new IllegalArgumentException("SQL blocked by safety guard: " + guardResult.reason());
    }
    if (databaseConnector == null) {
      throw new IllegalStateException(
          "No database connected. Use connectToStorage() in the builder.");
    }
    return databaseConnector.runSql(sql);
  }

  /** Run the SQL from a QueryResponse and cache the result. */
  public DataFrame run(QueryResponse response) {
    if (response.sql() == null) {
      throw new IllegalArgumentException("QueryResponse has no SQL to execute");
    }
    DataFrame df = runSql(response.sql());
    history.set(response.id(), response.question(), response.sql(), df);
    return df;
  }

  /** Generate follow-up questions based on a completed query. */
  public List<String> generateFollowupQuestions(
      String question, String sql, String dfMarkdown, int nQuestions) {
    List<ChatMessage> prompt =
        PromptAssembler.followupQuestions(question, sql, dfMarkdown, nQuestions);
    String response = llmClient.submitPrompt(prompt);
    return Arrays.stream(response.split("\n"))
        .map(q -> q.replaceFirst("^\\d+\\.\\s*", "").trim())
        .filter(q -> !q.isEmpty())
        .toList();
  }

  public List<String> generateFollowupQuestions(String question, String sql, String dfMarkdown) {
    return generateFollowupQuestions(question, sql, dfMarkdown, 5);
  }

  /** Summarize query results in natural language. */
  public String generateSummary(String question, String dfMarkdown) {
    List<ChatMessage> prompt = PromptAssembler.summary(question, dfMarkdown);
    return llmClient.submitPrompt(prompt);
  }

  /** Generate suggested questions from trained data. Mirrors Vanna's generate_questions. */
  public List<String> generateQuestionsFromData() {
    return trainingService.getSimilarQuestionSql("", 10).stream()
        .map(QuestionAnswer::question)
        .toList();
  }

  /** Feed a successful Q&amp;A pair back into training for continuous improvement. */
  public void train(String question, String sql) {
    trainingService.trainQuestionSql(question, sql);
  }

  public String trainDdl(String ddl) {
    return trainingService.trainDdl(ddl);
  }

  public String trainDocumentation(String documentation) {
    return trainingService.trainDocumentation(documentation);
  }

  public String trainSqlAutoQuestion(String sql) {
    return trainingService.trainSqlAutoQuestion(sql);
  }

  /** Export a DataFrame to a file. */
  public void exportAs(DataFrame df, ExportType type, String path) {
    Exporter.export(df, type, path);
  }

  public String getAllTrainedData() {
    return trainingService.getAllTrainingData().toString();
  }

  public boolean deleteTrainedData(String id) {
    return trainingService.removeTrainingData(id);
  }

  public void deleteAllTrainedData() {
    trainingService.removeAllTrainingData();
  }

  public ConversationHistory history() {
    return history;
  }

  /**
   * Auto-discovers the database schema and trains DDLs from a live DataSource. Eliminates the need
   * to manually provide DDL strings.
   *
   * @return the number of tables discovered and trained
   */
  public int trainFromDatabase(DataSource dataSource) {
    SchemaIntrospector introspector = new SchemaIntrospector(dataSource);
    List<String> ddls = introspector.introspect();
    for (String ddl : ddls) {
      trainingService.trainDdl(ddl);
    }
    logger.info("Auto-trained {} DDLs from database", ddls.size());
    return ddls.size();
  }

  /**
   * Auto-discovers schema with a specific schema pattern filter.
   *
   * @param dataSource the JDBC data source
   * @param schemaPattern SQL LIKE pattern for schema name (e.g., "public", "my_schema")
   * @return the number of tables discovered and trained
   */
  public int trainFromDatabase(DataSource dataSource, String schemaPattern) {
    SchemaIntrospector introspector = new SchemaIntrospector(dataSource, schemaPattern);
    List<String> ddls = introspector.introspect();
    for (String ddl : ddls) {
      trainingService.trainDdl(ddl);
    }
    logger.info("Auto-trained {} DDLs from schema '{}'", ddls.size(), schemaPattern);
    return ddls.size();
  }

  /** Returns the configured SQL safety guard. */
  public SqlGuard sqlGuard() {
    return sqlGuard;
  }
}
