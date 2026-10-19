#!/usr/bin/env bash
# A/B run6 (TASK-516): CONTROL = chain ON + budget-reservation OFF (identical to
# run4/run5, the -42 basin), TREATMENT = chain ON + budget-reservation ON. The
# chain (deriver + enumerator) stays ON in BOTH arms: this isolates the
# reservation alone. Gate (TASK-516 AC#4): hard 0 in the treatment with the 20
# boxed placed, KPI in band, wall comparable.
# Usage: background and poll <OUT>/ab-done-run6. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task467-results/ab-chain-2026-08-16}"
SUFFIX="${RUN_SUFFIX:-run6}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

CHAIN_FLAGS="-Dplanner.pre-assign.labrotation.derive-from-golden.enabled=true -Dplanner.pre-assign.rotation-enumerator.enabled=true"

say "TASK-516 RESERVATION A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

say "=== ARM control (chain ON, reservation OFF = run4 shape) ==="
PROFILE=task516-off OUT="$OUT/ab-off-$SUFFIX" \
  PLANNER_JAVA_OPTS="$CHAIN_FLAGS" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "=== ARM treatment (chain ON + budget-reservation ON) ==="
PROFILE=task516-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="$CHAIN_FLAGS -Dplanner.pre-assign.budget-reservation.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-516 RESERVATION A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
