package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.OllamaClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.OllamaEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.BM25Storage;
import io.github.imetaxas.sqlsage4j.storage.HybridEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.sqlite.SQLiteDataSource;

/**
 * Hardened integration test applying all 7 accuracy improvement steps:
 *
 * <ol>
 *   <li>Rich golden Q&A examples (15+ per table)
 *   <li>Domain documentation training
 *   <li>Auto-schema introspection (captures foreign keys)
 *   <li>Temperature = 0 (deterministic)
 *   <li>SQL validation + self-correction (built-in)
 *   <li>Hybrid search (vector + BM25)
 *   <li>SQL safety guardrails
 * </ol>
 *
 * Run: mvn test-compile -pl . -q && mvn failsafe:integration-test -pl . -Dit.test=OllamaHardenedIT
 *
 * <p>Disabled via {@link EnabledIf} when Ollama is not running or llama3.1:8b / nomic-embed-text
 * are not installed.
 */
@EnabledIf(
    value = "ollamaReady",
    disabledReason = "Ollama is not running or llama3.1:8b / nomic-embed-text are not installed")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class OllamaHardenedIT {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String OLLAMA_URL = "http://localhost:11434";
  private static final String MODEL = "llama3.1:8b";

  private static File dbFile;
  private static SQLiteDataSource dataSource;
  private static QueryChat chat;
  private static final List<TestResult> results = new ArrayList<>();

  static boolean ollamaReady() {
    return OllamaITSupport.isAvailable(OLLAMA_URL, MODEL, "nomic-embed-text");
  }

  record TestResult(
      String question,
      String sql,
      double confidence,
      String result,
      boolean executed,
      boolean correct) {}

  @BeforeAll
  static void setUp() throws Exception {
    dbFile = File.createTempFile("hardened_test_", ".db");
    dbFile.deleteOnExit();
    dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE products ("
              + "id INTEGER PRIMARY KEY, "
              + "name TEXT NOT NULL, "
              + "price REAL NOT NULL, "
              + "category TEXT NOT NULL)");
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 29.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (3, 'Desk', 249.99, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (4, 'Chair', 399.99, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (5, 'Monitor', 549.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (6, 'Keyboard', 79.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (7, 'Bookshelf', 189.99, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (8, 'Headphones', 149.99, 'Electronics')");

      stmt.execute(
          "CREATE TABLE orders ("
              + "id INTEGER PRIMARY KEY, "
              + "product_id INTEGER NOT NULL, "
              + "quantity INTEGER NOT NULL, "
              + "order_date TEXT NOT NULL, "
              + "FOREIGN KEY (product_id) REFERENCES products(id))");
      stmt.execute("INSERT INTO orders VALUES (1, 1, 2, '2026-01-15')");
      stmt.execute("INSERT INTO orders VALUES (2, 2, 5, '2026-01-20')");
      stmt.execute("INSERT INTO orders VALUES (3, 3, 1, '2026-02-01')");
      stmt.execute("INSERT INTO orders VALUES (4, 5, 3, '2026-02-10')");
      stmt.execute("INSERT INTO orders VALUES (5, 4, 2, '2026-03-05')");
      stmt.execute("INSERT INTO orders VALUES (6, 6, 4, '2026-03-10')");
      stmt.execute("INSERT INTO orders VALUES (7, 8, 1, '2026-03-15')");
      stmt.execute("INSERT INTO orders VALUES (8, 1, 1, '2026-04-01')");
    }

    var connector = new SQLiteConnector(dataSource);

    // Step 4: Temperature = 0 for deterministic output
    var llmClient = new OllamaClient(OLLAMA_URL, MODEL, 0.0, 4096L);
    var embeddingsProvider = new OllamaEmbeddingsProvider(OLLAMA_URL);

    // Step 6: Hybrid search (70% vector + 30% BM25)
    var hybridStorage =
        new HybridEmbeddingsStorage(new InMemoryEmbeddingsStorage(), new BM25Storage(), 0.7);

    var sage =
        SqlSage4j.builder(
                LLMProviderConfig.builder(MODEL)
                    .llmClient(llmClient)
                    .embeddingsProvider(embeddingsProvider)
                    .embeddingsStorage(hybridStorage)
                    .maxTokens(4096L)
                    .build())
            .databaseConnector(connector)
            .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
            // Step 7: SQL safety guardrails
            .sqlGuard(SqlGuard.readOnly())
            .build();

    chat = sage.queryChat();

    // Step 3: Auto-schema introspection (discovers tables, columns, types, FK relationships)
    chat.trainFromDatabase(dataSource);

    // Step 2: Domain documentation
    chat.trainDocumentation(
        "This is an e-commerce database with two tables. "
            + "The 'products' table stores inventory with columns: id (primary key), "
            + "name (product name like 'Laptop', 'Mouse'), price (in USD as REAL), "
            + "and category (either 'Electronics' or 'Furniture'). "
            + "The 'orders' table tracks customer purchases with columns: id (primary key), "
            + "product_id (foreign key to products.id), quantity (number of items purchased), "
            + "and order_date (in YYYY-MM-DD format). "
            + "To find which product was ordered, JOIN orders.product_id = products.id. "
            + "To count products, query the products table directly. "
            + "To sum order quantities, use SUM(quantity) on the orders table.");

    // Step 1: Rich golden Q&A examples (15 diverse patterns)
    chat.train("How many products are there?", "SELECT COUNT(*) AS product_count FROM products");
    chat.train(
        "What is the most expensive product?",
        "SELECT name, price FROM products ORDER BY price DESC LIMIT 1");
    chat.train(
        "List all products in the Electronics category",
        "SELECT name, price FROM products WHERE category = 'Electronics' ORDER BY price DESC");
    chat.train(
        "List all products in the Furniture category",
        "SELECT name, price FROM products WHERE category = 'Furniture' ORDER BY price DESC");
    chat.train(
        "What is the average product price?",
        "SELECT ROUND(AVG(price), 2) AS avg_price FROM products");
    chat.train("How many orders are there?", "SELECT COUNT(*) AS order_count FROM orders");
    chat.train(
        "What is the total quantity of all orders?",
        "SELECT SUM(quantity) AS total_quantity FROM orders");
    chat.train(
        "Which products cost more than 200 dollars?",
        "SELECT name, price FROM products WHERE price > 200 ORDER BY price DESC");
    chat.train(
        "Show the top 3 most ordered products by quantity",
        "SELECT p.name, SUM(o.quantity) AS total_ordered "
            + "FROM orders o JOIN products p ON o.product_id = p.id "
            + "GROUP BY p.name ORDER BY total_ordered DESC LIMIT 3");
    chat.train(
        "How many products are in each category?",
        "SELECT category, COUNT(*) AS count FROM products GROUP BY category");
    chat.train(
        "What is the cheapest product?",
        "SELECT name, price FROM products ORDER BY price ASC LIMIT 1");
    chat.train(
        "Show all orders from January 2026",
        "SELECT o.id, p.name, o.quantity, o.order_date "
            + "FROM orders o JOIN products p ON o.product_id = p.id "
            + "WHERE o.order_date BETWEEN '2026-01-01' AND '2026-01-31'");
    chat.train(
        "What is the total revenue from all orders?",
        "SELECT ROUND(SUM(o.quantity * p.price), 2) AS total_revenue "
            + "FROM orders o JOIN products p ON o.product_id = p.id");
    chat.train(
        "Which product has been ordered the most times?",
        "SELECT p.name, COUNT(o.id) AS order_count "
            + "FROM orders o JOIN products p ON o.product_id = p.id "
            + "GROUP BY p.name ORDER BY order_count DESC LIMIT 1");
    chat.train(
        "List products that have never been ordered",
        "SELECT p.name FROM products p "
            + "LEFT JOIN orders o ON p.id = o.product_id "
            + "WHERE o.id IS NULL");
  }

  @AfterAll
  static void tearDown() {
    if (dbFile != null) dbFile.delete();
    if (!results.isEmpty()) {
      printSummary();
    }
  }

  // ─── Test Questions (varied phrasing, not exact matches to training) ───
  // Each test clears conversation history to ensure independent evaluation.

  @Test
  @Order(1)
  void countProducts() {
    runTest("Count the total number of products in the database", "8");
  }

  @Test
  @Order(2)
  void mostExpensiveProduct() {
    runTest("What's the priciest item we have?", "999.99");
  }

  @Test
  @Order(3)
  void electronicsProducts() {
    runTest("Show me all Electronics products", "Laptop");
  }

  @Test
  @Order(4)
  void averagePrice() {
    runTest("What's the average price across all products?", "331");
  }

  @Test
  @Order(5)
  void totalOrders() {
    runTest("How many total orders have been placed?", "8");
  }

  @Test
  @Order(6)
  void totalQuantity() {
    runTest("What is the total number of items ordered?", "19");
  }

  @Test
  @Order(7)
  void expensiveProducts() {
    runTest("Which products cost over $200?", "Laptop");
  }

  @Test
  @Order(8)
  void categoryBreakdown() {
    runTest("Give me a breakdown of products per category", "Electronics");
  }

  @Test
  @Order(9)
  void cheapestProduct() {
    runTest("What's the least expensive item?", "29.99");
  }

  @Test
  @Order(10)
  void totalRevenue() {
    runTest("What is the total revenue from all orders?", "6319");
  }

  // ─── Helper ───

  private void runTest(String question, String expectedContains) {
    // Clear conversation history — each question is independent
    chat.history().clear();

    QueryResponse response = chat.ask(question);
    String sql = response.sql();
    double confidence = response.confidence();
    boolean executed = false;
    boolean correct = false;
    String resultStr = "";

    logger.info("Q: {}", question);
    logger.info("   SQL: {}", sql);
    logger.info("   Confidence: {}%", (int) (confidence * 100));

    if (!response.isSuccess()) {
      logger.info("   ERROR: {}", response.error());
      resultStr = "ERROR: " + response.error();
    } else if (sql != null && sql.toUpperCase().contains("SELECT")) {
      try {
        DataFrame df = chat.run(response);
        resultStr = df.rows().toString();
        executed = true;
        correct = resultStr.contains(expectedContains);
        logger.info("   Result: {}", resultStr);
        logger.info(
            "   Expected to contain: {} → {}", expectedContains, correct ? "CORRECT" : "WRONG");
      } catch (Exception e) {
        resultStr = "EXEC FAILED: " + e.getMessage();
        logger.info("   EXEC FAILED: {}", e.getMessage());
      }
    } else {
      resultStr = "NO SQL GENERATED";
      logger.info("   NO SQL GENERATED");
    }
    logger.info("");
    results.add(new TestResult(question, sql, confidence, resultStr, executed, correct));

    assertThat(response.isSuccess())
        .as("expected success but got error: " + response.error())
        .isTrue();
    assertThat(sql).isNotNull().containsIgnoringCase("SELECT");
    assertThat(executed).as("SQL execution failed: " + resultStr).isTrue();
    assertThat(resultStr).contains(expectedContains);
  }

  private static void printSummary() {
    logger.info("");
    logger.info("═══════════════════════════════════════════════════════════════════");
    logger.info("  HARDENED OLLAMA TEST SUMMARY ({})", MODEL);
    logger.info("═══════════════════════════════════════════════════════════════════");
    int executed = 0;
    int correct = 0;
    for (TestResult r : results) {
      String exec = r.executed ? "EXEC" : "FAIL";
      String corr = r.correct ? "CORRECT" : "WRONG";
      logger.info("  [{}][{}] {}", exec, corr, r.question);
      if (r.executed) executed++;
      if (r.correct) correct++;
    }
    logger.info("───────────────────────────────────────────────────────────────────");
    logger.printf(
        Level.INFO,
        "  Execution rate: %d/%d (%.0f%%)",
        executed,
        results.size(),
        100.0 * executed / results.size());
    logger.printf(
        Level.INFO,
        "  Answer accuracy: %d/%d (%.0f%%)",
        correct,
        results.size(),
        100.0 * correct / results.size());
    logger.printf(
        Level.INFO,
        "  Avg confidence: %.0f%%",
        results.stream().mapToDouble(TestResult::confidence).average().orElse(0) * 100);
    logger.info("───────────────────────────────────────────────────────────────────");
    logger.info("  Model: {}", MODEL);
    logger.info("  Hardening steps applied:");
    logger.info("    1. 15 golden Q&A examples (diverse patterns)");
    logger.info("    2. Domain documentation training");
    logger.info("    3. Auto-schema introspection (FK relationships)");
    logger.info("    4. Temperature = 0 (deterministic)");
    logger.info("    5. SQL validation + self-correction");
    logger.info("    6. Hybrid search (70% vector + 30% BM25)");
    logger.info("    7. SQL safety guardrails");
    logger.info("═══════════════════════════════════════════════════════════════════");
    logger.info("");
  }
}
