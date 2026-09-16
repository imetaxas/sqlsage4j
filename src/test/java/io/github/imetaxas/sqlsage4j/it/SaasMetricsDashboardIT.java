package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.MockDatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.SQLiteEmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scenario 3: SaaS Metrics Dashboard — uses SQLiteEmbeddingsStorage (SQLite in-memory JDBC).
 *
 * <p>A finance team member queries subscription data — MRR, churn rate, trial-to-paid conversion.
 * After getting SQL and results, they request a summary and follow-up questions.
 */
final class SaasMetricsDashboardIT {

  private MockLLMClient llmClient;
  private MockDatabaseConnector db;
  private SQLiteEmbeddingsStorage sqlite;
  private QueryChat queryChat;

  @BeforeEach
  void setUp() {
    llmClient = new MockLLMClient();
    db = new MockDatabaseConnector("PostgreSQL");
    sqlite = new SQLiteEmbeddingsStorage();

    queryChat =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(sqlite)
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.POSTGRES)
            .databaseConnector(db)
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.ANALYST)
                    .ddls(
                        List.of(
                            new DDL(
                                """
                    CREATE TABLE subscriptions (
                      subscription_id SERIAL PRIMARY KEY,
                      customer_id INT NOT NULL,
                      plan_name VARCHAR(50),
                      mrr DECIMAL(10,2),
                      status VARCHAR(20),
                      started_at DATE,
                      cancelled_at DATE,
                      trial_ends_at DATE
                    );"""),
                            new DDL(
                                """
                    CREATE TABLE invoices (
                      invoice_id SERIAL PRIMARY KEY,
                      subscription_id INT REFERENCES subscriptions(subscription_id),
                      amount DECIMAL(10,2),
                      paid_at TIMESTAMP,
                      status VARCHAR(20)
                    );""")))
                    .sampleQuestionsAnswers(
                        List.of(
                            new QuestionAnswer(
                                "Current MRR",
                                "SELECT SUM(mrr) AS current_mrr FROM subscriptions WHERE status = 'active';"),
                            new QuestionAnswer(
                                "Monthly churn rate",
                                "SELECT COUNT(CASE WHEN cancelled_at >= DATE_TRUNC('month', CURRENT_DATE) THEN 1 END)::FLOAT / COUNT(*) AS churn_rate FROM subscriptions WHERE started_at < DATE_TRUNC('month', CURRENT_DATE);"),
                            new QuestionAnswer(
                                "Trial to paid conversion rate",
                                "SELECT COUNT(CASE WHEN status = 'active' AND trial_ends_at IS NOT NULL THEN 1 END)::FLOAT / NULLIF(COUNT(CASE WHEN trial_ends_at IS NOT NULL THEN 1 END), 0) AS conversion_rate FROM subscriptions;")))
                    .sampleDocuments(
                        List.of(
                            new SampleDocument(
                                "MRR (Monthly Recurring Revenue) is the sum of all active subscription mrr values. Churned subscriptions have status = 'cancelled' and a non-null cancelled_at date."),
                            new SampleDocument(
                                "Trial subscriptions have a non-null trial_ends_at. A trial converts to paid when status changes to 'active' after trial_ends_at.")))
                    .build())
            .build()
            .queryChat();
  }

  @AfterEach
  void tearDown() {
    sqlite.close();
  }

  @Test
  void currentMrr_askRunSummarize() {
    llmClient.withResponse(
        "SELECT SUM(mrr) AS current_mrr FROM subscriptions WHERE status = 'active';");

    db.withResult(
        "subscriptions", new DataFrame(List.of("current_mrr"), List.of(List.of(247500.00))));

    QueryResponse response = queryChat.ask("What is our current MRR?");
    assertThat(response.isSuccess()).isTrue();

    DataFrame df = queryChat.run(response);
    assertThat(df.rowCount()).isEqualTo(1);
    assertThat(df.rows().get(0).get(0)).as("current MRR").isEqualTo(247500.00);

    llmClient.withResponse("Your current MRR is $247,500, indicating healthy recurring revenue.");
    String summary = queryChat.generateSummary("What is our current MRR?", df.toMarkdown());
    assertThat(summary).containsIgnoringCase("247");
  }

  @Test
  void followupQuestions_forBoardReport() {
    llmClient.withResponse("SELECT SUM(mrr) FROM subscriptions WHERE status = 'active';");
    queryChat.ask("Current MRR?");

    llmClient.withResponse(
        "1. How has MRR changed month-over-month?\n"
            + "2. What is the MRR breakdown by plan?\n"
            + "3. Which plans have the highest churn?\n"
            + "4. What is our net revenue retention rate?");

    List<String> followups =
        queryChat.generateFollowupQuestions(
            "Current MRR?",
            "SELECT SUM(mrr) FROM subscriptions WHERE status = 'active';",
            "| current_mrr |\n| 247500 |",
            4);

    assertThat(followups).hasSize(4);
    assertThat(followups).anyMatch(q -> q.toLowerCase().contains("mrr"));
    assertThat(followups).anyMatch(q -> q.toLowerCase().contains("churn"));
  }

  @Test
  void postgresDialect_inPrompt() {
    llmClient.withResponse("SELECT 1;");
    queryChat.ask("test");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("PostgreSQL");
  }

  @Test
  void churnCalculation_usesDocumentation() {
    llmClient.withResponse(
        "SELECT COUNT(*) FILTER (WHERE cancelled_at >= DATE_TRUNC('month', CURRENT_DATE)) * 1.0 / COUNT(*) FROM subscriptions;");

    queryChat.ask("What is our churn rate this month?");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("cancelled");
    assertThat(systemPrompt).contains("status");
  }
}
