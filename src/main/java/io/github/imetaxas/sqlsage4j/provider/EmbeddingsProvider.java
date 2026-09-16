package io.github.imetaxas.sqlsage4j.provider;

/**
 * Generates vector embeddings from text.
 *
 * <p>Implement this interface to integrate a custom embedding model. Built-in implementations
 * include {@link OpenAIEmbeddingsProvider} (for {@code text-embedding-3-small} and compatible APIs)
 * and {@link OllamaEmbeddingsProvider} (for local models like {@code nomic-embed-text}).
 *
 * <p>For local setups without an embedding model, use {@link NoOpEmbeddingsProvider} paired with
 * {@link io.github.imetaxas.sqlsage4j.storage.BM25Storage} for keyword-based retrieval.
 */
public interface EmbeddingsProvider {

  /**
   * Generate a vector embedding for the given text.
   *
   * @param text the input text to embed
   * @return a float array representing the text in vector space
   */
  float[] generateEmbedding(String text);
}
