package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.OllamaClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.provider.OllamaEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.Statement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.sqlite.SQLiteDataSource;

/**
 * Live integration test against a local Ollama instance.
 *
 * <p>Run with: mvn failsafe:integration-test -pl . -Dit.test=OllamaLocalIT
 *
 * <p>Prerequisites: Ollama running on localhost:11434 with llama3.1:8b and nomic-embed-text models.
 * Disabled via {@link EnabledIf} when the daemon or those models are missing.
 */
@EnabledIf(
    value = "ollamaReady",
    disabledReason = "Ollama is not running or llama3.1:8b / nomic-embed-text are not installed")
final class OllamaLocalIT {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String OLLAMA_URL = "http://localhost:11434";
  private static final String MODEL = "llama3.1:8b";

  private static File dbFile;
  private static SQLiteDataSource dataSource;
  private static QueryChat chat;

  static boolean ollamaReady() {
    return OllamaITSupport.isAvailable(OLLAMA_URL, MODEL, "nomic-embed-text");
  }

  @BeforeAll
  static void setUp() throws Exception {
    dbFile = File.createTempFile("ollama_test_", ".db");
    dbFile.deleteOnExit();
    dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price REAL, category TEXT)");
      stmt.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (2, 'Mouse', 29.99, 'Electronics')");
      stmt.execute("INSERT INTO products VALUES (3, 'Desk', 249.99, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (4, 'Chair', 399.99, 'Furniture')");
      stmt.execute("INSERT INTO products VALUES (5, 'Monitor', 549.99, 'Electronics')");

      stmt.execute(
          "CREATE TABLE orders (id INTEGER PRIMARY KEY, product_id INTEGER, quantity INTEGER, order_date TEXT)");
      stmt.execute("INSERT INTO orders VALUES (1, 1, 2, '2026-01-15')");
      stmt.execute("INSERT INTO orders VALUES (2, 2, 5, '2026-01-20')");
      stmt.execute("INSERT INTO orders VALUES (3, 3, 1, '2026-02-01')");
      stmt.execute("INSERT INTO orders VALUES (4, 5, 3, '2026-02-10')");
      stmt.execute("INSERT INTO orders VALUES (5, 4, 2, '2026-03-05')");
    }

    var connector = new SQLiteConnector(dataSource);
    var embeddingsProvider = new OllamaEmbeddingsProvider(OLLAMA_URL);
    var llmClient = new OllamaClient(OLLAMA_URL, MODEL, 0.0, 4096L);

    var sage =
        SqlSage4j.builder(
                LLMProviderConfig.builder(MODEL)
                    .llmClient(llmClient)
                    .embeddingsProvider(embeddingsProvider)
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .maxTokens(4096L)
                    .build())
            .databaseConnector(connector)
            .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
            .build();

    chat = sage.queryChat();

    // Train with schema
    chat.trainDdl(
        "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price REAL, category TEXT)");
    chat.trainDdl(
        "CREATE TABLE orders (id INTEGER PRIMARY KEY, product_id INTEGER, quantity INTEGER, order_date TEXT)");

    // Train with golden examples
    chat.train("How many products are there?", "SELECT COUNT(*) FROM products");
    chat.train(
        "What is the most expensive product?",
        "SELECT name, price FROM products ORDER BY price DESC LIMIT 1");
  }

  /**
   * Each test is an independent question. Without this, {@link QueryChat#ask} treats the previous
   * test's question as conversation context and spends an extra LLM round-trip rewriting the
   * question against it, which both doubles the runtime and leaks the previous test's subject into
   * the generated SQL.
   */
  @BeforeEach
  void clearHistory() {
    chat.history().clear();
  }

  @AfterAll
  static void tearDown() {
    if (dbFile != null) dbFile.delete();
  }

  @Test
  void askAndRun_countProducts() {
    String question = "Count the total number of products in inventory";
    QueryResponse response = chat.ask(question);
    logQuery(question, response);
    assertSuccessfulSelect(response);

    DataFrame df = chat.run(response);
    logger.info("Result:     {}", df.rows().get(0));
    logger.info("---");
  }

  @Test
  void askAndRun_mostExpensiveProduct() {
    String question = "What is the most expensive product?";
    QueryResponse response = chat.ask(question);
    logQuery(question, response);
    assertSuccessfulSelect(response);

    DataFrame df = chat.run(response);
    logger.info("Result:     {}", df.rows().get(0));
    logger.info("---");
  }

  @Test
  void askAndRun_electronicProducts() {
    String question = "List all products in the Electronics category";
    QueryResponse response = chat.ask(question);
    logQuery(question, response);
    assertSuccessfulSelect(response);

    DataFrame df = chat.run(response);
    logger.info("Result:     {}", df.rows());
    logger.info("Rows:       {}", df.rowCount());
    logger.info("---");
  }

  private static void assertSuccessfulSelect(QueryResponse response) {
    assertThat(response.isSuccess())
        .as("expected success but got error: " + response.error())
        .isTrue();
    assertThat(response.sql()).isNotNull().containsIgnoringCase("SELECT");
  }

  private static void logQuery(String question, QueryResponse response) {
    logger.info("Question:   {}", question);
    logger.info("SQL:        {}", response.sql());
    logger.info("Confidence: {}%", (int) (response.confidence() * 100));
  }
}
