#!/usr/bin/env bash
set -euo pipefail

# ── Planner Instance Manager ─────────────────────────────────────────────────
# Manages named PostgreSQL database profiles via Podman volumes.
# Each profile = a Podman volume (planner-data-<name>) with persistent data.
#
# Usage:
#   ./planner.sh start <profile>   Start DB + Quarkus (dev mode) for the named profile
#   ./planner.sh run               Restart Quarkus only - dev mode (DB must be running)
#   ./planner.sh prod <profile>    Start DB + Quarkus (production mode) for the named profile
#   ./planner.sh run-prod          Restart Quarkus only - production mode (DB must be running)
#   ./planner.sh stop              Stop the PostgreSQL container
#   ./planner.sh list              List available profiles
#   ./planner.sh delete <profile>  Delete a profile's volume
#   ./planner.sh kill-app          Kill the running Quarkus process
#   ./planner.sh wait              Wait until the app is ready
#   ./planner.sh status            Show what's currently running
# ──────────────────────────────────────────────────────────────────────────────

CONTAINER_NAME="planner-pg"
VOLUME_PREFIX="planner-data"
PG_IMAGE="docker.io/library/postgres:17"
PG_USER="planner"
PG_PASS="planner"
PG_DB="planner"
PG_PORT=5432
READY_TIMEOUT=30

# App JVM heap. Default 2g: at the ~260MB JVM default a solved 12,300-lesson
# schedule pins Old gen at ~81% and the idle app GC-thrashes a core; 2g removes
# it and shortens young-GC pauses (TASK-387/run 17, verified via jcmd). Not a
# score lever (GC overhead is ~2.3% at both sizes) — an operational default.
# Override for a constrained box: PLANNER_HEAP=1g ./planner.sh run
PLANNER_HEAP="${PLANNER_HEAP:-2g}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Colors (disabled when not a terminal) ────────────────────────────────────
if [[ -t 1 ]]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'
    BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; BLUE=''; CYAN=''; BOLD=''; RESET=''
fi

# ── Helpers ──────────────────────────────────────────────────────────────────

