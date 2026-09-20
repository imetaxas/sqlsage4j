# Ollama Setup Guide

> **Zero cost. Zero cloud. Zero data leaving your machine.**
> sqlsage4j + Ollama gives you production-quality text-to-SQL without spending a cent on API calls.

This guide walks you through installing and configuring [Ollama](https://ollama.com) for use with sqlsage4j.

## 1. Install Ollama

### macOS

```bash
brew install ollama
```

### Linux

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows

Download the installer from [ollama.com/download](https://ollama.com/download).

## 2. Start the Server

```bash
ollama serve
```

This starts the API on `http://localhost:11434`. The server runs in the foreground — open a second terminal for the next steps.

## 3. Pull Models

You need a **chat model** for SQL generation and an **embeddings model** for the vector store.

### Recommended models

```bash
ollama pull llama3            # 8B chat model (~4.7 GB)
ollama pull nomic-embed-text  # embeddings model (~274 MB)
```

### Lighter alternatives (for machines with less RAM)

```bash
ollama pull phi3              # 3.8B (~2.3 GB)
ollama pull gemma2:2b         # 2B (~1.6 GB)
```

### Code-focused models (better SQL generation)

```bash
ollama pull codellama         # 7B, optimized for code (~3.8 GB)
ollama pull deepseek-coder    # 6.7B, strong on SQL (~3.8 GB)
```

Verify your models are ready:

```bash
ollama list
```

## 4. Verify the Server

Run these curl commands to confirm everything works before touching Java.

### Chat endpoint

```bash
curl http://localhost:11434/api/chat -d '{
  "model": "llama3",
  "messages": [{"role": "user", "content": "Write a SQL query to count users"}],
  "stream": false
}'
```

You should get a JSON response with a `message.content` field containing SQL.

### Embeddings endpoint

```bash
curl http://localhost:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": "hello world"
}'
```

You should get a JSON response with an `embeddings` array of float vectors.

## Tips

- **No API key needed** — Ollama runs fully local, sqlsage4j skips the auth header automatically.
- **GPU acceleration** — Ollama auto-detects Apple Silicon (Metal) and NVIDIA GPUs. No configuration required.
- **Memory usage** — `llama3` (8B) needs ~5 GB RAM. For 16 GB machines it runs fine alongside other apps.
- **Multiple models** — You can pull several models and switch between them by changing the `modelName` in your `LLMProviderConfig`.
- **Custom model port** — If you run Ollama on a non-default port (e.g., `OLLAMA_HOST=0.0.0.0:8080 ollama serve`), update the `baseUrl` accordingly.

## Next Steps

1. See the [Local LLM Support](../README.md#local-llm-support) section in the main README for Java code examples.
2. Follow the [Local LLM Hardening Guide](local-llm-guide.md) to achieve **100% accuracy** — 7 proven steps to maximize results with free models.
