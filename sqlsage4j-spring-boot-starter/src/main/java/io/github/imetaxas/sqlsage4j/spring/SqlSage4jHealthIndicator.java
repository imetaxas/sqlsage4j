package io.github.imetaxas.sqlsage4j.spring;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import java.util.List;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator health indicator for the LLM connection. Reports UP when the configured LLM
 * responds to a simple ping prompt, DOWN otherwise.
 */
public class SqlSage4jHealthIndicator implements HealthIndicator {

  private final LLMClient llmClient;

  public SqlSage4jHealthIndicator(LLMClient llmClient) {
    this.llmClient = llmClient;
  }

  @Override
  public Health health() {
    try {
      String response =
          llmClient.submitPrompt(List.of(ChatMessage.user("Reply with exactly: pong")));
      if (response == null || response.isBlank()) {
        return Health.down()
            .withDetail("model", llmClient.modelName())
            .withDetail("error", "Empty response from LLM")
            .build();
      }
      return Health.up().withDetail("model", llmClient.modelName()).build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("model", llmClient.modelName())
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
