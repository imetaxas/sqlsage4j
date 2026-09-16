package io.github.imetaxas.sqlsage4j;

import java.util.Objects;

public final class QuestionAnswer implements Document {

  private final String question;
  private final String sql;

  public QuestionAnswer(String question, String sql) {
    this.question = Objects.requireNonNull(question);
    this.sql = Objects.requireNonNull(sql);
  }

  public String question() {
    return question;
  }

  public String sql() {
    return sql;
  }

  @Override
  public String content() {
    return question + "\n" + sql;
  }

  @Override
  public String toString() {
    return "Q: " + question + "\nSQL: " + sql;
  }
}
