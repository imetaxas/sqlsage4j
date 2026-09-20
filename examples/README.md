# Examples

Standalone examples showing how to use sqlsage4j in different configurations.
All examples work with **free local models** via Ollama — no API key or cloud account needed.

| File | Description |
|---|---|
| [QuickStart.java](QuickStart.java) | Minimal 20-line example with Ollama + auto-schema discovery |
| [OpenAIExample.java](OpenAIExample.java) | Production setup with OpenAI, safety guards, and training |
| [StreamingExample.java](StreamingExample.java) | Real-time token streaming with Ollama |

## Prerequisites

1. **Java 17+** installed
2. **Ollama** for local examples: `brew install ollama && ollama pull llama3.1:8b && ollama pull nomic-embed-text`
3. **A database** — examples use the [Chinook SQLite database](https://github.com/lerocha/chinook-database/releases)

## Running

```bash
# Download the Chinook sample database
curl -L -o chinook.db https://github.com/lerocha/chinook-database/raw/master/ChinookDatabase/DataSources/Chinook_Sqlite.sqlite

# Compile and run (replace version with your build)
javac -cp sqlsage4j-0.1.0.jar QuickStart.java
java -cp .:sqlsage4j-0.1.0.jar QuickStart
```

Or add sqlsage4j to a Maven/Gradle project and copy any example into your `src/main/java`.
