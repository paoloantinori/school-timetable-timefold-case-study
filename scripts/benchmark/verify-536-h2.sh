#!/usr/bin/env bash
# TASK-536 (c): verification run for the H2 delivery-refinement fix + the
# findDisplayRoomFor pseudo-null birth fix. Reproduces the h33b combined package
# (floor-aware-tail ON + maxAttempts 8 + portfolio 2) on a FRESH dedicated
# profile and expects: 0hard end-to-end (H2 0 at delivery), H32 11->0/quasi,
# H33 0, H17 3 (the known structural cells), TASK-535 sanity from the DB as the
# first read (capture-run.sh runs it).
#
# Launch vehicle: prod-start-and-wait.sh (app detached via nohup, survives the
# session; PLANNER_JAVA_OPTS puts the -D flags on the java -jar line per
# CLAUDE.md's live-solve rule). Polling is quiescence-aware on THREE signals:
# NOT_SOLVING + stagedStage null + refiningForDelivery false (TASK-509: the
# first two fire ~2 min into a ~10 min delivery refinement).
# Background this script; poll <OUT>/DONE and <OUT>/driver.log.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
PROFILE="${PROFILE:-h36-verify}"
OUT="${OUT:-claudedocs/spalmone-2026-27/task536-results/h36-verify}"
mkdir -p "$OUT"
LOG="$OUT/driver.log"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "TASK-536 verify START; profile=$PROFILE out=$OUT"
say "package: floor-aware-tail ON, maxAttempts 8, portfolio 2 (h33b replica), source=input (default)"

# 1. stop anything running; fresh profile. The DB stop matters: prod-start-and-wait
#    reuses a running planner-pg container AS-IS (old profile's volume), so it must
#    be down for step 2 to mount THIS profile's fresh volume.
./planner.sh kill-app >>"$LOG" 2>&1 || true
./planner.sh stop >>"$LOG" 2>&1 || true
yes | ./planner.sh delete "$PROFILE" >>"$LOG" 2>&1 || true

# 2. launch app in prod mode with the h33b flags on the java -jar line
#    (PROFILE env flows into prod-start-and-wait's DB volume + app datasource)
export PLANNER_JAVA_OPTS="-Dplanner.construction.floor-aware-tail.enabled=true -Dplanner.multistart.max-attempts=8 -Dplanner.portfolio.size=2"
say "launching app (prod, profile=$PROFILE, flags: $PLANNER_JAVA_OPTS)"
if ! PROFILE="$PROFILE" ./scripts/core/prod-start-and-wait.sh 600 >>"$LOG" 2>&1; then
  say "ERROR: app not ready; tail of /tmp/planner-prod.log:"; tail -40 /tmp/planner-prod.log >>"$LOG" 2>/dev/null
  echo "ERROR not-ready" > "$OUT/DONE"; exit 1
fi
say "app ready"

# 3. import-calendario + pre-assign
say "import-calendario"
./scripts/api/import-calendario.sh >>"$LOG" 2>&1 || say "WARN: import-calendario non-zero (continuing)"
say "pre-assign"
./scripts/api/api.sh pre-assign -t 120 >>"$LOG" 2>&1 || say "WARN: pre-assign non-zero (continuing)"
./scripts/api/api.sh wait 300 >>"$LOG" 2>&1 || true
./scripts/api/api.sh pre-assign-status >>"$LOG" 2>&1 || true

# 4. solve-staged (multi-start up to 8, portfolio 2: long; budget up to ~3h)
say "solve-staged"
./scripts/api/api.sh solve-staged -t 120 >>"$LOG" 2>&1 || say "WARN: solve-staged non-zero"

# 5. poll until TRUE quiescence: NOT_SOLVING + stagedStage null + refiningForDelivery false,
#    stable x2. Up to 240 min (maxAttempts 8 + portfolio 2 worst case).
say "polling (done = NOT_SOLVING + stagedStage null + refiningForDelivery false, stable x2)"
stable=0
for i in $(seq 1 240); do
  SJ="$(./scripts/api/api.sh status -j 2>/dev/null || true)"
  SST="$(printf '%s' "$SJ" | jq -r '.solverStatus // "UNKNOWN"' 2>/dev/null || echo UNKNOWN)"
  SSG="$(printf '%s' "$SJ" | jq -r '.stagedStage // "null"' 2>/dev/null || echo null)"
  RFD="$(printf '%s' "$SJ" | jq -r '.refiningForDelivery // false' 2>/dev/null || echo false)"
  SAT="$(printf '%s' "$SJ" | jq -r '.stagedAttempt // 0' 2>/dev/null || echo 0)"
  if [ "$((i % 10))" -eq 0 ]; then say "poll min ~$i: $SST stage=$SSG attempt=$SAT refining=$RFD"; fi
  if [ "$SST" = "NOT_SOLVING" ] && { [ "$SSG" = "null" ] || [ -z "$SSG" ]; } && [ "$RFD" = "false" ]; then
    stable=$((stable+1))
    if [ "$stable" -ge 2 ]; then say "solve FINISHED after ~$i min"; break; fi
  else
    stable=0
  fi
  sleep 60
done

# 6. canonical capture (score, analysis, export, lessons.txt, KPI battery incl.
#    the TASK-535 sanity gate as first read, confronto HTML)
say "capture-run.sh -> $OUT"
./scripts/benchmark/capture-run.sh "$OUT" >>"$LOG" 2>&1 || say "WARN: capture-run non-zero"

# 7. final score + DONE
SCORE="$(./scripts/api/api.sh score 2>/dev/null | tr '\n' ' ')"
say "DONE score=$SCORE"
printf 'DONE %s\n' "$SCORE" > "$OUT/DONE"
