package io.github.imetaxas.benchmark;

import javax.annotation.Nullable;

public record BenchmarkResult(
    String questionId,
    int tier,
    String question,
    String framework,
    @Nullable String generatedSql,
    @Nullable String goldSql,
    long latencyMs,
    boolean validSql,
    boolean executionMatch,
    boolean exactMatch,
    @Nullable String error) {

  public static BenchmarkResult success(
      String questionId,
      int tier,
      String question,
      String framework,
      String generatedSql,
      String goldSql,
      long latencyMs,
      boolean validSql,
      boolean executionMatch,
      boolean exactMatch) {
    return new BenchmarkResult(
        questionId, tier, question, framework, generatedSql, goldSql, latencyMs, validSql,
        executionMatch, exactMatch, null);
  }

  public static BenchmarkResult failure(
      String questionId,
      int tier,
      String question,
      String framework,
      long latencyMs,
      String error) {
    return new BenchmarkResult(
        questionId, tier, question, framework, null, null, latencyMs, false, false, false, error);
  }

  public static BenchmarkResult refusal(
      String questionId,
      int tier,
      String question,
      String framework,
      @Nullable String generatedSql,
      long latencyMs,
      boolean correctlyRefused) {
    return new BenchmarkResult(
        questionId, tier, question, framework, generatedSql, "__REFUSE__", latencyMs, false,
        correctlyRefused, false, null);
  }
}
