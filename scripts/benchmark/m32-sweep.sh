#!/usr/bin/env bash
#
# m32-sweep.sh - M32 template-deviation numerator SENSITIVITY sweep (read-only spike).
#
# Question: is the M32 weight knob (-Dplanner.m32.numerator, default 100000, formula
# clamp(numerator / |VRP cells of the course|, min=20, max=500)) TUNABLE, or FLAT (like
# the M37 weight sweep turned out to be)? Never swept systematically; this is a 3-point
# smoke sweep to see if there is any signal above LS variance.
#
# DESIGN: single variable. Same deterministic good seed for all 3 runs:
#   - GRASP off (default; deterministic construction)
#   - multi-start OFF (-Dplanner.multistart.enabled=false -> single staged cycle)
#   - source=input (-Dplanner.pre-assign.constrained-teacher.source=input)
# Only m32.numerator varies: 100000 (default/baseline), 20000 (low), 300000 (high).
# min=20 / max=500 held constant.
#
# The knob is static-final in SchoolSchedule, read ONCE at class-init, so changing it
# needs a fresh JVM boot per value. solve-staged re-plans from scratch each boot
# (runStagedCycle calls unassignUnpinnedPlacements); the source=input pins set up once
# persist, so the 3 runs differ ONLY in the M32 weight. Clean single-variable comparison.
#
# MAVEN_OPTS LESSON (CLAUDE.md): the -D MUST ride the java -jar line via PLANNER_JAVA_OPTS
# (planner.sh prod puts ${PLANNER_JAVA_OPTS} on the java -jar line), NOT MAVEN_OPTS (which
# stays on the Maven JVM and never reaches the forked app). Each run VERIFIES the knob
# arrived by grepping the app log for "M32 template-deviation weight knobs: numerator=V
# min=20 max=500" right after solve-staged starts, and ABORTS the run if it did not.
#
# NO ADOPTION: the default stays 100000. This is a measurement spike; it changes no code
# and no default.
#
# Usage:
#   ./scripts/benchmark/m32-sweep.sh [profile] [--no-setup]
#     profile    DB profile/volume (default: multistart)
#     --no-setup skip the fresh setup (assume profile already has seed+calendario+source=input pins)
#
# Output: claudedocs/spalmone-2026-27/m32-sweep-results/<run>/ (capture-run.sh artifacts)
# plus SUMMARY.txt with per-run (hard, medium, wall, full-day, resemblance-unpinned).
#
# Modeled on scripts/benchmark/multistart-ab.sh (the GRASP-seed sweep driver); shares the
# boot_prod / run_one / verdict shape. A future refactor could extract those helpers.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="${1:-multistart}"
NO_SETUP=0
for a in "${@:2}"; do
    case "$a" in
        --no-setup) NO_SETUP=1 ;;
        *) echo "unknown arg: $a" >&2; exit 1 ;;
    esac
done

RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/m32-sweep-results}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

# Single-variable flags shared by every run; only the numerator changes.
SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MULTISTART_OFF="-Dplanner.multistart.enabled=false"

# 3 numerator values: default first (baseline + pipeline sanity), then low, then high.
VALUES=(100000 20000 300000)

SOLVE_TIMEOUT=5400   # 90 min cap per staged solve (single cycle is ~28-50 min)
WAIT_BOOT=420        # 7 min: a boot may run DataGenerator + startup statics before /q/health
BOOT_LOG="/tmp/planner-m32-boot.log"

