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

public final class OpenAIClient implements LLMClient {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final Gson GSON = new Gson();
  private static final List<String> SQL_STOP_SEQUENCES = List.of(";\n", ";\r\n", "\n\n");

  private final String apiKey;
  private final String model;
  private final double temperature;
  private final long maxTokens;
  private final HttpClient httpClient;
  private final String baseUrl;

  public OpenAIClient(String apiKey, String model, double temperature, long maxTokens) {
    this(apiKey, model, temperature, maxTokens, DEFAULT_BASE_URL);
  }

  public OpenAIClient(
      String apiKey, String model, double temperature, long maxTokens, String baseUrl) {
    this.apiKey = apiKey;
    this.model = Objects.requireNonNull(model, "model");
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.baseUrl = baseUrl;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    int approxTokens = messages.stream().mapToInt(m -> m.content().length() / 4).sum();
    logger.info("Using model {} for ~{} tokens", model, approxTokens);

    JsonObject body = buildRequestBody(messages);
    String responseJson = doPost(baseUrl + "/chat/completions", body.toString());
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

    JsonArray stopArray = new JsonArray();
    SQL_STOP_SEQUENCES.forEach(stopArray::add);

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messagesArray);
    body.addProperty("max_tokens", maxTokens);
    body.addProperty("temperature", temperature);
    body.add("stop", stopArray);
    return body;
  }

  private String doPost(String url, String jsonBody) {
    try {
      HttpRequest.Builder reqBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      if (apiKey != null && !apiKey.isBlank()) {
        reqBuilder.header("Authorization", "Bearer " + apiKey);
      }
      HttpRequest request = reqBuilder.build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "OpenAI API error (HTTP " + response.statusCode() + "): " + response.body());
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("OpenAI request interrupted", e);
    } catch (Exception e) {
      throw new RuntimeException("OpenAI request failed", e);
    }
  }

  private String extractContent(String responseJson) {
    JsonObject resp = GSON.fromJson(responseJson, JsonObject.class);
    return resp.getAsJsonArray("choices")
        .get(0)
        .getAsJsonObject()
        .getAsJsonObject("message")
        .get("content")
        .getAsString();
  }
}