info()  { echo -e "${GREEN}[INFO]${RESET}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
err()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
die()   { err "$@"; exit 1; }

check_prereqs() {
    command -v podman >/dev/null 2>&1 || die "podman is not installed or not in PATH"
    command -v mvn    >/dev/null 2>&1 || die "mvn is not installed or not in PATH"
}

validate_profile() {
    local name="$1"
    if [[ ! "$name" =~ ^[a-zA-Z0-9][a-zA-Z0-9_-]*$ ]]; then
        die "Invalid profile name '${name}'. Use alphanumeric, hyphens, underscores (must start with alphanumeric)."
    fi
}

volume_name() {
    echo "${VOLUME_PREFIX}-$1"
}

# Returns the profile name of the currently running container, or empty string.
active_profile() {
    if podman inspect "$CONTAINER_NAME" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
        # Extract profile from the volume mount
        podman inspect "$CONTAINER_NAME" --format '{{range .Mounts}}{{.Name}}{{end}}' 2>/dev/null \
            | sed "s/^${VOLUME_PREFIX}-//"
    fi
}

is_container_running() {
    podman inspect "$CONTAINER_NAME" --format '{{.State.Running}}' 2>/dev/null | grep -q true
}

is_container_exists() {
    podman inspect "$CONTAINER_NAME" >/dev/null 2>&1
}

wait_for_pg() {
    info "Waiting for PostgreSQL to be ready..."
    local elapsed=0
    while (( elapsed < READY_TIMEOUT )); do
        if podman exec "$CONTAINER_NAME" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then
            info "PostgreSQL is ready."
            return 0
        fi
        sleep 1
        (( ++elapsed ))
    done
    die "PostgreSQL did not become ready within ${READY_TIMEOUT}s"
}

# ── Commands ─────────────────────────────────────────────────────────────────

cmd_start() {
    local profile="${1:?Usage: $0 start <profile>}"
    validate_profile "$profile"
    check_prereqs

    local vol
    vol="$(volume_name "$profile")"
    local current
    current="$(active_profile)"

    if [[ "$current" == "$profile" ]]; then
        info "Profile '${BOLD}${profile}${RESET}' is already active."
    else
        # Stop any currently running instance
        if [[ -n "$current" ]]; then
            warn "Stopping active profile '${current}'..."
            podman stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
            podman rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
        elif is_container_exists; then
            podman rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
        fi

        # Create volume if it doesn't exist
        if ! podman volume inspect "$vol" >/dev/null 2>&1; then
            info "Creating new profile '${BOLD}${profile}${RESET}'..."
            podman volume create "$vol" >/dev/null
        else
            info "Using existing profile '${BOLD}${profile}${RESET}'."
        fi

        # Start PostgreSQL container
        info "Starting PostgreSQL container..."
        podman run -d \
            --name "$CONTAINER_NAME" \
            -e POSTGRES_USER="$PG_USER" \
            -e POSTGRES_PASSWORD="$PG_PASS" \
            -e POSTGRES_DB="$PG_DB" \
            -p "${PG_PORT}:5432" \
            -v "${vol}:/var/lib/postgresql/data:Z" \
            "$PG_IMAGE" >/dev/null

        wait_for_pg
    fi

    # Start Quarkus
    # Detect if we're in an interactive shell or being called from a script
    if [[ -t 0 ]]; then
        # Interactive terminal - run in foreground (user can Ctrl+C)
        cmd_run_internal
    else
        # Non-interactive (scripted) - background Quarkus and wait for readiness
        info "Starting Quarkus in background (scripted mode)..."
        cd "$SCRIPT_DIR"
        # SOLVER_CONFIG env var overrides solver-config-xml without rebuild
        if [[ -n "${SOLVER_CONFIG:-}" ]]; then
            export QUARKUS_TIMEFOLD_SOLVER_SOLVER_CONFIG_XML="$SOLVER_CONFIG"
        fi
        JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Xmx${PLANNER_HEAP}" mvn quarkus:run \
            -Dquarkus.hibernate-orm.schema-management.strategy=update \
            -Dnet.bytebuddy.experimental=true \
            > /tmp/planner.log 2>&1 &
        local app_pid=$!
        info "Quarkus starting (PID: $app_pid)..."
        # Wait for readiness before returning (so caller sees process as alive)
        cmd_wait
    fi
}

cmd_run() {
    check_prereqs

    if ! is_container_running; then
        die "No PostgreSQL container is running. Use '${BOLD}$0 start <profile>${RESET}' first."
    fi

    cmd_run_internal
}

cmd_run_internal() {
    # ⚠️  BLOCKS FOREVER — runs mvn quarkus:run in the foreground.
    # For scripted/automated use, call this in the background and then
    # poll readiness separately:
    #     ./planner.sh start <profile> &
    #     ./planner.sh wait
    # See also: scripts/benchmark.sh for a working example.

    # Kill any existing Quarkus process to free the port
    cmd_kill_app

    local current
    current="$(active_profile)"

    info "Starting Quarkus (profile: ${BOLD}${current}${RESET})..."
    echo -e "${CYAN}  Hibernate strategy: update (data will be preserved)${RESET}"
    echo -e "${CYAN}  Ctrl+C to stop the app (DB stays running)${RESET}"
    echo ""

    # Trap Ctrl+C: let Quarkus die, print reminder
    trap 'echo ""; info "Quarkus stopped. DB is still running (profile: ${BOLD}${current}${RESET})."; echo -e "  ${YELLOW}Use ${BOLD}./planner.sh run${RESET}${YELLOW} to restart the app, or ${BOLD}./planner.sh stop${RESET}${YELLOW} to stop the DB.${RESET}"; exit 0' INT

    cd "$SCRIPT_DIR"
    # SOLVER_CONFIG env var overrides solver-config-xml without rebuild
    if [[ -n "${SOLVER_CONFIG:-}" ]]; then
        export QUARKUS_TIMEFOLD_SOLVER_SOLVER_CONFIG_XML="$SOLVER_CONFIG"
    fi
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Xmx${PLANNER_HEAP}" mvn quarkus:run \
        -Dquarkus.hibernate-orm.schema-management.strategy=update \
        -Dnet.bytebuddy.experimental=true
}

# Build production JAR if needed
ensure_prod_jar() {
    local jar_path="$SCRIPT_DIR/target/quarkus-app/quarkus-run.jar"
    local java_sources=($SCRIPT_DIR/src/main/java/**/*.java)

    # Check if JAR exists
    if [[ ! -f "$jar_path" ]]; then
        info "Production JAR not found. Building..."
        (cd "$SCRIPT_DIR" && mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true) || \
            die "Build failed. Cannot run in production mode."
        return
    fi

    # Check if JAR is older than any Java source file (rebuild needed)
    local jar_mtime=$(stat -c %Y "$jar_path" 2>/dev/null || stat -f %m "$jar_path")
    local needs_rebuild=false

    for src_file in "$SCRIPT_DIR"/src/main/java/**/*.java; do
        if [[ -f "$src_file" ]]; then
            local src_mtime=$(stat -c %Y "$src_file" 2>/dev/null || stat -f %m "$src_file")
            if (( src_mtime > jar_mtime )); then
                needs_rebuild=true
                break
            fi
        fi
    done

    if $needs_rebuild; then
        warn "Java sources are newer than JAR. Rebuilding..."
        (cd "$SCRIPT_DIR" && mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true) || \
            die "Build failed. Cannot run in production mode."
    fi
}

# Enhanced version that also ensures UI resources are bundled
ensure_prod_jar_with_ui() {
    local jar_path="$SCRIPT_DIR/target/quarkus-app/quarkus-run.jar"
    local resources_dir="$SCRIPT_DIR/src/main/resources/META-INF/resources"

    # Check if resources need to be copied
    local needs_rebuild=false
    local needs_resources_copy=false

    # Check if JAR exists
    if [[ ! -f "$jar_path" ]]; then
        needs_rebuild=true
        needs_resources_copy=true
    else
        # Get JAR mtime once (reuse for both checks)
        local jar_mtime
        jar_mtime=$(stat -c %Y "$jar_path" 2>/dev/null || stat -f %m "$jar_path")

        # Check Java sources efficiently using find -newer
        if find "$SCRIPT_DIR/src/main/java" -name "*.java" -newer "$jar_path" -print -quit | grep -q . 2>/dev/null; then
            needs_rebuild=true
        fi

        # Check UI resources (compare JAR with src/main/resources/META-INF/resources)
        if [[ ! $needs_rebuild && -f "$resources_dir/index.html" ]]; then
            local resources_mtime
            resources_mtime=$(stat -c %Y "$resources_dir/index.html" 2>/dev/null || stat -f %m "$resources_dir/index.html")
            if (( resources_mtime > jar_mtime )); then
                needs_rebuild=true
            fi
        fi
    fi

    # Build or rebuild
    if $needs_rebuild; then
        info "Building production JAR with UI resources..."
        (cd "$SCRIPT_DIR" && mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true) || \
            die "Build failed. Cannot run in production mode."
    fi

    # Copy UI resources if needed (after build, so Quinoa output exists)
    if $needs_resources_copy || [[ ! -f "$resources_dir/index.html" ]]; then
        if [[ -f "$SCRIPT_DIR/scripts/build/copy-ui-to-resources.sh" ]]; then
            info "Copying UI resources to classpath..."
            "$SCRIPT_DIR/scripts/build/copy-ui-to-resources.sh" || \
                warn "UI copy script failed, but continuing..."
        else
            warn "UI copy script not found. UI may not work in production."
        fi
    fi
}

cmd_prod() {
    local profile="${1:?Usage: $0 prod [--fresh] <profile>}"
    local fresh=false

    # Parse --fresh flag
    if [[ "$profile" == "--fresh" ]]; then
        fresh=true
        shift
        profile="${1:?Usage: $0 prod [--fresh] <profile>}"
    fi

    validate_profile "$profile"
    check_prereqs

    local vol
    vol="$(volume_name "$profile")"
    local current
    current="$(active_profile)"

    # === STEP 1: Stop any running instance ===
    if [[ "$current" == "$profile" && ! "$fresh" ]]; then
        info "Profile '${BOLD}${profile}${RESET}' is already active (use --fresh to reset)."
        info "Starting Quarkus in production mode..."
        cmd_run_prod_internal
        return
    fi

    if [[ -n "$current" ]]; then
        warn "Stopping active profile '${current}'..."
        podman stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
        podman rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
    elif is_container_exists; then
        podman rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    fi

    # === STEP 2: Handle fresh data if requested ===
    if $fresh; then
        if podman volume inspect "$vol" >/dev/null 2>&1; then
            echo -en "${YELLOW}Delete existing '${BOLD}${profile}${RESET}' profile data and start fresh? [y/N] ${RESET}"
            read -r response
            if [[ "$response" =~ ^[Yy]$ ]]; then
                info "Deleting volume '${vol}'..."
                podman volume rm "$vol" >/dev/null 2>&1 || \
                    die "Failed to delete volume. Stop the app and try again."
            else
                info "Keeping existing data."
            fi
        fi
    fi

    # Create volume if it doesn't exist
    if ! podman volume inspect "$vol" >/dev/null 2>&1; then
        info "Creating new profile '${BOLD}${profile}${RESET}'..."
        podman volume create "$vol" >/dev/null
    else
        info "Using existing profile '${BOLD}${profile}${RESET}'."
    fi

    # === STEP 3: Build production JAR with UI resources ===
    info "Ensuring production JAR and UI resources are up to date..."
    ensure_prod_jar_with_ui

    # === STEP 4: Start PostgreSQL container ===
    info "Starting PostgreSQL container..."
    podman run -d \
        --name "$CONTAINER_NAME" \
        -e POSTGRES_USER="$PG_USER" \
        -e POSTGRES_PASSWORD="$PG_PASS" \
        -e POSTGRES_DB="$PG_DB" \
        -p "${PG_PORT}:5432" \
        -v "${vol}:/var/lib/postgresql/data:Z" \
        "$PG_IMAGE" >/dev/null

    wait_for_pg

    # === STEP 5: Start Quarkus in production mode ===
    cmd_run_prod_internal
}

cmd_run_prod() {
    check_prereqs

    if ! is_container_running; then
        die "No PostgreSQL container is running. Use '${BOLD}$0 prod <profile>${RESET}' first."
    fi

    cmd_run_prod_internal
}

cmd_run_prod_internal() {
    # ⚠️  BLOCKS FOREVER — runs java -jar in the foreground.
    # For scripted/automated use, call this in the background and then
    # poll readiness separately.

    # Kill any existing Quarkus process to free the port
    cmd_kill_app

    # Ensure JAR is built and up-to-date
    ensure_prod_jar

    local current
    current="$(active_profile)"

    info "Starting Quarkus in ${BOLD}PRODUCTION MODE${RESET} (profile: ${BOLD}${current}${RESET})..."
    echo -e "${CYAN}  Hibernate strategy: update (data will be preserved)${RESET}"
    echo -e "${CYAN}  Ctrl+C to stop the app (DB stays running)${RESET}"
    echo ""

    # Trap Ctrl+C: let Quarkus die, print reminder
    trap 'echo ""; info "Quarkus stopped. DB is still running (profile: ${BOLD}${current}${RESET})."; echo -e "  ${YELLOW}Use ${BOLD}./planner.sh run-prod${RESET}${YELLOW} to restart the app, or ${BOLD}./planner.sh stop${RESET}${YELLOW} to stop the DB.${RESET}"; exit 0' INT

    cd "$SCRIPT_DIR"
    # Configure via environment variables for production
    export QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}"
    export QUARKUS_DATASOURCE_USERNAME="$PG_USER"
    export QUARKUS_DATASOURCE_PASSWORD="$PG_PASS"
    export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY="update"

    # PLANNER_JAVA_OPTS: extra JVM flags for the prod app (e.g. a -D that must reach the
    # app JVM on the java -jar line, not MAVEN_OPTS - see CLAUDE.md's MAVEN_OPTS lesson).
    # Word-split on purpose (the caller passes space-separated -D flags).
    # shellcheck disable=SC2086
    java "-Xmx${PLANNER_HEAP}" ${PLANNER_JAVA_OPTS:-} -jar target/quarkus-app/quarkus-run.jar
}

