package io.github.imetaxas.sqlsage4j.provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decorator that caches embedding results by text content, avoiding redundant API calls for
 * repeated or identical texts. Uses an LRU eviction policy.
 *
 * <pre>{@code
 * EmbeddingsProvider base = new OllamaEmbeddingsProvider("http://localhost:11434");
 * EmbeddingsProvider cached = new CachingEmbeddingsProvider(base, 256);
 * }</pre>
 */
public final class CachingEmbeddingsProvider implements EmbeddingsProvider {

  private static final int DEFAULT_MAX_SIZE = 512;

  private final EmbeddingsProvider delegate;
  private final Map<String, float[]> cache;

  public CachingEmbeddingsProvider(EmbeddingsProvider delegate) {
    this(delegate, DEFAULT_MAX_SIZE);
  }

  public CachingEmbeddingsProvider(EmbeddingsProvider delegate, int maxSize) {
    this.delegate = delegate;
    this.cache =
        new LinkedHashMap<>(maxSize, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > maxSize;
          }
        };
  }

  @Override
  public float[] generateEmbedding(String text) {
    return cache.computeIfAbsent(text, delegate::generateEmbedding);
  }
}
