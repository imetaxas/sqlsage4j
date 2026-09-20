package io.github.imetaxas.sqlsage4j;

import com.google.auto.value.AutoValue;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.provider.DiagramsPlotProvider;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import javax.annotation.Nullable;

/**
 * Configuration for the LLM provider, embeddings, and storage backend.
 *
 * <p>Use the builder to configure model name, API credentials, temperature, and the RAG components:
 *
 * <pre>{@code
 * LLMProviderConfig config = LLMProviderConfig.builder("llama3")
 *     .baseUrl("http://localhost:11434")
 *     .embeddingsProvider(new OllamaEmbeddingsProvider("http://localhost:11434", "nomic-embed-text"))
 *     .embeddingsStorage(new InMemoryEmbeddingsStorage())
 *     .maxTokens(512L)
 *     .temperature(0.0)
 *     .build();
 * }</pre>
 */
@AutoValue
public abstract class LLMProviderConfig {
  LLMProviderConfig() {}

  @Nullable
  public abstract LLMClient llmClient();

  @Nullable
  public abstract DiagramsPlotProvider diagramsPlotProvider();

  @Nullable
  public abstract EmbeddingsStorage embeddingsStorage();

  @Nullable
  public abstract EmbeddingsProvider embeddingsProvider();

  @Nullable
  public abstract Double temperature();

  public abstract String modelName();

  @Nullable
  public abstract String serviceAccount();

  @Nullable
  public abstract String apiKey();

  @Nullable
  public abstract String baseUrl();

  public abstract Long maxTokens();

  public static Builder builder(String modelName) {
    return new AutoValue_LLMProviderConfig.Builder().modelName(modelName);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder llmClient(final LLMClient llmClient);

    public abstract Builder diagramsPlotProvider(final DiagramsPlotProvider diagramsPlotProvider);

    public abstract Builder embeddingsStorage(final EmbeddingsStorage embeddingsStorage);

    public abstract Builder embeddingsProvider(final EmbeddingsProvider embeddingsProvider);

    public abstract Builder modelName(final String modelName);

    public abstract Builder serviceAccount(final String serviceAccount);

    public abstract Builder apiKey(final String apiKey);

    public abstract Builder baseUrl(final String baseUrl);

    public abstract Builder maxTokens(final Long maxTokens);

    public abstract Builder temperature(final Double temperature);

    public abstract LLMProviderConfig build();
  }
}