cmd_stop() {
    # Also kill any running Quarkus process
    cmd_kill_app

    if ! is_container_exists; then
        info "No container to stop."
        return 0
    fi

    local current
    current="$(active_profile)"
    if [[ -n "$current" ]]; then
        info "Stopping profile '${BOLD}${current}${RESET}'..."
    else
        info "Stopping container..."
    fi

    podman stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
    podman rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
    info "PostgreSQL stopped."
}

# Internal: stop without info messages (used during profile switching)
cmd_stop_quiet() {
    podman stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
    podman rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
}

cmd_list() {
    local current
    current="$(active_profile)"
    local volumes
    volumes="$(podman volume ls --format '{{.Name}}' | grep "^${VOLUME_PREFIX}-" | sort || true)"

    if [[ -z "$volumes" ]]; then
        info "No profiles found. Use '${BOLD}$0 start <name>${RESET}' to create one."
        return 0
    fi

    echo -e "${BOLD}Available profiles:${RESET}"
    while IFS= read -r vol; do
        local name="${vol#${VOLUME_PREFIX}-}"
        if [[ "$name" == "$current" ]]; then
            echo -e "  ${GREEN}${BOLD}${name}${RESET} ${GREEN}(active)${RESET}"
        else
            echo -e "  ${name}"
        fi
    done <<< "$volumes"
}

