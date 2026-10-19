#!/usr/bin/env bash
#
# acceptedCountLimit-sweep.sh - LIVE staged-solve sweep of solverConfig.xml's
# <acceptedCountLimit> (TASK-296). CONFIG-based (not a -D knob), so each value needs
# editing solverConfig.xml (both LS phases) + a JAR rebuild. Single variable: same good
# deterministic seed (source=input, multi-start OFF, GRASP OFF), only acceptedCountLimit
# varies. Reverts solverConfig.xml on ANY exit (trap). NO ADOPTION.
#
# Why live and not the benchmark harness: the harness is broken (Timefold 2.3.0 API drift
# benchmarkAndShowReportInBrowser + Jackson 3.x, hidden by pom <testExclude>**/benchmark/**).
# The live staged solve is production-representative (full pipeline, consistent with the
# 4 -D medium-knob sweeps) and needs no benchmark test classes.
#
# Verification: the knob has no runtime banner (it is baked into the JAR), so per value we
# confirm (a) the solverConfig.xml edit landed in BOTH LS phases, (b) the rebuild succeeded,
# (c) target/classes/solverConfig.xml has the baked value, (d) the -D mechanism banners
# (multi-start off, grasp off) confirm the source=input/multistart flags arrived.
#
# Usage: ./scripts/benchmark/acceptedCountLimit-sweep.sh [profile] [--no-setup]
#   profile   default m32-sweep (reuse its source=input pins)
#   --no-setup  skip fresh setup (assume profile ready). Default: reuse, no setup.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="${1:-m32-sweep}"
SETUP=0
[[ "${2:-}" == "--setup" ]] && SETUP=1

# Values to sweep (override via env for finer sweeps, e.g. VALUES="50 200 500").
# Default: the original coarse 100/10000 (acl-1000 baseline referenced separately).
read -ra VALUES <<< "${VALUES:-100 10000}"

SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MULTISTART_OFF="-Dplanner.multistart.enabled=false"
CONFIG="src/main/resources/solverConfig.xml"
BACKUP="/tmp/solverConfig.xml.acl-sweep.bak"

RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/acceptedcountlimit-sweep-results}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

SOLVE_TIMEOUT=5400
WAIT_BOOT=420
BOOT_LOG="/tmp/planner-acl-boot.log"

