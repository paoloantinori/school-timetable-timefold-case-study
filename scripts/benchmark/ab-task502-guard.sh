#!/usr/bin/env bash
# A/B round 4-preview (TASK-502 follow-up forma a): CONTROL = chain ON + guard OFF
# (identical to the round-3 treatment), TREATMENT = chain ON + guard ON. The chain
# (deriver + enumerator) stays ON in BOTH arms: this isolates the guard alone.
# Usage: background and poll <OUT>/ab-done. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task467-results/ab-chain-2026-08-16}"
SUFFIX="${RUN_SUFFIX:-run4}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

CHAIN_FLAGS="-Dplanner.pre-assign.labrotation.derive-from-golden.enabled=true -Dplanner.pre-assign.rotation-enumerator.enabled=true"

say "TASK-502 GUARD A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

say "=== ARM control (chain ON, guard OFF = round-3 treatment) ==="
PROFILE=task467g-off OUT="$OUT/ab-off-$SUFFIX" \
  PLANNER_JAVA_OPTS="$CHAIN_FLAGS" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "=== ARM treatment (chain ON + teacher-budget guard ON) ==="
PROFILE=task467g-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="$CHAIN_FLAGS -Dplanner.pre-assign.teacher-budget.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-502 GUARD A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
