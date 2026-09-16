package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.diff.DiffResult;
import io.github.imetaxas.sqlsage4j.diff.LLMDiff;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests verifying the full SqlSage4j pipeline from builder to query response using mock
 * LLM and embeddings.
 */
final class SqlSage4jEndToEndTest {

  @Test
  void fullQueryChatPipeline() {
    MockLLMClient mockClient = new MockLLMClient();
    MockEmbeddingsProvider mockEmbeddings = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage mockStorage = new InMemoryEmbeddingsStorage();

    // Wire up manually since SqlSage4j.builder uses LLMClientFactory which creates OpenAIClient
    TrainingService trainingService = new TrainingService(mockEmbeddings, mockStorage, mockClient);

    trainingService.trainDdl("CREATE TABLE users (id INT, name STRING, email STRING);");
    trainingService.trainDdl(
        "CREATE TABLE orders (id INT, user_id INT, amount DECIMAL, date DATE);");
    trainingService.trainQuestionSql("How many users?", "SELECT count(*) FROM users;");
    trainingService.trainDocumentation("The users table contains all registered users.");

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(mockClient, trainingService, "BigQuery SQL", 14000, null);

    mockClient.withResponse(
        "SELECT count(DISTINCT u.id) FROM users u JOIN orders o ON u.id = o.user_id;");

    QueryChat chat = new QueryChat(mockClient, pipeline, trainingService, null);
    QueryResponse response = chat.ask("How many users have placed orders?");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).contains("SELECT");
    assertThat(response.sql()).contains("users");
  }

  @Test
  void fullLLMChatPipeline() {
    MockLLMClient mockClient = new MockLLMClient();
    MockEmbeddingsProvider mockEmbeddings = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage mockStorage = new InMemoryEmbeddingsStorage();

    TrainingService trainingService = new TrainingService(mockEmbeddings, mockStorage, mockClient);
    trainingService.trainDocumentation("Revenue is calculated as the sum of all order amounts.");

    mockClient.withResponse("Revenue is the total of all order amounts in the system.");

    LLMChat chat = new LLMChat(mockClient, trainingService, "You are a data assistant.");
    ChatResponse response = chat.ask("What is revenue?");

    assertThat(response.content()).contains("Revenue");
    assertThat(chat.getHistory()).hasSize(2); // user + assistant
  }

  @Test
  void followupQuestions() {
    MockLLMClient mockClient = new MockLLMClient();
    MockEmbeddingsProvider mockEmbeddings = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage mockStorage = new InMemoryEmbeddingsStorage();
    TrainingService trainingService = new TrainingService(mockEmbeddings, mockStorage, mockClient);
    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(mockClient, trainingService, "SQL", 14000, null);

    QueryChat chat = new QueryChat(mockClient, pipeline, trainingService, null);

    mockClient.withResponse("SELECT count(*) FROM users;"); // for ask()
    chat.ask("How many users?");

    mockClient.withResponse(
        "1. How many users signed up this month?\n2. What is the average user age?\n3. Which users are most active?");

    List<String> followups =
        chat.generateFollowupQuestions(
            "How many users?", "SELECT count(*) FROM users;", "| count |\n| 100 |");

    assertThat(followups).hasSize(3);
    assertThat(followups.get(0)).contains("users");
  }

  @Test
  void trainFeedbackLoop() {
    MockLLMClient mockClient = new MockLLMClient();
    MockEmbeddingsProvider mockEmbeddings = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage mockStorage = new InMemoryEmbeddingsStorage();
    TrainingService trainingService = new TrainingService(mockEmbeddings, mockStorage, mockClient);
    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(mockClient, trainingService, "SQL", 14000, null);

    QueryChat chat = new QueryChat(mockClient, pipeline, trainingService, null);

    chat.train("How many active users?", "SELECT count(*) FROM users WHERE active = true;");

    List<QuestionAnswer> similar = trainingService.getSimilarQuestionSql("active users count");
    assertThat(similar).isNotEmpty();
    assertThat(similar.get(0).sql()).contains("active = true");
  }

  @Test
  void llmDiff() {
    MockLLMClient clientA = new MockLLMClient().withDefaultResponse("SELECT count(*) FROM users;");
    MockLLMClient clientB =
        new MockLLMClient().withDefaultResponse("SELECT count(DISTINCT id) FROM users;");

    MockEmbeddingsProvider embProv = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage storageA = new InMemoryEmbeddingsStorage();
    InMemoryEmbeddingsStorage storageB = new InMemoryEmbeddingsStorage();

    TrainingService tsA = new TrainingService(embProv, storageA, clientA);
    TrainingService tsB = new TrainingService(embProv, storageB, clientB);

    SqlGenerationPipeline pipeA = new SqlGenerationPipeline(clientA, tsA, "SQL", 14000, null);
    SqlGenerationPipeline pipeB = new SqlGenerationPipeline(clientB, tsB, "SQL", 14000, null);

    QueryChat chatA = new QueryChat(clientA, pipeA, tsA, null);
    QueryChat chatB = new QueryChat(clientB, pipeB, tsB, null);

    DiffResult result = LLMDiff.of(chatA, chatB).run("How many users?", 3);

    assertThat(result.totalRuns()).isEqualTo(3);
    assertThat(result.successCountA()).isEqualTo(3L);
    assertThat(result.successCountB()).isEqualTo(3L);
    assertThat(result.matchingCount()).isEqualTo(0L); // different SQL
    assertThat(result.responsesA().get(0).sql()).contains("count(*)");
    assertThat(result.responsesB().get(0).sql()).contains("count(DISTINCT");
  }

  @Test
  void sqlSage4jBuilderWithEmbeddings() {
    MockEmbeddingsProvider mockEmbeddings = new MockEmbeddingsProvider();
    InMemoryEmbeddingsStorage mockStorage = new InMemoryEmbeddingsStorage();

    SqlSage4j sqlSage4j =
        SqlSage4j.builder(
                LLMProviderConfig.builder("gpt-4o")
                    .apiKey("test-key")
                    .maxTokens(2048L)
                    .temperature(0.7)
                    .embeddingsProvider(mockEmbeddings)
                    .embeddingsStorage(mockStorage)
                    .build())
            .train(
                List.of(
                    new DDL("CREATE TABLE users (id INT, name TEXT);"),
                    new QuestionAnswer("How many users?", "SELECT count(*) FROM users;")))
            .prompt(Prompt.builder().userPrompt(PromptEnum.DATA_SCIENTIST).build())
            .build();

    assertThat(sqlSage4j).as("sqlSage4j instance").isNotNull();
    assertThat(sqlSage4j.llmProviderConfig().modelName()).isEqualTo("gpt-4o");
  }
}
