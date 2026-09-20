package io.github.imetaxas.sqlsage4j.training;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.DDL;
import io.github.imetaxas.sqlsage4j.Document;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.prompt.PromptAssembler;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.SearchResult;
import io.github.imetaxas.sqlsage4j.storage.TrainingRecord;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TrainingService {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int DEFAULT_N_RESULTS = 10;

  private final EmbeddingsProvider embeddingsProvider;
  private final EmbeddingsStorage embeddingsStorage;
  @Nullable private final LLMClient llmClient;

  public TrainingService(
      EmbeddingsProvider embeddingsProvider,
      EmbeddingsStorage embeddingsStorage,
      @Nullable LLMClient llmClient) {
    this.embeddingsProvider = embeddingsProvider;
    this.embeddingsStorage = embeddingsStorage;
    this.llmClient = llmClient;
  }

  public String trainDdl(String ddl) {
    logger.info("Training DDL ({} chars)", ddl.length());
    return storeWithEmbedding(TrainingDataType.DDL, ddl, Map.of());
  }

  public String trainQuestionSql(String question, String sql) {
    logger.info("Training Q&A: {}", question);
    return storeWithEmbedding(
        TrainingDataType.SQL_QA, question + "\n" + sql, Map.of("question", question, "sql", sql));
  }

  public String trainDocumentation(String documentation) {
    logger.info("Training documentation ({} chars)", documentation.length());
    return storeWithEmbedding(TrainingDataType.DOCUMENTATION, documentation, Map.of());
  }

  public String train(Document document) {
    if (document instanceof DDL ddl) {
      return trainDdl(ddl.ddl());
    } else if (document instanceof QuestionAnswer qa) {
      return trainQuestionSql(qa.question(), qa.sql());
    } else {
      return trainDocumentation(document.content());
    }
  }

  /**
   * Train SQL without a question — auto-generate the question using the LLM (mirrors Vanna's
   * generate_question).
   */
  public String trainSqlAutoQuestion(String sql) {
    if (llmClient == null) {
      throw new IllegalStateException("LLM client required for auto-question generation");
    }
    List<ChatMessage> prompt = PromptAssembler.reverseQuestion(sql);
    String generatedQuestion = llmClient.submitPrompt(prompt);
    logger.info("Auto-generated question for SQL: {}", generatedQuestion);
    return trainQuestionSql(generatedQuestion, sql);
  }

  /**
   * Retrieves all context (similar Q&amp;A, related DDL, related docs) for a question using a
   * single embedding computation. This avoids making 3 separate embedding API calls for the same
   * text.
   */
  public RetrievedContext retrieveAllContext(String question) {
    return retrieveAllContext(question, DEFAULT_N_RESULTS);
  }

  public RetrievedContext retrieveAllContext(String question, int nResults) {
    float[] embedding = embeddingsProvider.generateEmbedding(question);

    List<SearchResult> qaResults =
        embeddingsStorage.search(TrainingDataType.SQL_QA.collectionName(), embedding, nResults);
    double topScore =
        qaResults.isEmpty()
            ? 0.0
            : qaResults.stream().mapToDouble(SearchResult::score).max().orElse(0.0);

    List<QuestionAnswer> similarQA =
        qaResults.stream()
            .filter(r -> r.record().metadata().containsKey("question"))
            .map(
                r ->
                    new QuestionAnswer(
                        r.record().metadata().get("question"), r.record().metadata().get("sql")))
            .collect(Collectors.toList());

    List<String> relatedDdl = searchDdl(embedding, nResults);
    List<String> relatedDocs = searchDocumentation(embedding, nResults);

    return new RetrievedContext(similarQA, relatedDdl, relatedDocs, topScore);
  }

  /** Mirrors Vanna's get_similar_question_sql. */
  public List<QuestionAnswer> getSimilarQuestionSql(String question) {
    return getSimilarQuestionSql(question, DEFAULT_N_RESULTS);
  }

  public List<QuestionAnswer> getSimilarQuestionSql(String question, int nResults) {
    float[] embedding = embeddingsProvider.generateEmbedding(question);
    return searchQuestionSql(embedding, nResults);
  }

  /** Mirrors Vanna's get_related_ddl. */
  public List<String> getRelatedDdl(String question) {
    return getRelatedDdl(question, DEFAULT_N_RESULTS);
  }

  public List<String> getRelatedDdl(String question, int nResults) {
    float[] embedding = embeddingsProvider.generateEmbedding(question);
    return searchDdl(embedding, nResults);
  }

  /** Mirrors Vanna's get_related_documentation. */
  public List<String> getRelatedDocumentation(String question) {
    return getRelatedDocumentation(question, DEFAULT_N_RESULTS);
  }

  public List<String> getRelatedDocumentation(String question, int nResults) {
    float[] embedding = embeddingsProvider.generateEmbedding(question);
    return searchDocumentation(embedding, nResults);
  }

  /** Returns the total number of Q&amp;A pairs in the store (used for small-dataset detection). */
  public int totalQuestionSqlCount() {
    return embeddingsStorage.getAll(TrainingDataType.SQL_QA.collectionName()).size();
  }

  /** Returns all Q&amp;A pairs (bypassing similarity search). */
  public List<QuestionAnswer> getAllQuestionSql() {
    return embeddingsStorage.getAll(TrainingDataType.SQL_QA.collectionName()).stream()
        .filter(r -> r.metadata().containsKey("question"))
        .map(r -> new QuestionAnswer(r.metadata().get("question"), r.metadata().get("sql")))
        .collect(Collectors.toList());
  }

  public List<TrainingRecord> getAllTrainingData() {
    List<TrainingRecord> all = new java.util.ArrayList<>();
    for (TrainingDataType type : TrainingDataType.values()) {
      all.addAll(embeddingsStorage.getAll(type.collectionName()));
    }
    return all;
  }

  /** Returns all trained DDL content strings. */
  public List<String> getAllDdl() {
    return embeddingsStorage.getAll(TrainingDataType.DDL.collectionName()).stream()
        .map(TrainingRecord::content)
        .collect(Collectors.toList());
  }

  public boolean removeTrainingData(String id) {
    return embeddingsStorage.remove(id);
  }

  public void removeAllTrainingData() {
    for (TrainingDataType type : TrainingDataType.values()) {
      embeddingsStorage.removeAll(type.collectionName());
    }
  }

  private List<QuestionAnswer> searchQuestionSql(float[] embedding, int nResults) {
    List<SearchResult> results =
        embeddingsStorage.search(TrainingDataType.SQL_QA.collectionName(), embedding, nResults);
    return results.stream()
        .filter(r -> r.record().metadata().containsKey("question"))
        .map(
            r ->
                new QuestionAnswer(
                    r.record().metadata().get("question"), r.record().metadata().get("sql")))
        .collect(Collectors.toList());
  }

  private List<String> searchDdl(float[] embedding, int nResults) {
    List<SearchResult> results =
        embeddingsStorage.search(TrainingDataType.DDL.collectionName(), embedding, nResults);
    return results.stream().map(r -> r.record().content()).collect(Collectors.toList());
  }

  private List<String> searchDocumentation(float[] embedding, int nResults) {
    List<SearchResult> results =
        embeddingsStorage.search(
            TrainingDataType.DOCUMENTATION.collectionName(), embedding, nResults);
    return results.stream().map(r -> r.record().content()).collect(Collectors.toList());
  }

  private String storeWithEmbedding(
      TrainingDataType type, String content, Map<String, String> metadata) {
    String id = UUID.randomUUID().toString();
    float[] embedding = embeddingsProvider.generateEmbedding(content);
    TrainingRecord record = new TrainingRecord(id, type, content, embedding, metadata);
    embeddingsStorage.store(type.collectionName(), record);
    return id;
  }

  public record RetrievedContext(
      List<QuestionAnswer> similarQA,
      List<String> relatedDdl,
      List<String> relatedDocs,
      double topSimilarityScore) {}
}
