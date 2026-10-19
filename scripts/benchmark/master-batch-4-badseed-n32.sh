#!/usr/bin/env bash
# Master batch 4: bad-seed N=32 extension (seeds 17-32, NEW constructions).
#
# Extends the N=16 (master-batch-1, p(0-hard)=1.0 [0.82,1.0]) with 16 NEW GRASP constructions
# (seeds 17-32 via SEED_OFFSET) so the per-construction p(0-hard) estimate samples the
# construction SPACE, not just the LS space of fixed seeds. Pools to N=32 for a tighter m-13
# CI AND surfaces the probabilistic H17 basin if any of the new constructions hits it (the
# N=16 didn't, but n=16 clean doesn't prove H17 is gone - it's LS-probabilistic).
#
# Chains AFTER master-batch-3 (stability series). Cap 18h.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/master-batch-4-2026-08-09}"
MASTER3_DONE="claudedocs/spalmone-2026-27/master-batch-3-2026-08-09/master3-done"
EXT_DIR="claudedocs/spalmone-2026-27/task480-results/badseed-frequency-laptop-2026-08-09-seeds17-32"
mkdir -p "$OUT"
LOG="$OUT/master4.log"
DONE_MARKER="$OUT/master4-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 4 START (bad-seed N=32 ext, seeds 17-32)"
say "waiting for master-batch-3 ($MASTER3_DONE) - cap 18h"
wait_secs=0; cap=$((18 * 3600))
while [ ! -f "$MASTER3_DONE" ]; do
  sleep 60; wait_secs=$((wait_secs + 60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for stability (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then say "ABORT: stability not done after 18h"; echo "ABORT" > "$DONE_MARKER"; exit 1; fi
done
say "stability done. Starting bad-seed seeds 17-32 (N=32 extension, no baseline)."
SEED_OFFSET=16 RESULTS_DIR="$EXT_DIR" ./scripts/benchmark/multistart-ab.sh 16 multistart >>"$LOG" 2>&1 \
  || say "WARN: bad-seed ext non-zero (partial data in $EXT_DIR)"
say "bad-seed ext done. SUMMARY: $(test -f "$EXT_DIR/SUMMARY.txt" && echo present || echo MISSING)"
say "MASTER BATCH 4 COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
