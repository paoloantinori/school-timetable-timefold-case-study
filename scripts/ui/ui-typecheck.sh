#!/bin/bash
# ui-typecheck.sh - Quick TypeScript type checking (no build artifacts)
#
# Runs `tsc --noEmit` against the frontend source.
# Fast (~2-3s) compared to full build (~5s).
#
# Usage:
#   ./scripts/ui/ui-typecheck.sh
#   ./scripts/ui/ui-typecheck.sh --watch    # Watch mode for continuous feedback
#
# Exit codes: 0 = no errors, 1 = type errors found

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEBUI_DIR="$PROJECT_ROOT/src/main/webui"

if [[ ! -d "$WEBUI_DIR" ]]; then
    echo "ERROR: webui directory not found at $WEBUI_DIR" >&2
    exit 1
fi

cd "$WEBUI_DIR"

# Check node_modules exist
if [[ ! -d "node_modules" ]]; then
    echo "Installing dependencies..."
    npm install --silent
fi

if [[ "$1" == "--watch" ]]; then
    echo "TypeScript watch mode (Ctrl+C to stop)..."
    npx tsc --noEmit --watch
else
    echo "Running TypeScript type check..."
    if npx tsc --noEmit; then
        echo "OK - No type errors"
    else
        echo ""
        echo "FAIL - Fix the errors above"
        exit 1
    fi
fi