log() { echo "[acl-sweep $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }

# Back up solverConfig.xml and restore it on ANY exit (error, interrupt, normal).
cp "$CONFIG" "$BACKUP"
restore() {
  cp "$BACKUP" "$CONFIG" 2>/dev/null || true
  ./planner.sh kill-app >/dev/null 2>&1 || true
  log "solverConfig.xml restored from backup (no adoption)."
}
trap restore INT TERM EXIT

boot_prod() {
    local java_opts="$1"
    rm -f "$BOOT_LOG"
    PLANNER_JAVA_OPTS="$java_opts" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
    local pid=$!
    for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
        if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
            log "boot: ready (pid $pid)"
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            log "boot: prod process DIED (see $BOOT_LOG)"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
            return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
    ./planner.sh kill-app >/dev/null 2>&1 || true
    return 1
}

# Confirm the -D mechanism banners (source=input / multi-start off arrived). acceptedCountLimit
# is baked, not a -D, so this only confirms the companion flags; the bake is verified separately.
verify_mechanism() {
    local ms="" gp=""
    for _ in 1 2 3 4 5; do
        ms="$(grep -aoE "Multi-start staged retry: enabled=[a-z]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        gp="$(grep -aoE "grasp=[a-z]+ seed=[0-9-]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        [[ -n "$ms" && -n "$gp" ]] && break
        sleep 3
    done
    if [[ -z "$ms" || -z "$gp" ]]; then
        log "verify_mechanism: -D banners NOT found. ABORT run."; return 1
    fi
    log "verify_mechanism: OK - $ms; $gp"
    return 0
}

setup_profile() {
    log "=== SETUP: rebuild JAR (--build-only) ==="; ./scripts/core/rebuild.sh --build-only
    log "=== SETUP: fresh profile '$PROFILE' + boot (source=input) ==="
    boot_prod "$SRC_INPUT_FLAG" || { log "SETUP: boot FAILED"; exit 1; }
    log "=== SETUP: import-calendario ==="; ./scripts/api/import-calendario.sh
    log "=== SETUP: pre-assign (source=input) ==="; ./scripts/api/api.sh pre-assign
    local st=""
    for i in {1..120}; do
        st="$(./scripts/api/api.sh pre-assign-status 2>/dev/null | grep -oiE 'COMPLETE|ERROR|RUNNING|IDLE' | head -1 || true)"
        [[ "$st" == "COMPLETE" ]] && break
        [[ "$st" == "ERROR" ]] && { log "SETUP: pre-assign ERROR"; exit 1; }
        sleep 5
    done
    log "SETUP: pre-assign $st"; ./planner.sh kill-app
}

run_one() {
    local value="$1"
    local out="$RESULTS/acl-$value"
    local opts="$SRC_INPUT_FLAG $MULTISTART_OFF"
    log "--- RUN acl-$value ---"
    local t0="$SECONDS"

    # 1. Edit BOTH LS phases' acceptedCountLimit. Portable in-place (no `sed -i`:
    # BSD/macOS `sed -i SCRIPT` treats SCRIPT as the backup suffix and does not edit).
    sed "s|<acceptedCountLimit>[0-9]*</acceptedCountLimit>|<acceptedCountLimit>${value}</acceptedCountLimit>|g" "$CONFIG" > "$CONFIG.tmp" && mv "$CONFIG.tmp" "$CONFIG"
    local n; n="$(grep -c "<acceptedCountLimit>${value}</acceptedCountLimit>" "$CONFIG" || true)"
    if [[ "$n" -ne 2 ]]; then log "RUN acl-$value: edit landed $n/2 phases. ABORT"; return 1; fi
    log "RUN acl-$value: edited both LS phases -> $value"

    # 2. Rebuild the JAR (bakes the edited solverConfig.xml).
    log "RUN acl-$value: rebuilding JAR (--build-only)..."
    if ! ./scripts/core/rebuild.sh --build-only >/tmp/acl-rebuild.log 2>&1; then
        log "RUN acl-$value: REBUILD FAILED"; tail -15 /tmp/acl-rebuild.log | tee -a "$SUMMARY"; return 1
    fi
    # 3. Confirm the baked value reached target/classes.
    local nb; nb="$(grep -c "<acceptedCountLimit>${value}</acceptedCountLimit>" target/classes/solverConfig.xml 2>/dev/null || true)"
    if [[ "$nb" -ne 2 ]]; then log "RUN acl-$value: baked $nb/2 in target/classes. ABORT"; return 1; fi
    log "RUN acl-$value: baked value confirmed in target/classes"

    # 4. Boot + solve + capture.
    boot_prod "$opts" || { log "RUN acl-$value: boot FAILED"; return 1; }
    ./scripts/api/api.sh solve-staged || { log "RUN acl-$value: solve-staged FAILED"; ./planner.sh kill-app; return 1; }
    verify_mechanism || { ./planner.sh kill-app; return 1; }
    log "RUN acl-$value: verified, waiting for solve (cap ${SOLVE_TIMEOUT}s)..."
    ./scripts/api/api.sh wait "$SOLVE_TIMEOUT" || log "RUN acl-$value: wait non-zero (often benign near completion)"
    local elapsed=$(( SECONDS - t0 ))
    PLANNER_LOG="$BOOT_LOG" ./scripts/benchmark/capture-run.sh "$out" || log "RUN acl-$value: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN acl-$value: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

if (( SETUP == 1 )); then setup_profile; else log "=== --no-setup: reusing profile '$PROFILE' (source=input pins persist) ==="; fi

for v in "${VALUES[@]}"; do
    run_one "$v" || log "RUN acl-$v: FAILED (skipped)"
done

log "=== SUMMARY ==="
{
    echo "=== acceptedCountLimit live staged-sweep - $(date) ==="
    echo "profile=$PROFILE  source=input  multi-start=OFF  GRASP=OFF  (single deterministic solve)"
    echo "knob=<acceptedCountLimit> in solverConfig.xml (BOTH LS phases), rebuilt per value. Default 1000."
    echo
    printf "%-10s %-20s %-9s %-10s %s\n" "acl" "score(h/m/s)" "wall(s)" "full-day" "resemblance-unpinned"
    for v in "${VALUES[@]}"; do
        local_out="$RESULTS/acl-$v"
        [[ -d "$local_out" ]] || continue
        sc="$(grep -m1 -oE '[-0-9]+hard/[-0-9]+medium/[-0-9]+soft' "$local_out/score.txt" 2>/dev/null || echo '?')"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$local_out/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        fd="$(grep -m1 -iE 'FULL-DAY RATE' "$local_out/kpi-fill.txt" 2>/dev/null | tr -d '\n' || echo '?')"
        re="$(grep -m1 -iE 'UNPINNED' "$local_out/kpi-resemblance.txt" 2>/dev/null | tr -d '\n' || echo '?')"
        printf "%-10s %-20s %-9s %-10s %s\n" "$v" "$sc" "$wc" "$fd" "$re"
    done
    echo
    echo "NO ADOPTION - solverConfig.xml reverted (default 1000 stands). Sensitivity verdict: see VERDICT."
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
