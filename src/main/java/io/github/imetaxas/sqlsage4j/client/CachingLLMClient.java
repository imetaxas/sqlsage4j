package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decorator that caches LLM responses keyed by the full message list.
 *
 * <p>Useful for avoiding redundant LLM calls during development, testing, or when the same
 * questions are asked repeatedly. Supports configurable max entries and time-to-live (TTL).
 *
 * <p>Usage:
 *
 * <pre>{@code
 * LLMClient cached = CachingLLMClient.builder(openAiClient)
 *     .maxEntries(200)
 *     .ttl(Duration.ofMinutes(30))
 *     .build();
 * }</pre>
 */
public final class CachingLLMClient implements LLMClient {

  private final LLMClient delegate;
  private final Map<String, CacheEntry> cache;
  private final long ttlMillis;

  private CachingLLMClient(Builder builder) {
    this.delegate = builder.delegate;
    this.ttlMillis = builder.ttl.toMillis();
    int maxEntries = builder.maxEntries;
    this.cache =
        new LinkedHashMap<>(maxEntries, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > maxEntries;
          }
        };
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    String key = computeKey(messages);

    synchronized (cache) {
      CacheEntry entry = cache.get(key);
      if (entry != null && !entry.isExpired(ttlMillis)) {
        return entry.response;
      }
    }

    String response = delegate.submitPrompt(messages);

    synchronized (cache) {
      cache.put(key, new CacheEntry(response, System.currentTimeMillis()));
    }
    return response;
  }

  @Override
  public String modelName() {
    return delegate.modelName();
  }

  /** Clears all cached entries. */
  public void invalidate() {
    synchronized (cache) {
      cache.clear();
    }
  }

  /** Returns current cache size. */
  public int size() {
    synchronized (cache) {
      return cache.size();
    }
  }

  public static Builder builder(LLMClient delegate) {
    return new Builder(delegate);
  }

  private static String computeKey(List<ChatMessage> messages) {
    return messages.stream()
        .map(m -> m.role().name() + ":" + m.content())
        .collect(Collectors.joining("\n---\n"));
  }

  private record CacheEntry(String response, long createdAt) {
    boolean isExpired(long ttlMillis) {
      if (ttlMillis <= 0) return false;
      return System.currentTimeMillis() - createdAt > ttlMillis;
    }
  }

  public static final class Builder {
    private final LLMClient delegate;
    private int maxEntries = 100;
    private Duration ttl = Duration.ZERO;

    private Builder(LLMClient delegate) {
      this.delegate = delegate;
    }

    public Builder maxEntries(int maxEntries) {
      this.maxEntries = maxEntries;
      return this;
    }

    /** Set time-to-live for entries. {@code Duration.ZERO} means no expiration. */
    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    public CachingLLMClient build() {
      return new CachingLLMClient(this);
    }
  }
}
