package io.github.imetaxas.sqlsage4j.spring;

import io.github.imetaxas.sqlsage4j.LLMProviderConfig;
import io.github.imetaxas.sqlsage4j.Prompt;
import io.github.imetaxas.sqlsage4j.QueryChat;
import io.github.imetaxas.sqlsage4j.SqlSage4j;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.client.OllamaClient;
import io.github.imetaxas.sqlsage4j.client.OpenAIClient;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.OllamaEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.OpenAIEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.BM25Storage;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.HnswEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.LSHEmbeddingsStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for sqlsage4j.
 *
 * <p>Activated when {@code sqlsage4j.model-name} is set and {@code sqlsage4j.enabled} is not {@code
 * false}. All beans back off when the user provides their own implementation.
 */
@AutoConfiguration
@EnableConfigurationProperties(SqlSage4jProperties.class)
@ConditionalOnProperty(
    prefix = "sqlsage4j",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SqlSage4jAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public LLMClient sqlSage4jLlmClient(SqlSage4jProperties properties) {
    String baseUrl = properties.getBaseUrl();
    String model = properties.getModelName();
    double temperature = properties.getTemperature();
    int maxTokens = (int) properties.getMaxTokens();

    if (baseUrl != null && !baseUrl.endsWith("/v1") && isOllamaUrl(baseUrl)) {
      return new OllamaClient(baseUrl, model, temperature, maxTokens);
    }
    return new OpenAIClient(properties.getApiKey(), model, temperature, maxTokens, baseUrl);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public EmbeddingsProvider sqlSage4jEmbeddingsProvider(SqlSage4jProperties properties) {
    SqlSage4jProperties.Embeddings emb = properties.getEmbeddings();
    String provider = emb.getProvider();

    return switch (provider.toLowerCase()) {
      case "ollama" -> {
        String url = emb.getBaseUrl() != null ? emb.getBaseUrl() : properties.getBaseUrl();
        yield new OllamaEmbeddingsProvider(url, emb.getModel());
      }
      case "noop", "none", "bm25" -> new NoOpEmbeddingsProvider();
      default -> {
        String apiKey = emb.getApiKey() != null ? emb.getApiKey() : properties.getApiKey();
        String url = emb.getBaseUrl();
        yield new OpenAIEmbeddingsProvider(apiKey, emb.getModel(), url);
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public EmbeddingsStorage sqlSage4jEmbeddingsStorage(SqlSage4jProperties properties) {
    String type = properties.getStorage().getType();

    return switch (type.toLowerCase()) {
      case "bm25" -> new BM25Storage();
      case "hnsw" -> new HnswEmbeddingsStorage();
      case "lsh" -> new LSHEmbeddingsStorage();
      default -> new InMemoryEmbeddingsStorage();
    };
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public SqlGuard sqlSage4jSqlGuard(SqlSage4jProperties properties) {
    return properties.getSafety().isReadOnly() ? SqlGuard.readOnly() : SqlGuard.allowAll();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public SqlSage4j sqlSage4j(
      SqlSage4jProperties properties,
      LLMClient llmClient,
      EmbeddingsProvider embeddingsProvider,
      EmbeddingsStorage embeddingsStorage,
      SqlGuard sqlGuard) {

    LLMProviderConfig config =
        LLMProviderConfig.builder(properties.getModelName())
            .llmClient(llmClient)
            .embeddingsProvider(embeddingsProvider)
            .embeddingsStorage(embeddingsStorage)
            .maxTokens(properties.getMaxTokens())
            .temperature(properties.getTemperature())
            .build();

    Prompt prompt = Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build();

    return SqlSage4j.builder(config).prompt(prompt).sqlGuard(sqlGuard).build();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "sqlsage4j", name = "model-name")
  public QueryChat sqlSage4jQueryChat(SqlSage4j sqlSage4j) {
    return sqlSage4j.queryChat();
  }

  private static boolean isOllamaUrl(String url) {
    return url.contains(":11434");
  }
}
