package io.github.imetaxas.sqlsage4j.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class OpenAIEmbeddingsProvider implements EmbeddingsProvider {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DEFAULT_MODEL = "text-embedding-3-small";
  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final Gson GSON = new Gson();

  private final String apiKey;
  private final String model;
  private final HttpClient httpClient;
  private final String baseUrl;

  public OpenAIEmbeddingsProvider(String apiKey) {
    this(apiKey, DEFAULT_MODEL, DEFAULT_BASE_URL);
  }

  public OpenAIEmbeddingsProvider(String apiKey, String model, String baseUrl) {
    this.apiKey = apiKey;
    this.model = model;
    this.baseUrl = baseUrl;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public float[] generateEmbedding(String text) {
    logger.debug("Generating embedding for text of length {}", text.length());

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.addProperty("input", text);

    try {
      HttpRequest.Builder reqBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/embeddings"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
      if (apiKey != null && !apiKey.isBlank()) {
        reqBuilder.header("Authorization", "Bearer " + apiKey);
      }
      HttpRequest request = reqBuilder.build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "OpenAI embeddings error (HTTP " + response.statusCode() + "): " + response.body());
      }

      JsonObject resp = GSON.fromJson(response.body(), JsonObject.class);
      JsonArray embeddingArray =
          resp.getAsJsonArray("data").get(0).getAsJsonObject().getAsJsonArray("embedding");

      float[] embedding = new float[embeddingArray.size()];
      for (int i = 0; i < embeddingArray.size(); i++) {
        embedding[i] = embeddingArray.get(i).getAsFloat();
      }
      return embedding;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Embedding request interrupted", e);
    } catch (Exception e) {
      throw new RuntimeException("Embedding request failed", e);
    }
  }
}