cmd_delete() {
    local profile="${1:?Usage: $0 delete <profile>}"
    validate_profile "$profile"

    local vol
    vol="$(volume_name "$profile")"

    # Check the volume exists
    if ! podman volume inspect "$vol" >/dev/null 2>&1; then
        die "Profile '${profile}' does not exist."
    fi

    # Refuse to delete active profile
    local current
    current="$(active_profile)"
    if [[ "$current" == "$profile" ]]; then
        die "Cannot delete active profile '${profile}'. Stop it first with '${BOLD}$0 stop${RESET}'."
    fi

    # Confirmation
    echo -en "${YELLOW}Delete profile '${BOLD}${profile}${RESET}${YELLOW}' and all its data? [y/N] ${RESET}"
    read -r confirm
    if [[ "$confirm" =~ ^[yY]$ ]]; then
        podman volume rm "$vol" >/dev/null
        info "Profile '${profile}' deleted."
    else
        info "Cancelled."
    fi
}

cmd_kill_app() {
    local pids
    pids="$(pgrep -f 'quarkus-run.jar' 2>/dev/null || true)"
    if [[ -z "$pids" ]]; then
        info "No Quarkus process found."
        return 0
    fi
    info "Stopping Quarkus process (PID: ${pids})..."
    kill $pids 2>/dev/null || true
    # Wait briefly for graceful shutdown
    local elapsed=0
    while (( elapsed < 10 )); do
        if ! pgrep -f 'quarkus-run.jar' >/dev/null 2>&1; then
            info "Quarkus stopped."
            return 0
        fi
        sleep 1
        (( ++elapsed ))
    done
    # Force kill if still running
    kill -9 $pids 2>/dev/null || true
    info "Quarkus force-stopped."
}

