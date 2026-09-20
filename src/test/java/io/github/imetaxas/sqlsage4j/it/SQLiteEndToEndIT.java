package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.it.fixture.TestDatabase;
import io.github.imetaxas.sqlsage4j.pipeline.PipelineListener;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test with a real SQLite database. Tests the full pipeline: training →
 * prompt assembly → LLM call (mocked) → SQL extraction → execution against real DB → result
 * validation.
 */
final class SQLiteEndToEndIT {

  private TestDatabase db;
  private MockLLMClient llmClient;
  private QueryChat queryChat;
  private final AtomicLong lastLLMLatency = new AtomicLong();

  @BeforeEach
  void setUp() {
    db = TestDatabase.ecommerce();
    llmClient = new MockLLMClient();

    PipelineListener listener =
        new PipelineListener() {
          @Override
          public void onLLMResponse(String response, long latencyMs) {
            lastLLMLatency.set(latencyMs);
          }
        };

    queryChat =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.SQLITE)
            .databaseConnector(db.connector())
            .pipelineListener(listener)
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .ddls(
                        List.of(
                            new DDL(
                                "CREATE TABLE products (product_id INTEGER PRIMARY KEY, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)"),
                            new DDL(
                                "CREATE TABLE customers (customer_id INTEGER PRIMARY KEY, name TEXT, email TEXT, country TEXT, signup_date TEXT)"),
                            new DDL(
                                "CREATE TABLE orders (order_id INTEGER PRIMARY KEY, customer_id INTEGER, order_date TEXT, total_amount REAL, status TEXT)"),
                            new DDL(
                                "CREATE TABLE order_items (item_id INTEGER PRIMARY KEY, order_id INTEGER, product_id INTEGER, quantity INTEGER, unit_price REAL)")))
                    .sampleQuestionsAnswers(
                        List.of(
                            new QuestionAnswer(
                                "Total revenue",
                                "SELECT SUM(total_amount) AS revenue FROM orders WHERE status = 'completed'"),
                            new QuestionAnswer(
                                "Product count by category",
                                "SELECT category, COUNT(*) AS cnt FROM products GROUP BY category")))
                    .build())
            .build()
            .queryChat();
  }

  @AfterEach
  void tearDown() {
    db.close();
  }

  @Test
  void countProducts_executesAgainstRealDB() {
    llmClient.withResponse("SELECT COUNT(*) AS product_count FROM products");

    QueryResponse response = queryChat.ask("How many products are there?");
    assertThat(response.isSuccess()).isTrue();

    DataFrame df = queryChat.run(response);
    assertThat(df.rowCount()).isEqualTo(1);
    assertThat(df.rows().get(0).get(0)).as("product count").isEqualTo(5);
  }

  @Test
  void revenueByCategory_realJoinExecution() {
    llmClient.withResponse(
        "SELECT p.category, SUM(oi.quantity * oi.unit_price) AS revenue "
            + "FROM order_items oi JOIN products p ON oi.product_id = p.product_id "
            + "GROUP BY p.category ORDER BY revenue DESC");

    QueryResponse response = queryChat.ask("Revenue by category");
    assertThat(response.isSuccess()).isTrue();

    DataFrame df = queryChat.run(response);
    assertThat(df.rowCount()).isGreaterThanOrEqualTo(1);
    assertThat(df.columns()).containsExactly("category", "revenue");
  }

  @Test
  void invalidSql_isDetectedByRealDB() {
    llmClient.withResponse("SELECT * FROM nonexistent_table");

    QueryResponse response = queryChat.ask("Show me something");
    assertThat(response.isSuccess()).isTrue();
    assertThat(db.connector().isValidSql("SELECT * FROM nonexistent_table")).isFalse();
  }

  @Test
  void customersByCountry_groupingWorks() {
    llmClient.withResponse(
        "SELECT country, COUNT(*) AS cnt FROM customers GROUP BY country ORDER BY cnt DESC");

    QueryResponse response = queryChat.ask("How many customers per country?");
    DataFrame df = queryChat.run(response);

    assertThat(df.rowCount()).isEqualTo(2);
    assertThat(df.rows().get(0).get(0)).as("top country").isEqualTo("US");
    assertThat(df.rows().get(0).get(1)).as("US count").isEqualTo(2);
  }

  @Test
  void lowStockProducts_filterWorks() {
    llmClient.withResponse(
        "SELECT name, stock_quantity FROM products WHERE stock_quantity < 10 ORDER BY stock_quantity");

    QueryResponse response = queryChat.ask("Which products are low stock?");
    DataFrame df = queryChat.run(response);

    assertThat(df.rowCount()).isEqualTo(1);
    assertThat(df.rows().get(0).get(0)).as("low stock product").isEqualTo("Monitor");
  }

  @Test
  void pipelineListener_reportsLatency() {
    llmClient.withResponse("SELECT 1");
    queryChat.ask("anything");

    assertThat(lastLLMLatency.get()).isGreaterThanOrEqualTo(0L);
  }
}
