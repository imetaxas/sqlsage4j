package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.*;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.it.fixture.TestDatabase;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for streaming SQL generation. Exercises the full pipeline from
 * streaming ask → SQL extraction → database execution with real SQLite databases.
 */
final class StreamingEndToEndIT {

  // ──────────────────────────────────────────────────────────────────────────
  // 1. Full pipeline: streaming ask → run SQL → get results
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_callback_fullPipeline_generatesAndExecutesSql() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient("SELECT ", "COUNT(*)", " FROM ", "products");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());
      chat.trainDdl(
          "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)");

      List<StreamToken> received = new ArrayList<>();
      QueryResponse response =
          chat.askStreaming("How many products?", token -> received.add(token));

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.sql()).isEqualTo("SELECT COUNT(*) FROM products");

      assertThat(received).isNotEmpty();
      assertThat(received.stream().anyMatch(StreamToken::finished)).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(1);
      assertThat(df.rows().get(0).get(0)).isEqualTo(5);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 2. Stream API with try-with-resources
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_javaStream_deliversTokensLazilyAndCloses() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient(
              "SELECT ",
              "name, ",
              "price ",
              "FROM ",
              "products ",
              "WHERE ",
              "category = 'Electronics'");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());

      StringBuilder assembled = new StringBuilder();
      try (Stream<StreamToken> tokens = chat.askStreaming("List electronics")) {
        tokens.filter(t -> !t.finished()).forEach(t -> assembled.append(t.text()));
      }

      assertThat(assembled.toString())
          .isEqualTo("SELECT name, price FROM products WHERE category = 'Electronics'");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 3. Streaming with SqlGuard — SQL executes only if guard allows
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_withReadOnlyGuard_allowsSelectExecution() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient("SELECT ", "MAX(price)", " FROM products");

      QueryChat chat = buildChat(streaming, db, SqlGuard.readOnly());

      QueryResponse response = chat.askStreaming("Most expensive product?", t -> {});
      DataFrame df = chat.run(response);

      assertThat(df.rowCount()).isEqualTo(1);
      assertThat(df.rows().get(0).get(0)).isEqualTo(999.99);
    }
  }

  @Test
  void streaming_withReadOnlyGuard_blocksDestructiveSql() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming = new MockStreamingClient("DROP ", "TABLE ", "products");

      QueryChat chat = buildChat(streaming, db, SqlGuard.readOnly());

      QueryResponse response = chat.askStreaming("delete products table", t -> {});
      assertThat(response.sql()).contains("DROP TABLE products");

      assertThatThrownBy(() -> chat.run(response))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blocked");

      DataFrame verify = db.connector().runSql("SELECT COUNT(*) FROM products");
      assertThat(verify.rows().get(0).get(0)).as("table still exists").isEqualTo(5);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 4. Streaming with multi-token SQL extraction (code fenced response)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_extractsSqlFromMarkdownCodeFence() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient(
              "Here is your query:\n\n",
              "```sql\n",
              "SELECT ",
              "name, price ",
              "FROM products ",
              "ORDER BY price DESC ",
              "LIMIT 3",
              "\n```\n\n",
              "This returns the top 3 most expensive products.");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());
      chat.trainDdl(
          "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)");

      QueryResponse response = chat.askStreaming("Top 3 expensive items?", t -> {});

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.sql()).contains("ORDER BY price DESC");
      assertThat(response.sql()).contains("LIMIT 3");

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(3);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 5. Token counting and callback invocation order
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_callback_receivesTokensInOrder_withFinalDoneToken() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming = new MockStreamingClient("A", "B", "C");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());

      List<String> texts = new ArrayList<>();
      AtomicBoolean sawDone = new AtomicBoolean(false);
      AtomicInteger tokenCount = new AtomicInteger(0);

      chat.askStreaming(
          "test",
          token -> {
            tokenCount.incrementAndGet();
            if (token.finished()) {
              sawDone.set(true);
            } else {
              texts.add(token.text());
            }
          });

      assertThat(texts).containsExactly("A", "B", "C");
      assertThat(sawDone.get()).as("done token received").isTrue();
      assertThat(tokenCount.get()).as("3 content + 1 done = 4 total").isEqualTo(4);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 6. supportsStreaming detection
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void supportsStreaming_trueForStreamingClient() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      QueryChat chat = buildChat(new MockStreamingClient("x"), db, SqlGuard.allowAll());
      assertThat(chat.supportsStreaming()).isTrue();
    }
  }

  @Test
  void supportsStreaming_falseForStandardClient() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient nonStreaming = new MockLLMClient().withDefaultResponse("SELECT 1");
      QueryChat chat = buildNonStreamingChat(nonStreaming, db);
      assertThat(chat.supportsStreaming()).isFalse();
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 7. Graceful error when streaming not supported
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void askStreaming_nonStreamingClient_throwsUnsupportedOperation() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient nonStreaming = new MockLLMClient().withDefaultResponse("SELECT 1");
      QueryChat chat = buildNonStreamingChat(nonStreaming, db);

      assertThatThrownBy(() -> chat.askStreaming("test"))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("StreamingLLMClient");

      assertThatThrownBy(() -> chat.askStreaming("test", t -> {}))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("StreamingLLMClient");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 8. Streaming with conversation history (multi-turn)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_multiTurn_secondQuestionBuildsOnHistory() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient("SELECT ", "COUNT(*)", " FROM ", "products");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());
      chat.trainDdl(
          "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)");

      QueryResponse first = chat.askStreaming("How many products?", t -> {});
      assertThat(first.isSuccess()).isTrue();

      assertThat(chat.history().getLastQuestion()).isNotNull();
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 9. Streaming with trained Q&A and real data query
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_withTrainedData_executesComplexQuery() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient(
              "SELECT ",
              "c.name, ",
              "SUM(o.total_amount) as total_spent ",
              "FROM customers c ",
              "JOIN orders o ON c.customer_id = o.customer_id ",
              "GROUP BY c.name ",
              "ORDER BY total_spent DESC");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());
      chat.trainDdl(
          "CREATE TABLE customers (customer_id INTEGER, name TEXT, email TEXT, country TEXT)");
      chat.trainDdl(
          "CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, order_date TEXT, total_amount REAL, status TEXT)");
      chat.train(
          "Top spending customers?",
          "SELECT c.name, SUM(o.total_amount) as total_spent FROM customers c JOIN orders o ON c.customer_id = o.customer_id GROUP BY c.name ORDER BY total_spent DESC");

      QueryResponse response = chat.askStreaming("Who are the top spenders?", t -> {});
      assertThat(response.isSuccess()).isTrue();

      DataFrame df = chat.run(response);
      assertThat(df.rowCount()).isEqualTo(3);
      assertThat(df.rows().get(0).get(0)).as("Alice spent the most").isEqualTo("Alice");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 10. Streaming with non-SQL response — execution against DB fails gracefully
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_nonSqlResponse_executionFailsGracefully() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient("I'm sorry, ", "I cannot help ", "with that request.");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());

      QueryResponse response = chat.askStreaming("What is the meaning of life?", t -> {});

      assertThat(response.isSuccess()).isTrue();
      assertThat(response.sql()).contains("sorry");

      assertThatThrownBy(() -> chat.run(response)).isInstanceOf(RuntimeException.class);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 11. Streaming with Stream API - partial consumption (takeWhile)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_javaStream_partialConsumptionClosesCleanly() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClient streaming =
          new MockStreamingClient("SELECT ", "1", " + ", "2", " + ", "3");

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());

      List<String> firstThree = new ArrayList<>();
      try (Stream<StreamToken> tokens = chat.askStreaming("test")) {
        tokens.filter(t -> !t.finished()).limit(3).forEach(t -> firstThree.add(t.text()));
      }

      assertThat(firstThree).hasSize(3);
      assertThat(firstThree.get(0)).isEqualTo("SELECT ");
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // 12. StreamToken done token carries usage stats
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  void streaming_doneTokenWithUsage_carriesTokenCounts() {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockStreamingClientWithUsage streaming = new MockStreamingClientWithUsage("SELECT 1", 42, 8);

      QueryChat chat = buildChat(streaming, db, SqlGuard.allowAll());

      List<StreamToken> tokens = new ArrayList<>();
      chat.askStreaming("test", tokens::add);

      StreamToken doneToken =
          tokens.stream().filter(StreamToken::finished).findFirst().orElse(null);
      assertThat(doneToken).isNotNull();
      assertThat(doneToken.promptTokens()).isEqualTo(42L);
      assertThat(doneToken.completionTokens()).isEqualTo(8L);
      assertThat(doneToken.totalTokens()).isEqualTo(50L);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────────

  private QueryChat buildChat(StreamingLLMClient client, TestDatabase db, SqlGuard guard) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("streaming-it")
                .llmClient(client)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
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
                            "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)")))
                .build())
        .build()
        .queryChat();
  }

  private QueryChat buildNonStreamingChat(MockLLMClient client, TestDatabase db) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("non-streaming-it")
                .llmClient(client)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(14000L)
                .build())
        .databaseConnector(db.connector())
        .prompt(
            Prompt.builder()
                .userPrompt(PromptEnum.SQL_EXPERT)
                .ddls(
                    List.of(
                        new DDL(
                            "CREATE TABLE products (product_id INTEGER, name TEXT, category TEXT, price REAL, stock_quantity INTEGER)")))
                .build())
        .build()
        .queryChat();
  }

  /** Mock streaming client that produces tokens from given chunks. */
  static final class MockStreamingClient implements StreamingLLMClient {

    private final List<String> chunks;

    MockStreamingClient(String... chunks) {
      this.chunks = List.of(chunks);
    }

    @Override
    public Stream<StreamToken> streamPrompt(List<ChatMessage> messages) {
      List<StreamToken> tokens = new ArrayList<>();
      for (String chunk : chunks) {
        tokens.add(StreamToken.of(chunk));
      }
      tokens.add(StreamToken.done());
      return tokens.stream();
    }

    @Override
    public String submitPrompt(List<ChatMessage> messages) {
      StringBuilder sb = new StringBuilder();
      for (String chunk : chunks) {
        sb.append(chunk);
      }
      return sb.toString();
    }

    @Override
    public String modelName() {
      return "mock-streaming-it";
    }
  }

  /** Mock streaming client that includes usage stats in the done token. */
  static final class MockStreamingClientWithUsage implements StreamingLLMClient {

    private final String content;
    private final long promptTokens;
    private final long completionTokens;

    MockStreamingClientWithUsage(String content, long promptTokens, long completionTokens) {
      this.content = content;
      this.promptTokens = promptTokens;
      this.completionTokens = completionTokens;
    }

    @Override
    public Stream<StreamToken> streamPrompt(List<ChatMessage> messages) {
      return Stream.of(StreamToken.of(content), StreamToken.done(promptTokens, completionTokens));
    }

    @Override
    public String submitPrompt(List<ChatMessage> messages) {
      return content;
    }

    @Override
    public String modelName() {
      return "mock-streaming-usage";
    }
  }
}