cmd_status() {
    if is_container_running; then
        local current
        current="$(active_profile)"
        echo -e "${GREEN}${BOLD}PostgreSQL is running${RESET}"
        echo -e "  Profile: ${BOLD}${current}${RESET}"
        echo -e "  Container: ${CONTAINER_NAME}"
        echo -e "  Port: ${PG_PORT}"
    elif is_container_exists; then
        echo -e "${YELLOW}PostgreSQL container exists but is stopped.${RESET}"
    else
        echo -e "${YELLOW}No PostgreSQL container found.${RESET}"
    fi

    # Check if Quarkus is running
    if curl -s -m 2 http://localhost:8080/api/teachers >/dev/null 2>&1; then
        echo -e "${GREEN}${BOLD}Quarkus is running${RESET} (http://localhost:8080)"
    else
        echo -e "${YELLOW}Quarkus is not running.${RESET}"
    fi
}

APP_READY_TIMEOUT=120

cmd_wait() {
    info "Waiting for Quarkus to be ready..."
    local elapsed=0
    local poll_interval=1
    local curl_timeout=2

    while (( elapsed < APP_READY_TIMEOUT )); do
        # Use q/health endpoint (faster than /api/teachers)
        if curl -s -m "$curl_timeout" http://localhost:8080/q/health >/dev/null 2>&1; then
            info "Quarkus is ready at ${BOLD}http://localhost:8080${RESET} (${elapsed}s)"
            return 0
        fi
        sleep "$poll_interval"
        (( elapsed += poll_interval ))
    done
    die "Quarkus did not become ready within ${APP_READY_TIMEOUT}s"
}

