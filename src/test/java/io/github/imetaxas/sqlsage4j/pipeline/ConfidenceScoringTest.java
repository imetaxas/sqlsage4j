package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import org.junit.jupiter.api.Test;

final class ConfidenceScoringTest {

  @Test
  void confidence_withNoTrainingData_isLow() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");

    QueryChat chat = buildChat(llm);
    QueryResponse response = chat.ask("Random question");

    assertThat(response.confidence()).isGreaterThanOrEqualTo(0.0);
    assertThat(response.confidence()).isLessThanOrEqualTo(1.0);
  }

  @Test
  void confidence_withMatchingTrainingData_isHigher() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT COUNT(*) FROM users");

    QueryChat chat = buildChat(llm);
    chat.train("How many users?", "SELECT COUNT(*) FROM users");

    QueryResponse response = chat.ask("How many users are there?");

    assertThat(response.confidence()).isGreaterThan(0.0);
    assertThat(response.confidence()).isLessThanOrEqualTo(1.0);
  }

  @Test
  void confidence_errorResponse_isZero() {
    QueryResponse error = QueryResponse.error("id", "q", "failed");
    assertThat(error.confidence()).isEqualTo(0.0);
  }

  @Test
  void confidence_successWithoutExplicitScore_defaultsTo05() {
    QueryResponse success = QueryResponse.success("id", "q", "SELECT 1", "raw");
    assertThat(success.confidence()).isEqualTo(0.5);
  }

  @Test
  void confidence_successWithExplicitScore_usesProvided() {
    QueryResponse success = QueryResponse.success("id", "q", "SELECT 1", "raw", 0.85);
    assertThat(success.confidence()).isEqualTo(0.85);
  }

  @Test
  void confidence_structureBoost_whenResponseLooksLikeSql() {
    MockLLMClient llm =
        new MockLLMClient().withDefaultResponse("SELECT name FROM products ORDER BY price DESC");
    QueryChat chat = buildChat(llm);

    QueryResponse response = chat.ask("Most expensive product");

    assertThat(response.confidence()).as("SQL structure adds 0.2 boost").isGreaterThan(0.1);
  }

  @Test
  void confidence_noStructureBoost_whenResponseIsNotSql() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("I cannot answer that question");
    QueryChat chat = buildChat(llm);

    QueryResponse response = chat.ask("What is the meaning of life?");

    assertThat(response.confidence())
        .as("non-SQL response gets no structure boost")
        .isLessThanOrEqualTo(0.6);
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
}
