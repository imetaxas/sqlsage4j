package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InMemoryEmbeddingsStorageTest {

  private InMemoryEmbeddingsStorage storage;

  @BeforeEach
  void setUp() {
    storage = new InMemoryEmbeddingsStorage();
  }

  @Test
  void storeAndRetrieve() {
    TrainingRecord record =
        new TrainingRecord(
            "id1",
            TrainingDataType.DDL,
            "CREATE TABLE t(id INT);",
            new float[] {1, 0, 0},
            Map.of());

    storage.store("ddl", record);

    List<TrainingRecord> all = storage.getAll("ddl");
    assertThat(all).hasSize(1);
    assertThat(all.get(0).content()).isEqualTo("CREATE TABLE t(id INT);");
  }

  @Test
  void searchReturnsSortedByRelevance() {
    storage.store(
        "ddl",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "users table", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "orders table", new float[] {0, 1, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord(
            "id3", TrainingDataType.DDL, "products table", new float[] {0.9f, 0.1f, 0}, Map.of()));

    // Query vector close to id1
    List<SearchResult> results = storage.search("ddl", new float[] {1, 0, 0}, 2);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).record().id()).isEqualTo("id1");
    assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
  }

  @Test
  void searchRespectsNResults() {
    for (int i = 0; i < 10; i++) {
      storage.store(
          "docs",
          new TrainingRecord(
              "id" + i, TrainingDataType.DOCUMENTATION, "doc " + i, new float[] {i, 0}, Map.of()));
    }

    List<SearchResult> results = storage.search("docs", new float[] {5, 0}, 3);
    assertThat(results).hasSize(3);
  }

  @Test
  void removeById() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "table1", new float[] {1, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "table2", new float[] {0, 1}, Map.of()));

    assertThat(storage.remove("id1")).isTrue();
    assertThat(storage.getAll("ddl")).hasSize(1);
    assertThat(storage.remove("nonexistent")).isFalse();
  }

  @Test
  void removeAll() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1}, Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {1}, Map.of()));

    storage.removeAll("ddl");
    assertThat(storage.getAll("ddl")).isEmpty();
  }

  @Test
  void emptyCollectionReturnsEmpty() {
    assertThat(storage.getAll("nonexistent")).isEmpty();
    assertThat(storage.search("nonexistent", new float[] {1}, 5)).isEmpty();
  }
}
