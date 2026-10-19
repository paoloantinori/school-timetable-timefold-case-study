#!/bin/bash
# rebuild.sh - Quick rebuild with optional app restart
#
# Builds backend + frontend (Quinoa). Always runs the Maven build.
# App restart is optional — skipped when DB is not running or --build-only is passed.
#
# Usage:
#   ./scripts/core/rebuild.sh              # Build + restart (if DB running)
#   ./scripts/core/rebuild.sh --build-only # Build only, no restart
#
# For thorough preflight checks, use rebuild-checked.sh.
# For fresh profile (new DB), use dev-start-and-wait.sh after this.

set -e

# Source sdkman so JAVA_HOME is set for Maven
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build-only) BUILD_ONLY=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

cd "$PROJECT_ROOT"

echo "=== Quick Rebuild (Backend + Frontend) ==="
echo ""

# Step 1: Kill running app (if any)
echo "=== Step 1: Killing running app ==="
./planner.sh kill-app 2>/dev/null || true

# Step 2: Build (always — does not require DB)
echo ""
echo "=== Step 2: Building ==="
mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true

if $BUILD_ONLY; then
    echo ""
    echo "=== Build complete (no restart) ==="
    exit 0
fi

# Step 3: Restart app (only if DB is running)
echo ""
if ./scripts/container/podman-tools.sh list 2>/dev/null | grep -q "planner-pg"; then
    echo "=== Step 3: Restarting app ==="
    ./scripts/core/dev-start-and-wait.sh dev
    echo ""
    echo "=== Rebuild complete ==="
    echo "App running at http://localhost:8080"
else
    echo "=== Step 3: Skipped (DB not running) ==="
    echo ""
    echo "To start with a fresh profile:"
    echo "  ./scripts/core/dev-start-and-wait.sh dev"
    echo ""
    echo "To restart against existing DB:"
    echo "  ./planner.sh start <profile>"
fi
