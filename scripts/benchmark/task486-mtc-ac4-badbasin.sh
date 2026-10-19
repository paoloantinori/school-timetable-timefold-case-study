#!/usr/bin/env bash
#
# task486-mtc-ac4-badbasin.sh - TASK-486 AC#4: does the per-retry M-TC cure a bad basin IN VIVO?
#
# The good-basin A/B (task486-mtc-per-retry.sh) confirmed arming the feature is zero-cost, but the
# retry never fired on that seed (attempt 1 deterministic = 0-hard, loop stops at 1). AC#4 is the
# open in-vivo question: on a basin where attempt 1 FAILS (residual hard), does the retry (attempt
# >=2) arm M-TC and deliver the deterministic cure to 0-hard?
#
# WHY A HOOK (not -Dgrasp.seed=6). The wrapper forces attempt 1 deterministic (applyGraspSeedForAttempt
# sets grasp.enabled=false at attempt 1), so a plain -Dplanner.construction.grasp.seed=6 is overridden
# and never reaches attempt 1 -> attempt 1 stays the 0-hard good basin -> no retry. To put a bad basin
# on attempt 1 we force EVERY attempt to seed 6 via the planner.multistart.test.force-grasp-seed hook:
# attempt 1 and 2 are then the SAME seed-6 construction, and M-TC (off at 1, on at 2) is the only
# variable. Attempt 1 is the within-run CONTROL, attempt 2 the CURE. Null-default hook (zero behaviour
# change in production).
#
# SELF-CONTROLLED A/B (one run). attempt 1 = seed-6 + M-TC off (expected -1hard, the meccanica
# room-null of VERDICT-ab-mtc-seed6). attempt 2 = seed-6 + M-TC on (expected 0hard cure). The loop
# stops at attempt 2 if it hits 0-hard. Forensics: per-attempt score log ("staged multi-start attempt
# N done: mtc=X, score=Y"), retry_fired (a 2nd attempt ran), mtc_enabled_true_refreshes>0 (M-TC read
# enabled=true during the retry), final score.
#
# Prerequisite: the `multistart` profile set up (source=input pre-assign persists; DB volume up).
# Usage: ./scripts/benchmark/task486-mtc-ac4-badbasin.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="multistart"
BASE="claudedocs/spalmone-2026-27/task486-results/ab-mtc-ac4-badbasin"
BOOT_LOG="/tmp/planner-task486-ac4-boot.log"
SRC_INPUT="-Dplanner.pre-assign.constrained-teacher.source=input"
# multi-start is DEFAULT ON. Arm the per-retry feature AND force seed 6 on every attempt.
OPTS="$SRC_INPUT -Dplanner.multistart.mtc-per-retry.enabled=true -Dplanner.multistart.test.force-grasp-seed=6"
SOLVE_TIMEOUT=7200   # 120 min cap: 2 attempts x ~20-25 min each
WAIT_BOOT=420

log() { echo "[task486-ac4 $(date +%H:%M:%S)] $*" | tee -a "$BASE.log"; }
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

mkdir -p "$(dirname "$BASE")"
: > "$BASE.log"
log "=== TASK-486 AC#4 bad-basin in-vivo (force-grasp-seed=6, mtc-per-retry ON, source=input) ==="
out="$BASE"
mkdir -p "$out"
t0="$SECONDS"
boot_prod "$OPTS" || { log "boot FAILED"; exit 1; }
./scripts/api/api.sh status >/dev/null 2>&1 || true
sleep 2
# Verify the per-retry banner reached the JVM (MAVEN_OPTS lesson).
if ! grep -q "M-TC per-retry: armed=true" "$BOOT_LOG"; then
    log "!!! VOID: per-retry banner not armed=true (flag did not reach JVM). Aborting."
    grep -E "M-TC per-retry|Multi-start staged retry" "$BOOT_LOG" | tee -a "$BASE.log" || true
    ./planner.sh kill-app; exit 1
fi
log "VERIFIED per-retry armed=true."
grep -E "M-TC per-retry|Multi-start staged retry" "$BOOT_LOG" | tail -2 | tee -a "$BASE.log" || true
./scripts/api/api.sh solve-staged || { log "solve-staged start FAILED"; ./planner.sh kill-app; exit 1; }
log "solve-staged started, waiting (cap ${SOLVE_TIMEOUT}s for up to 2 attempts)..."
if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then log "wait TIMEOUT/FAILED"; fi
elapsed=$(( SECONDS - t0 ))
./scripts/benchmark/capture-run.sh "$out" || log "capture FAILED"
echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"

# Forensics.
# 1. Per-attempt outcome lines (the within-run control vs cure).
echo "--- per-attempt outcomes (mtc state + score) ---" >> "$out/score.txt"
grep "staged multi-start attempt.*done: mtc=" "$BOOT_LOG" >> "$out/score.txt" 2>/dev/null || echo "(no per-attempt lines found)" >> "$out/score.txt"
# 2. retry_fired = a 2nd attempt ran.
# `|| true` not `|| echo 0`: grep -c already prints "0" on no-match (then exits 1); `|| echo 0`
# would append a SECOND "0", yielding "0\n0". `|| true` just suppresses the nonzero exit.
attempts_run=$(grep -c "staged multi-start attempt.*done: mtc=" "$BOOT_LOG" 2>/dev/null || true)
echo "attempts_run=$attempts_run" >> "$out/score.txt"
# 3. mtc_enabled_true_refreshes: M-TC read enabled=true at any refresh (the retry's M-TC-on signature).
mtc_true=$(grep -c "M-TC lab tag-capacity: enabled=true" "$BOOT_LOG" 2>/dev/null || true)
echo "mtc_enabled_true_refreshes=$mtc_true" >> "$out/score.txt"
# 4. Did the loop restore a best attempt (only when best != last attempt)?
if grep -q "restoring best attempt" "$BOOT_LOG"; then
    echo "restore_best=yes" >> "$out/score.txt"
else
    echo "restore_best=no" >> "$out/score.txt"
fi

./planner.sh kill-app
log "done in ${elapsed}s. attempts_run=$attempts_run mtc_true=$mtc_true"
log "final score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
log "per-attempt lines:"
grep "staged multi-start attempt.*done: mtc=" "$BOOT_LOG" | tee -a "$BASE.log" 2>/dev/null || true
