package io.github.imetaxas.sqlsage4j.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.imetaxas.sqlsage4j.client.ResponseFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Extracts SQL from LLM responses. Ported from Vanna's extract_sql — uses the same regex chain. */
public final class SqlExtractor {

  private static final Gson GSON = new Gson();

  private static final List<Pattern> EXTRACTION_PATTERNS =
      List.of(
          Pattern.compile("\\bWITH\\b\\s.*?;", Pattern.DOTALL),
          Pattern.compile("SELECT.*?;", Pattern.DOTALL),
          Pattern.compile("```sql\\n(.*?)```", Pattern.DOTALL),
          Pattern.compile("```(.*?)```", Pattern.DOTALL));

  private SqlExtractor() {}

  public static String extract(String llmResponse) {
    return extract(llmResponse, ResponseFormat.TEXT);
  }

  /**
   * Extracts SQL from an LLM response using the given format strategy.
   *
   * <p>When {@link ResponseFormat#JSON} is used, expects a JSON object with a {@code "sql"} field.
   * Falls back to regex extraction if JSON parsing fails.
   */
  public static String extract(String llmResponse, ResponseFormat format) {
    if (format == ResponseFormat.JSON) {
      String jsonResult = extractFromJson(llmResponse);
      if (jsonResult != null) return jsonResult;
    }
    return extractWithRegex(llmResponse);
  }

  @Nullable
  static String extractFromJson(String response) {
    try {
      String trimmed = response.trim();
      if (!trimmed.startsWith("{")) return null;
      JsonObject obj = GSON.fromJson(trimmed, JsonObject.class);
      if (obj.has("sql") && !obj.get("sql").isJsonNull()) {
        String sql = obj.get("sql").getAsString().trim();
        return sql.isEmpty() ? null : sql;
      }
      return null;
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  private static String extractWithRegex(String llmResponse) {
    for (Pattern pattern : EXTRACTION_PATTERNS) {
      Matcher matcher = pattern.matcher(llmResponse);
      String lastMatch = null;
      while (matcher.find()) {
        lastMatch = matcher.group(matcher.groupCount() > 0 ? 1 : 0);
      }
      if (lastMatch != null) {
        return lastMatch.trim();
      }
    }
    return llmResponse.trim();
  }

  public static boolean containsIntermediateSql(String llmResponse) {
    return llmResponse.contains("intermediate_sql");
  }
}
