#!/usr/bin/env bash
#
# run-experiment.sh — Standardized solver experiment runner
#
# Runs ONE full experiment (reset → pre-assign → solve-to-self-termination) on the
# CURRENTLY DEPLOYED solverConfig.xml and writes a machine-readable scorecard.
# All m-3/m-4/m-5/m-6 experiment tasks compare their results against scorecards
# this script produces. See claudedocs/research/critical-assessment-and-roadmap-20260712.md
# sections 2 and 5 for the protocol rationale.
#
# Usage:
#   ./scripts/benchmark/run-experiment.sh [--collect-only] NAME CONFIG_DELTA_DESCRIPTION
#
#   NAME                     Short slug for this run (used in the output filename).
#   CONFIG_DELTA_DESCRIPTION What changed vs the baseline (free text, stored in the scorecard).
#
#   --collect-only           Skip reset/pre-assign/solve and just snapshot the CURRENT
#                            schedule state into a scorecard. Use this to capture the result
#                            of a solve you started separately (e.g. `api.sh solve` run
#                            server-side), so a long solve does not have to hold open a
#                            blocking poll loop. wall_clock_seconds is 0 in this mode
#                            (per-phase timings still come from phase_progression).
#
# Environment:
#   MAX_WAIT_SECONDS   Polling safety ceiling for the solve phase (default 3600 = 1h).
#                      The solver self-terminates via solverConfig.xml; this only caps polling.
#                      For solves expected to exceed the ceiling, start the solve with
#                      `./scripts/api/api.sh solve` and run this script with --collect-only
#                      once solverStatus is NOT_SOLVING.
#
# Output:
#   local/experiments/NAME-YYYYmmdd-HHMMSS.json   scorecard
#   local/experiments/NAME-YYYYmmdd-HHMMSS.log    run log
#
# Rules: only the whitelisted wrapper scripts (api.sh, db-query.sh) are used for
# API/DB access; no background pipe chains; DB column names are camelCase-collapsed.
#
set -euo pipefail

cd "$(dirname "$0")/../.."

MODE="full"
if [[ "${1:-}" == "--collect-only" ]]; then
  MODE="collect-only"
  shift
fi
NAME="${1:?Usage: run-experiment.sh [--collect-only] NAME CONFIG_DELTA_DESCRIPTION}"
DELTA="${2:?Usage: run-experiment.sh [--collect-only] NAME CONFIG_DELTA_DESCRIPTION}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-3600}"

EXP_DIR="local/experiments"
mkdir -p "$EXP_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTFILE="$EXP_DIR/${NAME}-${TIMESTAMP}.json"
LOG="$EXP_DIR/${NAME}-${TIMESTAMP}.log"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG" >&2; }

# ---------------------------------------------------------------------------
# 1. Pre-flight: app + DB must be up
# ---------------------------------------------------------------------------
log "Pre-flight: health check..."
if ! ./scripts/api/api.sh health >/dev/null 2>&1; then
  echo "ERROR: app not healthy. Start the stack with: ./scripts/core/dev-start-and-wait.sh dev" >&2
  exit 1
fi

GIT_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
log "Experiment: $NAME (mode=$MODE)"
log "Config delta: $DELTA"
log "Git commit: $GIT_COMMIT"

if [[ "$MODE" == "full" ]]; then
  START="$(date +%s)"

  # ----- 2. Full reset (NOT pre-assign-reset — that only flips the status enum) -----
  log "Full schedule reset..."
  ./scripts/api/api.sh reset >/dev/null

  # ----- 3. Pre-assignment (pre-assign → poll COMPLETE) -----
  log "Running pre-assignment..."
  ./scripts/api/api.sh pre-assign >/dev/null
  PRE_DEADLINE=$(( $(date +%s) + 600 ))   # 10 min ceiling for pre-assignment
  while :; do
    STATUS="$(./scripts/api/api.sh -j pre-assign-status 2>/dev/null | jq -r '.status // "UNKNOWN"' || echo UNKNOWN)"
    log "  pre-assign status: $STATUS"
    [[ "$STATUS" == "COMPLETE" ]] && break
    if [[ "$STATUS" == "ERROR" ]]; then
      echo "ERROR: pre-assignment failed (status=ERROR)" >&2; exit 1
    fi
    if [[ $(date +%s) -gt $PRE_DEADLINE ]]; then
      echo "ERROR: pre-assignment timed out after 10 min (last status=$STATUS)" >&2; exit 1
    fi
    sleep 2
  done

  # ----- 4. Solve — the solver self-terminates via solverConfig.xml termination -----
  log "Starting solver (self-terminating; polling cap ${MAX_WAIT_SECONDS}s)..."
  ./scripts/api/api.sh solve >/dev/null
  SOLVE_DEADLINE=$(( START + MAX_WAIT_SECONDS ))
  while :; do
    # TASK-419: `status` is the light /api/schedule/status payload. A staged solve keeps the
    # SolverManager idle between its stages, so "finished" is NOT_SOLVING **and** no
    # stagedStage - polling on solverStatus alone would collect a half-promoted grid.
    SJSON="$(./scripts/api/api.sh -j status 2>/dev/null || echo '{}')"
    SSTAT="$(echo "$SJSON" | jq -r '.solverStatus // "UNKNOWN"' 2>/dev/null || echo UNKNOWN)"
    STAGED="$(echo "$SJSON" | jq -r '.stagedStage // ""' 2>/dev/null || echo "")"
    [[ "$SSTAT" == "NOT_SOLVING" && -z "$STAGED" ]] && { log "solver stopped"; break; }
    if [[ $(date +%s) -gt $SOLVE_DEADLINE ]]; then
      log "WARNING: solve polling ceiling reached; collecting current state"
      break
    fi
    sleep 10
  done
  END="$(date +%s)"
  WALL=$(( END - START ))
  log "Wall clock: ${WALL}s"
