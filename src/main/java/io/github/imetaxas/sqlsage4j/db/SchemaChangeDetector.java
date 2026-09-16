package io.github.imetaxas.sqlsage4j.db;

import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Detects schema drift between the live database and trained DDLs.
 *
 * <p>Compares the current database schema (via {@link SchemaIntrospector}) against the DDLs stored
 * in {@link TrainingService} and reports additions, removals, and modifications.
 *
 * <pre>{@code
 * SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);
 * List<SchemaChange> changes = detector.detect();
 * if (!changes.isEmpty()) {
 *     changes.forEach(c -> logger.warn("Schema drift: {}", c));
 * }
 * }</pre>
 */
public final class SchemaChangeDetector {

  private static final Pattern TABLE_NAME_PATTERN =
      Pattern.compile(
          "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE);

  private final DataSource dataSource;
  private final TrainingService trainingService;

  public SchemaChangeDetector(DataSource dataSource, TrainingService trainingService) {
    this.dataSource = dataSource;
    this.trainingService = trainingService;
  }

  /** Detect schema changes between the live database and trained DDLs. */
  public List<SchemaChange> detect() {
    List<String> liveDdls = new SchemaIntrospector(dataSource).introspect();
    List<String> trainedDdls = trainingService.getAllDdl();

    Set<String> liveTables = extractTableNames(liveDdls);
    Set<String> trainedTables = extractTableNames(trainedDdls);

    List<SchemaChange> changes = new ArrayList<>();

    for (String table : liveTables) {
      if (!trainedTables.contains(table)) {
        changes.add(
            new SchemaChange(
                SchemaChange.ChangeType.TABLE_ADDED,
                table,
                "Table exists in database but not in training data"));
      }
    }

    for (String table : trainedTables) {
      if (!liveTables.contains(table)) {
        changes.add(
            new SchemaChange(
                SchemaChange.ChangeType.TABLE_REMOVED,
                table,
                "Table exists in training data but not in database"));
      }
    }

    for (String table : liveTables) {
      if (trainedTables.contains(table)) {
        Set<String> liveColumns = extractColumns(liveDdls, table);
        Set<String> trainedColumns = extractColumns(trainedDdls, table);

        for (String col : liveColumns) {
          if (!trainedColumns.contains(col)) {
            changes.add(
                new SchemaChange(
                    SchemaChange.ChangeType.COLUMN_ADDED,
                    table,
                    "Column '" + col + "' added to database"));
          }
        }
        for (String col : trainedColumns) {
          if (!liveColumns.contains(col)) {
            changes.add(
                new SchemaChange(
                    SchemaChange.ChangeType.COLUMN_REMOVED,
                    table,
                    "Column '" + col + "' removed from database"));
          }
        }
      }
    }

    return changes;
  }

  /** Returns true if the schema has drifted from training data. */
  public boolean hasDrift() {
    return !detect().isEmpty();
  }

  private Set<String> extractTableNames(List<String> ddls) {
    Set<String> names = new LinkedHashSet<>();
    for (String ddl : ddls) {
      Matcher m = TABLE_NAME_PATTERN.matcher(ddl);
      if (m.find()) {
        names.add(m.group(1).toLowerCase(Locale.ROOT));
      }
    }
    return names;
  }

  private Set<String> extractColumns(List<String> ddls, String tableName) {
    Set<String> columns = new LinkedHashSet<>();
    for (String ddl : ddls) {
      Matcher m = TABLE_NAME_PATTERN.matcher(ddl);
      if (m.find() && m.group(1).equalsIgnoreCase(tableName)) {
        Pattern colPattern =
            Pattern.compile(
                "(?:^|[,(])\\s*(\\w+)\\s+(?:INTEGER|TEXT|REAL|BLOB|VARCHAR|INT|DECIMAL|BOOLEAN|DATE|TIMESTAMP|BIGINT|FLOAT|DOUBLE|CHAR)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher cm = colPattern.matcher(ddl);
        while (cm.find()) {
          String col = cm.group(1).toLowerCase(Locale.ROOT);
          if (!col.equalsIgnoreCase("TABLE") && !col.equalsIgnoreCase("CREATE")) {
            columns.add(col);
          }
        }
      }
    }
    return columns;
  }

  public record SchemaChange(ChangeType type, String tableName, String detail) {

    public enum ChangeType {
      TABLE_ADDED,
      TABLE_REMOVED,
      COLUMN_ADDED,
      COLUMN_REMOVED
    }

    @Override
    public String toString() {
      return type + " [" + tableName + "]: " + detail;
    }
  }
}
