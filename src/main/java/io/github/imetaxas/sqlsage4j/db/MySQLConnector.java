package io.github.imetaxas.sqlsage4j.db;

import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/**
 * MySQL / MariaDB database connector.
 *
 * <pre>{@code
 * MysqlDataSource ds = new MysqlDataSource();
 * ds.setUrl("jdbc:mysql://localhost:3306/mydb");
 * ds.setUser("user");
 * ds.setPassword("pass");
 * DatabaseConnector mysql = new MySQLConnector(ds);
 * }</pre>
 */
public final class MySQLConnector extends JdbcDatabaseConnector {

  public MySQLConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "MySQL";
  }

  @Override
  protected void validateSql(Statement stmt, String sql) throws SQLException {
    stmt.execute("EXPLAIN " + sql);
  }
}
