package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class StreamingQueryChatTest {

  @Test
  void askStreaming_withCallback_returnsFullSql() {
    StreamingClientTest.MockStreamingLLMClient streaming =
        new StreamingClientTest.MockStreamingLLMClient("SELECT ", "COUNT(*)", " FROM ", "users");

    QueryChat chat = buildStreamingChat(streaming);

    List<String> received = new ArrayList<>();
    QueryResponse response =
        chat.askStreaming("How many users?", token -> received.add(token.text()));

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).contains("SELECT");
    assertThat(response.sql()).contains("users");
    assertThat(received).isNotEmpty();
  }

  @Test
  void askStreaming_withStream_deliversTokensLazily() {
    StreamingClientTest.MockStreamingLLMClient streaming =
        new StreamingClientTest.MockStreamingLLMClient("SELECT ", "name ", "FROM ", "products");

    QueryChat chat = buildStreamingChat(streaming);

    StringBuilder sb = new StringBuilder();
    try (Stream<StreamToken> tokens = chat.askStreaming("product names")) {
      tokens.filter(t -> !t.finished()).forEach(t -> sb.append(t.text()));
    }

    assertThat(sb.toString()).isEqualTo("SELECT name FROM products");
  }

  @Test
  void supportsStreaming_returnsTrueForStreamingClient() {
    StreamingClientTest.MockStreamingLLMClient streaming =
        new StreamingClientTest.MockStreamingLLMClient("x");
    QueryChat chat = buildStreamingChat(streaming);

    assertThat(chat.supportsStreaming()).isTrue();
  }

  @Test
  void supportsStreaming_returnsFalseForNonStreamingClient() {
    MockLLMClient mock = new MockLLMClient().withResponse("SELECT 1");
    QueryChat chat = buildNonStreamingChat(mock);

    assertThat(chat.supportsStreaming()).isFalse();
  }

  @Test
  void askStreaming_withNonStreamingClient_throwsUnsupported() {
    MockLLMClient mock = new MockLLMClient().withResponse("SELECT 1");
    QueryChat chat = buildNonStreamingChat(mock);

    assertThatThrownBy(() -> chat.askStreaming("test"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("StreamingLLMClient");
  }

  @Test
  void askStreaming_callback_withNonStreamingClient_throwsUnsupported() {
    MockLLMClient mock = new MockLLMClient().withResponse("SELECT 1");
    QueryChat chat = buildNonStreamingChat(mock);

    assertThatThrownBy(() -> chat.askStreaming("test", t -> {}))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("StreamingLLMClient");
  }

  @Test
  void askStreaming_callback_responseCanBeRunWithSqlGuard() {
    StreamingClientTest.MockStreamingLLMClient streaming =
        new StreamingClientTest.MockStreamingLLMClient("DELETE ", "FROM ", "users");

    QueryChat chat = buildStreamingChat(streaming, SqlGuard.readOnly());

    QueryResponse response = chat.askStreaming("delete users", t -> {});

    assertThat(response.sql()).contains("DELETE");
    assertThatThrownBy(() -> chat.runSql(response.sql()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blocked");
  }

  private QueryChat buildStreamingChat(StreamingLLMClient client) {
    return buildStreamingChat(client, SqlGuard.allowAll());
  }

  private QueryChat buildStreamingChat(StreamingLLMClient client, SqlGuard guard) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("streaming-test")
                .llmClient(client)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(4096L)
                .build())
        .sqlGuard(guard)
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .build()
        .queryChat();
  }

  private QueryChat buildNonStreamingChat(MockLLMClient mock) {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("non-streaming-test")
                .llmClient(mock)
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(4096L)
                .build())
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .build()
        .queryChat();
  }
}
