#!/usr/bin/env bash
# Simple Overnight Benchmark - runs solver with different Phase 3 configs
#
# Usage:
#   ./scripts/benchmark/simple-overnight-benchmark.sh
#   FRESH_DB=true ./scripts/benchmark/simple-overnight-benchmark.sh
#
# Environment Variables:
#   FRESH_DB  - If "true", recreates database before first benchmark (default: false)
#               Use this to ensure clean state and avoid pre-assignment failures
#               caused by leftover pinned lessons from previous runs.
#
set -euo pipefail

cd "$(dirname "$0")/../.."
LOG_DIR="local/benchmark-logs"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
LOG_FILE="$LOG_DIR/simple-benchmark-$TIMESTAMP.log"

# Flag to force fresh database on first benchmark
FRESH_DB="${FRESH_DB:-false}"

mkdir -p "$LOG_DIR"

echo "============================================================" | tee -a "$LOG_FILE"
echo "  Simple Overnight Benchmark" | tee -a "$LOG_FILE"
echo "  Started: $(date)" | tee -a "$LOG_FILE"
echo "  Log: $LOG_FILE" | tee -a "$LOG_FILE"
echo "  FRESH_DB=$FRESH_DB" | tee -a "$LOG_FILE"
echo "============================================================" | tee -a "$LOG_FILE"

# Verify app is running
if ! bash scripts/api/api.sh health &>/dev/null; then
  echo "[$(date)] ERROR: App not running" | tee -a "$LOG_FILE"
  exit 1
fi
echo "[$(date)] App is running" | tee -a "$LOG_FILE"

run_solver_benchmark() {
  local name="$1"
  local config="$2"
  local max_seconds="${3:-300}"
  local is_first="${4:-false}"

  echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
  echo "[$(date)] Benchmark: $name" | tee -a "$LOG_FILE"
  echo "[$(date)] Config: $config" | tee -a "$LOG_FILE"
  echo "[$(date)] Max time: ${max_seconds}s" | tee -a "$LOG_FILE"

  # For first benchmark with FRESH_DB=true, recreate database
  if [[ "$is_first" == "true" && "$FRESH_DB" == "true" ]]; then
    echo "[$(date)] Recreating database for clean state..." | tee -a "$LOG_FILE"
    bash scripts/api/api.sh stop >/dev/null 2>&1 || true
    sleep 2
    yes | ./planner.sh delete dev >/dev/null 2>&1
    ./planner.sh start dev >/dev/null 2>&1
    ./planner.sh wait
    echo "[$(date)] Database recreated" | tee -a "$LOG_FILE"
  fi

  # Reset schedule
  echo "[$(date)] Resetting schedule..." | tee -a "$LOG_FILE"
  local reset_status=$(bash scripts/api/api.sh reset 2>&1)
  if [[ ! "$reset_status" =~ "Schedule reset" ]]; then
    echo "[$(date)] WARNING: Reset may have failed: $reset_status" | tee -a "$LOG_FILE"
  fi

  local start=$(date +%s)

  # Start solver
  echo "[$(date)] Starting solver..." | tee -a "$LOG_FILE"
  local solve_status=$(bash scripts/api/api.sh solve 2>&1)
  if [[ ! "$solve_status" =~ "202" ]] && [[ ! "$solve_status" =~ "Accepted" ]]; then
    echo "[$(date)] ERROR: Solver failed to start: $solve_status" | tee -a "$LOG_FILE"
    return 1
  fi

  # Monitor solver progress
  local solver_start=$(date +%s)
  local last_score=""
  local error_count=0
  while true; do
    # Safe jq parsing with fallback
    local STATUS_JSON=$(bash scripts/api/api.sh status 2>/dev/null)
    local SCORE_JSON=$(bash scripts/api/api.sh score 2>/dev/null)

    local STATUS="UNKNOWN"
    local SCORE=""

    # Parse status with error handling
    if [[ -n "$STATUS_JSON" ]]; then
      STATUS=$(echo "$STATUS_JSON" 2>/dev/null | jq -r '.solverStatus // "NOT_SOLVING"' 2>/dev/null || echo "UNKNOWN")
    fi

    # Parse score with error handling - jq -r '.' just outputs the JSON string
    if [[ -n "$SCORE_JSON" ]]; then
      SCORE=$(echo "$SCORE_JSON" 2>/dev/null | jq -r 'if type == "string" then . else .score // . end' 2>/dev/null || echo "")
    fi

    # Track consecutive errors for debugging
    if [[ "$STATUS" == "UNKNOWN" && -z "$SCORE" ]]; then
      ((error_count++))
      if [[ $error_count -gt 3 ]]; then
        echo "[$(date)] WARNING: Multiple API failures, checking solver status..." | tee -a "$LOG_FILE"
        error_count=0
      fi
    else
      error_count=0
    fi

    if [[ "$STATUS" == "NOT_SOLVING" ]]; then
      local end=$(date +%s)
      local solver_duration=$((end - solver_start))
      echo "[$(date)] Solver finished in ${solver_duration}s" | tee -a "$LOG_FILE"
      break
    fi

    local elapsed=$(( $(date +%s) - solver_start ))
    if [[ $elapsed -ge $max_seconds ]]; then
      echo "[$(date)] Timeout (${max_seconds}s), stopping solver..." | tee -a "$LOG_FILE"
      bash scripts/api/api.sh stop >/dev/null 2>&1 || true
      break
    fi

    # Only print when score changes (and score is valid)
    if [[ -n "$SCORE" && "$SCORE" != "$last_score" && "$SCORE" != "null" ]]; then
      echo "[$(date)] Solving... ${elapsed}s | Score: ${SCORE}" | tee -a "$LOG_FILE"
      last_score="$SCORE"
    fi

    sleep 10
  done

  # Capture results
  echo "[$(date)] Capturing results..." | tee -a "$LOG_FILE"
  bash scripts/api/api.sh analysis > "$LOG_DIR/$name-analysis.json" 2>/dev/null || echo "{}" > "$LOG_DIR/$name-analysis.json"
  bash scripts/api/api.sh phase-progression > "$LOG_DIR/$name-progression.json" 2>/dev/null || echo "{}" > "$LOG_DIR/$name-progression.json"

  # Extract metrics
  local final_score=$(jq -r '.score // "N/A"' "$LOG_DIR/$name-analysis.json")
  local hard_score=$(jq -r '.hardScore // "N/A"' "$LOG_DIR/$name-analysis.json")
  local medium_score=$(jq -r '.mediumScore // "N/A"' "$LOG_DIR/$name-analysis.json")
  local soft_score=$(jq -r '.softScore // "N/A"' "$LOG_DIR/$name-analysis.json")

  echo "[$(date)] Final Score: $final_score" | tee -a "$LOG_FILE"
  echo "[$(date)]   Hard: $hard_score | Medium: $medium_score | Soft: $soft_score" | tee -a "$LOG_FILE"

  local end=$(date +%s)
  local total_duration=$((end - start))
  echo "[$(date)] Total time: ${total_duration}s" | tee -a "$LOG_FILE"
}

