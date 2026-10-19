#!/bin/bash
# ui-test.sh - Run Playwright e2e tests
#
# Pre-flight checks:
#   1. Verifies the app is running at localhost:8080
#   2. Checks Playwright browsers are installed
#
# Usage:
#   ./scripts/ui/ui-test.sh                  # Run all e2e tests
#   ./scripts/ui/ui-test.sh <test-file>      # Run specific test file
#   ./scripts/ui/ui-test.sh --headed         # Run with visible browser
#   ./scripts/ui/ui-test.sh --ui             # Open Playwright UI mode
#   ./scripts/ui/ui-test.sh --list          # List available tests
#   ./scripts/ui/ui-test.sh --install        # Install Playwright browsers
#
# Examples:
#   ./scripts/ui/ui-test.sh view-switch-perf
#   ./scripts/ui/ui-test.sh --headed view-switch-perf
#
# Exit codes: 0 = all tests pass, 1 = failures or setup issues
#
# TASK-367 runbook: profile state expected by the suite
#   The suite is written to run against EITHER a fresh 2026/27-seed profile
#   (all Lessons unpinned, LabRotation empty) or the golden-imported profile
#   (100% pinned). Specs that need specific fixtures (courses, lessons,
#   calendar rules) create them via the REST API in a beforeEach/test body
#   instead of relying on seed content - see course-grid-edit.spec.ts and
#   group-calendar-editor.spec.ts for the pattern.
#
#   Two-command prep sequence for a clean run against a fresh profile:
#     ./planner.sh start dev
#     ./planner.sh wait
#   Then run this script. For the golden-imported profile, import first via
#   POST /api/schedule/import (see claudedocs/rest-api.md) before running.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
WEBUI_DIR="$PROJECT_ROOT/src/main/webui"
APP_URL="http://localhost:8080"

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

# Parse arguments
PW_ARGS=()
TEST_FILE=""
INSTALL_ONLY=false
LIST_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --install)
            INSTALL_ONLY=true
            shift
            ;;
        --list)
            LIST_ONLY=true
            shift
            ;;
        --headed|--ui|--debug)
            PW_ARGS+=("$1")
            shift
            ;;
        --*)
            PW_ARGS+=("$1")
            shift
            ;;
        *)
            TEST_FILE="$1"
            shift
            ;;
    esac
done

# Handle --install
if $INSTALL_ONLY; then
    echo "Installing Playwright browsers..."
    npx playwright install chromium
    echo "Done."
    exit 0
fi

# Handle --list
if $LIST_ONLY; then
    npx playwright test --list
    exit 0
fi

# Pre-flight: check app is running
echo "Pre-flight checks..."
if ! curl -sf "$APP_URL" > /dev/null 2>&1; then
    echo ""
    echo "ERROR: App is not running at $APP_URL" >&2
    echo ""
    echo "Start the app first:"
    echo "  ./planner.sh start dev"
    echo "  ./planner.sh wait"
    echo ""
    echo "Or for a full rebuild:"
    echo "  ./scripts/rebuild-ui.sh"
    exit 1
fi
echo "  App is running at $APP_URL"

# Pre-flight: check Playwright browsers installed
if ! npx playwright test --list > /dev/null 2>&1; then
    echo ""
    echo "Playwright browsers not installed. Installing..."
    npx playwright install chromium
fi
echo "  Playwright ready"
echo ""

# Build test command
CMD=(npx playwright test)

if [[ -n "$TEST_FILE" ]]; then
    CMD+=("$TEST_FILE")
fi

CMD+=("${PW_ARGS[@]}")

echo "Running: ${CMD[*]}"
echo ""

"${CMD[@]}"
