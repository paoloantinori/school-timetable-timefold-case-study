#!/usr/bin/env bash
# TASK-435 final certification run (maintainer 2026-07-28 OK, via Fedora).
# Measurement for the adoption ratification; NOT the adoption itself.
#
# Runs ON THE laptop (where the full staged solve lives, ~90min). The ONE extra flag
# -Dplanner.pre-assign.constrained-teacher.enabled=true goes on the java -jar line
# via PLANNER_JAVA_OPTS (NOT MAVEN_OPTS - see CLAUDE.md's MAVEN_OPTS lesson: a -D
# read via Boolean.getBoolean reaches the app JVM only on the java -jar command line).
#
# Invoke on the laptop:
#   ~/planner && bash scripts/benchmark/run-435-final-cert.sh
#
# Captures to claudedocs/spalmone-2026-27/m12-results/final-confirm-435.
# Expected all-together (no barter): hard 0, buchi 0, day-start>=99.3, emptySpan 0,
# ricorrenza>=72, full-day>=77. Verdict vs final-confirm (-41hard baseline).
set -euo pipefail
cd "$(dirname "$0")/../.."

PROFILE=production
OUT=claudedocs/spalmone-2026-27/m12-results/final-confirm-435
export PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.enabled=true"

echo "=== TASK-435 final cert: fresh $PROFILE, flag on java -jar ==="

# 1. Fresh profile
./planner.sh kill-app || true
./planner.sh stop || true
yes | ./planner.sh delete "$PROFILE" || true

# 2. Start app PROD (java -jar) with the flag; --fresh reseeds + starts DB + app.
./planner.sh prod --fresh "$PROFILE"
# (prod blocks in the foreground; run it backgrounded + wait for readiness instead:)
#   nohup ./planner.sh prod --fresh "$PROFILE" > /tmp/435-app.log 2>&1 &
#   ./planner.sh wait

echo "=== verify the flag reached the app JVM (banner must show constrained-teacher=true) ==="
# After start, hit the status/health or grep the app log for the refreshSolverStatics banner:
#   grep "constrained-teacher=" /tmp/435-app.log
# Expected: "...constrained-teacher=true"

# 3. import-calendario + gates
./scripts/api/import-calendario.sh
./scripts/benchmark/verify-baseline.sh import   # MUST pass: 866 MODA + 1800 stage + 280 off-grid

# 4. pre-assign (Phase 0 pins T01 231h + T07 72h on his Thursdays/Wednesdays)
./scripts/api/api.sh pre-assign
# poll pre-assign-status until COMPLETE (Phase 0 logs "Phase 0 done: 2 teachers, 303 pinned")

# 5. staged solve (the campaign reference mode, NOT plain solve)
./scripts/api/api.sh solve-staged
# poll api.sh status every ~90s until NOT_SOLVING (~90min)

# 6. capture (score/analysis/phases/export/lessons dump/full KPI battery/golden-vs-run viz)
./scripts/benchmark/capture-run.sh "$OUT"

echo "=== TASK-435 cert captured to $OUT ==="
echo "Verdict vs final-confirm (-41hard): check score (hard 0?), day-start/emptySpan/ricorrenza/full-day KPIs."
