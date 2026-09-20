package io.github.imetaxas.sqlsage4j.training;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.TrainingRecord;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import/export training data as JSON for backup, version control, and sharing.
 *
 * <pre>{@code
 * // Export all training data
 * TrainingDataIO.exportToFile(trainingService, Path.of("training-data.json"));
 *
 * // Import into a new service
 * TrainingDataIO.importFromFile(trainingService, Path.of("training-data.json"));
 * }</pre>
 */
public final class TrainingDataIO {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private TrainingDataIO() {}

  /** Exports all training data from a TrainingService to a JSON file. */
  public static void exportToFile(TrainingService trainingService, Path outputPath) {
    try (Writer writer = Files.newBufferedWriter(outputPath)) {
      exportToWriter(trainingService, writer);
    } catch (IOException e) {
      throw new RuntimeException("Failed to export training data: " + e.getMessage(), e);
    }
  }

  /** Exports all training data from a TrainingService to a Writer. */
  public static void exportToWriter(TrainingService trainingService, Writer writer) {
    List<TrainingRecord> allRecords = trainingService.getAllTrainingData();
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);
    root.addProperty("recordCount", allRecords.size());

    JsonArray records = new JsonArray();
    for (TrainingRecord record : allRecords) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", record.id());
      obj.addProperty("type", record.type().name());
      obj.addProperty("content", record.content());
      if (record.metadata() != null && !record.metadata().isEmpty()) {
        obj.add("metadata", GSON.toJsonTree(record.metadata()));
      }
      records.add(obj);
    }
    root.add("records", records);

    GSON.toJson(root, writer);
  }

  /** Exports all training data as a JSON string. */
  public static String exportToString(TrainingService trainingService) {
    List<TrainingRecord> allRecords = trainingService.getAllTrainingData();
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);
    root.addProperty("recordCount", allRecords.size());

    JsonArray records = new JsonArray();
    for (TrainingRecord record : allRecords) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", record.id());
      obj.addProperty("type", record.type().name());
      obj.addProperty("content", record.content());
      if (record.metadata() != null && !record.metadata().isEmpty()) {
        obj.add("metadata", GSON.toJsonTree(record.metadata()));
      }
      records.add(obj);
    }
    root.add("records", records);
    return GSON.toJson(root);
  }

  /** Imports training data from a JSON file into a TrainingService. */
  public static int importFromFile(TrainingService trainingService, Path inputPath) {
    try (Reader reader = Files.newBufferedReader(inputPath)) {
      return importFromReader(trainingService, reader);
    } catch (IOException e) {
      throw new RuntimeException("Failed to import training data: " + e.getMessage(), e);
    }
  }

  /** Imports training data from a Reader into a TrainingService. */
  public static int importFromReader(TrainingService trainingService, Reader reader) {
    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
    return importRecords(trainingService, root);
  }

  /** Imports training data from a JSON string into a TrainingService. */
  public static int importFromString(TrainingService trainingService, String json) {
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    return importRecords(trainingService, root);
  }

  private static int importRecords(TrainingService trainingService, JsonObject root) {
    JsonArray records = root.getAsJsonArray("records");
    int imported = 0;

    for (JsonElement element : records) {
      JsonObject obj = element.getAsJsonObject();
      String type = obj.get("type").getAsString();
      String content = obj.get("content").getAsString();

      TrainingDataType dataType = TrainingDataType.valueOf(type);
      switch (dataType) {
        case DDL -> trainingService.trainDdl(content);
        case SQL_QA -> {
          Map<String, String> meta = extractMetadata(obj);
          String question = meta.getOrDefault("question", "");
          String sql = meta.getOrDefault("sql", "");
          if (!question.isEmpty() && !sql.isEmpty()) {
            trainingService.trainQuestionSql(question, sql);
          } else {
            int newlineIdx = content.indexOf('\n');
            if (newlineIdx > 0) {
              trainingService.trainQuestionSql(
                  content.substring(0, newlineIdx), content.substring(newlineIdx + 1));
            }
          }
        }
        case DOCUMENTATION -> trainingService.trainDocumentation(content);
      }
      imported++;
    }
    return imported;
  }

  private static Map<String, String> extractMetadata(JsonObject obj) {
    Map<String, String> metadata = new HashMap<>();
    if (obj.has("metadata") && obj.get("metadata").isJsonObject()) {
      JsonObject meta = obj.getAsJsonObject("metadata");
      for (String key : meta.keySet()) {
        JsonElement val = meta.get(key);
        if (val.isJsonPrimitive()) {
          metadata.put(key, val.getAsString());
        }
      }
    }
    return metadata;
  }

  /**
   * Exports raw records from an EmbeddingsStorage (all collections) to a JSON file. Useful for
   * low-level backup of specific storage backends.
   */
  public static void exportStorage(
      EmbeddingsStorage storage, List<String> collections, Path outputPath) {
    try (Writer writer = Files.newBufferedWriter(outputPath)) {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);

      JsonArray allRecords = new JsonArray();
      for (String collection : collections) {
        for (TrainingRecord record : storage.getAll(collection)) {
          JsonObject obj = new JsonObject();
          obj.addProperty("collection", collection);
          obj.addProperty("id", record.id());
          obj.addProperty("type", record.type().name());
          obj.addProperty("content", record.content());
          if (record.metadata() != null && !record.metadata().isEmpty()) {
            obj.add("metadata", GSON.toJsonTree(record.metadata()));
          }
          allRecords.add(obj);
        }
      }
      root.addProperty("recordCount", allRecords.size());
      root.add("records", allRecords);
      GSON.toJson(root, writer);
    } catch (IOException e) {
      throw new RuntimeException("Failed to export storage: " + e.getMessage(), e);
    }
  }
}
