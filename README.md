<p align="center">
  <img src="docs/logo.png" width="400" alt="sqlsage4j logo">
</p>

<p align="center">
  <strong>Java text-to-SQL with local LLM support — zero cost, zero cloud, 100% accurate.</strong>
</p>

<p align="center">
  <a href="https://github.com/imetaxas/sqlsage4j/actions"><img src="https://img.shields.io/github/actions/workflow/status/imetaxas/sqlsage4j/ci.yml?branch=master" alt="Build Status"></a>
  <img src="https://img.shields.io/badge/java-17%2B-blue" alt="Java 17+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License"></a>
  <a href="https://github.com/imetaxas/sqlsage4j/stargazers"><img src="https://img.shields.io/github/stars/imetaxas/sqlsage4j" alt="GitHub Stars"></a>
</p>

<p align="center">
  <a href="#quick-start">Quick Start</a> &middot;
  <a href="docs/local-llm-guide.md">Local LLM Guide</a> &middot;
  <a href="docs/features.md">Full Docs</a> &middot;
  <a href="docs/roadmap.md#feature-comparison">Comparison</a> &middot;
  <a href="#getting-help">Community</a>
</p>

---

<p align="center">
  <img src="docs/demo.gif" width="700" alt="sqlsage4j demo — ask questions in English, get SQL and results (zero cost, local LLM)">
</p>

## Ask your database questions in plain English. Get perfect SQL back.

**sqlsage4j** is a Java RAG framework that turns natural language into SQL. It works with free local models (Ollama) or any OpenAI-compatible provider. Train it with your schemas and examples, then ask questions — it generates, validates, and self-corrects SQL automatically.

### Why sqlsage4j?

- :white_check_mark: **Zero cost** — runs on free local LLMs (Ollama). No API key, no cloud bills, ever.
- :white_check_mark: **100% accurate** — [proven with proper training](docs/local-llm-guide.md) (8B model, 10/10 queries correct)
- :white_check_mark: **Java native** — single JAR, no Python dependency, works in any JVM application
- :white_check_mark: **Self-correcting** — validates SQL against your database, retries with error context on failure
- :white_check_mark: **Private** — your data never leaves your machine
- :white_check_mark: **Production-ready** — retry, caching, rate limiting, streaming, async, observability built in

