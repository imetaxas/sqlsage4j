package io.github.imetaxas.sqlsage4j.storage;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decorator that persists all training data to a JSON file on every write operation and loads it on
 * construction. Wraps any {@link EmbeddingsStorage} to make training data survive JVM restarts.
 *
 * <pre>{@code
 * EmbeddingsStorage persistent = PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
 *     .filePath(Path.of("./training-data.json"))
 *     .build();
 *
 * // Use in builder — data auto-loads from file and auto-saves on every train call
 * SqlSage4j sage = SqlSage4j.builder(LLMProviderConfig.builder("gpt-4")
 *         .embeddingsStorage(persistent)
 *         .build())
 *     .build();
 * }</pre>
 */
public final class PersistentEmbeddingsStorage implements EmbeddingsStorage {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final EmbeddingsStorage delegate;
  private final Path filePath;

  private PersistentEmbeddingsStorage(EmbeddingsStorage delegate, Path filePath) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.filePath = Objects.requireNonNull(filePath, "filePath");
    loadFromDisk();
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    String id = delegate.store(collection, record);
    saveToDisk();
    return id;
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    return delegate.search(collection, queryEmbedding, nResults);
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    return delegate.getAll(collection);
  }

  @Override
  public boolean remove(String id) {
    boolean removed = delegate.remove(id);
    if (removed) saveToDisk();
    return removed;
  }

  @Override
  public void removeAll(String collection) {
    delegate.removeAll(collection);
    saveToDisk();
  }

  private void loadFromDisk() {
    if (!Files.exists(filePath)) {
      logger.debug("No persisted training data at {}", filePath);
      return;
    }
    try {
      String json = Files.readString(filePath);
      int count = importJson(json);
      logger.info("Loaded {} training records from {}", count, filePath);
    } catch (IOException e) {
      logger.warn("Failed to load training data from {}: {}", filePath, e.getMessage());
    }
  }

  private void saveToDisk() {
    try {
      String json = exportJson();
      Files.createDirectories(filePath.getParent());
      Files.writeString(filePath, json);
    } catch (IOException e) {
      logger.warn("Failed to persist training data to {}: {}", filePath, e.getMessage());
    }
  }

  private String exportJson() {
    com.google.gson.JsonArray array = new com.google.gson.JsonArray();
    for (String collection : List.of("ddl", "sql", "documentation")) {
      for (TrainingRecord record : delegate.getAll(collection)) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("id", record.id());
        obj.addProperty("collection", collection);
        obj.addProperty("content", record.content());
        if (record.metadata() != null && !record.metadata().isEmpty()) {
          com.google.gson.JsonObject meta = new com.google.gson.JsonObject();
          record.metadata().forEach(meta::addProperty);
          obj.add("metadata", meta);
        }
        array.add(obj);
      }
    }
    return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array);
  }

  private int importJson(String json) {
    com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
    int count = 0;
    for (var element : array) {
      com.google.gson.JsonObject obj = element.getAsJsonObject();
      String collection = obj.get("collection").getAsString();
      String content = obj.get("content").getAsString();
      String id =
          obj.has("id") ? obj.get("id").getAsString() : java.util.UUID.randomUUID().toString();

      java.util.Map<String, String> metadata = new java.util.HashMap<>();
      if (obj.has("metadata") && obj.get("metadata").isJsonObject()) {
        obj.getAsJsonObject("metadata")
            .entrySet()
            .forEach(e -> metadata.put(e.getKey(), e.getValue().getAsString()));
      }

      TrainingRecord record =
          new TrainingRecord(
              id,
              io.github.imetaxas.sqlsage4j.training.TrainingDataType.valueOf(
                  collection.toUpperCase().equals("SQL") ? "SQL_QA" : collection.toUpperCase()),
              content,
              new float[0],
              metadata);
      delegate.store(collection, record);
      count++;
    }
    return count;
  }

  public static Builder builder(EmbeddingsStorage delegate) {
    return new Builder(delegate);
  }

  public static final class Builder {
    private final EmbeddingsStorage delegate;
    private Path filePath = Path.of("sqlsage4j-training-data.json");

    private Builder(EmbeddingsStorage delegate) {
      this.delegate = delegate;
    }

    /** Path to the JSON file for persistence. Default: ./sqlsage4j-training-data.json */
    public Builder filePath(Path filePath) {
      this.filePath = Objects.requireNonNull(filePath);
      return this;
    }

    public PersistentEmbeddingsStorage build() {
      return new PersistentEmbeddingsStorage(delegate, filePath);
    }
  }
}
