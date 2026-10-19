#!/usr/bin/env bash
# Master batch 2 (Fedora-offline window): clean re-run of the TASK-449 ON arm.
#
# The first 449 A/B (ab-task449-mono-room.sh) left the ON arm INCONCLUSIVE: baseline-golden.sh's
# 150-min poll cap cut attempt 3 mid-B-TIMESLOT and the capture failed (VERDICT-ab-2026-08-09.md).
# The best COMPLETED attempt was -19hard (not 0hard, vs OFF's 0hard) - a suggestive-negative
# signal that 449 ON degrades the basin. This re-run gives the ON arm a longer cap (POLL_CAP=240,
# enough for 3 attempts) so the verdict is clean: does 449 ON reach 0hard (just slower) or stay
# hard-negative (confirms it harms)?
#
# Chains AFTER master-batch-fedora-offline.sh (the bad-seed N=16): waits for its master-done
# marker so there is never a second solve while bad-seed runs (single DB actor). Cap the wait
# at 13h (bad-seed is ~8-12h; if it hangs, abort).
#
# Backgroundable; wrap in `caffeinate -is`. Poll <OUT>/master2-done + <OUT>/master2.log.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/master-batch-2-2026-08-09}"
MASTER1_DONE="claudedocs/spalmone-2026-27/master-batch-2026-08-09/master-done"
RERUN_OUT="claudedocs/spalmone-2026-27/task449-results/ab-on-rerun"
mkdir -p "$OUT"
LOG="$OUT/master2.log"
DONE_MARKER="$OUT/master2-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 2 START (449 ON clean re-run)"

# Gate: wait for master batch 1 (bad-seed N=16) to finish. Cap 13h.
say "waiting for master-batch-1 ($MASTER1_DONE) - cap 13h"
wait_secs=0; cap=$((13 * 3600))
while [ ! -f "$MASTER1_DONE" ]; do
  sleep 60; wait_secs=$((wait_secs + 60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for bad-seed (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then
    say "ABORT: bad-seed not done after 13h cap; aborting 449 ON re-run"
    echo "ABORT waited 13h for bad-seed" > "$DONE_MARKER"; exit 1
  fi
done
say "bad-seed done. Starting 449 ON clean re-run (POLL_CAP=240)."

# 449 ON re-run: longer cap so 3 attempts can finish + capture cleanly.
PROFILE=task449-on-rerun OUT="$RERUN_OUT" POLL_CAP=240 \
  PLANNER_JAVA_OPTS="-Dplanner.pre-assign.mono-room-tag.enabled=true" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 \
  || say "WARN: 449 ON re-run exited non-zero (see $RERUN_OUT)"
say "449 ON re-run DONE: $(cat "$RERUN_OUT/DONE" 2>/dev/null || echo '(no marker)')"

say "MASTER BATCH 2 COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
