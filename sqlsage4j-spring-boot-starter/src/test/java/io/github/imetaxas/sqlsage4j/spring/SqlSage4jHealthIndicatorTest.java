package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.LLMClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class SqlSage4jHealthIndicatorTest {

  @Test
  void healthUpWhenClientResponds() {
    LLMClient workingClient =
        new LLMClient() {
          @Override
          public String submitPrompt(
              java.util.List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
            return "pong";
          }

          @Override
          public String modelName() {
            return "test-model";
          }
        };

    SqlSage4jHealthIndicator indicator = new SqlSage4jHealthIndicator(workingClient);
    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("model", "test-model");
  }

  @Test
  void healthDownWhenClientThrows() {
    LLMClient failingClient =
        new LLMClient() {
          @Override
          public String submitPrompt(
              java.util.List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
            throw new RuntimeException("Connection refused");
          }

          @Override
          public String modelName() {
            return "broken-model";
          }
        };

    SqlSage4jHealthIndicator indicator = new SqlSage4jHealthIndicator(failingClient);
    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
    assertThat(health.getDetails().get("error").toString()).contains("Connection refused");
  }

  @Test
  void healthDownWhenClientReturnsEmpty() {
    LLMClient emptyClient =
        new LLMClient() {
          @Override
          public String submitPrompt(
              java.util.List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
            return "";
          }

          @Override
          public String modelName() {
            return "empty-model";
          }
        };

    SqlSage4jHealthIndicator indicator = new SqlSage4jHealthIndicator(emptyClient);
    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  @Test
  void healthDownWhenClientReturnsNull() {
    LLMClient nullClient =
        new LLMClient() {
          @Override
          public String submitPrompt(
              java.util.List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
            return null;
          }

          @Override
          public String modelName() {
            return "null-model";
          }
        };

    SqlSage4jHealthIndicator indicator = new SqlSage4jHealthIndicator(nullClient);
    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }
}
