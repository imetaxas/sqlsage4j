package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LuceneEmbeddingsStorageTest {

  private LuceneEmbeddingsStorage storage;

  @BeforeEach
  void setUp() {
    storage = new LuceneEmbeddingsStorage();
  }

  @AfterEach
  void tearDown() {
    storage.close();
  }

  @Test
  void storeAndSearch() {
    TrainingRecord r1 =
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE users", new float[] {1, 0, 0}, Map.of());
    TrainingRecord r2 =
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "CREATE TABLE orders", new float[] {0, 1, 0}, Map.of());

    storage.store("ddl", r1);
    storage.store("ddl", r2);

    List<SearchResult> results = storage.search("ddl", new float[] {1, 0, 0}, 2);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).record().id()).isEqualTo("id1");
  }

  @Test
  void searchEmptyCollection() {
    List<SearchResult> results = storage.search("empty", new float[] {1, 0, 0}, 5);
    assertThat(results).isEmpty();
  }

  @Test
  void getAllReturnsStoredRecords() {
    storage.store(
        "docs",
        new TrainingRecord(
            "d1", TrainingDataType.DOCUMENTATION, "doc1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "docs",
        new TrainingRecord(
            "d2", TrainingDataType.DOCUMENTATION, "doc2", new float[] {0, 1, 0}, Map.of()));
    storage.store(
        "other",
        new TrainingRecord("d3", TrainingDataType.DDL, "ddl1", new float[] {0, 0, 1}, Map.of()));

    List<TrainingRecord> docs = storage.getAll("docs");
    assertThat(docs).hasSize(2);
  }

  @Test
  void getAllEmptyCollection() {
    List<TrainingRecord> results = storage.getAll("nonexistent");
    assertThat(results).isEmpty();
  }

  @Test
  void removeById() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "table1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "table2", new float[] {0, 1, 0}, Map.of()));

    assertThat(storage.remove("id1")).isTrue();
    assertThat(storage.getAll("ddl")).hasSize(1);
  }

  @Test
  void removeNonexistent() {
    assertThat(storage.remove("ghost")).isFalse();
  }

  @Test
  void removeAll() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1, 0}, Map.of()));

    storage.removeAll("ddl");
    assertThat(storage.getAll("ddl")).isEmpty();
  }

  @Test
  void metadataIsPreserved() {
    Map<String, String> meta = Map.of("source", "migration", "version", "2");
    storage.store(
        "ddl",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE t", new float[] {1, 0, 0}, meta));

    List<TrainingRecord> all = storage.getAll("ddl");
    assertThat(all.get(0).metadata().get("source")).isEqualTo("migration");
    assertThat(all.get(0).metadata().get("version")).isEqualTo("2");
  }

  @Test
  void searchRespectsCollectionIsolation() {
    storage.store(
        "col1",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "in col1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "col2",
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "in col2", new float[] {1, 0, 0}, Map.of()));

    List<SearchResult> col1Results = storage.search("col1", new float[] {1, 0, 0}, 10);
    assertThat(col1Results).hasSize(1);
    assertThat(col1Results.get(0).record().id()).isEqualTo("id1");
  }
}
