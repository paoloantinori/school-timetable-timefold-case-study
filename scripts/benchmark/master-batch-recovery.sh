#!/usr/bin/env bash
# Recovery batch (after the 2026-08-10 ~17:02 reboot killed the chain mid-449-input-ON).
#
# The laptop rebooted (caffeinate can't prevent a reboot, only sleep), so master-batch-5 (449
# input) died mid-ON-arm (the OFF arm finished 0hard on disk) and master-batch-6 (470) never
# started. This batch resumes exactly those 2 remaining jobs, sequentially (single DB actor):
#   1. 449 input ON arm re-run (the interrupted treatment; OFF control is already 0hard on disk
#      in task449-results-input/ab-off-run1).
#   2. TASK-470 theory-spread live A/B (both arms fresh - it never started).
# Both caffeinate-wrapped (this driver is launched under caffeinate -is). If the laptop reboots
# again, re-run this script: each job writes its own DONE marker, so partial progress survives.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/recovery-2026-08-10}"
mkdir -p "$OUT"
LOG="$OUT/recovery.log"
DONE_MARKER="$OUT/recovery-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "RECOVERY BATCH START (host=$(uname -srm), uptime=$(uptime | tr -s ' '))"

# --- Job 1: 449 input ON arm re-run (OFF control already 0hard on disk). ---
say "=== 449 input ON arm re-run (source=input, 449 ON, POLL_CAP=240) ==="
PROFILE=task449-on-input-rerun POLL_CAP=240 OUT="claudedocs/spalmone-2026-27/task449-results-input/ab-on-rerun" \
  PLANNER_JAVA_OPTS="-Dplanner.pre-assign.mono-room-tag.enabled=true -Dplanner.pre-assign.constrained-teacher.source=input" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "449 input ON re-run DONE: $(cat claudedocs/spalmone-2026-27/task449-results-input/ab-on-rerun/DONE 2>/dev/null || echo '(no marker)')"

# --- Job 2: TASK-470 theory-spread live A/B (both arms, never started). ---
say "=== TASK-470 theory-spread A/B (source=golden, POLL_CAP=240) ==="
export POLL_CAP=240
TREATMENT_FLAG="-Dplanner.construction.theory-spread.enabled=true" SRC_LABEL=golden \
  OUT="claudedocs/spalmone-2026-27/task470-results" \
  bash ./scripts/benchmark/ab-task449-mono-room.sh >>"$LOG" 2>&1 || true
say "470 A/B done. OFF: $(cat claudedocs/spalmone-2026-27/task470-results/ab-off-run1/DONE 2>/dev/null || echo n/a) | ON: $(cat claudedocs/spalmone-2026-27/task470-results/ab-on-run1/DONE 2>/dev/null || echo n/a)"

say "RECOVERY BATCH COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
