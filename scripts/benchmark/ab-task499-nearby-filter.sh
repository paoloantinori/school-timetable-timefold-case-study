#!/usr/bin/env bash
# A/B TASK-499 (nearby template filter prototype): CONTROL unfiltered moves (flag OFF)
# vs TREATMENT grade-3 timeslot changes filtered (flag ON, keep-probability 0.20).
#
# Both arms: source=golden DEFAULT, staged cycle, multi-start DEFAULT ON, MT-LS DEFAULT
# ON. The gate: hard 0 both, KPI in band, WALL of the LS stages and the move evaluation
# speed from the solver logs (the filter should buy more useful evaluations per second).
#
# Usage: background and poll <OUT>/ab-done. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task499-results/ab-2026-08-15}"
SUFFIX="${RUN_SUFFIX:-run1}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "TASK-499 NEARBY TEMPLATE FILTER A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

say "=== ARM control (flag OFF, full move generation) ==="
PROFILE=task499-off OUT="$OUT/ab-off-$SUFFIX" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "=== ARM treatment (nearby template filter armed) ==="
PROFILE=task499-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="-Dplanner.ls.nearby-template-filter.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-499 A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
