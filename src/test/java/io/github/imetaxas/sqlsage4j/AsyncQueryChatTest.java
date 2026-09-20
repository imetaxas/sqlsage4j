package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.it.fixture.TestDatabase;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class AsyncQueryChatTest {

  @Test
  void askAsync_returnsCompletableFuture() throws Exception {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    QueryChat chat = buildChat(llm);

    CompletableFuture<QueryResponse> future = chat.askAsync("test");

    QueryResponse response = future.get(5, TimeUnit.SECONDS);
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).contains("SELECT 1");
  }

  @Test
  void askAsync_withExecutor_usesProvidedPool() throws Exception {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 2");
    QueryChat chat = buildChat(llm);
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      CompletableFuture<QueryResponse> future = chat.askAsync("test", executor);
      QueryResponse response = future.get(5, TimeUnit.SECONDS);
      assertThat(response.sql()).contains("SELECT 2");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void runAsync_executesAndReturnsDataFrame() throws Exception {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT COUNT(*) FROM products");
      QueryChat chat = buildChatWithDb(llm, db);

      QueryResponse response = chat.ask("count products");
      CompletableFuture<io.github.imetaxas.sqlsage4j.db.DataFrame> future = chat.runAsync(response);

      io.github.imetaxas.sqlsage4j.db.DataFrame df = future.get(5, TimeUnit.SECONDS);
      assertThat(df.rowCount()).isEqualTo(1);
      assertThat(df.rows().get(0).get(0)).isEqualTo(5);
    }
  }

  @Test
  void askAsync_multipleConcurrent_allSucceed() throws Exception {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    QueryChat chat = buildChat(llm);

    CompletableFuture<QueryResponse> f1 = chat.askAsync("q1");
    CompletableFuture<QueryResponse> f2 = chat.askAsync("q2");
    CompletableFuture<QueryResponse> f3 = chat.askAsync("q3");

    CompletableFuture.allOf(f1, f2, f3).get(10, TimeUnit.SECONDS);

    assertThat(f1.get().isSuccess()).isTrue();
    assertThat(f2.get().isSuccess()).isTrue();
    assertThat(f3.get().isSuccess()).isTrue();
  }

  @Test
  void askAsync_chainingWithThenApply() throws Exception {
    try (TestDatabase db = TestDatabase.ecommerce()) {
      MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT COUNT(*) FROM customers");
      QueryChat chat = buildChatWithDb(llm, db);

      Integer rowCount =
          chat.askAsync("count customers")
              .thenApply(chat::run)
              .thenApply(io.github.imetaxas.sqlsage4j.db.DataFrame::rowCount)
              .get(5, TimeUnit.SECONDS);

      assertThat(rowCount).isEqualTo(1);
    }
  }

  private QueryChat buildChat(MockLLMClient llm) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("test")
                .llmClient(llm)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(4096L)
                .build())
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .build()
        .queryChat();
  }

  private QueryChat buildChatWithDb(MockLLMClient llm, TestDatabase db) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("test")
                .llmClient(llm)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(4096L)
                .build())
        .databaseConnector(db.connector())
        .prompt(
            Prompt.builder()
                .userPrompt(PromptEnum.SQL_EXPERT)
                .ddls(List.of(new DDL("CREATE TABLE products (product_id INT)")))
                .build())
        .build()
        .queryChat();
  }
}
