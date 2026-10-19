#!/usr/bin/env bash
# WORK 2: room-tag sensitivity to school answers. Runs RoomPhaseSnapshotTest (export mode,
# all groups, 90s phase-5 LS) for each candidate school-answer and tees the residual
# REAL room-null (the 189 H29 target). In-harness, read-only on the cert export, no
# protected files. Results -> /tmp/room-tag-sensitivity.txt.
#
# Baseline (no override) already validated: REAL room-null 189 (7 non-lab + 182 lab),
# phase-5 LS fills 0 (no free room). Each case below applies an override BEFORE the LS
# and measures how many of the 189 the LS then closes.
set -u
EXPORT="claudedocs/spalmone-2026-27/task447-results/cert/export.json.gz"
OUT="/tmp/room-tag-sensitivity.txt"
: > "$OUT"

run_case() {
  local label="$1"; shift
  echo "=== CASE: $label ===" | tee -a "$OUT"
  PLANNER_TEST_ROOM_PHASE=1 \
  PLANNER_TEST_ROOM_PHASE_EXPORT="$EXPORT" \
  PLANNER_TEST_ROOM_PHASE_GROUPS="" \
  PLANNER_TEST_ROOM_PHASE_BUDGET=90 \
  env "$@" ./scripts/core/test.sh -c RoomPhaseSnapshotTest 2>&1 \
    | grep -E "FASE3 override|non-lab REAL|LAB REAL|rooms after|score:" | tee -a "$OUT"
  echo "" | tee -a "$OUT"
}

run_case "servizio stilistico -> aula (de-tag)"            PLANNER_TEST_ROOM_TAG_UNTAG="servizio stilistico"
run_case "taglio metalli -> aula (de-tag)"                 PLANNER_TEST_ROOM_TAG_UNTAG="taglio metalli"
run_case "R17 cap real (24, saldatura course)"        PLANNER_TEST_ROOM_R17_CAP=24
run_case "combo A: serv.stil + taglio de-tag"              PLANNER_TEST_ROOM_TAG_UNTAG="servizio stilistico,taglio metalli"
echo "=== DONE ===" | tee -a "$OUT"
