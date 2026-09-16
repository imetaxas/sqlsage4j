# Benchmark Plan: sqlsage4j vs Vanna

A plan to systematically compare sqlsage4j and Vanna across accuracy, performance, and developer experience.

## 1. What to Measure

### 1.1 Accuracy Metrics

| Metric | What It Measures | How to Compute |
|---|---|---|
| **Execution Accuracy (EX)** | Does the generated SQL produce the correct result? | Run both generated and gold SQL, compare result sets |
| **Exact Match (EM)** | Is the generated SQL identical to the gold SQL? | Normalize whitespace/case, then string compare |
| **Valid SQL Rate** | Does the generated SQL parse and execute without errors? | Try `EXPLAIN` or execute; count successes |
| **Partial Match** | How many clauses (SELECT, WHERE, GROUP BY, ORDER BY, JOIN) are correct? | Parse SQL AST, compare clause by clause |
| **Semantic Equivalence** | Do structurally different queries return the same results? | Run both queries, compare DataFrames |

**Execution Accuracy (EX)** is the primary metric — it's the industry standard used by Spider, BIRD, and WikiSQL benchmarks. Exact Match is too strict (penalizes valid alternative SQL), but useful as a secondary signal.

### 1.2 Performance Metrics

| Metric | What It Measures | How to Compute |
|---|---|---|
| **End-to-end latency (P50/P95/P99)** | Total time from question → SQL response | Wall-clock timer around `ask()` / `vn.ask()` |
| **Retrieval latency** | Time to retrieve relevant context from vector store | Timer around embedding + search |
| **LLM call latency** | Time waiting for the LLM API | Timer around `submitPrompt()` |
| **Prompt token count** | Total tokens sent to the LLM | Count tokens in assembled prompt |
| **Embedding throughput** | Embeddings generated per second during training | Timer around bulk `trainDdl()`/`trainDocumentation()` |
| **Memory footprint** | Heap usage with N trained items | `Runtime.getRuntime().totalMemory()` / Python `tracemalloc` |
| **Startup time** | Time from cold start to first query ready | Timer from init to first `ask()` |
| **Training throughput** | Records trained per second | Timer around bulk training loop |

### 1.3 Developer Experience Metrics

| Metric | What It Measures | How to Compute |
|---|---|---|
| **Lines of code to first query** | Setup complexity | Count lines in minimal working example |
| **Dependency count** | Deployment complexity | Count required JARs / pip packages |
| **JAR / package size** | Distribution size | Measure artifact size |
| **Time to first query** | Developer onboarding speed | Time a developer from `git clone` to first result |

### 1.4 Robustness Metrics

| Metric | What It Measures | How to Compute |
|---|---|---|
| **Error recovery rate** | Can the system fix broken SQL on retry? | Count queries that fail then succeed after N retries |
| **Ambiguity handling** | Does it ask for clarification or guess? | Rate responses to ambiguous questions |
| **Out-of-scope rejection** | Does it refuse questions about missing tables? | Count correct "I can't answer" responses |
| **SQL injection resistance** | Does it block destructive queries? | Test with adversarial prompts ("drop all tables") |

## 2. Benchmark Dataset

### 2.1 Schema

Use a realistic multi-table schema that both frameworks can query. A SaaS analytics database works well because it has joins, aggregations, and business logic:

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(255),
    plan VARCHAR(20),        -- 'free', 'pro', 'enterprise'
    created_at DATE,
    country VARCHAR(50)
);

CREATE TABLE orders (
    id INT PRIMARY KEY,
    user_id INT REFERENCES users(id),
    amount DECIMAL(10,2),
    status VARCHAR(20),      -- 'completed', 'refunded', 'pending'
    created_at TIMESTAMP
);

CREATE TABLE events (
    id INT PRIMARY KEY,
    user_id INT REFERENCES users(id),
    event_type VARCHAR(50),  -- 'page_view', 'click', 'signup', 'purchase'
    page VARCHAR(100),
    created_at TIMESTAMP
);

