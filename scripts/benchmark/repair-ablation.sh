#!/usr/bin/env bash
#
# repair-ablation.sh - READ-ONLY sensitivity study: disable ONE repair pass at a time on the
# staged pipeline and measure its contribution to hard=0. Baseline = all repair ON (reused from
# the vndfi-ab tabu-baseline, same profile, committed). NO ADOPTION: disabling is only to
# measure; ScheduleService.java is restored on any exit (trap) and no flag is left set.
#
# Ablations (single variable each, rest ON):
#   exact-finisher-off : -Dplanner.exact-finisher.enabled=false        (flag, runtime, no code edit)
#   hole-repair-off    : comment the runHoleRepair(roomProblem) call    (unconditional pass: edit + rebuild)
#   anchor-repair-off  : comment the runAnchorRepair(roomProblem) call (unconditional pass: edit + rebuild)
# All on source=input, multi-start OFF, single deterministic solve, profile vndfi-ab (reused).
#
# Usage: ./scripts/benchmark/repair-ablation.sh [profile]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="${1:-vndfi-ab}"
SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MULTISTART_OFF="-Dplanner.multistart.enabled=false"
# Bad-basin mode: set GRASP_SEED (e.g. 1 = H17 -9 structural, 6 = room-null -1) to build the bad
# basin via GRASP construction; leave empty for the good (deterministic) basin. RUN_BASELINE=1
# runs a fresh all-ON baseline (needed for the bad basin, which differs from the good-basin one).
GRASP_SEED="${GRASP_SEED:-}"
RUN_BASELINE="${RUN_BASELINE:-0}"
GRASP_FLAG=""
[[ -n "$GRASP_SEED" ]] && GRASP_FLAG="-Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=$GRASP_SEED"
SVC="src/main/java/com/school/planner/service/ScheduleService.java"
BACKUP="/tmp/ScheduleService.java.ablation.bak"

RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/task284-results/repair-ablation}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

SOLVE_TIMEOUT=5400
WAIT_BOOT=420
BOOT_LOG="/tmp/planner-ablation-boot.log"

log() { echo "[ablation $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }

cp "$SVC" "$BACKUP"
restore() {
  cp "$BACKUP" "$SVC" 2>/dev/null || true
  ./planner.sh kill-app >/dev/null 2>&1 || true
  log "ScheduleService.java restored from backup (no adoption)."
}
trap restore INT TERM EXIT

boot_prod() {
    local java_opts="$1"
    rm -f "$BOOT_LOG"
    PLANNER_JAVA_OPTS="$java_opts" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
    local pid=$!
    for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
        if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
            log "boot: ready (pid $pid)"; return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            log "boot: prod DIED (see $BOOT_LOG)"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true; return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
    ./planner.sh kill-app >/dev/null 2>&1 || true; return 1
}

verify_mechanism() {
    local ms="" gp=""
    for _ in 1 2 3 4 5; do
        ms="$(grep -aoE "Multi-start staged retry: enabled=[a-z]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        gp="$(grep -aoE "grasp=[a-z]+ seed=[0-9-]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        [[ -n "$ms" && -n "$gp" ]] && break
        sleep 3
    done
    [[ -n "$ms" && -n "$gp" ]] || { log "verify_mechanism: -D banners NOT found. ABORT"; return 1; }
    log "verify_mechanism: OK - $ms; $gp"
    return 0
}

# Comment the `<method>(roomProblem);` invocation (unconditional pass). Unique vs the method
# definition (which takes SchoolSchedule problem) and javadoc {@link}s.
disable_repair() {
    local method="$1"
    python3 - "$SVC" "$method" <<'PY'
import re, sys
p, method = sys.argv[1], sys.argv[2]
s = open(p).read()
pat = re.compile(r'^(\s*)(' + re.escape(method) + r'\(roomProblem\);)', re.MULTILINE)
hits = pat.findall(s)
if len(hits) != 1:
    print(f"DISABLE {method}: expected 1 invocation, found {len(hits)}"); sys.exit(1)
s2 = pat.sub(lambda m: m.group(1) + '// ' + m.group(2) + '  // ABLATION disabled', s)
open(p, 'w').write(s2)
print(f"DISABLE {method}: commented 1 invocation")
PY
    grep -q "// $method(roomProblem);" "$SVC" || { log "disable $method: VERIFY FAIL (not commented)"; return 1; }
    log "disable $method: OK"
    return 0
}

run_ablation() {
    local label="$1"
    local out="$RESULTS/$label"
    local opts="$SRC_INPUT_FLAG $MULTISTART_OFF $GRASP_FLAG"
    local t0="$SECONDS"
    log "--- RUN $label ---"

    # Always start from the restored (all-ON) source so each run is single-variable.
    cp "$BACKUP" "$SVC"

    case "$label" in
        baseline)
            : # all repair ON, no ablation mechanism (the reference run)
            ;;
        exact-finisher-off)
            opts="$opts -Dplanner.exact-finisher.enabled=false"   # runtime flag, no code edit
            ;;
        hole-repair-off)
            disable_repair runHoleRepair || return 1
            ;;
        anchor-repair-off)
            disable_repair runAnchorRepair || return 1
            ;;
        *)
            log "unknown run $label"; return 1 ;;
    esac

    log "RUN $label: rebuilding JAR (--build-only)..."
    if ! ./scripts/core/rebuild.sh --build-only >/tmp/ablation-rebuild.log 2>&1; then
        log "RUN $label: REBUILD FAILED"; tail -15 /tmp/ablation-rebuild.log | tee -a "$SUMMARY"; return 1
    fi

    # Edit-ablation verification = the source comment (disable_repair already grepped it) +
    # BUILD SUCCESS + the score delta vs baseline (the empirical proof the pass was inert).
    # (Comments are stripped from bytecode, so grepping the .class is pointless.)
    log "RUN $label: baked (source edit verified pre-rebuild; score delta is the proof)"

    boot_prod "$opts" || { log "RUN $label: boot FAILED"; return 1; }
    if [[ "$label" == "exact-finisher-off" ]]; then
        # Confirm the flag reached the app (banner).
        local ef=""
        for _ in 1 2 3 4 5; do
            ef="$(grep -aoE "ExactFinisher residual-completion: enabled=[a-z]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
            [[ -n "$ef" ]] && break
            sleep 3
        done
        [[ "$ef" == *"enabled=false"* ]] || { log "RUN $label: ExactFinisher banner not false ('$ef'). ABORT"; ./planner.sh kill-app; return 1; }
        log "RUN $label: $ef"
    fi
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged FAILED"; ./planner.sh kill-app; return 1; }
    verify_mechanism || { ./planner.sh kill-app; return 1; }
    log "RUN $label: waiting for staged solve (cap ${SOLVE_TIMEOUT}s)..."
    ./scripts/api/api.sh wait "$SOLVE_TIMEOUT" || log "RUN $label: wait non-zero (often benign near completion)"
    local elapsed=$(( SECONDS - t0 ))
    PLANNER_LOG="$BOOT_LOG" ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

