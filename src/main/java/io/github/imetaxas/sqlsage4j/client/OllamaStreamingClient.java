package io.github.imetaxas.sqlsage4j.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Streaming LLM client for Ollama's native API. Tokens are delivered as they're generated, enabling
 * real-time UI updates and reducing perceived latency.
 *
 * <pre>{@code
 * StreamingLLMClient client = new OllamaStreamingClient(
 *     "http://localhost:11434", "llama3", 0.0, 4096);
 *
 * // Stream with callback
 * String sql = client.streamPrompt(messages, token -> System.out.print(token.text()));
 *
 * // Stream with Java Stream API
 * try (Stream<StreamToken> tokens = client.streamPrompt(messages)) {
 *     tokens.takeWhile(t -> !t.finished()).forEach(t -> System.out.print(t.text()));
 * }
 * }</pre>
 */
public final class OllamaStreamingClient implements StreamingLLMClient {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final Gson GSON = new Gson();
  private static final List<String> SQL_STOP_SEQUENCES = List.of(";\n", ";\r\n", "\n\n");

  private final String baseUrl;
  private final String model;
  private final double temperature;
  private final long maxTokens;
  private final HttpClient httpClient;

  public OllamaStreamingClient(String baseUrl, String model, double temperature, long maxTokens) {
    this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    this.model = Objects.requireNonNull(model, "model");
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    StringBuilder result = new StringBuilder();
    try (Stream<StreamToken> tokens = streamPrompt(messages)) {
      tokens.forEach(t -> result.append(t.text()));
    }
    return result.toString();
  }

  @Override
  public Stream<StreamToken> streamPrompt(List<ChatMessage> messages) {
    logger.info("Ollama streaming [{}]", model);

    JsonObject body = buildRequestBody(messages, true);
    InputStream inputStream = doStreamPost(baseUrl + "/api/chat", body.toString());
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    Iterator<StreamToken> iterator = new OllamaStreamIterator(reader);
    Spliterator<StreamToken> spliterator =
        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);

    return StreamSupport.stream(spliterator, false)
        .onClose(
            () -> {
              try {
                reader.close();
              } catch (IOException e) {
                logger.debug("Error closing stream: {}", e.getMessage());
              }
            });
  }

  @Override
  public String modelName() {
    return model;
  }

  private JsonObject buildRequestBody(List<ChatMessage> messages, boolean stream) {
    var messagesArray = new com.google.gson.JsonArray();
    for (ChatMessage msg : messages) {
      JsonObject m = new JsonObject();
      m.addProperty("role", msg.role().name().toLowerCase());
      m.addProperty("content", msg.content());
      messagesArray.add(m);
    }

    JsonObject options = new JsonObject();
    options.addProperty("temperature", temperature);
    options.addProperty("num_predict", maxTokens);
    var stopArray = new com.google.gson.JsonArray();
    SQL_STOP_SEQUENCES.forEach(stopArray::add);
    options.add("stop", stopArray);

    JsonObject body = new JsonObject();
    body.addProperty("model", model);
    body.add("messages", messagesArray);
    body.add("options", options);
    body.addProperty("stream", stream);
    return body;
  }

  private InputStream doStreamPost(String url, String jsonBody) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() != 200) {
        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        throw new RuntimeException(
            "Ollama API error (HTTP " + response.statusCode() + "): " + errorBody);
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Ollama streaming request interrupted", e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Ollama streaming request failed: " + e.getMessage(), e);
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static final class OllamaStreamIterator implements Iterator<StreamToken> {

    private final BufferedReader reader;
    private StreamToken next;
    private boolean finished;

    OllamaStreamIterator(BufferedReader reader) {
      this.reader = reader;
    }

    @Override
    public boolean hasNext() {
      if (next != null) return true;
      if (finished) return false;
      next = readNext();
      return next != null;
    }

    @Override
    public StreamToken next() {
      if (!hasNext()) throw new NoSuchElementException();
      StreamToken token = next;
      next = null;
      return token;
    }

    private StreamToken readNext() {
      try {
        String line = reader.readLine();
        if (line == null) {
          finished = true;
          return null;
        }
        if (line.isBlank()) return readNext();

        JsonObject obj = GSON.fromJson(line, JsonObject.class);
        boolean done = obj.has("done") && obj.get("done").getAsBoolean();

        if (done) {
          finished = true;
          Long promptTokens =
              obj.has("prompt_eval_count") ? obj.get("prompt_eval_count").getAsLong() : null;
          Long completionTokens = obj.has("eval_count") ? obj.get("eval_count").getAsLong() : null;
          if (promptTokens != null && completionTokens != null) {
            return StreamToken.done(promptTokens, completionTokens);
          }
          return StreamToken.done();
        }

        String content = "";
        if (obj.has("message") && obj.getAsJsonObject("message").has("content")) {
          content = obj.getAsJsonObject("message").get("content").getAsString();
        }
        return StreamToken.of(content);
      } catch (IOException e) {
        finished = true;
        throw new RuntimeException("Error reading Ollama stream", e);
      }
    }
  }
}
