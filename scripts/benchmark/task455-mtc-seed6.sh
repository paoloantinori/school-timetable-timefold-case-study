#!/usr/bin/env bash
#
# task455-mtc-seed6.sh - TASK-455 controlled A/B: re-run the EXACT overnight seed-6 staged
# solve (source=input, GRASP seed=6) with the M-TC lab tag-capacity constraint turned ON,
# against the OFF run already captured (the seed-6 bad basin = H29 room-null of G04
# "Lavorazioni meccaniche" lesson, structural saturation of the 1-room "meccanica" tag).
#
# ONE variable changed vs overnight grasp-seed-6: -Dplanner.tag-capacity.enabled=true.
# seed-6 is deterministic (same GRASP seed -> same construction), so this is a clean A/B.
#
# The M-TC flag MUST reach the app JVM (CLAUDE.md MAVEN_OPTS lesson: -D on the java -jar
# line, not MAVEN_OPTS). We verify it by grepping the boot log for the refreshSolverStatics
# banner "M-TC lab tag-capacity: enabled=true" - if it does not appear, the flag was dropped
# and the run is VOID (we abort before solve-staged, per maintainer's gate).
#
# Prerequisite: the `multistart` profile already set up (rebuild + seed + import-calendario +
# pre-assign source=input) by the overnight batch; the DB volume planner-data-multistart is
# up. Each solve-staged re-plans from scratch (runStagedCycle unassigns unpinned), so no
# reset is needed. This script mirrors multistart-ab.sh's run_one exactly, plus M-TC + banner.
#
# Usage: ./scripts/benchmark/task455-mtc-seed6.sh
# Output: claudedocs/spalmone-2026-27/task455-results/ab-mtc-seed6/ (capture-run.sh artifacts)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="multistart"
# Overridable via RESULTS_DIR so the confirmation run (M-TC + exemption) does not clobber the
# no-exemption baseline (ab-mtc-seed6).
OUT="${RESULTS_DIR:-claudedocs/spalmone-2026-27/task455-results/ab-mtc-seed6}"
mkdir -p "$OUT"
BOOT_LOG="/tmp/planner-task455-mtc-boot.log"

# source=input (the solo-input regime) + GRASP seed=6 (identical to overnight) + M-TC ON.
# M-TC weight stays at its default (1000) - the clean A/B changes only the enabled flag.
JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input \
-Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=6 \
-Dplanner.tag-capacity.enabled=true"

SOLVE_TIMEOUT=5400   # 90 min cap (the cycle is ~30 min)
WAIT_BOOT=420        # 7 min: non-fresh boot still runs startup statics before /q/health 200

log() { echo "[task455-mtc-seed6 $(date +%H:%M:%S)] $*" | tee -a "$OUT/run.log"; }

cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

rm -f "$BOOT_LOG"
log "=== boot prod (profile=$PROFILE, non-fresh) with M-TC ON + GRASP seed=6 ==="
log "JAVA_OPTS: $JAVA_OPTS"
PLANNER_JAVA_OPTS="$JAVA_OPTS" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
BOOT_PID=$!

# Poll readiness (api.sh health, better signal than /q/health on startup statics).
READY=0
for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
    if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
        log "boot: ready (pid $BOOT_PID)"; READY=1; break
    fi
    if ! kill -0 "$BOOT_PID" 2>/dev/null; then
        log "boot: prod process DIED (see $BOOT_LOG)"; tail -20 "$BOOT_LOG" | tee -a "$OUT/run.log"; exit 1
    fi
    sleep 3
done
[[ "$READY" == "1" ]] || { log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -20 "$BOOT_LOG" | tee -a "$OUT/run.log"; exit 1; }

# Trigger refreshSolverStatics (idle status load) so the flag banners land in the boot log,
# then VERIFY M-TC arrived. If the banner is missing the flag was dropped -> run is VOID.
./scripts/api/api.sh status >/dev/null 2>&1 || true
sleep 2
if ! grep -q "M-TC lab tag-capacity: enabled=true" "$BOOT_LOG"; then
    log "!!! VOID: M-TC banner not found with enabled=true -> flag did NOT reach the JVM. Aborting before solve."
    grep "M-TC lab tag-capacity" "$BOOT_LOG" | tee -a "$OUT/run.log" || true
    exit 2
fi
log "VERIFIED: M-TC flag reached the JVM (banner shows enabled=true)."
grep -E "M-TC lab tag-capacity|Construction/repair flags.*grasp" "$BOOT_LOG" | tail -2 | tee -a "$OUT/run.log" || true

# Solve.
log "=== solve-staged (cap ${SOLVE_TIMEOUT}s) ==="
T0="$SECONDS"
./scripts/api/api.sh solve-staged || { log "solve-staged start FAILED"; exit 1; }
if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then
    log "wait: TIMEOUT/FAILED (capturing whatever we have)"
fi
ELAPSED=$(( SECONDS - T0 ))
./scripts/benchmark/capture-run.sh "$OUT" || log "capture FAILED"
echo "wall_clock_seconds=$ELAPSED" >> "$OUT/score.txt"
./planner.sh kill-app
log "=== done in ${ELAPSED}s. score: $(grep -m1 hard "$OUT/score.txt" 2>/dev/null || echo '?') ==="
