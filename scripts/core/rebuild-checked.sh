#!/bin/bash
# rebuild-checked.sh - Full rebuild with thorough preflight checks
#
# Builds Java backend + React frontend (Quinoa), then restarts the app.
# Always runs the build. App restart only happens if DB is running.
#
# Usage:
#   ./scripts/core/rebuild-checked.sh [--no-wait] [--build-only]
#
# Options:
#   --no-wait      Don't wait for app to start (exit after restart command)
#   --build-only   Build only, skip app restart entirely
#
# After this script completes, hard-refresh browser (Ctrl+Shift+R) to clear cached JS.

set -e

# Source sdkman so JAVA_HOME is set for Maven
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
NO_WAIT=false
BUILD_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-wait) NO_WAIT=true; shift ;;
        --build-only) BUILD_ONLY=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

cd "$PROJECT_ROOT"

echo "=== Full Rebuild (Backend + Frontend) ==="
echo ""

# Preflight: Check if database is running (warn only, don't block build)
DB_RUNNING=false
if ./scripts/container/podman-tools.sh list 2>/dev/null | grep -q "planner-pg"; then
    DB_RUNNING=true
else
    echo "[WARN] PostgreSQL container 'planner-pg' is not running."
    echo "        Build will proceed; restart step will be skipped."
    echo "        Use './scripts/core/dev-start-and-wait.sh dev' afterwards to start."
    echo ""
fi

# Step 1: Kill running app
echo "Step 1: Killing running app..."
./planner.sh kill-app 2>/dev/null || true

# Step 2: Clean build
echo ""
echo "Step 2: Clean build (backend + frontend)..."
mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true -q

echo ""
echo "=== Build complete ==="

if $BUILD_ONLY; then
    exit 0
fi

# Step 3: Restart app (only if DB is running)
if $DB_RUNNING; then
    echo ""
    echo "Step 3: Starting app..."
    if $NO_WAIT; then
        ./planner.sh start dev
        echo ""
        echo "App starting. Run './planner.sh wait' to poll for readiness."
    else
        ./scripts/core/dev-start-and-wait.sh dev
        echo ""
        echo "=== Rebuild complete ==="
        echo ""
        echo "IMPORTANT: Hard-refresh your browser (Ctrl+Shift+R) to clear cached JS"
    fi
else
    echo ""
    echo "To start the app now:"
    echo "  ./scripts/core/dev-start-and-wait.sh dev"
fi
