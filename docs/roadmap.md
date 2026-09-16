# Roadmap

Feature comparison and prioritized roadmap for sqlsage4j.

## Feature Comparison

How sqlsage4j compares to Vanna, LangChain SQL Agent, LlamaIndex, and Java alternatives (Text2Sql, Spring AI).

| Feature | sqlsage4j | Vanna | LangChain SQL | LlamaIndex | Text2Sql (Java) |
|---|:---:|:---:|:---:|:---:|:---:|
| Text-to-SQL generation | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| Multi-turn conversations | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: | :x: |
| RAG with vector store | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| Training data management | :white_check_mark: | :white_check_mark: | Via tools | Via index | Partial |
| Multiple vector backends | :white_check_mark: (6) | :white_check_mark: | :white_check_mark: | :white_check_mark: | Partial |
| LLM A/B testing (LLMDiff) | :white_check_mark: | :x: | :x: | :x: | :x: |
| Local LLM support (Ollama) | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| Proven 100% local accuracy | :white_check_mark: | :x: | :x: | :x: | :x: |
| Multiple DB connectors (JDBC) | :white_check_mark: (6) | :white_check_mark: | :white_check_mark: | :white_check_mark: | Partial |
| Export (CSV/JSON/text) | :white_check_mark: | Via pandas | Via tools | :x: | :x: |
| Auto-schema introspection | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |
| SQL safety guardrails | :white_check_mark: | :white_check_mark: | :white_check_mark: | Partial | :white_check_mark: |
| Automatic error recovery | :white_check_mark: | Partial | :white_check_mark: | :x: | :white_check_mark: |
| Streaming responses | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: | :x: |
| Auto-visualization (Plotly) | Interface only | :white_check_mark: | :x: | :x: | :x: |
| Confidence scoring | :white_check_mark: | :x: | :x: | :x: | :x: |
| Schema change detection | :white_check_mark: | :x: | :x: | :x: | :x: |
| Async / reactive API | :white_check_mark: | Async | Async | Async | :x: |
| Spring Boot starter | :white_check_mark: | N/A | N/A | N/A | :white_check_mark: |
| Observability / tracing | :white_check_mark: | :white_check_mark: (v2) | LangSmith | :white_check_mark: | :x: |
| Hybrid search (vector + keyword) | :white_check_mark: | :x: | :x: | :x: | :white_check_mark: |
| Structured output / JSON mode | :white_check_mark: | :x: | :white_check_mark: | :white_check_mark: | :x: |
| Rate limiting / throttling | :white_check_mark: | :white_check_mark: (v2) | :x: | :x: | :x: |
| Persistent training store | :white_check_mark: | ChromaDB etc. | N/A | N/A | Qdrant etc. |

### What sqlsage4j Does Better

- **100% accuracy with free local LLMs** — proven with Ollama `llama3.1:8b` (10/10 queries correct). No API key, no cloud cost, no data leaving your machine. The [hardening guide](local-llm-guide.md) documents exactly how.
- **Hybrid search (vector + BM25)** — combines semantic and keyword retrieval for better context. No Python competitor offers this out of the box.
- **Self-correcting pipeline** — if SQL fails validation, automatically retries with the error as context. Works especially well with local models.
- **LLMDiff** — unique A/B testing framework for prompts, models, and configurations. No competitor has this.
- **Confidence scoring** — quantifies how trustworthy each generated query is. No competitor has this.
- **Schema change detection** — alerts when training data goes stale. No competitor has this.
- **Six pure-Java vector backends** — no external infrastructure needed for prototyping (InMemory, Lucene, H2, SQLite, HNSW, LSH).
- **Single-JAR, zero-framework** — no Spring/Quarkus dependency, works anywhere on the JVM.
- **JVM performance** — faster startup and lower memory than Python equivalents.
- **Privacy-first architecture** — designed for on-prem and air-gapped deployments where data cannot leave the network.

## Future Roadmap

Features that would further differentiate sqlsage4j from competitors.

### HIGH IMPACT

#### 1. More LLM Providers

Anthropic Claude, Google Gemini, Mistral, Groq, AWS Bedrock clients. Expanding provider coverage directly increases adoption.

#### 2. External Vector Store Modules

Production-grade durable vector stores as optional dependency modules:
- `sqlsage4j-pinecone`, `sqlsage4j-weaviate`, `sqlsage4j-qdrant`, `sqlsage4j-pgvector`, `sqlsage4j-milvus`

#### 3. Query Cost Estimation

For cloud databases (BigQuery, Snowflake), estimate query cost before execution using dry-run APIs. Prevents surprise bills.

### MEDIUM IMPACT

#### 4. More Embeddings Providers

Cohere, Voyage AI, local sentence-transformers via ONNX Runtime.

#### 5. GraalVM Native Image Support

Verify and document native compilation for serverless/cloud-native deployments with sub-second cold starts.

#### 6. Batch Query Mode

Process a list of questions in parallel, useful for benchmarking, regression testing, and bulk analytics.

#### 7. Query Explanation

After generating SQL, ask the LLM to explain what the query does in plain English. Builds user trust.

#### 8. Auto-Visualization

Full Plotly/chart generation from query results (currently interface-only).
