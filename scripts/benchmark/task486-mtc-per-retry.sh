#!/usr/bin/env bash
#
# task486-mtc-per-retry.sh - TASK-486 A/B: does arming M-TC-per-retry cost anything on a GOOD basin?
#
# CONTEXT. The flat M-TC ratification was REJECTED (2026-08-03): even inert on a good basin, M-TC in
# the constraint set doubles the wall-clock (goodseed OFF 1230s vs ON 2435s, VERDICT-goodseed). The
# per-retry prototype (this task) arms M-TC only on multi-start attempts >=2: attempt 1 stays M-TC
# off, so a good basin that stops at attempt 1 should pay NO 2x. This A/B measures that claim.
#
# TWO runs, SAME deterministic good basin (GRASP off = attempt 1, source=input). Multi-start is ON
# on BOTH (the feature requires it; DEFAULT ON since TASK-483). Only -Dplanner.multistart.mtc-per-retry
# .enabled varies:
#   control:  OFF (today's production: the wrapper never touches planner.tag-capacity.enabled).
#   feature:  ON  (attempt 1 forces planner.tag-capacity.enabled=false; a retry would arm it true).
# Good basin = the deterministic baseline is 0-hard, so the loop stops at attempt 1 (no retry, M-TC
# never armed true during the solve).
#
# LOAD-BEARING SIGNAL = wall-clock. M-TC in the constraint set is the ~2x signature (VERDICT-goodseed);
# if the feature run is ~control wall (NOT ~2x), M-TC was off during attempt 1 and the good basin paid
# nothing. The banner checks confirm arming + that M-TC read enabled=false during the solve. Absence
# of the "restoring best attempt" log confirms no retry fired (1 attempt).
#
# Prerequisite: the `multistart` profile set up (source=input pre-assign persists; DB volume up).
# Usage: ./scripts/benchmark/task486-mtc-per-retry.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="multistart"
BASE="claudedocs/spalmone-2026-27/task486-results/ab-mtc-per-retry-goodseed"
BOOT_LOG="/tmp/planner-task486-perretry-boot.log"
SRC_INPUT="-Dplanner.pre-assign.constrained-teacher.source=input"
# Multi-start stays DEFAULT ON on both runs (do NOT pass -Dplanner.multistart.enabled=false): the
# feature is subordinate to multi-start, so this is the configuration that actually exercises it.
SOLVE_TIMEOUT=5400   # 90 min cap (a good-basin staged cycle is ~20 min)
WAIT_BOOT=420

log() { echo "[task486-perretry $(date +%H:%M:%S)] $*" | tee -a "$BASE.log"; }
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

# One run: label, java_opts, expected M-TC-PER-RETRY armed banner value.
run_one() {
    local label="$1" java_opts="$2" want_armed="$3"
    local out="$BASE-$label"
    mkdir -p "$out"
    log "--- RUN $label : opts='$java_opts' (expect per-retry armed=$want_armed) ---"
    local t0="$SECONDS"
    boot_prod "$java_opts" || { log "RUN $label: boot FAILED"; return 1; }
    ./scripts/api/api.sh status >/dev/null 2>&1 || true
    sleep 2
    # Verify the per-retry banner reached the app JVM (MAVEN_OPTS lesson). enabled=false is expected
    # on BOTH runs at attempt 1 (control never touches the flag; feature forces it false at attempt 1).
    if ! grep -q "M-TC per-retry: armed=$want_armed" "$BOOT_LOG"; then
        log "!!! RUN $label VOID: per-retry banner not armed=$want_armed (flag did not reach JVM). Aborting."
        grep -E "M-TC per-retry|M-TC lab tag-capacity|Multi-start staged retry" "$BOOT_LOG" | tee -a "$BASE.log" || true
        ./planner.sh kill-app; return 1
    fi
    log "RUN $label: VERIFIED per-retry armed=$want_armed."
    grep -E "M-TC per-retry|Multi-start staged retry" "$BOOT_LOG" | tail -2 | tee -a "$BASE.log" || true
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged start FAILED"; ./planner.sh kill-app; return 1; }
    log "RUN $label: solve-staged started, waiting (cap ${SOLVE_TIMEOUT}s)..."
    if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then log "RUN $label: wait TIMEOUT/FAILED"; fi
    local elapsed=$(( SECONDS - t0 ))
    ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    # Post-solve forensic: did a retry fire? The restore log only appears when the loop retried and
    # restored the best attempt. Absence => good basin, exactly 1 attempt, M-TC never armed true.
    if grep -q "restoring best attempt" "$BOOT_LOG"; then
        echo "retry_fired=yes" >> "$out/score.txt"
        log "RUN $label: RETRY FIRED (see boot log) - NOT a clean good basin!"
    else
        echo "retry_fired=no" >> "$out/score.txt"
        log "RUN $label: no retry (good basin, 1 attempt)."
    fi
    # Did M-TC read enabled=true at any refreshSolverStatics during the solve? (Should be no on both:
    # control never sets it; feature forces false at attempt 1 and the loop never reached >=2.)
    # `|| true` not `|| echo 0`: grep -c already prints "0" on no-match (then exits 1); `|| echo 0`
    # would append a SECOND "0", yielding "0\n0". `|| true` just suppresses the nonzero exit.
    local mtc_true_during=$(grep -c "M-TC lab tag-capacity: enabled=true" "$BOOT_LOG" 2>/dev/null || true)
    echo "mtc_enabled_true_refreshes=$mtc_true_during" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

mkdir -p "$(dirname "$BASE")"
: > "$BASE.log"
log "=== TASK-486 M-TC-per-retry good-basin A/B (multi-start ON, source=input, only per-retry varies) ==="
run_one "control-off" "$SRC_INPUT" "false" || log "control run failed"
run_one "feature-on"  "$SRC_INPUT -Dplanner.multistart.mtc-per-retry.enabled=true" "true" || log "feature run failed"
log "=== A/B done. control: $(grep -m1 hard "$BASE-control-off/score.txt" 2>/dev/null) | feature: $(grep -m1 hard "$BASE-feature-on/score.txt" 2>/dev/null) ==="
log "control wall: $(grep '^wall_clock_seconds' "$BASE-control-off/score.txt" 2>/dev/null) | feature wall: $(grep '^wall_clock_seconds' "$BASE-feature-on/score.txt" 2>/dev/null)"
