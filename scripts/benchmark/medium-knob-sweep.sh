#!/usr/bin/env bash
#
# medium-knob-sweep.sh - GENERIC medium-constraint weight/numerator sensitivity sweep.
#
# Generalizes m32-sweep.sh: sweep ANY single -D medium knob (planner.m32.numerator,
# planner.m34.weight, planner.m35.weight, ...) as the only variable across N values,
# same deterministic seed (source=input, multi-start OFF, GRASP OFF), fresh JVM per
# value (the knobs are static-final in SchoolSchedule[ConstraintProvider], read once
# at class-init). Captures + tabulates per-run score, the swept constraint's own
# medium/matches (to separate self-penalty from real schedule change), and KPIs.
#
# Read-only measurement. NO ADOPTION (caller's default stands).
#
# MAVEN_OPTS lesson (CLAUDE.md): the -D rides the java -jar line via PLANNER_JAVA_OPTS
# (planner.sh prod puts it there), NOT MAVEN_OPTS. Verification:
#   - ALWAYS verify the -D mechanism arrived: grep the "Multi-start staged retry:
#     enabled=false" + "grasp=false" banners (they confirm PLANNER_JAVA_OPTS reached the
#     app, so the swept knob riding the same opts arrived too). Abort the run if missing.
#   - IF VALUE_BANNER is set (a regex with a %V placeholder, e.g. M32's "M32 ... knobs:
#     numerator=%V"), ALSO verify the knob's exact value logged. For knobs with no value
#     banner (M34/M35), the mechanism check + a post-solve constraint-medium-differs
#     check (in the summary) confirm the value took effect.
#
# Config (env vars):
#   KNOB           required, the -D property, e.g. planner.m34.weight
#   VALUES         required, space-separated, e.g. "20 100 300"
#   CONSTRAINT_ID  required for the analysis extract, e.g. M34 (grep tag in analysis.json)
#   VALUE_BANNER   optional, regex with %V placeholder for per-value verification
#   LABEL_TAG      optional, output subdir tag, e.g. "weight" (default "value")
#   RESULTS_DIR    optional, output root (default claudedocs/spalmone-2026-27/<knob>-sweep-results)
#
# Usage: KNOB=planner.m34.weight VALUES="20 100 300" CONSTRAINT_ID=M34 \
#        ./scripts/benchmark/medium-knob-sweep.sh [profile] [--no-setup]
#   profile   DB profile (default: m32-sweep, reuse its source=input pins)
#   --no-setup  skip fresh setup (assume profile already has seed+calendario+source=input pins)
#
# Modeled on scripts/benchmark/multistart-ab.sh and m32-sweep.sh (same boot/run_one/capture
# shape). m32-sweep.sh is retained as the committed driver that produced the M32 results.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

KNOB="${KNOB:?KNOB required, e.g. planner.m34.weight}"
read -r -a VALUES <<< "${VALUES:?VALUES required, e.g. \"20 100 300\"}"
CONSTRAINT_ID="${CONSTRAINT_ID:?CONSTRAINT_ID required, e.g. M34}"
VALUE_BANNER="${VALUE_BANNER:-}"
LABEL_TAG="${LABEL_TAG:-value}"

PROFILE="${1:-m32-sweep}"
NO_SETUP=0
for a in "${@:2}"; do
    case "$a" in
        --no-setup) NO_SETUP=1 ;;
        *) echo "unknown arg: $a" >&2; exit 1 ;;
    esac
done

# Strip the "planner." namespace prefix for a clean output-dir tag (m34.weight, not
# planner.m34.weight; basename alone keeps the dots since there is no "/").
KNOB_SHORT="${KNOB#planner.}"
RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/${KNOB_SHORT}-sweep-results}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MULTISTART_OFF="-Dplanner.multistart.enabled=false"
SOLVE_TIMEOUT=5400
WAIT_BOOT=420
BOOT_LOG="/tmp/planner-medium-knob-boot.log"

