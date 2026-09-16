package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SqlSage4jHealthAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  SqlSage4jAutoConfiguration.class, SqlSage4jHealthAutoConfiguration.class));

  @Test
  void createsHealthIndicatorWhenActuatorAndClientPresent() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25",
            "management.health.sqlsage4j.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SqlSage4jHealthIndicator.class);
              assertThat(context).hasSingleBean(HealthIndicator.class);
            });
  }

  @Test
  void doesNotCreateHealthIndicatorWhenDisabled() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25",
            "management.health.sqlsage4j.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(SqlSage4jHealthIndicator.class));
  }

  @Test
  void backsOffWhenUserProvidesOwnHealthIndicator() {
    LLMClient client =
        new LLMClient() {
          @Override
          public String submitPrompt(List<ChatMessage> messages) {
            return "ok";
          }

          @Override
          public String modelName() {
            return "test";
          }
        };

    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25",
            "management.health.sqlsage4j.enabled=true")
        .withBean(SqlSage4jHealthIndicator.class, () -> new SqlSage4jHealthIndicator(client))
        .run(context -> assertThat(context).hasSingleBean(SqlSage4jHealthIndicator.class));
  }
}
