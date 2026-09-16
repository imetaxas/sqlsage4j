package io.github.imetaxas.sqlsage4j.db;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock database connector that returns pre-registered results for specific SQL queries. Supports
 * substring matching for flexible test setup.
 */
public final class MockDatabaseConnector implements DatabaseConnector {

  private final String dialect;
  private final Map<String, DataFrame> results = new LinkedHashMap<>();
  private DataFrame defaultResult = DataFrame.empty();

  public MockDatabaseConnector(String dialect) {
    this.dialect = dialect;
  }

  public MockDatabaseConnector withResult(String sqlSubstring, DataFrame result) {
    results.put(sqlSubstring.toLowerCase(), result);
    return this;
  }

  public MockDatabaseConnector withDefaultResult(DataFrame result) {
    this.defaultResult = result;
    return this;
  }

  @Override
  public DataFrame runSql(String sql) {
    String lower = sql.toLowerCase();
    for (Map.Entry<String, DataFrame> entry : results.entrySet()) {
      if (lower.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return defaultResult;
  }

  @Override
  public boolean isValidSql(String sql) {
    return sql != null && !sql.isBlank();
  }

  @Override
  public String dialect() {
    return dialect;
  }
}
