#!/bin/bash
#
# app-health.sh - Check if the planner app is running and healthy
#
# Usage: app-health.sh [options]
#
# Options:
#   -p, --port PORT     App port (default: 8080)
#   -j, --json          Output full JSON response
#   -q, --quiet         Exit code only (0=healthy, 1=unhealthy)
#   -h, --help          Show this help
#
# Exit codes:
#   0 - App is healthy
#   1 - App is not responding
#
# Security: Read-only HTTP GET, safe to whitelist
#

set -euo pipefail

PORT=8080
OUTPUT="status"
QUIET=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port) PORT="$2"; shift 2 ;;
        -j|--json) OUTPUT="json"; shift ;;
        -q|--quiet) QUIET=true; shift ;;
        -h|--help) sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'; exit 0 ;;
        *) shift ;;
    esac
done

URL="http://localhost:${PORT}/api/schedule"

# Try to fetch schedule
RESPONSE=$(curl -sS -f --connect-timeout 5 --max-time 10 "$URL" 2>&1) || {
    $QUIET && exit 1
    echo "UNHEALTHY: App not responding on port $PORT"
    exit 1
}

$QUIET && exit 0

case "$OUTPUT" in
    json)
        echo "$RESPONSE"
        ;;
    status)
        # Extract key info using grep/sed (no jq dependency)
        SOLVER=$(echo "$RESPONSE" | grep -o '"solverStatus":"[^"]*"' | cut -d'"' -f4 || echo "unknown")
        LESSONS=$(echo "$RESPONSE" | grep -o '"lessons":\[' | wc -l)
        echo "HEALTHY: solverStatus=$SOLVER"
        ;;
esac

exit 0
