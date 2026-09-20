package io.github.imetaxas.sqlsage4j.db;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable tabular data structure representing SQL query results.
 *
 * <p>Provides column names, typed rows, and export methods for Markdown, CSV, and JSON. Instances
 * are created by {@link DatabaseConnector#runSql(String)} or the static factory {@link #empty()}.
 */
public final class DataFrame {

  private final List<String> columns;
  private final List<List<Object>> rows;

  public DataFrame(List<String> columns, List<List<Object>> rows) {
    this.columns = List.copyOf(Objects.requireNonNull(columns));
    this.rows = rows.stream().map(List::copyOf).collect(Collectors.toUnmodifiableList());
  }

  public static DataFrame empty() {
    return new DataFrame(Collections.emptyList(), Collections.emptyList());
  }

  public List<String> columns() {
    return columns;
  }

  public List<List<Object>> rows() {
    return rows;
  }

  public int rowCount() {
    return rows.size();
  }

  public int columnCount() {
    return columns.size();
  }

  public boolean isEmpty() {
    return rows.isEmpty();
  }

  public DataFrame head(int n) {
    return new DataFrame(columns, rows.subList(0, Math.min(n, rows.size())));
  }

  public String toMarkdown() {
    if (columns.isEmpty()) return "(empty)";
    StringBuilder sb = new StringBuilder();
    sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
    sb.append("| ")
        .append(columns.stream().map(c -> "---").collect(Collectors.joining(" | ")))
        .append(" |\n");
    for (List<Object> row : rows) {
      sb.append("| ");
      for (int i = 0; i < row.size(); i++) {
        if (i > 0) sb.append(" | ");
        sb.append(row.get(i) == null ? "NULL" : row.get(i).toString());
      }
      sb.append(" |\n");
    }
    return sb.toString();
  }

  public String toCsv() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.join(",", columns)).append("\n");
    for (List<Object> row : rows) {
      sb.append(
              row.stream().map(v -> v == null ? "" : v.toString()).collect(Collectors.joining(",")))
          .append("\n");
    }
    return sb.toString();
  }

  public String toJson() {
    StringBuilder sb = new StringBuilder("[");
    for (int r = 0; r < rows.size(); r++) {
      if (r > 0) sb.append(",");
      sb.append("{");
      List<Object> row = rows.get(r);
      for (int c = 0; c < columns.size(); c++) {
        if (c > 0) sb.append(",");
        sb.append("\"").append(columns.get(c)).append("\":");
        Object val = c < row.size() ? row.get(c) : null;
        if (val == null) sb.append("null");
        else if (val instanceof Number) sb.append(val);
        else sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
      }
      sb.append("}");
    }
    sb.append("]");
    return sb.toString();
  }
}
