#!/bin/bash
# Wrapper script for building the frontend UI
# This script isolates the UI build process and handles errors gracefully

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[UI-BUILD]${NC} $1"
}

log_error() {
    echo -e "${RED}[UI-BUILD]${NC} $1" >&2
}

# Navigate to project root from wherever we are
# The script is at scripts/ui/ui-build.sh, so going up two levels gets us to project root
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

log_info "Building frontend (TypeScript + Vite)..."
log_info "Project root: $PROJECT_ROOT"

cd "$PROJECT_ROOT/src/main/webui"

# Run the build
if npm run build; then
    log_info "✓ Build completed successfully"
    log_info "Output: dist/"
    exit 0
else
    exit_code=$?
    log_error "✗ Build failed with exit code $exit_code"
    exit $exit_code
fi
