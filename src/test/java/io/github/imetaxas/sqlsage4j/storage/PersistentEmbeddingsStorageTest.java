package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentEmbeddingsStorageTest {

  @TempDir Path tempDir;
  private Path filePath;

  @BeforeEach
  void setUp() {
    filePath = tempDir.resolve("test-training.json");
  }

  @Test
  void store_persistsToFile() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage.store(
        "ddl",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE t (id INT)", new float[0], Map.of()));

    assertThat(Files.exists(filePath)).isTrue();
  }

  @Test
  void store_andReload_preservesData() throws IOException {
    PersistentEmbeddingsStorage storage1 =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage1.store(
        "ddl",
        new TrainingRecord(
            "id1",
            TrainingDataType.DDL,
            "CREATE TABLE users (id INT, name TEXT)",
            new float[0],
            Map.of()));
    storage1.store(
        "sql",
        new TrainingRecord(
            "id2",
            TrainingDataType.SQL_QA,
            "SELECT COUNT(*) FROM users",
            new float[0],
            Map.of("question", "How many users?", "sql", "SELECT COUNT(*) FROM users")));

    PersistentEmbeddingsStorage storage2 =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    List<TrainingRecord> ddls = storage2.getAll("ddl");
    assertThat(ddls).hasSize(1);
    assertThat(ddls.get(0).content()).contains("CREATE TABLE users");

    List<TrainingRecord> qas = storage2.getAll("sql");
    assertThat(qas).hasSize(1);
    assertThat(qas.get(0).metadata().get("question")).isEqualTo("How many users?");
  }

  @Test
  void remove_persistsRemoval() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage.store(
        "ddl",
        new TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE t (id INT)", new float[0], Map.of()));
    storage.store(
        "ddl",
        new TrainingRecord(
            "id2", TrainingDataType.DDL, "CREATE TABLE u (id INT)", new float[0], Map.of()));

    assertThat(storage.getAll("ddl")).hasSize(2);

    storage.remove("id1");

    PersistentEmbeddingsStorage reloaded =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    assertThat(reloaded.getAll("ddl")).hasSize(1);
    assertThat(reloaded.getAll("ddl").get(0).id()).isEqualTo("id2");
  }

  @Test
  void removeAll_persistsClear() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "DDL1", new float[0], Map.of()));
    storage.store(
        "ddl", new TrainingRecord("id2", TrainingDataType.DDL, "DDL2", new float[0], Map.of()));

    storage.removeAll("ddl");

    PersistentEmbeddingsStorage reloaded =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    assertThat(reloaded.getAll("ddl")).isEmpty();
  }

  @Test
  void construction_withNoFile_startsEmpty() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(tempDir.resolve("nonexistent.json"))
            .build();

    assertThat(storage.getAll("ddl")).isEmpty();
  }

  @Test
  void search_delegatesToInnerStore() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage.store(
        "ddl",
        new TrainingRecord(
            "id1",
            TrainingDataType.DDL,
            "CREATE TABLE x (id INT)",
            new float[] {1.0f, 0.0f},
            Map.of()));

    List<SearchResult> results = storage.search("ddl", new float[] {1.0f, 0.0f}, 5);
    assertThat(results).isNotEmpty();
  }

  @Test
  void defaultFilePath_isUsedWhenNotConfigured() {
    PersistentEmbeddingsStorage storage =
        PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
            .filePath(filePath)
            .build();

    storage.store(
        "ddl", new TrainingRecord("id1", TrainingDataType.DDL, "test", new float[0], Map.of()));
    assertThat(Files.exists(filePath)).isTrue();
  }
}
