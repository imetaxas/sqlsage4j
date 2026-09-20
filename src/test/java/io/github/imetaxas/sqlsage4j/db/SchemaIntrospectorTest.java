package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

final class SchemaIntrospectorTest {

  private SQLiteDataSource ds;
  private File dbFile;

  @BeforeEach
  void setUp() throws Exception {
    dbFile = File.createTempFile("introspect_test_", ".db");
    dbFile.deleteOnExit();

    ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT)");
      stmt.execute(
          "CREATE TABLE orders (id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), total REAL, status TEXT)");
      stmt.execute("CREATE TABLE empty_table (pk INTEGER PRIMARY KEY)");
    }
  }

  @AfterEach
  void tearDown() {
    if (dbFile != null) dbFile.delete();
  }

  @Test
  void introspect_discoversAllTables() {
    SchemaIntrospector introspector = new SchemaIntrospector(ds);
    List<String> ddls = introspector.introspect();

    assertThat(ddls.size()).isGreaterThanOrEqualTo(3);
  }

  @Test
  void introspect_generatesValidDdlWithColumns() {
    SchemaIntrospector introspector = new SchemaIntrospector(ds);
    List<String> ddls = introspector.introspect();

    String usersDdl =
        ddls.stream()
            .filter(d -> d.contains("TABLE users") || d.contains("TABLE main.users"))
            .findFirst()
            .orElse("");
    assertThat(usersDdl).contains("CREATE TABLE");
    assertThat(usersDdl).contains("id");
    assertThat(usersDdl).contains("name");
    assertThat(usersDdl).contains("email");
    assertThat(usersDdl).contains("NOT NULL");
  }

  @Test
  void introspect_includesPrimaryKey() {
    SchemaIntrospector introspector = new SchemaIntrospector(ds);
    List<String> ddls = introspector.introspect();

    String usersDdl =
        ddls.stream()
            .filter(d -> d.contains("users") && !d.contains("orders"))
            .findFirst()
            .orElse("");
    assertThat(usersDdl).contains("PRIMARY KEY");
  }

  @Test
  void introspect_includesForeignKeys() {
    SchemaIntrospector introspector = new SchemaIntrospector(ds);
    List<String> ddls = introspector.introspect();

    String ordersDdl = ddls.stream().filter(d -> d.contains("orders")).findFirst().orElse("");
    assertThat(ordersDdl).contains("REFERENCES");
    assertThat(ordersDdl).contains("users");
  }
}
