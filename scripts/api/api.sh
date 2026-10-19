#!/bin/bash
#
# api.sh - REST API client with built-in JSON handling
#
# Usage: api.sh <command> [args] [options]
#
# GET Commands:
#   status              Get solver status
#   score               Get current score
#   analysis            Get constraint analysis
#   lessons [limit]     List lessons (default 20)
#   teacher ID          Get teacher by ID
#   group NAME          Lessons for student group
#   health              Health check
#   export              Export current solution to JSON file
#
# Solver Control (POST/DELETE):
#   reset               Clear all assignments (unpin + remove timeslot/room)
#   solve               Start solver (POST)
#   solve-staged        Start the TASK-419 staged solve (POST): construction -> promote
#                       template pins -> solve the tail/box on the TIMESLOT phases ->
#                       selective demotion -> release -> ROOM phases (the release comes
#                       before them: a pin freezes the room too). Stages published in
#                       stagedStage: A-CONSTRUCTION, PROMOTION, B-TIMESLOT-<n>,
#                       C-DEMOTION-<n>, RELEASE, B-ROOM.
#   stop                Stop solver (DELETE)
#   reschedule          Reset and solve (POST)
#   wait                Poll until solver/pre-assign finishes (with timeout)
#
# Pre-Assignment Pipeline (CSP-based, async):
#   pre-assign          Run hybrid pre-assignment (acconciatura → labs → prof)
#   pre-assign-status   Get pre-assignment status
#   pre-assign-reset    Reset pre-assignment status to IDLE
#
# Constraint Modes:
#   compaction-mode [on|off]  Enable/disable compaction-only mode (H1,H2,H3,H7,H11,H17,M20)
#
# Generic Methods:
#   post PATH [-d JSON] POST to path (JSON from -d or stdin)
#   delete PATH         DELETE to path
#   put PATH [-d JSON]  PUT to path (JSON from -d or stdin)
#
# Options:
#   -p, --port PORT     API port (default: 8080)
#   -j, --json          Output raw JSON
#   -t, --timeout SEC   Request timeout (default: 30, use higher for solve/pre-assign)
#   -X, --method METHOD HTTP method for raw command (GET|POST|PUT|DELETE)
#   -d, --data JSON     Request body for POST/PUT
#   -h, --help          Show this help
#
# Security: HTTP GET/POST/DELETE for REST API operations
#
# Examples:
#   api.sh status
#   api.sh solve -t 60
#   api.sh pre-assign
#   api.sh post /api/teachers -d '{"name":"New Teacher"}'
#   echo '{"name":"Test"}' | api.sh post /api/teachers
#   curl -s http://localhost:8080/api/schedule/pre-assign -X POST
#   curl -s http://localhost:8080/api/teachers/1 -X PUT -d '{"name":"Updated"}'
#

set -euo pipefail

PORT=8080
OUTPUT="parsed"
TIMEOUT=30
METHOD="GET"
COMMAND=""
ARGS=()
BODY=""

# Parse options first, then command and args
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port) PORT="$2"; shift 2 ;;
        -j|--json) OUTPUT="json"; shift ;;
        -t|--timeout) TIMEOUT="$2"; shift 2 ;;
        -X|--method)
            METHOD="$2"
            # Validate method
            case "$METHOD" in
                GET|POST|PUT|DELETE) ;;
                *)
                    echo "ERROR: Invalid method '$METHOD'. Use GET, POST, PUT, or DELETE." >&2
                    exit 2
                    ;;
            esac
            shift 2
            ;;
        -h|--help)
            sed -n '/^# Usage:/,/^set -euo pipefail$/p' "$0" | grep '^# ' | sed 's/^# //'
            exit 0
            ;;
        -d|--data) BODY="$2"; shift 2 ;;
        -*)
            # Check for common mistakes and provide helpful hints
            case "$1" in
                -x)
                    echo "ERROR: Unknown option '-x'. Did you mean '-X' (uppercase) for HTTP method?" >&2
                    ;;
                POST|PUT|DELETE)
                    echo "ERROR: '$1' looks like an HTTP method. Use: api.sh ${1,,} /api/path" >&2
                    ;;
                *)
                    echo "ERROR: Unknown option: $1" >&2
                    echo "       Run 'api.sh --help' for available options" >&2
                    ;;
            esac
            exit 2
            ;;
        *)
            if [[ -z "$COMMAND" ]]; then
                COMMAND="$1"
            else
                ARGS+=("$1")
            fi
            shift
            ;;
    esac
