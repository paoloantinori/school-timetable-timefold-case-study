#!/bin/bash
#
# db-query.sh - Safe psql wrapper for database queries
#
# Usage: db-query.sh [options] <query|file.sql>
#
# Options:
#   -p, --profile NAME     Database profile (default: dev)
#   -f, --format FORMAT    Output format: table, json, csv (default: table)
#   -c, --command SQL      Execute SQL directly
#   -F, --file FILE        Execute SQL from file
#   -r, --readonly         Enforce read-only transaction (default: true)
#   -w, --write            Allow write operations
#   -h, --help             Show this help
#
# Exit codes:
#   0 - Success
#   1 - Query error
#   2 - Invalid arguments or connection failure
#
# Security: Defaults to read-only transactions, safe to whitelist
#
# Examples:
#   db-query.sh "SELECT * FROM teacher LIMIT 5"
#   db-query.sh -f json "SELECT count(*) FROM lesson"
#   db-query.sh -F queries/summary.sql
#

set -euo pipefail

# Default options
PROFILE="dev"
FORMAT="table"
READONLY=true
QUERY=""
FILE=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--profile)
            PROFILE="$2"
            shift 2
            ;;
        -f|--format)
            FORMAT="$2"
            shift 2
            ;;
        -c|--command)
            QUERY="$2"
            shift 2
            ;;
        -F|--file)
            FILE="$2"
            shift 2
            ;;
        -r|--readonly)
            READONLY=true
            shift
            ;;
        -w|--write)
            READONLY=false
            shift
            ;;
        -h|--help)
            sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
            exit 0
            ;;
        -*)
            echo "Error: Unknown option: $1" >&2
            exit 2
            ;;
        *)
            if [[ -z "$QUERY" ]]; then
                QUERY="$1"
            fi
            shift
            ;;
    esac
done

# Validate format
case "$FORMAT" in
    table|json|csv) ;;
    *)
        echo "Error: Invalid format '$FORMAT'. Use: table, json, csv" >&2
        exit 2
        ;;
esac

# Check for podman
if ! command -v podman &>/dev/null; then
    echo "Error: podman is required but not installed" >&2
    exit 2
fi

# Container name (planner.sh uses fixed name "planner-pg" regardless of profile)
CONTAINER="planner-pg"

# Check container is running. NOTE: no `grep -q` here - under `set -o pipefail` the
# early-exit SIGPIPEs `podman ps` once its output grows past one buffer (it started
# failing the day sibling containers joined the machine, 26/08), and the pipeline
# reports "not running" while the container is up. Plain grep drains the stream.
if ! podman ps --format '{{.Names}}' | grep "^${CONTAINER}$" >/dev/null; then
    echo "Error: Container $CONTAINER is not running" >&2
    echo "Start it with: ./planner.sh start $PROFILE" >&2
    exit 2
fi

# Determine input source
if [[ -n "$FILE" ]]; then
    if [[ ! -f "$FILE" ]]; then
        echo "Error: File not found: $FILE" >&2
        exit 2
    fi
    QUERY=$(cat "$FILE")
elif [[ -z "$QUERY" ]]; then
    echo "Error: No query provided" >&2
    echo "Usage: $0 [options] <query|file.sql>" >&2
    exit 2
fi

# Build psql arguments based on format
case "$FORMAT" in
    table) PSQL_ARGS="-t" ;;
    json)  PSQL_ARGS="-t -A" ;;
    csv)   PSQL_ARGS="-t -A -F ','" ;;
esac

# Add read-only transaction if requested
if $READONLY; then
    QUERY="SET TRANSACTION READ ONLY; $QUERY"
fi

# Execute query
podman exec -i "$CONTAINER" psql -U planner -d planner $PSQL_ARGS -c "$QUERY"
