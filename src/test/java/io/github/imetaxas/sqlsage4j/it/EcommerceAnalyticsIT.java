package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.MockDatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.export.ExportType;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.LuceneEmbeddingsStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Scenario 1: E-commerce Analytics — uses LuceneEmbeddingsStorage (Apache Lucene KNN, in-memory).
 *
 * <p>A data analyst trains the system with product, order, and customer schemas plus golden
 * queries, then asks progressively complex business questions.
 */
final class EcommerceAnalyticsIT {

  private static final String PRODUCTS_DDL =
      """
      CREATE TABLE products (
        product_id INT PRIMARY KEY,
        name STRING,
        category STRING,
        price DECIMAL(10,2),
        stock_quantity INT,
        created_at TIMESTAMP
      );""";

  private static final String ORDERS_DDL =
      """
      CREATE TABLE orders (
        order_id INT PRIMARY KEY,
        customer_id INT,
        order_date DATE,
        total_amount DECIMAL(10,2),
        status STRING
      );""";

  private static final String ORDER_ITEMS_DDL =
      """
      CREATE TABLE order_items (
        item_id INT PRIMARY KEY,
        order_id INT REFERENCES orders(order_id),
        product_id INT REFERENCES products(product_id),
        quantity INT,
        unit_price DECIMAL(10,2)
      );""";

  private static final String CUSTOMERS_DDL =
      """
      CREATE TABLE customers (
        customer_id INT PRIMARY KEY,
        email STRING,
        name STRING,
        signup_date DATE,
        country STRING
      );""";

  @TempDir Path tempDir;

  private MockLLMClient llmClient;
  private MockDatabaseConnector db;
  private LuceneEmbeddingsStorage lucene;
  private QueryChat queryChat;

  @BeforeEach
  void setUp() {
    llmClient = new MockLLMClient();
    db =
        new MockDatabaseConnector("BigQuery SQL")
            .withDefaultResult(new DataFrame(List.of("result"), List.of(List.of("mock-data"))));
    lucene = new LuceneEmbeddingsStorage();

    queryChat =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(lucene)
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.BIGQUERY)
            .databaseConnector(db)
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .ddls(
                        List.of(
                            new DDL(PRODUCTS_DDL),
                            new DDL(ORDERS_DDL),
                            new DDL(ORDER_ITEMS_DDL),
                            new DDL(CUSTOMERS_DDL)))
                    .sampleQuestionsAnswers(
                        List.of(
                            new QuestionAnswer(
                                "What is total revenue?",
                                "SELECT SUM(total_amount) AS total_revenue FROM orders WHERE status = 'completed';"),
                            new QuestionAnswer(
                                "Top 10 customers by spend",
                                "SELECT c.name, SUM(o.total_amount) AS total_spend FROM customers c JOIN orders o ON c.customer_id = o.customer_id GROUP BY c.name ORDER BY total_spend DESC LIMIT 10;"),
                            new QuestionAnswer(
                                "Revenue by product category",
                                "SELECT p.category, SUM(oi.quantity * oi.unit_price) AS revenue FROM order_items oi JOIN products p ON oi.product_id = p.product_id GROUP BY p.category ORDER BY revenue DESC;")))
                    .sampleDocuments(
                        List.of(
                            new SampleDocument(
                                "The orders table only contains finalized transactions. Filter by status = 'completed' for revenue calculations. The order_items table links orders to products."),
                            new SampleDocument(
                                "Products with stock_quantity < 10 are considered low-stock and should trigger reorder alerts.")))
                    .build())
            .build()
            .queryChat();
  }

  @AfterEach
  void tearDown() {
    lucene.close();
  }

  @Test
  void revenueByCategory_promptIncludesTrainedDdlsAndExamples() {
    llmClient.withResponse(
        "SELECT p.category, SUM(oi.quantity * oi.unit_price) AS category_revenue "
            + "FROM order_items oi JOIN products p ON oi.product_id = p.product_id "
            + "JOIN orders o ON oi.order_id = o.order_id WHERE o.status = 'completed' "
            + "GROUP BY p.category ORDER BY category_revenue DESC;");

    QueryResponse response = queryChat.ask("Show me revenue broken down by category");

    assertThat(response.isSuccess()).as("query success").isTrue();
    assertThat(response.sql()).as("generated SQL").containsIgnoringCase("category");
    assertThat(response.sql()).containsIgnoringCase("GROUP BY");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("products");
    assertThat(systemPrompt).contains("order_items");
  }

  @Test
  void lowStockAlert_usesDocumentationContext() {
    llmClient.withResponse(
        "SELECT name, stock_quantity FROM products WHERE stock_quantity < 10 ORDER BY stock_quantity ASC;");

    QueryResponse response = queryChat.ask("Which products need to be reordered?");

    assertThat(response.isSuccess()).as("query success").isTrue();
    assertThat(response.sql()).as("generated SQL").containsIgnoringCase("stock_quantity");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("stock_quantity < 10");
  }

  @Test
  void askThenRunThenExport() throws IOException {
    llmClient.withResponse(
        "SELECT name, SUM(total_amount) FROM customers c JOIN orders o ON c.customer_id = o.customer_id GROUP BY name ORDER BY 2 DESC LIMIT 5;");

    db.withResult(
        "customers",
        new DataFrame(
            List.of("name", "total_spend"),
            List.of(
                List.of("Alice", 12500.0), List.of("Bob", 9800.0), List.of("Charlie", 7200.0))));

    QueryResponse response = queryChat.ask("Who are the top 5 customers?");
    DataFrame df = queryChat.run(response);

    assertThat(df.rowCount()).isEqualTo(3);
    assertThat(df.columns()).containsExactly("name", "total_spend");

    String csvPath = tempDir.resolve("top_customers.csv").toString();
    queryChat.exportAs(df, ExportType.CSV, csvPath);

    String csvContent = Files.readString(Path.of(csvPath));
    assertThat(csvContent).contains("Alice");
    assertThat(csvContent).contains("12500");
  }

  @Test
  void fewShotExamplesAppearAsUserAssistantPairs() {
    llmClient.withResponse("SELECT 1;");
    queryChat.ask("What is the total revenue this quarter?");

    var prompt = llmClient.lastPrompt();
    long assistantMsgCount = prompt.stream().filter(m -> m.role() == ChatRole.ASSISTANT).count();
    assertThat(assistantMsgCount).isGreaterThanOrEqualTo(1L);
  }

  @Test
  void responseGuidelines_includeDialect() {
    llmClient.withResponse("SELECT 1;");
    queryChat.ask("any question");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("BigQuery SQL");
    assertThat(systemPrompt).contains("Response Guidelines");
  }
}
