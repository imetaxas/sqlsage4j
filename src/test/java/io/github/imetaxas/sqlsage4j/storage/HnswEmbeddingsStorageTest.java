package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HnswEmbeddingsStorageTest {

  private HnswEmbeddingsStorage storage;

  @BeforeEach
  void setUp() {
    storage = new HnswEmbeddingsStorage();
  }

  @Test
  void storeAndSearch() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "users", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "orders", new float[] {0, 1, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord(
            "id3", TrainingDataType.DDL, "products", new float[] {0.9f, 0.1f, 0}, Map.of()));

    List<SearchResult> results = storage.search("ddl", new float[] {1, 0, 0}, 2);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).record().id()).isEqualTo("id1");
  }

  @Test
  void searchEmptyCollection() {
    assertThat(storage.search("empty", new float[] {1, 0, 0}, 5)).isEmpty();
  }

  @Test
  void getAllReturnsAllRecords() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1}, Map.of()));

    assertThat(storage.getAll("ddl")).hasSize(2);
  }

  @Test
  void getAllNonexistentCollection() {
    assertThat(storage.getAll("ghost")).isEmpty();
  }

  @Test
  void removeById() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id3", TrainingDataType.DDL, "t3", new float[] {0, 0, 1}, Map.of()));

    assertThat(storage.remove("id2")).isTrue();
    assertThat(storage.getAll("ddl")).hasSize(2);
    assertThat(storage.remove("nonexistent")).isFalse();
  }

  @Test
  void removeEntryPoint_graphStillFunctions() {
    storage.store(
        "ddl",
        new TrainingRecord("ep", TrainingDataType.DDL, "entry", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "second", new float[] {0, 1, 0}, Map.of()));

    assertThat(storage.remove("ep")).isTrue();
    List<SearchResult> results = storage.search("ddl", new float[] {0, 1, 0}, 1);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).record().id()).isEqualTo("id2");
  }

  @Test
  void removeAll() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1}, Map.of()));

    storage.removeAll("ddl");
    assertThat(storage.getAll("ddl")).isEmpty();
    assertThat(storage.search("ddl", new float[] {1, 0}, 5)).isEmpty();
  }

  @Test
  void customParameters() {
    HnswEmbeddingsStorage custom = new HnswEmbeddingsStorage(4, 16);
    custom.store(
        "col", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));
    custom.store(
        "col", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1}, Map.of()));

    List<SearchResult> results = custom.search("col", new float[] {1, 0}, 1);
    assertThat(results).hasSize(1);
  }

  @Test
  void manyInsertions_prunesConnections() {
    for (int i = 0; i < 50; i++) {
      float[] vec = new float[3];
      vec[i % 3] = 1.0f;
      storage.store(
          "big", new TrainingRecord("id" + i, TrainingDataType.DDL, "record" + i, vec, Map.of()));
    }

    List<SearchResult> results = storage.search("big", new float[] {1, 0, 0}, 5);
    assertThat(results.size()).isGreaterThan(0);
    assertThat(results.size()).isLessThanOrEqualTo(5);
  }

  @Test
  void removeFromMultipleCollections() {
    storage.store(
        "col1",
        new TrainingRecord(
            "shared_id", TrainingDataType.DDL, "shared", new float[] {1, 0}, Map.of()));

    assertThat(storage.remove("shared_id")).isTrue();
    assertThat(storage.getAll("col1")).isEmpty();
  }
}
