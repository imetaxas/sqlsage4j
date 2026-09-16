package io.github.imetaxas.sqlsage4j.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Automatically discovers database schemas from a live JDBC connection using {@link
 * DatabaseMetaData}. Generates DDL statements suitable for training the RAG pipeline.
 *
 * <pre>{@code
 * SchemaIntrospector introspector = new SchemaIntrospector(dataSource);
 * List<String> ddls = introspector.introspect();
 * ddls.forEach(chat::trainDdl);
 * }</pre>
 */
public final class SchemaIntrospector {

  private final DataSource dataSource;
  private final String catalog;
  private final String schemaPattern;
  private final String tablePattern;

  public SchemaIntrospector(DataSource dataSource) {
    this(dataSource, null, null, "%");
  }

  public SchemaIntrospector(DataSource dataSource, String schemaPattern) {
    this(dataSource, null, schemaPattern, "%");
  }

  public SchemaIntrospector(
      DataSource dataSource, String catalog, String schemaPattern, String tablePattern) {
    this.dataSource = dataSource;
    this.catalog = catalog;
    this.schemaPattern = schemaPattern;
    this.tablePattern = tablePattern;
  }

  /**
   * Introspects the database and returns a list of CREATE TABLE DDL statements (one per table).
   * Includes column names, types, nullability, primary keys, and foreign keys.
   */
  public List<String> introspect() {
    try (Connection conn = dataSource.getConnection()) {
      return introspect(conn);
    } catch (SQLException e) {
      throw new RuntimeException("Schema introspection failed: " + e.getMessage(), e);
    }
  }

  private List<String> introspect(Connection conn) throws SQLException {
    DatabaseMetaData meta = conn.getMetaData();
    List<String> ddls = new ArrayList<>();

    try (ResultSet tables =
        meta.getTables(catalog, schemaPattern, tablePattern, new String[] {"TABLE", "VIEW"})) {
      while (tables.next()) {
        String tableCatalog = tables.getString("TABLE_CAT");
        String tableSchema = tables.getString("TABLE_SCHEM");
        String tableName = tables.getString("TABLE_NAME");
        String tableType = tables.getString("TABLE_TYPE");

        String ddl = generateDdl(meta, tableCatalog, tableSchema, tableName, tableType);
        if (ddl != null) {
          ddls.add(ddl);
        }
      }
    }
    return ddls;
  }

  private String generateDdl(
      DatabaseMetaData meta,
      String tableCatalog,
      String tableSchema,
      String tableName,
      String tableType)
      throws SQLException {

    List<String> primaryKeys = getPrimaryKeys(meta, tableCatalog, tableSchema, tableName);
    Map<String, ForeignKey> foreignKeys =
        getForeignKeys(meta, tableCatalog, tableSchema, tableName);

    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE ").append(qualify(tableSchema, tableName)).append(" (\n");

    List<String> columnDefs = new ArrayList<>();
    try (ResultSet columns = meta.getColumns(tableCatalog, tableSchema, tableName, "%")) {
      while (columns.next()) {
        String colName = columns.getString("COLUMN_NAME");
        String typeName = columns.getString("TYPE_NAME");
        int size = columns.getInt("COLUMN_SIZE");
        int nullable = columns.getInt("NULLABLE");

        StringBuilder colDef = new StringBuilder("  ").append(colName).append(" ").append(typeName);
        if (needsSize(typeName) && size > 0) {
          colDef.append("(").append(size).append(")");
        }
        if (nullable == DatabaseMetaData.columnNoNulls) {
          colDef.append(" NOT NULL");
        }
        if (primaryKeys.contains(colName) && primaryKeys.size() == 1) {
          colDef.append(" PRIMARY KEY");
        }
        if (foreignKeys.containsKey(colName)) {
          ForeignKey fk = foreignKeys.get(colName);
          colDef
              .append(" REFERENCES ")
              .append(qualify(fk.schema, fk.table))
              .append("(")
              .append(fk.column)
              .append(")");
        }
        columnDefs.add(colDef.toString());
      }
    }

    if (primaryKeys.size() > 1) {
      columnDefs.add("  PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
    }

    sb.append(String.join(",\n", columnDefs));
    sb.append("\n);");

    if ("VIEW".equals(tableType)) {
      sb.insert(0, "-- VIEW\n");
    }

    return columnDefs.isEmpty() ? null : sb.toString();
  }

  private List<String> getPrimaryKeys(
      DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
    List<String> keys = new ArrayList<>();
    try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
      while (rs.next()) {
        keys.add(rs.getString("COLUMN_NAME"));
      }
    }
    return keys;
  }

  private Map<String, ForeignKey> getForeignKeys(
      DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
    Map<String, ForeignKey> fks = new LinkedHashMap<>();
    try (ResultSet rs = meta.getImportedKeys(catalog, schema, table)) {
      while (rs.next()) {
        String fkColumn = rs.getString("FKCOLUMN_NAME");
        String pkTable = rs.getString("PKTABLE_NAME");
        String pkSchema = rs.getString("PKTABLE_SCHEM");
        String pkColumn = rs.getString("PKCOLUMN_NAME");
        fks.put(fkColumn, new ForeignKey(pkSchema, pkTable, pkColumn));
      }
    }
    return fks;
  }

  private static String qualify(String schema, String table) {
    if (schema != null && !schema.isBlank()) {
      return schema + "." + table;
    }
    return table;
  }

  private static boolean needsSize(String typeName) {
    String upper = typeName.toUpperCase();
    return upper.contains("CHAR") || upper.contains("BINARY") || upper.contains("BIT");
  }

  private record ForeignKey(String schema, String table, String column) {}
}
