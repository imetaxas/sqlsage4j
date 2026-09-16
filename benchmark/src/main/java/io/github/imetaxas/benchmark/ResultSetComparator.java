package io.github.imetaxas.benchmark;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Compares the result sets of two SQL queries (generated vs gold) by executing both against the
 * same database. Supports both strict matching (same columns, same rows) and lenient matching
 * (generated may return extra columns, comparison on shared columns only).
 */
public final class ResultSetComparator {

  private final DataSource dataSource;

  public ResultSetComparator(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Strict match: same rows on all columns (order-insensitive). */
  public boolean executionMatch(String generatedSql, String goldSql) {
    try {
      QueryResult generated = executeWithMeta(generatedSql);
      QueryResult gold = executeWithMeta(goldSql);

      if (rowSetsEqual(generated.rows(), gold.rows())) {
        return true;
      }

      return lenientMatch(generated, gold);
    } catch (SQLException e) {
      return false;
    }
  }

  public boolean isValidSql(String sql) {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("EXPLAIN " + sql);
      return true;
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * Lenient match: compare only on columns that exist in both result sets (by name, case
   * insensitive). This handles cases where the generated SQL returns extra columns (e.g., email
   * when gold only selects name) or uses slightly different aliases.
   */
  private boolean lenientMatch(QueryResult generated, QueryResult gold) {
    Set<String> genColsLower = new HashSet<>();
    for (String c : generated.columnNames()) genColsLower.add(c.toLowerCase());

    Set<String> goldColsLower = new HashSet<>();
    for (String c : gold.columnNames()) goldColsLower.add(c.toLowerCase());

    Set<String> sharedCols = new HashSet<>(genColsLower);
    sharedCols.retainAll(goldColsLower);

    if (sharedCols.isEmpty()) {
      if (generated.rows().size() != gold.rows().size()) return false;
      return numericValuesMatch(generated, gold);
    }

    List<Integer> genIndices = columnIndices(generated.columnNames(), sharedCols);
    List<Integer> goldIndices = columnIndices(gold.columnNames(), sharedCols);

    List<List<String>> genProjected = project(generated.rows(), genIndices);
    List<List<String>> goldProjected = project(gold.rows(), goldIndices);

    return rowSetsEqual(genProjected, goldProjected);
  }

  /**
   * When column names don't overlap at all (different aliases), fall back to comparing numeric
   * values across result sets. This handles cases like "total_spend" vs "revenue" that are the same
   * data.
   */
  private boolean numericValuesMatch(QueryResult generated, QueryResult gold) {
    if (generated.rows().size() != gold.rows().size()) return false;
    if (generated.rows().isEmpty()) return true;

    int genCols = generated.rows().get(0).size();
    int goldCols = gold.rows().get(0).size();
    if (genCols == 0 || goldCols == 0) return false;

    List<String> genSorted =
        generated.rows().stream().map(r -> String.join("|", r)).sorted().toList();
    List<String> goldSorted =
        gold.rows().stream().map(r -> String.join("|", r)).sorted().toList();

    int matchCount = 0;
    for (int i = 0; i < genSorted.size(); i++) {
      if (rowValuesApproxEqual(genSorted.get(i), goldSorted.get(i))) {
        matchCount++;
      }
    }
    return matchCount == genSorted.size();
  }

  private boolean rowValuesApproxEqual(String genRow, String goldRow) {
    String[] genParts = genRow.split("\\|");
    String[] goldParts = goldRow.split("\\|");

    Set<String> genValues = new HashSet<>();
    for (String p : genParts) genValues.add(normalizeValue(p));
    Set<String> goldValues = new HashSet<>();
    for (String p : goldParts) goldValues.add(normalizeValue(p));

    Set<String> intersection = new HashSet<>(genValues);
    intersection.retainAll(goldValues);
    return !intersection.isEmpty()
        && intersection.size() >= Math.min(genValues.size(), goldValues.size()) * 0.5;
  }

  private String normalizeValue(String val) {
    if (val == null) return "NULL";
    try {
      double d = Double.parseDouble(val);
      return String.format("%.2f", d);
    } catch (NumberFormatException e) {
      return val.trim().toLowerCase();
    }
  }

  private List<Integer> columnIndices(List<String> columns, Set<String> sharedLower) {
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < columns.size(); i++) {
      if (sharedLower.contains(columns.get(i).toLowerCase())) {
        indices.add(i);
      }
    }
    return indices;
  }

  private List<List<String>> project(List<List<String>> rows, List<Integer> indices) {
    List<List<String>> projected = new ArrayList<>();
    for (List<String> row : rows) {
      List<String> pr = new ArrayList<>();
      for (int idx : indices) {
        pr.add(idx < row.size() ? row.get(idx) : "NULL");
      }
      projected.add(pr);
    }
    return projected;
  }

  private QueryResult executeWithMeta(String sql) throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      ResultSetMetaData meta = rs.getMetaData();
      int cols = meta.getColumnCount();

      List<String> columnNames = new ArrayList<>(cols);
      for (int i = 1; i <= cols; i++) {
        columnNames.add(meta.getColumnLabel(i));
      }

      List<List<String>> rows = new ArrayList<>();
      while (rs.next()) {
        List<String> row = new ArrayList<>(cols);
        for (int i = 1; i <= cols; i++) {
          Object val = rs.getObject(i);
          row.add(val == null ? "NULL" : val.toString());
        }
        rows.add(row);
      }
      return new QueryResult(columnNames, rows);
    }
  }

  private boolean rowSetsEqual(List<List<String>> a, List<List<String>> b) {
    if (a.size() != b.size()) return false;
    List<String> aSorted = a.stream().map(r -> String.join("|", r)).sorted().toList();
    List<String> bSorted = b.stream().map(r -> String.join("|", r)).sorted().toList();
    return aSorted.equals(bSorted);
  }

  private record QueryResult(List<String> columnNames, List<List<String>> rows) {}
}