CREATE TABLE subscriptions (
    id INT PRIMARY KEY,
    user_id INT REFERENCES users(id),
    plan VARCHAR(20),
    mrr DECIMAL(10,2),
    started_at DATE,
    cancelled_at DATE
);
```

### 2.2 Question Set

50 questions across 5 difficulty tiers:

**Tier 1 — Simple (10 questions)**: Single-table, no joins, no aggregation
- "How many users do we have?"
- "List all enterprise users"
- "What is the email of user with id 42?"

**Tier 2 — Moderate (10 questions)**: Single aggregation or single join
- "Total revenue from completed orders"
- "How many users signed up last month?"
- "Average order amount by plan type"

**Tier 3 — Complex (10 questions)**: Multi-table joins, GROUP BY, HAVING
- "Top 10 customers by total spend"
- "Revenue by country for enterprise users"
- "Users who signed up but never placed an order"

**Tier 4 — Advanced (10 questions)**: Subqueries, window functions, CTEs
- "Month-over-month revenue growth rate"
- "Users whose spending is above the 90th percentile"
- "Running total of MRR by month"

**Tier 5 — Adversarial (10 questions)**: Edge cases, ambiguity, out-of-scope
- "Delete all users" (should refuse)
- "What's the weather today?" (out of scope)
- "Revenue" (ambiguous — total? by month? by user?)
- "Show me the data" (vague)
- "How much money did John spend?" (depends on which John)

Each question has a gold SQL answer and expected result set.

### 2.3 Training Data

Both frameworks get identical training data:
- All 4 DDL statements above
- 10 gold Q&A pairs (from Tier 1-3 questions, NOT from the test set)
- 5 documentation entries (business rules like "revenue = completed orders only")

## 3. Implementation Plan

### 3.1 Project Structure

```
benchmark/
├── pom.xml                          # JMH + sqlsage4j dependency
├── schema.sql                       # Shared schema
├── seed-data.sql                    # Test data (1000 rows per table)
├── questions.json                   # 50 questions with gold SQL + expected results
├── training-data.json               # DDLs, Q&A pairs, docs (shared input)
├── src/main/java/
│   └── io/github/imetaxas/benchmark/
│       ├── BenchmarkRunner.java     # Orchestrates full benchmark suite
│       ├── AccuracyEvaluator.java   # Computes EX, EM, valid rate, partial match
│       ├── PerformanceProfiler.java # Latency, memory, throughput measurement
│       ├── SqlNormalizer.java       # Normalize SQL for comparison
│       ├── ResultSetComparator.java # Compare DataFrames / result sets
│       ├── BenchmarkResult.java     # Data class for results
│       ├── ReportGenerator.java     # Markdown / CSV report output
│       ├── sqlsage4j/
│       │   └── SqlSage4jHarness.java  # Wrapper: init, train, ask, cleanup
│       └── vanna/
│           └── VannaHarness.java      # Wrapper: calls Vanna via subprocess
├── scripts/
│   ├── vanna_harness.py             # Python script wrapping Vanna
│   ├── setup_database.py            # Populate SQLite with seed data
│   └── run_benchmark.sh             # End-to-end runner
└── results/
    └── (generated reports)
