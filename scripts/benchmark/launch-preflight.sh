#!/bin/bash
#
# launch-preflight.sh - the STANDALONE gate a cycle must pass BEFORE its JVM starts
# (TASK-579, maintainer 2/09: "girato con codice vecchio, lanciato flags inesistenti, e
# ogni altro genere di cazzata: costruisci quello che lo impedisce").
#
# Every guard here is a REAL incident of the 26/08..02/09 week; run-measure.sh has
# the same soul but is a full measurement wrapper - cycles launched by mandate or
# via ssh bypassed it entirely. This script is the lightweight gate those paths can
# afford: one command, verdict lines FIRST, exit 1 on any failure.
#
#   1. STALE APP-JAR (26/08 series-E confound, 02/09 05:50 cycle without
#      SurplusHarvest.class): fast-jar layout makes quarkus-run.jar a manifest-only
#      stub; the CLASSES live in the inner planner jar (app/ here, lib/main/ in
#      older layouts). Guard: that inner jar must be NEWER than HEAD's commit time,
#      and every --classes entry must be present in it. "mvn test" compiles
#      target/classes but does NOT rebuild quarkus-app - passing tests prove
#      NOTHING about the jar.
#   2. NONEXISTENT FLAGS (MAVEN_OPTS lesson, typo'd -Dplanner.* names): a flag the
#      code never reads fails SILENTLY - the run measures the default. Guard: every
#      -D<name> passed on the command line must exist as a literal in the sources
#      (java or resources: quarkus.* lives in application.properties). A typo'd
#      flag that shares a prefix with a real one gets the nearest real names.
#   3. MISSING BANNER (--expect-banner, post-launch): the banner is the ONLY proof
#      the running code consumed the flag (the command line proves nothing).
#      Waits up to --timeout seconds for the regex in the log, fails loud otherwise.
#      NOTE: the banner proves ARRIVAL, not persistence (the F7-A case: a mid-cycle
#      disarm passes it; persistence is the tests' job).
#   4. HOST BATTERY (Darwin; the 31/08-1/09 1% night): WARN when not on AC or low.
#
# Usage:
#   ./scripts/benchmark/launch-preflight.sh [--flags " -Dplanner.x=true ..."]
#        [--classes "SurplusHarvest,EmptyDayFinisher"]
#        [--expect-banner 'surplus-harvest=true'] [--log /tmp/planner-prod.log]
#        [--timeout 90] [--jar-ok]
#   Run WITHOUT --expect-banner BEFORE the launch (jar+flags); run a second time
#   WITH it right AFTER (banner). Every verdict is one greppable line:
#        PREFLIGHT-OK ...   /   PREFLIGHT-FAIL <reason>   (WARNs print BEFORE the OK)
#
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

FLAGS=""
CLASSES=""
EXPECT_BANNER=""
LOG="/tmp/planner-prod.log"
TIMEOUT=90
JAR_OK=0

need_value() { [[ $# -ge 2 ]] || { echo "PREFLIGHT-FAIL option $1 needs a value" >&2; exit 2; } }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --flags) need_value "$@"; FLAGS="$2"; shift ;;
    --classes) need_value "$@"; CLASSES="$2"; shift ;;
    --expect-banner) need_value "$@"; EXPECT_BANNER="$2"; shift ;;
    --log) need_value "$@"; LOG="$2"; shift ;;
    --timeout) need_value "$@"; TIMEOUT="$2"; shift ;;
    --jar-ok) JAR_OK=1 ;;
    -h|--help) sed -n '2,/^set /p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

fail() { echo "PREFLIGHT-FAIL $*"; exit 1; }

# ── 1. STALE APP-JAR ─────────────────────────────────────────────────────────
# quarkus-run.jar is a manifest-only stub in fast-jar layout: age the INNER app
# jar. Its location varies by Quarkus layout (app/ in this tree, lib/main/ in
# older ones): probe both, NEWEST wins, TRUSTING NEITHER from memory (this script
# itself first shipped with the wrong glob - same disease it exists to cure).
APPJAR="$(ls -t target/quarkus-app/app/planner-*.jar target/quarkus-app/lib/main/planner-*.jar 2>/dev/null | head -1)"
[[ -n "$APPJAR" ]] || fail "app-jar missing (target/quarkus-app/app/ or lib/main/ planner-*.jar): build first (mvn clean package or ./scripts/core/rebuild.sh)"
# stat dialects, assigned separately so a GNU-side -f failure can never CONCATENATE
# with the fallback output (that would silently disable this guard: review I3)
JAR_MT="$(stat -f %m "$APPJAR" 2>/dev/null)" || JAR_MT=""
[[ -z "$JAR_MT" ]] && JAR_MT="$(stat -c %Y "$APPJAR" 2>/dev/null)" || true
[[ "$JAR_MT" =~ ^[0-9]+$ ]] || fail "cannot stat mtime of $APPJAR"
HEAD_MT=$(git log -1 --format=%ct 2>/dev/null || echo 0)
JAR_WHEN="$(date -r "$JAR_MT" '+%F %T' 2>/dev/null || date -d "@$JAR_MT" '+%F %T')"
if [[ "$JAR_MT" -lt "$HEAD_MT" && "$JAR_OK" == 0 ]]; then
  fail "STALE APP-JAR: $APPJAR ($JAR_WHEN) predates HEAD $(git log -1 --format='%h %ci'). 'mvn test' does NOT rebuild quarkus-app: rebuild (./scripts/core/rebuild.sh) or pass --jar-ok to accept the risk explicitly."
