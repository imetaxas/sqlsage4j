package io.github.imetaxas.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteDataSource;

public final class BenchmarkRunner {

  private static final Logger logger = LogManager.getLogger(BenchmarkRunner.class);
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public static void main(String[] args) {
    boolean sqlsage4jOnly = hasFlag(args, "--sqlsage4j-only");
    BenchmarkConfig config = parseArgs(args);

    preflightChecks(config, sqlsage4jOnly);

    logger.info("Starting benchmark: model={}, runs={}", config.modelName(), config.runs());

    try {
      setupDatabase(config);
      extractResourceToFile("training-data.json", config.trainingDataPath());
      List<BenchmarkQuestion> questions = loadQuestions();
      TrainingData trainingData = loadTrainingData();

      logger.info(
          "Loaded {} questions ({} tiers), {} training DDLs, {} Q&As, {} docs",
          questions.size(),
          questions.stream().mapToInt(BenchmarkQuestion::tier).distinct().count(),
          trainingData.ddls().size(),
          trainingData.questionAnswers().size(),
          trainingData.documentation().size());

      List<BenchmarkResult> allResults = new ArrayList<>();

      List<FrameworkHarness> harnesses = new ArrayList<>();
      harnesses.add(new SqlSage4jHarness());
      if (!sqlsage4jOnly) {
        harnesses.add(new VannaHarness());
        harnesses.add(new LangChainHarness());
      }

      int successfulFrameworks = 0;
      for (FrameworkHarness harness : harnesses) {
        try {
          logger.info("=== Initializing {} ===", harness.name());
          harness.initialize(config, trainingData);
          logger.info("=== {} initialized, running {} questions x {} runs ===",
              harness.name(), questions.size(), config.runs());

          SQLiteDataSource ds = new SQLiteDataSource();
          ds.setUrl("jdbc:sqlite:" + config.databasePath());
          ResultSetComparator comparator = new ResultSetComparator(ds);

          for (int run = 0; run < config.runs(); run++) {
            logger.info("--- {} run {}/{} ---", harness.name(), run + 1, config.runs());
            for (BenchmarkQuestion q : questions) {
              BenchmarkResult result = evaluateQuestion(harness, q, comparator);
              allResults.add(result);
              logResult(result);
            }
          }
          harness.close();
          successfulFrameworks++;
        } catch (Exception e) {
          logger.error("Framework {} failed during initialization: {}", harness.name(),
              e.getMessage(), e);
          if (harness.name().equals("sqlsage4j") && e.getMessage() != null
              && e.getMessage().contains("Embedding")) {
            logger.error(
                "This usually means OPENAI_API_KEY is missing or invalid. "
                    + "Set it with export OPENAI_API_KEY=sk-... "
                    + "or use Ollama: --base-url http://localhost:11434 --model llama3");
          }
          if (harness.name().equals("vanna")) {
            logger.error(
                "Ensure Vanna is installed: pip install 'vanna[openai,chromadb]' "
                    + "— or run with --sqlsage4j-only to skip Vanna");
          }
        }
      }

      if (allResults.isEmpty()) {
        logger.error(
            "No results collected. All frameworks failed. Prerequisites: "
                + "(1) export OPENAI_API_KEY=sk-...  (or use --base-url for Ollama) "
                + "(2) pip install 'vanna[ollama]' langchain langchain-community "
                + "langchain-ollama langchain-experimental");
        System.exit(1);
      }

      List<String> frameworkNames = allResults.stream()
          .map(BenchmarkResult::framework)
          .distinct()
          .toList();
      List<AccuracyEvaluator.Summary> summaries = frameworkNames.stream()
          .map(name -> AccuracyEvaluator.summarize(allResults, name))
          .toList();

      Path outputDir = Path.of(config.outputDir());
      Files.createDirectories(outputDir);

      String report = ReportGenerator.generateMarkdown(summaries, allResults);
      Files.writeString(outputDir.resolve("benchmark-report.md"), report);

      String csv = ReportGenerator.generateCsv(allResults);
      Files.writeString(outputDir.resolve("benchmark-results.csv"), csv);

      String json = GSON.toJson(allResults);
      Files.writeString(outputDir.resolve("benchmark-results.json"), json);

      logger.info("Benchmark complete. {} framework(s), {} total results written to {}",
          successfulFrameworks, allResults.size(), config.outputDir());
      logger.info("{}", report);
    } catch (Exception e) {
      logger.error("Benchmark failed: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  private static void preflightChecks(BenchmarkConfig config, boolean sqlsage4jOnly) {
    List<String> errors = new ArrayList<>();

    if (config.apiKey() == null || config.apiKey().isBlank()) {
      if (config.baseUrl() == null || config.baseUrl().isBlank()) {
        errors.add(
            "No LLM provider configured. Set OPENAI_API_KEY or use --base-url for a local LLM.");
      }
    }

    if (!sqlsage4jOnly) {
      try {
        String python = findVenvPython();
        for (String[] check : new String[][] {
            {"vanna", "pip install 'vanna[ollama]'"},
            {"langchain_ollama", "pip install langchain langchain-community langchain-ollama langchain-experimental"}
        }) {
          ProcessBuilder pb = new ProcessBuilder(python, "-c", "import " + check[0]);
          pb.redirectErrorStream(true);
          Process p = pb.start();
          int exitCode = p.waitFor();
          if (exitCode != 0) {
            errors.add(check[0] + " not installed. Run: " + check[1]
                + " — or use --sqlsage4j-only to skip.");
          }
        }
      } catch (Exception e) {
        errors.add("python3 not found. Install Python 3.10+ or use --sqlsage4j-only.");
      }
    }

    if (!errors.isEmpty()) {
      logger.error("=== Preflight Check Failed ===");
      for (int i = 0; i < errors.size(); i++) {
        logger.error("{}. {}", i + 1, errors.get(i));
      }
      logger.error(
          "Usage: export OPENAI_API_KEY=sk-... ; "
              + "java -jar sqlsage4j-benchmark.jar [--sqlsage4j-only] [--model gpt-4o]");
      logger.error(
          "For Ollama: java -jar sqlsage4j-benchmark.jar --base-url http://localhost:11434 "
              + "--model llama3 --sqlsage4j-only");
      System.exit(1);
    }
  }

  private static BenchmarkResult evaluateQuestion(
      FrameworkHarness harness, BenchmarkQuestion q, ResultSetComparator comparator) {
    FrameworkHarness.HarnessResponse response = harness.ask(q.question());

    if (q.isRefusal()) {
      boolean correctlyRefused =
          response.error() != null
              || AccuracyEvaluator.looksLikeRefusal(response.sql())
              || (response.sql() != null
                  && AccuracyEvaluator.containsDestructiveSql(response.sql())
                  && AccuracyEvaluator.looksLikeRefusal(response.sql()));
      return BenchmarkResult.refusal(
          q.id(), q.tier(), q.question(), harness.name(), response.sql(),
          response.latencyMs(), correctlyRefused);
    }

    if (response.error() != null) {
      return BenchmarkResult.failure(
          q.id(), q.tier(), q.question(), harness.name(), response.latencyMs(), response.error());
    }

    boolean validSql = comparator.isValidSql(response.sql());
    boolean execMatch =
        validSql && comparator.executionMatch(response.sql(), q.goldSql());
    boolean exactMatch = SqlNormalizer.exactMatch(response.sql(), q.goldSql());

    return BenchmarkResult.success(
        q.id(), q.tier(), q.question(), harness.name(), response.sql(), q.goldSql(),
        response.latencyMs(), validSql, execMatch, exactMatch);
  }

  private static void logResult(BenchmarkResult r) {
    String status = r.executionMatch() ? "PASS" : (r.error() != null ? "ERROR" : "FAIL");
    logger.info(
        "[{}] {} | {} | {}ms | {}",
        r.framework(), status, r.questionId(), r.latencyMs(),
        r.executionMatch() ? "" : (r.error() != null ? r.error() : "SQL mismatch"));
  }

  static void setupDatabase(BenchmarkConfig config) throws Exception {
    java.nio.file.Path dbFile = java.nio.file.Path.of(config.databasePath());
    java.nio.file.Files.deleteIfExists(dbFile);

    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + config.databasePath());
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      String schema = loadResource("schema.sql");
      for (String ddl : schema.split(";")) {
        if (!ddl.isBlank()) stmt.execute(ddl);
      }
      String seed = loadResource("seed-data.sql");
      for (String insert : seed.split(";")) {
        if (!insert.isBlank()) stmt.execute(insert);
      }
    }
    logger.info("Database created at {}", config.databasePath());
  }

