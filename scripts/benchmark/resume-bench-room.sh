#!/usr/bin/env bash
# TASK-550 room-tail finisher bench: resume a staged cycle from a PRE-ROOM checkpoint
# (POST_EXACT_FINISHER or later-but-before-B-ROOM) and let it run THROUGH the room stage,
# so the post-B-ROOM deterministic passes (PHR, room-tail finisher) execute on the banco.
# Sister of resume-bench.sh, which stops before the slow room LS; this one pays it.
#
# Usage: ./scripts/benchmark/resume-bench-room.sh <checkpoint.json> [timeout-seconds]
# Requires the app launched with -Dplanner.checkpoint.capture.enabled=true
# and -Dplanner.construction.room-tail-finisher.enabled=true (and PHR's flag if wanted).
set -euo pipefail

CKPT="${1:?usage: resume-bench-room.sh <checkpoint.json> [timeout]}"
TIMEOUT="${2:-3600}"
DIR="${PLANNER_CHECKPOINT_DIR:-staged-checkpoints}"
BROOM="$DIR/attempt1/POST_B_ROOM.json"

if [ ! -f "$CKPT" ]; then
  echo "ERROR: checkpoint not found: $CKPT" >&2
  exit 1
fi

echo "INPUT  $(python3 -c "
import json
c=json.load(open('$CKPT'))
print(f\"{c['cursor']['boundary']} tier={c['timeslotTierHard']} boxed={c['boxed']} score={c['score']}\")")"

rm -f "$BROOM"
./scripts/api/api.sh post "/api/schedule/solve-staged-resume?path=$CKPT" 2>/dev/null || {
    echo "ERROR: resume call failed" >&2; exit 1; }

elapsed=0
while [ ! -f "$BROOM" ]; do
  sleep 10; elapsed=$((elapsed+10))
  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    echo "TIMEOUT waiting for POST_B_ROOM after ${TIMEOUT}s" >&2
    ./scripts/api/api.sh stop >/dev/null 2>&1 || true
    exit 2
  fi
done
sleep 2

echo "OUTPUT $(python3 -c "
import json
c=json.load(open('$BROOM'))
print(f\"POST_B_ROOM tier={c['timeslotTierHard']} boxed={c['boxed']} score={c['score']}\")")"
