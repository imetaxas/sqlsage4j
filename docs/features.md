# Feature Reference

Comprehensive documentation for all sqlsage4j features. For a quick overview, see the [README](../README.md).

---

## Overview

**sqlsage4j** is a lightweight Java RAG library for **text-to-SQL** and **natural language to SQL** generation on the JVM. Works with **free local LLMs** (Ollama) — no API key, no cloud costs, no data leaving your machine — or any OpenAI-compatible provider. Train it with your schemas, documentation, and golden queries, then ask questions in plain English and get back precise SQL. Built for Java 17+, it provides a complete Retrieval-Augmented Generation pipeline with pluggable LLM clients, embeddings providers, and vector store backends.

It ships three RAG runners:

| Runner | Purpose |
|---|---|
| **QueryChat** | Text-to-SQL: natural language &rarr; SQL &rarr; execution &rarr; export |
| **LLMChat** | Free-form conversational AI with RAG-powered context retrieval |
| **LLMDiff** | Side-by-side comparison of two LLM/prompt configurations for evaluation |

Every component — LLM client, embeddings provider, vector store, database connector — is behind an interface, so you can swap OpenAI for a local model (Ollama, LM Studio, llama.cpp), replace the in-memory store with Lucene, or plug in your own implementations without touching the pipeline.

For local LLM setup (free, private), see the [Local LLM Guide](local-llm-guide.md).

## Quick Start

