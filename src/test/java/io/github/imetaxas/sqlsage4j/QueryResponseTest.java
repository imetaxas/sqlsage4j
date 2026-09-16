package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class QueryResponseTest {

  @Test
  void success_isSuccessReturnsTrue() {
    QueryResponse r = QueryResponse.success("id1", "How many?", "SELECT COUNT(*) FROM t", "raw");

    assertThat(r.isSuccess()).isTrue();
    assertThat(r.id()).isEqualTo("id1");
    assertThat(r.question()).isEqualTo("How many?");
    assertThat(r.sql()).isEqualTo("SELECT COUNT(*) FROM t");
    assertThat(r.rawLlmResponse()).isEqualTo("raw");
    assertThat(r.error()).isNull();
  }

  @Test
  void error_isSuccessReturnsFalse() {
    QueryResponse r = QueryResponse.error("id2", "Bad question", "LLM failed");

    assertThat(r.isSuccess()).isFalse();
    assertThat(r.id()).isEqualTo("id2");
    assertThat(r.question()).isEqualTo("Bad question");
    assertThat(r.sql()).isNull();
    assertThat(r.rawLlmResponse()).isNull();
    assertThat(r.error()).isEqualTo("LLM failed");
  }

  @Test
  void success_withNullRawResponse_isStillSuccess() {
    QueryResponse r = QueryResponse.success("id3", "Q", "SELECT 1", null);

    assertThat(r.isSuccess()).isTrue();
    assertThat(r.rawLlmResponse()).isNull();
  }

  @Test
  void error_withEmptyMessage() {
    QueryResponse r = QueryResponse.error("id4", "Q", "");

    assertThat(r.isSuccess()).isFalse();
    assertThat(r.error()).isEqualTo("");
  }

  @Test
  void equality_twoSuccessWithSameFields_areEqual() {
    QueryResponse a = QueryResponse.success("x", "Q", "SELECT 1", "raw");
    QueryResponse b = QueryResponse.success("x", "Q", "SELECT 1", "raw");

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void equality_successAndError_areNotEqual() {
    QueryResponse success = QueryResponse.success("x", "Q", "SELECT 1", "raw");
    QueryResponse error = QueryResponse.error("x", "Q", "fail");

    assertThat(success).isNotEqualTo(error);
  }

  @Test
  void toString_containsFieldValues() {
    QueryResponse r = QueryResponse.success("id5", "test question", "SELECT 1", "response");
    String str = r.toString();

    assertThat(str).contains("id5");
    assertThat(str).contains("test question");
    assertThat(str).contains("SELECT 1");
  }
}