done

BASE="http://localhost:${PORT}"

# Read body from stdin if not provided via -d and stdin is not a terminal
read_body_from_stdin() {
    if [[ -n "$BODY" ]]; then
        # Already have body from -d flag
        echo "$BODY"
    elif [[ ! -t 0 ]]; then
        # Stdin has data (piped)
        cat
    else
        # No body
        echo ""
    fi
}

# Generic fetch with method, body, and timeout support
# Usage: fetch path [method] [body]
fetch() {
    local path="$1"
    local method="${2:-GET}"
    local body="${3:-}"
    # 27/08: optional per-call timeout override (4th arg) for the heavy endpoints
    local timeout="${4:-$TIMEOUT}"

    local curl_args=(
        -sS
        --connect-timeout 5
        --max-time "$timeout"
        -X "$method"
        -w "\n%{http_code}"
    )

    if [[ -n "$body" ]]; then
        curl_args+=(-H "Content-Type: application/json" -d "$body")
    fi

    local response
    local exit_code=0

    # Capture both body and HTTP code (don't use -f, handle errors ourselves)
    response=$(curl "${curl_args[@]}" "${BASE}${path}" 2>&1) || exit_code=$?

    # Connection failures (exit codes 6-7, 28 for timeout)
    if [[ $exit_code -eq 6 ]] || [[ $exit_code -eq 7 ]]; then
        echo "ERROR: Cannot connect to ${BASE} (network error)" >&2
        return 1
    fi
    if [[ $exit_code -eq 28 ]]; then
        echo "ERROR: Request timed out after ${TIMEOUT}s" >&2
        return 1
    fi
    if [[ $exit_code -ne 0 ]]; then
        echo "ERROR: curl failed with exit code $exit_code" >&2
        return 1
    fi

    # Split response and HTTP code
    local http_code
    http_code=$(echo "$response" | tail -n1)
    response=$(echo "$response" | sed '$d')

    # Handle HTTP errors - show the actual error response
    if [[ "$http_code" =~ ^[45] ]]; then
        echo "ERROR: HTTP $http_code" >&2
        # Try to extract error message from JSON response
        if [[ -n "$response" ]]; then
            echo "$response" | head -c 500 >&2
        fi
        return 1
    fi

    echo "$response"
}

# Extract JSON field without jq
extract() {
    local json="$1"
    local field="$2"
    echo "$json" | grep -o "\"$field\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | cut -d'"' -f4 || \
    echo "$json" | grep -o "\"$field\"[[:space:]]*:[[:space:]]*[0-9]*" | grep -o '[0-9]*'
}

# Get combined args as string
ARGS_STR="${ARGS[*]:-}"

