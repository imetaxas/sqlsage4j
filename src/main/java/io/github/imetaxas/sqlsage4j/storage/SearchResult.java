package io.github.imetaxas.sqlsage4j.storage;

import java.util.Objects;

/** A training record matched by vector similarity search, with its relevance score. */
public final class SearchResult implements Comparable<SearchResult> {

  private final TrainingRecord record;
  private final double score;

  public SearchResult(TrainingRecord record, double score) {
    this.record = Objects.requireNonNull(record);
    this.score = score;
  }

  public TrainingRecord record() {
    return record;
  }

  public double score() {
    return score;
  }

  @Override
  public int compareTo(SearchResult other) {
    return Double.compare(other.score, this.score);
  }
}