> :bar_chart: [**How we compare to Vanna, LangChain, and Spring AI**](docs/roadmap.md#feature-comparison) — 34 features tracked, 14 unique to sqlsage4j.

---

## Quick Start

```bash
ollama pull llama3.1:8b && ollama pull nomic-embed-text
```

```java
// 1. Connect to your database
SQLiteDataSource ds = new SQLiteDataSource();
ds.setUrl("jdbc:sqlite:my_database.db");

// 2. Build sqlsage4j (zero cost — uses local Ollama)
QueryChat chat = SqlSage4j.builder(
        LLMProviderConfig.builder("llama3.1:8b")
            .llmClient(new OllamaClient("http://localhost:11434", "llama3.1:8b", 0.0, 4096L))
            .embeddingsProvider(new OllamaEmbeddingsProvider("http://localhost:11434"))
            .embeddingsStorage(new InMemoryEmbeddingsStorage())
            .build())
    .databaseConnector(new SQLiteConnector(ds))
    .build()
    .queryChat();

// 3. Train it (one-time)
chat.trainFromDatabase(ds);
chat.train("How many users?", "SELECT COUNT(*) FROM users");

// 4. Ask questions in English
QueryResponse response = chat.ask("Top 10 customers by revenue");
System.out.println(response.sql());       // → SELECT ...
System.out.println(response.confidence()); // → 0.85

DataFrame results = chat.run(response);   // Execute and get results
```

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.imetaxas</groupId>
  <artifactId>sqlsage4j</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Using Spring Boot?** Add `sqlsage4j-spring-boot-starter` instead — zero-code config via `application.yml`. [Details →](docs/features.md#spring-boot-starter)

**Using OpenAI?** Replace the Ollama client with `LLMProviderConfig.builder("gpt-4o").apiKey(key)...`. [Details →](docs/features.md#quick-start)

---

## Features

Every feature is documented with code examples in the [Feature Reference](docs/features.md).

| Feature | Description |
|---|---|
| **[Text-to-SQL](docs/features.md#querychat--text-to-sql)** | Natural language → SQL → execution → export |
| **[Local LLM (Ollama)](docs/local-llm-guide.md)** | Zero cost, zero cloud, 100% private |
| **[Streaming](docs/features.md#streaming)** | Token-by-token delivery for real-time UIs |
| **[Async API](docs/features.md#async-api)** | `CompletableFuture`-based non-blocking pipelines |
| **[Self-Correction](docs/features.md#sql-validation--self-correction)** | Validates SQL, retries with error context |
| **[Confidence Scoring](docs/features.md#confidence-scoring)** | 0.0–1.0 score on every response |
| **[Schema Discovery](docs/features.md#auto-schema-introspection)** | Auto-introspect tables/columns/FKs from live DB |
| **[Schema Drift Detection](docs/features.md#schema-change-detection)** | Alert when DB changes vs. trained DDLs |
| **[Hybrid Search](docs/features.md#hybrid-search-vector--keyword)** | Vector + BM25 keyword retrieval fusion |
| **[SQL Safety Guards](docs/features.md#sql-safety-guardrails)** | Block DROP/DELETE/UPDATE before execution |
| **[6 Vector Stores](docs/features.md#vector-store-backends)** | InMemory, Lucene, H2, SQLite, HNSW, LSH |
| **[6 Database Connectors](docs/features.md#supported-databases)** | BigQuery, Postgres, MySQL, Snowflake, SQLite, DuckDB |
| **[LLM A/B Testing](docs/features.md#llmdiff--ab-testing-for-prompts)** | Side-by-side comparison of models/prompts |
| **[Rate Limiting](docs/features.md#rate-limiting)** | Token-bucket throttle + concurrency control |
| **[Retry & Caching](docs/features.md#resilience--observability)** | Exponential backoff + LRU response cache |
| **[Observability](docs/features.md#pipeline-listener-observability)** | Hook into every pipeline stage |
| **[Training Import/Export](docs/features.md#training-data-import--export)** | JSON serialization for version control |
| **[Persistent Store](docs/features.md#persistent-training-store)** | Auto-save training data across restarts |
| **[Spring Boot Starter](docs/features.md#spring-boot-starter)** | Auto-configuration via `application.yml` |
| **[Structured Output](docs/features.md#structured-output-json-mode)** | JSON mode for reliable SQL extraction |

---

## Use Cases

- **Analytics dashboards** — Let business users query data without writing SQL
- **Internal tools** — Build Slack bots or admin panels that answer data questions
- **Data exploration** — Onboard new team members who don't know the schema
- **CI/CD pipelines** — Automated data validation and reporting
- **Privacy-sensitive environments** — On-prem, air-gapped, HIPAA/SOC2 deployments

---

## How It Works

```
Question → [Retrieve context from vector store] → [Assemble prompt] → [LLM generates SQL]
         → [Validate against DB] → [Self-correct if needed] → SQL + Results
```

1. **Train** — Feed schemas, golden Q&A, and documentation into the vector store
2. **Retrieve** — Find the most relevant context for each new question
3. **Generate** — LLM produces SQL using the assembled prompt
4. **Validate** — Run `EXPLAIN` against the database to catch errors
5. **Self-Correct** — If invalid, retry with the error message as context
6. **Execute** — Run the SQL and return a `DataFrame`

---

## Supported Providers

| LLM Providers | Embeddings | Vector Stores | Databases |
|---|---|---|---|
| Ollama (local, free) | Ollama | InMemory | SQLite |
| OpenAI | OpenAI | Lucene | PostgreSQL |
| Any OpenAI-compatible | Any custom | H2, SQLite | MySQL |
| LM Studio, vLLM, llama.cpp | | HNSW, LSH, BM25 | BigQuery, Snowflake, DuckDB |

---

<details>
<summary><strong>Building from Source</strong></summary>

```bash
git clone https://github.com/imetaxas/sqlsage4j.git
cd sqlsage4j
mvn compile          # compile + auto-format
mvn test             # unit tests
mvn verify           # unit + integration tests
```

Requires Java 17+ and Maven 3.5.4+.

</details>

<details>
<summary><strong>Project Structure</strong></summary>

```
src/main/java/io/github/imetaxas/sqlsage4j/
├── SqlSage4j.java               # Entry point and builder
├── QueryChat.java               # Text-to-SQL runner
├── LLMChat.java                 # Conversational AI runner
├── client/                      # LLM clients (OpenAI, Ollama, streaming, retry, cache)
├── provider/                    # Embeddings providers
├── storage/                     # Vector store backends (6 implementations)
├── pipeline/                    # RAG orchestration, SQL extraction, safety
├── training/                    # Training data management and I/O
├── db/                          # Database connectors (6 implementations)
├── diff/                        # LLM A/B testing framework
└── export/                      # CSV, JSON, text export
```

</details>

<details>
<summary><strong>Configuration Reference</strong></summary>

| Parameter | Required | Description |
|---|---|---|
| `modelName` | Yes | LLM model identifier (e.g., `"gpt-4o"`, `"llama3.1:8b"`) |
| `apiKey` | No* | API key for the LLM provider |
| `baseUrl` | No | Base URL for the LLM API (enables local LLMs) |
| `embeddingsProvider` | Yes | Embeddings generator |
| `embeddingsStorage` | Yes | Vector store backend |
| `maxTokens` | Yes | Maximum tokens for LLM responses |
| `temperature` | No | Sampling temperature (default: `0.7`) |
| `llmClient` | No | Custom `LLMClient` (bypasses auto-detection) |

*\* Required for OpenAI. Not needed for local LLMs.*

See [full configuration docs](docs/features.md#configuration) for all options.

</details>

---

## Getting Help

- :book: **[Feature Reference](docs/features.md)** — Full documentation with code examples
- :rocket: **[Local LLM Guide](docs/local-llm-guide.md)** — 7 steps to 100% accuracy with free models
- :shield: **[Security Guide](docs/security.md)** — Production security recommendations
- :bug: **[Report a Bug](https://github.com/imetaxas/sqlsage4j/issues/new)**
- :bulb: **[Request a Feature](https://github.com/imetaxas/sqlsage4j/discussions)**
- :star: **[Star us on GitHub](https://github.com/imetaxas/sqlsage4j)** — it helps others find the project!

---

## License

[MIT License](LICENSE) — use it however you want.
