package io.github.imetaxas.sqlsage4j.diff;

import io.github.imetaxas.sqlsage4j.QueryChat;
import io.github.imetaxas.sqlsage4j.QueryResponse;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A/B testing framework for comparing SQL generation across two LLM configurations.
 *
 * <p>Given two {@link QueryChat} instances (e.g., different models, temperatures, or prompts), runs
 * the same questions against both and reports success rates, consistency, and SQL agreement.
 *
 * <p>Example:
 *
 * <pre>{@code
 * LLMDiff diff = LLMDiff.of(chatGpt4, chatLlama3);
 * DiffResult result = diff.run("How many active users?", 5);
 * System.out.printf("GPT-4: %d/5, Llama3: %d/5, Matching: %d/5%n",
 *     result.successCountA(), result.successCountB(), result.matchingCount());
 * }</pre>
 */
public final class LLMDiff {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final QueryChat chatA;
  private final QueryChat chatB;

  private LLMDiff(QueryChat chatA, QueryChat chatB) {
    this.chatA = Objects.requireNonNull(chatA);
    this.chatB = Objects.requireNonNull(chatB);
  }

  public static LLMDiff of(QueryChat chatA, QueryChat chatB) {
    return new LLMDiff(chatA, chatB);
  }

  /** Run the same question against both configs N times and compare results. */
  public DiffResult run(String question, int times) {
    logger.info("Running LLM diff for '{}' x{} times", question, times);

    List<QueryResponse> responsesA = new ArrayList<>();
    List<QueryResponse> responsesB = new ArrayList<>();

    for (int i = 0; i < times; i++) {
      logger.info("Diff iteration {}/{}", i + 1, times);
      responsesA.add(chatA.ask(question));
      responsesB.add(chatB.ask(question));
    }

    DiffResult result = new DiffResult(question, responsesA, responsesB, times);
    logger.info("Diff complete: {}", result);
    return result;
  }

  /** Run multiple questions and return results for each. */
  public List<DiffResult> runBatch(List<String> questions, int timesEach) {
    return questions.stream().map(q -> run(q, timesEach)).toList();
  }
}
