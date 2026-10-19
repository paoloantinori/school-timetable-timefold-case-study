#!/usr/bin/env bash
# TASK-542 prediction-verification bench runner: resume a staged cycle from a checkpoint,
# let the deterministic repair passes run (seconds), read the gate numbers the capture hook
# wrote into the POST_EXACT_FINISHER checkpoint (the last pre-room boundary), then stop the
# solve. This is the minutes-scale loop the -642 bench iterates on: NO full pipeline.
#
# Usage: ./scripts/benchmark/resume-bench.sh <checkpoint.json> [timeout-seconds]
# Output: the gate line "<tierHard> <boxed>" plus the repair-chain checkpoints' numbers.
set -euo pipefail

CKPT="${1:?usage: resume-bench.sh <checkpoint.json> [timeout]}"
TIMEOUT="${2:-900}"
DIR="${PLANNER_CHECKPOINT_DIR:-staged-checkpoints}"
EXACT="$DIR/attempt1/POST_EXACT_FINISHER.json"

if [ ! -f "$CKPT" ]; then
  echo "ERROR: checkpoint not found: $CKPT" >&2
  exit 1
fi

# baseline: the numbers the input checkpoint carries
echo "INPUT  $(python3 -c "
import json,sys
c=json.load(open('$CKPT'))
print(f\"{c['cursor']['boundary']} tier={c['timeslotTierHard']} boxed={c['boxed']} score={c['score']}\")")"

rm -f "$EXACT"
./scripts/api/api.sh post "/api/schedule/solve-staged-resume?path=$CKPT" 2>/dev/null || {
    echo "ERROR: resume call failed" >&2; exit 1; }

# wait for the repairs to finish (the checkpoint appears), then stop before the slow room LS
elapsed=0
while [ ! -f "$EXACT" ]; do
  sleep 5; elapsed=$((elapsed+5))
  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    echo "TIMEOUT waiting for POST_EXACT_FINISHER after ${TIMEOUT}s" >&2
    ./scripts/api/api.sh stop >/dev/null 2>&1 || true
    exit 2
  fi
done
sleep 2
./scripts/api/api.sh stop >/dev/null 2>&1 || true

echo "OUTPUT $(python3 -c "
import json
c=json.load(open('$EXACT'))
print(f\"POST_EXACT_FINISHER tier={c['timeslotTierHard']} boxed={c['boxed']} score={c['score']}\")")"
