package io.github.imetaxas.sqlsage4j.storage;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.Map;
import java.util.Objects;

/**
 * A stored training data item with its embedding vector.
 *
 * <p>Training records represent the knowledge the RAG pipeline retrieves from: DDL schemas, Q&amp;A
 * pairs (question + gold SQL), and documentation snippets.
 */
public final class TrainingRecord {

  private final String id;
  private final TrainingDataType type;
  private final String content;
  private final float[] embedding;
  private final Map<String, String> metadata;

  public TrainingRecord(
      String id,
      TrainingDataType type,
      String content,
      float[] embedding,
      Map<String, String> metadata) {
    this.id = Objects.requireNonNull(id);
    this.type = Objects.requireNonNull(type);
    this.content = Objects.requireNonNull(content);
    this.embedding = Objects.requireNonNull(embedding);
    this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
  }

  public String id() {
    return id;
  }

  public TrainingDataType type() {
    return type;
  }

  public String content() {
    return content;
  }

  public float[] embedding() {
    return embedding;
  }

  public Map<String, String> metadata() {
    return metadata;
  }

  @Override
  public String toString() {
    return "TrainingRecord{type="
        + type
        + ", content='"
        + content
        + "', metadata="
        + metadata
        + "}";
  }
}
