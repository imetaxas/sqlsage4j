package com.spotify.queryfy;

/** Utility methods for constructing model objects from this library. */
public final class Queryfy {
  private Queryfy() {
    throw new IllegalAccessError("This utility class must not be instantiated");
  }

  /**
   * Creates a new {@link QueryfyArticle.Builder}.
   *
   * <p>This is equivalent to calling {@link QueryfyArticle#builder()}.
   */
  public static QueryfyArticle.Builder articleBuilder() {
    // This is an example utility function
    return QueryfyArticle.builder();
  }
}
