package io.github.imetaxas.sqlsage4j;

import java.util.Objects;
import javax.annotation.Nullable;

public final class SampleDocument implements Document {

  private final String documentContent;
  @Nullable private final String path;

  public SampleDocument(String documentContent) {
    this(documentContent, null);
  }

  public SampleDocument(String documentContent, @Nullable String path) {
    this.documentContent = Objects.requireNonNull(documentContent);
    this.path = path;
  }

  @Override
  public String content() {
    return documentContent;
  }

  @Nullable
  public String path() {
    return path;
  }

  @Override
  public String toString() {
    return path != null ? path + ": " + documentContent : documentContent;
  }
}
