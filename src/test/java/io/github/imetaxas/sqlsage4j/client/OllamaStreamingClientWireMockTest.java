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

final class OllamaStreamingClientWireMockTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void streamPrompt_parsesOllamaNdjsonChunks() {
    String ndjson =
        """
        {"message":{"role":"assistant","content":"SELECT"},"done":false}
        {"message":{"role":"assistant","content":" *"},"done":false}
        {"message":{"role":"assistant","content":" FROM"},"done":false}
        {"message":{"role":"assistant","content":" users"},"done":false}
        {"done":true,"prompt_eval_count":50,"eval_count":10}
        """;

    wm.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(ok(ndjson).withHeader("Content-Type", "application/x-ndjson")));

    OllamaStreamingClient client = new OllamaStreamingClient(wm.baseUrl(), "llama3", 0.0, 4096);

    List<StreamToken> tokens = new ArrayList<>();
    try (Stream<StreamToken> stream =
        client.streamPrompt(List.of(ChatMessage.user("list users")))) {
      stream.forEach(tokens::add);
    }

    assertThat(tokens).hasSize(5);
    assertThat(tokens.get(0).text()).isEqualTo("SELECT");
    assertThat(tokens.get(0).finished()).isFalse();
    assertThat(tokens.get(1).text()).isEqualTo(" *");
    assertThat(tokens.get(3).text()).isEqualTo(" users");
    assertThat(tokens.get(4).finished()).isTrue();
    assertThat(tokens.get(4).promptTokens()).isEqualTo(50L);
    assertThat(tokens.get(4).completionTokens()).isEqualTo(10L);
  }

  @Test
  void streamPrompt_callback_assemblesFullResponse() {
    String ndjson =
        """
        {"message":{"role":"assistant","content":"SELECT 1"},"done":false}
        {"done":true}
        """;

    wm.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(ok(ndjson).withHeader("Content-Type", "application/x-ndjson")));

    OllamaStreamingClient client = new OllamaStreamingClient(wm.baseUrl(), "llama3", 0.0, 4096);

    List<StreamToken> received = new ArrayList<>();
    String full = client.streamPrompt(List.of(ChatMessage.user("test")), received::add);

    assertThat(full).isEqualTo("SELECT 1");
    assertThat(received).hasSize(2);
    assertThat(received.get(1).finished()).isTrue();
  }

  @Test
  void submitPrompt_viaStreaming_returnsFullText() {
    String ndjson =
        """
        {"message":{"role":"assistant","content":"SELECT "},"done":false}
        {"message":{"role":"assistant","content":"name FROM t"},"done":false}
        {"done":true}
        """;

    wm.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(ok(ndjson).withHeader("Content-Type", "application/x-ndjson")));

    OllamaStreamingClient client = new OllamaStreamingClient(wm.baseUrl(), "llama3", 0.0, 4096);

    String result = client.submitPrompt(List.of(ChatMessage.user("names")));

    assertThat(result).isEqualTo("SELECT name FROM t");
  }

  @Test
  void streamPrompt_serverError_throwsRuntimeException() {
    wm.stubFor(post(urlEqualTo("/api/chat")).willReturn(serverError().withBody("model not found")));

    OllamaStreamingClient client = new OllamaStreamingClient(wm.baseUrl(), "llama3", 0.0, 4096);

    assertThatThrownBy(() -> client.streamPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("500");
  }

  @Test
  void modelName_returnsConfiguredModel() {
    OllamaStreamingClient client =
        new OllamaStreamingClient("http://localhost:11434", "codellama", 0.0, 4096);

    assertThat(client.modelName()).isEqualTo("codellama");
  }
}
