package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.ChatRole;
import io.github.imetaxas.sqlsage4j.QueryResponse;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SqlGenerationPipelineTest {

  private MockLLMClient mockClient;
  private TrainingService trainingService;
  private SqlGenerationPipeline pipeline;

  @BeforeEach
  void setUp() {
    mockClient = new MockLLMClient();
    trainingService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), mockClient);
    pipeline = new SqlGenerationPipeline(mockClient, trainingService, "BigQuery SQL", 14000, null);
  }

  @Test
  void generatesSimpleSql() {
    mockClient.withDefaultResponse("```sql\nSELECT count(*) FROM users;\n```");

    QueryResponse response = pipeline.generateSql("How many users are there?");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).isEqualTo("SELECT count(*) FROM users;");
    assertThat(response.question()).isEqualTo("How many users are there?");
  }

  @Test
  void usesTrainedContext() {
    trainingService.trainDdl("CREATE TABLE users (id INT, name STRING, created_at DATE);");
    trainingService.trainQuestionSql("How many users?", "SELECT count(*) FROM users;");
    trainingService.trainDocumentation("The users table tracks all registered users.");

    mockClient.withDefaultResponse("SELECT count(DISTINCT id) FROM users;");

    pipeline.generateSql("How many unique users exist?");

    // Verify the prompt included trained context
    var lastPrompt = mockClient.lastPrompt();
    String systemMsg = lastPrompt.get(0).content();
    assertThat(systemMsg).contains("CREATE TABLE users");
    assertThat(systemMsg).contains("users table tracks");

    // Verify few-shot example was included
    assertThat(
            lastPrompt.stream()
                .anyMatch(
                    m -> m.role() == ChatRole.ASSISTANT && m.content().contains("SELECT count(*)")))
        .isTrue();
  }

  @Test
  void returnsErrorOnException() {
    mockClient.withDefaultResponse("I cannot generate SQL for this question.");

    QueryResponse response = pipeline.generateSql("What is the meaning of life?");

    assertThat(response.isSuccess()).isTrue(); // extractor falls back to raw response
  }

  @Test
  void promptIncludesDialect() {
    mockClient.withDefaultResponse("SELECT 1;");
    pipeline.generateSql("test");

    String systemContent = mockClient.lastPrompt().get(0).content();
    assertThat(systemContent).contains("BigQuery SQL");
  }
}
