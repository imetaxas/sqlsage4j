package io.github.imetaxas.sqlsage4j.db;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

final class DataFrameTest {

  @Test
  void basicProperties() {
    DataFrame df =
        new DataFrame(List.of("id", "name"), List.of(List.of(1, "Alice"), List.of(2, "Bob")));

    assertThat(df.columns()).containsExactly("id", "name");
    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.columnCount()).isEqualTo(2);
    assertThat(df.isEmpty()).isFalse();
  }

  @Test
  void emptyDataFrame() {
    DataFrame df = DataFrame.empty();
    assertThat(df.isEmpty()).isTrue();
    assertThat(df.rowCount()).isEqualTo(0);
    assertThat(df.columnCount()).isEqualTo(0);
  }

  @Test
  void head() {
    DataFrame df = new DataFrame(List.of("x"), List.of(List.of(1), List.of(2), List.of(3)));
    DataFrame head = df.head(2);
    assertThat(head.rowCount()).isEqualTo(2);
    assertThat(head.rows().get(0).get(0)).as("first cell").isEqualTo(1);
  }

  @Test
  void toMarkdown() {
    DataFrame df = new DataFrame(List.of("id", "name"), List.of(List.of(1, "Alice")));
    String md = df.toMarkdown();
    assertThat(md).contains("| id | name |");
    assertThat(md).contains("| 1 | Alice |");
  }

  @Test
  void toCsv() {
    DataFrame df = new DataFrame(List.of("a", "b"), List.of(List.of(1, 2)));
    String csv = df.toCsv();
    assertThat(csv).contains("a,b");
    assertThat(csv).contains("1,2");
  }

  @Test
  void toJson() {
    DataFrame df = new DataFrame(List.of("id"), List.of(List.of(42)));
    String json = df.toJson();
    assertThat(json).contains("\"id\":42");
  }

  @Test
  void immutable() {
    List<String> cols = new java.util.ArrayList<>(List.of("a"));
    List<List<Object>> rows =
        new java.util.ArrayList<>(List.of(new java.util.ArrayList<>(List.of(1))));
    DataFrame df = new DataFrame(cols, rows);

    cols.add("b");
    rows.add(List.of(2));

    assertThat(df.columnCount()).isEqualTo(1);
    assertThat(df.rowCount()).isEqualTo(1);
  }
}
