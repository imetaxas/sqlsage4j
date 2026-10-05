package io.github.imetaxas.sqlsage4j.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class OpenAIClientWireMockTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void submitPrompt_parsesSuccessfulResponse() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(
                okJson(
                    """
                    {
                      "choices": [{
                        "message": {"role": "assistant", "content": "SELECT COUNT(*) FROM users;"}
                      }]
                    }""")));

    OpenAIClient client = new OpenAIClient("test-key", "gpt-4", 0.0, 1024, wm.baseUrl() + "/v1");
    String result = client.submitPrompt(List.of(ChatMessage.user("How many users?")));

    assertThat(result).isEqualTo("SELECT COUNT(*) FROM users;");
  }

  @Test
  void submitPrompt_sendsCorrectRequestBody() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(
                okJson(
                    """
                    {"choices":[{"message":{"content":"ok"}}]}""")));

    OpenAIClient client = new OpenAIClient("my-api-key", "gpt-4o", 0.5, 2048, wm.baseUrl() + "/v1");
    client.submitPrompt(
        List.of(ChatMessage.system("You are an expert"), ChatMessage.user("test question")));

    wm.verify(
        postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer my-api-key"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content", equalTo("You are an expert")))
            .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
            .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("2048"))));
  }

  @Test
  void submitPrompt_includesStopSequences() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(
                okJson(
                    """
                    {"choices":[{"message":{"content":"SELECT 1"}}]}""")));

    OpenAIClient client = new OpenAIClient("key", "gpt-4", 0.0, 1024, wm.baseUrl() + "/v1");
    client.submitPrompt(List.of(ChatMessage.user("test")));

    wm.verify(
        postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matchingJsonPath("$.stop")));
  }

  @Test
  void submitPrompt_handlesHttpError() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded")));

    OpenAIClient client = new OpenAIClient("key", "gpt-4", 0.0, 1024, wm.baseUrl() + "/v1");

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .cause()
        .hasMessageContaining("429");
  }

  @Test
  void submitPrompt_handlesServerError() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    OpenAIClient client = new OpenAIClient("key", "gpt-4", 0.0, 1024, wm.baseUrl() + "/v1");

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .cause()
        .hasMessageContaining("500");
  }

  @Test
  void submitPrompt_noApiKey_sendsWithoutAuthHeader() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(
                okJson(
                    """
                    {"choices":[{"message":{"content":"result"}}]}""")));

    OpenAIClient client = new OpenAIClient(null, "llama3", 0.0, 1024, wm.baseUrl() + "/v1");
    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("result");
    wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions")).withoutHeader("Authorization"));
  }

  @Test
  void modelName_returnsConfiguredModel() {
    OpenAIClient client = new OpenAIClient("key", "gpt-4o-mini", 0.0, 1024, wm.baseUrl() + "/v1");
    assertThat(client.modelName()).isEqualTo("gpt-4o-mini");
  }
}
