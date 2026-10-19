#!/usr/bin/env bash
# Master batch 5: TASK-449 A/B on source=INPUT (the room-tier saturation regime 449 targets).
#
# The golden A/B (ab-task449-mono-room.sh default) tests 449 in the GOOD basin, where the
# room-tier is not saturated (so 449 ON just over-constrains -> the -19hard signal). This
# input A/B tests 449 in its TARGET regime: source=input is where the room-tier saturation
# (TASK-486 M-TC context) lives. So this is the more relevant test of whether 449's
# pre-booking helps the bad basin it was designed for. Uses ab-task449-mono-room.sh with
# EXTRA_OPTS=-Dplanner.pre-assign.constrained-teacher.source=input (both arms) + POLL_CAP=240
# (the ON arm may need 3 attempts like the golden ON did).
#
# Chains AFTER master-batch-4 (N=32 ext). Cap 22h.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/master-batch-5-2026-08-09}"
MASTER4_DONE="claudedocs/spalmone-2026-27/master-batch-4-2026-08-09/master4-done"
mkdir -p "$OUT"
LOG="$OUT/master5.log"
DONE_MARKER="$OUT/master5-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 5 START (449 A/B source=input)"
say "waiting for master-batch-4 ($MASTER4_DONE) - cap 22h"
wait_secs=0; cap=$((22 * 3600))
while [ ! -f "$MASTER4_DONE" ]; do
  sleep 60; wait_secs=$((wait_secs + 60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for N=32 ext (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then say "ABORT: N=32 ext not done after 22h"; echo "ABORT" > "$DONE_MARKER"; exit 1; fi
done
say "N=32 ext done. Starting 449 A/B source=input (POLL_CAP=240 for the ON arm)."
export POLL_CAP=240
EXTRA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input" SRC_LABEL=input \
  OUT="claudedocs/spalmone-2026-27/task449-results-input" \
  bash ./scripts/benchmark/ab-task449-mono-room.sh >>"$LOG" 2>&1 \
  || say "WARN: 449 input A/B non-zero (see task449-results-input/)"
say "449 input A/B done. OFF: $(cat claudedocs/spalmone-2026-27/task449-results-input/ab-off-run1/DONE 2>/dev/null || echo n/a) | ON: $(cat claudedocs/spalmone-2026-27/task449-results-input/ab-on-run1/DONE 2>/dev/null || echo n/a)"
say "MASTER BATCH 5 COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
