package io.github.imetaxas.benchmark;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base harness for Python-based text-to-SQL frameworks. Communicates with a Python subprocess via
 * JSON over stdin/stdout. Subclasses only need to provide the framework name and script path.
 */
public abstract class PythonHarness implements FrameworkHarness {

  private static final Logger logger = LogManager.getLogger(PythonHarness.class);
  private static final Gson GSON = new Gson();

  private Process process;
  private PrintWriter toProcess;
  private BufferedReader fromProcess;

  protected abstract String scriptPath();

  @Override
  public void initialize(BenchmarkConfig config, TrainingData trainingData) {
    try {
      String python = findPython();
      ProcessBuilder pb =
          new ProcessBuilder(
              python,
              scriptPath(),
              "--model", config.modelName(),
              "--temperature", String.valueOf(config.temperature()),
              "--max-tokens", String.valueOf(config.maxTokens()),
              "--database", config.databasePath(),
              "--training-data", config.trainingDataPath());

      if (config.apiKey() != null) {
        pb.environment().put("OPENAI_API_KEY", config.apiKey());
      }
      if (config.baseUrl() != null) {
        pb.environment().put("OPENAI_BASE_URL", config.baseUrl());
      }

      pb.redirectErrorStream(false);
      process = pb.start();

      toProcess =
          new PrintWriter(
              new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
      fromProcess =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

      String readyLine = fromProcess.readLine();
      if (readyLine == null || !readyLine.contains("ready")) {
        String stderr = drainStderr();
        throw new RuntimeException(
            name() + " harness did not signal ready: " + readyLine
                + (stderr.isEmpty() ? "" : "\nstderr: " + stderr));
      }

      logger.info("{} harness started: python={}, model={}", name(), python, config.modelName());
    } catch (Exception e) {
      throw new RuntimeException("Failed to start " + name() + " harness: " + e.getMessage(), e);
    }
  }

  @Override
  public HarnessResponse ask(String question) {
    try {
      JsonObject request = new JsonObject();
      request.addProperty("action", "ask");
      request.addProperty("question", question);
      toProcess.println(GSON.toJson(request));

      String responseLine = fromProcess.readLine();
      if (responseLine == null) {
        return HarnessResponse.failure(0, name() + " process terminated unexpectedly");
      }

      JsonObject resp = GSON.fromJson(responseLine, JsonObject.class);
      long latency = resp.has("latency_ms") ? resp.get("latency_ms").getAsLong() : 0;

      if (resp.has("error")) {
        return HarnessResponse.failure(latency, resp.get("error").getAsString());
      }
      String sql = resp.has("sql") ? resp.get("sql").getAsString() : null;
      return HarnessResponse.success(sql, latency);
    } catch (Exception e) {
      return HarnessResponse.failure(0, name() + " communication error: " + e.getMessage());
    }
  }

  @Override
  public void close() {
    try {
      if (toProcess != null) {
        JsonObject quit = new JsonObject();
        quit.addProperty("action", "quit");
        toProcess.println(GSON.toJson(quit));
        toProcess.close();
      }
      if (process != null) {
        process.waitFor(TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS);
        process.destroyForcibly();
      }
    } catch (Exception e) {
      logger.warn("Error closing {} harness: {}", name(), e.getMessage());
    }
  }

  static String findPython() {
    Path venvPython = Path.of(".venv", "bin", "python3");
    if (Files.isExecutable(venvPython)) {
      return venvPython.toAbsolutePath().toString();
    }
    return "python3";
  }

  private String drainStderr() {
    try {
      if (process != null && process.getErrorStream().available() > 0) {
        return new String(process.getErrorStream().readNBytes(2048), StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {
    }
    return "";
  }
}
