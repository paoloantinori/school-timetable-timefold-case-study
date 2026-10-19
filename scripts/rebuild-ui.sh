#!/bin/bash
# rebuild-ui.sh - Rebuild frontend + restart app
#
# Safe frontend rebuild script that:
# 1. Kills the running app
# 2. Builds frontend (npm run build in src/main/webui)
# 3. Rebuilds JAR (mvn package)
# 4. Restarts app
# 5. Waits for readiness
#
# Usage:
#   ./scripts/rebuild-ui.sh           # Full cycle with wait
#   ./scripts/rebuild-ui.sh --no-wait # Exit immediately after restart
#
# Exit codes: 0 = success, 1 = failure
#
# Security: Writes files, kills processes, starts app. NOT safe to whitelist without consideration.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NO_WAIT=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-wait) NO_WAIT=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo "=== Frontend Rebuild (UI + JAR + Restart) ==="
echo ""

# Step 1: Kill running app
echo "Step 1: Killing running app..."
cd "$PROJECT_ROOT"
./planner.sh kill-app 2>/dev/null || true

# Step 2: Build frontend
echo ""
echo "Step 2: Building frontend..."
cd "$PROJECT_ROOT/src/main/webui"
if npm run build; then
    echo "  Frontend build OK"
else
    echo "  Frontend build FAILED"
    exit 1
fi

# Step 3: Rebuild JAR (frontend is now baked in)
echo ""
echo "Step 3: Rebuilding JAR with new frontend..."
cd "$PROJECT_ROOT"
if mvn package -DskipTests -Dnet.bytebuddy.experimental=true -q; then
    echo "  JAR build OK"
else
    echo "  JAR build FAILED"
    exit 1
fi

# Step 4: Restart app
echo ""
echo "Step 4: Restarting app..."
if $NO_WAIT; then
    # Start in background without waiting
    ./planner.sh run > /tmp/planner.log 2>&1 &
    echo "  App starting in background (PID: $!)"
    echo "  Run './planner.sh wait' to poll for readiness"
else
    # Start in background and wait for readiness
    ./planner.sh run > /tmp/planner.log 2>&1 &
    echo "  Waiting for app to be ready..."
    ./planner.sh wait
fi

echo ""
echo "=== Rebuild complete ==="
echo "App running at http://localhost:8080"
echo ""
echo "IMPORTANT: Hard-refresh your browser (Ctrl+Shift+R) to clear cached JS"
