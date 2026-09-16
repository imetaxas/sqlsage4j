package io.github.imetaxas.sqlsage4j.diff;

import io.github.imetaxas.sqlsage4j.QueryResponse;
import java.util.List;

/** Holds the outcomes of an {@link LLMDiff} comparison for a single question. */
public final class DiffResult {

  private final String question;
  private final List<QueryResponse> responsesA;
  private final List<QueryResponse> responsesB;
  private final int totalRuns;

  public DiffResult(
      String question,
      List<QueryResponse> responsesA,
      List<QueryResponse> responsesB,
      int totalRuns) {
    this.question = question;
    this.responsesA = List.copyOf(responsesA);
    this.responsesB = List.copyOf(responsesB);
    this.totalRuns = totalRuns;
  }

  public String question() {
    return question;
  }

  public List<QueryResponse> responsesA() {
    return responsesA;
  }

  public List<QueryResponse> responsesB() {
    return responsesB;
  }

  public int totalRuns() {
    return totalRuns;
  }

  public long successCountA() {
    return responsesA.stream().filter(QueryResponse::isSuccess).count();
  }

  public long successCountB() {
    return responsesB.stream().filter(QueryResponse::isSuccess).count();
  }

  /** Count how many runs produced identical SQL across both configs. */
  public long matchingCount() {
    long matches = 0;
    int limit = Math.min(responsesA.size(), responsesB.size());
    for (int i = 0; i < limit; i++) {
      String sqlA = responsesA.get(i).sql();
      String sqlB = responsesB.get(i).sql();
      if (sqlA != null && sqlA.equals(sqlB)) {
        matches++;
      }
    }
    return matches;
  }

  @Override
  public String toString() {
    return String.format(
        "DiffResult{question='%s', runs=%d, successA=%d, successB=%d, matching=%d}",
        question, totalRuns, successCountA(), successCountB(), matchingCount());
  }
}
