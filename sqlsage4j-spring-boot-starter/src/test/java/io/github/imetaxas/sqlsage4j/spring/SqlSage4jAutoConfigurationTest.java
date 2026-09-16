package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.imetaxas.sqlsage4j.QueryChat;
import io.github.imetaxas.sqlsage4j.SqlSage4j;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SqlSage4jAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SqlSage4jAutoConfiguration.class));

  @Test
  void createsBeansWithMinimalConfig() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=ollama",
            "sqlsage4j.embeddings.base-url=http://localhost:11434")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SqlSage4jProperties.class);
              assertThat(context).hasSingleBean(EmbeddingsProvider.class);
              assertThat(context).hasSingleBean(EmbeddingsStorage.class);
              assertThat(context).hasSingleBean(LLMClient.class);
              assertThat(context).hasSingleBean(SqlSage4j.class);
              assertThat(context).hasSingleBean(QueryChat.class);
            });
  }

  @Test
  void doesNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("sqlsage4j.enabled=false", "sqlsage4j.model-name=gpt-4")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SqlSage4j.class);
              assertThat(context).doesNotHaveBean(QueryChat.class);
              assertThat(context).doesNotHaveBean(LLMClient.class);
            });
  }

  @Test
  void usesOpenAiClientWhenBaseUrlEndsWithV1() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=gpt-4o",
            "sqlsage4j.api-key=sk-test",
            "sqlsage4j.base-url=http://localhost:11434/v1",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=openai",
            "sqlsage4j.embeddings.api-key=sk-test")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LLMClient.class);
              LLMClient client = context.getBean(LLMClient.class);
              assertThat(client.modelName()).isEqualTo("gpt-4o");
            });
  }

  @Test
  void usesOllamaClientWhenBaseUrlIsOllamaPort() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=ollama",
            "sqlsage4j.embeddings.base-url=http://localhost:11434")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LLMClient.class);
              LLMClient client = context.getBean(LLMClient.class);
              assertThat(client.modelName()).isEqualTo("llama3");
            });
  }

  @Test
  void usesInMemoryStorageByDefault() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=ollama",
            "sqlsage4j.embeddings.base-url=http://localhost:11434")
        .run(
            context -> {
              EmbeddingsStorage storage = context.getBean(EmbeddingsStorage.class);
              assertThat(storage)
                  .isInstanceOf(
                      io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage.class);
            });
  }

  @Test
  void usesBm25StorageWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.storage.type=bm25",
            "sqlsage4j.embeddings.provider=noop")
        .run(
            context -> {
              EmbeddingsStorage storage = context.getBean(EmbeddingsStorage.class);
              assertThat(storage)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.storage.BM25Storage.class);
            });
  }

  @Test
  void usesHnswStorageWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.storage.type=hnsw",
            "sqlsage4j.embeddings.provider=ollama",
            "sqlsage4j.embeddings.base-url=http://localhost:11434")
        .run(
            context -> {
              EmbeddingsStorage storage = context.getBean(EmbeddingsStorage.class);
              assertThat(storage)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.storage.HnswEmbeddingsStorage.class);
            });
  }

  @Test
  void usesNoOpEmbeddingsProviderWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              EmbeddingsProvider provider = context.getBean(EmbeddingsProvider.class);
              assertThat(provider)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider.class);
            });
  }

  @Test
  void configuresSqlGuardReadOnlyWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.safety.read-only=true",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              SqlGuard guard = context.getBean(SqlGuard.class);
              assertThat(guard.check("DROP TABLE x").blocked()).isTrue();
              assertThat(guard.check("SELECT 1").blocked()).isFalse();
            });
  }

  @Test
  void configuresSqlGuardAllowAllByDefault() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              SqlGuard guard = context.getBean(SqlGuard.class);
              assertThat(guard.check("DROP TABLE x").blocked()).isFalse();
            });
  }

  @Test
  void backsOffWhenUserProvidesOwnLlmClient() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .withBean(
            LLMClient.class,
            () ->
                new LLMClient() {
                  @Override
                  public String submitPrompt(
                      java.util.List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
                    return "custom";
                  }

                  @Override
                  public String modelName() {
                    return "custom-model";
                  }
                })
        .run(
            context -> {
              assertThat(context).hasSingleBean(LLMClient.class);
              assertThat(context.getBean(LLMClient.class).modelName()).isEqualTo("custom-model");
            });
  }

  @Test
  void backsOffWhenUserProvidesOwnStorage() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop")
        .withBean(EmbeddingsStorage.class, io.github.imetaxas.sqlsage4j.storage.BM25Storage::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingsStorage.class);
              assertThat(context.getBean(EmbeddingsStorage.class))
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.storage.BM25Storage.class);
            });
  }

  @Test
  void backsOffWhenUserProvidesOwnEmbeddingsProvider() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.storage.type=bm25")
        .withBean(
            EmbeddingsProvider.class,
            io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingsProvider.class);
              assertThat(context.getBean(EmbeddingsProvider.class))
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider.class);
            });
  }

  @Test
  void doesNotCreateBeansWithoutModelName() {
    contextRunner
        .withPropertyValues("sqlsage4j.max-tokens=1024")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SqlSage4j.class);
              assertThat(context).doesNotHaveBean(QueryChat.class);
            });
  }
}