# ── Main ─────────────────────────────────────────────────────────────────────

usage() {
    echo -e "${BOLD}Planner Instance Manager${RESET}"
    echo ""
    echo -e "Usage: ${BOLD}$0 <command> [args]${RESET}"
    echo ""
    echo -e "Commands:"
    echo -e "  ${BOLD}start <profile>${RESET}   Start DB + app (dev mode) for the named profile"
    echo -e "  ${BOLD}run${RESET}               Restart app only - dev mode (DB must be running)"
    echo -e "  ${BOLD}prod <profile>${RESET}    Start DB + app (production mode) for the named profile"
    echo -e "  ${BOLD}run-prod${RESET}          Restart app only - production mode (DB must be running)"
    echo -e "  ${BOLD}stop${RESET}              Stop the DB container"
    echo -e "  ${BOLD}list${RESET}              List available profiles"
    echo -e "  ${BOLD}delete <profile>${RESET}  Delete a profile's data"
    echo -e "  ${BOLD}kill-app${RESET}          Kill the running Quarkus process"
    echo -e "  ${BOLD}wait${RESET}              Wait until the app is ready"
    echo -e "  ${BOLD}status${RESET}            Show what's currently running"
    echo ""
    echo -e "Each profile stores its PostgreSQL data in a separate Podman volume."
    echo -e "New profiles are auto-seeded with test data on first run."
    echo ""
    echo -e "${BOLD}Dev mode${RESET} (${YELLOW}mvn quarkus:run${RESET}): Hot-reload enabled, slower startup"
    echo -e "${BOLD}Production mode${RESET} (${YELLOW}java -jar${RESET}): Optimized, faster, no hot-reload"
}

case "${1:-}" in
    start)     shift; cmd_start "${1:-}" ;;
    run)       cmd_run ;;
    prod)      shift; cmd_prod "${1:-}" ;;
    run-prod)  cmd_run_prod ;;
    stop)      cmd_stop ;;
    list)      cmd_list ;;
    delete)    shift; cmd_delete "${1:-}" ;;
    kill-app)  cmd_kill_app ;;
    wait)      cmd_wait ;;
    status)    cmd_status ;;
    -h|--help|help) usage ;;
    *)         usage; exit 1 ;;
esac
