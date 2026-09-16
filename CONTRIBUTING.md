# Contributing to sqlsage4j

Thank you for your interest in contributing to sqlsage4j! This guide will help you get started.

## Prerequisites

- Java 17+ (we test on 17 and 21)
- Maven 3.8+
- Git

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone git@github.com:YOUR_USERNAME/sqlsage4j.git
   cd sqlsage4j
   ```
3. Create a branch for your change:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## Building

```bash
mvn compile        # Compile (also auto-formats code)
mvn test           # Run unit tests
mvn verify         # Run unit + integration tests
mvn package        # Build JAR
```

## Code Style

This project uses [Google Java Format](https://github.com/google/google-java-format) enforced by the `fmt-maven-plugin`. Code is auto-formatted on `mvn compile`, so you don't need to configure your IDE — just compile before committing.

To check formatting without auto-fixing:

```bash
mvn fmt:check
```

## Running Tests

```bash
mvn test                          # All unit tests
mvn test -Dtest=SqlExtractorTest  # Single test class
```

Integration tests (require Ollama running locally):

```bash
mvn verify -DskipUTs
```

## Making Changes

### Adding a new LLM provider

1. Implement `io.github.imetaxas.sqlsage4j.client.LLMClient`
2. Add a case to `LLMClientFactory` if auto-detection is desired
3. Write unit tests mocking the HTTP layer
4. Update README with usage example

### Adding a new database connector

1. Implement `io.github.imetaxas.sqlsage4j.db.DatabaseConnector`
2. Add the JDBC driver as an `<optional>` dependency in `pom.xml`
3. Add an entry to `StorageEnum`
4. Write tests (use Testcontainers if a live DB is needed)

### Adding a new embeddings storage backend

1. Implement `io.github.imetaxas.sqlsage4j.storage.EmbeddingsStorage`
2. Write tests covering `store`, `search`, `getAll`, `remove`, `removeAll`
3. Add any dependencies as `<optional>`

## Pull Request Process

1. Ensure all tests pass: `mvn verify`
2. Update the README if your change affects the public API
3. Add an entry to `CHANGELOG.md` under `[Unreleased]`
4. Keep PRs focused — one feature or fix per PR
5. Write a clear PR description explaining **why**, not just **what**

## Commit Messages

Use conventional-ish commit messages:

```
feat: add PostgreSQL schema introspection
fix: handle NULL columns in result set comparison
docs: add streaming API example
test: add integration test for Ollama client
refactor: extract SQL validation into SqlGuard
```

## Reporting Bugs

Open a [GitHub Issue](https://github.com/yanimetaxas/sqlsage4j/issues) with:

- sqlsage4j version
- Java version
- LLM provider and model used
- Minimal reproducing code or test case
- Expected vs. actual behavior

## Feature Requests

Open a [GitHub Issue](https://github.com/yanimetaxas/sqlsage4j/issues) with the `feature_request` template. Describe the use case, not just the solution.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
