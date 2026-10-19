#!/bin/bash
#
# copy-ui-to-resources.sh - Copy Quinoa build output to classpath resources
#
# This script copies the frontend build output to src/main/resources/META-INF/resources/
# so it gets bundled into the production JAR. This allows the IndexResource to serve
# the UI from classpath resources in production mode.
#
# Usage:
#   ./scripts/build/copy-ui-to-resources.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Navigate to project root (scripts/build/ -> scripts/ -> project root)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

QUINOA_BUILD_DIR="$PROJECT_ROOT/target/quinoa/build"
RESOURCES_DIR="$PROJECT_ROOT/src/main/resources/META-INF/resources"

echo "=== Copying UI build to resources ==="

if [[ ! -d "$QUINOA_BUILD_DIR" ]]; then
    echo "ERROR: Quinoa build directory not found: $QUINOA_BUILD_DIR"
    echo "Run: mvn clean package (or ./scripts/core/rebuild.sh)"
    exit 1
fi

# Create target directory
echo "Creating: $RESOURCES_DIR"
mkdir -p "$RESOURCES_DIR"

# Copy index.html
echo "Copying index.html..."
cp "$QUINOA_BUILD_DIR/index.html" "$RESOURCES_DIR/"

# Copy assets directory
echo "Copying assets directory..."
if [[ -d "$QUINOA_BUILD_DIR/assets" ]]; then
    cp -r "$QUINOA_BUILD_DIR/assets" "$RESOURCES_DIR/"
else
    echo "WARNING: assets directory not found"
fi

# Count copied files
HTML_COUNT=$(find "$RESOURCES_DIR" -name "*.html" | wc -l | tr -d ' ')
ASSET_COUNT=$(find "$RESOURCES_DIR/assets" -type f 2>/dev/null | wc -l | tr -d ' ' || echo "0")

echo ""
echo "=== Copy complete ==="
echo "  HTML files: $HTML_COUNT"
echo "  Asset files: $ASSET_COUNT"
echo ""
echo "Next step: Run 'mvn clean package' to bundle resources into the JAR"
