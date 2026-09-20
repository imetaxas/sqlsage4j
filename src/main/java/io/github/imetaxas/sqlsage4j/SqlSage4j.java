package io.github.imetaxas.sqlsage4j;

import com.google.auto.value.AutoValue;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.client.LLMClientFactory;
import io.github.imetaxas.sqlsage4j.db.BigQueryConnector;
import io.github.imetaxas.sqlsage4j.db.DatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.RerankingAlgorithmEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.pipeline.PipelineListener;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGenerationPipeline;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.lang.invoke.MethodHandles;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point for the sqlsage4j library.
 *
 * <p>Configures LLM, embeddings, storage, and database connections, then produces {@link QueryChat}
 * (text-to-SQL) or {@link LLMChat} (general RAG) instances.
 *
 * <p>Example:
 *
 * <pre>{@code
 * LLMProviderConfig config = LLMProviderConfig.builder("gpt-4")
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .embeddingsProvider(new OpenAIEmbeddingsProvider(apiKey, "text-embedding-3-small"))
 *     .embeddingsStorage(new InMemoryEmbeddingsStorage())
 *     .maxTokens(1024L)
 *     .temperature(0.0)
 *     .build();
 *
 * SqlSage4j sage = SqlSage4j.builder(config)
 *     .databaseConnector(new SQLiteConnector("mydb.sqlite"))
 *     .prompt(prompt)
 *     .build();
 *
 * QueryChat chat = sage.queryChat();
 * QueryResponse response = chat.ask("How many users signed up last week?");
 * DataFrame results = chat.run(response);
 * }</pre>
 */
@AutoValue
public abstract class SqlSage4j {
  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private boolean trainingProcessed;

  SqlSage4j() {
    logger.info("SqlSage4j initialized");
  }

  @Nullable
  public abstract List<Document> train();

  @Nullable
  public abstract StorageEnum connectToStorage();

  public abstract LLMProviderConfig llmProviderConfig();

  public abstract Prompt prompt();

  @Nullable
  public abstract RerankingAlgorithmEnum rerankingAlgorithm();

  @Nullable
  public abstract DatabaseConnector databaseConnector();

  public abstract PipelineListener pipelineListener();

  public abstract SqlGuard sqlGuard();

  /**
   * Create a QueryChat instance — the primary text-to-SQL chat. Initializes the LLM client,
   * training service, and SQL generation pipeline from the configuration, then processes any
   * training documents.
   */
  public QueryChat queryChat() {
    LLMClient client = resolveClient();
    TrainingService trainingService = createTrainingService(client);
    processTrainingData(trainingService);

    String dialect = resolveDialect();
    int maxTokens = llmProviderConfig().maxTokens().intValue();
    String initialPrompt = prompt().initialPrompt();

    DatabaseConnector dbConnector = resolveDatabaseConnector();

    SqlGenerationPipeline pipeline =
        new SqlGenerationPipeline(
            client,
            trainingService,
            dialect,
            maxTokens,
            initialPrompt,
            dbConnector,
            pipelineListener());
    return new QueryChat(client, pipeline, trainingService, dbConnector, sqlGuard());
  }

  /** Create an LLMChat instance — general-purpose RAG chat. */
  public LLMChat llmChat() {
    LLMClient client = resolveClient();
    TrainingService trainingService = createTrainingService(client);
    processTrainingData(trainingService);

    String systemPrompt = prompt().userPrompt().getText();
    return new LLMChat(client, trainingService, systemPrompt);
  }

  private LLMClient resolveClient() {
    LLMClient injected = llmProviderConfig().llmClient();
    return injected != null ? injected : LLMClientFactory.create(llmProviderConfig());
  }

  private TrainingService createTrainingService(LLMClient client) {
    if (llmProviderConfig().embeddingsProvider() == null
        || llmProviderConfig().embeddingsStorage() == null) {
      throw new IllegalStateException(
          "embeddingsProvider and embeddingsStorage are required. "
              + "Set them in LLMProviderConfig.builder().");
    }
    return new TrainingService(
        llmProviderConfig().embeddingsProvider(), llmProviderConfig().embeddingsStorage(), client);
  }

  private void processTrainingData(TrainingService trainingService) {
    if (trainingProcessed) return;
    trainingProcessed = true;

    if (train() != null) {
      for (Document doc : train()) {
        trainingService.train(doc);
      }
    }
    Prompt p = prompt();
    if (p.ddls() != null) {
      for (DDL ddl : p.ddls()) {
        trainingService.trainDdl(ddl.ddl());
      }
    }
    if (p.sampleQuestionsAnswers() != null) {
      for (QuestionAnswer qa : p.sampleQuestionsAnswers()) {
        trainingService.trainQuestionSql(qa.question(), qa.sql());
      }
    }
    if (p.sampleDocuments() != null) {
      for (SampleDocument doc : p.sampleDocuments()) {
        trainingService.trainDocumentation(doc.content());
      }
    }
    if (p.ontologies() != null) {
      for (Ontology ont : p.ontologies()) {
        trainingService.trainDocumentation(ont.content());
      }
    }
  }

  private String resolveDialect() {
    if (databaseConnector() != null) return databaseConnector().dialect();
    if (connectToStorage() != null) {
      return switch (connectToStorage()) {
        case BIGQUERY -> "BigQuery SQL";
        case SNOWFLAKE -> "Snowflake SQL";
        case POSTGRES -> "PostgreSQL";
        case SQLITE -> "SQLite";
        case MYSQL -> "MySQL";
        case DUCKDB -> "DuckDB SQL";
      };
    }
    return "SQL";
  }

  @Nullable
  private DatabaseConnector resolveDatabaseConnector() {
    if (databaseConnector() != null) return databaseConnector();
    if (connectToStorage() == null) return null;
    return switch (connectToStorage()) {
      case BIGQUERY -> new BigQueryConnector("default-project");
      default -> null;
    };
  }

  public static Builder builder(LLMProviderConfig llmProviderConfig) {
    return new AutoValue_SqlSage4j.Builder()
        .llmProviderConfig(llmProviderConfig)
        .pipelineListener(PipelineListener.NOOP)
        .sqlGuard(SqlGuard.ALLOW_ALL);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    Builder() {}

    public abstract Builder llmProviderConfig(final LLMProviderConfig llmProviderConfig);

    public abstract Builder train(final List<Document> documents);

    public abstract Builder connectToStorage(final StorageEnum storage);

    public abstract Builder prompt(final Prompt prompt);

    public abstract Builder rerankingAlgorithm(final RerankingAlgorithmEnum rerankingAlgorithm);

    public abstract Builder databaseConnector(final DatabaseConnector databaseConnector);

    public abstract Builder pipelineListener(final PipelineListener listener);

    public abstract Builder sqlGuard(final SqlGuard sqlGuard);

    public abstract SqlSage4j build();
  }
}
