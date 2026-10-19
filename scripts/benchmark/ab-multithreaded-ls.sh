#!/usr/bin/env bash
# A/B: single-thread LS (control) vs MultiThreadedLocalSearchDecider (moveThreadCount=8).
#
# Runs in the worktree (multithreaded-ls-experiment). Uses the same staged cycle
# (source=golden, default config) as the ratified baseline, but the ON arm wires
# MultiThreadedLsWiring.buildMultiThreadedSolver(8) via a system property flag read
# by ScheduleService.
#
# The flag (-Dplanner.multithreaded-ls.enabled=true -Dplanner.multithreaded-ls.threads=8)
# is read LIVE in runStagedSolver: if set, the SolverFactory-built solver's LS deciders
# are swapped with MultiThreadedLocalSearchDecider before solve().
#
# Backgroundable; wrap in caffeinate -is. Poll <OUT>/ab-done.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/multithreaded-ls-ab-2026-08-13}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "MULTI-THREADED LS A/B START (worktree=$(basename $ROOT))"

# ARM 1: OFF (control, single-thread LS = ratified default)
say "=== ARM off (single-thread LS, control) ==="
PROFILE=mtls-off OUT="$OUT/ab-off" bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM off DONE: $(cat "$OUT/ab-off/DONE" 2>/dev/null || echo '(no marker)')"

# ARM 2: ON (multi-threaded LS, 8 threads)
say "=== ARM on (multi-threaded LS, 8 threads) ==="
PROFILE=mtls-on OUT="$OUT/ab-on" \
  PLANNER_JAVA_OPTS="-Dplanner.multithreaded-ls.enabled=true -Dplanner.multithreaded-ls.threads=8" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM on DONE: $(cat "$OUT/ab-on/DONE" 2>/dev/null || echo '(no marker)')"

say "MULTI-THREADED LS A/B COMPLETE"
echo "DONE $(ts)" > "$DONE_MARKER"
