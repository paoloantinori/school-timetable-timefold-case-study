#!/bin/bash
# ui-dev.sh - Full frontend development cycle
#
# Orchestrates: typecheck -> build -> deploy (JAR) -> wait -> test
# Stops at the first failure. Each stage can be skipped.
#
# Usage:
#   ./scripts/ui/ui-dev.sh                  # Full cycle: check + build + deploy + test
#   ./scripts/ui/ui-dev.sh --check-only       # Just typecheck
#   ./scripts/ui/ui-dev.sh --build-only       # Typecheck + build (no deploy/test)
#   ./scripts/ui/ui-dev.sh --skip-test        # Deploy but skip Playwright tests
#   ./scripts/ui/ui-dev.sh --test-only        # Only run tests (app must be running)
#
# Exit codes: 0 = success, 1 = failure at some stage

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

# Defaults
DO_CHECK=true
DO_BUILD=true
DO_DEPLOY=true
DO_TEST=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --check-only)
            DO_BUILD=false; DO_DEPLOY=false; DO_TEST=false
            shift ;;
        --build-only)
            DO_DEPLOY=false; DO_TEST=false
            shift ;;
        --skip-test)
            DO_TEST=false
            shift ;;
        --test-only)
            DO_CHECK=false; DO_BUILD=false; DO_DEPLOY=false
            shift ;;
        *)
            echo "Unknown option: $1"; exit 1 ;;
    esac
done

cd "$PROJECT_ROOT"

PASS=0
FAIL=0

run_stage() {
    local label="$1"
    shift
    echo ""
    echo "=== $label ==="
    echo ""
    if "$@"; then
        echo ""
        echo "  $label: PASS"
        ((PASS++))
    else
        echo ""
        echo "  $label: FAIL"
        ((FAIL++))
        return 1
    fi
}

# Stage 1: TypeScript check
if $DO_CHECK; then
    run_stage "TypeScript Check" ./scripts/ui/ui-typecheck.sh || exit 1
fi

# Stage 2: Frontend build
if $DO_BUILD; then
    run_stage "Frontend Build" ./scripts/ui/ui-build.sh || exit 1
fi

# Stage 3: Deploy (full JAR rebuild + app restart)
if $DO_DEPLOY; then
    run_stage "Deploy" ./scripts/rebuild-ui.sh || exit 1
fi

# Stage 4: Playwright tests
if $DO_TEST; then
    run_stage "Playwright Tests" ./scripts/ui/ui-test.sh || {
        echo ""
        echo "=== SUMMARY: $PASS passed, $((FAIL)) failed ==="
        exit 1
    }
fi

echo ""
echo "=== SUMMARY: $((PASS)) stages passed ==="
