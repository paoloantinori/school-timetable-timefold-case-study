#!/bin/bash
# Full frontend rebuild: kill app, clean build, restart, wait
# This is the safe way to rebuild UI when React/Vite changes aren't detected

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[REBUILD-UI]${NC} $1"
}

log_error() {
    echo -e "${RED}[REBUILD-UI]${NC} $1" >&2
}

log_step() {
    echo -e "${BLUE}[REBUILD-UI]${NC} $1"
}

# Change to project root
cd "$(dirname "$0")/.."

# Step 1: Kill the running Quarkus app
log_step "Step 1/4: Killing running app..."
./planner.sh kill-app 2>/dev/null || true
sleep 1

# Step 2: Clean build the frontend (important for Vite to pick up changes)
log_step "Step 2/4: Clean building frontend (mvn clean package)..."
if mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true; then
    log_info "✓ Frontend build succeeded"
else
    log_error "✗ Frontend build failed"
    exit 1
fi

# Step 3: Restart the app using start-and-wait
log_step "Step 3/4: Restarting app..."
if ./scripts/start-and-wait.sh dev; then
    log_info "✓ App is ready at http://localhost:8080"
else
    log_error "✗ App failed to start or timed out"
    exit 1
fi

log_info "✓ Rebuild complete! Don't forget to hard-refresh your browser (Ctrl+Shift+R)"
