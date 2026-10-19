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

# Change to the webui directory relative to project root
# Use PWD to get absolute path, then navigate to webui
PROJECT_ROOT="$(pwd)"
WEBUI_DIR="$PROJECT_ROOT/src/main/webui"

log_info "Building frontend (TypeScript + Vite)..."
log_info "Working directory: $WEBUI_DIR"

cd "$WEBUI_DIR"

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
