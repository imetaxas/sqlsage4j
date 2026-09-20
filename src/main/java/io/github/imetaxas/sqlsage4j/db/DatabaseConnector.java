package io.github.imetaxas.sqlsage4j.db;

/**
 * Abstraction for executing SQL against a database.
 *
 * <p>Implement this interface to add support for a new database. Built-in implementations cover
 * SQLite, PostgreSQL, MySQL, DuckDB, Snowflake, and BigQuery.
 *
 * <p>Connectors are used in two contexts:
 *
 * <ul>
 *   <li>Query execution — running LLM-generated SQL via {@link #runSql(String)}
 *   <li>Validation — checking SQL syntax before execution via {@link #isValidSql(String)}
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * SqlSage4j sage = SqlSage4j.builder(config)
 *     .databaseConnector(new SQLiteConnector("path/to/db.sqlite"))
 *     .prompt(prompt)
 *     .build();
 * }</pre>
 */
public interface DatabaseConnector {

  /**
   * Execute a SQL query and return the results as a {@link DataFrame}.
   *
   * @param sql the SQL statement to execute
   * @return query results with column names and rows
   * @throws RuntimeException if execution fails (e.g., syntax error, connection issue)
   */
  DataFrame runSql(String sql);

  /**
   * Check whether a SQL statement is syntactically valid without executing it. Typically uses the
   * database's {@code EXPLAIN} or equivalent mechanism.
   *
   * @param sql the SQL statement to validate
   * @return {@code true} if the statement is valid
   */
  boolean isValidSql(String sql);

  /**
   * Returns the SQL dialect name for this database (e.g., "SQLite", "PostgreSQL"). Used by the
   * prompt builder to guide the LLM toward dialect-specific syntax.
   */
  String dialect();
}
