#!/usr/bin/env bash
# Benchmark: Pillar Moves Experiment
# Compares baseline (change moves only) vs pillar moves configuration
#
# Usage:
#   ./scripts/benchmark/benchmark-pillar-moves.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Benchmark configuration
SOLVE_TIME=120  # 2 minutes per run (better for LS phases)
RUNS=2          # Quick comparison

log() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"
}

log_step() {
    echo -e "${GREEN}>>>${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}WARNING:${NC} $*"
}

# Check app is running
check_app() {
    if ! ./scripts/api/api.sh health &>/dev/null; then
        log "App not running. Start with: ./planner.sh start <profile>"
        exit 1
    fi
}

# Reset and solve
run_solve() {
    local label="$1"
    local time="$2"

    log_step "$label: Resetting schedule..."
    ./scripts/api/api.sh reset >/dev/null

    log_step "$label: Starting solver (${time}s)..."
    local start_time=$(date +%s)

    ./scripts/api/api.sh solve >/dev/null

    # Wait for solve duration
    sleep "$time"

    # Stop solver
    log_step "$label: Stopping solver..."
    ./scripts/api/api.sh stop >/dev/null 2>&1 || true

    # Wait a moment for finalization
    sleep 2

    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    # Collect metrics
    log_step "$label: Collecting metrics..."
    local score=$(./scripts/api/api.sh score 2>/dev/null)
    # Fields of the light /api/schedule/status payload `api.sh status` now returns
    # (TASK-419): the cumulative CH/LS second counters live only on the full
    # GET /api/schedule and are no longer read here.
    local phase_info=$(./scripts/api/api.sh -j status 2>/dev/null | jq -r '{
        solverStatus,
        currentPhase,
        currentPhaseName,
        stagedStage
    }')
    local analysis=$(./scripts/api/api.sh analysis 2>/dev/null | jq -r '{
        score: .score,
        constraints: [.constraints[] | select(.constraint | test("H1:|S7:")) | {constraint, score, hard, soft}]
    }')

    echo ""
    log "=== $label Results (elapsed: ${elapsed}s) ==="
    echo "Score: $score"
    echo "Phase Info: $phase_info"
    echo "Key Constraints (H1/S7):"
    echo "$analysis" | jq -r '.constraints[] | "  \(.constraint): \(.score)"'
    echo ""
}

# Main benchmark
main() {
    log "=== Pillar Moves Benchmark ==="
    log "Branch: $(git branch --show-current)"
    log "Commit: $(git log -1 --format='%h %s')"
    log ""

    check_app

    for i in $(seq 1 $RUNS); do
        log "=== Run $i of $RUNS ==="
        run_solve "Pillar Moves (Run $i)" "$SOLVE_TIME"
    done

    log "=== Benchmark Complete ==="
}

cd "$PROJECT_DIR"
main "$@"