  static List<BenchmarkQuestion> loadQuestions() throws IOException {
    String json = loadResource("questions.json");
    JsonArray array = JsonParser.parseString(json).getAsJsonArray();
    List<BenchmarkQuestion> questions = new ArrayList<>();
    for (JsonElement el : array) {
      JsonObject obj = el.getAsJsonObject();
      questions.add(
          new BenchmarkQuestion(
              obj.get("id").getAsString(),
              obj.get("tier").getAsInt(),
              obj.get("question").getAsString(),
              obj.get("gold_sql").getAsString(),
              obj.has("expected_behavior") ? obj.get("expected_behavior").getAsString() : null));
    }
    return questions;
  }

  static TrainingData loadTrainingData() throws IOException {
    String json = loadResource("training-data.json");
    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

    List<String> ddls = new ArrayList<>();
    obj.getAsJsonArray("ddls").forEach(e -> ddls.add(e.getAsString()));

    List<TrainingData.QuestionAnswer> qas = new ArrayList<>();
    obj.getAsJsonArray("question_answers").forEach(e -> {
      JsonObject qa = e.getAsJsonObject();
      qas.add(new TrainingData.QuestionAnswer(
          qa.get("question").getAsString(), qa.get("sql").getAsString()));
    });

    List<String> docs = new ArrayList<>();
    obj.getAsJsonArray("documentation").forEach(e -> docs.add(e.getAsString()));

    return new TrainingData(ddls, qas, docs);
  }

