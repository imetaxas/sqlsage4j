package io.github.imetaxas.sqlsage4j.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Condition for {@link org.junit.jupiter.api.condition.EnabledIf} on live Ollama ITs.
 *
 * <p>Uses {@code GET /api/tags} with a short timeout so tests never wait on a model pull.
 */
final class OllamaITSupport {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  private OllamaITSupport() {}

  /**
   * Returns {@code true} when Ollama is reachable and every required model is installed. Logs a
   * warning and returns {@code false} otherwise (JUnit then disables the class).
   */
  static boolean isAvailable(String baseUrl, String... requiredModels) {
    ProbeResult result = probe(baseUrl, requiredModels);
    if (!result.available()) {
      logger.warn("Skipping Ollama integration tests: {}", result.reason());
    }
    return result.available();
  }

  static ProbeResult probe(String baseUrl, String... requiredModels) {
    String url = stripTrailingSlash(baseUrl) + "/api/tags";
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return ProbeResult.unavailable(
            "Ollama at " + baseUrl + " returned HTTP " + response.statusCode() + " for /api/tags");
      }
      Set<String> installed = parseModelNames(response.body());
      for (String required : requiredModels) {
        if (!hasModel(installed, required)) {
          return ProbeResult.unavailable(
              "Ollama is running but model '"
                  + required
                  + "' is not installed. Pull it with: ollama pull "
                  + required
                  + ". Installed: "
                  + installed);
        }
      }
      return ProbeResult.AVAILABLE;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ProbeResult.unavailable("Interrupted while checking Ollama at " + baseUrl);
    } catch (Exception e) {
      return ProbeResult.unavailable(
          "Ollama is not reachable at " + baseUrl + " (" + e.getClass().getSimpleName() + ")");
    }
  }

  static Set<String> parseModelNames(String tagsJson) {
    Set<String> names = new LinkedHashSet<>();
    JsonObject root = JsonParser.parseString(tagsJson).getAsJsonObject();
    JsonArray models = root.getAsJsonArray("models");
    if (models == null) {
      return names;
    }
    for (JsonElement element : models) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject model = element.getAsJsonObject();
      addName(names, model, "name");
      addName(names, model, "model");
    }
    return names;
  }

  static boolean hasModel(Set<String> installed, String required) {
    String want = required.toLowerCase(Locale.ROOT);
    for (String name : installed) {
      String have = name.toLowerCase(Locale.ROOT);
      if (have.equals(want) || have.startsWith(want + ":")) {
        return true;
      }
    }
    return false;
  }

  private static void addName(Set<String> names, JsonObject model, String field) {
    JsonElement value = model.get(field);
    if (value != null && value.isJsonPrimitive()) {
      String name = value.getAsString();
      if (!name.isBlank()) {
        names.add(name);
      }
    }
  }

  private static String stripTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  record ProbeResult(boolean available, String reason) {
    static final ProbeResult AVAILABLE = new ProbeResult(true, "");

    static ProbeResult unavailable(String reason) {
      return new ProbeResult(false, reason);
    }
  }
}
