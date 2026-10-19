#!/bin/bash
#
# run-measure.sh - the MEASUREMENT HARNESS (TASK-542, maintainer 24/08: "un harness che
# impedisca gli errori di processo e li colga sul nascere").
#
# Every process incident of the 23-24/08 campaign was a LAUNCH-PROTOCOL error, not a
# code error. This wrapper makes each of them impossible or loud:
#
#   1. STALE JAR      - a run launched on a jar older than the code being measured
#                       (12:39 24/08: banner absent, run invalid). Preflight: refuse if
#                       quarkus-run.jar is older than HEAD's commit time, unless --jar-ok.
#   2. DIRTY PROFILE  - a relaunch inheriting a killed app's mutated state (dci-546a:
#                       construction diverged 1498 placements). Default: FRESH profile
#                       (delete + recreate); reuse only with an explicit --reuse.
#   3. SHARED LOGS    - /tmp/planner-prod.log is overwritten by every prod-start (the
#                       -6 morning log was lost this way). The harness tees the app log
#                       into the measure dir, unique per run.
#   4. MANIFEST-LESS MEASURES - verdicts written without capture, or runs without a
#                       written prediction. The manifest is written BEFORE launch and
#                       capture-run refuses to close without it (see finalize).
#   5. MISSING BANNERS - a flag that never reached the JVM is discovered mid-run
#                       (MAVEN_OPTS lesson). Declare expected banners with --expect;
#                       finalize fails loud if any is absent.
#
# Usage:
#   run-measure.sh <measure-dir> [--flags " -Da=.. -Db=.."] [--expect "BANNER1,BANNER2"]
#                  [--profile NAME] [--reuse] [--jar-ok] [--phase-assert]
#   run-measure.sh <measure-dir> --finalize   # after delivery: lock artifacts, assert
#                                             # banners, sanity; refuses without manifest
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

DIR=""
FLAGS=""
EXPECT=""
PROFILE=""
CLASSES=""
REUSE=0
JAR_OK=0
PHASE_ASSERT=0
FINALIZE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --finalize) FINALIZE=1 ;;
    --flags) FLAGS="$2"; shift ;;
    --expect) EXPECT="$2"; shift ;;
    --profile) PROFILE="$2"; shift ;;
    --reuse) REUSE=1 ;;
    --jar-ok) JAR_OK=1 ;;
    --classes) CLASSES="$2"; shift ;;
    --phase-assert) PHASE_ASSERT=1 ;;
    -h|--help) sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) if [[ -z "$DIR" && "$1" != -* ]]; then DIR="$1"; else echo "unknown arg: $1" >&2; exit 2; fi ;;
  esac
  shift
done
[[ -n "$DIR" ]] || { echo "usage: run-measure.sh <measure-dir> [options|--finalize]" >&2; exit 2; }
mkdir -p "$DIR"
MANIFEST="$DIR/run-manifest.json"
JAR="target/quarkus-app/quarkus-run.jar"

fail() { echo "RUN-MEASURE PREFLIGHT FAIL: $*" >&2; exit 1; }

if [[ "$FINALIZE" == 1 ]]; then
  [[ -f "$MANIFEST" ]] || fail "no manifest: nothing to finalize (measure launched outside the harness?)"
  APPLOG="$DIR/app.log"
  [[ -f "$APPLOG" ]] || fail "no per-run app log captured"
  # banner assertions: every --expect token must appear in the run's own log
  if [[ -n "${EXPECT:-}" ]]; then
    IFS=',' read -ra TOKENS <<< "$EXPECT"
    for t in "${TOKENS[@]}"; do
      grep -q "$t" "$APPLOG" || fail "expected banner MISSING: '$t' (flag lost? wrong jar?)"
    done
    echo "RUN-MEASURE: all expected banners present"
  fi
  # canonical capture + sanity (refuses nothing, but records everything)
  ./scripts/benchmark/capture-run.sh "$DIR"
  # TASK-579/579.01: the capture must pass its own doctor before anyone reads a
  # number. A DOCTOR-FAIL (corrupt export, hard-VECTOR divergence on either
  # coordinate) fails the CHAIN too: finalization below preserves the artifacts,
  # but the harness must not report green over a capture nobody may quote.
  DOCTOR_EXIT=0
  if [[ -x ./scripts/benchmark/capture-doctor.sh ]]; then
    ./scripts/benchmark/capture-doctor.sh "$DIR" || DOCTOR_EXIT=$?
  fi
  echo "RUN-MEASURE: finalized. Verdict material in $DIR"
  if [[ "$DOCTOR_EXIT" != 0 ]]; then
    echo "RUN-MEASURE: CAPTURE-DOCTOR-FAILED (exit $DOCTOR_EXIT): the numbers in $DIR must NOT be quoted."
    exit "$DOCTOR_EXIT"
  fi
  exit 0
