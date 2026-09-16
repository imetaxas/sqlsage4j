package io.github.imetaxas.sqlsage4j.export;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.db.DataFrame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExporterTest {

  @TempDir Path tempDir;

  @Test
  void exportCsv() throws IOException {
    DataFrame df =
        new DataFrame(List.of("id", "name"), List.of(List.of(1, "Alice"), List.of(2, "Bob")));
    String path = tempDir.resolve("output.csv").toString();

    Exporter.export(df, ExportType.CSV, path);

    String content = Files.readString(Path.of(path));
    assertThat(content).contains("id,name");
    assertThat(content).contains("1,Alice");
    assertThat(content).contains("2,Bob");
  }

  @Test
  void exportJson() throws IOException {
    DataFrame df = new DataFrame(List.of("x"), List.of(List.of(42)));
    String path = tempDir.resolve("output.json").toString();

    Exporter.export(df, ExportType.JSON, path);

    String content = Files.readString(Path.of(path));
    assertThat(content).contains("\"x\":42");
  }

  @Test
  void exportText() throws IOException {
    DataFrame df = new DataFrame(List.of("col"), List.of(List.of("val")));
    String path = tempDir.resolve("output.md").toString();

    Exporter.export(df, ExportType.TEXT, path);

    String content = Files.readString(Path.of(path));
    assertThat(content).contains("| col |");
    assertThat(content).contains("| val |");
  }
}