else
  log "collect-only: snapshotting current schedule state (no reset / pre-assign / solve)"
  WALL=0
fi

# ---------------------------------------------------------------------------
# 5. Collect scorecard fields
# ---------------------------------------------------------------------------
log "Collecting scorecard..."

# 5a. Per-constraint breakdown + score (single api.sh analysis call).
#     analysis returns `score` as a single Timefold string ("{h}hard/{m}medium/{s}soft");
#     hardScore/mediumScore/softScore are NOT separate fields — the string is parsed below.
#     The endpoint can briefly return empty during solution swaps (mid-solve and right
#     when solving stops), so retry until it carries a score.
for _ in 1 2 3 4 5 6; do
  ANALYSIS="$(./scripts/api/api.sh analysis 2>/dev/null || true)"
  printf '%s' "$ANALYSIS" > "$TMP/analysis.json"
  jq -e '.score' "$TMP/analysis.json" >/dev/null 2>&1 && break
  log "  analysis not ready (empty); retrying..."
  sleep 2
done
jq -e . "$TMP/analysis.json" >/dev/null 2>&1 || echo '{}' > "$TMP/analysis.json"

# 5b. Per-phase timings + scores
PHASES="$(./scripts/api/api.sh phase-progression 2>/dev/null || true)"
printf '%s' "${PHASES:-[]}" > "$TMP/phases.json"
jq -e . "$TMP/phases.json" >/dev/null 2>&1 || echo '[]' > "$TMP/phases.json"

# 5c. Unassigned lesson count + avg lessons per active day per student group.
#     timeslot has only startdatetime/enddatetime persisted (date is JPA-computed),
#     so group by CAST(startdatetime AS DATE). -f json => raw tuples-only output;
#     tail -1 skips the SET-transaction line, coalesce guards the empty case.
AVG_SQL="SELECT coalesce(json_object_agg(name, avg_per_day), '{}'::json)
FROM (
  SELECT sg.name, avg(daily.cnt) AS avg_per_day
  FROM (
    SELECT c.studentgroup_id AS gid, CAST(t.startdatetime AS DATE) AS d, count(*) AS cnt
    FROM lesson l
    JOIN course c ON l.course_id = c.id
    JOIN timeslot t ON l.timeslot_id = t.id
    GROUP BY c.studentgroup_id, CAST(t.startdatetime AS DATE)
  ) daily
  JOIN studentgroup sg ON sg.id = daily.gid
  GROUP BY sg.name
) x"
UNASSIGNED="$(./scripts/db/db-query.sh -f json "SELECT count(*) FROM lesson WHERE timeslot_id IS NULL" 2>/dev/null | tail -1 | tr -d '[:space:]')"
AVG="$(./scripts/db/db-query.sh -f json "$AVG_SQL" 2>/dev/null | tail -1)"
[[ "$UNASSIGNED" =~ ^[0-9]+$ ]] || UNASSIGNED=0
[[ -n "$AVG" ]] || AVG='{}'
printf '%s' "$AVG" > "$TMP/avg.json"
jq -e . "$TMP/avg.json" >/dev/null 2>&1 || echo '{}' > "$TMP/avg.json"

# 5d. Assemble the scorecard. Parse the Timefold score string for hard/medium/soft;
#     slurpfile wraps each file in a 1-element array; the capture defaults to
#     0hard/0medium/0soft when the score is null.
jq -n \
  --arg name "$NAME" \
  --arg timestamp "$TIMESTAMP" \
  --arg git "$GIT_COMMIT" \
  --arg delta "$DELTA" \
  --argjson wall "$WALL" \
  --argjson unassigned "$UNASSIGNED" \
  --slurpfile a "$TMP/analysis.json" \
  --slurpfile p "$TMP/phases.json" \
  --slurpfile avg "$TMP/avg.json" \
  '(($a[0].score // "0hard/0medium/0soft")
      | capture("^(?<h>-?[0-9]+)hard/(?<m>-?[0-9]+)medium/(?<sf>-?[0-9]+)soft$")) as $c
   | {
       name: $name,
       timestamp: $timestamp,
       git_commit: $git,
       config_delta: $delta,
       mode: "'"$MODE"'",
       wall_clock_seconds: $wall,
       final_score: ($a[0].score // null),
       hard_score: ($c.h | tonumber),
       medium_score: ($c.m | tonumber),
       soft_score: ($c.sf | tonumber),
       unassigned_lessons: $unassigned,
       avg_lessons_per_day_per_group: ($avg[0] // {}),
       constraint_breakdown: ($a[0].constraints // []),
       phase_progression: ($p[0] // [])
     }' > "$OUTFILE"

log "Scorecard written: $OUTFILE"
echo "$OUTFILE"
