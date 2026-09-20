package io.github.imetaxas.sqlsage4j;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import javax.annotation.Nullable;

/** Reusable wiring for integration tests — builds the full pipeline from mocks. */
public final class TestHarness {

  public final MockLLMClient llmClient;
  public final MockEmbeddingsProvider embeddingsProvider;
  public final EmbeddingsStorage embeddingsStorage;
  public final TrainingService trainingService;
  public final SqlGenerationPipeline pipeline;
  public final QueryChat queryChat;
  public final LLMChat llmChat;

  public TestHarness(
      String dialect,
      @Nullable String initialPrompt,
      @Nullable DatabaseConnector db,
      EmbeddingsStorage storage) {
    llmClient = new MockLLMClient();
    embeddingsProvider = new MockEmbeddingsProvider();
    embeddingsStorage = storage;
    trainingService = new TrainingService(embeddingsProvider, embeddingsStorage, llmClient);
    pipeline = new SqlGenerationPipeline(llmClient, trainingService, dialect, 14000, initialPrompt);
    queryChat = new QueryChat(llmClient, pipeline, trainingService, db);
    llmChat = new LLMChat(llmClient, trainingService, "You are a helpful data assistant.");
  }

  public TestHarness(
      String dialect, @Nullable String initialPrompt, @Nullable DatabaseConnector db) {
    this(dialect, initialPrompt, db, new InMemoryEmbeddingsStorage());
  }

  public TestHarness(String dialect) {
    this(dialect, null, null, new InMemoryEmbeddingsStorage());
  }

  public TestHarness(String dialect, EmbeddingsStorage storage) {
    this(dialect, null, null, storage);
  }
}
