#!/usr/bin/env bash
# A/B TASK-498 (skip-doomed-room prototype): CONTROL full room stage (flag OFF) vs
# TREATMENT doomed attempts skip the room-refinement LS (flag ON).
#
# Both arms: source=golden DEFAULT, staged cycle, multi-start DEFAULT ON, MT-LS DEFAULT
# ON. The lever only fires on multi-start attempts that are doomed at the post-RELEASE
# boundary, so on the deterministic golden good basin it is expected INERT (safety gate);
# the wall saving shows on bad basins (see the VERDICT for the forced-bad measurement).
#
# Usage: background and poll <OUT>/ab-done. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task498-results/ab-2026-08-15}"
SUFFIX="${RUN_SUFFIX:-run1}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-$SUFFIX"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "TASK-498 SKIP-DOOMED-ROOM A/B START (HEAD=$(git rev-parse --short HEAD), suffix=$SUFFIX)"

say "=== ARM control (flag OFF, full room stage = production) ==="
PROFILE=task498-off OUT="$OUT/ab-off-$SUFFIX" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM control DONE: $(cat "$OUT/ab-off-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "=== ARM treatment (flag ON: doomed attempts skip the room-refinement LS) ==="
PROFILE=task498-on OUT="$OUT/ab-on-$SUFFIX" \
  PLANNER_JAVA_OPTS="-Dplanner.construction.skip-doomed-room.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM treatment DONE: $(cat "$OUT/ab-on-$SUFFIX/DONE" 2>/dev/null || echo '(no marker)')"

say "TASK-498 A/B COMPLETE (suffix=$SUFFIX)"
echo "DONE $(ts)" > "$DONE_MARKER"