echo "" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
echo "[$(date)] Starting Benchmark Suite" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"

# Benchmark 1: Default config (120s Phase 3) - FIRST, will recreate DB if FRESH_DB=true
run_solver_benchmark "default-120s" "solverConfig.xml" "180" "true"

# Benchmark 2: Phase 3 60s unimproved
run_solver_benchmark "phase3-60s" "solverConfig-phase3-60s.xml" "120" "false"

# Benchmark 3: Phase 3 120s unimproved
run_solver_benchmark "phase3-120s" "solverConfig-phase3-120s.xml" "240" "false"

# Benchmark 4: Phase 3 180s unimproved
run_solver_benchmark "phase3-180s" "solverConfig-phase3-180s.xml" "300" "false"

# Benchmark 5: Longer run on best config (10 minutes)
run_solver_benchmark "best-extended" "solverConfig-phase3-120s.xml" "600" "false"

# Benchmark 3: Phase 3 120s unimproved
run_solver_benchmark "phase3-120s" "solverConfig-phase3-120s.xml" "240"

# Benchmark 4: Phase 3 180s unimproved
run_solver_benchmark "phase3-180s" "solverConfig-phase3-180s.xml" "300"

# Benchmark 5: Longer run on best config (10 minutes)
run_solver_benchmark "best-extended" "solverConfig-phase3-120s.xml" "600"

echo "" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"
echo "[$(date)] Benchmark Suite Complete" | tee -a "$LOG_FILE"
echo "[$(date)] ========================================" | tee -a "$LOG_FILE"

# Generate summary
echo "" | tee -a "$LOG_FILE"
echo "[$(date)] Summary:" | tee -a "$LOG_FILE"
echo "[$(date)] --------" | tee -a "$LOG_FILE"
for file in "$LOG_DIR"/simple-benchmark-*-analysis.json; do
  if [[ -f "$file" ]]; then
    name=$(basename "$file" -analysis.json)
    score=$(jq -r '.score // "N/A"' "$file")
    hard=$(jq -r '.hardScore // "N/A"' "$file")
    medium=$(jq -r '.mediumScore // "N/A"' "$file")
    echo "  $name: $score (H:$hard M:$medium)" | tee -a "$LOG_FILE"
  fi
done

echo "" | tee -a "$LOG_FILE"
echo "[$(date)] Benchmark suite finished at $(date)" | tee -a "$LOG_FILE"
echo "[$(date)] Logs saved to $LOG_DIR" | tee -a "$LOG_FILE"
echo "============================================================" | tee -a "$LOG_FILE"
