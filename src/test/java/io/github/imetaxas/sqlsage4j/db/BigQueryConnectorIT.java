package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for BigQueryConnector using a real BigQuery emulator in Docker. Requires Docker
 * to be running. Skipped automatically if Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@Tag("docker")
final class BigQueryConnectorIT {

  private static final String PROJECT_ID = "test-project";
  private static final String DATASET = "test_dataset";

  @Container
  static GenericContainer<?> bigqueryEmulator =
      new GenericContainer<>(DockerImageName.parse("ghcr.io/goccy/bigquery-emulator:0.6.6"))
          .withCommand("--project", PROJECT_ID, "--dataset", DATASET)
          .withExposedPorts(9050, 9060);

  private static BigQuery bigQuery;
  private static BigQueryConnector connector;

  @BeforeAll
  static void setUp() {
    String emulatorUrl =
        "http://" + bigqueryEmulator.getHost() + ":" + bigqueryEmulator.getMappedPort(9050);

    bigQuery =
        BigQueryOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .setHost(emulatorUrl)
            .setLocation(emulatorUrl)
            .setCredentials(NoCredentials.getInstance())
            .build()
            .getService();

    Schema schema =
        Schema.of(
            Field.of("id", StandardSQLTypeName.INT64),
            Field.of("name", StandardSQLTypeName.STRING),
            Field.of("city", StandardSQLTypeName.STRING));

    TableId tableId = TableId.of(DATASET, "users");
    bigQuery.create(TableInfo.of(tableId, StandardTableDefinition.of(schema)));

    bigQuery.insertAll(
        InsertAllRequest.newBuilder(tableId)
            .addRow(Map.of("id", 1, "name", "Alice", "city", "New York"))
            .addRow(Map.of("id", 2, "name", "Bob", "city", "London"))
            .addRow(Map.of("id", 3, "name", "Charlie", "city", "New York"))
            .build());

    connector =
        new BigQueryConnector(
            PROJECT_ID,
            (sql, dryRun) -> {
              try {
                var config =
                    dryRun
                        ? com.google.cloud.bigquery.QueryJobConfiguration.newBuilder(sql)
                            .setDryRun(true)
                            .build()
                        : com.google.cloud.bigquery.QueryJobConfiguration.of(sql);
                var result = bigQuery.query(config);
                var fields = result.getSchema().getFields();
                var columns = new java.util.ArrayList<String>();
                for (var f : fields) {
                  columns.add(f.getName());
                }
                var rows = new java.util.ArrayList<java.util.List<Object>>();
                for (var row : result.iterateAll()) {
                  var rowData = new java.util.ArrayList<Object>();
                  for (int i = 0; i < columns.size(); i++) {
                    rowData.add(row.get(i).getValue());
                  }
                  rows.add(rowData);
                }
                return new DataFrame(columns, rows);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  void runSql_selectAll() {
    DataFrame df = connector.runSql("SELECT * FROM `test_dataset.users` ORDER BY id");

    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.columns()).contains("id");
    assertThat(df.columns()).contains("name");
    assertThat(df.columns()).contains("city");
  }

  @Test
  void runSql_withFilter() {
    DataFrame df =
        connector.runSql("SELECT name FROM `test_dataset.users` WHERE city = 'New York'");

    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.columns()).containsExactly("name");
  }

  @Test
  void runSql_withAggregation() {
    DataFrame df =
        connector.runSql(
            "SELECT city, COUNT(*) AS cnt FROM `test_dataset.users` GROUP BY city ORDER BY cnt DESC");

    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.columns()).containsExactly("city", "cnt");
  }

  @Test
  void isValidSql_validQuery() {
    assertThat(connector.isValidSql("SELECT 1")).isTrue();
  }

  @Test
  void isValidSql_invalidQuery() {
    assertThat(connector.isValidSql("SELECT * FROM nonexistent_table_xyz")).isFalse();
  }

  @Test
  void dialect_returnsBigQuerySQL() {
    assertThat(connector.dialect()).isEqualTo("BigQuery SQL");
  }
}
