package io.github.imetaxas.sqlsage4j.db;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base JDBC implementation of {@link DatabaseConnector}. Subclasses only need to provide a {@link
 * DataSource} and declare their SQL dialect.
 */
public abstract class JdbcDatabaseConnector implements DatabaseConnector {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final DataSource dataSource;

  protected JdbcDatabaseConnector(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
  }

  @Override
  public DataFrame runSql(String sql) {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {

      ResultSetMetaData meta = rs.getMetaData();
      int columnCount = meta.getColumnCount();

      List<String> columns = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        columns.add(meta.getColumnLabel(i));
      }

      List<List<Object>> rows = new ArrayList<>();
      while (rs.next()) {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
          row.add(rs.getObject(i));
        }
        rows.add(row);
      }

      logger.debug("{}: {} rows, {} columns", dialect(), rows.size(), columnCount);
      return new DataFrame(columns, rows);
    } catch (SQLException e) {
      throw new RuntimeException(dialect() + " query failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean isValidSql(String sql) {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      validateSql(stmt, sql);
      return true;
    } catch (SQLException e) {
      logger.debug("{} SQL validation failed: {}", dialect(), e.getMessage());
      return false;
    }
  }

  /**
   * Validates SQL without executing it. The default implementation sets {@code maxRows=0} and runs
   * the query. Subclasses can override to use database-specific validation (e.g., EXPLAIN).
   */
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.setMaxRows(0);
    stmt.execute(sql);
  }
}
