#!/bin/bash
#
# spalmone-analyze.sh - Turnkey spalmone analysis pipeline
#
# Chains: CSV parse -> build -> fresh DB profile -> import -> validate -> cleanup
#
# Usage: scripts/analyze/spalmone-analyze.sh [options]
#
# Options:
#   -c, --csv PATH       CSV file path (default: scripts/data/spalmone.csv)
#   -o, --output PATH    Report output (default: claudedocs/spalmone-analysis-TIMESTAMP.md)
#   --skip-build         Skip Maven rebuild
#   --skip-parse         Skip CSV parsing (reuse existing JSON at /tmp/spalmone-parsed.json)
#   --keep-profile       Don't delete temp profile after analysis
#   -h, --help           Show this help
#
# Exit codes:
#   0 - Analysis complete, report generated
#   1 - Pipeline error (build, parse, import, or validate failed)
#   2 - Invalid arguments or missing prerequisites
#
# Security: Creates temp DB profile, runs builds and imports. NOT read-only.
#
# Examples:
#   scripts/analyze/spalmone-analyze.sh
#   scripts/analyze/spalmone-analyze.sh --skip-build --skip-parse
#   scripts/analyze/spalmone-analyze.sh -c ~/Desktop/spalmone.csv --keep-profile
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PLANNER_SH="$PROJECT_ROOT/planner.sh"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Defaults
CSV_PATH=""
OUTPUT_PATH=""
SKIP_BUILD=false
SKIP_PARSE=false
KEEP_PROFILE=false
PARSED_JSON="/tmp/spalmone-parsed.json"
PROFILE="spalmone-$$"
BRANCH=$(git -C "$PROJECT_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--csv)      CSV_PATH="$2"; shift 2 ;;
        -o|--output)   OUTPUT_PATH="$2"; shift 2 ;;
        --skip-build)  SKIP_BUILD=true; shift ;;
        --skip-parse)  SKIP_PARSE=true; shift ;;
        --keep-profile) KEEP_PROFILE=true; shift ;;
        -h|--help)
            sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
            exit 0
            ;;
        *) echo "ERROR: Unknown option: $1" >&2; exit 2 ;;
    esac
done

# Set default output path
if [[ -z "$OUTPUT_PATH" ]]; then
    OUTPUT_PATH="$PROJECT_ROOT/claudedocs/spalmone-analysis-${TIMESTAMP}.md"
fi

# Default to project-local CSV
if [[ -z "$CSV_PATH" ]]; then
    CSV_PATH="$SCRIPT_DIR/data/spalmone.csv"
fi

if [[ ! -f "$CSV_PATH" ]]; then
    echo "ERROR: CSV file not found: $CSV_PATH" >&2
    exit 2
fi

# Check prerequisites
for cmd in python3 mvn podman; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: Required command not found: $cmd" >&2
        exit 2
    fi
done

if [[ ! -x "$PLANNER_SH" ]]; then
    echo "ERROR: planner.sh not found at $PLANNER_SH" >&2
    exit 2
fi

echo "=============================================="
echo "  Spalmone Analysis Pipeline"
echo "=============================================="
echo "  CSV:     $CSV_PATH"
echo "  Branch:  $BRANCH"
echo "  Profile: $PROFILE"
echo "  Output:  $OUTPUT_PATH"
echo "=============================================="
echo ""

# Cleanup trap — runs on exit (success or failure)
cleanup() {
    echo ""
    echo "[Cleanup] Stopping app and DB..."
    "$PLANNER_SH" kill-app 2>/dev/null || true
    "$PLANNER_SH" stop 2>/dev/null || true

    if [[ "$KEEP_PROFILE" == "false" ]]; then
        echo "[Cleanup] Deleting temp profile '$PROFILE'..."
        yes | "$PLANNER_SH" delete "$PROFILE" 2>/dev/null || true
    else
        echo "[Cleanup] Keeping profile '$PROFILE' for inspection"
    fi
}
trap cleanup EXIT

# Step 1: Build (unless skipped)
if [[ "$SKIP_BUILD" == "false" ]]; then
    echo "[1/6] Building app..."
    (cd "$PROJECT_ROOT" && mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true -q)
    echo "  Build complete."
else
    echo "[1/6] Skipping build (--skip-build)"
fi

# Step 2: Parse CSV (unless skipped)
if [[ "$SKIP_PARSE" == "false" ]]; then
    echo "[2/6] Parsing CSV -> $PARSED_JSON"
    python3 "$SCRIPT_DIR/spalmone-parser.py" "$CSV_PATH" --output "$PARSED_JSON"
    echo "  Parse complete."
else
    echo "[2/6] Skipping parse (--skip-parse)"
    if [[ ! -f "$PARSED_JSON" ]]; then
        echo "ERROR: --skip-parse specified but $PARSED_JSON not found" >&2
        exit 1
    fi
fi

# Step 3: Stop any running app/DB
echo "[3/6] Stopping any running instances..."
"$PLANNER_SH" kill-app 2>/dev/null || true
"$PLANNER_SH" stop 2>/dev/null || true

# Step 4: Start fresh profile
echo "[4/6] Starting fresh profile '$PROFILE'..."
"$SCRIPT_DIR/start-and-wait.sh" "$PROFILE" 180
echo "  App is ready."

# Step 5: Import data
echo "[5/6] Importing spalmone data..."
python3 "$SCRIPT_DIR/spalmone-db-importer.py" "$PARSED_JSON"

# Normalize rooms: assign all NULL room_id to room 1 (Aula 1)
echo "  Normalizing rooms (room_id=1 for all lessons)..."
"$SCRIPT_DIR/db-query.sh" -w "UPDATE lesson SET room_id = 1 WHERE room_id IS NULL"
echo "  Import + normalization complete."

# Step 6: Run validation
echo "[6/6] Running validation..."
python3 "$SCRIPT_DIR/spalmone-validate.py" \
    --output "$OUTPUT_PATH" \
    --branch "$BRANCH" \
    --port 8080

echo ""
echo "=============================================="
echo "  Pipeline Complete"
echo "=============================================="
echo "  Report: $OUTPUT_PATH"
echo "=============================================="
