package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;
import static io.github.imetaxas.realitycheck.json.JsonReality.assertThatJson;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OllamaClientTest {

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
  void submitPrompt_parsesOllamaResponse() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "SELECT * FROM users;");
    JsonObject response = new JsonObject();
    response.add("message", message);
    response.addProperty("done", true);

    server.createContext(
        "/api/chat",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertThatJson(requestBody)
              .fieldEquals("model", "llama3")
              .fieldEquals("stream", false)
              .hasField("options.temperature");
          JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
          assertThat(req.getAsJsonObject("options").get("temperature").getAsDouble())
              .isEqualTo(0.1);

          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);

    String result =
        client.submitPrompt(
            List.of(
                ChatMessage.system("You are a SQL expert."), ChatMessage.user("Show all users")));

    assertThat(result).isEqualTo("SELECT * FROM users;");
  }

  @Test
  void modelName_returnsConfiguredModel() {
    OllamaClient client = new OllamaClient("http://localhost:11434", "codellama", 0.5, 1024);
    assertThat(client.modelName()).isEqualTo("codellama");
  }

  @Test
  void submitPrompt_throwsOnHttpError() {
    server.createContext(
        "/api/chat",
        exchange -> {
          byte[] body = "model not found".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(404, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "nonexistent", 0.7, 2048);

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("hello"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Ollama API error (HTTP 404)");
  }

  @Test
  void submitPrompt_sendsAllMessages() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "ok");
    JsonObject response = new JsonObject();
    response.add("message", message);

    server.createContext(
        "/api/chat",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertThatJson(requestBody).fieldIsArray("messages");

          JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
          assertThat(req.getAsJsonArray("messages").size()).isEqualTo(3);

          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.7, 2048);
    client.submitPrompt(
        List.of(
            ChatMessage.system("system"),
            ChatMessage.user("question"),
            ChatMessage.assistant("prior answer")));
  }

  @Test
  void trailingSlashInBaseUrl_isHandled() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "done");
    JsonObject response = new JsonObject();
    response.add("message", message);

    server.createContext(
        "/api/chat",
        exchange -> {
          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl + "/", "llama3", 0.7, 2048);
    String result = client.submitPrompt(List.of(ChatMessage.user("test")));
    assertThat(result).isEqualTo("done");
  }
}
