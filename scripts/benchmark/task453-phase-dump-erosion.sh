#!/usr/bin/env bash
#
# task453-phase-dump-erosion.sh - TASK-453: produce the WORK 2 erosion verdict (construction vs final).
#
# The tooling is delivered (lesson-identity-erosion.py + PhaseTransitionMarker.writeTransitionDumpWithIds,
# commit f0448e9e). This runs ONE staged solve with -Dplanner.phase-transition-dump.enabled=true so the
# marker writes ID-tagged dumps at each phase boundary (target/phase-transitions/phase-<n>-<slug>-id.txt),
# then lesson-identity-erosion.py --dir compares the first (post-construction) vs last (post-final) cut:
# which (group, subject) cells the LS eroded between construction and final, composition-controlled
# (MOBILE = unpinned at BOTH cuts; authoritative pins and template-promoted pins split out separately).
#
# MAVEN_OPTS TRAP (CLAUDE.md): the dump -D MUST reach the forked app JVM. planner.sh prod runs
# `java -jar` directly with PLANNER_JAVA_OPTS on the command line (NOT MAVEN_OPTS), so the flag reaches
# the app. We VERIFY early by polling for the marker's output files. NOTE on timing: stage A's solver
# (constructionOnlyConfig) DROPS the PhaseTransitionMarker, so no dumps are written during stage A
# construction; the first dump appears in stage B-TIMESLOT, ~35-40s in (after stage A construction +
# PROMOTION + stage B's own construction). The 180s early-verify window covers this with headroom for a
# larger dataset; if target/phase-transitions has no files at 180s, the flag did NOT reach the app JVM
# (MAVEN_OPTS trap) or the marker is not wired in, and we abort instead of burning a 40-min solve.
#
# Prerequisite: the `multistart` profile set up (source=input pre-assign persists; DB volume up).
# Usage: ./scripts/benchmark/task453-phase-dump-erosion.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="multistart"
RESULTS="claudedocs/spalmone-2026-27/task453-results"
DUMP_DIR="$ROOT/target/phase-transitions"
BOOT_LOG="/tmp/planner-task453-dump-boot.log"
SOLVE_LOG="/tmp/planner-task453-dump-solve.log"
SRC_INPUT="-Dplanner.pre-assign.constrained-teacher.source=input"
DUMP_FLAG="-Dplanner.phase-transition-dump.enabled=true"
# multi-start is DEFAULT ON; on the good-basin seed it stops at attempt 1 = ONE staged cycle = one
# clean construction->final pass (the dumps we want). Do NOT disable multi-start. Pass the dump dir
# explicitly so the marker writes to our $DUMP_DIR regardless of the java process's cwd (the marker
# defaults to a relative "target/phase-transitions"; this removes the implicit cwd coupling).
OPTS="$SRC_INPUT $DUMP_FLAG -Dplanner.phase-transition-dump.dir=$DUMP_DIR"
SOLVE_TIMEOUT=5400
WAIT_BOOT=420

log() { echo "[task453-dump $(date +%H:%M:%S)] $*" | tee -a "$RESULTS/run.log"; }
cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

mkdir -p "$RESULTS"
: > "$RESULTS/run.log"

# Clear stale dumps so we only capture THIS solve's phase transitions.
rm -rf "$DUMP_DIR"
mkdir -p "$DUMP_DIR"
log "cleared $DUMP_DIR"

boot_prod() {
    local java_opts="$1"
    rm -f "$BOOT_LOG"
    PLANNER_JAVA_OPTS="$java_opts" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
    local pid=$!
    for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
        if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
            log "boot: ready (pid $pid)"; return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            log "boot: prod DIED (see $BOOT_LOG)"; tail -15 "$BOOT_LOG" | tee -a "$RESULTS/run.log"; return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -15 "$BOOT_LOG" | tee -a "$RESULTS/run.log"; return 1
}

log "=== TASK-453 phase-dump erosion run (source=input, dump enabled) ==="
t0="$SECONDS"
boot_prod "$OPTS" || { log "boot FAILED"; exit 1; }
./scripts/api/api.sh status >/dev/null 2>&1 || true
sleep 2

./scripts/api/api.sh solve-staged || { log "solve-staged start FAILED"; ./planner.sh kill-app; exit 1; }
log "solve-staged started. Early-verify the dump flag reached the app JVM (MAVEN_OPTS trap check)..."

# EARLY VERIFY: poll for the first dump (appears ~35-40s in, in stage B). 180s window (60x3s) for
# headroom on larger datasets; if no dump file appears, the flag did NOT reach the app JVM -> abort.
dump_seen=0
for i in $(seq 1 60); do
    if ls "$DUMP_DIR"/phase-*-id.txt >/dev/null 2>&1; then
        dump_seen=1
        log "EARLY-VERIFY OK: dump files appearing in $DUMP_DIR (flag reached the app JVM)."
        ls -1 "$DUMP_DIR" | head -3 | tee -a "$RESULTS/run.log"
        break
    fi
    sleep 3
done
if [[ "$dump_seen" != "1" ]]; then
    log "!!! EARLY-VERIFY FAIL: no dump files after 180s. The dump flag did NOT reach the app JVM"
    log "    (MAVEN_OPTS trap) OR the marker is not wired into the staged cycle. Aborting (no 40-min waste)."
    grep -E "PhaseTransitionMarker|phase-transition" "$BOOT_LOG" | tail -5 | tee -a "$RESULTS/run.log" || true
    ./planner.sh kill-app
    echo "DUMP_FLAG_REACHED=no" > "$RESULTS/erosion-status.txt"
    exit 2
fi

log "waiting for the staged solve to finish (cap ${SOLVE_TIMEOUT}s)..."
if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT" > "$SOLVE_LOG" 2>&1; then
    log "wait TIMEOUT/FAILED (see $SOLVE_LOG)"
fi
elapsed=$(( SECONDS - t0 ))
# Score BEFORE kill-app (the app must be up to answer /score; fetching after kill gives "Cannot connect").
./scripts/api/api.sh score > "$RESULTS/score.txt" 2>&1 || log "score fetch FAILED (app down?)"
./planner.sh kill-app
log "solve done in ${elapsed}s."

# Collect dumps.
n_dumps=$(ls -1 "$DUMP_DIR"/phase-*-id.txt 2>/dev/null | wc -l | tr -d ' ' || true)
log "collected $n_dumps ID-tagged phase dumps."
ls -1 "$DUMP_DIR" | sort > "$RESULTS/dump-file-list.txt"

# Run the erosion verdict (first cut = post-construction, last cut = post-final).
if (( n_dumps >= 2 )); then
    log "running lesson-identity-erosion.py --dir ..."
    python3 scripts/analyze/lesson-identity-erosion.py --dir "$DUMP_DIR" > "$RESULTS/erosion-verdict.txt" 2>&1 || log "erosion script FAILED (see erosion-verdict.txt)"
    log "erosion verdict written to $RESULTS/erosion-verdict.txt"
    echo "DUMP_FLAG_REACHED=yes" > "$RESULTS/erosion-status.txt"
    echo "n_dumps=$n_dumps" >> "$RESULTS/erosion-status.txt"
    echo "wall_clock_seconds=$elapsed" >> "$RESULTS/erosion-status.txt"
    head -25 "$RESULTS/erosion-verdict.txt" | tee -a "$RESULTS/run.log"
else
    log "!!! only $n_dumps dumps (need >=2 for construction->final). Dump incomplete."
    echo "DUMP_FLAG_REACHED=yes but n_dumps=$n_dumps (<2, incomplete)" > "$RESULTS/erosion-status.txt"
fi
log "done."
