package io.github.imetaxas.sqlsage4j.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Embeddings provider for Ollama's native API ({@code /api/embed}). Supports any embedding model
 * available in Ollama (e.g., {@code nomic-embed-text}, {@code mxbai-embed-large}).
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings">Ollama
 *     Embeddings API</a>
 */
public final class OllamaEmbeddingsProvider implements EmbeddingsProvider {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DEFAULT_MODEL = "nomic-embed-text";
  private static final Gson GSON = new Gson();

  private final String baseUrl;
  private final String model;
  private final HttpClient httpClient;

  public OllamaEmbeddingsProvider(String baseUrl) {
    this(baseUrl, DEFAULT_MODEL);
  }

  public OllamaEmbeddingsProvider(String baseUrl, String model) {
    this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    this.model = Objects.requireNonNull(model, "model");
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public float[] generateEmbedding(String text) {
    logger.debug("Ollama embedding [{}] for text of length {}", model, text.length());

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.addProperty("input", text);

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/api/embed"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException(
            "Ollama embeddings error (HTTP " + response.statusCode() + "): " + response.body());
      }

      JsonObject resp = GSON.fromJson(response.body(), JsonObject.class);
      JsonArray embeddingsOuter = resp.getAsJsonArray("embeddings");
      JsonArray embeddingArray = embeddingsOuter.get(0).getAsJsonArray();

      float[] embedding = new float[embeddingArray.size()];
      for (int i = 0; i < embeddingArray.size(); i++) {
        embedding[i] = embeddingArray.get(i).getAsFloat();
      }
      return embedding;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Ollama embedding request interrupted", e);
    } catch (Exception e) {
      throw new RuntimeException("Ollama embedding request failed: " + e.getMessage(), e);
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
