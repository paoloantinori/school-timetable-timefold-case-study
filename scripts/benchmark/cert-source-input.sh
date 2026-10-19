#!/usr/bin/env bash
# Certification solve: source=input, all ratified flags ON (their defaults).
# Launches the app in PRODUCTION mode (java -jar) with
#   -Dplanner.pre-assign.constrained-teacher.source=input
# on the java -jar command line via PLANNER_JAVA_OPTS (per CLAUDE.md: a -D for a
# LIVE SOLVE must be on the java -jar line, not MAVEN_OPTS).
# Full cycle: fresh profile -> import-calendario -> pre-assign -> solve-staged
# (multi-start + M-TC per-retry + repair are default ON) -> wait -> capture-run.sh
# -> DONE marker. Designed to be backgrounded; poll <OUT>/DONE and <OUT>/cert-driver.log.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
PROFILE="${PROFILE:-cert}"
OUT="${OUT:-claudedocs/spalmone-2026-27/cert-2026-08-05}"
mkdir -p "$OUT"
LOG="$OUT/cert-driver.log"
DONE_MARKER="$OUT/DONE"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "cert-source-input START; profile=$PROFILE out=$OUT"

# 1. stop any running app; fresh profile (fresh seed = no stale pins)
./planner.sh kill-app >>"$LOG" 2>&1 || true
yes | ./planner.sh delete "$PROFILE" >>"$LOG" 2>&1 || true

# 2. launch app PROD with source=input (backgrounded inside this script).
#    Not using --fresh: planner.sh's `prod` dispatch passes only the first arg to
#    cmd_prod, so `prod --fresh <profile>` is broken. The pre-delete above
#    guarantees a fresh seed on the cert volume.
say "launching app (prod, source=input, $PROFILE; fresh via pre-delete)"
PLANNER_JAVA_OPTS="-Dplanner.pre-assign.constrained-teacher.source=input" \
  nohup ./planner.sh prod "$PROFILE" > "$OUT/app.log" 2>&1 &
say "app launch PID=$!"

# 3. wait for readiness (manual /q/health poll, up to 600s for fresh-seed startup;
#    planner.sh wait's APP_READY_TIMEOUT global is not arg-controllable)
say "waiting for app ready (up to 600s)"
ready=0
for r in $(seq 1 120); do
  if curl -s -m 3 http://localhost:8080/q/health >/dev/null 2>&1; then ready=1; break; fi
  sleep 5
done
if [ "$ready" -eq 1 ]; then
  say "app ready"
else
  say "ERROR: app not ready within 600s; tail app.log:"; tail -40 "$OUT/app.log" >>"$LOG"
  echo "ERROR not-ready" > "$DONE_MARKER"; exit 1
fi

# 4. record the statics banner (proves source=input + which flags are live)
./scripts/api/api.sh status -j >>"$LOG" 2>&1 || true

# 5. import-calendario (pins stage + MODA lab hours the school committed to)
say "import-calendario"
./scripts/api/import-calendario.sh >>"$LOG" 2>&1 || say "WARN: import-calendario non-zero (continuing)"

# 6. pre-assign (constrained-teacher reads source=input here) + wait for COMPLETE
say "pre-assign"
./scripts/api/api.sh pre-assign -t 120 >>"$LOG" 2>&1 || say "WARN: pre-assign post non-zero (continuing)"
./scripts/api/api.sh wait 300 >>"$LOG" 2>&1 || true
say "pre-assign-status:"; ./scripts/api/api.sh pre-assign-status >>"$LOG" 2>&1 || true

# 7. solve-staged (multi-start, M-TC per-retry, repair passes all DEFAULT ON)
say "solve-staged (source=input, multi-start ON, M-TC per-retry ON, repair ON)"
./scripts/api/api.sh solve-staged -t 120 >>"$LOG" 2>&1 || say "WARN: solve-staged post non-zero"

# 8. poll until the staged cycle rests: solverStatus=NOT_SOLVING AND stagedStage=null
#    (stable for 2 consecutive checks). Up to ~150 min for a bad-seed 3x multi-start.
say "polling solve (done = NOT_SOLVING + stagedStage null, stable x2)"
stable=0
for i in $(seq 1 150); do
  SJ="$(./scripts/api/api.sh status -j 2>/dev/null || true)"
  SST="$(printf '%s' "$SJ" | jq -r '.solverStatus // "UNKNOWN"' 2>/dev/null || echo UNKNOWN)"
  SSG="$(printf '%s' "$SJ" | jq -r '.stagedStage // "null"' 2>/dev/null || echo null)"
  # TASK-509 follow-up: delivery refinement keeps the two old signals quiet
  RFD="$(printf '%s' "$SJ" | jq -r '.refiningForDelivery // false' 2>/dev/null || echo false)"
  SAT="$(printf '%s' "$SJ" | jq -r '.stagedAttempt // 0' 2>/dev/null || echo 0)"
  SMX="$(printf '%s' "$SJ" | jq -r '.stagedMaxAttempts // 0' 2>/dev/null || echo 0)"
  if [ "$((i % 5))" -eq 0 ]; then say "poll min ~$i: solverStatus=$SST stagedStage=$SSG attempt=$SAT/$SMX"; fi
  if [ "$SST" = "NOT_SOLVING" ] && { [ "$SSG" = "null" ] || [ -z "$SSG" ]; } && [ "$RFD" != "true" ]; then
    stable=$((stable+1))
    if [ "$stable" -ge 2 ]; then say "solve FINISHED after ~$i min"; break; fi
  else
    stable=0
  fi
  sleep 60
done

# 9. capture the canonical KPI battery
say "capture-run.sh -> $OUT"
./scripts/benchmark/capture-run.sh "$OUT" >>"$LOG" 2>&1 || say "WARN: capture-run non-zero"

# 10. final score + DONE marker
SCORE="$(./scripts/api/api.sh score 2>/dev/null | tr '\n' ' ')"
say "DONE score=$SCORE"
printf 'DONE %s\n' "$SCORE" > "$DONE_MARKER"
