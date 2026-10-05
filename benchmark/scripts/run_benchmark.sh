#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCHMARK_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$BENCHMARK_DIR")"

# Defaults
MODEL="${MODEL:-gpt-4o}"
TEMPERATURE="${TEMPERATURE:-0.0}"
MAX_TOKENS="${MAX_TOKENS:-4096}"
RUNS="${RUNS:-1}"
OUTPUT_DIR="${OUTPUT_DIR:-$BENCHMARK_DIR/results}"
DATABASE="$BENCHMARK_DIR/benchmark.db"

echo "======================================"
echo " sqlsage4j vs Vanna Benchmark"
echo "======================================"
echo "Model:       $MODEL"
echo "Temperature: $TEMPERATURE"
echo "Max tokens:  $MAX_TOKENS"
echo "Runs:        $RUNS"
echo "Output:      $OUTPUT_DIR"
echo ""

# Step 1: Build sqlsage4j if needed
if [ ! -f "$PROJECT_DIR/target/sqlsage4j-0.1.0-SNAPSHOT.jar" ]; then
    echo "[1/4] Building sqlsage4j..."
    (cd "$PROJECT_DIR" && mvn clean package -DskipTests -q)
else
    echo "[1/4] sqlsage4j already built."
fi

# Step 2: Build benchmark module
echo "[2/4] Building benchmark harness..."
(cd "$BENCHMARK_DIR" && mvn clean package -q)

# Step 3: Check Vanna is installed
echo "[3/4] Checking Vanna installation..."
if python3 -c "import vanna" 2>/dev/null; then
    echo "  Vanna: installed"
    RUN_VANNA=true
else
    echo "  Vanna: NOT installed (skipping). Install with: pip install 'vanna[openai,chromadb]'"
    RUN_VANNA=false
fi

# Step 4: Run benchmark
echo "[4/4] Running benchmark..."
echo ""

# Clean up old database
rm -f "$DATABASE"

ARGS=(
    --model "$MODEL"
    --temperature "$TEMPERATURE"
    --max-tokens "$MAX_TOKENS"
    --runs "$RUNS"
    --database "$DATABASE"
    --output "$OUTPUT_DIR"
)

if [ -n "${OPENAI_API_KEY:-}" ]; then
    ARGS+=(--api-key "$OPENAI_API_KEY")
fi

if [ -n "${OPENAI_BASE_URL:-}" ]; then
    ARGS+=(--base-url "$OPENAI_BASE_URL")
fi

java -jar "$BENCHMARK_DIR/target/sqlsage4j-benchmark-0.1.0-SNAPSHOT.jar" "${ARGS[@]}"

echo ""
echo "======================================"
echo " Benchmark complete!"
echo " Results: $OUTPUT_DIR/"
echo "======================================"