fi

# ── preflight ──
[[ -f "$JAR" ]] || fail "jar missing: build first"
# TASK-579: route through the standalone gate (flag-existence + inner-jar class
# check + battery): the harness must not age its own checks out of sync
if [[ -x ./scripts/benchmark/launch-preflight.sh ]]; then
  ./scripts/benchmark/launch-preflight.sh --flags "$FLAGS" --classes "${CLASSES:-}"     $( [[ "$JAR_OK" == 1 ]] && echo --jar-ok ) || fail "launch-preflight refused (see its line above)"
fi
# TASK-579: the stale-jar verdict now comes from launch-preflight, which ages the
# INNER app jar (quarkus-run.jar is a manifest-only stub); here we only record it
# the lib/main glob is a legacy layout fallback: with pipefail a missing match kills
# the assignment under set -e (laptop rebuilds put planner jars only in app/), hence || true
APPJAR="$(ls -t target/quarkus-app/app/planner-*.jar target/quarkus-app/lib/main/planner-*.jar 2>/dev/null | head -1)" || true
[[ -n "$APPJAR" ]] || fail "inner app jar missing: build first"
JAR_MT=$(stat -f %m "$APPJAR" 2>/dev/null || stat -c %Y "$APPJAR")
GIT_SHA=$(git rev-parse --short HEAD)
PROFILE="${PROFILE:-measure-$GIT_SHA-$$}"
# fresh profile by default: the dci-546a incident was a relaunch on a mutated profile
if [[ "$REUSE" == 0 ]]; then
  ./planner.sh kill-app >/dev/null 2>&1 || true
  ./planner.sh stop >/dev/null 2>&1 || true
  yes | ./planner.sh delete "$PROFILE" >/dev/null 2>&1 || true
fi
# manifest BEFORE launch: no measure without its provenance
cat > "$MANIFEST" <<EOF
{
  "gitSha": "$GIT_SHA",
  "jarMtime": "$JAR_MT",
  "appJar": "$APPJAR",
  "profile": "$PROFILE",
  "flags": "$FLAGS",
  "phaseAssert": $PHASE_ASSERT,
  "expectedBanners": "$EXPECT",
  "launchedAt": "$(date '+%F %T')",
  "prediction": "REQUIRED: write it in the iterations log BEFORE --finalize"
}
EOF
echo "RUN-MEASURE: manifest written -> $MANIFEST"

# ── launch: prod-start with per-run log capture ──
ENVV="PROFILE=$PROFILE"
[[ "$PHASE_ASSERT" == 1 ]] && ENVV="$ENVV QUARKUS_TIMEFOLD_SOLVER_ENVIRONMENT_MODE=phase-assert"
[[ -n "$FLAGS" ]] && ENVV="$ENVV PLANNER_JAVA_OPTS='$FLAGS'"
# the app's own /tmp/planner-prod.log stays shared (prod-start hardcodes it): tail-copy
# it into the per-run log on finalize is racy, so START the capture now and stop it in
# finalize via the marker file
echo "$PROFILE" > "$DIR/.profile"
eval env "$ENVV" ./scripts/core/prod-start-and-wait.sh 300
cp /tmp/planner-prod.log "$DIR/app.log"
echo "RUN-MEASURE: launched on profile $PROFILE (git $GIT_SHA). Solve with: ./scripts/api/api.sh solve-staged"
echo "RUN-MEASURE: RIGHT AFTER launch, prove the flags reached the JVM: ./scripts/benchmark/launch-preflight.sh --jar-ok --expect-banner '<banner>' --log $DIR/app.log"
echo "RUN-MEASURE: when delivered -> $0 $DIR --finalize ${EXPECT:+--expect '$EXPECT'}"
