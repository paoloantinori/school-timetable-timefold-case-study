#!/usr/bin/env bash
# Prepare a pinned benchmark input file by running pre-assignment then exporting.
# Usage: ./scripts/benchmark/prepare-pinned-benchmark.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

APP_BASE="http://localhost:8080"
PINNED_FILE="local/data/schedule-production-pinned.json"

# Check app is running
if ! lsof -i :8080 -t >/dev/null 2>&1; then
  echo "ERROR: App not running on port 8080. Start it first with ./planner.sh start <profile>"
  exit 1
fi

# Check solver is idle
STATUS=$(curl -s "$APP_BASE/api/schedule" | python3 -c "import sys,json; print(json.load(sys.stdin).get('solverStatus','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
if [ "$STATUS" = "SOLVING" ]; then
  echo "ERROR: Solver is currently running. Stop it first."
  exit 1
fi

echo "=== Pre-assignment + Export for Benchmark ==="
echo "App: $APP_BASE"
echo "Output: $PINNED_FILE"
echo ""

# Step 1: Run pre-assignment
echo "Step 1: Running pre-assignment..."
RESPONSE=$(curl -s -X POST "$APP_BASE/api/schedule/pre-assign")
PRE_STATUS=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','UNKNOWN'))" 2>/dev/null || echo "PARSE_ERROR")

if [ "$PRE_STATUS" != "COMPLETE" ]; then
  echo "Pre-assignment status: $PRE_STATUS"
  echo "Response: $RESPONSE"
  echo ""
  echo "WARNING: Pre-assignment did not complete successfully."
  echo "Proceeding with export anyway (may have partial pins)..."
else
  echo "Pre-assignment: COMPLETE"
fi
echo ""

# Step 2: Export schedule
echo "Step 2: Exporting schedule with pinned lessons..."
curl -s "$APP_BASE/api/schedule/export" > "$PINNED_FILE"

# Step 3: Count pinned lessons
PINNED_COUNT=$(python3 -c "
import json, sys
with open('$PINNED_FILE') as f:
    data = json.load(f)
lessons = data.get('lessonList', [])
pinned = [l for l in lessons if l.get('pinned', False)]
print(f'{len(pinned)} pinned / {len(lessons)} total')
" 2>/dev/null || echo "PARSE_ERROR")

echo "Pinned lessons: $PINNED_COUNT"
echo "Saved to: $PINNED_FILE"
echo ""
echo "=== Ready for benchmark ==="
