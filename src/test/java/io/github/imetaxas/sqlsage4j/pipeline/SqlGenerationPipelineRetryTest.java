package io.github.imetaxas.sqlsage4j.pipeline;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.QueryResponse;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

final class SqlGenerationPipelineRetryTest {

  private MockLLMClient mockClient;
  private TrainingService trainingService;
  private DatabaseConnector dbConnector;
  private Connection sharedConn;

  @BeforeEach
  void setUp() throws Exception {
    mockClient = new MockLLMClient();
    trainingService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), mockClient);

    SQLiteDataSource ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite::memory:");
    sharedConn = ds.getConnection();
    try (Statement stmt = sharedConn.createStatement()) {
      stmt.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)");
      stmt.execute("INSERT INTO users VALUES (1, 'Alice', 'alice@test.com')");
    }

    dbConnector =
        new DatabaseConnector() {
          @Override
          public DataFrame runSql(String sql) {
            try (Statement stmt = sharedConn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
              ResultSetMetaData meta = rs.getMetaData();
              List<String> cols = new ArrayList<>();
              for (int i = 1; i <= meta.getColumnCount(); i++) cols.add(meta.getColumnName(i));
              List<List<Object>> rows = new ArrayList<>();
              while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) row.add(rs.getObject(i));
                rows.add(row);
              }
              return new DataFrame(cols, rows);
            } catch (Exception e) {
              throw new RuntimeException(e.getMessage(), e);
            }
          }

          @Override
          public boolean isValidSql(String sql) {
            try (Statement stmt = sharedConn.createStatement()) {
              stmt.execute("EXPLAIN QUERY PLAN " + sql);
              return true;
            } catch (Exception e) {
              return false;
            }
          }

          @Override
          public String dialect() {
            return "SQLite";
          }
        };
  }

  @AfterEach
  void tearDown() throws Exception {
    if (sharedConn != null && !sharedConn.isClosed()) {
      sharedConn.close();
    }
  }

  @Test
  void validSql_noRetryAttempted() {
    mockClient.withDefaultResponse("SELECT * FROM users");
    RecordingListener listener = new RecordingListener();

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            mockClient, trainingService, "SQLite", 14000, null, dbConnector, listener);

    QueryResponse response = pipeline.generateSql("Show all users");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).isEqualTo("SELECT * FROM users");
    assertThat(listener.retryCount).isEqualTo(0);
    assertThat(mockClient.callCount()).isEqualTo(1);
  }

  @Test
  void invalidSql_retriesThenReturnsCorrected() {
    mockClient.withResponse("SELECT * FROM nonexistent_table").withResponse("SELECT * FROM users");
    RecordingListener listener = new RecordingListener();

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            mockClient, trainingService, "SQLite", 14000, null, dbConnector, listener);

    QueryResponse response = pipeline.generateSql("Show all users");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).isEqualTo("SELECT * FROM users");
    assertThat(listener.retryCount).isEqualTo(1);
    assertThat(mockClient.callCount()).isEqualTo(2);
  }

  @Test
  void invalidSql_retryAlsoFails_returnsLastAttempt() {
    mockClient.withResponse("SELECT * FROM bad_table").withResponse("SELECT * FROM still_bad");
    RecordingListener listener = new RecordingListener();

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            mockClient, trainingService, "SQLite", 14000, null, dbConnector, listener);

    QueryResponse response = pipeline.generateSql("Show something");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).isEqualTo("SELECT * FROM still_bad");
    assertThat(listener.retryCount).isEqualTo(1);
  }

  @Test
  void retryExceptionIsCaught_returnsOriginalSql() {
    LLMClient failingClient =
        new LLMClient() {
          private int calls = 0;

          @Override
          public String submitPrompt(List<io.github.imetaxas.sqlsage4j.ChatMessage> messages) {
            calls++;
            if (calls == 1) return "SELECT * FROM missing";
            throw new RuntimeException("LLM unavailable");
          }

          @Override
          public String modelName() {
            return "fail-model";
          }
        };

    TrainingService ts =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), failingClient);

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            failingClient, ts, "SQLite", 14000, null, dbConnector, PipelineListener.NOOP);

    QueryResponse response = pipeline.generateSql("test");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).isEqualTo("SELECT * FROM missing");
  }

  @Test
  void listenerReceivesAllCallbacks() {
    mockClient.withDefaultResponse("SELECT name FROM users WHERE id = 1");
    RecordingListener listener = new RecordingListener();

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            mockClient, trainingService, "SQLite", 14000, null, dbConnector, listener);

    pipeline.generateSql("Get user name for id 1");

    assertThat(listener.contextRetrieved).isTrue();
    assertThat(listener.promptAssembled).isTrue();
    assertThat(listener.llmResponded).isTrue();
    assertThat(listener.sqlExtracted).isTrue();
    assertThat(listener.pipelineCompleted).isTrue();
    assertThat(listener.success).isTrue();
  }

  private static final class RecordingListener implements PipelineListener {
    boolean contextRetrieved;
    boolean promptAssembled;
    boolean llmResponded;
    boolean sqlExtracted;
    boolean pipelineCompleted;
    boolean success;
    int retryCount;

    @Override
    public void onContextRetrieved(int qaCount, int ddlCount, int docCount) {
      contextRetrieved = true;
    }

    @Override
    public void onPromptAssembled(
        List<io.github.imetaxas.sqlsage4j.ChatMessage> messages, int estimatedTokens) {
      promptAssembled = true;
    }

    @Override
    public void onLLMResponse(String response, long latencyMs) {
      llmResponded = true;
    }

    @Override
    public void onSqlExtracted(String sql) {
      sqlExtracted = true;
    }

    @Override
    public void onValidationRetry(String failedSql, String error, int attempt) {
      retryCount++;
    }

    @Override
    public void onPipelineComplete(String question, boolean isSuccess, long totalLatencyMs) {
      pipelineCompleted = true;
      success = isSuccess;
    }
  }
}
