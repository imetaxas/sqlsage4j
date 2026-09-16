package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.ResponseFormat;
import org.junit.jupiter.api.Test;

final class SqlExtractorJsonTest {

  @Test
  void extractJson_validJsonWithSqlField() {
    String response =
        """
        {"sql": "SELECT COUNT(*) FROM users", "explanation": "Counts all users"}""";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).isEqualTo("SELECT COUNT(*) FROM users");
  }

  @Test
  void extractJson_nestedJsonWithWhitespace() {
    String response =
        """
        {
          "sql": "SELECT name FROM products ORDER BY price DESC LIMIT 5",
          "confidence": 0.92
        }""";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).isEqualTo("SELECT name FROM products ORDER BY price DESC LIMIT 5");
  }

  @Test
  void extractJson_nullSqlField_fallsBackToRegex() {
    String response =
        """
        {"sql": null, "error": "Cannot generate SQL"}""";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).contains("Cannot generate SQL");
  }

  @Test
  void extractJson_emptySqlField_fallsBackToRegex() {
    String response =
        """
        {"sql": "", "error": "empty"}""";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).contains("empty");
  }

  @Test
  void extractJson_noSqlField_fallsBackToRegex() {
    String response =
        """
        {"query": "SELECT 1", "note": "wrong field name"}""";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).contains("SELECT 1");
  }

  @Test
  void extractJson_invalidJson_fallsBackToRegex() {
    String response = "Here is your SQL: SELECT * FROM orders;";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).contains("SELECT * FROM orders;");
  }

  @Test
  void extractJson_textPrefixBeforeJson_fallsBackToRegex() {
    String response = "Sure! Here's the query: {\"sql\": \"SELECT 1\"}";

    String sql = SqlExtractor.extract(response, ResponseFormat.JSON);

    assertThat(sql).isNotEmpty();
  }

  @Test
  void extractText_defaultBehavior_unchanged() {
    String response = "The SQL is: SELECT MAX(price) FROM products;";

    String sql = SqlExtractor.extract(response, ResponseFormat.TEXT);

    assertThat(sql).isEqualTo("SELECT MAX(price) FROM products;");
  }

  @Test
  void extractText_singleArgOverload_usesTextMode() {
    String response = "SELECT 1;";

    String sql = SqlExtractor.extract(response);

    assertThat(sql).isEqualTo("SELECT 1;");
  }

  @Test
  void extractFromJson_directMethod_returnsNullForNonJson() {
    assertThat(SqlExtractor.extractFromJson("not json")).isNull();
    assertThat(SqlExtractor.extractFromJson("")).isNull();
    assertThat(SqlExtractor.extractFromJson("[]")).isNull();
  }
}
