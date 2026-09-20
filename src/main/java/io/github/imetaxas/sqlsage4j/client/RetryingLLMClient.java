package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Decorator that retries failed LLM calls with exponential backoff.
 *
 * <p>Wraps any {@link LLMClient} and automatically retries on transient failures (HTTP 429 rate
 * limits, 5xx server errors, timeouts). Non-retryable exceptions propagate immediately.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * LLMClient resilient = RetryingLLMClient.builder(openAiClient)
 *     .maxAttempts(3)
 *     .initialDelay(Duration.ofMillis(500))
 *     .maxDelay(Duration.ofSeconds(10))
 *     .retryOn(e -> e.getMessage().contains("429") || e.getMessage().contains("500"))
 *     .build();
 * }</pre>
 */
public final class RetryingLLMClient implements LLMClient {

  private final LLMClient delegate;
  private final int maxAttempts;
  private final Duration initialDelay;
  private final Duration maxDelay;
  private final Predicate<Exception> retryPredicate;

  private RetryingLLMClient(Builder builder) {
    this.delegate = builder.delegate;
    this.maxAttempts = builder.maxAttempts;
    this.initialDelay = builder.initialDelay;
    this.maxDelay = builder.maxDelay;
    this.retryPredicate = builder.retryPredicate;
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    Exception lastException = null;
    long delayMs = initialDelay.toMillis();

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return delegate.submitPrompt(messages);
      } catch (Exception e) {
        lastException = e;
        if (attempt == maxAttempts || !retryPredicate.test(e)) {
          break;
        }
        sleep(delayMs);
        delayMs = Math.min(delayMs * 2, maxDelay.toMillis());
      }
    }
    throw new RuntimeException("LLM call failed after " + maxAttempts + " attempts", lastException);
  }

  @Override
  public String modelName() {
    return delegate.modelName();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Retry interrupted", e);
    }
  }

  public static Builder builder(LLMClient delegate) {
    return new Builder(delegate);
  }

  public static final class Builder {
    private final LLMClient delegate;
    private int maxAttempts = 3;
    private Duration initialDelay = Duration.ofMillis(500);
    private Duration maxDelay = Duration.ofSeconds(10);
    private Predicate<Exception> retryPredicate = RetryingLLMClient::isRetryableByDefault;

    private Builder(LLMClient delegate) {
      this.delegate = delegate;
    }

    public Builder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    public Builder initialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
      return this;
    }

    public Builder maxDelay(Duration maxDelay) {
      this.maxDelay = maxDelay;
      return this;
    }

    public Builder retryOn(Predicate<Exception> retryPredicate) {
      this.retryPredicate = retryPredicate;
      return this;
    }

    public RetryingLLMClient build() {
      return new RetryingLLMClient(this);
    }
  }

  private static boolean isRetryableByDefault(Exception e) {
    String msg = e.getMessage();
    if (msg == null) return false;
    return msg.contains("429")
        || msg.contains("500")
        || msg.contains("502")
        || msg.contains("503")
        || msg.contains("timeout")
        || msg.contains("Timeout")
        || msg.contains("Connection refused");
  }
}
