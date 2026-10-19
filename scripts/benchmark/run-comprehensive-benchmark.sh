#!/usr/bin/env bash
#
# Comprehensive Local Search Benchmark Runner
# Runs Timefold benchmarker with 15 algorithm configurations (~5 hours; trimmed from 23 in TASK-283)
#

set -euo pipefail

cd "$(dirname "$0")/../.."

echo "============================================================"
echo "  Comprehensive Local Search Benchmark"
echo "  Started: $(date)"
echo "  Configurations: $(grep -c '<name>' src/main/resources/solverBenchmarkConfig.xml)"
echo "  Expected duration: ~5 hours"
echo "============================================================"
echo ""

# Verify build exists
if [ ! -f "target/quarkus-app/quarkus-run.jar" ]; then
  echo "ERROR: Build not found. Run ./scripts/core/rebuild.sh first."
  exit 1
fi

# Verify benchmark data exists
if [ ! -f "local/data/schedule-production.json" ]; then
  echo "ERROR: Benchmark data not found at local/data/schedule-production.json"
  exit 1
fi

# Ensure app is NOT running (benchmarker runs its own solvers)
if ./planner.sh status &>/dev/null; then
  echo "WARNING: App is running. Stopping it for benchmark..."
  ./planner.sh kill-app
  sleep 2
fi

# Create log directory
mkdir -p local/benchmark-logs
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="local/benchmark-logs/comprehensive-$TIMESTAMP.log"

echo "Starting Timefold Benchmarker via Maven test..."
echo "Log: $LOG_FILE"
echo ""

# Run the benchmark using Maven test
# This uses the QuarkusIntegrationTest to run the benchmarker
mvn test -Dtest=ComprehensiveLocalSearchBenchmark \
  -Dquarkus.test.arg-line="-Xmx2G" \
  2>&1 | tee "$LOG_FILE"

END_TIME=$(date)
echo ""
echo "============================================================"
echo "  Benchmark Complete"
echo "  Finished: $END_TIME"
echo "  Log: $LOG_FILE"
echo "  Report: local/benchmarkReport/"
echo "============================================================"
