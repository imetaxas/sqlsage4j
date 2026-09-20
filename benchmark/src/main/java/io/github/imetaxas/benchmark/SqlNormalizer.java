package io.github.imetaxas.benchmark;

import java.util.regex.Pattern;

/** Normalizes SQL for comparison: collapse whitespace, lowercase, strip trailing semicolons. */
public final class SqlNormalizer {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern TRAILING_SEMICOLONS = Pattern.compile(";+$");

  private SqlNormalizer() {}

  public static String normalize(String sql) {
    if (sql == null) return "";
    String result = sql.strip();
    result = TRAILING_SEMICOLONS.matcher(result).replaceAll("");
    result = WHITESPACE.matcher(result).replaceAll(" ");
    result = result.toLowerCase();
    return result.strip();
  }

  public static boolean exactMatch(String generated, String gold) {
    return normalize(generated).equals(normalize(gold));
  }
}
