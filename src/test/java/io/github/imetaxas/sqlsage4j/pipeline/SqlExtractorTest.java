package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class SqlExtractorTest {

  @Test
  void extractsSimpleSelect() {
    String response = "Here is the SQL query:\nSELECT * FROM users WHERE id = 1;";
    assertThat(SqlExtractor.extract(response)).isEqualTo("SELECT * FROM users WHERE id = 1;");
  }

  @Test
  void extractsCte() {
    String response = "WITH cte AS (SELECT id FROM users) SELECT * FROM cte WHERE id > 10;";
    assertThat(SqlExtractor.extract(response))
        .isEqualTo("WITH cte AS (SELECT id FROM users) SELECT * FROM cte WHERE id > 10;");
  }

  @Test
  void extractsFromMarkdownCodeBlock() {
    String response = "```sql\nSELECT count(*) FROM orders\n```";
    assertThat(SqlExtractor.extract(response)).isEqualTo("SELECT count(*) FROM orders");
  }

  @Test
  void extractsFromGenericCodeBlock() {
    String response = "```\nSELECT 1\n```";
    assertThat(SqlExtractor.extract(response)).isEqualTo("SELECT 1");
  }

  @Test
  void fallsBackToRawResponse() {
    String response = "I cannot generate SQL for this question.";
    assertThat(SqlExtractor.extract(response)).isEqualTo(response);
  }

  @Test
  void extractsLastMatchWhenMultiple() {
    String response = "SELECT 1; and also SELECT 2;";
    assertThat(SqlExtractor.extract(response)).isEqualTo("SELECT 2;");
  }

  @Test
  void detectsIntermediateSql() {
    assertThat(SqlExtractor.containsIntermediateSql("-- intermediate_sql\nSELECT ...")).isTrue();
    assertThat(SqlExtractor.containsIntermediateSql("SELECT * FROM users;")).isFalse();
  }
}
