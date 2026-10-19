#!/bin/bash
#
# api-get.sh - Safe curl wrapper for GET requests
#
# Usage: api-get.sh [options] <url>
#
# Options:
#   -t, --timeout SECONDS  Request timeout (default: 30)
#   -H, --header HEADER    Add header (can be repeated)
#   -q, --quiet            Suppress progress output
#   -j, --json             Validate response is valid JSON
#   -h, --help             Show this help
#
# Exit codes:
#   0 - Success
#   1 - HTTP error or invalid JSON
#   2 - Invalid arguments or missing curl
#
# Security: Only supports GET requests, safe to whitelist
#

set -euo pipefail

# Default options
TIMEOUT=30
QUIET=false
VALIDATE_JSON=false
HEADERS=()

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -H|--header)
            HEADERS+=("-H" "$2")
            shift 2
            ;;
        -q|--quiet)
            QUIET=true
            shift
            ;;
        -j|--json)
            VALIDATE_JSON=true
            shift
            ;;
        -h|--help)
            sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
            exit 0
            ;;
        -*)
            echo "Error: Unknown option: $1" >&2
            exit 2
            ;;
        *)
            URL="$1"
            shift
            ;;
    esac
done

# Check dependencies
if ! command -v curl &>/dev/null; then
    echo "Error: curl is required but not installed" >&2
    exit 2
fi

# Check URL
if [[ -z "${URL:-}" ]]; then
    echo "Error: URL is required" >&2
    echo "Usage: $0 [options] <url>" >&2
    exit 2
fi

# Build curl arguments
CURL_ARGS=(
    -sS                    # Silent but show errors
    -X GET                 # Force GET only
    --connect-timeout 10   # Connection timeout
    --max-time "$TIMEOUT"  # Total timeout
    -f                     # Fail on HTTP errors
    "${HEADERS[@]}"
    "$URL"
)

# Execute request
if ! RESPONSE=$(curl "${CURL_ARGS[@]}" 2>&1); then
    echo "Error: HTTP request failed: $RESPONSE" >&2
    exit 1
fi

# Validate JSON if requested
if $VALIDATE_JSON; then
    if ! command -v jq &>/dev/null; then
        echo "Warning: jq not available, skipping JSON validation" >&2
    elif ! echo "$RESPONSE" | jq empty 2>/dev/null; then
        echo "Error: Response is not valid JSON" >&2
        exit 1
    fi
fi

# Output
if ! $QUIET; then
    echo "$RESPONSE"
else
    echo "$RESPONSE" > /dev/null
fi

exit 0
