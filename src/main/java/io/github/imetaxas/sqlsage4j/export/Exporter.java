package io.github.imetaxas.sqlsage4j.export;

import io.github.imetaxas.sqlsage4j.db.DataFrame;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Exporter {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Exporter() {}

  public static void export(DataFrame df, ExportType type, String outputPath) {
    String content =
        switch (type) {
          case JSON -> df.toJson();
          case CSV -> df.toCsv();
          case TEXT -> df.toMarkdown();
        };

    try {
      Files.writeString(Path.of(outputPath), content);
      logger.info("Exported {} rows as {} to {}", df.rowCount(), type, outputPath);
    } catch (IOException e) {
      throw new RuntimeException("Export failed: " + e.getMessage(), e);
    }
  }
}
