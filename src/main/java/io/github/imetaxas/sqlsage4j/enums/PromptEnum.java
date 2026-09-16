package io.github.imetaxas.sqlsage4j.enums;

public enum PromptEnum {
  DATA_SCIENTIST("You are a data scientist expert. Help the user explore and understand data."),
  SYSTEM("You are a helpful assistant that answers questions about data."),
  ASSISTANT(""),
  SQL_EXPERT("You are a SQL expert. Generate precise, optimized SQL queries."),
  ANALYST("You are a data analyst. Focus on insights and actionable findings.");

  private final String text;

  PromptEnum(String text) {
    this.text = text;
  }

  public String getText() {
    return text;
  }
}
