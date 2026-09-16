package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.LLMProviderConfig;

public final class LLMClientFactory {

  private LLMClientFactory() {}

  public static LLMClient create(LLMProviderConfig config) {
    double temperature = config.temperature() != null ? config.temperature() : 0.7;
    String baseUrl = config.baseUrl();

    if (baseUrl != null && isOllamaNativeUrl(baseUrl)) {
      return new OllamaClient(baseUrl, config.modelName(), temperature, config.maxTokens());
    }

    String effectiveBaseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
    return new OpenAIClient(
        config.apiKey(), config.modelName(), temperature, config.maxTokens(), effectiveBaseUrl);
  }

  private static boolean isOllamaNativeUrl(String url) {
    return url.contains(":11434") && !url.endsWith("/v1");
  }
}
