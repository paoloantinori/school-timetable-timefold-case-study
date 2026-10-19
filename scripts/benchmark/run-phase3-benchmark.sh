#!/usr/bin/env bash
# Run Phase 3 Benchmark with PostgreSQL production data
# Usage: ./scripts/benchmark/run-phase3-benchmark.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
echo "============================================================"
echo "  Phase 3 Benchmark (Production Data)"
echo "  Comparing 60s, 120s, 180s unimproved limits"
echo "============================================================"

# Ensure PostgreSQL database is running
if ! ./planner.sh status &>/dev/null; then
  echo "Starting PostgreSQL and app..."
  ./planner.sh start dev >/dev/null 2>&1
  ./planner.sh wait
fi

# Run the integration test benchmark (connects to PostgreSQL)
echo "Starting benchmark (this will take 10-15 minutes)..."
./scripts/core/test.sh -c SchoolScheduleBenchmark -m phase3CompactnessBenchmark

echo ""
echo "============================================================"
echo "  Report: local/benchmarkReport/index.html"
echo "============================================================"
