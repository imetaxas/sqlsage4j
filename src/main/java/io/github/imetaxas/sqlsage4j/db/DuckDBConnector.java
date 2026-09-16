package io.github.imetaxas.sqlsage4j.db;

import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * DuckDB database connector. Works with in-memory or file-based DuckDB databases. Requires the
 * DuckDB JDBC driver on the classpath.
 *
 * <pre>{@code
 * DuckDBDataSource ds = new DuckDBDataSource();
 * ds.setUrl("jdbc:duckdb:");  // in-memory
 * DatabaseConnector duckdb = new DuckDBConnector(ds);
 * }</pre>
 */
public final class DuckDBConnector extends JdbcDatabaseConnector {

  public DuckDBConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "DuckDB SQL";
  }

  @Override
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.execute("EXPLAIN " + sql);
  }
}
