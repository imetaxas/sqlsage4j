package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.MockDatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.LSHEmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scenario 6: Multi-Turn Conversational Exploration — uses LSHEmbeddingsStorage (Locality-Sensitive
 * Hashing).
 *
 * <p>A user has a flowing conversation that drills down through data: starts with a broad overview,
 * narrows to a specific segment, pivots to a different angle, and references prior context.
 */
final class MultiTurnConversationIT {

  private MockLLMClient llmClient;
  private MockDatabaseConnector db;
  private LSHEmbeddingsStorage lsh;
  private SqlSage4j sqlSage4j;
  private QueryChat queryChat;
  private LLMChat llmChat;

  @BeforeEach
  void setUp() {
    llmClient = new MockLLMClient();
    db =
        new MockDatabaseConnector("BigQuery SQL")
            .withDefaultResult(new DataFrame(List.of("result"), List.of(List.of("value"))));
    lsh = new LSHEmbeddingsStorage();

    sqlSage4j =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(lsh)
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.BIGQUERY)
            .databaseConnector(db)
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.DATA_SCIENTIST)
                    .ddls(
                        List.of(
                            new DDL(
                                "CREATE TABLE campaigns (campaign_id INT, name STRING, channel STRING, budget DECIMAL, start_date DATE, end_date DATE);"),
                            new DDL(
                                "CREATE TABLE campaign_events (event_id INT, campaign_id INT, event_type STRING, user_id INT, occurred_at TIMESTAMP);"),
                            new DDL(
                                "CREATE TABLE conversions (conversion_id INT, campaign_id INT, user_id INT, revenue DECIMAL, converted_at TIMESTAMP);")))
                    .sampleDocuments(
                        List.of(
                            new SampleDocument(
                                "Campaign ROI = (total conversion revenue - budget) / budget. Channels include 'email', 'social', 'search', 'display'."),
                            new SampleDocument(
                                "Event types: 'impression', 'click', 'signup', 'purchase'. Click-through rate = clicks / impressions.")))
                    .build())
            .build();

    queryChat = sqlSage4j.queryChat();
    llmChat = sqlSage4j.llmChat();
  }

  @Test
  void threeStepDrillDown_broadToNarrowToPivot() {
    llmClient.withResponse(
        "SELECT channel, SUM(budget) AS total_budget, COUNT(*) AS campaigns FROM campaigns GROUP BY channel;");
    QueryResponse r1 = queryChat.ask("Give me an overview of campaigns by channel");
    assertThat(r1.isSuccess()).isTrue();

    llmClient
        .withResponse("What is the performance of email campaigns?")
        .withResponse(
            "SELECT c.name, COUNT(ce.event_id) AS events, SUM(cv.revenue) AS revenue "
                + "FROM campaigns c LEFT JOIN campaign_events ce ON c.campaign_id = ce.campaign_id "
                + "LEFT JOIN conversions cv ON c.campaign_id = cv.campaign_id "
                + "WHERE c.channel = 'email' GROUP BY c.name;");

    QueryResponse r2 = queryChat.ask("What about email specifically?");
    assertThat(r2.isSuccess()).isTrue();
    assertThat(r2.sql()).containsIgnoringCase("email");

    llmClient
        .withResponse("What is the click-through rate for email campaigns?")
        .withResponse(
            "SELECT c.name, "
                + "COUNTIF(ce.event_type = 'click') / NULLIF(COUNTIF(ce.event_type = 'impression'), 0) AS ctr "
                + "FROM campaigns c JOIN campaign_events ce ON c.campaign_id = ce.campaign_id "
                + "WHERE c.channel = 'email' GROUP BY c.name;");

    QueryResponse r3 = queryChat.ask("And the click-through rate?");
    assertThat(r3.isSuccess()).isTrue();
    assertThat(r3.sql()).containsIgnoringCase("click");

    var historyEntries = queryChat.history().getAll();
    assertThat(historyEntries).hasSize(3);
  }

  @Test
  void conversationHistory_cachedAfterRun() {
    llmClient.withResponse("SELECT name, budget FROM campaigns ORDER BY budget DESC LIMIT 5;");

    db.withResult(
        "campaigns",
        new DataFrame(
            List.of("name", "budget"),
            List.of(
                List.of("Summer Sale", 50000),
                List.of("Black Friday", 45000),
                List.of("New Year", 30000))));

    QueryResponse response = queryChat.ask("Top 5 campaigns by budget");
    DataFrame df = queryChat.run(response);

    var cached = queryChat.history().get(response.id());
    assertThat(cached).as("cached response").isNotNull();
    assertThat(cached.question()).contains("campaigns by budget");
    assertThat(cached.sql()).contains("budget");
    assertThat(cached.df()).as("cached dataframe").isNotNull();
    assertThat(cached.df().rowCount()).isEqualTo(3);
  }

  @Test
  void llmChat_multiTurnWithContext() {
    llmClient.withResponse(
        "We support two attribution models: last-touch and multi-touch. "
            + "Last-touch gives 100% credit to the final interaction before conversion.");
    ChatResponse r1 = llmChat.ask("What attribution models do we support?");
    assertThat(r1.content()).containsIgnoringCase("attribution");

    llmClient.withResponse(
        "Multi-touch attribution distributes credit proportionally across all touchpoints "
            + "in the user journey, giving a more holistic view of campaign effectiveness.");
    ChatResponse r2 = llmChat.ask("Tell me more about the multi-touch one");
    assertThat(r2.content()).containsIgnoringCase("multi-touch");

    assertThat(llmChat.getHistory()).hasSize(4);

    var lastPrompt = llmClient.lastPrompt();
    long userMessages =
        lastPrompt.stream().filter(m -> m.content().contains("attribution")).count();
    assertThat(userMessages).isGreaterThanOrEqualTo(1L);
  }

  @Test
  void llmChat_clearHistory_resetsConversation() {
    llmClient.withDefaultResponse("Some response");

    llmChat.ask("First question");
    llmChat.ask("Second question");
    assertThat(llmChat.getHistory()).hasSize(4);

    llmChat.clearHistory();
    assertThat(llmChat.getHistory()).isEmpty();

    llmChat.ask("Fresh question after reset");
    assertThat(llmChat.getHistory()).hasSize(2);
  }

  @Test
  void llmChat_retrievesSchemaContext() {
    llmClient.withResponse("The campaigns table has columns for name, channel, budget, and dates.");

    llmChat.ask("What tables do we have for campaign data?");

    var lastPrompt = llmClient.lastPrompt();
    String systemContent = lastPrompt.get(0).content();
    assertThat(systemContent).contains("campaigns");
    assertThat(systemContent).contains("Relevant Schema");
  }
}
