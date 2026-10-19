#!/usr/bin/env bash
# Timefold Benchmarker Runner
#
# Runs the Timefold benchmarker with proper progress tracking and error handling.
# Continues even if individual benchmarks fail.
#
# Usage:
#   ./scripts/benchmark/run-benchmarker.sh [config-file]
#
#   config-file: Path to solver benchmark config XML
#                 Default: src/main/resources/solverBenchmarkConfig.xml
#
# Environment Variables:
#   MAVEN_OPTS  - JVM options for Maven (e.g., "-Xmx8G")
#   BENCHMARK_DIR - Output directory for reports (default: local/benchmarkReport)
#   BENCHMARK_SYSTEM_PROPERTIES - extra -D flags spliced into the mvn command line
#                 (surefire passes them to the forked benchmark JVM), e.g.
#                 "-Dplanner.room-tier.override=zero"

set -o pipefail  # Don't use -e (exit on error) - we want to continue on failures

# Script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

# Configuration
CONFIG_FILE="${1:-src/main/resources/solverBenchmarkConfig.xml}"
BENCHMARK_DIR="${BENCHMARK_DIR:-local/benchmarkReport}"
LOG_DIR="local/benchmark-logs"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="$LOG_DIR/benchmarker-$TIMESTAMP.log"
PID_FILE="$LOG_DIR/benchmarker.pid"

# Create directories
mkdir -p "$LOG_DIR" "$BENCHMARK_DIR"

# Clean up on exit
cleanup() {
  local exit_code=$?
  if [[ -f "$PID_FILE" ]]; then
    rm -f "$PID_FILE"
  fi
  if [[ $exit_code -ne 0 ]]; then
    echo "[$(date)] Benchmark exited with code $exit_code" | tee -a "$LOG_FILE"
  fi
  exit $exit_code
}
trap cleanup EXIT INT TERM

# Logging function
log() {
  echo "[$(date)] $*" | tee -a "$LOG_FILE"
}

# Error logging that doesn't exit
log_error() {
  echo "[$(date)] ERROR: $*" | tee -a "$LOG_FILE"
}

# Warning logging
log_warning() {
  echo "[$(date)] WARNING: $*" | tee -a "$LOG_FILE"
}

# Progress indicator
show_progress() {
  local elapsed=$1
  local total=$2
  if [[ $total -gt 0 ]]; then
    local percent=$((elapsed * 100 / total))
    printf "\r[$(date)] Progress: %02d%% (%ds/%ds)" "$percent" "$elapsed" "$total"
  else
    printf "\r[$(date)] Elapsed: %ds" "$elapsed"
  fi
}

# ==============================================================================
# Benchmark Setup
# ==============================================================================

log "============================================================"
log "  Timefold Benchmarker Runner"
log "  Started: $(date)"
log "  Config: $CONFIG_FILE"
log "  Output: $BENCHMARK_DIR"
log "  Log: $LOG_FILE"
log "============================================================"
echo ""

# Validate config file exists
if [[ ! -f "$CONFIG_FILE" ]]; then
  log_error "Config file not found: $CONFIG_FILE"
  exit 1
fi

# Count number of benchmarks in config
BENCHMARK_COUNT=$(grep -c '<solverBenchmark>' "$CONFIG_FILE" 2>/dev/null || echo "0")
log "Found $BENCHMARK_COUNT benchmark configuration(s)"

# Estimate time (15 min per benchmark, divided by CPU cores)
if command -v nproc &>/dev/null; then
  CORES=$(nproc)
elif command -v sysctl &>/dev/null; then
  CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo "4")
else
  CORES=4
fi
CORES=${CORES:-4}
ESTIMATED_MINUTES=$((BENCHMARK_COUNT * 15 / CORES))
log "Parallel benchmarks will run on up to $CORES cores"
log "Estimated time: ~${ESTIMATED_MINUTES} minutes"
echo ""

# ==============================================================================
# Pre-flight Checks
# ==============================================================================

log "Running pre-flight checks..."

# Check if input file exists
INPUT_FILE=$(grep '<inputSolutionFile>' "$CONFIG_FILE" | sed 's/.*<inputSolutionFile>\(.*\)<\/inputSolutionFile>.*/\1/' | head -1)
if [[ -n "$INPUT_FILE" && ! -f "$INPUT_FILE" ]]; then
  log_error "Input file not found: $INPUT_FILE"
  log "Please export data first or update the config file"
  exit 1
fi
log "✓ Input file OK: $INPUT_FILE"

# Check if Maven is available
if ! command -v mvn &>/dev/null; then
  log_error "Maven not found in PATH"
  exit 1
