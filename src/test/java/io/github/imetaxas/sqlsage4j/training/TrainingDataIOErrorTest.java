package io.github.imetaxas.sqlsage4j.training;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TrainingDataIOErrorTest {

  private TrainingService trainingService;

  @BeforeEach
  void setUp() {
    trainingService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());
  }

  @Test
  void exportToFile_nonExistentDirectory_throwsWrappedException() {
    Path invalid = Path.of("/nonexistent/dir/output.json");
    trainingService.trainDdl("CREATE TABLE t (id INT)");

    assertThatThrownBy(() -> TrainingDataIO.exportToFile(trainingService, invalid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to export training data");
  }

  @Test
  void importFromFile_nonExistentFile_throwsWrappedException() {
    Path missing = Path.of("/nonexistent/missing.json");

    assertThatThrownBy(() -> TrainingDataIO.importFromFile(trainingService, missing))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to import training data");
  }

  @Test
  void exportToFile_andImportFromFile_roundTrip(@TempDir Path tempDir) {
    trainingService.trainDdl("CREATE TABLE users (id INT, name TEXT)");
    trainingService.trainQuestionSql("Count?", "SELECT COUNT(*) FROM users");
    trainingService.trainDocumentation("User docs");

    Path file = tempDir.resolve("export.json");
    TrainingDataIO.exportToFile(trainingService, file);

    assertThat(file.toFile().exists()).isTrue();
    assertThat(file.toFile().length()).as("file not empty").isGreaterThan(0L);

    TrainingService fresh =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());
    int imported = TrainingDataIO.importFromFile(fresh, file);

    assertThat(imported).isEqualTo(3);
    assertThat(fresh.getAllTrainingData()).hasSize(3);
  }

  @Test
  void exportToWriter_writesValidJson() {
    trainingService.trainDdl("CREATE TABLE t (x INT)");
    StringWriter sw = new StringWriter();
    TrainingDataIO.exportToWriter(trainingService, sw);

    String json = sw.toString();
    assertThat(json).contains("\"version\"");
    assertThat(json).contains("\"recordCount\": 1");
    assertThat(json).contains("CREATE TABLE t");
  }

  @Test
  void importFromReader_parsesValidJson() {
    String json =
        """
        {"version":1,"recordCount":2,"records":[
          {"id":"a","type":"DDL","content":"CREATE TABLE a (id INT)"},
          {"id":"b","type":"DOCUMENTATION","content":"Some docs"}
        ]}
        """;

    int imported = TrainingDataIO.importFromReader(trainingService, new java.io.StringReader(json));
    assertThat(imported).isEqualTo(2);
  }

  @Test
  void importFromString_sqlQaWithEmptyMetadataAndNewlineContent() {
    String json =
        """
        {"version":1,"recordCount":1,"records":[
          {"id":"1","type":"SQL_QA","content":"What is X?\\nSELECT x FROM t","metadata":{}}
        ]}
        """;

    int imported = TrainingDataIO.importFromString(trainingService, json);
    assertThat(imported).isEqualTo(1);
  }

  @Test
  void importFromString_sqlQaWithNoNewlineAndNoMetadata_skipsGracefully() {
    String json =
        """
        {"version":1,"recordCount":1,"records":[
          {"id":"1","type":"SQL_QA","content":"no newline here"}
        ]}
        """;

    int imported = TrainingDataIO.importFromString(trainingService, json);
    assertThat(imported).isEqualTo(1);
  }

  @Test
  void exportStorage_writesToFile(@TempDir Path tempDir) {
    InMemoryEmbeddingsStorage storage = new InMemoryEmbeddingsStorage();
    storage.store(
        "ddl",
        new io.github.imetaxas.sqlsage4j.storage.TrainingRecord(
            "id1", TrainingDataType.DDL, "CREATE TABLE x (id INT)", new float[] {0.1f}, null));

    Path out = tempDir.resolve("storage.json");
    TrainingDataIO.exportStorage(storage, List.of("ddl"), out);

    assertThat(out.toFile().exists()).isTrue();
    String content;
    try {
      content = java.nio.file.Files.readString(out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertThat(content).contains("CREATE TABLE x");
    assertThat(content).contains("\"collection\"");
  }

  @Test
  void exportStorage_nonExistentPath_throwsWrappedException() {
    InMemoryEmbeddingsStorage storage = new InMemoryEmbeddingsStorage();
    Path invalid = Path.of("/nonexistent/dir/storage.json");

    assertThatThrownBy(() -> TrainingDataIO.exportStorage(storage, List.of("ddl"), invalid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to export storage");
  }

  @Test
  void exportToWriter_ioExceptionDuringWrite_bubbles() {
    trainingService.trainDdl("CREATE TABLE t (id INT)");

    Writer failingWriter =
        new Writer() {
          @Override
          public void write(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("disk full");
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };

    assertThatThrownBy(() -> TrainingDataIO.exportToWriter(trainingService, failingWriter))
        .isInstanceOf(com.google.gson.JsonIOException.class);
  }
}
