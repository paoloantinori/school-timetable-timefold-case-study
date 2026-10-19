#!/usr/bin/env bash
# Per-arm capture for the B-TIMESLOT-1 metaheuristic benchmark (notte2, 2026-07-31).
#
# Wraps capture-run.sh (score / analysis.json / phases.json / export / lessons dump /
# full KPI battery / golden-vs-run viz) and ADDS the staged-cycle B-TIMESLOT stage lines
# from the prod app log. Those lines hold the primary metaheuristic metric (the B-TIMESLOT-1
# end score, per-iteration timeslot-tier hard and box counts), and they MUST be grabbed now
# because prod-start-and-wait.sh OVERWRITES /tmp/planner-prod.log when it restarts the app
# for the next arm. capture-run.sh alone misses them: it greps /tmp/planner.log and the dev
# log, not the prod log that prod-start-and-wait writes.
#
# Usage: ./scripts/benchmark/capture-btimeslot-arm.sh <outdir>
set -euo pipefail

OUT="${1:?usage: capture-btimeslot-arm.sh <outdir>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# 1. Canonical capture (score/analysis/phases/export/KPI/viz).
./scripts/benchmark/capture-run.sh "$OUT"

# 2. Staged-cycle B-TIMESLOT stage lines from the PROD log (overwritten on next restart).
LOG="${PLANNER_PROD_LOG:-/tmp/planner-prod.log}"
if [ -f "$LOG" ]; then
  grep -aE "stage A \(construction|PROMOTED |stage B-TIMESLOT #|demotion|DEMOTE|cycle finished|hole repair|anchor repair|surplus rebalance|stage B-ROOM|still boxed" "$LOG" \
    | tail -50 > "$OUT/btimeslot-stages.txt" || true
  echo ">> B-TIMESLOT stages -> $OUT/btimeslot-stages.txt"
else
  echo ">> WARN: prod log $LOG not found; B-TIMESLOT stage lines not captured" >&2
fi

echo ">> arm captured to $OUT"
