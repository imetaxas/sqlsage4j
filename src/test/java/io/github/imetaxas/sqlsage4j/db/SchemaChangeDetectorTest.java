package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.SchemaChangeDetector.SchemaChange;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

final class SchemaChangeDetectorTest {

  private File dbFile;
  private SQLiteDataSource dataSource;
  private TrainingService trainingService;

  @BeforeEach
  void setUp() throws Exception {
    dbFile = File.createTempFile("schema_change_test_", ".db");
    dbFile.deleteOnExit();
    dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
      stmt.execute(
          "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");
    }

    trainingService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());
  }

  @AfterEach
  void tearDown() {
    if (dbFile != null) dbFile.delete();
  }

  @Test
  void noChanges_whenTrainedMatchesDatabase() {
    trainingService.trainDdl("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
    trainingService.trainDdl(
        "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");

    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    List<SchemaChange> changes = detector.detect();

    assertThat(changes).isEmpty();
    assertThat(detector.hasDrift()).isFalse();
  }

  @Test
  void detectsNewTable_inDatabase() {
    trainingService.trainDdl("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");

    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    List<SchemaChange> changes = detector.detect();

    assertThat(changes).isNotEmpty();
    assertThat(
            changes.stream()
                .anyMatch(
                    c ->
                        c.type() == SchemaChange.ChangeType.TABLE_ADDED
                            && c.tableName().equals("orders")))
        .isTrue();
  }

  @Test
  void detectsRemovedTable_fromDatabase() {
    trainingService.trainDdl("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
    trainingService.trainDdl(
        "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");
    trainingService.trainDdl("CREATE TABLE products (product_id INTEGER, name TEXT, price REAL)");

    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    List<SchemaChange> changes = detector.detect();

    assertThat(
            changes.stream()
                .anyMatch(
                    c ->
                        c.type() == SchemaChange.ChangeType.TABLE_REMOVED
                            && c.tableName().equals("products")))
        .isTrue();
  }

  @Test
  void detectsNewColumn_inExistingTable() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("ALTER TABLE users ADD COLUMN age INTEGER");
    }

    trainingService.trainDdl("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
    trainingService.trainDdl(
        "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");

    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    List<SchemaChange> changes = detector.detect();

    assertThat(
            changes.stream()
                .anyMatch(
                    c ->
                        c.type() == SchemaChange.ChangeType.COLUMN_ADDED
                            && c.detail().contains("age")))
        .isTrue();
  }

  @Test
  void detectsRemovedColumn() {
    trainingService.trainDdl(
        "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT, phone TEXT)");
    trainingService.trainDdl(
        "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, user_id INTEGER, amount REAL)");

    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    List<SchemaChange> changes = detector.detect();

    assertThat(
            changes.stream()
                .anyMatch(
                    c ->
                        c.type() == SchemaChange.ChangeType.COLUMN_REMOVED
                            && c.detail().contains("phone")))
        .isTrue();
  }

  @Test
  void hasDrift_returnsTrueWhenChangesExist() {
    SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
    assertThat(detector.hasDrift()).isTrue();
  }

  @Test
  void toString_formatsReadably() {
    SchemaChange change =
        new SchemaChange(SchemaChange.ChangeType.TABLE_ADDED, "products", "New table");
    assertThat(change.toString()).contains("TABLE_ADDED");
    assertThat(change.toString()).contains("products");
  }
}
