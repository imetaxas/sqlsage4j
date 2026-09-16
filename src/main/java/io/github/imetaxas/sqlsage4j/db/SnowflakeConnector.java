package io.github.imetaxas.sqlsage4j.db;

import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * Snowflake database connector. Requires the Snowflake JDBC driver on the classpath.
 *
 * <pre>{@code
 * SnowflakeBasicDataSource ds = new SnowflakeBasicDataSource();
 * ds.setUrl("jdbc:snowflake://account.snowflakecomputing.com");
 * ds.setUser("user");
 * ds.setPassword("pass");
 * ds.setDatabaseName("MY_DB");
 * ds.setWarehouse("COMPUTE_WH");
 * DatabaseConnector sf = new SnowflakeConnector(ds);
 * }</pre>
 */
public final class SnowflakeConnector extends JdbcDatabaseConnector {

  public SnowflakeConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "Snowflake SQL";
  }

  @Override
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.execute("EXPLAIN " + sql);
  }
}
