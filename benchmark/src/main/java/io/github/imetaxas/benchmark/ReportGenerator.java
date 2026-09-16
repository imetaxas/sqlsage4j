package io.github.imetaxas.benchmark;

import java.util.Comparator;
import java.util.List;

public final class ReportGenerator {

  private ReportGenerator() {}

  public static String generateMarkdown(
      List<AccuracyEvaluator.Summary> summaries, List<BenchmarkResult> allResults) {

    StringBuilder sb = new StringBuilder();
    String title =
        summaries.stream().map(AccuracyEvaluator.Summary::framework).reduce((a, b) -> a + " vs " + b).orElse("Benchmark");
    sb.append("# Benchmark Report: ").append(title).append("\n\n");

    sb.append("## Summary\n\n");
    sb.append("| Metric |");
    for (AccuracyEvaluator.Summary s : summaries) {
      sb.append(" ").append(s.framework()).append(" |");
    }
    sb.append(" Winner |\n|---|");
    for (int i = 0; i < summaries.size(); i++) sb.append("---|");
    sb.append("---|\n");

    appendMetricRow(sb, "Execution Accuracy", summaries,
        s -> fmt(s.executionAccuracy()), s -> s.executionAccuracy(), true);
    appendMetricRow(sb, "Exact Match Rate", summaries,
        s -> fmt(s.exactMatchRate()), s -> s.exactMatchRate(), true);
    appendMetricRow(sb, "Valid SQL Rate", summaries,
        s -> fmt(s.validSqlRate()), s -> s.validSqlRate(), true);
    appendMetricRow(sb, "Errors", summaries,
        s -> String.valueOf(s.errors()), s -> (double) s.errors(), false);
    appendMetricRow(sb, "Avg Latency (ms)", summaries,
        s -> String.format("%.0f", s.avgLatencyMs()), s -> s.avgLatencyMs(), false);
    appendMetricRow(sb, "P50 Latency (ms)", summaries,
        s -> String.valueOf(s.p50LatencyMs()), s -> (double) s.p50LatencyMs(), false);
    appendMetricRow(sb, "P95 Latency (ms)", summaries,
        s -> String.valueOf(s.p95LatencyMs()), s -> (double) s.p95LatencyMs(), false);
    appendMetricRow(sb, "P99 Latency (ms)", summaries,
        s -> String.valueOf(s.p99LatencyMs()), s -> (double) s.p99LatencyMs(), false);

    sb.append("\n## Accuracy by Tier\n\n");
    sb.append("| Tier |");
    for (AccuracyEvaluator.Summary s : summaries) {
      sb.append(" ").append(s.framework()).append(" |");
    }
    sb.append("\n|---|");
    for (int i = 0; i < summaries.size(); i++) sb.append("---|");
    sb.append("\n");
    for (int tier = 1; tier <= 5; tier++) {
      String tierLabel = switch (tier) {
        case 1 -> "Tier 1 (Simple)";
        case 2 -> "Tier 2 (Moderate)";
        case 3 -> "Tier 3 (Complex)";
        case 4 -> "Tier 4 (Advanced)";
        case 5 -> "Tier 5 (Adversarial)";
        default -> "Tier " + tier;
      };
      sb.append("| ").append(tierLabel).append(" |");
      for (AccuracyEvaluator.Summary s : summaries) {
        double val = s.executionAccuracyByTier().getOrDefault(tier, 0.0);
        sb.append(" ").append(fmt(val)).append(" |");
      }
      sb.append("\n");
    }

    sb.append("\n## Per-Question Detail\n\n");
    sb.append("| ID | Tier | Question | Framework | SQL Valid | Exec Match | Latency |\n");
    sb.append("|---|---|---|---|---|---|---|\n");
    for (BenchmarkResult r : allResults) {
      String q = r.question().length() > 50
          ? r.question().substring(0, 47) + "..." : r.question();
      sb.append("| ").append(r.questionId())
          .append(" | ").append(r.tier())
          .append(" | ").append(q)
          .append(" | ").append(r.framework())
          .append(" | ").append(r.validSql() ? "Yes" : "No")
          .append(" | ").append(r.executionMatch() ? "PASS" : "FAIL")
          .append(" | ").append(r.latencyMs()).append("ms")
          .append(" |\n");
    }

    return sb.toString();
  }

  public static String generateCsv(List<BenchmarkResult> results) {
    StringBuilder sb = new StringBuilder();
    sb.append("question_id,tier,question,framework,generated_sql,gold_sql,")
        .append("latency_ms,valid_sql,execution_match,exact_match,error\n");
    for (BenchmarkResult r : results) {
      sb.append(csvEscape(r.questionId())).append(",");
      sb.append(r.tier()).append(",");
      sb.append(csvEscape(r.question())).append(",");
      sb.append(csvEscape(r.framework())).append(",");
      sb.append(csvEscape(r.generatedSql())).append(",");
      sb.append(csvEscape(r.goldSql())).append(",");
      sb.append(r.latencyMs()).append(",");
      sb.append(r.validSql()).append(",");
      sb.append(r.executionMatch()).append(",");
      sb.append(r.exactMatch()).append(",");
      sb.append(csvEscape(r.error())).append("\n");
    }
    return sb.toString();
  }

  private static void appendMetricRow(
      StringBuilder sb,
      String metric,
      List<AccuracyEvaluator.Summary> summaries,
      java.util.function.Function<AccuracyEvaluator.Summary, String> formatter,
      java.util.function.Function<AccuracyEvaluator.Summary, Double> valueExtractor,
      boolean higherIsBetter) {
    sb.append("| ").append(metric).append(" |");
    for (AccuracyEvaluator.Summary s : summaries) {
      sb.append(" ").append(formatter.apply(s)).append(" |");
    }
    sb.append(" ").append(findWinner(summaries, valueExtractor, higherIsBetter)).append(" |\n");
  }

  private static String findWinner(
      List<AccuracyEvaluator.Summary> summaries,
      java.util.function.Function<AccuracyEvaluator.Summary, Double> valueExtractor,
      boolean higherIsBetter) {
    if (summaries.size() < 2) return summaries.get(0).framework();

    AccuracyEvaluator.Summary best = summaries.stream()
        .min(higherIsBetter
            ? Comparator.comparingDouble((AccuracyEvaluator.Summary s) -> valueExtractor.apply(s)).reversed()
            : Comparator.comparingDouble(s -> valueExtractor.apply(s)))
        .orElse(summaries.get(0));

    long countAtBest = summaries.stream()
        .filter(s -> Math.abs(valueExtractor.apply(s) - valueExtractor.apply(best)) < 0.01)
        .count();

    if (countAtBest == summaries.size()) return "Tie";
    return best.framework();
  }

  private static String fmt(double pct) {
    return String.format("%.1f%%", pct);
  }

  private static String csvEscape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
