package io.github.imetaxas.sqlsage4j.provider;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OllamaEmbeddingsProviderTest {

  private static final Gson GSON = new Gson();
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
  void generateEmbedding_parsesOllamaResponse() {
    JsonArray vec = new JsonArray();
    vec.add(0.1f);
    vec.add(0.2f);
    vec.add(0.3f);
    JsonArray outer = new JsonArray();
    outer.add(vec);
    JsonObject response = new JsonObject();
    response.add("embeddings", outer);

    server.createContext(
        "/api/embed",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
          assertThat(req.get("model").getAsString()).isEqualTo("nomic-embed-text");
          assertThat(req.get("input").getAsString()).isEqualTo("hello world");

          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaEmbeddingsProvider provider = new OllamaEmbeddingsProvider(baseUrl);
    float[] embedding = provider.generateEmbedding("hello world");

    assertThat(embedding).as("embedding vector").hasLength(3);
    assertThat(embedding[0]).isCloseTo(0.1f, 0.001f);
    assertThat(embedding[1]).isCloseTo(0.2f, 0.001f);
    assertThat(embedding[2]).isCloseTo(0.3f, 0.001f);
  }

  @Test
  void generateEmbedding_usesCustomModel() {
    JsonArray vec = new JsonArray();
    vec.add(1.0f);
    JsonArray outer = new JsonArray();
    outer.add(vec);
    JsonObject response = new JsonObject();
    response.add("embeddings", outer);

    server.createContext(
        "/api/embed",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
          assertThat(req.get("model").getAsString()).isEqualTo("mxbai-embed-large");

          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaEmbeddingsProvider provider = new OllamaEmbeddingsProvider(baseUrl, "mxbai-embed-large");
    float[] embedding = provider.generateEmbedding("test");

    assertThat(embedding).hasLength(1);
  }

  @Test
  void generateEmbedding_throwsOnHttpError() {
    server.createContext(
        "/api/embed",
        exchange -> {
          byte[] body = "server error".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaEmbeddingsProvider provider = new OllamaEmbeddingsProvider(baseUrl);

    assertThatThrownBy(() -> provider.generateEmbedding("test"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Ollama embeddings error (HTTP 500)");
  }

  @Test
  void trailingSlashInBaseUrl_isHandled() {
    JsonArray vec = new JsonArray();
    vec.add(0.5f);
    JsonArray outer = new JsonArray();
    outer.add(vec);
    JsonObject response = new JsonObject();
    response.add("embeddings", outer);

    server.createContext(
        "/api/embed",
        exchange -> {
          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaEmbeddingsProvider provider = new OllamaEmbeddingsProvider(baseUrl + "/");
    float[] embedding = provider.generateEmbedding("test");
    assertThat(embedding).hasLength(1);
  }
}
