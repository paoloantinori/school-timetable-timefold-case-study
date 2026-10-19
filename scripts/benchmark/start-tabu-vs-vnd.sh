#!/usr/bin/env bash
# Tabu vs VND Benchmark Launcher
# Starts the benchmark in the background and returns immediately.
# Usage: ./scripts/benchmark/start-tabu-vs-vnd.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

LOG_DIR="local/benchmark-logs"
mkdir -p "$LOG_DIR"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="$LOG_DIR/tabu-vs-vnd-$TIMESTAMP.log"

# Check if benchmark is already running
if [[ -f "$LOG_DIR/benchmarker.pid" ]]; then
  OLD_PID=$(cat "$LOG_DIR/benchmarker.pid")
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Benchmark already running (PID=$OLD_PID). Log: $LOG_DIR/tabu-vs-vnd-*.log"
    exit 0
  fi
  rm -f "$LOG_DIR/benchmarker.pid"
fi

echo "Starting Tabu vs VND benchmark..."
echo "Log: $LOG_FILE"
echo "Config: solverBenchmarkConfigTabuVsVND.xml (9 configs, est. ~135 min)"

# Start benchmark in background — redirect all output to log
nohup mvn test -Dtest=TabuVsVNDBenchmark > "$LOG_FILE" 2>&1 &
BGPID=$!
echo "$BGPID" > "$LOG_DIR/benchmarker.pid"
echo "PID: $BGPID"
echo ""
echo "Monitor with:  tail -f $LOG_FILE"
echo "Check status:  ps -p $BGPID -o pid,etime,command"
echo "Stop:          kill $BGPID"
