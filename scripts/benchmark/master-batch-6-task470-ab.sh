#!/usr/bin/env bash
# Master batch 6: TASK-470 theory-spread A/B (live, the missing test).
#
# TASK-470 verdict (2026-08-01) measured the theory-spread prototype only at CONSTRUCTION-END
# (flat: 1669->1675 M-TR residual) and recommended "don't adopt." A LIVE staged A/B (ON vs OFF,
# full schedule) was never run - same gap TASK-449 had before its A/B ruled it out. This closes
# 470 the same way: does the construction dedup help or hurt the FINAL hard/medium? (449, a
# pre-assignment pin, degraded hard via over-constrain; 470 is a construction dedup - different
# mechanism, so the outcome is not predictable from 449.) Reuses ab-task449-mono-room.sh with
# TREATMENT_FLAG=-Dplanner.construction.theory-spread.enabled, source=golden, POLL_CAP=240.
#
# Chains AFTER master-batch-5 (449 input A/B). Cap 24h.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/master-batch-6-2026-08-09}"
MASTER5_DONE="claudedocs/spalmone-2026-27/master-batch-5-2026-08-09/master5-done"
mkdir -p "$OUT"
LOG="$OUT/master6.log"
DONE_MARKER="$OUT/master6-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 6 START (TASK-470 theory-spread live A/B)"
say "waiting for master-batch-5 ($MASTER5_DONE) - cap 24h"
wait_secs=0; cap=$((24 * 3600))
while [ ! -f "$MASTER5_DONE" ]; do
  sleep 60; wait_secs=$((wait_secs + 60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for 449 input (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then say "ABORT: 449 input not done after 24h"; echo "ABORT" > "$DONE_MARKER"; exit 1; fi
done
say "449 input done. Starting TASK-470 theory-spread A/B (source=golden, POLL_CAP=240)."
export POLL_CAP=240
TREATMENT_FLAG="-Dplanner.construction.theory-spread.enabled=true" SRC_LABEL=golden \
  OUT="claudedocs/spalmone-2026-27/task470-results" \
  bash ./scripts/benchmark/ab-task449-mono-room.sh >>"$LOG" 2>&1 \
  || say "WARN: 470 A/B non-zero (see task470-results/)"
say "470 A/B done. OFF: $(cat claudedocs/spalmone-2026-27/task470-results/ab-off-run1/DONE 2>/dev/null || echo n/a) | ON: $(cat claudedocs/spalmone-2026-27/task470-results/ab-on-run1/DONE 2>/dev/null || echo n/a)"
say "MASTER BATCH 6 COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
