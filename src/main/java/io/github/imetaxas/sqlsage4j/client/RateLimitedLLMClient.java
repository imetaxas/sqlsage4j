package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Decorator that rate-limits LLM calls using a token-bucket algorithm.
 *
 * <p>Prevents exceeding API rate limits by controlling both concurrency and requests-per-second.
 * Callers block until a permit is available.
 *
 * <pre>{@code
 * LLMClient limited = RateLimitedLLMClient.builder(openAiClient)
 *     .maxConcurrent(5)
 *     .requestsPerSecond(10.0)
 *     .build();
 * }</pre>
 */
public final class RateLimitedLLMClient implements LLMClient {

  private final LLMClient delegate;
  private final Semaphore concurrencyPermits;
  private final double requestsPerSecond;
  private long lastRequestNanos;
  private final Object timingLock = new Object();

  private RateLimitedLLMClient(Builder builder) {
    this.delegate = builder.delegate;
    this.concurrencyPermits = new Semaphore(builder.maxConcurrent);
    this.requestsPerSecond = builder.requestsPerSecond;
    this.lastRequestNanos = 0;
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    try {
      concurrencyPermits.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Rate-limited request interrupted while waiting for permit", e);
    }
    try {
      waitForRateLimit();
      return delegate.submitPrompt(messages);
    } finally {
      concurrencyPermits.release();
    }
  }

  @Override
  public String modelName() {
    return delegate.modelName();
  }

  private void waitForRateLimit() {
    if (requestsPerSecond <= 0) return;
    long minIntervalNanos = (long) (1_000_000_000.0 / requestsPerSecond);
    synchronized (timingLock) {
      long now = System.nanoTime();
      long elapsed = now - lastRequestNanos;
      if (elapsed < minIntervalNanos && lastRequestNanos != 0) {
        long sleepNanos = minIntervalNanos - elapsed;
        try {
          Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      lastRequestNanos = System.nanoTime();
    }
  }

  public static Builder builder(LLMClient delegate) {
    return new Builder(delegate);
  }

  public static final class Builder {
    private final LLMClient delegate;
    private int maxConcurrent = 5;
    private double requestsPerSecond = 10.0;

    private Builder(LLMClient delegate) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Maximum number of concurrent LLM calls allowed. Default: 5. */
    public Builder maxConcurrent(int maxConcurrent) {
      if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be >= 1");
      this.maxConcurrent = maxConcurrent;
      return this;
    }

    /** Maximum requests per second. Use 0 or negative to disable rate limiting. Default: 10. */
    public Builder requestsPerSecond(double rps) {
      this.requestsPerSecond = rps;
      return this;
    }

    public RateLimitedLLMClient build() {
      return new RateLimitedLLMClient(this);
    }
  }
}
