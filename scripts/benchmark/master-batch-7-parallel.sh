#!/usr/bin/env bash
# Master batch 7: parallel multi-start spike (after the m-13 validate solve, for clean CPU timing).
#
# Waits for verify-m3435-m13-2026-08-11/DONE (the m-13 operational-validation solve on the MAIN
# instance), then runs parallel-multistart.sh N=3. Waiting ensures the MAIN solve is done so the
# 3 parallel solves get uncontended CPU (else their timing is skewed and the speedup measurement
# is invalid). Cap 8h on the wait.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT" || exit 1
OUT="claudedocs/spalmone-2026-27/master-batch-7-2026-08-11"
VALIDATE_DONE="claudedocs/spalmone-2026-27/verify-m3435-m13-2026-08-11/DONE"
mkdir -p "$OUT"
LOG="$OUT/master7.log"; DONE_MARKER="$OUT/master7-done"
ts(){ date '+%Y-%m-%d %H:%M:%S'; }; say(){ echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MASTER BATCH 7 START (parallel multi-start spike N=3)"
say "waiting for m-13 validate ($VALIDATE_DONE) - cap 8h (clean CPU for timing)"
wait_secs=0; cap=$((8*3600))
while ! grep -q "DONE" "$VALIDATE_DONE" 2>/dev/null; do
  sleep 60; wait_secs=$((wait_secs+60))
  if [ $((wait_secs % 1800)) -eq 0 ]; then say "  still waiting for m-13 validate (~$((wait_secs/60)) min)"; fi
  if [ "$wait_secs" -ge "$cap" ]; then say "ABORT: m-13 validate not done after 8h"; echo ABORT > "$DONE_MARKER"; exit 1; fi
done
say "m-13 validate done. MAIN CPU free. Starting parallel multi-start spike N=3."
bash ./scripts/benchmark/parallel-multistart.sh 3 >>"$LOG" 2>&1 || say "WARN: parallel spike non-zero (see parallel-multistart-*/driver.log)"
say "MASTER BATCH 7 COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
