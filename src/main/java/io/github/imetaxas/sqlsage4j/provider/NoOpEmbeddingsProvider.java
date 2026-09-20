package io.github.imetaxas.sqlsage4j.provider;

/**
 * An embeddings provider that encodes text as character values instead of calling an external
 * model. Designed for use with {@link io.github.imetaxas.sqlsage4j.storage.BM25Storage}, which
 * performs text-based similarity matching and decodes the original text from this encoding.
 *
 * <p>This eliminates the need for an embedding model entirely, avoiding memory contention with the
 * LLM on local Ollama setups.
 *
 * <pre>{@code
 * EmbeddingsProvider provider = new NoOpEmbeddingsProvider();
 * EmbeddingsStorage storage = new BM25Storage();
 * }</pre>
 */
public final class NoOpEmbeddingsProvider implements EmbeddingsProvider {

  @Override
  public float[] generateEmbedding(String text) {
    if (text == null || text.isEmpty()) {
      return new float[] {0f};
    }
    float[] encoded = new float[text.length()];
    for (int i = 0; i < text.length(); i++) {
      encoded[i] = text.charAt(i);
    }
    return encoded;
  }
}
