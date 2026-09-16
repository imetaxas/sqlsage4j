package io.github.imetaxas.sqlsage4j.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class OpenAIStreamingClientWireMockTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void streamPrompt_parsesSSEChunks() {
    String sse =
        """
        data: {"choices":[{"delta":{"role":"assistant","content":""},"index":0}]}

        data: {"choices":[{"delta":{"content":"SELECT"},"index":0}]}

        data: {"choices":[{"delta":{"content":" *"},"index":0}]}

        data: {"choices":[{"delta":{"content":" FROM users"},"index":0}]}

        data: [DONE]

        """;

    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(ok(sse).withHeader("Content-Type", "text/event-stream")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4", 0.0, 4096, wm.baseUrl() + "/v1");

    List<StreamToken> tokens = new ArrayList<>();
    try (Stream<StreamToken> stream =
        client.streamPrompt(List.of(ChatMessage.user("list users")))) {
      stream.forEach(tokens::add);
    }

    assertThat(tokens).hasSize(4);
    assertThat(tokens.get(0).text()).isEqualTo("SELECT");
    assertThat(tokens.get(0).finished()).isFalse();
    assertThat(tokens.get(1).text()).isEqualTo(" *");
    assertThat(tokens.get(2).text()).isEqualTo(" FROM users");
    assertThat(tokens.get(3).finished()).isTrue();
  }

  @Test
  void streamPrompt_withUsageChunk_returnsTokenCounts() {
    String sse =
        """
        data: {"choices":[{"delta":{"content":"SELECT 1"},"index":0}]}

        data: {"choices":[],"usage":{"prompt_tokens":20,"completion_tokens":5,"total_tokens":25}}

        """;

    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(ok(sse).withHeader("Content-Type", "text/event-stream")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4o", 0.0, 4096, wm.baseUrl() + "/v1");

    List<StreamToken> tokens = new ArrayList<>();
    try (Stream<StreamToken> stream = client.streamPrompt(List.of(ChatMessage.user("test")))) {
      stream.forEach(tokens::add);
    }

    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).text()).isEqualTo("SELECT 1");
    assertThat(tokens.get(1).finished()).isTrue();
    assertThat(tokens.get(1).promptTokens()).isEqualTo(20L);
    assertThat(tokens.get(1).completionTokens()).isEqualTo(5L);
  }

  @Test
  void streamPrompt_callback_assemblesFullResponse() {
    String sse =
        """
        data: {"choices":[{"delta":{"content":"SELECT "},"index":0}]}

        data: {"choices":[{"delta":{"content":"COUNT(*)"},"index":0}]}

        data: [DONE]

        """;

    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(ok(sse).withHeader("Content-Type", "text/event-stream")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4", 0.0, 4096, wm.baseUrl() + "/v1");

    List<StreamToken> received = new ArrayList<>();
    String full = client.streamPrompt(List.of(ChatMessage.user("test")), received::add);

    assertThat(full).isEqualTo("SELECT COUNT(*)");
    assertThat(received).hasSize(3);
  }

  @Test
  void submitPrompt_viaStreaming_returnsFullText() {
    String sse =
        """
        data: {"choices":[{"delta":{"content":"SELECT 1"},"index":0}]}

        data: [DONE]

        """;

    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(ok(sse).withHeader("Content-Type", "text/event-stream")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4", 0.0, 4096, wm.baseUrl() + "/v1");

    String result = client.submitPrompt(List.of(ChatMessage.user("test")));
    assertThat(result).isEqualTo("SELECT 1");
  }

  @Test
  void streamPrompt_serverError_throwsRuntimeException() {
    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(serverError().withBody("rate limited")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4", 0.0, 4096, wm.baseUrl() + "/v1");

    assertThatThrownBy(() -> client.streamPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("500");
  }

  @Test
  void streamPrompt_skipsEmptyDeltaContent() {
    String sse =
        """
        data: {"choices":[{"delta":{"role":"assistant"},"index":0}]}

        data: {"choices":[{"delta":{"content":"SELECT 1"},"index":0}]}

        data: [DONE]

        """;

    wm.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(ok(sse).withHeader("Content-Type", "text/event-stream")));

    OpenAIStreamingClient client =
        new OpenAIStreamingClient("sk-test", "gpt-4", 0.0, 4096, wm.baseUrl() + "/v1");

    List<StreamToken> tokens = new ArrayList<>();
    try (Stream<StreamToken> stream = client.streamPrompt(List.of(ChatMessage.user("test")))) {
      stream.forEach(tokens::add);
    }

    assertThat(tokens).hasSize(2);
    assertThat(tokens.get(0).text()).isEqualTo("SELECT 1");
    assertThat(tokens.get(1).finished()).isTrue();
  }

  @Test
  void modelName_returnsConfiguredModel() {
    OpenAIStreamingClient client = new OpenAIStreamingClient("sk-test", "gpt-4o-mini", 0.0, 4096);
    assertThat(client.modelName()).isEqualTo("gpt-4o-mini");
  }
}
