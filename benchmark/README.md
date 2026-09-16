# sqlsage4j Benchmark Harness

Compares **sqlsage4j** (Java) and **Vanna** (Python) on the same text-to-SQL benchmark: 50 questions across 5 difficulty tiers evaluated on the same SQLite database using the same LLM.

## Metrics

| Category | Metrics |
|---|---|
| **Accuracy** | Execution Accuracy (EX), Exact Match (EM), Valid SQL Rate |
| **Performance** | End-to-end latency (P50/P95/P99), average latency |
| **Robustness** | SQL injection resistance, out-of-scope rejection, ambiguity handling |

## Question Tiers

| Tier | Count | Description | Examples |
|---|---|---|---|
| 1 — Simple | 10 | Single-table, direct filters | "How many users do we have?" |
| 2 — Moderate | 10 | Aggregations, GROUP BY | "Total revenue from completed orders" |
| 3 — Complex | 10 | JOINs, HAVING, CASE | "Top 5 customers by spend" |
| 4 — Advanced | 10 | CTEs, window functions, subqueries | "Month-over-month revenue growth" |
| 5 — Adversarial | 10 | SQL injection, vague, out-of-scope | "DROP TABLE users; SELECT 1;" |

## Prerequisites

- Java 17+ (tested on 25)
- Maven 3.5+
- An OpenAI API key (or local Ollama server)
- Python 3.10+ with `vanna` installed (for the Vanna comparison)

```bash
pip install 'vanna[openai,chromadb]'  # Only needed for Vanna comparison
```

## Quick Start

```bash
# 1. Build parent project (from repo root)
mvn install -DskipTests

# 2. Build benchmark
cd benchmark
mvn package

# 3. Run (requires OPENAI_API_KEY)
export OPENAI_API_KEY=sk-...
./scripts/run_benchmark.sh
```

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `MODEL` | `gpt-4o` | LLM model to use |
| `TEMPERATURE` | `0.0` | Sampling temperature |
| `MAX_TOKENS` | `4096` | Max response tokens |
| `RUNS` | `1` | Number of runs per question |
| `OPENAI_API_KEY` | — | API key |
| `OPENAI_BASE_URL` | — | Custom endpoint (Ollama, etc.) |
| `OUTPUT_DIR` | `benchmark/results` | Where to write reports |

### Running with Ollama

```bash
export MODEL=llama3
export OPENAI_BASE_URL=http://localhost:11434
./scripts/run_benchmark.sh
```

## Output

After a run, `results/` contains:

- `benchmark-report.md` — Summary table comparing both frameworks
- `benchmark-results.csv` — Per-question detail for further analysis
- `benchmark-results.json` — Raw structured results

## Architecture

```
BenchmarkRunner
├── SqlSage4jHarness    (Java, in-process)
│   └── SqlSage4j.builder() → QueryChat.ask()
├── VannaHarness        (Python subprocess, JSON over stdin/stdout)
│   └── vanna_harness.py → vn.generate_sql()
├── ResultSetComparator (runs both SQLs against SQLite, compares result sets)
├── AccuracyEvaluator   (computes EX, EM, Valid SQL, per-tier breakdown)
└── ReportGenerator     (Markdown + CSV output)
```

## Project Structure

```
benchmark/
├── pom.xml
├── scripts/
│   ├── run_benchmark.sh         # One-command runner
│   └── vanna_harness.py         # Python Vanna bridge
├── src/main/java/.../benchmark/
│   ├── BenchmarkRunner.java     # Main entry point
│   ├── BenchmarkConfig.java     # CLI arg parsing + defaults
│   ├── BenchmarkQuestion.java   # Question model (50 questions)
│   ├── BenchmarkResult.java     # Per-question result
│   ├── FrameworkHarness.java    # Interface for framework adapters
│   ├── SqlSage4jHarness.java    # sqlsage4j adapter
│   ├── VannaHarness.java        # Vanna adapter (subprocess)
│   ├── TrainingData.java        # DDLs + Q&As + docs
│   ├── AccuracyEvaluator.java   # Metric computation
│   ├── SqlNormalizer.java       # SQL normalization for EM
│   ├── ResultSetComparator.java # Execution accuracy via SQLite
│   └── ReportGenerator.java     # Markdown + CSV output
└── src/main/resources/
    ├── schema.sql               # 4-table SaaS schema
    ├── seed-data.sql            # 20 users, 30 orders, 30 events, 20 subs
    ├── questions.json           # 50 benchmark questions
    ├── training-data.json       # Shared RAG training data
    └── log4j2.xml
```
