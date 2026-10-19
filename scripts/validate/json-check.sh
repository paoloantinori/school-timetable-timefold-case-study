#!/bin/bash
#
# json-check.sh - Validate JSON files
#
# Usage: json-check.sh <file.json>
#
# Exit codes:
#   0 - Valid JSON
#   1 - Invalid JSON or file not found
#   2 - Missing jq or no file argument
#
# Security: Read-only operation, safe to whitelist
#

set -euo pipefail

# Check dependencies
if ! command -v jq &>/dev/null; then
    echo "Error: jq is required but not installed" >&2
    exit 2
fi

# Check arguments
if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <file.json>" >&2
    echo "  Validates that a file contains valid JSON" >&2
    exit 2
fi

FILE="$1"

# Check file exists
if [[ ! -f "$FILE" ]]; then
    echo "Error: File not found: $FILE" >&2
    exit 1
fi

# Validate JSON
if jq empty "$FILE" 2>/dev/null; then
    echo "OK: $FILE is valid JSON"
    exit 0
else
    echo "ERROR: $FILE is not valid JSON" >&2
    # Show detailed error
    jq empty "$FILE" 2>&1 | head -5 >&2
    exit 1
fi
