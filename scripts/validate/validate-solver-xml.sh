#!/usr/bin/env bash
# Validate solver XML configs against Timefold's XSD schema.
# Extracts the XSD from the Timefold JAR and runs xmllint validation.
#
# Usage:
#   ./scripts/validate/validate-solver-xml.sh                # Validate all solver configs
#   ./scripts/validate/validate-solver-xml.sh file.xml       # Validate specific file

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Find Timefold JAR (exclude tests and sources)
TIMEFOLD_JAR=$(find ~/.m2/repository/ai/timefold/solver/timefold-solver-core -name "timefold-solver-core-*.jar" ! -name "*tests*" ! -name "*sources*" | sort -V | tail -1)
if [[ -z "$TIMEFOLD_JAR" ]]; then
    echo "ERROR: Timefold solver JAR not found in ~/.m2/repository" >&2
    echo "Run 'mvn dependency:resolve' first." >&2
    exit 2
fi

# Extract XSD to temp location
XSD_FILE=$(mktemp /tmp/solver-xsd-XXXXXX.xsd)
trap 'rm -f "$XSD_FILE"' EXIT
(cd /tmp && jar xf "$TIMEFOLD_JAR" solver.xsd && mv solver.xsd "$XSD_FILE")

# Check xmllint is available
if ! command -v xmllint &>/dev/null; then
    echo "ERROR: xmllint not found. Install libxml2-utils." >&2
    exit 2
fi

# Determine files to validate
if [[ $# -gt 0 ]]; then
    FILES=("$@")
else
    FILES=(
        "$PROJECT_DIR/src/main/resources/solverConfig.xml"
    )
fi

ERRORS=0
for f in "${FILES[@]}"; do
    if [[ ! -f "$f" ]]; then
        echo "SKIP: $f (not found)"
        continue
    fi
    BASENAME=$(basename "$f")
    if xmllint --schema "$XSD_FILE" "$f" --noout 2>&1; then
        : # xmllint prints "validates" on success
    else
        ERRORS=$((ERRORS + 1))
    fi
done

exit $ERRORS
