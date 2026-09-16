package io.github.imetaxas.sqlsage4j.it;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.OllamaClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.provider.OllamaEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

/**
 * Live integration test against a local Ollama instance.
 *
 * <p>Run with: mvn failsafe:integration-test -pl . -Dit.test=OllamaLocalIT
 *
 * <p>Prerequisites: Ollama running on localhost:11434 with llama3.1:8b and nomic-embed-text models.
 */
final class OllamaLocalIT {

  private static final String OLLAMA_URL = "http://localhost:11434";
  private static final String MODEL = "llama3.1:8b";

  private static File dbFile;
  private static SQLiteDataSource dataSource;
  private static QueryChat chat;

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

  @AfterAll
  static void tearDown() {
    if (dbFile != null) dbFile.delete();
  }

  @Test
  void askAndRun_countProducts() {
    QueryResponse response = chat.ask("Count the total number of products in inventory");

    System.out.println("Question: Count the total number of products in inventory");
    System.out.println("SQL:      " + response.sql());
    System.out.println("Confidence: " + (int) (response.confidence() * 100) + "%");

    assert response.isSuccess() : "Expected success but got error: " + response.error();
    assert response.sql().toUpperCase().contains("SELECT")
        : "Expected SELECT in SQL: " + response.sql();

    DataFrame df = chat.run(response);
    System.out.println("Result:   " + df.rows().get(0));
    System.out.println("---");
  }

  @Test
  void askAndRun_mostExpensiveProduct() {
    QueryResponse response = chat.ask("What is the most expensive product?");

    System.out.println("Question: What is the most expensive product?");
    System.out.println("SQL:      " + response.sql());
    System.out.println("Confidence: " + (int) (response.confidence() * 100) + "%");

    assert response.isSuccess() : "Expected success but got error: " + response.error();

    DataFrame df = chat.run(response);
    System.out.println("Result:   " + df.rows().get(0));
    System.out.println("---");
  }

  @Test
  void askAndRun_electronicProducts() {
    QueryResponse response = chat.ask("List all products in the Electronics category");

    System.out.println("Question: List all products in the Electronics category");
    System.out.println("SQL:      " + response.sql());
    System.out.println("Confidence: " + (int) (response.confidence() * 100) + "%");

    assert response.isSuccess() : "Expected success but got error: " + response.error();
    assert response.sql().toUpperCase().contains("SELECT")
        : "Expected SELECT in SQL: " + response.sql();

    DataFrame df = chat.run(response);
    System.out.println("Result:   " + df.rows());
    System.out.println("Rows:     " + df.rowCount());
    System.out.println("---");
  }
}
