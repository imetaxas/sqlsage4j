# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Local LLM hardening guide** — comprehensive documentation proving 100% accuracy with free Ollama models (`docs/local-llm-guide.md`)
- **Hardened Ollama integration test** — `OllamaHardenedIT` demonstrates all 7 accuracy steps achieving 10/10 correct answers
- **Streaming responses** — `OllamaStreamingClient` and `OpenAIStreamingClient` with token-by-token delivery via Java `Stream<StreamToken>` or callbacks
- **Async API** — `askAsync()` and `runAsync()` returning `CompletableFuture` for non-blocking pipelines
- **Confidence scoring** — every `QueryResponse` now includes a 0.0–1.0 confidence score based on retrieval similarity, SQL validation, and structure analysis
- **Schema change detection** — `SchemaChangeDetector` detects drift between live database and trained DDLs (new/removed tables and columns)
- **Rate limiting** — `RateLimitedLLMClient` decorator with token-bucket throttling and concurrency control
- **Structured output / JSON mode** — `ResponseFormat.JSON` enables reliable JSON-based SQL extraction from LLMs that support structured output
- **Persistent training store** — `PersistentEmbeddingsStorage` auto-saves training data to JSON on every write, auto-loads on startup
- **Spring Boot starter** — `sqlsage4j-spring-boot-starter` with auto-configuration, health indicator, and property-driven setup
- **SQL safety guardrails** — `SqlGuard` blocks destructive SQL (DROP, DELETE, TRUNCATE, etc.) before execution
- **Auto-schema introspection** — `SchemaIntrospector` discovers tables, columns, types, and foreign keys from live JDBC connections
- **Hybrid search** — `HybridEmbeddingsStorage` combines vector similarity and BM25 keyword search via Reciprocal Rank Fusion
- **Training data import/export** — `TrainingDataIO` serializes and deserializes training data to portable JSON
- **Retry/resilience** — `RetryingLLMClient` with exponential backoff and configurable retry predicates
- **LLM caching** — `CachingLLMClient` with LRU eviction and TTL-based expiration
- **Observability hooks** — `PipelineListener` interface with callbacks for context retrieval, prompt assembly, LLM response, SQL extraction, and pipeline completion
- BM25-based retrieval storage for local LLM setups without embedding models
- SQL validation and self-correction retry loop in the generation pipeline
- Stop sequences for faster LLM response termination
- Deterministic Q&A ordering for small training sets (improves prompt caching)
- `NoOpEmbeddingsProvider` for use with BM25 storage
- `CachingEmbeddingsProvider` decorator for embedding result caching
- Integration test suite covering end-to-end flows (SQLite, streaming, new features)
- WireMock-based tests for OpenAI and Ollama clients
- BigQuery emulator integration tests via Testcontainers

### Changed
- `QueryResponse` now includes `confidence()` field (defaults to 0.5 for unscored results)
- `SqlGenerationPipeline` now limits few-shot examples to top 3 by default
- `TrainingService.retrieveAllContext()` computes embeddings once per query and returns top similarity score

### Fixed
- Result set comparison now handles numeric approximation and column-subset matching
- Adversarial/destructive SQL detection in prompt guidelines

## [0.1.0] - 2026-07-20

### Added
- Core text-to-SQL generation pipeline with RAG
- Multi-turn conversational query rewriting
- LLM provider abstraction (`LLMClient` interface)
  - OpenAI client (GPT-4, GPT-3.5, any compatible API)
  - Ollama client (local LLMs: Llama 3, Mistral, CodeLlama, etc.)
- Embeddings provider abstraction (`EmbeddingsProvider` interface)
  - OpenAI embeddings (`text-embedding-3-small`)
  - Ollama embeddings (`nomic-embed-text`, etc.)
- Six vector storage backends (`EmbeddingsStorage` interface)
  - InMemory, Lucene, H2, SQLite, HNSW, LSH
- Six database connectors (`DatabaseConnector` interface)
  - SQLite, PostgreSQL, MySQL, DuckDB, Snowflake, BigQuery
- Training data management (DDLs, Q&A pairs, documentation)
- `LLMDiff` — A/B testing framework for comparing LLM configurations
- `QueryChat` — full ask/run/export/train workflow
- `LLMChat` — general-purpose RAG chat
- Prompt template system with customizable system prompts
- DataFrame model with export to CSV, JSON, and text
- Conversation history tracking
- Follow-up question generation
- Result summarization

[Unreleased]: https://github.com/yanimetaxas/sqlsage4j/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/yanimetaxas/sqlsage4j/releases/tag/v0.1.0