if [[ "$RUN_BASELINE" == "1" ]]; then
    log "=== baseline = fresh all-ON run (bad basin: GRASP seed=${GRASP_SEED:-?}) ==="
    run_ablation "baseline" || log "BASELINE: FAILED (skipped)"
else
    log "=== baseline = all repair ON (reused from vndfi-ab tabu-baseline: 0hard/-1051561medium/+4301soft, full-day 87.1%, recurrence 74.1%, day-start 99.5%, wall 1600s) ==="
fi
log "=== profile=$PROFILE reused (source=input pins persist), multi-start OFF, GRASP=${GRASP_SEED:-off} ==="

for abl in exact-finisher-off hole-repair-off anchor-repair-off; do
    run_ablation "$abl" || log "RUN $abl: FAILED (skipped)"
done

log "=== SUMMARY ==="
{
    echo "=== Repair-pass ablation (read-only sensitivity) - $(date) ==="
    echo "profile=$PROFILE (reused)  source=input  multi-start=OFF  GRASP=${GRASP_SEED:-off}  (single deterministic solve)"
    echo
    printf "%-22s %-24s %-9s %-10s %-12s %s\n" "run" "score(h/m/s)" "wall(s)" "full-day" "recurrence" "day-start"
    variants="exact-finisher-off hole-repair-off anchor-repair-off"
    [[ -d "$RESULTS/baseline" ]] && variants="baseline $variants"
    [[ -d "$RESULTS/baseline" ]] || printf "%-22s %-24s %-9s %-10s %-12s %s\n" "baseline(all ON,reuse)" "0hard/-1051561medium/4301soft" "1600" "87.1%" "74.1%" "99.5%"
    for v in $variants; do
        local_out="$RESULTS/$v"
        [[ -d "$local_out" ]] || { printf "%-22s %s\n" "$v" "(no capture)"; continue; }
        sc="$(grep -m1 -oE '[-0-9]+hard/[-0-9]+medium/[-0-9]+soft' "$local_out/score.txt" 2>/dev/null || echo '?')"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$local_out/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        fd="$(grep -m1 -oE 'FULL-DAY RATE: [0-9/]+ = [0-9.]+%' "$local_out/kpi-fill.txt" 2>/dev/null | grep -oE '[0-9.]+%' | head -1 || echo '?')"
        re="$(grep -m1 -oE 'UNPINNED: [0-9/]+ = [0-9.]+%' "$local_out/kpi-resemblance.txt" 2>/dev/null | grep -oE '[0-9.]+%' | head -1 || echo '?')"
        ds="$(grep -m1 -oE 'ANCHORED RATE: [0-9/]+ = [0-9.]+%' "$local_out/kpi-daystart.txt" 2>/dev/null | grep -oE '[0-9.]+%' | head -1 || echo '?')"
        printf "%-22s %-24s %-9s %-10s %-12s %s\n" "$v" "$sc" "$wc" "$fd" "$re" "$ds"
    done
    echo
    echo "NO ADOPTION - ScheduleService.java + flags reverted (all repair ON stands)."
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
