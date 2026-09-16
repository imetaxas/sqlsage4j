package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.MockDatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scenario 4: Data Team Onboarding — uses InMemoryEmbeddingsStorage (brute-force cosine).
 *
 * <p>A new hire starts with minimal training data, asks questions the system can't fully answer,
 * then progressively improves it by feeding back correct Q&A pairs. Tests the self-improving
 * training loop and graceful degradation.
 */
final class DataTeamOnboardingIT {

  private MockLLMClient llmClient;
  private MockDatabaseConnector db;
  private QueryChat queryChat;

  @BeforeEach
  void setUp() {
    llmClient = new MockLLMClient();
    db = new MockDatabaseConnector("BigQuery SQL");

    queryChat =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.BIGQUERY)
            .databaseConnector(db)
            .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
            .build()
            .queryChat();
  }

  @Test
  void coldStart_noTrainingData_stillGeneratesSQL() {
    llmClient.withResponse("SELECT * FROM unknown_table LIMIT 10;");

    QueryResponse response = queryChat.ask("Show me recent user signups");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).contains("SELECT");
  }

  @Test
  void coldStart_promptHasNoFewShotExamples() {
    llmClient.withResponse("SELECT 1;");
    queryChat.ask("anything");

    assertThat(llmClient.callCount()).isEqualTo(1);
    var prompt = llmClient.submittedPrompts().get(0);
    assertThat(prompt).hasSize(2);
    assertThat(prompt.get(0).content()).contains("expert");
    assertThat(prompt.get(1).content()).isEqualTo("anything");
  }

  @Test
  void progressiveTraining_improvesContextOverTime() {
    llmClient.withResponse("SELECT * FROM users;");
    queryChat.ask("How many active users?");
    int promptSizeBeforeTraining = llmClient.lastPrompt().get(0).content().length();

    queryChat.train(
        "How many active users?",
        "SELECT COUNT(*) FROM users WHERE status = 'active' AND last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY);");

    queryChat.trainDdl(
        "CREATE TABLE users (user_id INT, status STRING, last_login DATE, signup_date DATE);");
    queryChat.trainDocumentation(
        "Active users are defined as users with status='active' who logged in within the last 30 days.");

    llmClient.withResponse(
        "SELECT COUNT(*) FROM users WHERE status = 'active' AND last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY);");
    queryChat.ask("Count of active users in the last month");

    int promptSizeAfterTraining = llmClient.lastPrompt().get(0).content().length();
    assertThat(promptSizeAfterTraining).isGreaterThan(promptSizeBeforeTraining);

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("users");
    assertThat(systemPrompt).contains("status");
  }

  @Test
  void trainThenDelete_resetsContext() {
    String id1 = queryChat.trainDdl("CREATE TABLE experiments (id INT, name STRING);");
    queryChat.train("List experiments", "SELECT * FROM experiments;");

    assertThat(queryChat.getAllTrainedData()).contains("experiments");

    queryChat.deleteTrainedData(id1);
    queryChat.deleteAllTrainedData();
    assertThat(queryChat.getAllTrainedData()).isEqualTo("[]");
  }

  @Test
  void autoGenerateQuestion_fromSqlOnly() {
    llmClient.withResponse("What is the total number of completed orders?");

    queryChat.trainSqlAutoQuestion("SELECT COUNT(*) FROM orders WHERE status = 'completed';");

    assertThat(llmClient.callCount()).isEqualTo(1);
    assertThat(queryChat.getAllTrainedData()).contains("completed");
  }

  @Test
  void suggestedQuestions_fromTrainedData() {
    queryChat.train(
        "Revenue this month?", "SELECT SUM(amount) FROM sales WHERE date >= '2024-01-01';");
    queryChat.train(
        "Top products?", "SELECT product, SUM(qty) FROM sales GROUP BY product ORDER BY 2 DESC;");
    queryChat.train("Customer count?", "SELECT COUNT(DISTINCT customer_id) FROM customers;");

    var suggestions = queryChat.generateQuestionsFromData();
    assertThat(suggestions).isNotEmpty();
    assertThat(suggestions).allMatch(q -> !q.isBlank());
  }
}
