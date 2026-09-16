package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests DuckDBConnector using H2 as a stand-in (DuckDB JDBC isn't on the test classpath). The base
 * JDBC behavior is identical.
 */
final class DuckDBConnectorTest {

  private DuckDBConnector connector;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:testdb_duckdb;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");

    connector = new DuckDBConnector(ds);

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS events");
      stmt.execute(
          "CREATE TABLE events (id INT PRIMARY KEY, event_type VARCHAR(50), user_id INT, ts TIMESTAMP)");
      stmt.execute(
          "INSERT INTO events VALUES (1, 'page_view', 10, TIMESTAMP '2025-01-15 10:00:00')");
      stmt.execute("INSERT INTO events VALUES (2, 'click', 10, TIMESTAMP '2025-01-15 10:01:00')");
      stmt.execute(
          "INSERT INTO events VALUES (3, 'page_view', 20, TIMESTAMP '2025-01-15 11:00:00')");
    }
  }

  @Test
  void runSql_returnsCorrectData() {
    DataFrame df = connector.runSql("SELECT event_type, user_id FROM events ORDER BY id");

    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.columns()).containsExactly("EVENT_TYPE", "USER_ID");
    assertThat(df.rows().get(0).get(0)).as("event type").isEqualTo("page_view");
  }

  @Test
  void runSql_countDistinct() {
    DataFrame df = connector.runSql("SELECT COUNT(DISTINCT user_id) AS unique_users FROM events");

    assertThat(df.rowCount()).isEqualTo(1);
    assertThat(((Number) df.rows().get(0).get(0)).intValue()).isEqualTo(2);
  }

  @Test
  void isValidSql_works() {
    assertThat(connector.isValidSql("SELECT * FROM events")).isTrue();
    assertThat(connector.isValidSql("INVALID SQL SYNTAX %%%")).isFalse();
  }

  @Test
  void dialect_returnsDuckDB() {
    assertThat(connector.dialect()).isEqualTo("DuckDB SQL");
  }
}
