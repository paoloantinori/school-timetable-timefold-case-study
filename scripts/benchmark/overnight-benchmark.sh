#!/usr/bin/env bash
# Overnight Benchmark Suite for Phase 3 Constraint Changes
# Runs comprehensive benchmarks without manual approval
set -euo pipefail

cd "$(dirname "$0")/../.."
LOG_DIR="local/benchmark-logs"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="$LOG_DIR/benchmark-$TIMESTAMP.log"

mkdir -p "$LOG_DIR"

echo "============================================================" | tee -a "$LOG_FILE"
echo "  Overnight Benchmark Suite" | tee -a "$LOG_FILE"
echo "  Started: $(date)" | tee -a "$LOG_FILE"
echo "  Log: $LOG_FILE" | tee -a "$LOG_FILE"
echo "============================================================" | tee -a "$LOG_FILE"

# Verify app is running (fail if not - user should start it manually)
if ! ./planner.sh status &>/dev/null; then
  echo "[$(date)] ERROR: App not running. Start with: ./planner.sh start dev && ./planner.sh wait" | tee -a "$LOG_FILE"
  exit 1
fi
echo "[$(date)] App is running" | tee -a "$LOG_FILE"

# Reset to fresh state for clean benchmarks
echo "[$(date)] Resetting schedule for clean benchmark..." | tee -a "$LOG_FILE"
./scripts/api/api.sh reset >/dev/null 2>&1 || true

run_benchmark() {
  local name="$1"
  local config="$2"
  local duration="$3"

  echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
  echo "[$(date)] Benchmark: $name" | tee -a "$LOG_FILE"
  echo "[$(date)] Config: $config" | tee -a "$LOG_FILE"
  echo "[$(date)] Expected duration: ~$duration" | tee -a "$LOG_FILE"

  local start=$(date +%s)

  # Run pre-assignment first
  echo "[$(date)] Running pre-assignment..." | tee -a "$LOG_FILE"
  ./scripts/api/api.sh pre-assign >/dev/null 2>&1

  # Wait for completion
  for i in {1..120}; do
    STATUS=$(./scripts/api/api.sh pre-assign-status 2>/dev/null | jq -r '.status // "UNKNOWN"')
    if [[ "$STATUS" == "COMPLETE" ]]; then
      echo "[$(date)] Pre-assignment complete" | tee -a "$LOG_FILE"
      break
    elif [[ "$STATUS" == "ERROR" ]]; then
      echo "[$(date)] Pre-assignment failed!" | tee -a "$LOG_FILE"
      return 1
    fi
    sleep 2
  done

  # Run solver with this config
  echo "[$(date)] Starting solver..." | tee -a "$LOG_FILE"
  ./scripts/api/api.sh solve >/dev/null 2>&1

  # Wait for solver completion
  local solver_start=$(date +%s)
  while true; do
    STATUS=$(./scripts/api/api.sh status 2>/dev/null | jq -r '.solverStatus // "NOT_SOLVING"')
    SCORE=$(./scripts/api/api.sh score 2>/dev/null | jq -r '.')

    if [[ "$STATUS" == "NOT_SOLVING" ]]; then
      local end=$(date +%s)
      local solver_duration=$((end - solver_start))
      echo "[$(date)] Solver finished in ${solver_duration}s" | tee -a "$LOG_FILE"
      break
    fi

    local elapsed=$(( $(date +%s) - solver_start ))
    echo -ne "\r[$(date)] Solving... ${elapsed}s | Score: ${SCORE}      " | tee -a "$LOG_FILE"
    sleep 5
  done
  echo "" | tee -a "$LOG_FILE"

  # Capture final results
  echo "[$(date)] Capturing results..." | tee -a "$LOG_FILE"
  ./scripts/api/api.sh analysis > "$LOG_DIR/$name-analysis.json"
  ./scripts/api/api.sh phase-progression > "$LOG_DIR/$name-progression.json"

  # Extract key metrics
  local final_score=$(jq -r '.score' "$LOG_DIR/$name-analysis.json")
  local hard_score=$(jq -r '.hardScore' "$LOG_DIR/$name-analysis.json")
  local medium_score=$(jq -r '.mediumScore' "$LOG_DIR/$name-analysis.json")
  local soft_score=$(jq -r '.softScore' "$LOG_DIR/$name-analysis.json")

  echo "[$(date)] Final Score: $final_score" | tee -a "$LOG_FILE"
  echo "[$(date)]   Hard: $hard_score | Medium: $medium_score | Soft: $soft_score" | tee -a "$LOG_FILE"

  local end=$(date +%s)
  local total_duration=$((end - start))
  echo "[$(date)] Total time: ${total_duration}s" | tee -a "$LOG_FILE"

  # Stop solver
  ./scripts/api/api.sh stop >/dev/null 2>&1 || true
}

# Run benchmark suite
echo "" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
echo "[$(date)] Starting Benchmark Suite" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"

# Benchmark 1: Current config (baseline)
run_benchmark "baseline-current" "solverConfig.xml" "~5 min"

# Benchmark 2: Phase 3 60s unimproved
run_benchmark "phase3-60s" "solverConfig-phase3-60s.xml" "~3 min"

# Benchmark 3: Phase 3 120s unimproved
run_benchmark "phase3-120s" "solverConfig-phase3-120s.xml" "~5 min"

# Benchmark 4: Phase 3 180s unimproved
run_benchmark "phase3-180s" "solverConfig-phase3-180s.xml" "~7 min"

echo "" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
echo "[$(date)] Benchmark Suite Complete" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"

# Generate summary
echo "" | tee -a "$LOG_FILE"
echo "[$(date)] Summary:" | tee -a "$LOG_FILE"
for file in "$LOG_DIR"/*-analysis.json; do
  name=$(basename "$file" -analysis.json)
  score=$(jq -r '.score' "$file")
  hard=$(jq -r '.hardScore' "$file")
  medium=$(jq -r '.mediumScore' "$file")
  echo "  $name: $score (H:$hard M:$medium)" | tee -a "$LOG_FILE"
done

echo "" | tee -a "$LOG_FILE"
echo "[$(date)] Benchmark suite finished at $(date)" | tee -a "$LOG_FILE"
echo "[$(date)] Logs saved to $LOG_DIR" | tee -a "$LOG_FILE"
echo "============================================================" | tee -a "$LOG_FILE"
