package io.github.imetaxas.sqlsage4j.pipeline;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * SQL safety guardrail that validates generated SQL before execution. Prevents destructive
 * operations (INSERT, UPDATE, DELETE, DROP, etc.) from reaching the database.
 *
 * <pre>{@code
 * SqlGuard guard = SqlGuard.readOnly();
 * SqlGuard.Result result = guard.check("DROP TABLE users;");
 * // result.blocked() == true
 * // result.reason() == "Blocked keyword: DROP"
 * }</pre>
 */
public final class SqlGuard {

  /** Policy that blocks all write/DDL operations — only SELECT and WITH...SELECT are allowed. */
  public static final SqlGuard READ_ONLY = new SqlGuard(Policy.BLOCK_WRITES);

  /** Policy that allows everything — no validation is performed. */
  public static final SqlGuard ALLOW_ALL = new SqlGuard(Policy.ALLOW_ALL);

  private static final Set<String> BLOCKED_KEYWORDS =
      Set.of(
          "INSERT",
          "UPDATE",
          "DELETE",
          "DROP",
          "TRUNCATE",
          "ALTER",
          "CREATE",
          "GRANT",
          "REVOKE",
          "EXEC",
          "EXECUTE",
          "CALL",
          "MERGE",
          "UPSERT");

  private static final Pattern STATEMENT_START =
      Pattern.compile("^\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

  private static final Set<String> ALLOWED_STARTERS =
      Set.of("SELECT", "WITH", "EXPLAIN", "SHOW", "DESCRIBE", "DESC");

  private final Policy policy;

  private SqlGuard(Policy policy) {
    this.policy = policy;
  }

  /** Creates a read-only guard (blocks all writes). */
  public static SqlGuard readOnly() {
    return READ_ONLY;
  }

  /** Creates a permissive guard (allows everything). */
  public static SqlGuard allowAll() {
    return ALLOW_ALL;
  }

  /** Checks whether the SQL is safe to execute under this guard's policy. */
  public Result check(@Nullable String sql) {
    if (policy == Policy.ALLOW_ALL) {
      return Result.ALLOWED;
    }
    if (sql == null || sql.isBlank()) {
      return Result.allowed();
    }

    String normalized = sql.strip().toUpperCase(Locale.ROOT);

    for (String keyword : BLOCKED_KEYWORDS) {
      if (startsWithKeyword(normalized, keyword)) {
        return Result.blocked("Blocked keyword: " + keyword);
      }
    }

    var matcher = STATEMENT_START.matcher(normalized);
    if (matcher.find()) {
      String firstWord = matcher.group(1);
      if (!ALLOWED_STARTERS.contains(firstWord)) {
        return Result.blocked("Statement type not allowed: " + firstWord);
      }
    }

    if (containsMultipleStatements(normalized)) {
      return Result.blocked("Multiple statements detected (possible injection)");
    }

    return Result.allowed();
  }

  private static boolean startsWithKeyword(String normalized, String keyword) {
    if (normalized.startsWith(keyword)) {
      return normalized.length() == keyword.length()
          || !Character.isLetterOrDigit(normalized.charAt(keyword.length()));
    }
    return false;
  }

  private static boolean containsMultipleStatements(String sql) {
    boolean inString = false;
    char stringChar = 0;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (inString) {
        if (c == stringChar) inString = false;
      } else if (c == '\'' || c == '"') {
        inString = true;
        stringChar = c;
      } else if (c == ';' && i < sql.length() - 1) {
        String remainder = sql.substring(i + 1).strip();
        if (!remainder.isEmpty()) return true;
      }
    }
    return false;
  }

  public enum Policy {
    BLOCK_WRITES,
    ALLOW_ALL
  }

  public record Result(boolean blocked, @Nullable String reason) {
    static final Result ALLOWED = new Result(false, null);

    static Result allowed() {
      return ALLOWED;
    }

    static Result blocked(String reason) {
      return new Result(true, reason);
    }
  }
}