log() { echo "[m32-sweep $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }

# Don't orphan a java -jar holding port 8080.
cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

# Boot the app in prod mode (java -jar) with the given -D opts on the command line, wait
# HTTP-ready. planner.sh prod blocks forever in foreground, so background it + poll health.
# Arg 1: "fresh" to reset/seed the profile (stop DB + delete volume first), "" to reboot.
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

# Verify the m32.numerator knob actually reached the app JVM. The banner is logged by
# ScheduleService.refreshSolverStatics (runs at solve start). Aborts the run if the knob
# did not arrive (the MAVEN_OPTS failure mode: defaults 100000/20/500 = NOT arrived).
verify_knob() {
    local expected="$1"
    # refreshSolverStatics runs when solve-staged starts; give it a moment to log.
    local banner=""
    for _ in 1 2 3 4 5; do
        banner="$(grep -aoE "M32 template-deviation weight knobs: numerator=[0-9]+ min=[0-9]+ max=[0-9]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        [[ -n "$banner" ]] && break
        sleep 3
    done
    if [[ -z "$banner" ]]; then
        log "verify_knob: NO M32 banner found in log - knob did not reach the app (MAVEN_OPTS failure?). ABORT run."
        return 1
    fi
    local got="$(echo "$banner" | grep -oE "numerator=[0-9]+" | cut -d= -f2)"
    if [[ "$got" != "$expected" ]]; then
        log "verify_knob: numerator=$got, expected=$expected (banner: $banner). ABORT run."
        return 1
    fi
    log "verify_knob: OK - $banner"
    # Bonus: confirm multi-start is OFF and GRASP is OFF (single deterministic solve).
    grep -aoE "Multi-start staged retry: enabled=[a-z]+(, maxAttempts=[0-9]+)?(, budgetSeconds=[0-9]+)?" "$BOOT_LOG" 2>/dev/null | tail -1 | tee -a "$SUMMARY" >/dev/null || true
    grep -aoE "grasp=[a-z]+ seed=[0-9-]+" "$BOOT_LOG" 2>/dev/null | tail -1 | tee -a "$SUMMARY" >/dev/null || true
    return 0
}

# Setup (once): rebuild JAR, fresh profile (seed), import-calendario, pre-assign (source=input).
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

    log "=== SETUP: kill-app (DB stays up; profile ready with seed + calendario + source=input pins) ==="
    ./planner.sh kill-app
}

# One solve run: boot with the numerator knob, verify it arrived, solve-staged, capture, stop.
run_one() {
    local value="$1"
    local label="numerator-$value"
    local out="$RESULTS/$label"
    local opts="$SRC_INPUT_FLAG $MULTISTART_OFF -Dplanner.m32.numerator=$value"
    log "--- RUN $label : opts='$opts' ---"
    local t0="$SECONDS"
    boot_prod "" "$opts" || { log "RUN $label: boot FAILED"; return 1; }
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged start FAILED"; ./planner.sh kill-app; return 1; }
    # CRITICAL: verify the knob reached the app before committing to the ~30-min solve wait.
    if ! verify_knob "$value"; then
        ./planner.sh kill-app
        return 1
    fi
    log "RUN $label: knob verified, waiting for solve (cap ${SOLVE_TIMEOUT}s)..."
    if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then
        log "RUN $label: wait TIMEOUT/FAILED"
    fi
    local elapsed=$(( SECONDS - t0 ))
    PLANNER_LOG="$BOOT_LOG" ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

# Extract a KPI line from a run's capture (tolerant: prints '?' if absent).
kpi_of() { local file="$1" pattern="$2"; grep -m1 -iE "$pattern" "$file" 2>/dev/null | tr -d '\n' || echo "?"; }

if (( NO_SETUP == 1 )); then
    log "=== SKIPPING setup (--no-setup): profile '$PROFILE' assumed ready (seed + calendario + source=input pins) ==="
else
    setup_profile
fi

# The 3-point sweep.
for v in "${VALUES[@]}"; do
    run_one "$v" || log "RUN numerator-$v: FAILED (skipped)"
done

# Summary: per-run score + key KPIs. The sensitivity analysis (delta vs LS variance ~50k
# medium) is done in the main session against these captured artifacts, not here.
log "=== SUMMARY ==="
{
    echo "=== M32 numerator sweep - $(date) ==="
    echo "profile=$PROFILE  source=input  multi-start=OFF  GRASP=OFF  (single deterministic solve)"
    echo "min=20 max=500 held constant; only numerator varies."
    echo
    printf "%-16s %-20s %-9s %-10s %s\n" "numerator" "score(h/m/s)" "wall(s)" "full-day" "resemblance-unpinned"
    for v in "${VALUES[@]}"; do
        rout="$RESULTS/numerator-$v"
        [[ -d "$rout" ]] || continue
        sc="$(grep -m1 -oE '[-0-9]+hard/[-0-9]+medium/[-0-9]+soft' "$rout/score.txt" 2>/dev/null || echo '?')"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$rout/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        fd="$(kpi_of "$rout/kpi-fill.txt" 'FULL-DAY RATE')"
        re="$(kpi_of "$rout/kpi-resemblance.txt" 'unpinned')"
        printf "%-16s %-20s %-9s %-10s %s\n" "$v" "$sc" "$wc" "$fd" "$re"
    done
    echo
    echo "Raw artifacts: $RESULTS/numerator-*/ (score, KPIs, golden-vs-run HTML)."
    echo "NO ADOPTION - default stays 100000. Sensitivity verdict: see VERDICT-m32-sweep doc."
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
