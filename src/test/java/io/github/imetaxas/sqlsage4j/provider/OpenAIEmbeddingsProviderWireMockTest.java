package io.github.imetaxas.sqlsage4j.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class OpenAIEmbeddingsProviderWireMockTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void generateEmbedding_parsesSuccessfulResponse() {
    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                okJson(
                    """
                    {
                      "data": [{
                        "embedding": [0.1, 0.2, 0.3, 0.4, 0.5],
                        "index": 0
                      }],
                      "model": "text-embedding-3-small",
                      "usage": {"prompt_tokens": 5, "total_tokens": 5}
                    }""")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider("test-key", "text-embedding-3-small", wm.baseUrl() + "/v1");

    float[] embedding = provider.generateEmbedding("Hello world");

    assertThat(embedding).hasLength(5);
    assertThat(embedding[0]).isCloseTo(0.1f, 0.001f);
    assertThat(embedding[4]).isCloseTo(0.5f, 0.001f);
  }

  @Test
  void generateEmbedding_sendsCorrectRequest() {
    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                okJson(
                    """
                    {"data":[{"embedding":[0.0],"index":0}],"model":"m"}""")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider("my-key", "text-embedding-3-small", wm.baseUrl() + "/v1");

    provider.generateEmbedding("test input");

    wm.verify(
        postRequestedFor(urlEqualTo("/v1/embeddings"))
            .withHeader("Authorization", equalTo("Bearer my-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("text-embedding-3-small")))
            .withRequestBody(matchingJsonPath("$.input", equalTo("test input"))));
  }

  @Test
  void generateEmbedding_handlesHttpError() {
    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider("bad-key", "text-embedding-3-small", wm.baseUrl() + "/v1");

    assertThatThrownBy(() -> provider.generateEmbedding("test"))
        .isInstanceOf(RuntimeException.class)
        .cause()
        .hasMessageContaining("401");
  }

  @Test
  void generateEmbedding_handlesRateLimit() {
    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody("{\"error\":{\"message\":\"Rate limit exceeded\"}}")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider("key", "text-embedding-3-small", wm.baseUrl() + "/v1");

    assertThatThrownBy(() -> provider.generateEmbedding("test"))
        .isInstanceOf(RuntimeException.class)
        .cause()
        .hasMessageContaining("429");
  }

  @Test
  void generateEmbedding_noApiKey_sendsWithoutAuth() {
    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                okJson(
                    """
                    {"data":[{"embedding":[1.0, 2.0],"index":0}],"model":"m"}""")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider(null, "nomic-embed-text", wm.baseUrl() + "/v1");

    float[] result = provider.generateEmbedding("test");

    assertThat(result).hasLength(2);
    wm.verify(postRequestedFor(urlEqualTo("/v1/embeddings")).withoutHeader("Authorization"));
  }

  @Test
  void generateEmbedding_highDimensionalVector() {
    StringBuilder embJson = new StringBuilder("[");
    for (int i = 0; i < 1536; i++) {
      if (i > 0) embJson.append(",");
      embJson.append("0.").append(i % 10);
    }
    embJson.append("]");

    wm.stubFor(
        post(urlEqualTo("/v1/embeddings"))
            .willReturn(
                okJson(
                    "{\"data\":[{\"embedding\":" + embJson + ",\"index\":0}],\"model\":\"m\"}")));

    OpenAIEmbeddingsProvider provider =
        new OpenAIEmbeddingsProvider("key", "text-embedding-3-small", wm.baseUrl() + "/v1");

    float[] result = provider.generateEmbedding("long text for embedding");
    assertThat(result).hasLength(1536);
  }
}
