package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.*;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.SchemaIntrospector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.it.fixture.TestDatabase;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.BM25Storage;
import io.github.imetaxas.sqlsage4j.storage.HybridEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingDataIO;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for recently added features. Each test exercises the full
 * user-facing workflow through the builder API with a real SQLite database.
 */
final class NewFeaturesEndToEndIT {

  // ──────────────────────────────────────────────────────────────────────────
  // 1. SQL Safety Guardrails — via builder → ask → run flow
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void sqlGuard_blocksDestructiveSqlBeforeExecution() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      llm.withResponse("DELETE FROM products WHERE stock_quantity = 0");

      QueryChat chat =
          buildChat(
              llm,
              new MockEmbeddingsProvider(),
              new InMemoryEmbeddingsStorage(),
              db,
              SqlGuard.readOnly());
      QueryResponse response = chat.ask("Remove out of stock products");

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.sql()).contains("DELETE");

      assertThatThrownBy(() -> chat.run(response))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blocked by safety guard");

      DataFrame verify = db.connector().runSql("SELECT COUNT(*) FROM products");
      assertThat(verify.rows().get(0).get(0)).as("no rows deleted").isEqualTo(5);
    }
  }

  @Test
  void sqlGuard_allowsSelectToExecute() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      llm.withResponse("SELECT COUNT(*) AS cnt FROM products");

      QueryChat chat =
          buildChat(
              llm,
              new MockEmbeddingsProvider(),
              new InMemoryEmbeddingsStorage(),
              db,
              SqlGuard.readOnly());
      QueryResponse response = chat.ask("How many products?");
      DataFrame df = chat.run(response);

      assertThat(df.rowCount()).isEqualTo(1);
      assertThat(df.rows().get(0).get(0)).isEqualTo(5);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 2. Auto-Schema Introspection — trainFromDatabase → ask uses discovered DDLs
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void schemaIntrospection_trainFromDatabaseProvidesContextForRetrieval() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      llm.withResponse("SELECT COUNT(*) FROM customers");

      QueryChat chat =
          buildChat(
              llm,
              new MockEmbeddingsProvider(),
              new InMemoryEmbeddingsStorage(),
              db,
              SqlGuard.allowAll());

      int tablesDiscovered = chat.trainFromDatabase(db.dataSource());
      assertThat(tablesDiscovered).isGreaterThanOrEqualTo(4);

      QueryResponse response = chat.ask("How many customers?");
      assertThat(response.isSuccess()).isTrue();

      String allData = chat.getAllTrainedData();
      assertThat(allData).contains("customers");
      assertThat(allData).contains("products");
      assertThat(allData).contains("orders");
    }
  }

  @Test
  void schemaIntrospection_discoveredDdlsExecuteAgainstRealDb() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      SchemaIntrospector introspector = new SchemaIntrospector(db.dataSource());
      List<String> ddls = introspector.introspect();

      assertThat(ddls.size()).isGreaterThanOrEqualTo(4);
      for (String ddl : ddls) {
        assertThat(ddl).contains("CREATE TABLE");
      }

      String customersDdl =
          ddls.stream()
              .filter(d -> d.contains("TABLE customers") || d.contains("TABLE main.customers"))
              .findFirst()
              .orElse("");
      assertThat(customersDdl).contains("customer_id");
      assertThat(customersDdl).contains("email");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 3. Hybrid Search — HybridEmbeddingsStorage through full pipeline
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void hybridSearch_fullPipelineWithVectorAndBm25() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      llm.withResponse("SELECT name, price FROM products ORDER BY price DESC");

      InMemoryEmbeddingsStorage vectorStore = new InMemoryEmbeddingsStorage();
      BM25Storage bm25Store = new BM25Storage();
      HybridEmbeddingsStorage hybrid = new HybridEmbeddingsStorage(vectorStore, bm25Store, 0.5);

      QueryChat chat =
          buildChat(llm, new MockEmbeddingsProvider(), hybrid, db, SqlGuard.allowAll());

      chat.trainDdl(
          "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)");
      chat.train(
          "Most expensive products?", "SELECT name, price FROM products ORDER BY price DESC");

      QueryResponse response = chat.ask("Most expensive products?");
      assertThat(response.isSuccess()).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(5);
      assertThat(df.columns()).containsExactly("name", "price");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 4. Training Data Import/Export — export → import → same query quality
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void trainingDataImportExport_roundTripPreservesQueryCapability() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      MockEmbeddingsProvider emb = new MockEmbeddingsProvider();

      TrainingService originalService =
          new TrainingService(emb, new InMemoryEmbeddingsStorage(), llm);
      originalService.trainDdl("CREATE TABLE products (product_id INT, name TEXT, price REAL)");
      originalService.trainDdl("CREATE TABLE orders (order_id INT, customer_id INT, total REAL)");
      originalService.trainQuestionSql(
          "Revenue?", "SELECT SUM(total_amount) FROM orders WHERE status = 'completed'");
      originalService.trainDocumentation("Only completed orders count as revenue.");

      String exported = TrainingDataIO.exportToString(originalService);
      assertThat(exported).contains("products");
      assertThat(exported).contains("Revenue?");
      assertThat(exported).contains("completed orders");

      TrainingService importedService =
          new TrainingService(emb, new InMemoryEmbeddingsStorage(), llm);
      int imported = TrainingDataIO.importFromString(importedService, exported);
      assertThat(imported).isEqualTo(4);

      llm.withResponse(
          "SELECT SUM(total_amount) AS revenue FROM orders WHERE status = 'completed'");

      QueryChat chat =
          new QueryChat(
              llm,
              new io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline(
                  llm, importedService, "SQLite", 14000, null, db.connector()),
              importedService,
              db.connector());

      QueryResponse response = chat.ask("What is the total revenue?");
      assertThat(response.isSuccess()).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(1);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 5. RetryingLLMClient — transient failure → retry → success with real DB
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void retryingClient_recoversFromTransientFailureAndExecutesSql() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      AtomicInteger attempts = new AtomicInteger();
      LLMClient flaky =
          new LLMClient() {
            @Override
            public String submitPrompt(List<ChatMessage> messages) {
              if (attempts.incrementAndGet() <= 2) {
                throw new RuntimeException("HTTP 429 Too Many Requests");
              }
              return "SELECT COUNT(*) AS product_count FROM products";
            }

            @Override
            public String modelName() {
              return "flaky-model";
            }
          };

      RetryingLLMClient resilient =
          RetryingLLMClient.builder(flaky)
              .maxAttempts(5)
              .initialDelay(Duration.ofMillis(10))
              .retryOn(e -> e.getMessage().contains("429"))
              .build();

      QueryChat chat =
          buildChat(
              resilient,
              new MockEmbeddingsProvider(),
              new InMemoryEmbeddingsStorage(),
              db,
              SqlGuard.allowAll());
      chat.trainDdl("CREATE TABLE products (product_id INT, name TEXT, price REAL)");

      QueryResponse response = chat.ask("How many products?");
      assertThat(response.isSuccess()).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rows().get(0).get(0)).isEqualTo(5);
      assertThat(attempts.get()).isEqualTo(3);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 6. CachingLLMClient — same question twice → LLM called only once
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void cachingClient_twoSessionsSameQuestionCallsLlmOnce() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient underlying =
          new MockLLMClient().withDefaultResponse("SELECT COUNT(*) AS cnt FROM products");

      CachingLLMClient cached =
          CachingLLMClient.builder(underlying).maxEntries(100).ttl(Duration.ofMinutes(5)).build();

      MockEmbeddingsProvider embeddings = new MockEmbeddingsProvider();
      InMemoryEmbeddingsStorage storage = new InMemoryEmbeddingsStorage();
      TrainingService sharedTraining = new TrainingService(embeddings, storage, cached);
      sharedTraining.trainDdl("CREATE TABLE products (product_id INT, name TEXT, price REAL)");

      io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline pipeline =
          new io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline(
              cached, sharedTraining, "SQLite", 14000, null, db.connector());

      QueryChat session1 = new QueryChat(cached, pipeline, sharedTraining, db.connector());

      int callsBeforeAsk = underlying.callCount();

      QueryResponse first = session1.ask("How many products?");
      int callsAfterFirst = underlying.callCount();
      assertThat(callsAfterFirst).as("first ask calls LLM").isGreaterThan(callsBeforeAsk);

      QueryChat session2 = new QueryChat(cached, pipeline, sharedTraining, db.connector());

      QueryResponse second = session2.ask("How many products?");
      int callsAfterSecond = underlying.callCount();
      assertThat(callsAfterSecond)
          .as("second session uses cache, no extra LLM call")
          .isEqualTo(callsAfterFirst);

      assertThat(first.sql()).isEqualTo(second.sql());

      DataFrame df1 = session1.run(first);
      DataFrame df2 = session2.run(second);
      assertThat(df1.rows().get(0).get(0)).isEqualTo(df2.rows().get(0).get(0));
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 7. BM25 + NoOp — embedding-free mode full pipeline
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void bm25NoOpMode_fullPipelineWithoutEmbeddingModel() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient();
      llm.withResponse(
          "SELECT name, price FROM products WHERE category = 'Electronics' ORDER BY price DESC");

      NoOpEmbeddingsProvider noOp = new NoOpEmbeddingsProvider();
      BM25Storage bm25 = new BM25Storage();

      QueryChat chat = buildChat(llm, noOp, bm25, db, SqlGuard.allowAll());

      chat.trainDdl(
          "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)");
      chat.trainDdl(
          "CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, total_amount REAL, status TEXT)");
      chat.train(
          "Electronics products?",
          "SELECT name, price FROM products WHERE category = 'Electronics'");
      chat.trainDocumentation("Products have categories: Electronics, Furniture, Stationery.");

      QueryResponse response = chat.ask("Show me electronics sorted by price");
      assertThat(response.isSuccess()).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(3);
      assertThat(df.columns()).containsExactly("name", "price");
    }
  }

  @Test
  void bm25NoOpMode_keywordRetrievalFindsRelevantTrainingData() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    NoOpEmbeddingsProvider noOp = new NoOpEmbeddingsProvider();
    BM25Storage bm25 = new BM25Storage();

    TrainingService ts = new TrainingService(noOp, bm25, llm);
    ts.trainDdl("CREATE TABLE user_sessions (session_id INT, user_id INT, duration_ms INT)");
    ts.trainDdl("CREATE TABLE page_views (view_id INT, session_id INT, url TEXT)");
    ts.trainQuestionSql("Average session duration?", "SELECT AVG(duration_ms) FROM user_sessions");
    ts.trainQuestionSql(
        "Page views per session?",
        "SELECT session_id, COUNT(*) FROM page_views GROUP BY session_id");

    List<QuestionAnswer> similar = ts.getSimilarQuestionSql("session duration");
    assertThat(similar).isNotEmpty();
    assertThat(similar.get(0).sql()).contains("user_sessions");
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helper
  // ──────────────────────────────────────────────────────────────────────────

  private QueryChat buildChat(
      LLMClient llm,
      io.github.imetaxas.sqlsage4j.provider.EmbeddingsProvider embeddings,
      io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage storage,
      TestDatabase db,
      SqlGuard guard) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("test-model")
                .llmClient(llm)
                .embeddingsProvider(embeddings)
                .embeddingsStorage(storage)
                .maxTokens(14000L)
                .build())
        .databaseConnector(db.connector())
        .sqlGuard(guard)
        .prompt(
            Prompt.builder()
                .userPrompt(PromptEnum.SQL_EXPERT)
                .ddls(
                    List.of(
                        new DDL(
                            "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL)")))
                .build())
        .build()
        .queryChat();
  }
}
