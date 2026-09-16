package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

final class SQLiteConnectorTest {

  private SQLiteConnector connector;
  private File dbFile;

  @BeforeEach
  void setUp() throws Exception {
    dbFile = File.createTempFile("sqlsage4j_test_", ".db");
    dbFile.deleteOnExit();

    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

    connector = new SQLiteConnector(ds);

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price REAL, category TEXT)");
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (2, 'Desk', 249.00, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (3, 'Mouse', 29.99, 'Electronics')");
    }
  }

  @AfterEach
  void tearDown() {
    if (dbFile != null) {
      dbFile.delete();
    }
  }

  @Test
  void runSql_returnsCorrectData() {
    DataFrame df = connector.runSql("SELECT name, price FROM products ORDER BY price DESC");

    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.columns()).containsExactly("name", "price");
    assertThat(df.rows().get(0).get(0)).as("first product").isEqualTo("Laptop");
  }

  @Test
  void runSql_groupBy() {
    DataFrame df =
        connector.runSql(
            "SELECT category, COUNT(*) AS cnt FROM products GROUP BY category ORDER BY category");

    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.rows().get(0).get(0)).as("first category").isEqualTo("Electronics");
  }

  @Test
  void isValidSql_worksForSQLite() {
    assertThat(connector.isValidSql("SELECT * FROM products")).isTrue();
    assertThat(connector.isValidSql("SELECT * FROM nonexistent")).isFalse();
  }

  @Test
  void dialect_returnsSQLite() {
    assertThat(connector.dialect()).isEqualTo("SQLite");
  }
}
