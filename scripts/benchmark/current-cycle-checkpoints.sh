#!/usr/bin/env bash
# current-cycle-checkpoints.sh - extract THIS cycle's checkpoints from the
# shared default heap (the 8/09 discovery: launches without
# -Dplanner.checkpoint.dir write to the suffix-less "staged-checkpoints/",
# which ACCUMULATES attempts across cycles - attempt1..15 of mixed provenance).
# Lists only files newer than the given reference time (default: today 15:00,
# the 8/09 boxdef relaunch), so tonight's reading cannot mix stale attempts.
#
# Usage: current-cycle-checkpoints.sh [laptop] ["YYYY-MM-DD HH:MM"]
set -uo pipefail
TARGET="${1:-laptop}"
SINCE="${2:-$(date '+%Y-%m-%d') 15:00}"

if [[ "$TARGET" == "laptop" ]]; then
  timeout 40 ssh -o ConnectTimeout=15 -o ClearAllForwardings=yes laptop \
    "find /Users/user/planner/staged-checkpoints -name '*.json' -newermt '$SINCE' 2>/dev/null | sort"
else
  find staged-checkpoints -name '*.json' -newermt "$SINCE" 2>/dev/null | sort
fi
