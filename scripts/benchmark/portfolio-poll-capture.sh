#!/usr/bin/env bash
# Poll a running staged solve and run the canonical capture when it completes.
# Used by the portfolio N=3 measurement (2026-07-31): the staged cycle sets
# `stagedStage` to a non-null label for its whole duration and clears it at the
# end, so "stagedStage empty/null" is the completion signal (solverStatus stays
# NOT_SOLVING for the whole staged cycle - see CLAUDE.md "Osservare un solve
# staged").
#
# Usage: portfolio-poll-capture.sh <outdir> [poll_interval_seconds]
#   <outdir>  capture destination (passed to capture-run.sh)
#   interval  poll cadence in seconds (default 60)
set -euo pipefail

OUT="${1:?usage: portfolio-poll-capture.sh <outdir> [interval]}"
INTERVAL="${2:-60}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

case "$OUT" in
  /*) : ;;
  *)  OUT="$ROOT/$OUT" ;;
esac
mkdir -p "$OUT"

LOG_APP="/tmp/planner-prod.log"
STAMP='date +%H:%M:%S'

echo "[$($STAMP)] polling staged solve every ${INTERVAL}s; capture -> $OUT"
# Wait for the cycle to be ACTIVELY running first (stagedStage non-null), so we
# don't mistake a not-yet-started or already-null state for completion.
STARTED=false
while true; do
    STATUS=$(./scripts/api/api.sh status 2>/dev/null || true)
    STAGE=$(printf '%s\n' "$STATUS" | grep -i 'stagedStage' | sed 's/.*stagedStage[: ]*//' | tr -d ' \r' || true)
    if [ -n "$STAGE" ] && [ "$STAGE" != "null" ]; then
        STARTED=true
        echo "[$($STAMP)] stagedStage=$STAGE"
    else
        if [ "$STARTED" = "true" ]; then
            echo "[$($STAMP)] stagedStage cleared -> cycle done"
            break
        fi
        echo "[$($STAMP)] stagedStage empty (not started yet), waiting"
    fi
    sleep "$INTERVAL"
done

# Portfolio-specific: per-candidate scores + the pickBest line from the app log.
# (Only present when -Dplanner.portfolio.size>1; empty otherwise = single solve.)
grep -E 'portfolio B-TIMESLOT candidate|picked best' "$LOG_APP" > "$OUT/portfolio-candidates.txt" 2>/dev/null || true
# Wall-clock + RAM footprint: grep the JVM + the staged-solve timing lines.
grep -E 'stage B-TIMESLOT finished|stage .* finished|picked best' "$LOG_APP" > "$OUT/timings.txt" 2>/dev/null || true

# Canonical post-solve capture (score, analysis, phase progression, export, KPIs).
./scripts/benchmark/capture-run.sh "$OUT"
echo "[$($STAMP)] capture complete -> $OUT"
