package io.github.imetaxas.sqlsage4j.db;

import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * SQLite database connector. Works with in-memory or file-based databases.
 *
 * <pre>{@code
 * SQLiteDataSource ds = new SQLiteDataSource();
 * ds.setUrl("jdbc:sqlite:/path/to/database.db");
 * DatabaseConnector sqlite = new SQLiteConnector(ds);
 * }</pre>
 */
public final class SQLiteConnector extends JdbcDatabaseConnector {

  public SQLiteConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "SQLite";
  }

  @Override
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.execute("EXPLAIN " + sql);
  }
}
