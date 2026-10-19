#!/bin/bash
#
# laptop-start-and-wait.sh - Start laptop planner app in production mode and wait for readiness
#
# This script is specifically for laptop server testing where the production JAR
# runs via `java -jar` (not mvn quarkus:run). It handles the pattern of:
# 1. Starting DB if needed
# 2. Starting the app in background (since prod mode blocks forever)
# 3. Polling for readiness
# 4. Exiting when ready (or timeout)
#
# Usage: laptop-start-and-wait.sh [timeout_seconds]
#
# Options:
#   timeout           Max wait time in seconds (default: 120)
#   -h, --help        Show this help
#
# Exit codes:
#   0 - App started and is ready
#   1 - App failed to start or timed out
#
# Security: Starts app process. Consider carefully before whitelisting.
#
# Examples:
#   laptop-start-and-wait.sh          # Start with 120s timeout
#   laptop-start-and-wait.sh 60       # Start with 60s timeout

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Navigate to project root (scripts/core/ -> scripts/ -> project root)
PLANNER_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# TASK-604 incident guard (12/09): this script resolves PLANNER_ROOT from ITS OWN
# location, so the worktree's copy of this file launches the worktree's (possibly
# weeks-old) jar - the m39 bench's first arm ran a 29/08 build this way. Prod/dev
# cycles always belong to the MAIN checkout: refuse from a worktree unless explicitly
# overridden. Fires BEFORE any side effect (DB, kill-app, launch).
if [[ -f "$PLANNER_ROOT/.git" ]] && [[ "${PLANNER_LAUNCH_FROM_WORKTREE:-0}" != "1" ]]; then
    echo "LAUNCH ABORTED: '$PLANNER_ROOT' is a git WORKTREE, not the main checkout." >&2
    echo "  A prod/dev launch from here serves the worktree's own jar (possibly stale:" >&2
    echo "  incident 12/09 served a 29/08 jar and wasted a bench arm)." >&2
    echo "  Run from the main checkout, or set PLANNER_LAUNCH_FROM_WORKTREE=1 to override." >&2
    exit 1
fi

PLANNER_SH="$PLANNER_ROOT/planner.sh"

# Default values
TIMEOUT_SECONDS=120
# PROFILE is env-overridable so dedicated verification profiles can reuse this
# launcher (TASK-536 h36-verify): PROFILE=h36-verify ./scripts/core/prod-start-and-wait.sh
PROFILE="${PROFILE:-production}"
# App JVM heap — match planner.sh's PLANNER_HEAP default (TASK-387/389).
PLANNER_HEAP="${PLANNER_HEAP:-2g}"
POLL_INTERVAL=1  # Poll every second

# Parse arguments
TIMEOUT="${1:-$TIMEOUT_SECONDS}"

# Check for help flag
if [[ "$TIMEOUT" == "-h" || "$TIMEOUT" == "--help" ]]; then
    grep '^#' "$0" | cut -c 4- | sed 's/^ //'
    exit 0
fi

echo "=== laptop Production Start-and-Wait ==="
echo "Profile: $PROFILE"
echo "Timeout: ${TIMEOUT}s"
echo ""

# Step 1: Start DB if not running (using podman directly, not planner.sh start)
echo "Step 1: Checking database..."
# no `grep -q`: under pipefail the early exit SIGPIPEs `podman ps` once sibling
# containers grow its output, and this check spuriously fails (26/08 incidents)
if ! podman ps --format "{{.Names}}" 2>/dev/null | grep "^planner-pg\$"; then
    echo "  Database not running. Starting PostgreSQL container..."
    cd "$PLANNER_ROOT"
    # Start PostgreSQL container directly (same as planner.sh start, but without the app)
    podman run -d \
        --name planner-pg \
        --restart unless-stopped \
        -e POSTGRES_USER=planner \
        -e POSTGRES_PASSWORD=planner \
        -e POSTGRES_DB=planner \
        -p 5432:5432 \
        -v planner-data-$PROFILE:/var/lib/postgresql/data \
        docker.io/postgres:16-alpine > /dev/null 2>&1
    echo "  Waiting for PostgreSQL to be ready..."
    # Wait for PostgreSQL to accept connections (same as planner.sh)
    for i in {1..30}; do
        if podman exec planner-pg pg_isready -U planner > /dev/null 2>&1; then
            echo "  PostgreSQL is ready."
            break
        fi
        if (( i == 30 )); then
            echo "  ERROR: PostgreSQL failed to start"
            exit 1
        fi
        sleep 1
    done
