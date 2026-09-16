package io.github.imetaxas.sqlsage4j.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
 * Streaming LLM client for OpenAI-compatible APIs (OpenAI, Azure OpenAI, vLLM, LM Studio, etc.).
 * Uses Server-Sent Events (SSE) to deliver tokens in real time.
 *
 * <pre>{@code
 * StreamingLLMClient client = new OpenAIStreamingClient(
 *     System.getenv("OPENAI_API_KEY"), "gpt-4o", 0.0, 4096);
 *
 * client.streamPrompt(messages, token -> System.out.print(token.text()));
 * }</pre>
 */
public final class OpenAIStreamingClient implements StreamingLLMClient {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final Gson GSON = new Gson();
  private static final List<String> SQL_STOP_SEQUENCES = List.of(";\n", ";\r\n", "\n\n");
  private static final String SSE_DATA_PREFIX = "data: ";
  private static final String SSE_DONE_MARKER = "[DONE]";

  private final String apiKey;
  private final String model;
  private final double temperature;
  private final long maxTokens;
  private final HttpClient httpClient;
  private final String baseUrl;

  public OpenAIStreamingClient(String apiKey, String model, double temperature, long maxTokens) {
    this(apiKey, model, temperature, maxTokens, DEFAULT_BASE_URL);
  }

  public OpenAIStreamingClient(
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
    StringBuilder result = new StringBuilder();
    try (Stream<StreamToken> tokens = streamPrompt(messages)) {
      tokens.forEach(t -> result.append(t.text()));
    }
    return result.toString();
  }

  @Override
  public Stream<StreamToken> streamPrompt(List<ChatMessage> messages) {
    logger.info("OpenAI streaming [{}]", model);

    JsonObject body = buildRequestBody(messages, true);
    InputStream inputStream = doStreamPost(baseUrl + "/chat/completions", body.toString());
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    Iterator<StreamToken> iterator = new SSEStreamIterator(reader);
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
    body.addProperty("stream", stream);
    if (stream) {
      JsonObject streamOptions = new JsonObject();
      streamOptions.addProperty("include_usage", true);
      body.add("stream_options", streamOptions);
    }
    return body;
  }

  private InputStream doStreamPost(String url, String jsonBody) {
    try {
      HttpRequest.Builder reqBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "application/json")
              .header("Accept", "text/event-stream")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      if (apiKey != null && !apiKey.isBlank()) {
        reqBuilder.header("Authorization", "Bearer " + apiKey);
      }
      HttpRequest request = reqBuilder.build();

      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() != 200) {
        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        throw new RuntimeException(
            "OpenAI API error (HTTP " + response.statusCode() + "): " + errorBody);
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("OpenAI streaming request interrupted", e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("OpenAI streaming request failed: " + e.getMessage(), e);
    }
  }

  /**
   * Parses OpenAI's Server-Sent Events (SSE) format:
   *
   * <pre>
   * data: {"choices":[{"delta":{"content":"SELECT"}}]}
   * data: {"choices":[{"delta":{"content":" *"}}]}
   * data: [DONE]
   * </pre>
   */
  private static final class SSEStreamIterator implements Iterator<StreamToken> {

    private final BufferedReader reader;
    private StreamToken next;
    private boolean finished;

    SSEStreamIterator(BufferedReader reader) {
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
        while (true) {
          String line = reader.readLine();
          if (line == null) {
            finished = true;
            return null;
          }
          if (line.isBlank()) continue;

          if (!line.startsWith(SSE_DATA_PREFIX)) continue;
          String data = line.substring(SSE_DATA_PREFIX.length()).trim();

          if (SSE_DONE_MARKER.equals(data)) {
            finished = true;
            return StreamToken.done();
          }

          JsonObject obj = GSON.fromJson(data, JsonObject.class);

          if (obj.has("usage") && obj.get("usage").isJsonObject()) {
            JsonObject usage = obj.getAsJsonObject("usage");
            long prompt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : 0;
            long completion =
                usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : 0;
            finished = true;
            return StreamToken.done(prompt, completion);
          }

          JsonArray choices = obj.getAsJsonArray("choices");
          if (choices == null || choices.size() == 0) continue;

          JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
          if (delta == null || !delta.has("content")) continue;

          String content = delta.get("content").getAsString();
          if (content.isEmpty()) continue;
          return StreamToken.of(content);
        }
      } catch (IOException e) {
        finished = true;
        throw new RuntimeException("Error reading OpenAI SSE stream", e);
      }
    }
  }
}
