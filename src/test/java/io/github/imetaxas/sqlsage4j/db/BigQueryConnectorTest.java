package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BigQueryConnectorTest {

  @Test
  void runSql_returnsDataFrameFromExecutor() {
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) ->
            new DataFrame(
                List.of("name", "count"), List.of(List.of("Alice", 42L), List.of("Bob", 17L)));

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);

    DataFrame df = connector.runSql("SELECT name, COUNT(*) FROM users GROUP BY name");

    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.columns()).containsExactly("name", "count");
    assertThat(df.rows().get(0).get(0)).as("first name").isEqualTo("Alice");
    assertThat(df.rows().get(1).get(1)).as("second count").isEqualTo(17L);
  }

  @Test
  void isValidSql_returnsTrueForValidQuery() {
    BigQueryConnector.QueryExecutor executor = (sql, dryRun) -> DataFrame.empty();

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);

    assertThat(connector.isValidSql("SELECT 1")).isTrue();
  }

  @Test
  void isValidSql_returnsFalseWhenExecutorThrows() {
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) -> {
          throw new RuntimeException("Syntax error at position 1");
        };

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);

    assertThat(connector.isValidSql("INVALID SQL")).isFalse();
  }

  @Test
  void isValidSql_passesDryRunFlag() {
    boolean[] dryRunCaptured = {false};
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) -> {
          dryRunCaptured[0] = dryRun;
          return DataFrame.empty();
        };

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);
    connector.isValidSql("SELECT 1");

    assertThat(dryRunCaptured[0]).isTrue();
  }

  @Test
  void runSql_passesFalseForDryRun() {
    boolean[] dryRunCaptured = {true};
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) -> {
          dryRunCaptured[0] = dryRun;
          return new DataFrame(List.of("x"), List.of(List.of(1)));
        };

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);
    connector.runSql("SELECT 1");

    assertThat(dryRunCaptured[0]).isFalse();
  }

  @Test
  void runSql_propagatesExceptions() {
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) -> {
          throw new RuntimeException("Connection timeout");
        };

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> connector.runSql("SELECT 1"));
    assertThat(ex.getMessage()).contains("Connection timeout");
  }

  @Test
  void dialect_returnsBigQuerySQL() {
    BigQueryConnector connector = new BigQueryConnector("proj", (sql, dryRun) -> DataFrame.empty());
    assertThat(connector.dialect()).isEqualTo("BigQuery SQL");
  }

  @Test
  void runSql_passesQueryToExecutor() {
    String[] capturedSql = {null};
    BigQueryConnector.QueryExecutor executor =
        (sql, dryRun) -> {
          capturedSql[0] = sql;
          return DataFrame.empty();
        };

    BigQueryConnector connector = new BigQueryConnector("test-project", executor);
    connector.runSql("SELECT COUNT(*) FROM events");

    assertThat(capturedSql[0]).isEqualTo("SELECT COUNT(*) FROM events");
  }
}
