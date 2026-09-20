package io.github.imetaxas.sqlsage4j;

import java.util.Objects;

public final class DDL implements Document {

  private final String ddl;

  public DDL(String ddl) {
    this.ddl = Objects.requireNonNull(ddl);
  }

  public String ddl() {
    return ddl;
  }

  @Override
  public String content() {
    return ddl;
  }

  @Override
  public String toString() {
    return ddl;
  }
}
