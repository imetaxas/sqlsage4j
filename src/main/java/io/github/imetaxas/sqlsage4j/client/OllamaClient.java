package io.github.imetaxas.sqlsage4j.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * LLM client for Ollama's native API ({@code /api/chat}). Use this when connecting directly to an
 * Ollama server without the OpenAI compatibility layer.
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">Ollama API docs</a>
 */
public final class OllamaClient implements LLMClient {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final Gson GSON = new Gson();

  /**
   * Stop generation when a SQL-terminating semicolon followed by a newline is produced. This
   * prevents the model from generating explanations or preamble after the query, significantly
   * reducing output tokens.
   */
  private static final List<String> SQL_STOP_SEQUENCES = List.of(";\n", ";\r\n", "\n\n");

  private final String baseUrl;
  private final String model;
  private final double temperature;
  private final long maxTokens;
  private final HttpClient httpClient;

  public OllamaClient(String baseUrl, String model, double temperature, long maxTokens) {
    this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    this.model = Objects.requireNonNull(model, "model");
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    int approxTokens = messages.stream().mapToInt(m -> m.content().length() / 4).sum();
    logger.info("Ollama [{}] ~{} tokens", model, approxTokens);

    JsonObject body = buildRequestBody(messages);
    String responseJson = doPost(baseUrl + "/api/chat", body.toString());
    return extractContent(responseJson);
  }

  @Override
  public String modelName() {
    return model;
  }

  private JsonObject buildRequestBody(List<ChatMessage> messages) {
    JsonArray messagesArray = new JsonArray();
    for (ChatMessage msg : messages) {
      JsonObject m = new JsonObject();
      m.addProperty("role", msg.role().name().toLowerCase());
      m.addProperty("content", msg.content());
      messagesArray.add(m);
    }

    JsonObject options = new JsonObject();
    options.addProperty("temperature", temperature);
    options.addProperty("num_predict", maxTokens);
    JsonArray stopArray = new JsonArray();
    SQL_STOP_SEQUENCES.forEach(stopArray::add);
    options.add("stop", stopArray);

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messagesArray);
    body.add("options", options);
    body.addProperty("stream", false);
    return body;
  }

  private String doPost(String url, String jsonBody) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Ollama API error (HTTP " + response.statusCode() + "): " + response.body());
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Ollama request interrupted", e);
    } catch (Exception e) {
      throw new RuntimeException("Ollama request failed: " + e.getMessage(), e);
    }
  }

  /** Extracts content from Ollama's native response: {@code {"message":{"content":"..."}}} */
  private String extractContent(String responseJson) {
    JsonObject resp = GSON.fromJson(responseJson, JsonObject.class);

    if (resp.has("prompt_eval_count") && resp.has("eval_count")) {
      long promptTokens = resp.get("prompt_eval_count").getAsLong();
      long evalTokens = resp.get("eval_count").getAsLong();
      long promptMs =
          resp.has("prompt_eval_duration")
              ? resp.get("prompt_eval_duration").getAsLong() / 1_000_000
              : 0;
      long evalMs =
          resp.has("eval_duration") ? resp.get("eval_duration").getAsLong() / 1_000_000 : 0;
      logger.debug(
          "Ollama stats: prompt={}tok/{}ms, eval={}tok/{}ms",
          promptTokens,
          promptMs,
          evalTokens,
          evalMs);
    }

    return resp.getAsJsonObject("message").get("content").getAsString();
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
