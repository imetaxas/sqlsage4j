package io.github.imetaxas.benchmark;

import io.github.imetaxas.sqlsage4j.DDL;
import io.github.imetaxas.sqlsage4j.LLMProviderConfig;
import io.github.imetaxas.sqlsage4j.Prompt;
import io.github.imetaxas.sqlsage4j.QueryChat;
import io.github.imetaxas.sqlsage4j.QueryResponse;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import io.github.imetaxas.sqlsage4j.SampleDocument;
import io.github.imetaxas.sqlsage4j.SqlSage4j;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.OpenAIEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.BM25Storage;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteDataSource;

public final class SqlSage4jHarness implements FrameworkHarness {

  private static final Logger logger = LogManager.getLogger(SqlSage4jHarness.class);
  private QueryChat queryChat;

  @Override
  public String name() {
    return "sqlsage4j";
  }

  @Override
  public void initialize(BenchmarkConfig config, TrainingData trainingData) {
    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + config.databasePath());

    boolean isOllama = config.baseUrl() != null && config.baseUrl().contains(":11434")
        && !config.baseUrl().endsWith("/v1");

    EmbeddingsProvider embeddingsProvider;
    EmbeddingsStorage embeddingsStorage;

    if (isOllama) {
      logger.info("Using BM25 retrieval (no embedding model needed for local Ollama)");
      embeddingsProvider = new NoOpEmbeddingsProvider();
      embeddingsStorage = new BM25Storage();
    } else {
      String embeddingsBaseUrl =
          config.baseUrl() != null ? config.baseUrl() : "https://api.openai.com/v1";
      embeddingsProvider =
          new OpenAIEmbeddingsProvider(config.apiKey(), "text-embedding-3-small", embeddingsBaseUrl);
      embeddingsStorage = new io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage();
    }

    LLMProviderConfig.Builder llmConfigBuilder =
        LLMProviderConfig.builder(config.modelName())
            .embeddingsProvider(embeddingsProvider)
            .embeddingsStorage(embeddingsStorage)
            .maxTokens(config.maxTokens())
            .temperature(config.temperature());

    if (config.apiKey() != null) {
      llmConfigBuilder.apiKey(config.apiKey());
    }
    if (config.baseUrl() != null) {
      llmConfigBuilder.baseUrl(config.baseUrl());
    }

    List<DDL> ddls =
        trainingData.ddls().stream().map(DDL::new).toList();
    List<QuestionAnswer> qas =
        trainingData.questionAnswers().stream()
            .map(qa -> new QuestionAnswer(qa.question(), qa.sql()))
            .toList();
    List<SampleDocument> docs =
        trainingData.documentation().stream().map(SampleDocument::new).toList();

    queryChat =
        SqlSage4j.builder(llmConfigBuilder.build())
            .databaseConnector(new SQLiteConnector(ds))
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .ddls(ddls)
                    .sampleQuestionsAnswers(qas)
                    .sampleDocuments(docs)
                    .build())
            .build()
            .queryChat();

    logger.info(
        "sqlsage4j initialized: model={}, retrieval={}, training={} DDLs, {} Q&As, {} docs",
        config.modelName(),
        isOllama ? "BM25" : "vector",
        trainingData.ddls().size(),
        trainingData.questionAnswers().size(),
        trainingData.documentation().size());
  }

  @Override
  public HarnessResponse ask(String question) {
    queryChat.history().clear();
    long start = System.currentTimeMillis();
    try {
      QueryResponse response = queryChat.ask(question);
      long elapsed = System.currentTimeMillis() - start;
      if (response.sql() != null) {
        return HarnessResponse.success(response.sql(), elapsed);
      }
      return HarnessResponse.failure(elapsed, response.error());
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - start;
      return HarnessResponse.failure(elapsed, e.getMessage());
    }
  }

  @Override
  public void close() {}
}
