package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

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

final class OllamaClientStatsTest {

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
    if (server != null) server.stop(0);
  }

  @Test
  void submitPrompt_parsesResponseWithTokenStats() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "SELECT 1;");

    JsonObject response = new JsonObject();
    response.add("message", message);
    response.addProperty("done", true);
    response.addProperty("prompt_eval_count", 150);
    response.addProperty("eval_count", 12);
    response.addProperty("prompt_eval_duration", 500_000_000L);
    response.addProperty("eval_duration", 200_000_000L);

    server.createContext(
        "/api/chat",
        exchange -> {
          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);
    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT 1;");
  }

  @Test
  void submitPrompt_parsesResponseWithPartialStats() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "SELECT 2;");

    JsonObject response = new JsonObject();
    response.add("message", message);
    response.addProperty("done", true);
    response.addProperty("prompt_eval_count", 100);
    response.addProperty("eval_count", 8);

    server.createContext(
        "/api/chat",
        exchange -> {
          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);
    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT 2;");
  }

  @Test
  void submitPrompt_handlesResponseWithoutStats() {
    JsonObject message = new JsonObject();
    message.addProperty("role", "assistant");
    message.addProperty("content", "SELECT 3;");

    JsonObject response = new JsonObject();
    response.add("message", message);
    response.addProperty("done", true);

    server.createContext(
        "/api/chat",
        exchange -> {
          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);
    String result = client.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT 3;");
  }

  @Test
  void submitPrompt_sendsStopSequencesInOptions() {
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
          JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
          JsonObject options = req.getAsJsonObject("options");
          assertThat(options.has("stop")).isTrue();
          assertThat(options.getAsJsonArray("stop").size()).isEqualTo(3);

          byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);
    client.submitPrompt(List.of(ChatMessage.user("test")));
  }

  @Test
  void submitPrompt_wrapsInterruptedException() throws Exception {
    server.createContext(
        "/api/chat",
        exchange -> {
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();

    OllamaClient client = new OllamaClient(baseUrl, "llama3", 0.1, 2048);

    Thread testThread = Thread.currentThread();
    new Thread(
            () -> {
              try {
                Thread.sleep(100);
              } catch (InterruptedException ignored) {
              }
              testThread.interrupt();
            })
        .start();

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class);

    Thread.interrupted();
  }

  @Test
  void submitPrompt_throwsOnConnectionRefused() {
    server.stop(0);

    OllamaClient client = new OllamaClient("http://localhost:1", "llama3", 0.1, 2048);

    assertThatThrownBy(() -> client.submitPrompt(List.of(ChatMessage.user("test"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Ollama request failed");
  }
}
