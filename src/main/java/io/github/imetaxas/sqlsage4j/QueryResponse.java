package io.github.imetaxas.sqlsage4j;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

/**
 * Result of a text-to-SQL generation request.
 *
 * <p>Contains the generated SQL (if successful), the raw LLM response, or an error message.
 * Construct via the static factory methods {@link #success} and {@link #error}.
 *
 * <p>The {@link #confidence()} score (0.0–1.0) indicates how likely the generated SQL is correct,
 * based on retrieval similarity and SQL validation.
 */
@AutoValue
public abstract class QueryResponse {
  QueryResponse() {}

  public abstract String id();

  public abstract String question();

  @Nullable
  public abstract String sql();

  @Nullable
  public abstract String rawLlmResponse();

  @Nullable
  public abstract String error();

  /**
   * Confidence score between 0.0 and 1.0 indicating how likely the generated SQL is correct. Based
   * on retrieval similarity (how close the question matches trained Q&amp;A) and SQL validation
   * (whether the database accepted the query). Returns 0.0 for error responses.
   */
  public abstract double confidence();

  public boolean isSuccess() {
    return sql() != null && error() == null;
  }

  public static QueryResponse success(String id, String question, String sql, String rawResponse) {
    return new AutoValue_QueryResponse(id, question, sql, rawResponse, null, 0.5);
  }

  public static QueryResponse success(
      String id, String question, String sql, String rawResponse, double confidence) {
    return new AutoValue_QueryResponse(id, question, sql, rawResponse, null, confidence);
  }

  public static QueryResponse error(String id, String question, String error) {
    return new AutoValue_QueryResponse(id, question, null, null, error, 0.0);
  }
}
