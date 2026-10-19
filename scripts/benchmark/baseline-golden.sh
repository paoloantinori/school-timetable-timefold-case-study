#!/usr/bin/env bash
# Baseline solve on THIS machine: source=golden (DEFAULT), all ratified flags ON.
# Thin sibling of cert-source-input.sh (which is source=input). Purpose: certify a
# host reproduces the ratified good-seed baseline (hard=0 + KPI band) before trusting
# local solve results. Faster than the source=input cert (single attempt, no bad-seed
# multi-start), so it is the quick "is this machine a valid solve host?" check.
#
# Full cycle: fresh profile -> import-calendario -> pre-assign -> solve-staged
# (multi-start + M-TC per-retry + repair all DEFAULT ON, source=golden) -> wait ->
# capture-run.sh -> DONE marker. Designed to be backgrounded; poll <OUT>/DONE and
# <OUT>/driver.log. Wrap in `caffeinate -i` if the host can sleep (laptop).
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
PROFILE="${PROFILE:-macbase}"
OUT="${OUT:-claudedocs/spalmone-2026-27/baseline-laptop-2026-08-08}"
mkdir -p "$OUT"
LOG="$OUT/driver.log"
DONE_MARKER="$OUT/DONE"
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

say "baseline-golden START; host=$(uname -srm) profile=$PROFILE out=$OUT"

# 1. stop any running app; fresh profile (fresh seed = no stale pins)
./planner.sh kill-app >>"$LOG" 2>&1 || true
yes | ./planner.sh delete "$PROFILE" >>"$LOG" 2>&1 || true

# 2. launch app PROD, source=golden (DEFAULT: no source -D needed). PLANNER_JAVA_OPTS is
#    env-respecting so an A/B can flip a live-read flag (e.g. TASK-449
#    -Dplanner.pre-assign.mono-room-tag.enabled=true) without a code change; defaults empty
#    (all ratified flags DEFAULT ON, so empty = the ratified baseline).
say "launching app (prod, source=golden DEFAULT, $PROFILE; PLANNER_JAVA_OPTS='${PLANNER_JAVA_OPTS:-}')"
PLANNER_JAVA_OPTS="${PLANNER_JAVA_OPTS:-}" nohup ./planner.sh prod "$PROFILE" > "$OUT/app.log" 2>&1 &
say "app launch PID=$!"

# 3. wait for readiness (up to 600s for fresh-seed startup)
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

# 4. record the statics banner (proves source=golden + which flags are live)
./scripts/api/api.sh status -j >>"$LOG" 2>&1 || true

# 5. import-calendario (pins stage + MODA lab hours the school committed to)
say "import-calendario"
./scripts/api/import-calendario.sh >>"$LOG" 2>&1 || say "WARN: import-calendario non-zero (continuing)"

# 6. pre-assign (constrained-teacher reads source=golden here) + wait for COMPLETE
say "pre-assign"
./scripts/api/api.sh pre-assign -t 120 >>"$LOG" 2>&1 || say "WARN: pre-assign post non-zero (continuing)"
./scripts/api/api.sh wait 300 >>"$LOG" 2>&1 || true
say "pre-assign-status:"; ./scripts/api/api.sh pre-assign-status >>"$LOG" 2>&1 || true

# 7. solve-staged (multi-start, M-TC per-retry, repair passes all DEFAULT ON)
say "solve-staged (source=golden, multi-start ON, M-TC per-retry ON, repair ON)"
./scripts/api/api.sh solve-staged -t 120 >>"$LOG" 2>&1 || say "WARN: solve-staged post non-zero"

# 8. poll until the staged cycle rests: solverStatus=NOT_SOLVING AND stagedStage=null
#    (stable for 2 consecutive checks). POLL_CAP is env-configurable: the default 150 min
#    fits a good-seed 1-2 attempt golden solve, but a 3-attempt arm (e.g. TASK-449 ON,
#    which degrades the basin) needs ~240. The cap is a SAFETY (a clean finish writes DONE
#    early via the stable-x2 gate); raising it only delays the abort, never the success.
say "polling solve (done = NOT_SOLVING + stagedStage null, stable x2; cap ${POLL_CAP:-150} min)"
stable=0
for i in $(seq 1 "${POLL_CAP:-150}"); do
  SJ="$(./scripts/api/api.sh status -j 2>/dev/null || true)"
  SST="$(printf '%s' "$SJ" | jq -r '.solverStatus // "UNKNOWN"' 2>/dev/null || echo UNKNOWN)"
  SSG="$(printf '%s' "$SJ" | jq -r '.stagedStage // "null"' 2>/dev/null || echo null)"
  # TASK-509 follow-up: the delivery refinement (corner-b) keeps solverStatus NOT_SOLVING
  # and stagedStage null while it works: a third signal, checked BEFORE the rest verdict
  # (run3's treatment was killed ~2 min into a ~10 min refinement).
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
