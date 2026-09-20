package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HybridEmbeddingsStorageTest {

  private HybridEmbeddingsStorage storage;
  private InMemoryEmbeddingsStorage vectorStore;
  private BM25Storage bm25Store;

  @BeforeEach
  void setUp() {
    vectorStore = new InMemoryEmbeddingsStorage();
    bm25Store = new BM25Storage();
    storage = new HybridEmbeddingsStorage(vectorStore, bm25Store, 0.6);
  }

  @Test
  void storePutsInBothBackends() {
    TrainingRecord record =
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE t", new float[] {1, 0}, Map.of());
    storage.store("ddl", record);

    assertThat(vectorStore.getAll("ddl")).hasSize(1);
    assertThat(bm25Store.getAll("ddl")).hasSize(1);
  }

  @Test
  void searchCombinesResults() {
    storage.store(
        "ddl",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE users", new float[] {1, 0, 0}, Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "CREATE TABLE orders", new float[] {0, 1, 0}, Map.of()));

    List<SearchResult> results = storage.search("ddl", new float[] {1, 0, 0}, 2);
    assertThat(results).hasSize(2);
  }

  @Test
  void removeRemovesFromBoth() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));

    assertThat(storage.remove("id1")).isTrue();
    assertThat(vectorStore.getAll("ddl")).isEmpty();
    assertThat(bm25Store.getAll("ddl")).isEmpty();
  }

  @Test
  void removeAllClearsBoth() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1}, Map.of()));

    storage.removeAll("ddl");
    assertThat(vectorStore.getAll("ddl")).isEmpty();
    assertThat(bm25Store.getAll("ddl")).isEmpty();
  }

  @Test
  void getAllDelegatesToVectorStore() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1}, Map.of()));

    assertThat(storage.getAll("ddl")).hasSize(1);
  }

  @Test
  void invalidAlphaThrows() {
    assertThatThrownBy(() -> new HybridEmbeddingsStorage(vectorStore, bm25Store, 1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HybridEmbeddingsStorage(vectorStore, bm25Store, -0.1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultAlphaIs06() {
    HybridEmbeddingsStorage defaultStorage = new HybridEmbeddingsStorage(vectorStore, bm25Store);
    defaultStorage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "test", new float[] {1}, Map.of()));
    assertThat(defaultStorage.search("ddl", new float[] {1}, 1)).hasSize(1);
  }
}
