package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.diff.DiffResult;
import io.github.imetaxas.sqlsage4j.diff.LLMDiff;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.HnswEmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Scenario 5: A/B Test Analysis with LLM Diff — uses HnswEmbeddingsStorage (pure Java NSW graph).
 *
 * <p>A data scientist compares two prompt configurations across a suite of benchmark questions.
 * Config A uses a generic SQL expert prompt. Config B uses a domain-specialized prompt with
 * additional training context. The diff reveals which configuration produces better SQL.
 */
final class ABTestWithLLMDiffIT {

  private QueryChat buildChat(
      MockLLMClient client, PromptEnum role, String initialPrompt, boolean withTraining) {
    Prompt.Builder promptBuilder = Prompt.builder().userPrompt(role);
    if (initialPrompt != null) {
      promptBuilder.initialPrompt(initialPrompt);
    }

    if (withTraining) {
      promptBuilder
          .ddls(
              List.of(
                  new DDL(
                      "CREATE TABLE events (event_id INT, user_id INT, event_type STRING, ts TIMESTAMP);"),
                  new DDL("CREATE TABLE users (user_id INT, plan STRING, country STRING);")))
          .sampleQuestionsAnswers(
              List.of(
                  new QuestionAnswer(
                      "Daily active users",
                      "SELECT DATE(ts) AS day, COUNT(DISTINCT user_id) AS dau FROM events GROUP BY day ORDER BY day;")))
          .sampleDocuments(
              List.of(
                  new SampleDocument(
                      "DAU = distinct users with at least one event per day. WAU = distinct users in a 7-day window.")));
    }

    return SqlSage4j.builder(
            LLMProviderConfig.builder("mock-model")
                .llmClient(client)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new HnswEmbeddingsStorage())
                .maxTokens(14000L)
                .build())
        .connectToStorage(StorageEnum.BIGQUERY)
        .prompt(promptBuilder.build())
        .build()
        .queryChat();
  }

  @Test
  void genericVsSpecialized_singleQuestion() {
    MockLLMClient genericClient =
        new MockLLMClient().withDefaultResponse("SELECT COUNT(DISTINCT user_id) FROM events;");
    MockLLMClient specializedClient =
        new MockLLMClient()
            .withDefaultResponse(
                "SELECT DATE(ts) AS day, COUNT(DISTINCT user_id) AS dau FROM events WHERE DATE(ts) = CURRENT_DATE() GROUP BY day;");

    QueryChat genericChat =
        buildChat(genericClient, PromptEnum.SQL_EXPERT, "You are a SQL expert.", false);
    QueryChat specializedChat =
        buildChat(
            specializedClient,
            PromptEnum.DATA_SCIENTIST,
            "You are an analytics expert specializing in product metrics.",
            true);

    DiffResult result = LLMDiff.of(genericChat, specializedChat).run("What is today's DAU?", 1);

    assertThat(result.totalRuns()).isEqualTo(1);
    assertThat(result.successCountA()).isEqualTo(1L);
    assertThat(result.successCountB()).isEqualTo(1L);
    assertThat(result.matchingCount()).isEqualTo(0L);
    assertThat(result.responsesA().get(0).sql()).doesNotContain("DATE(ts)");
    assertThat(result.responsesB().get(0).sql()).contains("DATE(ts)");
  }

  @Test
  void batchDiff_multipleQuestions() {
    MockLLMClient clientA = new MockLLMClient().withDefaultResponse("SELECT 1;");
    MockLLMClient clientB = new MockLLMClient().withDefaultResponse("SELECT 2;");

    QueryChat chatA = buildChat(clientA, PromptEnum.SQL_EXPERT, null, false);
    QueryChat chatB = buildChat(clientB, PromptEnum.SQL_EXPERT, null, false);

    List<String> benchmarkQuestions =
        List.of("What is the DAU?", "Top countries by users?", "Retention rate?", "Revenue trend?");

    List<DiffResult> results = LLMDiff.of(chatA, chatB).runBatch(benchmarkQuestions, 2);

    assertThat(results).hasSize(4);
    results.forEach(
        r -> {
          assertThat(r.totalRuns()).isEqualTo(2);
          assertThat(r.matchingCount()).isEqualTo(0L);
        });
  }

  @Test
  void identicalConfigs_shouldMatch() {
    MockLLMClient sharedClient =
        new MockLLMClient().withDefaultResponse("SELECT COUNT(*) FROM users;");

    QueryChat chatA = buildChat(sharedClient, PromptEnum.SQL_EXPERT, null, false);
    QueryChat chatB = buildChat(sharedClient, PromptEnum.SQL_EXPERT, null, false);

    DiffResult result = LLMDiff.of(chatA, chatB).run("How many users?", 3);

    assertThat(result.matchingCount()).isEqualTo(3L);
    assertThat(result.successCountA()).isEqualTo(3L);
    assertThat(result.successCountB()).isEqualTo(3L);
  }

  @Test
  void diffResult_capturesIndividualResponses() {
    int runs = 5;
    MockLLMClient clientA = new MockLLMClient().withDefaultResponse("SELECT 'a';");
    MockLLMClient clientB = new MockLLMClient().withDefaultResponse("SELECT 'b';");

    QueryChat chatA = buildChat(clientA, PromptEnum.SQL_EXPERT, null, false);
    QueryChat chatB = buildChat(clientB, PromptEnum.SQL_EXPERT, null, false);

    DiffResult result = LLMDiff.of(chatA, chatB).run("test", runs);

    assertThat(result.responsesA()).hasSize(runs);
    assertThat(result.responsesB()).hasSize(runs);
    assertThat(result.toString()).contains("test");
    assertThat(result.toString()).contains("runs=" + runs);
  }
}
