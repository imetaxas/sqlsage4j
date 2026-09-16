package io.github.imetaxas.sqlsage4j.it;

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
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
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
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class OllamaHardenedIT {

  private static final String OLLAMA_URL = "http://localhost:11434";
  private static final String MODEL = "llama3.1:8b";

  private static File dbFile;
  private static SQLiteDataSource dataSource;
  private static QueryChat chat;
  private static final List<TestResult> results = new ArrayList<>();

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
    printSummary();
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

    System.out.printf("Q: %s%n", question);
    System.out.printf("   SQL: %s%n", sql);
    System.out.printf("   Confidence: %d%%%n", (int) (confidence * 100));

    if (!response.isSuccess()) {
      System.out.printf("   ERROR: %s%n", response.error());
      resultStr = "ERROR: " + response.error();
    } else if (sql != null && sql.toUpperCase().contains("SELECT")) {
      try {
        DataFrame df = chat.run(response);
        resultStr = df.rows().toString();
        executed = true;
        correct = resultStr.contains(expectedContains);
        System.out.printf("   Result: %s%n", resultStr);
        System.out.printf(
            "   Expected to contain: %s → %s%n", expectedContains, correct ? "CORRECT" : "WRONG");
      } catch (Exception e) {
        resultStr = "EXEC FAILED: " + e.getMessage();
        System.out.printf("   EXEC FAILED: %s%n", e.getMessage());
      }
    } else {
      resultStr = "NO SQL GENERATED";
      System.out.printf("   NO SQL GENERATED%n");
    }
    System.out.println();
    results.add(new TestResult(question, sql, confidence, resultStr, executed, correct));
  }

  private static void printSummary() {
    System.out.println("\n═══════════════════════════════════════════════════════════════════");
    System.out.println("  HARDENED OLLAMA TEST SUMMARY (llama3.1:8b)");
    System.out.println("═══════════════════════════════════════════════════════════════════");
    int executed = 0;
    int correct = 0;
    for (TestResult r : results) {
      String exec = r.executed ? "EXEC" : "FAIL";
      String corr = r.correct ? "CORRECT" : "WRONG";
      System.out.printf("  [%s][%s] %s%n", exec, corr, r.question);
      if (r.executed) executed++;
      if (r.correct) correct++;
    }
    System.out.println("───────────────────────────────────────────────────────────────────");
    System.out.printf(
        "  Execution rate: %d/%d (%.0f%%)%n",
        executed, results.size(), 100.0 * executed / results.size());
    System.out.printf(
        "  Answer accuracy: %d/%d (%.0f%%)%n",
        correct, results.size(), 100.0 * correct / results.size());
    System.out.printf(
        "  Avg confidence: %.0f%%%n",
        results.stream().mapToDouble(TestResult::confidence).average().orElse(0) * 100);
    System.out.println("───────────────────────────────────────────────────────────────────");
    System.out.println("  Model: " + MODEL);
    System.out.println("  Hardening steps applied:");
    System.out.println("    1. 15 golden Q&A examples (diverse patterns)");
    System.out.println("    2. Domain documentation training");
    System.out.println("    3. Auto-schema introspection (FK relationships)");
    System.out.println("    4. Temperature = 0 (deterministic)");
    System.out.println("    5. SQL validation + self-correction");
    System.out.println("    6. Hybrid search (70% vector + 30% BM25)");
    System.out.println("    7. SQL safety guardrails");
    System.out.println("═══════════════════════════════════════════════════════════════════\n");
  }
}