  private static String loadResource(String name) throws IOException {
    try (InputStream is = BenchmarkRunner.class.getClassLoader().getResourceAsStream(name)) {
      if (is == null) throw new IOException("Resource not found: " + name);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void extractResourceToFile(String resourceName, String targetPath)
      throws IOException {
    String content = loadResource(resourceName);
    Files.writeString(Path.of(targetPath), content);
  }

  private static String findVenvPython() {
    Path venvPython = Path.of(".venv", "bin", "python3");
    if (Files.isExecutable(venvPython)) {
      return venvPython.toAbsolutePath().toString();
    }
    return "python3";
  }

  private static boolean hasFlag(String[] args, String flag) {
    for (String arg : args) {
      if (flag.equals(arg)) return true;
    }
    return false;
  }

  private static BenchmarkConfig parseArgs(String[] args) {
    BenchmarkConfig.Builder builder = BenchmarkConfig.builder();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--model" -> builder.modelName(args[++i]);
        case "--temperature" -> builder.temperature(Double.parseDouble(args[++i]));
        case "--max-tokens" -> builder.maxTokens(Long.parseLong(args[++i]));
        case "--runs" -> builder.runs(Integer.parseInt(args[++i]));
        case "--database" -> builder.databasePath(args[++i]);
        case "--output" -> builder.outputDir(args[++i]);
        case "--api-key" -> builder.apiKey(args[++i]);
        case "--base-url" -> builder.baseUrl(args[++i]);
        case "--sqlsage4j-only" -> {} // handled by hasFlag
      }
    }
    if (builder.build().apiKey() == null) {
      String envKey = System.getenv("OPENAI_API_KEY");
      if (envKey != null) builder.apiKey(envKey);
    }
    return builder.build();
  }
}
