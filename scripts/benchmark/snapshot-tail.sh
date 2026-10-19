#!/usr/bin/env bash
# snapshot-tail.sh - the snapshot-first iteration loop, one command (direttiva
# ITERAZIONE SU SNAPSHOT, maintainer 6/09): harvest a boundary checkpoint from the
# laptop (or take a local file), warm-start the LOCAL twin profile from it, wait
# for the tail, and dump score + hard-family analysis.
#
# Usage:
#   snapshot-tail.sh <laptop-relative-checkpoint-path>   # e.g. staged-checkpoints-v123/attempt2/POST_B_ROOM.json
#   snapshot-tail.sh -l <local-checkpoint-file>       # already-local file
#
# Prerequisites: the local app running on the TWIN profile (same jar as the
# snapshot's origin, same seed: id parity - verified 6/09), e.g.:
#   PLANNER_JAVA_PREFLIGHT_JAR_OK=1 PROFILE=v123-bridge2 \
#     PLANNER_JAVA_OPTS="-Dplanner.construction.surplus-harvest.enabled=true" \
#     ./scripts/core/prod-start-and-wait.sh 180
# Optional env: SURPLUS_TRACE / SURPLUS_TRACE_ALL (the pass's tracer).
set -euo pipefail

REMOTE_PATH=""
LOCAL_FILE=""
if [[ "${1:-}" == "-l" ]]; then
    LOCAL_FILE="${2:?usage: snapshot-tail.sh -l <file>}"
else
    REMOTE_PATH="${1:?usage: snapshot-tail.sh <laptop-relative-path> | -l <local-file>}"
fi
OUTDIR="${OUTDIR:-/tmp/snapshot-tails}"
mkdir -p "$OUTDIR"
STAMP="$(basename "${LOCAL_FILE:-$REMOTE_PATH}" .json)-$(date +%H%M%S)"
WORK="$OUTDIR/$STAMP"
mkdir -p "$WORK"

if [[ -n "$REMOTE_PATH" ]]; then
    echo "[tail] harvesting laptop:$REMOTE_PATH"
    scp -q "laptop:$REMOTE_PATH" "$WORK/checkpoint.json"
else
    cp "$LOCAL_FILE" "$WORK/checkpoint.json"
fi

echo "[tail] score declared by the snapshot:"
python3 - "$WORK/checkpoint.json" << 'PY'
import json, sys
with open(sys.argv[1]) as f:
    chk = json.load(f)
print(f"  score={chk.get('score')} boxed={chk.get('boxed')} tier={chk.get('timeslotTierHard')}")
PY

echo "[tail] warm-starting the local tail (resume)"
./scripts/api/api.sh resume-staged "$WORK/checkpoint.json" | tee "$WORK/resume.out"

echo "[tail] waiting for delivery (stagedStage null)"
for i in $(seq 1 90); do
    sleep 10
    STAGE="$(./scripts/api/api.sh status 2>/dev/null | grep stagedStage || true)"
    if ! grep -q "stagedStage: [A-Z]" <<<"$STAGE"; then break; fi
done

echo "[tail] delivered score:"
./scripts/api/api.sh score | tee "$WORK/score.txt"
echo "[tail] hard-family analysis:"
./scripts/api/api.sh -t 240 analysis > "$WORK/analysis.json"
python3 - "$WORK/analysis.json" << 'PY'
import json, sys
with open(sys.argv[1]) as f:
    a = json.load(f)
print("  " + a.get("score", "?"))
for c in a.get("constraints", []):
    if c.get("hard", 0) < 0:
        print(f"  {c['constraint'][:44]:44} hard={c['hard']} matches={c.get('matches')}")
PY
echo "[tail] artifacts in $WORK"
