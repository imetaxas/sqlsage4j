package io.github.imetaxas.benchmark;

/** Abstraction for a text-to-SQL framework under test. */
public interface FrameworkHarness extends AutoCloseable {

  String name();

  void initialize(BenchmarkConfig config, TrainingData trainingData);

  HarnessResponse ask(String question);

  record HarnessResponse(String sql, long latencyMs, String error) {
    public static HarnessResponse success(String sql, long latencyMs) {
      return new HarnessResponse(sql, latencyMs, null);
    }

    public static HarnessResponse failure(long latencyMs, String error) {
      return new HarnessResponse(null, latencyMs, error);
    }
  }
}
