package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class SqlGuardTest {

  private final SqlGuard guard = SqlGuard.readOnly();

  @Test
  void allowsSelectQueries() {
    assertThat(guard.check("SELECT * FROM users").blocked()).isFalse();
    assertThat(guard.check("select count(*) from orders").blocked()).isFalse();
    assertThat(guard.check("  SELECT id FROM t").blocked()).isFalse();
  }

  @Test
  void allowsWithCteQueries() {
    assertThat(guard.check("WITH cte AS (SELECT 1) SELECT * FROM cte").blocked()).isFalse();
  }

  @Test
  void allowsExplainQueries() {
    assertThat(guard.check("EXPLAIN SELECT * FROM users").blocked()).isFalse();
  }

  @Test
  void blocksDeleteStatements() {
    SqlGuard.Result result = guard.check("DELETE FROM users");
    assertThat(result.blocked()).isTrue();
    assertThat(result.reason()).contains("Blocked keyword");
  }

  @Test
  void blocksDropStatements() {
    SqlGuard.Result result = guard.check("DROP TABLE users");
    assertThat(result.blocked()).isTrue();
    assertThat(result.reason()).contains("DROP");
  }

  @Test
  void blocksInsertStatements() {
    SqlGuard.Result result = guard.check("INSERT INTO users VALUES (1, 'x')");
    assertThat(result.blocked()).isTrue();
  }

  @Test
  void blocksUpdateStatements() {
    assertThat(guard.check("UPDATE users SET name = 'x'").blocked()).isTrue();
  }

  @Test
  void blocksTruncateStatements() {
    assertThat(guard.check("TRUNCATE TABLE orders").blocked()).isTrue();
  }

  @Test
  void blocksAlterStatements() {
    assertThat(guard.check("ALTER TABLE users ADD col INT").blocked()).isTrue();
  }

  @Test
  void blocksGrantRevoke() {
    assertThat(guard.check("GRANT SELECT ON users TO public").blocked()).isTrue();
    assertThat(guard.check("REVOKE ALL ON users FROM public").blocked()).isTrue();
  }

  @Test
  void blocksMerge() {
    assertThat(guard.check("MERGE INTO target USING source ON (1=1)").blocked()).isTrue();
  }

  @Test
  void blocksExec() {
    assertThat(guard.check("EXEC sp_delete_all").blocked()).isTrue();
  }

  @Test
  void blocksMultipleStatements() {
    SqlGuard.Result result = guard.check("SELECT 1; DROP TABLE users");
    assertThat(result.blocked()).isTrue();
    assertThat(result.reason()).contains("Multiple statements");
  }

  @Test
  void allowsSemicolonAtEnd() {
    assertThat(guard.check("SELECT * FROM users;").blocked()).isFalse();
  }

  @Test
  void allowsSemicolonInsideString() {
    assertThat(guard.check("SELECT * FROM users WHERE name = 'a;b'").blocked()).isFalse();
  }

  @Test
  void allowAllPolicyPermitsEverything() {
    SqlGuard permissive = SqlGuard.allowAll();
    assertThat(permissive.check("DROP TABLE users").blocked()).isFalse();
    assertThat(permissive.check("DELETE FROM orders").blocked()).isFalse();
  }

  @Test
  void nullAndBlankAreAllowed() {
    assertThat(guard.check(null).blocked()).isFalse();
    assertThat(guard.check("").blocked()).isFalse();
    assertThat(guard.check("   ").blocked()).isFalse();
  }

  @Test
  void blocksUnknownStatementTypes() {
    SqlGuard.Result result = guard.check("CALL my_procedure()");
    assertThat(result.blocked()).isTrue();
  }

  @Test
  void caseInsensitive() {
    assertThat(guard.check("delete from users").blocked()).isTrue();
    assertThat(guard.check("Drop Table x").blocked()).isTrue();
  }
}
