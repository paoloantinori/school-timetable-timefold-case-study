#!/usr/bin/env bash
# A/B TASK-497 (M20 pool-cap prototype, forma b): CONTROL ratio-cap (TASK-461, production
# behaviour, flag OFF) vs TREATMENT pool-cap exact targets (flag ON).
#
# Both arms: source=golden DEFAULT, staged cycle, multi-start DEFAULT ON, MT-LS DEFAULT
# ON (the ratified production config; the ONLY difference between arms is the -D). The
# ratio-cap stays active in BOTH arms (it is capSupply's fallback); the treatment arm
# overrides it per-day with the published exact targets.
#
# Usage: background it and poll <OUT>/ab-done. Wrap in caffeinate -is if the host sleeps.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task497-results/ab-2026-08-14}"
# RUN_SUFFIX parametrizes the arm dirs (run1 = default, a paired run2 sets RUN_SUFFIX=run2)
# so a tiebreak re-measure never overwrites the first pair's raw.
SUFFIX="${RUN_SUFFIX:-run1}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "TASK-497 M20 POOL-CAP A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

# ARM 1: CONTROL - no -D (flag OFF = ratio-cap TASK-461, production behaviour)
say "=== ARM control (flag OFF, ratio-cap live = production) ==="
PROFILE=task497-off OUT="$OUT/ab-off-$SUFFIX" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

# ARM 2: TREATMENT - pool-cap exact targets armed
say "=== ARM treatment (pool-cap armed) ==="
PROFILE=task497-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="-Dplanner.constraints.m20-cap-pool.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-497 A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
