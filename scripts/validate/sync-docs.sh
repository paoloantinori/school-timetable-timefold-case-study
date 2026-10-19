#!/bin/bash
# sync-docs.sh - Synchronize frontend docs with Java constraint provider
#
# This script extracts constraint definitions from SchoolScheduleConstraintProvider.java
# and updates the frontend documentation files.
#
# Usage:
#   ./scripts/validate/sync-docs.sh [--ai] [--check]
#
# Options:
#   --ai     Use Claude to generate human-readable descriptions (requires claude CLI)
#   --check  Only check if sync is needed, exit 1 if out of sync
#
# The script:
# 1. Parses the constraint provider for constraint IDs
# 2. Compares with frontend data/constraints.ts
# 3. If --ai flag is set, delegates to Claude to generate descriptions
# 4. Updates frontend files if changes detected

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

CONSTRAINT_PROVIDER="$PROJECT_ROOT/src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java"
CONSTRAINTS_TS="$PROJECT_ROOT/src/main/webui/src/data/constraints.ts"
DOCS_EN="$PROJECT_ROOT/src/main/webui/src/i18n/locales/en/docs.json"
DOCS_IT="$PROJECT_ROOT/src/main/webui/src/i18n/locales/it/docs.json"

USE_AI=false
CHECK_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --ai) USE_AI=true; shift ;;
        --check) CHECK_ONLY=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

echo "=== Docs Sync Script ==="
echo ""

# Extract constraint IDs from Java file
extract_constraints() {
    grep -oP '\.asConstraint\("\K[^"]+' "$CONSTRAINT_PROVIDER" | \
        sed 's/:.*//' | \
        sort -u
}

echo "Extracting constraints from $CONSTRAINT_PROVIDER..."
JAVA_CONSTRAINTS=$(extract_constraints)
JAVA_COUNT=$(echo "$JAVA_CONSTRAINTS" | wc -l)
echo "Found $JAVA_COUNT constraint IDs in Java"

# Extract constraint IDs from TypeScript file
TS_CONSTRAINTS=$(grep -oP "id: '\K[^']+" "$CONSTRAINTS_TS" 2>/dev/null | sort -u || echo "")
TS_COUNT=$(echo "$TS_CONSTRAINTS" | wc -l)
echo "Found $TS_COUNT constraint IDs in TypeScript"

# Find new constraints
NEW_CONSTRAINTS=""
for c in $JAVA_CONSTRAINTS; do
    if ! echo "$TS_CONSTRAINTS" | grep -q "^${c}$"; then
        NEW_CONSTRAINTS="$NEW_CONSTRAINTS $c"
    fi
done

if [ -z "$NEW_CONSTRAINTS" ]; then
    echo ""
    echo "✓ Docs are in sync with constraint provider"
    exit 0
fi

echo ""
echo "⚠ New constraints detected:$(echo "$NEW_CONSTRAINTS" | tr ' ' '\n' | grep -v '^$' | sed 's/^/  - /')"

if $CHECK_ONLY; then
    echo ""
    echo "✗ Docs are out of sync. Run: ./scripts/validate/sync-docs.sh [--ai]"
    exit 1
fi

if $USE_AI; then
    echo ""
    echo "🤖 Delegating to Claude for constraint descriptions..."
    echo ""

    # Build prompt for Claude
    PROMPT="I need you to generate human-readable descriptions for the following scheduling constraints.
Each constraint needs:
1. A short name (2-4 words)
2. A human-readable description (1-2 sentences, avoid technical jargon)
3. An optional example (1 sentence showing a violation)

New constraints to document:
$(echo "$NEW_CONSTRAINTS" | tr ' ' '\n' | grep -v '^$' | sed 's/^/- /')

Context: This is for an Italian vocational school timetable scheduler.

Output format (JSON array):
[
  {
    \"id\": \"H1\",
    \"name\": \"Teacher Conflict\",
    \"type\": \"hard\",
    \"humanRead\": \"A teacher cannot be in two places at once.\",
    \"example\": \"If Prof. Rossi teaches at 9:00, they cannot have another class at 9:00.\"
  }
]

Only output valid JSON, no explanations."

    echo "$PROMPT"
    echo ""
    echo "Run this command manually with Claude:"
    echo "  claude \"$PROMPT\""
    echo ""
    echo "Then update $CONSTRAINTS_TS and $DOCS_EN/$DOCS_IT with the results."
else
    echo ""
    echo "To auto-generate descriptions, run:"
    echo "  ./scripts/validate/sync-docs.sh --ai"
    echo ""
    echo "Or manually add these constraints to:"
    echo "  - $CONSTRAINTS_TS"
    echo "  - $DOCS_EN"
    echo "  - $DOCS_IT"
fi

exit 0
