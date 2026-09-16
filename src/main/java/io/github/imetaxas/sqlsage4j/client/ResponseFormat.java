package io.github.imetaxas.sqlsage4j.client;

/**
 * Controls the response format from the LLM.
 *
 * <ul>
 *   <li>{@link #TEXT} — default free-form text response; SQL is extracted via regex.
 *   <li>{@link #JSON} — instructs the LLM to return a JSON object with a {@code "sql"} field,
 *       enabling reliable extraction without regex.
 * </ul>
 *
 * <pre>{@code
 * SqlSage4j sage = SqlSage4j.builder(config)
 *     .responseFormat(ResponseFormat.JSON)
 *     .build();
 * }</pre>
 */
public enum ResponseFormat {
  TEXT,
  JSON
}
