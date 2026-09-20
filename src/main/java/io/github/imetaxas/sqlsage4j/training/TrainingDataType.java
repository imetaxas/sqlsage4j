package io.github.imetaxas.sqlsage4j.training;

public enum TrainingDataType {
  DDL("ddl"),
  SQL_QA("sql"),
  DOCUMENTATION("documentation");

  private final String collectionName;

  TrainingDataType(String collectionName) {
    this.collectionName = collectionName;
  }

  public String collectionName() {
    return collectionName;
  }
}