```

### 3.2 Phase 1: Setup (1-2 days)

1. **Create benchmark SQLite database** with the schema above and 1,000 rows per table using realistic synthetic data
2. **Write `questions.json`** — 50 questions with gold SQL and expected results
3. **Write `training-data.json`** — shared training data for both frameworks
4. **Create `SqlSage4jHarness`** — wrapper that initializes sqlsage4j with SQLiteConnector, trains, and exposes `ask(question) → {sql, latencyMs}`
5. **Create `VannaHarness`** — wrapper that calls a Python subprocess running `vanna_harness.py` over stdin/stdout JSON protocol:
   ```
   Java → {"action":"ask","question":"..."} → Python subprocess
   Python → {"sql":"SELECT ...","latency_ms":150} → Java
   ```

### 3.3 Phase 2: Accuracy Evaluation (2-3 days)

1. **`AccuracyEvaluator`** runs each question through both harnesses and computes:
   - **Execution Accuracy**: Execute generated SQL + gold SQL against SQLite, compare result sets (order-insensitive)
   - **Exact Match**: Normalize both SQLs (lowercase, collapse whitespace, strip aliases), string compare
   - **Valid SQL Rate**: Try `EXPLAIN` on generated SQL, count successes
   - **Partial Match**: Parse SQL with regex to extract SELECT/WHERE/GROUP BY/ORDER BY clauses, score overlap

2. **`ResultSetComparator`** handles:
   - Order-insensitive comparison (sort both result sets)
   - Type coercion (int vs string "42")
   - NULL handling
   - Subset matching (if gold has 5 columns and generated has 3 correct ones)

3. **Run 3 times** to account for LLM non-determinism (temperature > 0). Report mean ± std.

### 3.4 Phase 3: Performance Profiling (1-2 days)

1. **Latency breakdown** using `System.nanoTime()` around:
   - Training phase (bulk train all DDLs + docs + Q&A)
   - Retrieval (embedding generation + vector search)
   - LLM call (API round-trip)
   - SQL extraction (regex parsing)
   - Total end-to-end

2. **Memory profiling**:
   - Record heap before/after training 100, 500, 1000 items
   - Java: `ManagementFactory.getMemoryMXBean()`
   - Python: `tracemalloc`

3. **Startup time**: Cold-start from process launch to first query ready

4. **Use same LLM** for both (e.g., GPT-4o or Ollama llama3) to isolate framework overhead from model quality

### 3.5 Phase 4: Report Generation (1 day)

**`ReportGenerator`** produces:

1. **Summary table** (Markdown):
   ```
   | Metric              | sqlsage4j | Vanna   | Winner |
   |---------------------|-----------|---------|--------|
   | Execution Accuracy  | 78%       | 82%     | Vanna  |
   | Valid SQL Rate      | 92%       | 88%     | sqlsage4j |
   | Avg Latency (ms)    | 1200      | 1450    | sqlsage4j |
   | P95 Latency (ms)    | 2100      | 2800    | sqlsage4j |
   | Memory (MB)         | 45        | 180     | sqlsage4j |
   | Training Speed (r/s)| 50        | 30      | sqlsage4j |
   ```

2. **Per-tier breakdown** — accuracy by difficulty tier (Tier 1 through 5)

3. **Per-question detail** — for each question: both generated SQLs, both results, pass/fail, latency

4. **Charts** (optional) — bar charts comparing metrics, generated as SVG or using a plotting library

## 4. Controlling Variables

For a fair comparison, these must be identical:

| Variable | How to Control |
|---|---|
| LLM model | Same model for both (e.g., `gpt-4o` or `llama3`) |
| Temperature | Both set to 0.0 (deterministic) |
| Max tokens | Both set to 4096 |
| Training data | Exact same DDLs, Q&A pairs, docs loaded into both |
| Database | Same SQLite file with same seed data |
| Vector store | Both use their default in-memory stores |
| Embeddings model | Same model (e.g., `text-embedding-3-small` or `nomic-embed-text`) |
| Hardware | Run sequentially on same machine |
| Network | Same API endpoint, run back-to-back to minimize variance |
| Retries | Neither framework gets retries (measure single-shot accuracy) |

## 5. Metrics That Make This Unique

Beyond standard accuracy/latency, these metrics would differentiate the comparison:

| Metric | Why It Matters |
|---|---|
| **Cost per query** | Token usage × price — JVM overhead vs Python overhead |
| **Accuracy vs training data size** | Plot accuracy at 0, 5, 10, 25, 50 training items — shows learning curve |
| **Cold start penalty** | First-query latency vs steady-state — JVM warmup vs Python import |
| **Multi-turn accuracy** | Accuracy on follow-up questions that depend on context |
| **Degradation under schema complexity** | How accuracy drops as tables/columns increase (10 → 50 → 100 columns) |
| **Prompt efficiency** | Tokens consumed per correct answer — less is better |
| **Recovery from bad training** | Add incorrect Q&A pairs, measure accuracy impact |

## 6. Running the Benchmark

```bash
# Setup
cd benchmark
python scripts/setup_database.py          # Create + seed SQLite
mvn clean package -DskipTests             # Build Java harness

# Run
./scripts/run_benchmark.sh \
    --model gpt-4o \
    --temperature 0.0 \
    --runs 3 \
    --output results/

# Or run individual components
java -jar target/benchmark.jar --framework sqlsage4j --questions questions.json
python scripts/vanna_harness.py --questions questions.json
java -jar target/benchmark.jar --evaluate results/
```

## 7. Expected Outcomes

Based on Vanna's published data (60-80% accuracy with training) and the architectural similarities:

| Area | Expected Winner | Why |
|---|---|---|
| Simple query accuracy | Tie | Same LLM, same training data |
| Complex query accuracy | Vanna (slight edge) | More mature prompt engineering |
| Valid SQL rate | Tie | Both use regex extraction |
| End-to-end latency | sqlsage4j | JVM HTTP client is faster than Python requests |
| Memory footprint | sqlsage4j | JVM with in-memory store vs Python + ChromaDB |
| Training speed | sqlsage4j | Java vector math is faster than Python |
| Startup time | Vanna | Python imports faster than JVM cold start |
| Multi-turn accuracy | sqlsage4j | Built-in conversation rewriting |
| Adversarial resistance | Tie (both weak) | Neither has guardrails yet |

This benchmark would be the **first published Java vs Python text-to-SQL comparison** — valuable content for a blog post and README.
