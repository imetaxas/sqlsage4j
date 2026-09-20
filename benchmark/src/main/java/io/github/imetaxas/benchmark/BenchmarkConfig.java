package io.github.imetaxas.benchmark;

public record BenchmarkConfig(
    String modelName,
    double temperature,
    long maxTokens,
    int runs,
    String databasePath,
    String questionsPath,
    String trainingDataPath,
    String outputDir,
    String apiKey,
    String baseUrl) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String modelName = "gpt-4o";
    private double temperature = 0.0;
    private long maxTokens = 4096;
    private int runs = 1;
    private String databasePath = "benchmark.db";
    private String questionsPath = "questions.json";
    private String trainingDataPath = "training-data.json";
    private String outputDir = "results";
    private String apiKey;
    private String baseUrl;

    public Builder modelName(String v) { modelName = v; return this; }
    public Builder temperature(double v) { temperature = v; return this; }
    public Builder maxTokens(long v) { maxTokens = v; return this; }
    public Builder runs(int v) { runs = v; return this; }
    public Builder databasePath(String v) { databasePath = v; return this; }
    public Builder questionsPath(String v) { questionsPath = v; return this; }
    public Builder trainingDataPath(String v) { trainingDataPath = v; return this; }
    public Builder outputDir(String v) { outputDir = v; return this; }
    public Builder apiKey(String v) { apiKey = v; return this; }
    public Builder baseUrl(String v) { baseUrl = v; return this; }

    public BenchmarkConfig build() {
      return new BenchmarkConfig(
          modelName, temperature, maxTokens, runs, databasePath, questionsPath,
          trainingDataPath, outputDir, apiKey, baseUrl);
    }
  }
}
