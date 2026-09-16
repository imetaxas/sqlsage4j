package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.pipeline.PipelineListener;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class SqlSage4jBuilderTest {

  @Test
  void builderDefaults_pipelineListenerIsNoop_sqlGuardIsAllowAll() {
    SqlSage4j sage = minimalBuild();

    assertThat(sage.pipelineListener()).isEqualTo(PipelineListener.NOOP);
    assertThat(sage.sqlGuard()).isEqualTo(SqlGuard.ALLOW_ALL);
  }

  @Test
  void queryChat_withMinimalConfig_returnsWorkingInstance() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage = buildWith(llm);

    QueryChat chat = sage.queryChat();
    assertThat(chat).isNotNull();

    QueryResponse response = chat.ask("hello");
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void llmChat_withMinimalConfig_returnsWorkingInstance() {
    MockLLMClient llm = new MockLLMClient().withResponse("Answer: 42");
    SqlSage4j sage = buildWith(llm);

    LLMChat chat = sage.llmChat();
    assertThat(chat).isNotNull();
  }

  @Test
  void queryChat_withDatabaseConnector_resolvesDialectFromConnector() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    DatabaseConnector customDb =
        new DatabaseConnector() {
          @Override
          public DataFrame runSql(String sql) {
            return DataFrame.empty();
          }

          @Override
          public boolean isValidSql(String sql) {
            return true;
          }

          @Override
          public String dialect() {
            return "CustomSQL";
          }
        };

    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .databaseConnector(customDb)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.databaseConnector()).isNotNull();
    assertThat(sage.databaseConnector().dialect()).isEqualTo("CustomSQL");
  }

  @Test
  void queryChat_withConnectToStorage_bigquery_resolvesDialect() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.BIGQUERY)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.BIGQUERY);
  }

  @Test
  void queryChat_withConnectToStorage_snowflake() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.SNOWFLAKE)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.SNOWFLAKE);
  }

  @Test
  void queryChat_withConnectToStorage_postgres() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.POSTGRES)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.POSTGRES);
  }

  @Test
  void queryChat_withConnectToStorage_mysql() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.MYSQL)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.MYSQL);
  }

  @Test
  void queryChat_withConnectToStorage_duckdb() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.DUCKDB)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.DUCKDB);
  }

  @Test
  void queryChat_withConnectToStorage_sqlite() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .connectToStorage(StorageEnum.SQLITE)
            .prompt(simplePrompt())
            .build();

    assertThat(sage.connectToStorage()).isEqualTo(StorageEnum.SQLITE);
  }

  @Test
  void queryChat_withCustomSqlGuard_passesItToChat() {
    MockLLMClient llm = new MockLLMClient().withResponse("DELETE FROM t");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .sqlGuard(SqlGuard.readOnly())
            .prompt(simplePrompt())
            .build();

    assertThat(sage.sqlGuard()).isEqualTo(SqlGuard.READ_ONLY);

    QueryChat chat = sage.queryChat();
    QueryResponse response = chat.ask("delete stuff");
    assertThatThrownBy(() -> chat.runSql(response.sql()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  void queryChat_withPipelineListener_firesEvents() {
    AtomicBoolean started = new AtomicBoolean(false);
    AtomicBoolean completed = new AtomicBoolean(false);

    PipelineListener listener =
        new PipelineListener() {
          @Override
          public void onLLMResponse(String response, long latencyMs) {
            started.set(true);
          }

          @Override
          public void onPipelineComplete(String question, boolean success, long totalLatencyMs) {
            completed.set(true);
          }
        };

    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .pipelineListener(listener)
            .prompt(simplePrompt())
            .build();

    QueryChat chat = sage.queryChat();
    chat.ask("test");

    assertThat(started.get()).isTrue();
    assertThat(completed.get()).isTrue();
  }

  @Test
  void queryChat_withTrainDocuments_processesOnFirstCall() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .train(
                List.of(
                    new DDL("CREATE TABLE t (id INT)"),
                    new QuestionAnswer("count?", "SELECT COUNT(*) FROM t"),
                    new SampleDocument("some doc"),
                    new Ontology("ont")))
            .prompt(simplePrompt())
            .build();

    assertThat(sage.train()).hasSize(4);

    QueryChat chat = sage.queryChat();
    assertThat(chat).isNotNull();
  }

  @Test
  void queryChat_withPromptDdlsAndQAs_trainsThemAllOnBuild() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .ddls(
                        List.of(
                            new DDL("CREATE TABLE a (id INT)"), new DDL("CREATE TABLE b (x TEXT)")))
                    .sampleQuestionsAnswers(List.of(new QuestionAnswer("q?", "SELECT 1")))
                    .sampleDocuments(List.of(new SampleDocument("doc1")))
                    .ontologies(List.of(new Ontology("ont1")))
                    .build())
            .build();

    QueryChat chat = sage.queryChat();
    String trained = chat.getAllTrainedData();
    assertThat(trained).contains("CREATE TABLE a");
    assertThat(trained).contains("CREATE TABLE b");
  }

  @Test
  void queryChat_withoutEmbeddingsProvider_throwsIllegalState() {
    MockLLMClient llm = new MockLLMClient();
    SqlSage4j sage =
        SqlSage4j.builder(
                LLMProviderConfig.builder("model")
                    .llmClient(llm)
                    .maxTokens(100L)
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .build())
            .prompt(simplePrompt())
            .build();

    assertThatThrownBy(sage::queryChat)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("embeddingsProvider");
  }

  @Test
  void queryChat_withoutEmbeddingsStorage_throwsIllegalState() {
    MockLLMClient llm = new MockLLMClient();
    SqlSage4j sage =
        SqlSage4j.builder(
                LLMProviderConfig.builder("model")
                    .llmClient(llm)
                    .maxTokens(100L)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .build())
            .prompt(simplePrompt())
            .build();

    assertThatThrownBy(sage::queryChat)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("embeddingsStorage");
  }

  @Test
  void queryChat_calledTwice_doesNotReprocessTraining() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    SqlSage4j sage = buildWith(llm);

    QueryChat chat1 = sage.queryChat();
    QueryChat chat2 = sage.queryChat();

    assertThat(chat1).isNotNull();
    assertThat(chat2).isNotNull();
  }

  @Test
  void queryChat_noDatabaseConnector_runSqlThrows() {
    MockLLMClient llm = new MockLLMClient().withResponse("SELECT 1");
    SqlSage4j sage = buildWith(llm);

    QueryChat chat = sage.queryChat();
    QueryResponse response = chat.ask("test");

    assertThatThrownBy(() -> chat.run(response))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No database connected");
  }

  @Test
  void queryChat_withInitialPrompt_passesItToPipeline() {
    MockLLMClient llm = new MockLLMClient().withDefaultResponse("SELECT 1");
    SqlSage4j sage =
        SqlSage4j.builder(configWith(llm))
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .initialPrompt("Be concise.")
                    .build())
            .build();

    assertThat(sage.prompt().initialPrompt()).isEqualTo("Be concise.");
    QueryChat chat = sage.queryChat();
    assertThat(chat).isNotNull();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private SqlSage4j minimalBuild() {
    return SqlSage4j.builder(
            LLMProviderConfig.builder("test")
                .llmClient(new MockLLMClient())
                .embeddingsProvider(new MockEmbeddingsProvider())
                .embeddingsStorage(new InMemoryEmbeddingsStorage())
                .maxTokens(100L)
                .build())
        .prompt(simplePrompt())
        .build();
  }

  private SqlSage4j buildWith(MockLLMClient llm) {
    return SqlSage4j.builder(configWith(llm)).prompt(simplePrompt()).build();
  }

  private LLMProviderConfig configWith(MockLLMClient llm) {
    return LLMProviderConfig.builder("test")
        .llmClient(llm)
        .embeddingsProvider(new MockEmbeddingsProvider())
        .embeddingsStorage(new InMemoryEmbeddingsStorage())
        .maxTokens(100L)
        .build();
  }

  private Prompt simplePrompt() {
    return Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build();
  }
}