fi
log "✓ Maven available"

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
log "✓ Java version: $JAVA_VERSION"

# ==============================================================================
# Run Benchmarker
# ==============================================================================

log "Starting benchmarker..."
log "============================================================"

# Store PID for tracking
echo $$ > "$PID_FILE"

# Record start time
START_TIME=$(date +%s)

# Maven command to run benchmarker
# Select test class based on config file
if [[ "$CONFIG_FILE" == *"Test.xml" ]]; then
  TEST_CLASS="LocalSearchComparisonBenchmark"
else
  TEST_CLASS="ComprehensiveLocalSearchBenchmark"
fi
MAVEN_CMD="mvn test -Dtest=$TEST_CLASS -Dbenchmark.config=$CONFIG_FILE${BENCHMARK_SYSTEM_PROPERTIES:+ $BENCHMARK_SYSTEM_PROPERTIES}"

log "Running: $MAVEN_CMD"
if [[ "$BENCHMARK_SYSTEM_PROPERTIES" == *room-tier.override=zero* || "$BENCHMARK_SYSTEM_PROPERTIES" == *room-tier.override=damped* ]]; then
  log "NOTE: room-tier override ARMED requested (surefire passes the -D to the forked benchmark JVM): the runner banner 'ROOM-TIER OVERRIDE (benchmark battery): ... ARMED' MUST appear below (a missing banner = the knob never reached the run; an explicit =none banner is the unarmed control, not a failure)"
fi
echo ""

# Run the benchmarker and capture output
# We use 'tee' to capture output to both log and stdout
# We DON'T use 'set -e' so the script continues even if Maven fails

start_ts=$(date +%s)
last_progress=0

# Run Maven in background with progress monitoring
$MAVEN_CMD 2>&1 | tee -a "$LOG_FILE" | while IFS= read -r line; do
  # Print line to stdout
  echo "$line"

  # Update progress every 10 seconds
  now=$(date +%s)
  if [[ $((now - last_progress)) -ge 10 ]]; then
    elapsed=$((now - start_ts))
    show_progress "$elapsed" "$((ESTIMATED_MINUTES * 60))"
    last_progress=$now
  fi
done

MAVEN_EXIT_CODE=${PIPESTATUS[0]}

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
DURATION_MINUTES=$((DURATION / 60))
DURATION_SECONDS=$((DURATION % 60))

echo ""
log "============================================================"
log "Benchmark completed in ${DURATION_MINUTES}m ${DURATION_SECONDS}s"
log "Exit code: $MAVEN_EXIT_CODE"

# ==============================================================================
# Post-Processing
# ==============================================================================

if [[ -d "$BENCHMARK_DIR" ]]; then
  log "✓ Benchmark report directory exists: $BENCHMARK_DIR"

  # List generated files
  # (pre-existing bug found in review: these were `local` OUTSIDE any function, which bash
  # rejects, leaving both vars empty and this block silent; plain assignments fix it)
  report_files=$(find "$BENCHMARK_DIR" -type f \( -name "*.html" -o -name "*.csv" -o -name "*.xml" \) 2>/dev/null | wc -l)
  log "✓ Found $report_files report file(s)"

  # Find summary CSV if it exists
  summary_csv=$(find "$BENCHMARK_DIR" -name "*summary*.csv" -o -name "benchmark*.csv" 2>/dev/null | head -1)
  if [[ -n "$summary_csv" && -f "$summary_csv" ]]; then
    log "✓ Summary CSV: $summary_csv"
    echo ""
    log "Top 5 configurations by score:"
    head -6 "$summary_csv" | tee -a "$LOG_FILE" || log_warning "Could not read summary CSV"
  fi
else
  log_warning "Benchmark report directory not found: $BENCHMARK_DIR"
fi

# ==============================================================================
# Final Summary
# ==============================================================================

echo ""
log "============================================================"
log "  Benchmark Complete"
log "  Finished: $(date)"
log "  Duration: ${DURATION_MINUTES}m ${DURATION_SECONDS}s"
log "  Config: $CONFIG_FILE"
log "  Logs: $LOG_FILE"
log "  Reports: $BENCHMARK_DIR"
log "============================================================"

if [[ $MAVEN_EXIT_CODE -eq 0 ]]; then
  log "✓ All benchmarks completed successfully"
  exit 0
else
  log_error "Some benchmarks failed (exit code $MAVEN_EXIT_CODE)"
  log "Check the log file for details: $LOG_FILE"
  exit $MAVEN_EXIT_CODE
fi
