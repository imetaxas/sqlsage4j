package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class JdbcDatabaseConnectorTest {

  private JdbcDataSource dataSource;
  private PostgresConnector connector;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:testdb_jdbc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    connector = new PostgresConnector(dataSource);

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS employees");
      stmt.execute(
          "CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(100), department VARCHAR(50), salary DECIMAL(10,2))");
      stmt.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 120000.00)");
      stmt.execute("INSERT INTO employees VALUES (2, 'Bob', 'Marketing', 95000.00)");
      stmt.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 130000.00)");
    }
  }

  @Test
  void runSql_returnsDataFrame() {
    DataFrame df = connector.runSql("SELECT name, salary FROM employees ORDER BY name");

    assertThat(df.columns()).containsExactly("NAME", "SALARY");
    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.rows().get(0).get(0)).as("first name").isEqualTo("Alice");
  }

  @Test
  void runSql_aggregateQuery() {
    DataFrame df =
        connector.runSql(
            "SELECT department, COUNT(*) AS cnt, AVG(salary) AS avg_salary "
                + "FROM employees GROUP BY department ORDER BY department");

    assertThat(df.columns()).containsExactly("DEPARTMENT", "CNT", "AVG_SALARY");
    assertThat(df.rowCount()).isEqualTo(2);
  }

  @Test
  void runSql_emptyResult() {
    DataFrame df = connector.runSql("SELECT * FROM employees WHERE id = 999");

    assertThat(df.isEmpty()).isTrue();
    assertThat(df.columns()).containsExactly("ID", "NAME", "DEPARTMENT", "SALARY");
  }

  @Test
  void runSql_invalidSqlThrows() {
    assertThatThrownBy(() -> connector.runSql("SELECT * FROM nonexistent_table"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("query failed");
  }

  @Test
  void isValidSql_trueForValidQuery() {
    assertThat(connector.isValidSql("SELECT * FROM employees")).isTrue();
  }

  @Test
  void isValidSql_falseForInvalidQuery() {
    assertThat(connector.isValidSql("SELECT * FROM ghost_table")).isFalse();
  }

  @Test
  void dialect_returnsPostgreSQL() {
    assertThat(connector.dialect()).isEqualTo("PostgreSQL");
  }

  @Test
  void dataFrame_toMarkdown() {
    DataFrame df = connector.runSql("SELECT name, department FROM employees ORDER BY name");
    String md = df.toMarkdown();

    assertThat(md).contains("| NAME | DEPARTMENT |");
    assertThat(md).contains("Alice");
    assertThat(md).contains("Engineering");
  }
}
