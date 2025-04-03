package com.spotify.queryfy;

import com.google.auto.value.AutoValue;

/** An example article model class. */
@AutoValue
public abstract class QueryfyArticle {
  QueryfyArticle() {
    // Prevent users outside of this package calling the constructor.
  }

  /**
   * The article's human-readable title.
   *
   * <p>The title is in plain text and does not support any mark-up.
   */
  public abstract String title();

  /** Converts this instance into a builder pre-seeded with this instance's values. */
  public abstract Builder toBuilder();

  /** Creates a new {@link QueryfyArticle.Builder} for a {@link QueryfyArticle}. */
  public static Builder builder() {
    return new AutoValue_QueryfyArticle.Builder();
  }

  /** A builder for a {@link QueryfyArticle}. */
  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {
      // Prevent users outside of this package calling the constructor.
    }

    /**
     * Sets the title of the resulting {@link QueryfyArticle}.
     *
     * @param title the desired title
     * @return this builder
     */
    public abstract Builder title(final String title);

    /**
     * Builds a {@link QueryfyArticle} out of this builder.
     *
     * <p>This is safe to be called several times on the same builder.
     */
    public abstract QueryfyArticle build();
  }
}
