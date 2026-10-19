#!/bin/bash
#
# start-and-wait.sh - Start planner app and wait for readiness
#
# Usage: start-and-wait.sh [profile] [timeout_seconds]
#
# This script handles the common pattern of starting the app in the background
# and polling until it's ready. Useful for automation and benchmarks.
#
# Options:
#   profile           Database profile (default: dev)
#   timeout           Max wait time in seconds (default: 120)
#   -h, --help        Show this help
#
# Exit codes:
#   0 - App started and is ready
#   1 - App failed to start or timed out
#
# Security: Starts app process, not read-only. Consider carefully before whitelisting.
#
# Examples:
#   start-and-wait.sh dev          # Start dev profile, wait up to 120s
#   start-and-wait.sh prod 60      # Start prod profile, wait up to 60s
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Navigate up two levels from scripts/core/ to project root
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

PROFILE="dev"
TIMEOUT=120

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'; exit 0 ;;
        -*)
            echo "Unknown option: $1" >&2
            exit 2
            ;;
        *)
            if [[ "$PROFILE" == "dev" && ! "$1" =~ ^[0-9]+$ ]]; then
                PROFILE="$1"
            elif [[ "$1" =~ ^[0-9]+$ ]]; then
                TIMEOUT="$1"
            fi
            shift
            ;;
    esac
done

# Check planner.sh exists
if [[ ! -x "$PLANNER_SH" ]]; then
    echo "ERROR: Cannot find planner.sh at $PLANNER_SH" >&2
    exit 1
fi

echo "Starting planner with profile '$PROFILE' (timeout: ${TIMEOUT}s)..."

# Start the app in background, capturing output
LOG_FILE="/tmp/planner-${PROFILE}.log"
if [[ -n "${SOLVER_CONFIG:-}" ]]; then
    SOLVER_CONFIG="$SOLVER_CONFIG" "$PLANNER_SH" start "$PROFILE" > "$LOG_FILE" 2>&1 &
else
    "$PLANNER_SH" start "$PROFILE" > "$LOG_FILE" 2>&1 &
fi
APP_PID=$!

# APP_PID is the planner.sh LAUNCHER, which may legitimately exit (re-exec/fork:
# the real app runs as a separate `java -jar quarkus-run.jar` process). The
# liveness check must watch the APP, not the launcher: false "App process died"
# was reported while the app was alive and becoming ready (incident 21/08).
app_pid() {
    pgrep -f "quarkus-run.jar" | head -1
}

# Trap to clean up on exit: kill the launcher AND the resolved app process
trap "kill $APP_PID 2>/dev/null || true; APP_REAL=\$(pgrep -f 'quarkus-run.jar' | head -1); [[ -n \"\$APP_REAL\" ]] && kill \"\$APP_REAL\" 2>/dev/null || true" EXIT

# Poll for readiness
START_TIME=$(date +%s)
while true; do
    ELAPSED=$(($(date +%s) - START_TIME))

    if [[ $ELAPSED -ge $TIMEOUT ]]; then
        echo "ERROR: Timeout after ${TIMEOUT}s. Last 20 lines of log:" >&2
        tail -20 "$LOG_FILE" >&2
        exit 1
    fi

    # Liveness = launcher alive OR real app process alive. The launcher exiting
    # alone (re-exec) is NOT a failure; only both dead before readiness is.
    if ! kill -0 $APP_PID 2>/dev/null && [[ -z "$(app_pid)" ]]; then
        echo "ERROR: App process died. Last 20 lines of log:" >&2
        tail -20 "$LOG_FILE" >&2
        exit 1
    fi

    # Check if app is responding
    if curl -sS -f "http://localhost:8080/api/schedule" > /dev/null 2>&1; then
        echo "READY: App is responding after ${ELAPSED}s"
        # Detach from trap since we succeeded
        trap - EXIT
        exit 0
    fi

    # Wait before next poll
    sleep 2
done