case "$COMMAND" in
    # === GET Commands ===
    status)
        # /api/schedule/status is the LIGHT in-memory poll (TASK-380): the full
        # /api/schedule costs ~9s / 16MB on the 2026/27 dataset, which piles up when a
        # polling loop asks every few seconds. Fields: solverStatus, score,
        # lastSolverError, preAssignmentStatus/Error, currentPhase, currentPhaseName,
        # stagedStage.
        data=$(fetch "/api/schedule/status") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            solver=$(extract "$data" "solverStatus")
            echo "solverStatus: ${solver:-unknown}"
            staged=$(extract "$data" "stagedStage")
            if [[ -n "$staged" && "$staged" != "null" ]]; then
                echo "stagedStage: $staged"
            fi
            err=$(extract "$data" "lastSolverError")
            if [[ -n "$err" && "$err" != "null" ]]; then
                echo "lastSolverError: $err"
            fi
        fi
        ;;

    score)
        # 27/08 (rilancio 2, infra a): three-tier chain. (1) the light /status
        # endpoint carries the delivered score string (22ms); (2) after resume
        # cycles /status resets to the "0hard/0medium/0soft" sentinel, and the
        # full /api/schedule pulls a 21MB payload plus a full refreshSolverStatics
        # that intermittently blanks under the default 30s timeout post-multi-start
        # (capture-run's score.txt came out empty twice on 26/08); in that state
        # (3) the analysis endpoint re-scores from the DB and always answers.
        data=$(fetch "/api/schedule/status") || data=""
        score=$(extract "$data" "score")
        # BendableScore branch (TASK-538 B): the sentinel arrives as "[0/0]hard/[0/0]soft";
        # both formats recognized so the same script works pre- and post-migration.
        if [[ -z "$score" || "$score" == "N/A" \
              || "$score" == "0hard/0medium/0soft" || "$score" == "[0/0]hard/[0/0]soft" ]]; then
            data=$(fetch "/api/schedule/analysis") || data=""
            score=$(extract "$data" "score")
        fi
        if [[ -z "$score" || "$score" == "N/A" ]]; then
            echo "ERROR: no score from /status nor /analysis (app busy?)" >&2
            exit 1
        fi
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "score: $score"
        fi
        ;;

    analysis)
        fetch "/api/schedule/analysis" || exit 1
        ;;

    lessons)
        limit="${ARGS_STR:-20}"
        fetch "/api/lessons?limit=${limit}" || exit 1
        ;;

    teacher)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh teacher ID" >&2
            exit 2
        fi
        fetch "/api/teachers/${ARGS[0]}" | grep -v '^$' || exit 1
        ;;

    group)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh group NAME" >&2
            exit 2
        fi
        fetch "/api/student-groups" 2>/dev/null || echo "[]"
        ;;

    health)
        if fetch "/api/schedule" > /dev/null 2>&1; then
            echo "HEALTHY: API responding on port $PORT"
            exit 0
        else
            echo "UNHEALTHY: API not responding on port $PORT"
            exit 1
        fi
        ;;

    # === Completion Stats ===
    completion)
        fetch "/api/completion"
        ;;

    # === Phase Progression ===
    phase-progression)
        fetch "/api/phase-progression"
        ;;

    # === Export ===
    export)
        OUTFILE="${ARGS[0]:-/tmp/schedule-export-$(date +%Y%m%d-%H%M%S).json}"
        echo "Exporting solution to $OUTFILE ..."
        # TASK-567: giant single-line JSON bodies die in fetch() (sed '$d' strips
        # the only line); curl writes straight to the file, http code checked separately
        HTTP_CODE=$(curl -sS --connect-timeout 5 --max-time "${ARGS[1]:-120}" -o "$OUTFILE" -w "%{http_code}" "${BASE}/api/schedule/export" 2>/dev/null)
        if [[ "$HTTP_CODE" == 200 ]]; then
            SIZE=$(ls -lh "$OUTFILE" | awk '{print $5}')
            LESSONS=$(python3 -c "import json; d=json.load(open('$OUTFILE')); print(len(d.get('lessons',[])))" 2>/dev/null || echo "?")
            ASSIGNED=$(python3 -c "import json; d=json.load(open('$OUTFILE')); print(sum(1 for l in d.get('lessons',[]) if l.get('timeslot')))" 2>/dev/null || echo "?")
            echo "Export complete: $SIZE, $LESSONS lessons ($ASSIGNED assigned)"
            echo "File: $OUTFILE"
        else
            echo "ERROR: Export failed" >&2
            rm -f "$OUTFILE"
            exit 1
        fi
        ;;

    # === Solver Control ===
    reset)
        echo "Resetting schedule (clearing all assignments)..."
        data=$(fetch "/api/schedule/reset" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Schedule reset to initial state"
        fi
        ;;

    solve)
        echo "Starting solver..."
        data=$(fetch "/api/schedule/solve" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Solver started (202 Accepted)"
        fi
        ;;

    solve-staged)
        echo "Starting staged solve (TASK-419)..."
        data=$(fetch "/api/schedule/solve-staged" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Staged solve started (202 Accepted) - follow 'stagedStage' in: api.sh status"
        fi
        ;;

    resume-staged)
        # TASK-542 warm start: continue a staged cycle from a boundary checkpoint
        # (the snapshot-first iteration loop: candidate code -> resume -> measure,
        # minutes instead of a full construction). Usage: api.sh resume-staged <path>
        chk_path="${ARGS[0]:-}"
        if [[ -z "$chk_path" ]]; then
            echo "Usage: api.sh resume-staged <checkpoint.json>" >&2
            exit 1
        fi
        echo "Starting staged resume from $chk_path (TASK-542)..."
        data=$(fetch "/api/schedule/solve-staged-resume?path=$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "$chk_path")" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Staged resume started (202 Accepted) - follow 'stagedStage' in: api.sh status"
        fi
        ;;

    stop)
        echo "Stopping solver..."
        data=$(fetch "/api/schedule/solve" "DELETE") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Solver stopped"
        fi
        ;;

    reschedule)
        echo "Rescheduling (reset + solve)..."
        data=$(fetch "/api/schedule/reschedule" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Reschedule initiated"
        fi
        ;;

    wait)
        # Poll until solver/pre-assign finishes
        timeout="${ARGS_STR:-300}"  # Default 5 minutes
        echo "Waiting for solver/pre-assign to finish (timeout: ${timeout}s)..."
        elapsed=0
        interval=3
        while [[ $elapsed -lt $timeout ]]; do
            # Check solver status (light endpoint, same as the `status` command)
            solver_data=$(fetch "/api/schedule/status" "GET") || exit 1
            solver_status=$(echo "$solver_data" | grep -o '"solverStatus"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
            # TASK-419: a staged solve runs EVERY stage on its own ad-hoc solver, with the
            # SolverManager IDLE throughout, so solverStatus alone reports NOT_SOLVING for the
            # whole cycle. stagedStage is null only once the cycle has released its pins AND
            # finished its room stage.
            staged_stage=$(echo "$solver_data" | grep -o '"stagedStage"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)

            # Check pre-assign status
            pre_data=$(fetch "/api/schedule/pre-assign/status" "GET" 2>/dev/null) || pre_data='{"status":"UNKNOWN"}'
            pre_status=$(echo "$pre_data" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 | head -1)

            # Check if all are done
            if [[ "$solver_status" == "NOT_SOLVING" ]] && [[ -z "$staged_stage" ]] && [[ "$pre_status" != "RUNNING" ]]; then
                echo "Done! Solver: $solver_status, Pre-assign: $pre_status"
                exit 0
            fi

            # Show progress
            echo "  [$((elapsed))s] Solver: $solver_status${staged_stage:+ (staged: $staged_stage)}, Pre-assign: $pre_status"
            sleep $interval
            elapsed=$((elapsed + interval))
        done

        echo "ERROR: Timeout after ${timeout}s" >&2
        exit 1
        ;;

    # === Pre-Assignment Pipeline (CSP-based) ===
    pre-assign)
        echo "Running hybrid pre-assignment (async, returns immediately)..."
        data=$(fetch "/api/schedule/pre-assign" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "$data"
            echo "Monitor with: ./scripts/api/api.sh pre-assign-status"
        fi
        ;;

    pre-assign-status)
        data=$(fetch "/api/schedule/pre-assign/status" "GET") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Status: $(echo "$data" | jq -r '.status')"
            echo "Phase: $(echo "$data" | jq -r '.currentPhase')"
            echo "Candidates Tried: $(echo "$data" | jq -r '.candidatesTried')"
            if [[ -n "$(echo "$data" | jq -r '.error')" ]] && [[ "$(echo "$data" | jq -r '.error')" != "null" ]]; then
                echo "Error: $(echo "$data" | jq -r '.error')"
            fi
        fi
        ;;

    pre-assign-reset)
        data=$(fetch "/api/schedule/pre-assign/reset" "POST") || exit 1
        if [[ "$OUTPUT" == "json" ]]; then
            echo "$data"
        else
            echo "Pre-assignment status reset to IDLE"
        fi
        ;;

    # === Constraint Modes ===
    compaction-mode)
        action="${1:-}"
        case "$action" in
            on|true|1|enable)
                echo "Enabling compaction-only mode (H1,H2,H3,H7,H11,H17,M20)..."
                data=$(fetch "/api/schedule/constraints/compaction-only-mode?enabled=true" "POST") || exit 1
                if [[ "$OUTPUT" == "json" ]]; then
                    echo "$data"
                else
                    echo "Compaction-only mode ENABLED"
                    echo "  Active constraints: H1,H2,H3 (conflicts), H7,H11 (daily hours), H17 (vertical), M20 (horizontal)"
                fi
                ;;
            off|false|0|disable)
                echo "Disabling compaction-only mode..."
                data=$(fetch "/api/schedule/constraints/compaction-only-mode?enabled=false" "POST") || exit 1
                if [[ "$OUTPUT" == "json" ]]; then
                    echo "$data"
                else
                    echo "Compaction-only mode DISABLED (all constraints active)"
                fi
                ;;
            *)
                # Show current status
                data=$(fetch "/api/schedule/constraints/compaction-only-mode" "GET") || exit 1
                if [[ "$OUTPUT" == "json" ]]; then
                    echo "$data"
                else
                    enabled=$(echo "$data" | grep -o '"compactionOnlyMode"[[:space:]]*:[[:space:]]*true' | wc -l)
                    if [[ "$enabled" -gt 0 ]]; then
                        echo "Compaction-only mode: ENABLED"
                    else
                        echo "Compaction-only mode: DISABLED"
                    fi
                fi
                ;;
        esac
        ;;

    # === Generic Methods ===
    post)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh post /api/path [-d JSON] or echo JSON | api.sh post /api/path" >&2
            exit 2
        fi
        body=$(read_body_from_stdin)
        fetch "${ARGS[0]}" "POST" "$body" || exit 1
        ;;

    get)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh get /api/path" >&2
            exit 2
        fi
        fetch "${ARGS[0]}" "GET" "" || exit 1
        ;;

    delete)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh delete /api/path" >&2
            exit 2
        fi
        fetch "${ARGS[0]}" "DELETE" || exit 1
        ;;

    put)
        if [[ -z "$ARGS_STR" ]]; then
            echo "Usage: api.sh put /api/path [-d JSON] or echo JSON | api.sh put /api/path" >&2
            exit 2
        fi
        body=$(read_body_from_stdin)
        fetch "${ARGS[0]}" "PUT" "$body" || exit 1
        ;;

    *)
        echo "Unknown command: $COMMAND" >&2
        echo "Commands: status, score, analysis, lessons, teacher, group, health, completion, phase-progression, export" >&2
        echo "          reset, solve, solve-staged, stop, reschedule" >&2
        echo "          pre-assign, pre-assign-status, pre-assign-reset" >&2
        echo "          export" >&2
        echo "          compaction-mode" >&2
        echo "          post, delete, put, wait" >&2
        exit 2
        ;;
esac
