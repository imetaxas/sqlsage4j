package io.github.imetaxas.sqlsage4j.db;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BigQuery database connector. Requires google-cloud-bigquery on the classpath. Falls back to a
 * no-op if the dependency is not present.
 */
public final class BigQueryConnector implements DatabaseConnector {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String projectId;
  private final QueryExecutor queryExecutor;

  @FunctionalInterface
  interface QueryExecutor {
    DataFrame execute(String sql, boolean dryRun);
  }

  public BigQueryConnector(String projectId) {
    this.projectId = projectId;
    Object client = initClient();
    this.queryExecutor = (sql, dryRun) -> executeBigQuery(client, sql, dryRun);
  }

  BigQueryConnector(String projectId, QueryExecutor executor) {
    this.projectId = projectId;
    this.queryExecutor = executor;
  }

  @Override
  public DataFrame runSql(String sql) {
    return queryExecutor.execute(sql, false);
  }

  @Override
  public boolean isValidSql(String sql) {
    try {
      queryExecutor.execute(sql, true);
      return true;
    } catch (Exception e) {
      logger.debug("SQL validation failed: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public String dialect() {
    return "BigQuery SQL";
  }

  private static DataFrame executeBigQuery(Object bigQueryClient, String sql, boolean dryRun) {
    try {
      Class<?> bigQueryClass = Class.forName("com.google.cloud.bigquery.BigQuery");
      Class<?> queryJobConfigClass =
          Class.forName("com.google.cloud.bigquery.QueryJobConfiguration");

      Object config;
      if (dryRun) {
        Object configBuilder =
            queryJobConfigClass.getMethod("newBuilder", String.class).invoke(null, sql);
        configBuilder.getClass().getMethod("setDryRun", Boolean.class).invoke(configBuilder, true);
        config = configBuilder.getClass().getMethod("build").invoke(configBuilder);
      } else {
        config = queryJobConfigClass.getMethod("of", String.class).invoke(null, sql);
      }

      Object tableResult =
          bigQueryClass.getMethod("query", queryJobConfigClass).invoke(bigQueryClient, config);
      return convertTableResult(tableResult);
    } catch (ClassNotFoundException e) {
      throw new UnsupportedOperationException(
          "google-cloud-bigquery is not on the classpath. Add the dependency to use BigQuery.", e);
    } catch (Exception e) {
      throw new RuntimeException("BigQuery query failed: " + e.getMessage(), e);
    }
  }

  private Object initClient() {
    try {
      Class<?> bigQueryOptionsClass = Class.forName("com.google.cloud.bigquery.BigQueryOptions");
      Object defaultInstance = bigQueryOptionsClass.getMethod("getDefaultInstance").invoke(null);
      return defaultInstance.getClass().getMethod("getService").invoke(defaultInstance);
    } catch (Exception e) {
      logger.warn("BigQuery client initialization deferred — dependency not available at startup");
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static DataFrame convertTableResult(Object tableResult) throws Exception {
    Iterable<?> schema =
        (Iterable<?>) tableResult.getClass().getMethod("getSchema").invoke(tableResult);
    Object fieldsList = schema.getClass().getMethod("getFields").invoke(schema);

    List<String> columns = new ArrayList<>();
    for (Object field : (Iterable<?>) fieldsList) {
      columns.add((String) field.getClass().getMethod("getName").invoke(field));
    }

    List<List<Object>> rows = new ArrayList<>();
    Iterable<?> rowIterator = (Iterable<?>) tableResult;
    for (Object row : rowIterator) {
      List<Object> rowData = new ArrayList<>();
      for (int i = 0; i < columns.size(); i++) {
        Object fieldValue = row.getClass().getMethod("get", int.class).invoke(row, i);
        Object value = fieldValue.getClass().getMethod("getValue").invoke(fieldValue);
        rowData.add(value);
      }
      rows.add(rowData);
    }
    return new DataFrame(columns, rows);
  }
}