else
    # TASK-541 (22/08): the bench profiles die here. A running planner-pg mounted on a
    # DIFFERENT profile's volume silently wins ("Database already running"), so a dedicated
    # verification run reads/writes the wrong DB - two night reseeds came from exactly this.
    mounted=$(podman inspect planner-pg --format "{{range .Mounts}}{{.Name}}{{end}}" 2>/dev/null)
    if [ "$mounted" != "planner-data-$PROFILE" ]; then
        echo "  ERROR: planner-pg is running on volume '$mounted' but PROFILE=$PROFILE expects 'planner-data-$PROFILE'."
        echo "  Stop it first (./planner.sh stop && podman rm -f planner-pg) or unset PROFILE to run on $mounted."
        exit 1
    fi
    echo "  Database already running (volume: $mounted)."
fi
echo ""

# Step 2: Kill any existing app process
echo "Step 2: Cleaning up existing app..."
cd "$PLANNER_ROOT"
"$PLANNER_SH" kill-app 2>/dev/null || true
echo "  Cleanup complete."

echo ""

# Step 3: Start app in background (production mode)
# PLANNER_JAVA_OPTS: extra JVM flags for the prod app on the java -jar line (NOT MAVEN_OPTS -
# see CLAUDE.md's MAVEN_OPTS lesson). e.g. the TASK-435 constrained-teacher flag.
# TASK-579: the generic preflight is ON THE MANDATORY PATH (this launcher is how
# every prod cycle starts): stale inner jar vs HEAD and flag-existence on the very
# flags this launcher is about to ship. The week of 26/08-02/09 lost a full day of
# measurements to exactly these two, with the gate available but bypassed.
if [[ -x "$PLANNER_ROOT/scripts/benchmark/launch-preflight.sh" ]]; then
  "$PLANNER_ROOT/scripts/benchmark/launch-preflight.sh" \
    --flags "${PLANNER_JAVA_OPTS:-}" \
    $( [[ "${PLANNER_JAVA_PREFLIGHT_JAR_OK:-0}" == 1 ]] && echo --jar-ok ) \
    || { echo "PROD-START ABORTED: launch-preflight refused. Set PLANNER_JAVA_PREFLIGHT_JAR_OK=1 ONLY to accept a stale jar explicitly." >&2; exit 1; }
fi
echo "Step 3: Starting app in production mode... PLANNER_JAVA_OPTS='${PLANNER_JAVA_OPTS:-}'"
LOG_FILE="/tmp/planner-prod.log"
nohup bash -c "cd \"$PLANNER_ROOT\" && java \"-Xmx${PLANNER_HEAP}\" ${PLANNER_JAVA_OPTS:-} -jar target/quarkus-app/quarkus-run.jar" > "$LOG_FILE" 2>&1 &
APP_PID=$!
echo "  App started in background (PID: $APP_PID)"
echo "  Log file: $LOG_FILE"
echo ""

# Step 4: Wait for app to be ready via HTTP polling
# HTTP is the only reliable check since log output is buffered
echo "Step 4: Waiting for HTTP readiness (max ${TIMEOUT}s)..."
echo -n "  Polling: "

START_TIME=$(date +%s)
READY=false

while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))

    if (( ELAPSED >= TIMEOUT )); then
        echo " ✗ TIMEOUT"
        echo ""
        echo "=== Timeout/Error ==="
        echo "App did not respond within ${TIMEOUT}s"
        echo ""
        echo "Last 20 lines of log:"
        tail -20 "$LOG_FILE"
        exit 1
    fi

    # Check if app process died
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo " ✗ APP PROCESS DIED"
        echo ""
        echo "=== App Crashed ==="
        echo "App process terminated unexpectedly"
        echo ""
        echo "Log:"
        cat "$LOG_FILE"
        exit 1
    fi

    # Try HTTP health endpoint first (fastest - Quarkus specific)
    if curl -s -m 1 -f http://localhost:8080/q/health >/dev/null 2>&1; then
        READY=true
        break
    fi

    # Fallback: try teachers endpoint
    if curl -s -m 1 -f http://localhost:8080/api/teachers >/dev/null 2>&1; then
        READY=true
        break
    fi

    # Progress indicator: show dot every second
    echo -n "."
    sleep "$POLL_INTERVAL"
done

# Show timing
END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))
echo " ✓ (${ELAPSED}s)"

echo ""
echo "=== Success ==="
echo "App is ready at http://localhost:8080"

# Optional: show the "started in" message from logs (if available)
if grep -q "started in" "$LOG_FILE" 2>/dev/null; then
    STARTUP_MSG=$(grep "started in" "$LOG_FILE" | head -1)
    echo "  $STARTUP_MSG" | sed 's/^[0-9]* INFO.*] //'
fi
