#!/usr/bin/env bash
#
# task455-mtc-goodseed.sh - TASK-455 ratification scout: good-seed A/B, ONE variable M-TC.
#
# The seed-6 A/B showed M-TC+exemption cures the meccanica room-null (0-hard) at a real -94k
# medium cost (displacement). The OPEN QUESTION for maintainer's ratification: is that -94k the cost of
# the CURE (only on bad seeds) or a FIXED LS perturbation (also on good seeds)? This A/B answers it.
#
# Two runs, SAME deterministic good basin (GRASP off = the production path, multi-start OFF so a
# single solve, source=input). Only -Dplanner.tag-capacity.enabled varies:
#   OFF: M-TC off  (= today's production path, the 0-hard good basin).
#   ON:  M-TC on + TASK-485 exemption (already in main).
# Good seed = deterministic (baseline-grasp-off is 0-hard); multi-start is forced OFF on both so
# the retry logic is not in the picture (single deterministic solve each, M-TC is the only variable).
#
# Banner verified per run (MAVEN_OPTS lesson: -D reaches the app JVM only on the java -jar line).
# No weight sweep (M-TR lesson). Output: task455-results/ab-mtc-goodseed-{off,on}/.
#
# Prerequisite: the `multistart` profile set up (source=input pre-assign persists); DB volume up.
# Usage: ./scripts/benchmark/task455-mtc-goodseed.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="multistart"
BASE="claudedocs/spalmone-2026-27/task455-results/ab-mtc-goodseed"
BOOT_LOG="/tmp/planner-task455-goodseed-boot.log"
SRC_INPUT="-Dplanner.pre-assign.constrained-teacher.source=input"
# Multi-start is DEFAULT ON since the 2026-08-03 ratification; force it OFF on both runs so each is
# a single deterministic solve (GRASP off) and M-TC is the only variable in this A/B.
NO_MULTISTART="-Dplanner.multistart.enabled=false"
SOLVE_TIMEOUT=5400
WAIT_BOOT=420

log() { echo "[task455-goodseed $(date +%H:%M:%S)] $*" | tee -a "$BASE.log"; }
cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

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
            log "boot: prod DIED (see $BOOT_LOG)"; tail -15 "$BOOT_LOG" | tee -a "$BASE.log"; return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -15 "$BOOT_LOG" | tee -a "$BASE.log"; return 1
}

# One run: label, java_opts, expected M-TC banner value. Verifies the banner matches before solve.
run_one() {
    local label="$1" java_opts="$2" want_mtc="$3"
    local out="$BASE-$label"
    mkdir -p "$out"
    log "--- RUN $label : opts='$java_opts' (expect M-TC $want_mtc) ---"
    local t0="$SECONDS"
    boot_prod "$java_opts" || { log "RUN $label: boot FAILED"; return 1; }
    ./scripts/api/api.sh status >/dev/null 2>&1 || true
    sleep 2
    if ! grep -q "M-TC lab tag-capacity: enabled=$want_mtc" "$BOOT_LOG"; then
        log "!!! RUN $label VOID: M-TC banner not enabled=$want_mtc (flag did not reach JVM). Aborting."
        grep "M-TC lab tag-capacity" "$BOOT_LOG" | tee -a "$BASE.log" || true
        ./planner.sh kill-app; return 1
    fi
    log "RUN $label: VERIFIED M-TC enabled=$want_mtc."
    grep -E "M-TC lab tag-capacity|Construction/repair flags" "$BOOT_LOG" | tail -1 | tee -a "$BASE.log" || true
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged start FAILED"; ./planner.sh kill-app; return 1; }
    log "RUN $label: solve-staged started, waiting (cap ${SOLVE_TIMEOUT}s)..."
    if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then log "RUN $label: wait TIMEOUT/FAILED"; fi
    local elapsed=$(( SECONDS - t0 ))
    ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

: > "$BASE.log"
log "=== TASK-455 good-seed A/B (deterministic, multi-start OFF, only M-TC varies) ==="
run_one "off" "$SRC_INPUT $NO_MULTISTART" "false" || log "OFF run failed"
run_one "on"  "$SRC_INPUT $NO_MULTISTART -Dplanner.tag-capacity.enabled=true" "true" || log "ON run failed"
log "=== A/B done. OFF: $(grep -m1 hard "$BASE-off/score.txt" 2>/dev/null) | ON: $(grep -m1 hard "$BASE-on/score.txt" 2>/dev/null) ==="
