#!/bin/bash
#
# import-calendario.sh - Import the school's pre-set "Calendario scolastico
# 2026/27" (src/main/resources/seed/calendario_*.csv) as pinned MODA lab
# Lessons, plus report-only FC/class-days coherence cross-checks (TASK-364).
#
# Usage: import-calendario.sh [options]
#
# Options:
#   -d, --dry-run       Compute and return the full report WITHOUT persisting
#                       anything (default: false, i.e. persist if valid)
#   -p, --port PORT     API port (default: 8080)
#   -t, --timeout SEC   Request timeout in seconds (default: 60)
#   -j, --json          Print the raw JSON report (no pretty-printing)
#   -h, --help          Show this help
#
# All-or-nothing: if dry-run is false and ANY calendario_moda.csv row fails
# validation (calendar-closed slot, teacher/group conflict, unresolvable
# course), NOTHING is persisted (HTTP 409, full report still returned) - run
# with --dry-run first to inspect failures safely.
#
# See claudedocs/rest-api.md (POST /api/schedule/import-calendario) and
# claudedocs/spalmone-2026-27/calendario-scolastico-format.md for details.
#
# Security: HTTP POST to a single fixed, whitelisted endpoint - safe to whitelist.
#

set -euo pipefail

PORT=8080
TIMEOUT=60
DRY_RUN=false
RAW_JSON=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--dry-run) DRY_RUN=true; shift ;;
        -p|--port) PORT="$2"; shift 2 ;;
        -t|--timeout) TIMEOUT="$2"; shift 2 ;;
        -j|--json) RAW_JSON=true; shift ;;
        -h|--help)
            sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
            exit 0
            ;;
        *)
            echo "ERROR: Unknown option: $1" >&2
            echo "Run 'import-calendario.sh --help' for usage." >&2
            exit 2
            ;;
    esac
done

URL="http://localhost:${PORT}/api/schedule/import-calendario?dryRun=${DRY_RUN}"

echo "Importing calendario scolastico (dryRun=${DRY_RUN}) from localhost:${PORT}..."

RESPONSE=$(curl -sS --connect-timeout 5 --max-time "$TIMEOUT" -w '\n%{http_code}' \
    -X POST "$URL") || {
    echo "ERROR: Cannot reach the app on port $PORT (is it running? ./planner.sh status)" >&2
    exit 1
}

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

print_body() {
    if $RAW_JSON || ! command -v python3 &>/dev/null; then
        echo "$BODY"
    else
        echo "$BODY" | python3 -m json.tool
    fi
}

if [[ "$HTTP_CODE" == "200" ]]; then
    echo "Import complete (HTTP 200)."
    print_body
elif [[ "$HTTP_CODE" == "409" ]]; then
    if echo "$BODY" | grep -q '"modaFailures"'; then
        echo "REFUSED (HTTP 409): validation failure(s) found - nothing was persisted (all-or-nothing)." >&2
        print_body
        echo "Re-run with --dry-run to inspect the report without retrying the write, or fix the underlying data." >&2
    else
        echo "REFUSED (HTTP 409): the solver is currently running." >&2
        echo "$BODY" >&2
        echo "Stop it first (POST /api/schedule/solve DELETE), then retry." >&2
    fi
    exit 1
else
    echo "ERROR: HTTP $HTTP_CODE" >&2
    echo "$BODY" >&2
    exit 1
fi
