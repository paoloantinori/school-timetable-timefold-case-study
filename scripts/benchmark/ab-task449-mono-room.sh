#!/usr/bin/env bash
# TASK-449 live staged A/B: mono-room-tag allocator OFF (control) vs ON (treatment).
#
# The missing experiment (TASK-449 Final Summary: "Adozione = maintainer dopo A/B staged che
# misuri la reale riduzione room-null"; AC#3 guards-live never run). Runs the ratified
# staged cycle source=golden twice, back-to-back, within the SAME machine/session
# (laptop-RUNBOOK ground rule: within-machine comparability only), flipping only the
# TASK-449 flag via PLANNER_JAVA_OPTS on the java -jar line (live-read via Boolean.getBoolean,
# same mechanism as the constrained-teacher pin). No code change, no worktree needed.
#
# Arm OFF: ratified baseline (449 default OFF). Arm ON: -Dplanner.pre-assign.mono-room-tag.
# enabled=true. Each arm = baseline-golden.sh (fresh profile -> import -> pre-assign ->
# solve-staged -> capture). Compare after: multi-start attempts, medium room-tier (M-TC/M37),
# B-ROOM progression (phases.json), KPI battery, and the Phase 0b pin log on the ON arm.
#
# Note on the metric: final H29 room-null is 0 for BOTH arms (both reach 0hard via
# multi-start), so the differentiating signal is attempts-to-0hard + medium + B-ROOM entry
# state, not final H29. The TASK-449 caveat (Final Summary) is that the allocator alone does
# not close the acconciatura capacity bottleneck, so expect PARTIAL benefit at best.
#
# Backgroundable; wrap in `caffeinate -is`. Poll <OUT>/ab-done + <OUT>/ab-driver.log.
# Usage: caffeinate -is bash ./scripts/benchmark/ab-task449-mono-room.sh
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
OUT="${OUT:-claudedocs/spalmone-2026-27/task449-results}"
mkdir -p "$OUT"
LOG="$OUT/ab-driver.log"
DONE_MARKER="$OUT/ab-done"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

# EXTRA_OPTS (env, default empty) is appended to BOTH arms' PLANNER_JAVA_OPTS, so an A/B can
# flip the source too (e.g. source=input, where the room-tier saturation 449 targets lives).
# TREATMENT_FLAG (env, default the 449 flag) is the flag the ON arm flips, so the same harness
# tests any live-read construction/pre-assign prototype (e.g. TASK-470 theory-spread).
EXTRA_OPTS="${EXTRA_OPTS:-}"
TREATMENT_FLAG="${TREATMENT_FLAG:--Dplanner.pre-assign.mono-room-tag.enabled=true}"
SRC_LABEL="${SRC_LABEL:-golden}"
say "TASK-449 A/B START (source=$SRC_LABEL, treatment=$TREATMENT_FLAG, within-session, host=$(uname -srm), EXTRA_OPTS='$EXTRA_OPTS')"

# ARM 1: OFF (control, ratified default)
say "=== ARM off (control, treatment OFF default) ==="
PROFILE=task449-off OUT="$OUT/ab-off-run1" PLANNER_JAVA_OPTS="$EXTRA_OPTS" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM off DONE: $(cat "$OUT/ab-off-run1/DONE" 2>/dev/null || echo '(no DONE marker)')"

# ARM 2: ON (treatment flag ON)
say "=== ARM on (treatment ON: $TREATMENT_FLAG) ==="
PROFILE=task449-on OUT="$OUT/ab-on-run1" \
  PLANNER_JAVA_OPTS="$TREATMENT_FLAG $EXTRA_OPTS" \
  bash ./scripts/benchmark/baseline-golden.sh >>"$LOG" 2>&1 || true
say "ARM on DONE: $(cat "$OUT/ab-on-run1/DONE" 2>/dev/null || echo '(no DONE marker)')"

say "TASK-449 A/B COMPLETE - confrontare ab-off-run1 vs ab-on-run1"
echo "DONE" > "$DONE_MARKER"
