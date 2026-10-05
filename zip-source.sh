#!/usr/bin/env bash
#
# Zips sqlsage4j source code for minimal storage/transfer.
# Excludes all build artifacts, caches, and generated files.
# The output is fully compilable with: mvn compile
#
# Usage: ./zip-source.sh [output-file]
#   Default output: sqlsage4j-source.zip

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="${1:-sqlsage4j-source.zip}"

cd "$PROJECT_DIR"

rm -f "$OUTPUT"

zip -r "$OUTPUT" . \
  -x '.git/*' \
  -x '**/target/*' \
  -x 'target/*' \
  -x '.idea/*' \
  -x '*.iml' \
  -x '.vscode/*' \
  -x '.settings/*' \
  -x '.classpath' \
  -x '.project' \
  -x '.DS_Store' \
  -x 'Thumbs.db' \
  -x '*.log' \
  -x '.env' \
  -x 'dependency-reduced-pom.xml' \
  -x 'benchmark/.venv/*' \
  -x 'benchmark/__pycache__/*' \
  -x 'benchmark/*.pyc' \
  -x 'concept-model/output/*' \
  -x '*.class' \
  -x '*.jar' \
  -x '*.war' \
  -x '*.ear' \
  -x '*.zip' \
  -x '*.tar.gz' \
  -x 'sqlsage4j-source.zip'

SIZE=$(du -h "$OUTPUT" | cut -f1)
echo ""
echo "Created: $OUTPUT ($SIZE)"
echo "To build: unzip $OUTPUT -d sqlsage4j && cd sqlsage4j && mvn compile"
