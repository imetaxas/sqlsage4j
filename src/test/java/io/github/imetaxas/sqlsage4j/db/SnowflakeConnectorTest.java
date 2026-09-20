package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests SnowflakeConnector's dialect and validation logic. Since the Snowflake JDBC driver isn't
 * available in tests, we verify the class wiring and error paths without a live connection.
 */
final class SnowflakeConnectorTest {

  @Test
  void dialect_returnsSnowflakeSQL() {
    SnowflakeConnector connector = new SnowflakeConnector(new FakeDataSource());
    assertThat(connector.dialect()).isEqualTo("Snowflake SQL");
  }

  @Test
  void runSql_throwsWhenConnectionFails() {
    SnowflakeConnector connector = new SnowflakeConnector(new FakeDataSource());
    assertThatThrownBy(() -> connector.runSql("SELECT 1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Snowflake SQL");
  }

  @Test
  void isValidSql_returnsFalseWhenConnectionFails() {
    SnowflakeConnector connector = new SnowflakeConnector(new FakeDataSource());
    assertThat(connector.isValidSql("SELECT 1")).isFalse();
  }

  private static final class FakeDataSource implements javax.sql.DataSource {
    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
      throw new java.sql.SQLException("No Snowflake driver in test");
    }

    @Override
    public java.sql.Connection getConnection(String u, String p) throws java.sql.SQLException {
      throw new java.sql.SQLException("No Snowflake driver in test");
    }

    @Override
    public java.io.PrintWriter getLogWriter() {
      return null;
    }

    @Override
    public void setLogWriter(java.io.PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
      return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
      return java.util.logging.Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
      return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }
}
