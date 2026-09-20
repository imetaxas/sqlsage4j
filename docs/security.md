# Security Guide

This document covers security considerations when using sqlsage4j in production.

## API Key Management

### Do

- Store API keys in environment variables or a secrets manager (AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager)
- Use short-lived, scoped tokens where your LLM provider supports them
- Rotate keys regularly

### Do Not

- Hardcode keys in source code or configuration files checked into version control
- Log full request/response payloads (they may contain keys in headers)
- Pass keys through URL query parameters

### Example (environment variable)

```java
LLMProviderConfig config = LLMProviderConfig.builder("gpt-4")
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .build();
```

## SQL Injection Prevention

sqlsage4j generates SQL from LLM output. The generated SQL should be treated as **untrusted input**.

### Recommendations

1. **Read-only database connections** — connect with a user/role that only has `SELECT` privileges.
2. **Query allowlisting** — only execute queries that begin with `SELECT` or `WITH`.
3. **Statement limits** — set query timeout and row-count limits at the JDBC level.
4. **Sandboxed schemas** — restrict the connected user to a read-only replica or a specific schema.

### Example (read-only SQLite)

```java
SQLiteConfig sqliteConfig = new SQLiteConfig();
sqliteConfig.setReadOnly(true);
SQLiteDataSource ds = new SQLiteDataSource(sqliteConfig);
ds.setUrl("jdbc:sqlite:analytics.db");
```

### Example (PostgreSQL read-only role)

```sql
CREATE ROLE sqlsage_reader WITH LOGIN PASSWORD '...' ;
GRANT CONNECT ON DATABASE analytics TO sqlsage_reader;
GRANT USAGE ON SCHEMA public TO sqlsage_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sqlsage_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sqlsage_reader;
```

## Prompt Injection

LLM-generated SQL can be influenced by adversarial input in the user's question. Mitigations:

1. **Input validation** — reject questions containing SQL keywords (`DROP`, `DELETE`, `ALTER`, `TRUNCATE`) if your use case is read-only analytics.
2. **Output validation** — sqlsage4j already validates SQL with `EXPLAIN` before execution (when a `DatabaseConnector` is configured). Ensure this is enabled.
3. **Self-correction loop** — enable the retry pipeline so invalid SQL is caught and re-generated.
4. **Audit logging** — log all generated SQL before execution for forensic review.

## Network Security

### LLM API calls

- Use HTTPS (enforced by default for OpenAI/cloud providers)
- For Ollama, if running remotely, use TLS or run behind a reverse proxy

### Database connections

- Use TLS for remote database connections
- Prefer Unix sockets for local connections
- Never expose database ports to the public internet

## Dependency Security

- sqlsage4j has minimal runtime dependencies (Gson, Log4j2, JDBC drivers)
- Run `mvn dependency:tree` to audit transitive dependencies
- Use tools like `dependabot` or `snyk` to get alerts on vulnerable dependencies
- Pin dependency versions in your `pom.xml`

## Data Privacy

- LLM providers may retain prompts and completions — review your provider's data retention policy
- For sensitive data, use a self-hosted model (Ollama, vLLM) to keep data on-premises
- The training data (DDLs, sample Q&A) is sent as part of the prompt — ensure it does not contain PII or secrets
- Consider redacting column names or using aliases for sensitive columns in DDL training data

## Checklist

| Concern | Mitigation |
|---------|-----------|
| API key exposure | Environment variables / secrets manager |
| SQL injection | Read-only connections, query allowlisting |
| Prompt injection | Input validation, EXPLAIN check, retry |
| Data leakage to LLM | Self-hosted models, redacted DDLs |
| Network eavesdropping | TLS everywhere |
| Dependency vulnerabilities | Automated scanning (Dependabot/Snyk) |
