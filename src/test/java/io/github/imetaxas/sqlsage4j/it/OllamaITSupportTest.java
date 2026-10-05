package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OllamaITSupportTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void parseModelNames_readsNameAndModelFields() {
    Set<String> names =
        OllamaITSupport.parseModelNames(
            """
            {"models":[
              {"name":"llama3.1:8b","model":"llama3.1:8b"},
              {"name":"nomic-embed-text:latest"}
            ]}
            """);
    assertThat(names).contains("llama3.1:8b").contains("nomic-embed-text:latest");
  }

  @Test
  void hasModel_matchesExactAndLatestTag() {
    Set<String> installed = Set.of("llama3.1:8b", "nomic-embed-text:latest");
    assertThat(OllamaITSupport.hasModel(installed, "llama3.1:8b")).isTrue();
    assertThat(OllamaITSupport.hasModel(installed, "nomic-embed-text")).isTrue();
    assertThat(OllamaITSupport.hasModel(installed, "llama3.1:70b")).isFalse();
  }

  @Test
  void probe_availableWhenRequiredModelsListed() {
    serveTags(
        200,
        """
        {"models":[
          {"name":"llama3.1:8b"},
          {"name":"nomic-embed-text:latest"}
        ]}
        """);
    OllamaITSupport.ProbeResult result =
        OllamaITSupport.probe(baseUrl, "llama3.1:8b", "nomic-embed-text");
    assertThat(result.available()).isTrue();
  }

  @Test
  void probe_unavailableWhenModelMissing() {
    serveTags(200, "{\"models\":[{\"name\":\"llama3.1:8b\"}]}");
    OllamaITSupport.ProbeResult result =
        OllamaITSupport.probe(baseUrl, "llama3.1:8b", "nomic-embed-text");
    assertThat(result.available()).isFalse();
    assertThat(result.reason()).contains("nomic-embed-text").contains("ollama pull");
  }

  @Test
  void probe_unavailableWhenDaemonDown() {
    OllamaITSupport.ProbeResult result = OllamaITSupport.probe("http://127.0.0.1:1", "llama3.1:8b");
    assertThat(result.available()).isFalse();
    assertThat(result.reason()).contains("not reachable");
  }

  @Test
  void isAvailable_falseWhenDaemonDown() {
    assertThat(OllamaITSupport.isAvailable("http://127.0.0.1:1", "llama3.1:8b")).isFalse();
  }

  private void serveTags(int status, String body) {
    server.createContext(
        "/api/tags",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }
}
