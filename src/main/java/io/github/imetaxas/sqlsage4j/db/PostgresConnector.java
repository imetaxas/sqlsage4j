package io.github.imetaxas.sqlsage4j.db;

import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * PostgreSQL database connector. Pass any JDBC {@link DataSource} — e.g., {@code
 * PGSimpleDataSource} or a connection pool.
 *
 * <pre>{@code
 * PGSimpleDataSource ds = new PGSimpleDataSource();
 * ds.setUrl("jdbc:postgresql://localhost:5432/mydb");
 * ds.setUser("user");
 * ds.setPassword("pass");
 * DatabaseConnector pg = new PostgresConnector(ds);
 * }</pre>
 */
public final class PostgresConnector extends JdbcDatabaseConnector {

  public PostgresConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "PostgreSQL";
  }

  @Override
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.execute("EXPLAIN " + sql);
  }
}
