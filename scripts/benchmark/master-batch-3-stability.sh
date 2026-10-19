#!/usr/bin/env bash
# Master batch 3 (Fedora-offline window): good-seed stability series (source=golden x3).
#
# The bad-seed N=16 (master-batch-1) characterized source=input variance. This characterizes
# source=GOOD-SEED variance: 3 baseline-golden.sh runs within-session, capture hard/medium/KPI
# each. The baseline (single run) took 2 multi-start attempts; this measures whether that is
# typical or a fluke, and the run-to-run medium/KPI spread (NON_REPRODUCIBLE LS). laptop-RUNBOOK:
# "stability matters as much as the minimum." Fed the campaign's confidence in the ratified
# golden-path numbers (hard=0, ~75.8% recurrence, ~86% full-day).
#
# Chains AFTER master-batch-2 (449 ON re-run): waits for its master2-done. Cap 16h.
#
# Backgroundable; wrap in `caffeinate -is`. Poll <OUT>/master3-done + <OUT>/master3.log.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/master-batch-3-2026-08-09}"
MASTER2_DONE="claudedocs/spalmone-2026-27/master-batch-2-2026-08-09/master2-done"
STAB_BASE="claudedocs/spalmone-2026-27/stability-golden-2026-08-09"
mkdir -p "$OUT" "$STAB_BASE"
LOG="$OUT/master3.log"
DONE_MARKER="$OUT/master3-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 3 START (good-seed stability x3)"

# Gate: wait for master-batch-2 (449 ON re-run). Cap 16h.
say "waiting for master-batch-2 ($MASTER2_DONE) - cap 16h"
wait_secs=0; cap=$((16 * 3600))
while [ ! -f "$MASTER2_DONE" ]; do
  sleep 60; wait_secs=$((wait_secs + 60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for 449 ON re-run (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then
    say "ABORT: master-batch-2 not done after 16h cap; aborting stability series"
    echo "ABORT waited 16h" > "$DONE_MARKER"; exit 1
  fi
done
say "449 ON re-run done. Starting good-seed stability series (x3)."

for n in 1 2 3; do
  say "=== stability run $n/3 (source=golden) ==="
  PROFILE="stab-$n" OUT="$STAB_BASE/run-$n" \
    bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
  say "stability run $n DONE: $(cat "$STAB_BASE/run-$n/DONE" 2>/dev/null || echo '(no marker)')"
done

say "MASTER BATCH 3 COMPLETE - confrontare run-1/2/3 (hard, medium, ricorrenza, full-day, wall)"
echo "DONE $(ts)" > "$DONE_MARKER"
