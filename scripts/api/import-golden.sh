#!/bin/bash
#
# import-golden.sh - Import the hand-made golden 2026/27 timetable
# (src/main/resources/seed/lessons.csv) as pinned Lessons (TASK-346).
#
# Usage: import-golden.sh [options]
#
# Options:
#   -f, --force         Clear existing Lessons first (default: refuse if any exist)
#   -p, --port PORT     API port (default: 8080)
#   -t, --timeout SEC   Request timeout in seconds (default: 60)
#   -j, --json          Print the raw JSON report (no pretty-printing)
#   -h, --help          Show this help
#
# Intended for a dedicated, otherwise-empty profile:
#   ./planner.sh start golden
#   ./scripts/api/import-golden.sh
#
# See claudedocs/scripts.md ("Golden profile how-to") and claudedocs/rest-api.md
# (POST /api/schedule/import-golden) for details.
#
# Security: HTTP POST to a single fixed, whitelisted endpoint - safe to whitelist.
#

set -euo pipefail

PORT=8080
TIMEOUT=60
FORCE=false
RAW_JSON=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--force) FORCE=true; shift ;;
        -p|--port) PORT="$2"; shift 2 ;;
        -t|--timeout) TIMEOUT="$2"; shift 2 ;;
        -j|--json) RAW_JSON=true; shift ;;
        -h|--help)
            sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
            exit 0
            ;;
        *)
            echo "ERROR: Unknown option: $1" >&2
            echo "Run 'import-golden.sh --help' for usage." >&2
            exit 2
            ;;
    esac
done

URL="http://localhost:${PORT}/api/schedule/import-golden?force=${FORCE}"

echo "Importing golden timetable (force=${FORCE}) from localhost:${PORT}..."

RESPONSE=$(curl -sS --connect-timeout 5 --max-time "$TIMEOUT" -w '\n%{http_code}' \
    -X POST "$URL") || {
    echo "ERROR: Cannot reach the app on port $PORT (is it running? ./planner.sh status)" >&2
    exit 1
}

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" == "200" ]]; then
    echo "Import complete (HTTP 200)."
    if $RAW_JSON || ! command -v python3 &>/dev/null; then
        echo "$BODY"
    else
        echo "$BODY" | python3 -m json.tool
        # M5: force deletes ALL existing Lessons (pinned + unpinned) - always
        # call this out explicitly so it's never silently swallowed inside the
        # full JSON dump above.
        DELETED_PINNED=$(echo "$BODY" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("deletedPinned", 0))' 2>/dev/null || echo 0)
        DELETED_UNPINNED=$(echo "$BODY" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("deletedUnpinned", 0))' 2>/dev/null || echo 0)
        if [[ "$DELETED_PINNED" != "0" || "$DELETED_UNPINNED" != "0" ]]; then
            echo "Deleted before import: ${DELETED_PINNED} pinned, ${DELETED_UNPINNED} unpinned Lesson(s)."
        fi
        # Code review H6 (TASK-373): the incarichi-vs-grid Stage drift check
        # used to be log-only (server LOG.warnf) - now report-visible via
        # stageDriftWarnings, so call it out here instead of requiring a dig
        # through server logs to notice a drift.
        DRIFT_WARNINGS=$(echo "$BODY" | python3 -c 'import json,sys; [print(" - " + w) for w in json.load(sys.stdin).get("stageDriftWarnings", [])]' 2>/dev/null || true)
        if [[ -n "$DRIFT_WARNINGS" ]]; then
            echo "Stage drift warnings (incarichi.csv disagrees with the grid-derived Stage totalHours):"
            echo "$DRIFT_WARNINGS"
        fi
    fi
elif [[ "$HTTP_CODE" == "409" ]]; then
    echo "REFUSED (HTTP 409): either Lessons already exist in this profile, or the solver is currently running." >&2
    echo "$BODY" >&2
    echo "If Lessons already exist: re-run with --force to clear ALL of them first (pinned + unpinned), or use a fresh profile." >&2
    echo "If the solver is running: stop it first (POST /api/schedule/solve DELETE), then retry." >&2
    exit 1
else
    echo "ERROR: HTTP $HTTP_CODE" >&2
    echo "$BODY" >&2
    exit 1
fi
