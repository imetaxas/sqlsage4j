package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RetryingLLMClientTest {

  @Test
  void succeedsOnFirstTry() {
    MockLLMClient mock = new MockLLMClient().withResponse("SELECT 1");
    RetryingLLMClient client = RetryingLLMClient.builder(mock).maxAttempts(3).build();

    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT 1");
    assertThat(mock.callCount()).isEqualTo(1);
  }

  @Test
  void retriesOnTransientFailure() {
    AtomicInteger attempts = new AtomicInteger();
    LLMClient failing =
        new LLMClient() {
          @Override
          public String submitPrompt(List<ChatMessage> messages) {
            if (attempts.incrementAndGet() < 3) {
              throw new RuntimeException("HTTP 429 Too Many Requests");
            }
            return "success";
          }

          @Override
          public String modelName() {
            return "test";
          }
        };

    RetryingLLMClient client =
        RetryingLLMClient.builder(failing)
            .maxAttempts(3)
            .initialDelay(Duration.ofMillis(10))
            .build();

    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("success");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void failsAfterMaxAttempts() {
    LLMClient alwaysFails =
        new LLMClient() {
          @Override
          public String submitPrompt(List<ChatMessage> messages) {
            throw new RuntimeException("HTTP 500 Server Error");
          }

          @Override
          public String modelName() {
            return "test";
          }
        };

    RetryingLLMClient client =
        RetryingLLMClient.builder(alwaysFails)
            .maxAttempts(2)
            .initialDelay(Duration.ofMillis(10))
            .build();

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("failed after 2 attempts");
  }

  @Test
  void nonRetryableExceptionPropagatesImmediately() {
    AtomicInteger attempts = new AtomicInteger();
    LLMClient invalid =
        new LLMClient() {
          @Override
          public String submitPrompt(List<ChatMessage> messages) {
            attempts.incrementAndGet();
            throw new RuntimeException("Invalid API key");
          }

          @Override
          public String modelName() {
            return "test";
          }
        };

    RetryingLLMClient client =
        RetryingLLMClient.builder(invalid)
            .maxAttempts(5)
            .initialDelay(Duration.ofMillis(10))
            .build();

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("failed after 5 attempts")
        .cause()
        .hasMessageContaining("Invalid API key");
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void delegatesModelName() {
    MockLLMClient mock = new MockLLMClient();
    RetryingLLMClient client = RetryingLLMClient.builder(mock).build();

    assertThat(client.modelName()).isEqualTo("mock-model");
  }
}