fi

if [[ -n "$CLASSES" ]]; then
  # the listing is retried: right after a rebuild the first unzip read has twice
  # returned an empty/partial listing on a jar that is complete and closed (the
  # manual re-run passes): not a rebuild race we want to diagnose at 3am
  MISSING=""
  for attempt in 1 2 3; do
    MISSING=""
    IFS=',' read -ra CS <<< "$CLASSES"
    for c in "${CS[@]}"; do
      c="$(echo "$c" | xargs)"   # trim
      [[ -n "$c" ]] || continue
      unzip -l "$APPJAR" 2>/dev/null | grep -qF "${c}.class" || MISSING="$MISSING $c"
    done
    [[ -z "$MISSING" ]] && break
    [[ $attempt == 3 ]] || sleep 2
  done
  [[ -z "$MISSING" ]] || fail "CLASSES NOT IN THE JAR (the running code lacks them):$MISSING. Rebuild before launching; passing tests prove nothing about quarkus-app."
fi

# ── 2. FLAG EXISTENCE ─────────────────────────────────────────────────────────
# Every -D<name> on the launch line must exist as a literal in the sources (java
# for planner.*, resources for quarkus.*). A flag the code never reads fails
# silently and the run measures the default.
if [[ -n "$FLAGS" ]]; then
  BAD=""
  for tok in $FLAGS; do
    case "$tok" in
      -D*)
        name="${tok#-D}"; name="${name%%=*}"
        [[ "$name" == planner.* || "$name" == quarkus.* ]] || continue
        grep -rqF "\"$name\"" src/main/java src/main/resources 2>/dev/null \
          || grep -rqF "$name" src/main/resources 2>/dev/null \
          || BAD="$BAD $name"
        ;;
    esac
  done
  if [[ -n "$BAD" ]]; then
    HINTS=""
    for n in $BAD; do
      stem="${n%.*}"
      NEAR="$(grep -rhoE "\"${stem}[.-][a-zA-Z0-9.-]+\"" src/main/java --include='*.java' | sort -u | head -3 | tr '\n' ' ')"
      HINTS="$HINTS  $n  (nearest real: ${NEAR:-NONE - the prefix does not exist at all})"
    done
    fail "NONEXISTENT FLAGS (the code never reads them; the run would measure the default):$HINTS"
  fi
fi

# ── 3. BANNER WAIT (post-launch proof the flags reached the JVM) ──────────────
if [[ -n "$EXPECT_BANNER" ]]; then
  [[ -f "$LOG" ]] || fail "log $LOG not found: no banner to check (wrong log path?)"
  waited=0
  until grep -qE "$EXPECT_BANNER" "$LOG" 2>/dev/null; do
    sleep 3; waited=$((waited+3))
    [[ $waited -ge $TIMEOUT ]] && fail "BANNER MISSING after ${TIMEOUT}s in $LOG: /$EXPECT_BANNER/. The flag never reached the running code (stale jar? MAVEN_OPTS? typo?): DO NOT trust this run."
  done
fi

# ── 4. HOST BATTERY (Darwin; WARNs print BEFORE the OK line: one verdict last) ─
if [[ "$(uname)" == "Darwin" ]] && command -v pmset >/dev/null 2>&1; then
  BATT="$(pmset -g batt 2>/dev/null || true)"
  if [[ -n "$BATT" ]] && ! echo "$BATT" | grep -q "AC Power"; then
    echo "PREFLIGHT-WARN not on AC Power: $(echo "$BATT" | head -1)"
  fi
  PCT="$(echo "$BATT" | grep -oE "[0-9]+%" | head -1 | tr -d '%' || true)"
  if [[ "${PCT:-100}" =~ ^[0-9]+$ ]] && [[ "${PCT}" -lt 20 ]]; then
    echo "PREFLIGHT-WARN battery at ${PCT}%: cycles die mid-run (the 1%-night precedent)."
  fi
fi

echo "PREFLIGHT-OK appjar=$JAR_WHEN head=$(git log -1 --format='%h %ci')${CLASSES:+ classes=[$CLASSES]}${FLAGS:+ flags-verified}${EXPECT_BANNER:+ banner=/$EXPECT_BANNER/ seen}"
exit 0
