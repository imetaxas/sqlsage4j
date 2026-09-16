package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LSHEmbeddingsStorageTest {

  private LSHEmbeddingsStorage storage;

  @BeforeEach
  void setUp() {
    storage = new LSHEmbeddingsStorage(8, 3, 42L);
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
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1, 0}, Map.of()));

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

    assertThat(storage.remove("id1")).isTrue();
    assertThat(storage.getAll("ddl")).hasSize(1);
    assertThat(storage.remove("nonexistent")).isFalse();
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
  void defaultConstructorUses64Dimensions() {
    LSHEmbeddingsStorage defaultStorage = new LSHEmbeddingsStorage();
    float[] vec64 = new float[64];
    vec64[0] = 1.0f;

    defaultStorage.store(
        "test", new TrainingRecord("id1", TrainingDataType.DDL, "t1", vec64, Map.of()));

    List<SearchResult> results = defaultStorage.search("test", vec64, 1);
    assertThat(results).hasSize(1);
  }

  @Test
  void searchFallsBackToBruteForceWhenFewCandidates() {
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0, 0}, Map.of()));

    List<SearchResult> results = storage.search("ddl", new float[] {0, 0, 1}, 5);
    assertThat(results).hasSize(1);
  }

  @Test
  void shortVectorHandledGracefully() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "short", new float[] {1}, Map.of()));

    List<SearchResult> results = storage.search("ddl", new float[] {1}, 1);
    assertThat(results).hasSize(1);
  }

  @Test
  void multipleCollectionsAreIsolated() {
    storage.store(
        "col1",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "in col1", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "col2",
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "in col2", new float[] {1, 0, 0}, Map.of()));

    assertThat(storage.getAll("col1")).hasSize(1);
    assertThat(storage.getAll("col2")).hasSize(1);
    assertThat(storage.getAll("col1").get(0).id()).isEqualTo("id1");
  }
}
