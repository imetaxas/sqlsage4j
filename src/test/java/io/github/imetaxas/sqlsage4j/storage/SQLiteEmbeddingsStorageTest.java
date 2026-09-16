package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SQLiteEmbeddingsStorageTest {

  private SQLiteEmbeddingsStorage storage;

  @BeforeEach
  void setUp() {
    storage = new SQLiteEmbeddingsStorage();
  }

  @AfterEach
  void tearDown() {
    storage.close();
  }

  @Test
  void storeAndRetrieve() {
    TrainingRecord record =
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE t", new float[] {1, 0, 0}, Map.of());
    storage.store("ddl", record);

    List<TrainingRecord> all = storage.getAll("ddl");
    assertThat(all).hasSize(1);
    assertThat(all.get(0).content()).isEqualTo("CREATE TABLE t");
    assertThat(all.get(0).id()).isEqualTo("id1");
  }

  @Test
  void searchReturnsSortedByRelevance() {
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
    assertThat(storage.search("empty", new float[] {1, 0}, 5)).isEmpty();
  }

  @Test
  void removeById() {
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "t1", new float[] {1, 0}, Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "t2", new float[] {0, 1}, Map.of()));

    assertThat(storage.remove("id1")).isTrue();
    assertThat(storage.getAll("ddl")).hasSize(1);
    assertThat(storage.remove("ghost")).isFalse();
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
  void getAllEmptyCollection() {
    assertThat(storage.getAll("nonexistent")).isEmpty();
  }

  @Test
  void metadataIsPreserved() {
    Map<String, String> meta = Map.of("key", "value", "num", "42");
    storage.store(
        "ddl",
        new TrainingRecord("id1", TrainingDataType.DDL, "content", new float[] {1, 0}, meta));

    List<TrainingRecord> all = storage.getAll("ddl");
    assertThat(all.get(0).metadata().get("key")).isEqualTo("value");
    assertThat(all.get(0).metadata().get("num")).isEqualTo("42");
  }

  @Test
  void embeddingRoundTrip() {
    float[] embedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "content", embedding, Map.of()));

    float[] retrieved = storage.getAll("ddl").get(0).embedding();
    assertThat(retrieved.length).isEqualTo(5);
    assertThat(retrieved[0]).isCloseTo(0.1f, 0.001f);
    assertThat(retrieved[4]).isCloseTo(0.5f, 0.001f);
  }
}