Add sqlsage4j to your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.imetaxas</groupId>
  <artifactId>sqlsage4j</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Spring Boot users** — see the [Spring Boot Starter](#spring-boot-starter) section below for zero-code auto-configuration via `application.yml`.

Minimal working example:

```java
QueryChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("gpt-4o")
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .embeddingsProvider(new OpenAIEmbeddingsProvider(System.getenv("OPENAI_API_KEY")))
            .embeddingsStorage(new InMemoryEmbeddingsStorage())
            .maxTokens(4096L)
            .build())
    .connectToStorage(StorageEnum.BIGQUERY)
    .prompt(Prompt.builder()
        .userPrompt(PromptEnum.SQL_EXPERT)
        .ddls(List.of(
            new DDL("CREATE TABLE users (id INT, name STRING, email STRING, created_at DATE);"),
            new DDL("CREATE TABLE orders (id INT, user_id INT, total DECIMAL, status STRING);")))
        .build())
    .build()
    .queryChat();

QueryResponse response = chat.ask("Who are our top 10 customers by total spend?");
System.out.println(response.sql());
```

## Usage

### QueryChat — Text-to-SQL

The primary runner. Train it with schemas, golden queries, and domain documentation, then ask natural language questions to get SQL.

```java
QueryChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("gpt-4o")
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .embeddingsProvider(new OpenAIEmbeddingsProvider(System.getenv("OPENAI_API_KEY")))
            .embeddingsStorage(new LuceneEmbeddingsStorage())
            .maxTokens(4096L)
            .temperature(0.0)
            .build())
    .connectToStorage(StorageEnum.BIGQUERY)
    .prompt(Prompt.builder()
        .userPrompt(PromptEnum.DATA_SCIENTIST)
        .ddls(List.of(
            new DDL("CREATE TABLE products (id INT, name STRING, category STRING, price DECIMAL);"),
            new DDL("CREATE TABLE orders (id INT, user_id INT, total DECIMAL, status STRING);")))
        .sampleQuestionsAnswers(List.of(
            new QuestionAnswer(
                "Total revenue?",
                "SELECT SUM(total) FROM orders WHERE status = 'completed';")))
        .sampleDocuments(List.of(
            new SampleDocument("Only orders with status = 'completed' count toward revenue.")))
        .build())
    .build()
    .queryChat();

// Ask a question
QueryResponse response = chat.ask("Revenue by product category last quarter");
System.out.println(response.sql());

// Execute the generated SQL
DataFrame df = chat.run(response);
System.out.println(df.toMarkdown());

// Export results
chat.exportAs(df, ExportType.CSV, "/tmp/revenue.csv");
chat.exportAs(df, ExportType.JSON, "/tmp/revenue.json");

// Generate follow-up questions
List<String> followups = chat.generateFollowupQuestions(
    response.question(), response.sql(), df.toMarkdown());

// Summarize in natural language
String summary = chat.generateSummary(response.question(), df.toMarkdown());

// Feed a good result back into training
chat.train(response.question(), response.sql());
```

**Multi-turn conversations** are built in. Follow-up questions are automatically rewritten using the conversation history:

```java
chat.ask("What are the most popular genres?");
chat.ask("What about just in the US?");      // rewritten to: "Most popular genres in the US?"
chat.ask("And the click-through rate?");      // rewritten with full prior context
```

### LLMChat — Conversational AI

General-purpose RAG chat. Retrieves relevant documentation from the vector store and uses it as context for free-form conversations.

```java
LLMChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("gpt-4o")
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .embeddingsProvider(new OpenAIEmbeddingsProvider(System.getenv("OPENAI_API_KEY")))
            .embeddingsStorage(new InMemoryEmbeddingsStorage())
            .maxTokens(4096L)
            .build())
    .prompt(Prompt.builder()
        .userPrompt(PromptEnum.DATA_SCIENTIST)
        .sampleDocuments(List.of(
            new SampleDocument("DAU = distinct users with at least one event per day."),
            new SampleDocument("Campaign ROI = (revenue - budget) / budget.")))
        .build())
    .build()
    .llmChat();

ChatResponse r1 = chat.ask("What attribution models do we support?");
ChatResponse r2 = chat.ask("Tell me more about multi-touch");  // prior context retained

chat.clearHistory();  // reset conversation
```

### LLMDiff — A/B Testing for Prompts

Compare two configurations side-by-side across a battery of questions. Useful for evaluating prompt changes, model upgrades, or training data improvements.

```java
QueryChat baseline = SqlSage4j.builder(configA).prompt(promptA).build().queryChat();
QueryChat candidate = SqlSage4j.builder(configB).prompt(promptB).build().queryChat();

// Single question, 5 runs
DiffResult result = LLMDiff.of(baseline, candidate).run("What is today's DAU?", 5);
System.out.println(result);  // success rates, match count, individual responses

// Batch of benchmark questions
List<DiffResult> results = LLMDiff.of(baseline, candidate)
    .runBatch(List.of("DAU?", "Revenue trend?", "Churn rate?"), 3);
```

## Vector Store Backends

sqlsage4j ships six `EmbeddingsStorage` implementations. The core library requires only `InMemoryEmbeddingsStorage` (zero dependencies). The others are **optional** — add the corresponding driver to your classpath to use them.

| Storage | Search Algorithm | Dependency | Best For |
|---|---|---|---|
| `InMemoryEmbeddingsStorage` | Brute-force cosine similarity | *none* | Prototyping, small datasets |
| `LuceneEmbeddingsStorage` | HNSW approximate nearest neighbor | `org.apache.lucene:lucene-core` | Production, large-scale search |
| `H2EmbeddingsStorage` | SQL + Java cosine similarity | `com.h2database:h2` | Embedded SQL with persistence |
| `SQLiteEmbeddingsStorage` | SQL + Java cosine similarity | `org.xerial:sqlite-jdbc` | Lightweight embedded storage |
| `HnswEmbeddingsStorage` | Navigable Small World graph | *none (pure Java)* | ANN search without dependencies |
| `LSHEmbeddingsStorage` | Locality-Sensitive Hashing | *none (pure Java)* | Fast approximate search |

To use a specific backend, pass it to the builder:

```java
LLMProviderConfig.builder("gpt-4o")
    .embeddingsStorage(new LuceneEmbeddingsStorage())  // or H2, SQLite, HNSW, LSH
    // ...
    .build()
```

### Implementing a Custom Backend

Implement the `EmbeddingsStorage` interface:

```java
public interface EmbeddingsStorage {
    String store(String collection, TrainingRecord record);
    List<SearchResult> search(String collection, float[] queryEmbedding, int nResults);
    List<TrainingRecord> getAll(String collection);
    boolean remove(String id);
    void removeAll(String collection);
}
```

## Training Data Types

sqlsage4j supports four types of training data, each stored in its own vector collection for targeted retrieval:

| Type | Class | Purpose |
|---|---|---|
| **DDL** | `DDL` | Table schemas — the LLM needs these to write correct column names and joins |
| **Q&A Pairs** | `QuestionAnswer` | Golden question-to-SQL examples for few-shot prompting |
| **Documentation** | `SampleDocument` | Business rules, metric definitions, domain knowledge |
| **Ontology** | `Ontology` | Semantic definitions and entity relationships ([paper](https://arxiv.org/pdf/2311.07509)) |

Training data can be provided upfront through the builder, or added incrementally at runtime:

```java
// Upfront via builder
Prompt.builder()
    .ddls(List.of(new DDL("CREATE TABLE ...")))
    .sampleQuestionsAnswers(List.of(new QuestionAnswer("Q?", "SELECT ...")))
    .sampleDocuments(List.of(new SampleDocument("Business rule...")))
    .ontologies(List.of(new Ontology("Entity definitions...")))
    .build()

// Incremental at runtime
chat.trainDdl("CREATE TABLE new_table (...)");
chat.trainDocumentation("New metric definition...");
chat.train("New question?", "SELECT ... new SQL ...");
```

## Local LLM Support

sqlsage4j works with local LLM servers out of the box — no API key required.

> **First time with Ollama?** Follow the [Ollama Setup Guide](docs/ollama-setup.md) to install, pull models, and verify the server before continuing here.
>
> **Want 100% accuracy?** Follow the [Local LLM Hardening Guide](local-llm-guide.md) — 7 proven steps to maximize results with free models.

### Ollama (native API)

Point `baseUrl` at Ollama's default port and sqlsage4j automatically uses the native Ollama `/api/chat` and `/api/embed` endpoints:

```java
QueryChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("llama3")
            .baseUrl("http://localhost:11434")
            .embeddingsProvider(new OllamaEmbeddingsProvider("http://localhost:11434"))
            .embeddingsStorage(new InMemoryEmbeddingsStorage())
            .maxTokens(4096L)
            .build())
    .prompt(Prompt.builder()
        .userPrompt(PromptEnum.SQL_EXPERT)
        .ddls(List.of(new DDL("CREATE TABLE users (id INT, name TEXT);")))
        .build())
    .build()
    .queryChat();
```

Use a different embeddings model with the two-argument constructor:

```java
new OllamaEmbeddingsProvider("http://localhost:11434", "mxbai-embed-large")
```

### Ollama (OpenAI-compatible mode)

Append `/v1` to the URL and sqlsage4j routes through the standard OpenAI client — useful if you need response format compatibility:

```java
LLMProviderConfig.builder("llama3")
    .baseUrl("http://localhost:11434/v1")
    .embeddingsProvider(new OpenAIEmbeddingsProvider(null, "llama3", "http://localhost:11434/v1"))
    .embeddingsStorage(new InMemoryEmbeddingsStorage())
    .maxTokens(4096L)
    .build()
```

### LM Studio / llama.cpp / vLLM

Any server exposing OpenAI-compatible `/v1/chat/completions` and `/v1/embeddings` endpoints works the same way:

```java
LLMProviderConfig.builder("local-model")
    .baseUrl("http://localhost:1234/v1")          // LM Studio default
    .embeddingsProvider(new OpenAIEmbeddingsProvider(null, "local-model", "http://localhost:1234/v1"))
    .embeddingsStorage(new InMemoryEmbeddingsStorage())
    .maxTokens(4096L)
    .build()
```

### Provider Detection

The factory automatically selects the right client:

| `baseUrl` | Client Used | Reason |
|---|---|---|
| *(not set)* | `OpenAIClient` | Default: calls `api.openai.com` |
| `http://localhost:11434` | `OllamaClient` | Port 11434 without `/v1` → native Ollama |
| `http://localhost:11434/v1` | `OpenAIClient` | Ends with `/v1` → OpenAI-compatible |
| `http://localhost:1234/v1` | `OpenAIClient` | Any URL ending with `/v1` |

You can always bypass automatic detection by passing a custom `llmClient` directly:

```java
LLMProviderConfig.builder("my-model")
    .llmClient(new OllamaClient("http://gpu-box:11434", "codellama", 0.0, 8192))
    .maxTokens(8192L)
    .build()
```

## Streaming

Real-time token-by-token streaming reduces perceived latency and enables live UI updates. Use `OllamaStreamingClient` or `OpenAIStreamingClient` as drop-in replacements:

### Callback-based (simplest)

```java
QueryChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("llama3")
            .llmClient(new OllamaStreamingClient("http://localhost:11434", "llama3", 0.0, 4096))
            .embeddingsProvider(new OllamaEmbeddingsProvider("http://localhost:11434"))
            .embeddingsStorage(new InMemoryEmbeddingsStorage())
            .maxTokens(4096L)
            .build())
    .prompt(Prompt.builder()
        .userPrompt(PromptEnum.SQL_EXPERT)
        .ddls(List.of(new DDL("CREATE TABLE orders (id INT, total DECIMAL);")))
        .build())
    .build()
    .queryChat();

// Tokens arrive as they're generated — ideal for real-time UIs
QueryResponse response = chat.askStreaming("Total revenue last month?", token -> {
    System.out.print(token.text());  // prints each token immediately
});
DataFrame df = chat.run(response);  // execute the SQL once complete
```

### Java Stream API (lazy, auto-closing)

```java
try (Stream<StreamToken> tokens = chat.askStreaming("Top 10 customers?")) {
    tokens.filter(t -> !t.finished())
          .forEach(t -> webSocket.send(t.text()));
}
```

### OpenAI streaming

```java
StreamingLLMClient client = new OpenAIStreamingClient(
    System.getenv("OPENAI_API_KEY"), "gpt-4o", 0.0, 4096);
```

### Feature detection

```java
if (chat.supportsStreaming()) {
    chat.askStreaming("...", token -> ui.append(token.text()));
} else {
    QueryResponse r = chat.ask("...");
    ui.append(r.sql());
}
```

Streaming clients also implement `LLMClient`, so `submitPrompt()` still works — it simply buffers the stream internally and returns the full response. Existing non-streaming code continues to work unchanged.

## Async API

All queries support `CompletableFuture`-based async execution for non-blocking pipelines:

```java
// Fire-and-forget
CompletableFuture<QueryResponse> future = chat.askAsync("Top 10 customers by revenue");

// Chain with execution
chat.askAsync("Total revenue this month")
    .thenApply(chat::run)
    .thenAccept(df -> System.out.println(df));

// Custom executor for thread pool control
ExecutorService pool = Executors.newFixedThreadPool(4);
chat.askAsync("Slow query", pool)
    .thenAccept(r -> log.info("Confidence: {}", r.confidence()));

// Parallel queries
CompletableFuture.allOf(
    chat.askAsync("Revenue by region"),
    chat.askAsync("Top products"),
    chat.askAsync("Customer churn rate")
).join();
```

## Confidence Scoring

Every `QueryResponse` includes a confidence score (0.0–1.0) that indicates how likely the generated SQL is correct:

```java
QueryResponse response = chat.ask("Total revenue last quarter");
System.out.printf("SQL: %s (confidence: %.0f%%)%n", response.sql(), response.confidence() * 100);

if (response.confidence() < 0.3) {
    System.out.println("Low confidence — consider training more examples for this question type");
}
```

The score is computed from three signals:
- **Retrieval similarity** (60%): How closely the question matches trained Q&A pairs
- **SQL validation** (20%): Whether the database accepted the query without errors
- **Structure analysis** (20%): Whether the response looks like valid SQL (starts with SELECT/WITH)

## Schema Change Detection

Detect when the live database schema has drifted from your trained DDLs:

```java
SchemaChangeDetector detector = new SchemaChangeDetector(dataSource, trainingService);

if (detector.hasDrift()) {
    List<SchemaChange> changes = detector.detect();
    changes.forEach(c -> System.out.println("⚠️  " + c));
    // TABLE_ADDED [analytics_events]: Table exists in database but not in training data
    // COLUMN_REMOVED [users]: Column 'legacy_id' removed from database
}
```

Detects: new tables, removed tables, new columns, and removed columns. Use in CI or on startup to trigger re-training.

## Rate Limiting

Prevent exceeding provider rate limits with the `RateLimitedLLMClient` decorator:

```java
LLMClient limited = RateLimitedLLMClient.builder(openAiClient)
    .maxConcurrent(5)          // Max 5 parallel LLM calls
    .requestsPerSecond(10.0)   // Token-bucket throttle
    .build();
```

Combine with retry and caching for production-grade resilience:

```java
LLMClient production = RateLimitedLLMClient.builder(
    RetryingLLMClient.builder(
        CachingLLMClient.builder(openAiClient).build()
    ).build()
).build();
```

## Structured Output (JSON Mode)

For more reliable SQL extraction, use JSON mode — the LLM returns a structured object instead of free text:

```java
String response = llmClient.submitPrompt(messages);
String sql = SqlExtractor.extract(response, ResponseFormat.JSON);
// Expects: {"sql": "SELECT COUNT(*) FROM users"}
// Falls back to regex if JSON parsing fails
```

## Persistent Training Store

Persist all training data to disk so it survives JVM restarts:

```java
EmbeddingsStorage persistent = PersistentEmbeddingsStorage.builder(new InMemoryEmbeddingsStorage())
    .filePath(Path.of("./my-training-data.json"))
    .build();

SqlSage4j sage = SqlSage4j.builder(LLMProviderConfig.builder("gpt-4o")
        .embeddingsStorage(persistent)  // auto-loads on startup, auto-saves on every train()
        .build())
    .build();

// Training data is automatically persisted to JSON
sage.queryChat().train("How many users?", "SELECT COUNT(*) FROM users");
// Restart the JVM → training data is still there
```

## Configuration

### LLMProviderConfig

| Parameter | Required | Description |
|---|---|---|
| `modelName` | Yes | LLM model identifier (e.g., `"gpt-4o"`, `"llama3"`) |
| `apiKey` | No* | API key for the LLM provider |
| `baseUrl` | No | Base URL for the LLM API (enables local LLMs) |
| `embeddingsProvider` | Yes | Embeddings generator (`OpenAIEmbeddingsProvider`, `OllamaEmbeddingsProvider`, or custom) |
| `embeddingsStorage` | Yes | Vector store backend (see table above) |
| `maxTokens` | Yes | Maximum tokens for LLM responses |
| `temperature` | No | Sampling temperature (default: `0.7`) |
| `serviceAccount` | No | Path to service account credentials |
| `diagramsPlotProvider` | No | Plotly code generator for visualization |
| `llmClient` | No | Custom `LLMClient` implementation (bypasses the factory) |

*\* Required for OpenAI. Not required for local LLMs or when providing a custom `llmClient`.*

### Prompt Configuration

| Parameter | Required | Description |
|---|---|---|
| `userPrompt` | Yes | Persona for the LLM (`SQL_EXPERT`, `DATA_SCIENTIST`, `ANALYST`) |
| `ddls` | No | Schema definitions for few-shot context |
| `sampleQuestionsAnswers` | No | Golden Q&A pairs for few-shot examples |
| `sampleDocuments` | No | Domain documentation and business rules |
| `ontologies` | No | Semantic ontology definitions |
| `initialPrompt` | No | Custom system prompt override |

### Supported Databases

sqlsage4j ships JDBC-based connectors for six databases. Each connector extends `JdbcDatabaseConnector` and accepts any standard `javax.sql.DataSource`.

| Database | Connector | Enum | JDBC Driver |
|---|---|---|---|
| Google BigQuery | `BigQueryConnector` | `BIGQUERY` | `google-cloud-bigquery` |
| PostgreSQL | `PostgresConnector` | `POSTGRES` | `org.postgresql:postgresql` |
| MySQL / MariaDB | `MySQLConnector` | `MYSQL` | `com.mysql:mysql-connector-j` |
| Snowflake | `SnowflakeConnector` | `SNOWFLAKE` | `net.snowflake:snowflake-jdbc` |
| SQLite | `SQLiteConnector` | `SQLITE` | `org.xerial:sqlite-jdbc` |
| DuckDB | `DuckDBConnector` | `DUCKDB` | `org.duckdb:duckdb_jdbc` |

**Quick start** — use the enum shortcut for dialect-only mode (generates SQL but doesn't execute):

```java
SqlSage4j.builder(config)
    .connectToStorage(StorageEnum.POSTGRES)
    .prompt(prompt)
    .build()
    .queryChat();
```

**Full execution** — pass a connector with a DataSource to generate *and* run SQL:

```java
PGSimpleDataSource ds = new PGSimpleDataSource();
ds.setUrl("jdbc:postgresql://localhost:5432/mydb");
ds.setUser("user");
ds.setPassword("pass");

SqlSage4j.builder(config)
    .databaseConnector(new PostgresConnector(ds))
    .prompt(prompt)
    .build()
    .queryChat();
```

### Custom Database Connectors

To add a database not listed above, extend `JdbcDatabaseConnector`:

```java
public final class ClickHouseConnector extends JdbcDatabaseConnector {

  public ClickHouseConnector(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public String dialect() {
    return "ClickHouse SQL";
  }
}
```

Or implement the `DatabaseConnector` interface directly for non-JDBC databases:

```java
public interface DatabaseConnector {
    DataFrame runSql(String sql);
    boolean isValidSql(String sql);
    String dialect();
}
```

Then pass your connector to the builder:

```java
SqlSage4j.builder(config)
    .databaseConnector(new ClickHouseConnector(myDataSource))
    .prompt(prompt)
    .build()
    .queryChat();
```

## Resilience & Observability

### Retry with Exponential Backoff

Wrap any `LLMClient` with automatic retries for transient failures (rate limits, server errors, timeouts):

```java
LLMClient resilient = RetryingLLMClient.builder(openAiClient)
    .maxAttempts(3)
    .initialDelay(Duration.ofMillis(500))
    .maxDelay(Duration.ofSeconds(10))
    .retryOn(e -> e.getMessage().contains("429"))
    .build();
```

### Response Caching

Cache LLM responses to avoid redundant API calls during development or for repeated queries:

```java
LLMClient cached = CachingLLMClient.builder(openAiClient)
    .maxEntries(200)
    .ttl(Duration.ofMinutes(30))
    .build();
```

### Pipeline Listener (Observability)

Hook into every pipeline stage for metrics, tracing, or structured logging:

```java
PipelineListener metrics = new PipelineListener() {
    @Override
    public void onLLMResponse(String response, long latencyMs) {
        histogram.record(latencyMs);
    }

    @Override
    public void onPipelineComplete(String question, boolean success, long totalMs) {
        counter.increment(success ? "success" : "failure");
    }
};

SqlSage4j.builder(config)
    .pipelineListener(metrics)
    .build();
```

Available callbacks: `onContextRetrieved`, `onPromptAssembled`, `onLLMResponse`, `onSqlExtracted`, `onValidationRetry`, `onPipelineComplete`.

### SQL Validation & Self-Correction

When a database connector is configured, the pipeline automatically validates generated SQL using `EXPLAIN` and retries with the error message as context if validation fails:

```java
// Automatically enabled when a DatabaseConnector is present:
SqlSage4j.builder(config)
    .databaseConnector(new PostgresConnector(dataSource))
    .prompt(prompt)
    .build()
    .queryChat();  // ask() now auto-validates and self-corrects SQL
```

The retry loop appends the database error to the prompt and asks the LLM to fix its SQL — typically recovering from missing column names, incorrect joins, or dialect-specific syntax errors.

## SQL Safety Guardrails

Prevent destructive SQL from reaching your database with `SqlGuard`:

```java
SqlSage4j.builder(config)
    .sqlGuard(SqlGuard.readOnly())  // blocks INSERT, UPDATE, DELETE, DROP, etc.
    .prompt(prompt)
    .build();
```

The guard inspects generated SQL before execution and blocks:
- Write operations (INSERT, UPDATE, DELETE, MERGE, UPSERT)
- DDL operations (DROP, TRUNCATE, ALTER, CREATE)
- Privilege operations (GRANT, REVOKE)
- SQL injection attempts (multiple statements separated by `;`)

```java
// Manual check
SqlGuard guard = SqlGuard.readOnly();
SqlGuard.Result result = guard.check("DROP TABLE users;");
if (result.blocked()) {
    System.out.println("Blocked: " + result.reason());
}
```

| Policy | Behavior |
|---|---|
| `SqlGuard.readOnly()` | Only SELECT, WITH, EXPLAIN, SHOW, DESCRIBE allowed |
| `SqlGuard.allowAll()` | No validation (default for backward compatibility) |

## Auto-Schema Introspection

Automatically discover your database schema — no manual DDL writing required:

```java
QueryChat chat = sage.queryChat();

// Auto-train from a live database connection
chat.trainFromDatabase(dataSource);

// Or with a schema filter
chat.trainFromDatabase(dataSource, "public");
```

Under the hood, `SchemaIntrospector` uses `DatabaseMetaData` to discover tables, columns, types, nullability, primary keys, and foreign keys — then generates `CREATE TABLE` DDL strings and feeds them into the training pipeline.

```java
// Standalone usage
SchemaIntrospector introspector = new SchemaIntrospector(dataSource);
List<String> ddls = introspector.introspect();
ddls.forEach(System.out::println);
```

## Hybrid Search (Vector + Keyword)

Pure vector search can miss exact table/column names. `HybridEmbeddingsStorage` combines vector similarity with BM25 keyword matching using Reciprocal Rank Fusion:

```java
EmbeddingsStorage hybrid = new HybridEmbeddingsStorage(
    new LuceneEmbeddingsStorage(),  // vector backend
    new BM25Storage(),              // keyword backend
    0.6                             // 60% vector weight, 40% BM25
);

SqlSage4j.builder(
    LLMProviderConfig.builder("gpt-4o")
        .embeddingsStorage(hybrid)
        .embeddingsProvider(new OpenAIEmbeddingsProvider(apiKey))
        .build())
    // ...
```

| Alpha | Behavior |
|---|---|
| `1.0` | Pure vector search |
| `0.6` | Default — vector-dominant with keyword boost |
| `0.0` | Pure BM25 keyword search |

## Training Data Import / Export

Serialize training data to JSON for backup, version control, and sharing across environments:

```java
// Export all training data
TrainingDataIO.exportToFile(trainingService, Path.of("training-data.json"));

// Import into a fresh instance
TrainingDataIO.importFromFile(trainingService, Path.of("training-data.json"));

// Or use strings for programmatic workflows
String json = TrainingDataIO.exportToString(trainingService);
TrainingDataIO.importFromString(newService, json);
```

The JSON format is human-readable and version-controlled:

```json
{
  "version": 1,
  "recordCount": 3,
  "records": [
    {"id": "...", "type": "DDL", "content": "CREATE TABLE users (...)"},
    {"id": "...", "type": "SQL_QA", "content": "...", "metadata": {"question": "...", "sql": "..."}},
    {"id": "...", "type": "DOCUMENTATION", "content": "Business rule..."}
  ]
}
```

## Spring Boot Starter

For Spring Boot applications, add the starter instead — it auto-configures everything from `application.yml`:

```xml
<dependency>
  <groupId>io.github.imetaxas</groupId>
  <artifactId>sqlsage4j-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
# application.yml
sqlsage4j:
  model-name: llama3
  base-url: http://localhost:11434
  max-tokens: 4096
  temperature: 0.0
  embeddings:
    provider: ollama                # openai | ollama | noop
    base-url: http://localhost:11434
    model: nomic-embed-text
  storage:
    type: in-memory                 # in-memory | bm25 | hnsw | lsh
  safety:
    read-only: true                 # blocks INSERT/UPDATE/DELETE/DROP
```

Then inject the ready-to-use `QueryChat` bean:

```java
@RestController
public class QueryController {

    private final QueryChat chat;

    public QueryController(QueryChat chat) {
        this.chat = chat;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody String question) {
        return chat.ask(question).sql();
    }
}
```

### Starter Configuration Reference

| Property | Default | Description |
|---|---|---|
| `sqlsage4j.enabled` | `true` | Enable/disable auto-configuration |
| `sqlsage4j.model-name` | *(required)* | LLM model identifier |
| `sqlsage4j.api-key` | | API key (for OpenAI) |
| `sqlsage4j.base-url` | | LLM server URL (auto-detects Ollama vs OpenAI-compatible) |
| `sqlsage4j.max-tokens` | `4096` | Max response tokens |
| `sqlsage4j.temperature` | `0.0` | Sampling temperature |
| `sqlsage4j.embeddings.provider` | `openai` | `openai`, `ollama`, `noop` (for BM25) |
| `sqlsage4j.embeddings.model` | `text-embedding-3-small` | Embeddings model name |
| `sqlsage4j.embeddings.base-url` | *(falls back to main base-url)* | Separate embeddings endpoint |
| `sqlsage4j.embeddings.api-key` | *(falls back to main api-key)* | Separate embeddings API key |
| `sqlsage4j.storage.type` | `in-memory` | `in-memory`, `bm25`, `hnsw`, `lsh` |
| `sqlsage4j.safety.read-only` | `false` | Block write SQL operations |

### Actuator Health Check

When Spring Boot Actuator is on the classpath, a `/actuator/health/sqlsage4j` health indicator is automatically registered. It pings the LLM and reports UP/DOWN.

```json
{
  "status": "UP",
  "details": { "model": "llama3" }
}
```

### Bean Override

All auto-configured beans back off when you provide your own. Register a custom `LLMClient`, `EmbeddingsProvider`, `EmbeddingsStorage`, or `SqlGuard` bean and it takes precedence:

```java
@Bean
public LLMClient myCustomClient() {
    return new MyCustomLLMClient();
}
```

## Building

### Requirements

- **Java** 17 or later
- **Maven** 3.5.4 or later

### Build from source

```bash
git clone https://github.com/yanimetaxas/sqlsage4j.git
cd sqlsage4j
mvn compile          # compile + auto-format
```

### Run tests

```bash
mvn test             # unit tests (60 tests)
mvn verify           # unit + integration tests (88 tests)
```

### Package

```bash
mvn clean package -DskipTests
```

## Project Structure

```
src/main/java/io/github/imetaxas/sqlsage4j/
├── SqlSage4j.java               # Entry point and builder
├── QueryChat.java               # Text-to-SQL runner
├── LLMChat.java                 # Conversational AI runner
├── client/
│   ├── LLMClient.java           # LLM interface
│   ├── LLMClientFactory.java    # Auto-detects provider from config
│   ├── OpenAIClient.java        # OpenAI / OpenAI-compatible implementation
│   └── OllamaClient.java        # Native Ollama API implementation
├── provider/
│   ├── EmbeddingsProvider.java  # Embeddings interface
│   ├── OpenAIEmbeddingsProvider.java
│   └── OllamaEmbeddingsProvider.java  # Native Ollama embeddings
├── storage/
│   ├── EmbeddingsStorage.java   # Vector store interface
│   ├── InMemoryEmbeddingsStorage.java
│   ├── LuceneEmbeddingsStorage.java
│   ├── H2EmbeddingsStorage.java
│   ├── SQLiteEmbeddingsStorage.java
│   ├── HnswEmbeddingsStorage.java
│   ├── LSHEmbeddingsStorage.java
│   ├── BM25Storage.java           # Keyword-based (no embeddings needed)
│   └── HybridEmbeddingsStorage.java  # Vector + BM25 fusion
├── pipeline/
│   ├── SqlGenerationPipeline.java  # Core RAG orchestrator
│   ├── SqlExtractor.java          # SQL extraction from LLM output
│   ├── SqlGuard.java              # SQL safety guardrails
│   └── PipelineListener.java      # Observability hooks
├── prompt/
│   ├── PromptAssembler.java     # Multi-purpose prompt builder
│   ├── PromptTemplate.java      # Resource-based templates
│   └── SqlPromptBuilder.java    # SQL-specific prompt construction
├── training/
│   ├── TrainingService.java     # Training data management
│   └── TrainingDataIO.java      # JSON import/export
├── diff/
│   └── LLMDiff.java             # A/B testing framework
├── db/
│   ├── DatabaseConnector.java   # Database interface
│   ├── JdbcDatabaseConnector.java  # Base JDBC implementation
│   ├── SchemaIntrospector.java  # Auto-schema discovery
│   ├── BigQueryConnector.java   # BigQuery (reflective)
│   ├── PostgresConnector.java   # PostgreSQL
│   ├── MySQLConnector.java      # MySQL / MariaDB
│   ├── SnowflakeConnector.java  # Snowflake
│   ├── SQLiteConnector.java     # SQLite
│   └── DuckDBConnector.java     # DuckDB
└── export/
    └── Exporter.java            # JSON, CSV, text export
```

## How It Works

1. **Train** — Feed schemas (DDLs), golden Q&A pairs, and documentation into the vector store via the embeddings provider.
2. **Retrieve** — When a question arrives, query the vector store for the most relevant DDLs, Q&A examples, and documentation.
3. **Assemble** — Build a prompt with a system message, retrieved context, few-shot examples, response guidelines, and the user question.
4. **Generate** — Send the prompt to the LLM and extract SQL from the response using a regex chain.
5. **Execute** — Optionally run the SQL against a connected database and return a `DataFrame`.
6. **Learn** — Feed successful Q&A pairs back into the training store for continuous improvement.

## License

This project is licensed under the [MIT License](LICENSE).
