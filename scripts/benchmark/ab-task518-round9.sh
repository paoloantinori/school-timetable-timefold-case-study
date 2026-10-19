#!/usr/bin/env bash
# ROUND 9 (TASK-518 follow-up): treatment del round 8 (chain + budget-reservation
# col gate teacher-wide) su N seed GRASP diversi. DOMANDA del mandato: -3hard e'
# pavimento strutturale della cura o singolo-run lucky?
# Per ogni seed: baseline-golden.sh (profilo fresco, import, pre-assign, solve-staged)
# con PLANNER_JAVA_OPTS = chain + reservation + grasp abilitato col seed N.
# Capture in <OUT>/ab-on-run9-seed<N>. Wrap in caffeinate -is.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task467-results/ab-chain-2026-08-16}"
SEEDS="${SEEDS:-1 2 3}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done-run9"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

CHAIN_FLAGS="-Dplanner.pre-assign.labrotation.derive-from-golden.enabled=true -Dplanner.pre-assign.rotation-enumerator.enabled=true"
RES_FLAGS="-Dplanner.pre-assign.budget-reservation.enabled=true"

say "ROUND 9 START (HEAD=$(git rev-parse --short HEAD), seeds=$SEEDS)"

for s in $SEEDS; do
    say "=== run9 seed $s (chain + reservation r8 + grasp seed $s) ==="
    # The ratified multi-start wrapper (TASK-483) forces attempt 1 deterministic
    # and reseeds retries with seed=attempt, so a plain -Dgrasp.seed=N is
    # NORMALIZED AWAY (proved live: banner grasp=false, jcmd shows the property
    # reset). The honest vehicle for a per-RUN construction seed is the
    # force-grasp-seed hook (ScheduleService.applyGraspSeedForAttempt), which
    # puts seed N on every attempt of this run.
    PROFILE=task518-r9s$s OUT="$OUT/ab-on-run9-seed$s" \
      PLANNER_JAVA_OPTS="$CHAIN_FLAGS $RES_FLAGS -Dplanner.multistart.test.force-grasp-seed=$s" \
      bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
    say "run9 seed $s DONE: $(cat "$OUT/ab-on-run9-seed$s/DONE" 2>/dev/null || echo '(no marker)')"
done

say "ROUND 9 COMPLETE (seeds=$SEEDS)"
echo "DONE $(ts)" > "$DONE_MARKER"
