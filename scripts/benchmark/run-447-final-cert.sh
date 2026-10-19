#!/usr/bin/env bash
# TASK-447 FASE E - final certification run (one staged solve, fresh profile,
# constrained-teacher flag ON via PLANNER_JAVA_OPTS on the java -jar line).
#
# Measurement for the ratification of the 435+446+M37+H28+TASK-447(H29/H0 reweight)
# package + the FASE C de-tag; NOT the adoption itself (default-flag-flip = maintainer).
#
# Runs ON THE laptop (~90min for the staged solve). Captures to
# claudedocs/spalmone-2026-27/task447-results/cert/. Invoke:
#   bash scripts/benchmark/run-447-final-cert.sh
# (self-contained: backgrounds the prod app, polls pre-assign + solve-staged to
# completion via api.sh wait, then runs the canonical capture.)
set -euo pipefail
cd "$(dirname "$0")/../.."

PROFILE=production
OUT=claudedocs/spalmone-2026-27/task447-results/cert
# Constrained-teacher flag (TASK-435) ON for the cert, on the java -jar line only
# (CLAUDE.md MAVEN_OPTS lesson: a -D read via Boolean.getBoolean reaches the app
# JVM only on the java -jar command line). Default in code is still OFF (adoption
# = maintainer); this measures the adopted-package behavior without flipping the default.
export PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.enabled=true"

echo "=== TASK-447 FASE E cert: fresh $PROFILE, flag ON via PLANNER_JAVA_OPTS ==="
mkdir -p "$OUT"

# 1. Fresh profile (re-seed with the FASE C de-tag in DataGenerator).
./planner.sh kill-app || true
./planner.sh stop || true
yes | ./planner.sh delete "$PROFILE" || true

# 2. Start app PROD (java -jar, fresh JAR with FASE A/B/C) + the flag; wait ready.
./scripts/core/prod-start-and-wait.sh 180

# 2b. Verify the flag reached the app JVM (refreshSolverStatics banner).
APP_LOG=$(ls -t /tmp/planner-prod-*.log 2>/dev/null | head -1 || true)
if [ -n "$APP_LOG" ] && grep -q 'constrained-teacher=true' "$APP_LOG"; then
  echo "=== flag OK: constrained-teacher=true (banner in $APP_LOG) ==="
else
  echo "=== WARN: constrained-teacher=true banner not found in $APP_LOG (verify manually) ==="
fi

# 3. import-calendario (golden MODA + Stage pins, off-grid hours). NOTE: verify-baseline.sh's
# thresholds are STALE post-TASK-434 (it expects stage=1800 / off-grid=280 for "7 stage-eligible
# groups", but TASK-434 added the 8th group G04, so the seed now yields stage~1993 / off-grid~327).
# FASE C does not touch stage pins (CalendarioImport-driven, not getRoomTags). Run the gate as a
# non-fatal signal; the import JSON printed above is the ground truth.
./scripts/api/import-calendario.sh
./scripts/benchmark/verify-baseline.sh import || \
  echo "=== WARN: verify-baseline gate stale post-TASK-434 (stage 1800->1993, offgrid 280->327); import JSON above is ground truth, continuing ==="

# 4. pre-assign (Phase 0 constrained-teacher pins, then CSP labs/prof). reset first.
./scripts/api/api.sh pre-assign-reset || true
./scripts/api/api.sh pre-assign -t 60
./scripts/api/api.sh wait 1800   # pre-assign with Phase 0 (constrained-teacher) is slow (>900s observed); allow 30 min

# 5. staged solve (the campaign reference mode), poll to completion (~90 min).
./scripts/api/api.sh solve-staged -t 60
./scripts/api/api.sh wait 7200   # 2h ceiling for the staged cycle

# 6. canonical capture (score/analysis/phases/export/lessons dump/full KPI battery/golden-vs-run viz).
./scripts/benchmark/capture-run.sh "$OUT"

echo "=== TASK-447 FASE E cert captured to $OUT ==="
./scripts/api/api.sh score || true
echo "=== score.txt: ==="
cat "$OUT/score.txt" 2>/dev/null || true
