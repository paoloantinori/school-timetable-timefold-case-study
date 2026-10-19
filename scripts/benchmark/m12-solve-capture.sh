#!/usr/bin/env bash
# m-12 live A/B: one staged solve on HEAD (424+425) + capture. Args: <label> <outdir>
set -uo pipefail
LABEL="${1:?label required}"
OUTDIR="${2:?outdir required}"
LOG="/tmp/m12-${LABEL}.log"
echo "[$(date '+%H:%M:%S')] m12 solve-capture START label=$LABEL outdir=$OUTDIR" | tee "$LOG"

# caffeinate: keep laptop awake for the whole solve (idle+system, 8h)
caffeinate -is -t 28800 &
CAF=$!
trap 'kill $CAF 2>/dev/null' EXIT

echo "[$(date '+%H:%M:%S')] starting staged solve..." | tee -a "$LOG"
./scripts/api/api.sh solve-staged >> "$LOG" 2>&1 || echo "[$(date '+%H:%M:%S')] solve-staged returned non-zero (may be OK, async)" | tee -a "$LOG"

# Poll until done: solverStatus NOT_SOLVING AND no stagedStage line (VERDICT-train2 done-condition)
echo "[$(date '+%H:%M:%S')] polling for NOT_SOLVING..." | tee -a "$LOG"
for i in $(seq 1 120); do   # up to ~120*75s = 2.5h cap
  sleep 75
  STATUS=$(./scripts/api/api.sh status 2>/dev/null)
  if echo "$STATUS" | grep -q "^solverStatus: NOT_SOLVING" && ! echo "$STATUS" | grep -q "stagedStage:"; then
    echo "[$(date '+%H:%M:%S')] DONE (NOT_SOLVING, no stagedStage) after ~$((i*75))s" | tee -a "$LOG"
    break
  fi
  ST=$(echo "$STATUS" | grep -E "^stagedStage:" | head -1)
  [ $((i % 4)) -eq 0 ] && echo "[$(date '+%H:%M:%S')] poll #$i ($((i*75))s) $ST" | tee -a "$LOG"
done

echo "[$(date '+%H:%M:%S')] capture-run -> $OUTDIR" | tee -a "$LOG"
./scripts/benchmark/capture-run.sh "$OUTDIR" >> "$LOG" 2>&1
CREPLY=$?
echo "[$(date '+%H:%M:%S')] capture-run EXIT=$CREPLY" | tee -a "$LOG"
echo "[$(date '+%H:%M:%S')] m12 solve-capture END label=$LABEL" | tee -a "$LOG"
