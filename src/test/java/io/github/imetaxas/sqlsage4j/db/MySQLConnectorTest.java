package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests MySQLConnector using H2 in MySQL compatibility mode. */
final class MySQLConnectorTest {

  private MySQLConnector connector;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:testdb_mysql;DB_CLOSE_DELAY=-1;MODE=MySQL");
    ds.setUser("sa");
    ds.setPassword("");

    connector = new MySQLConnector(ds);

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS orders");
      stmt.execute(
          "CREATE TABLE orders (id INT PRIMARY KEY, customer VARCHAR(100), total DECIMAL(10,2), status VARCHAR(20))");
      stmt.execute("INSERT INTO orders VALUES (1, 'Alice', 150.00, 'completed')");
      stmt.execute("INSERT INTO orders VALUES (2, 'Bob', 89.50, 'pending')");
      stmt.execute("INSERT INTO orders VALUES (3, 'Alice', 200.00, 'completed')");
    }
  }

  @Test
  void runSql_returnsCorrectData() {
    DataFrame df = connector.runSql("SELECT customer, total FROM orders ORDER BY total DESC");

    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.rows().get(0).get(0)).as("first name").isEqualTo("Alice");
  }

  @Test
  void runSql_aggregateWithFilter() {
    DataFrame df =
        connector.runSql(
            "SELECT customer, SUM(total) AS revenue FROM orders "
                + "WHERE status = 'completed' GROUP BY customer ORDER BY revenue DESC");

    assertThat(df.rowCount()).isEqualTo(1);
    assertThat(df.columns()).containsExactly("CUSTOMER", "REVENUE");
  }

  @Test
  void isValidSql_works() {
    assertThat(connector.isValidSql("SELECT * FROM orders")).isTrue();
    assertThat(connector.isValidSql("SELECT * FROM nowhere")).isFalse();
  }

  @Test
  void dialect_returnsMySQL() {
    assertThat(connector.dialect()).isEqualTo("MySQL");
  }
}
