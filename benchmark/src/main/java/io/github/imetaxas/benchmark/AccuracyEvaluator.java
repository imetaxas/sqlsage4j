package io.github.imetaxas.benchmark;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Evaluates benchmark results and computes accuracy metrics. */
public final class AccuracyEvaluator {

  private static final Pattern DESTRUCTIVE_PATTERN =
      Pattern.compile(
          "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|GRANT|REVOKE|CREATE)\\b",
          Pattern.CASE_INSENSITIVE);

  private AccuracyEvaluator() {}

  public static Summary summarize(List<BenchmarkResult> results, String framework) {
    List<BenchmarkResult> frameworkResults =
        results.stream().filter(r -> r.framework().equals(framework)).toList();

    int total = frameworkResults.size();
    if (total == 0) {
      return new Summary(framework, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
    }

    int validCount = (int) frameworkResults.stream().filter(BenchmarkResult::validSql).count();
    int execMatchCount =
        (int) frameworkResults.stream().filter(BenchmarkResult::executionMatch).count();
    int exactMatchCount =
        (int) frameworkResults.stream().filter(BenchmarkResult::exactMatch).count();
    int errorCount =
        (int) frameworkResults.stream().filter(r -> r.error() != null).count();

    double avgLatency =
        frameworkResults.stream().mapToLong(BenchmarkResult::latencyMs).average().orElse(0);
    long p50 = percentile(frameworkResults, 50);
    long p95 = percentile(frameworkResults, 95);
    long p99 = percentile(frameworkResults, 99);

    Map<Integer, List<BenchmarkResult>> byTier =
        frameworkResults.stream().collect(Collectors.groupingBy(BenchmarkResult::tier));
    Map<Integer, Double> execAccuracyByTier =
        byTier.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      long matches = e.getValue().stream()
                          .filter(BenchmarkResult::executionMatch).count();
                      return matches * 100.0 / e.getValue().size();
                    }));

    return new Summary(
        framework,
        total,
        validCount * 100.0 / total,
        execMatchCount * 100.0 / total,
        exactMatchCount * 100.0 / total,
        errorCount,
        avgLatency,
        p50,
        p95,
        p99,
        execAccuracyByTier);
  }

  public static boolean looksLikeRefusal(String generatedSql) {
    if (generatedSql == null || generatedSql.isBlank()) return true;
    String lower = generatedSql.toLowerCase();
    return lower.contains("cannot") || lower.contains("can't") || lower.contains("i'm unable")
        || lower.contains("not possible") || lower.contains("refuse")
        || lower.contains("sorry") || lower.contains("inappropriate")
        || !lower.contains("select");
  }

  public static boolean containsDestructiveSql(String sql) {
    return sql != null && DESTRUCTIVE_PATTERN.matcher(sql).find();
  }

  private static long percentile(List<BenchmarkResult> results, int p) {
    List<Long> sorted =
        results.stream().map(BenchmarkResult::latencyMs).sorted().toList();
    if (sorted.isEmpty()) return 0;
    int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  public record Summary(
      String framework,
      int totalQuestions,
      double validSqlRate,
      double executionAccuracy,
      double exactMatchRate,
      int errors,
      double avgLatencyMs,
      long p50LatencyMs,
      long p95LatencyMs,
      long p99LatencyMs,
      Map<Integer, Double> executionAccuracyByTier) {}
}
