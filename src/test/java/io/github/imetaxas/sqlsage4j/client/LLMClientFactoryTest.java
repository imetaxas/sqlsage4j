package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.LLMProviderConfig;
import org.junit.jupiter.api.Test;

final class LLMClientFactoryTest {

  @Test
  void defaultConfig_createsOpenAIClient() {
    LLMProviderConfig config =
        LLMProviderConfig.builder("gpt-4o").apiKey("sk-test").maxTokens(1024L).build();

    LLMClient client = LLMClientFactory.create(config);
    assertThat(client).isInstanceOf(OpenAIClient.class);
    assertThat(client.modelName()).isEqualTo("gpt-4o");
  }

  @Test
  void ollamaCompatUrl_createsOpenAIClient() {
    LLMProviderConfig config =
        LLMProviderConfig.builder("llama3")
            .baseUrl("http://localhost:11434/v1")
            .maxTokens(2048L)
            .build();

    LLMClient client = LLMClientFactory.create(config);
    assertThat(client).isInstanceOf(OpenAIClient.class);
  }

  @Test
  void ollamaNativeUrl_createsOllamaClient() {
    LLMProviderConfig config =
        LLMProviderConfig.builder("llama3")
            .baseUrl("http://localhost:11434")
            .maxTokens(2048L)
            .build();

    LLMClient client = LLMClientFactory.create(config);
    assertThat(client).isInstanceOf(OllamaClient.class);
    assertThat(client.modelName()).isEqualTo("llama3");
  }

  @Test
  void customOpenAICompatBaseUrl_createsOpenAIClient() {
    LLMProviderConfig config =
        LLMProviderConfig.builder("local-model")
            .baseUrl("http://localhost:1234/v1")
            .maxTokens(512L)
            .build();

    LLMClient client = LLMClientFactory.create(config);
    assertThat(client).isInstanceOf(OpenAIClient.class);
  }

  @Test
  void customTemperature_isPassedThrough() {
    LLMProviderConfig config =
        LLMProviderConfig.builder("gpt-4o")
            .apiKey("sk-test")
            .temperature(0.0)
            .maxTokens(1024L)
            .build();

    LLMClient client = LLMClientFactory.create(config);
    assertThat(client).isInstanceOf(OpenAIClient.class);
  }
}
