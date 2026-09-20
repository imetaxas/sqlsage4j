package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RateLimitedLLMClientTest {

  @Test
  void submitPrompt_delegatesToUnderlyingClient() {
    MockLLMClient mock = new MockLLMClient().withDefaultResponse("SELECT 1");
    RateLimitedLLMClient limited =
        RateLimitedLLMClient.builder(mock).maxConcurrent(5).requestsPerSecond(100.0).build();

    String result = limited.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT 1");
    assertThat(mock.callCount()).isEqualTo(1);
  }

  @Test
  void modelName_delegatesToUnderlyingClient() {
    MockLLMClient mock = new MockLLMClient();
    RateLimitedLLMClient limited = RateLimitedLLMClient.builder(mock).build();

    assertThat(limited.modelName()).isEqualTo("mock-model");
  }

  @Test
  void maxConcurrent_limitsParallelCalls() throws InterruptedException {
    AtomicInteger concurrent = new AtomicInteger(0);
    AtomicInteger maxConcurrent = new AtomicInteger(0);

    LLMClient slow =
        new LLMClient() {
          @Override
          public String submitPrompt(List<ChatMessage> messages) {
            int current = concurrent.incrementAndGet();
            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
            try {
              Thread.sleep(50);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            concurrent.decrementAndGet();
            return "SELECT 1";
          }

          @Override
          public String modelName() {
            return "slow";
          }
        };

    RateLimitedLLMClient limited =
        RateLimitedLLMClient.builder(slow).maxConcurrent(2).requestsPerSecond(0).build();

    ExecutorService exec = Executors.newFixedThreadPool(5);
    CountDownLatch latch = new CountDownLatch(5);
    for (int i = 0; i < 5; i++) {
      exec.submit(
          () -> {
            limited.submitPrompt(List.of(ChatMessage.user("test")));
            latch.countDown();
          });
    }
    latch.await();
    exec.shutdown();

    assertThat(maxConcurrent.get()).as("never exceeded 2 concurrent").isLessThanOrEqualTo(2);
  }

  @Test
  void requestsPerSecond_throttlesRate() {
    MockLLMClient mock = new MockLLMClient().withDefaultResponse("OK");
    RateLimitedLLMClient limited =
        RateLimitedLLMClient.builder(mock).maxConcurrent(10).requestsPerSecond(20.0).build();

    long start = System.nanoTime();
    for (int i = 0; i < 3; i++) {
      limited.submitPrompt(List.of(ChatMessage.user("q" + i)));
    }
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(mock.callCount()).isEqualTo(3);
    assertThat(elapsedMs).as("rate limiting adds delay").isGreaterThanOrEqualTo(80L);
  }

  @Test
  void builder_maxConcurrentZero_throws() {
    MockLLMClient mock = new MockLLMClient();
    assertThatThrownBy(() -> RateLimitedLLMClient.builder(mock).maxConcurrent(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void builder_nullDelegate_throws() {
    assertThatThrownBy(() -> RateLimitedLLMClient.builder(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void disabledRateLimit_allowsUnthrottledCalls() {
    MockLLMClient mock = new MockLLMClient().withDefaultResponse("OK");
    RateLimitedLLMClient limited =
        RateLimitedLLMClient.builder(mock).maxConcurrent(10).requestsPerSecond(0).build();

    for (int i = 0; i < 10; i++) {
      limited.submitPrompt(List.of(ChatMessage.user("q")));
    }
    assertThat(mock.callCount()).isEqualTo(10);
  }
}
