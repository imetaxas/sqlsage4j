package io.github.imetaxas.sqlsage4j;

import java.util.Objects;

public final class Ontology implements Document {

  private final String ontologyContent;

  public Ontology(String ontologyContent) {
    this.ontologyContent = Objects.requireNonNull(ontologyContent);
  }

  @Override
  public String content() {
    return ontologyContent;
  }

  @Override
  public String toString() {
    return ontologyContent;
  }
}
