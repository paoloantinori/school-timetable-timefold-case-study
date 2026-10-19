#!/usr/bin/env bash
# A/B TASK-364+467 CHAIN (mandato 16/08 sera): CONTROL both flags OFF (today's
# behaviour: Phase 1 short-circuited, single candidate) vs TREATMENT both ON
# (deriver seeds golden-validated LabRotations, enumerator ranks multi-candidates,
# first feasible wins). Both arms: source=input DEFAULT, maxAttempts=5 DEFAULT,
# everything else ratified ON. Banner check for BOTH flags in BOTH arms happens
# after the run (grep in app.log).
# Usage: background and poll <OUT>/ab-done. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task467-results/ab-chain-2026-08-16}"
SUFFIX="${RUN_SUFFIX:-run1}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "TASK-467 CHAIN A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

say "=== ARM control (deriver OFF + enumerator OFF = production today) ==="
PROFILE=task467chain-off OUT="$OUT/ab-off-$SUFFIX" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "=== ARM treatment (deriver ON + enumerator ON: full chain) ==="
PROFILE=task467chain-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="-Dplanner.pre-assign.labrotation.derive-from-golden.enabled=true -Dplanner.pre-assign.rotation-enumerator.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-467 CHAIN A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
