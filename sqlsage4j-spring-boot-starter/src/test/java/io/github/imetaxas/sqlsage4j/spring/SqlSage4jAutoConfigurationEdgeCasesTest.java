package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SqlSage4jAutoConfigurationEdgeCasesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SqlSage4jAutoConfiguration.class));

  @Test
  void usesLshStorageWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.storage.type=lsh",
            "sqlsage4j.embeddings.provider=noop")
        .run(
            context -> {
              EmbeddingsStorage storage = context.getBean(EmbeddingsStorage.class);
              assertThat(storage)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.storage.LSHEmbeddingsStorage.class);
            });
  }

  @Test
  void usesOpenAiEmbeddingsWithExplicitApiKey() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=gpt-4o",
            "sqlsage4j.api-key=sk-main",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=openai",
            "sqlsage4j.embeddings.api-key=sk-embed-key",
            "sqlsage4j.embeddings.model=text-embedding-ada-002")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingsProvider.class);
              assertThat(context.getBean(EmbeddingsProvider.class))
                  .isInstanceOf(
                      io.github.imetaxas.sqlsage4j.provider.OpenAIEmbeddingsProvider.class);
            });
  }

  @Test
  void openAiEmbeddingsFallsBackToMainApiKey() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=gpt-4o",
            "sqlsage4j.api-key=sk-main",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=openai")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingsProvider.class);
              assertThat(context.getBean(EmbeddingsProvider.class))
                  .isInstanceOf(
                      io.github.imetaxas.sqlsage4j.provider.OpenAIEmbeddingsProvider.class);
            });
  }

  @Test
  void ollamaEmbeddingsFallsBackToMainBaseUrl() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=ollama")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddingsProvider.class);
              assertThat(context.getBean(EmbeddingsProvider.class))
                  .isInstanceOf(
                      io.github.imetaxas.sqlsage4j.provider.OllamaEmbeddingsProvider.class);
            });
  }

  @Test
  void nonOllamaBaseUrlUsesOpenAiClient() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=custom",
            "sqlsage4j.api-key=sk-test",
            "sqlsage4j.base-url=http://my-llm-server:8080",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LLMClient.class);
              LLMClient client = context.getBean(LLMClient.class);
              assertThat(client.modelName()).isEqualTo("custom");
            });
  }

  @Test
  void noBaseUrlUsesOpenAiClient() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=gpt-4o",
            "sqlsage4j.api-key=sk-test",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=noop",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LLMClient.class);
              LLMClient client = context.getBean(LLMClient.class);
              assertThat(client.modelName()).isEqualTo("gpt-4o");
            });
  }

  @Test
  void databaseDsnPropertyBinds() {
    SqlSage4jProperties props = new SqlSage4jProperties();
    props.getDatabase().setDsn("jdbc:sqlite:test.db");
    assertThat(props.getDatabase().getDsn()).isEqualTo("jdbc:sqlite:test.db");
  }

  @Test
  void noneEmbeddingsProviderAlias() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=none",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              EmbeddingsProvider provider = context.getBean(EmbeddingsProvider.class);
              assertThat(provider)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider.class);
            });
  }

  @Test
  void bm25EmbeddingsProviderAlias() {
    contextRunner
        .withPropertyValues(
            "sqlsage4j.model-name=llama3",
            "sqlsage4j.base-url=http://localhost:11434",
            "sqlsage4j.max-tokens=1024",
            "sqlsage4j.embeddings.provider=bm25",
            "sqlsage4j.storage.type=bm25")
        .run(
            context -> {
              EmbeddingsProvider provider = context.getBean(EmbeddingsProvider.class);
              assertThat(provider)
                  .isInstanceOf(io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider.class);
            });
  }
}