log() { echo "[${KNOB_SHORT}-sweep $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }
cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

boot_prod() {
    local fresh="$1" java_opts="$2"
    if [[ "$fresh" == "fresh" ]]; then
        log "boot: fresh reset (stop DB + delete volume '$PROFILE')"
        ./planner.sh stop >/dev/null 2>&1 || true
        yes | ./planner.sh delete "$PROFILE" >/dev/null 2>&1 || true
    fi
    rm -f "$BOOT_LOG"
    PLANNER_JAVA_OPTS="$java_opts" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
    local pid=$!
    for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
        if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
            log "boot: ready (pid $pid, opts='$java_opts')"
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            log "boot: prod process DIED (see $BOOT_LOG)"
            tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
            return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s (see $BOOT_LOG)"
    tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
    ./planner.sh kill-app >/dev/null 2>&1 || true
    return 1
}

# Verify the -D mechanism reached the app. The multi-start-OFF + grasp-OFF banners are
# logged by refreshSolverStatics at solve start; their presence proves PLANNER_JAVA_OPTS
# rode the java -jar line (so the swept knob, in the same opts string, arrived too).
# If VALUE_BANNER (%V placeholder) is set, also confirm the knob's exact value logged.
verify_knob() {
    local expected="$1"
    local ms="" gp=""
    for _ in 1 2 3 4 5; do
        ms="$(grep -aoE "Multi-start staged retry: enabled=[a-z]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        gp="$(grep -aoE "grasp=[a-z]+ seed=[0-9-]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        [[ -n "$ms" && -n "$gp" ]] && break
        sleep 3
    done
    if [[ -z "$ms" || -z "$gp" ]]; then
        log "verify_knob: -D mechanism banners NOT found - PLANNER_JAVA_OPTS did not reach the app. ABORT run."
        return 1
    fi
    log "verify_knob: mechanism OK - $ms; $gp"
    if [[ -n "$VALUE_BANNER" ]]; then
        local regex="${VALUE_BANNER//%V/$expected}"
        local banner=""
        for _ in 1 2 3 4 5; do
            banner="$(grep -aoE "$regex" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
            [[ -n "$banner" ]] && break
            sleep 3
        done
        if [[ -z "$banner" ]]; then
            log "verify_knob: value banner not found for regex '$regex'. ABORT run."
            return 1
        fi
        log "verify_knob: value OK - $banner"
    else
        log "verify_knob: no VALUE_BANNER; mechanism check is the gate (post-solve ${CONSTRAINT_ID} medium-differs confirms effect)."
    fi
    return 0
}

setup_profile() {
    log "=== SETUP: rebuild JAR (--build-only) ==="
    ./scripts/core/rebuild.sh --build-only
    log "=== SETUP: fresh profile '$PROFILE' + boot (source=input) ==="
    boot_prod "fresh" "$SRC_INPUT_FLAG" || { log "SETUP: boot FAILED"; exit 1; }
    log "=== SETUP: import-calendario ==="
    ./scripts/api/import-calendario.sh
    log "=== SETUP: pre-assign (source=input). Poll COMPLETE. ==="
    ./scripts/api/api.sh pre-assign
    local st=""
    for i in {1..120}; do
        st="$(./scripts/api/api.sh pre-assign-status 2>/dev/null | grep -oiE 'COMPLETE|ERROR|RUNNING|IDLE' | head -1 || true)"
        [[ "$st" == "COMPLETE" ]] && break
        [[ "$st" == "ERROR" ]] && { log "SETUP: pre-assign ERROR"; exit 1; }
        sleep 5
    done
    log "SETUP: pre-assign $st"
    log "=== SETUP: kill-app (profile ready: seed + calendario + source=input pins) ==="
    ./planner.sh kill-app
}

run_one() {
    local value="$1"
    local label="${LABEL_TAG}-${value}"
    local out="$RESULTS/$label"
    # EXTRA_FLAGS (optional): companion -D flags every run needs, e.g. an enable flag
    # like "-Dplanner.tag-capacity.enabled=true" for the M-TC prototype (otherwise inert).
    local opts="$SRC_INPUT_FLAG $MULTISTART_OFF ${EXTRA_FLAGS:-} -D${KNOB}=${value}"
    log "--- RUN $label : opts='$opts' ---"
    local t0="$SECONDS"
    boot_prod "" "$opts" || { log "RUN $label: boot FAILED"; return 1; }
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged start FAILED"; ./planner.sh kill-app; return 1; }
    if ! verify_knob "$value"; then
        ./planner.sh kill-app
        return 1
    fi
    log "RUN $label: knob verified, waiting for solve (cap ${SOLVE_TIMEOUT}s)..."
    if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then
        log "RUN $label: wait returned non-zero (often a benign fetch hiccup ~3s after the last phase ended; capture will confirm)"
    fi
    local elapsed=$(( SECONDS - t0 ))
    PLANNER_LOG="$BOOT_LOG" ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

# Extract ${CONSTRAINT_ID} medium + matches from a run's analysis.json.
constraint_of() {
    python3 -c "
import json,sys
try:
    d=json.load(open('$1/analysis.json'))
    cs=d.get('constraints',[])
    m=[c for c in cs if '${CONSTRAINT_ID} -' in c.get('constraint','') or c.get('constraint','').startswith('${CONSTRAINT_ID} ')]
    if m: print(f\"medium={m[0]['medium']} matches={m[0].get('matches','?')}\")
    else: print('?')
except Exception as e: print('?')
" 2>/dev/null
}
kpi_of() { local file="$1" pattern="$2"; grep -m1 -iE "$pattern" "$file" 2>/dev/null | tr -d '\n' || echo "?"; }

if (( NO_SETUP == 1 )); then
    log "=== SKIPPING setup (--no-setup): profile '$PROFILE' assumed ready (seed + calendario + source=input pins) ==="
else
    setup_profile
fi

for v in "${VALUES[@]}"; do
    run_one "$v" || log "RUN ${LABEL_TAG}-$v: FAILED (skipped)"
done

log "=== SUMMARY ==="
{
    echo "=== ${KNOB} sweep - $(date) ==="
    echo "profile=$PROFILE  source=input  multi-start=OFF  GRASP=OFF  (single deterministic solve)"
    echo "knob=${KNOB}  values=${VALUES[*]}  constraint_id=${CONSTRAINT_ID}"
    echo
    printf "%-10s %-20s %-9s %-10s %s   %s\n" "value" "score(h/m/s)" "wall(s)" "full-day" "resemblance-unpinned" "${CONSTRAINT_ID}(med/matches)"
    for v in "${VALUES[@]}"; do
        rout="$RESULTS/${LABEL_TAG}-$v"
        [[ -d "$rout" ]] || continue
        sc="$(grep -m1 -oE '[-0-9]+hard/[-0-9]+medium/[-0-9]+soft' "$rout/score.txt" 2>/dev/null || echo '?')"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$rout/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        fd="$(kpi_of "$rout/kpi-fill.txt" 'FULL-DAY RATE')"
        re="$(kpi_of "$rout/kpi-resemblance.txt" 'UNPINNED')"
        co="$(constraint_of "$rout")"
        printf "%-10s %-20s %-9s %-10s %s   %s\n" "$v" "$sc" "$wc" "$fd" "$re" "$co"
    done
    echo
    echo "Decision read: if ${CONSTRAINT_ID} matches is ~invariant across values but its medium scales, the"
    echo "movement is self-penalty (flat knob, M37/M32 outcome). If matches/KPIs move, it is tunable."
    echo "Raw artifacts: $RESULTS/${LABEL_TAG}-*/  NO ADOPTION."
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
