#!/usr/bin/env bash
# constraint-violations.sh - Check constraint violations without hanging
#
# This is a SAFE alternative to piping api.sh to python3 in background.
# Uses a single API call with server-side filtering to avoid pipe chains.
#
# Usage:
#   ./scripts/api/constraint-violations.sh H17 H18
#   ./scripts/api/constraint-violations.sh --all
#
# Output:
#   ✓ H17: 12 violations
#   ✓ H18: 5 violations
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Colors for output
if [[ -t 1 ]]; then
    GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; RESET='\033[0m'
else
    GREEN=''; RED=''; YELLOW=''; RESET=''
fi

# Check app health first (fail fast if not ready)
if ! "$SCRIPT_DIR/api.sh" health &>/dev/null; then
    echo -e "${RED}ERROR: App not ready${RESET}" >&2
    exit 1
fi

# Function to get violations for a single constraint
check_constraint() {
    local constraint="$1"
    local response

    # Use timeout-enabled API call (default 30s)
    if response=$("$SCRIPT_DIR/api.sh" get "/api/schedule/analysis/lessons?constraint=${constraint}" 2>/dev/null); then
        local count=$(echo "$response" | python3 -c "import sys, json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
        echo -e "${GREEN}✓${RESET} ${constraint}: ${count} violations"
    else
        echo -e "${YELLOW}⚠${RESET} ${constraint}: API error"
    fi
}

# Parse arguments
if [[ "${1:-}" == "--all" ]]; then
    # Check common constraints
    for c in H1 H2 H3 H4 H5 H6 H7 H11 H14 H15 H16 H17 H-D1 H-M2 M3 M5 S1 S7 S17; do
        check_constraint "$c"
    done
elif [[ $# -eq 0 ]]; then
    echo "Usage: $0 <constraint_id> [<constraint_id>...]" >&2
    echo "   or: $0 --all" >&2
    exit 2
else
    # Check specified constraints
    for c in "$@"; do
        check_constraint "$c"
    done
fi
