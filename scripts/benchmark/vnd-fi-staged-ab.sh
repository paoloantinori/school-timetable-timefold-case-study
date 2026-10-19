#!/usr/bin/env bash
#
# vnd-fi-staged-ab.sh - LIVE staged A/B: VND-FirstImprovement vs the current Tabu-Size7
# acceptor on the STAGED production pipeline + source=input seed. Single variable: the LS
# acceptor/forager. NO ADOPTION (Tabu-Size7 stays ratified); solverConfig.xml is reverted
# on any exit (trap).
#
# VND-FirstImprovement (per the controlled-benchmark winner family, VERDICT-tabuvnd-benchmark):
#   - remove <acceptor><entityTabuSize>7</entityTabuSize></acceptor>  => hill climbing (only
#     improving moves), since VND has no short-term memory.
#   - <acceptedCountLimit>1</acceptedCountLimit>                      => first improvement.
#   Everything else is untouched: the move selectors, the <diminishedReturns> termination
#   (already VND-DR-style), the staged CH/repair/room/ExactFinisher phases.
# Applied to BOTH LS phases (2 compactness + 5 room) for a consistent metaheuristic.
#
# The controlled benchmark (old March dataset, LS-only) showed VND-FI hard -284 vs Tabu
# -6740 (~25x). This A/B tests whether that transfers to the real staged pipeline. Ex-ante
# prediction + honest outcome land in VERDICT-vnd-staged-*.md.
#
# Usage: ./scripts/benchmark/vnd-fi-staged-ab.sh [profile] [--setup]
#   profile  default m32-sweep (reuse its source=input pins)
#   --setup  fresh setup (rebuild + profile + import-calendario + pre-assign source=input)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

PROFILE="${1:-m32-sweep}"
SETUP=0
[[ "${2:-}" == "--setup" ]] && SETUP=1

SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MULTISTART_OFF="-Dplanner.multistart.enabled=false"
CONFIG="src/main/resources/solverConfig.xml"
BACKUP="/tmp/solverConfig.xml.vndfi-ab.bak"

RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/task284-results/vnd-fi-staged-ab}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

SOLVE_TIMEOUT=5400
WAIT_BOOT=420
BOOT_LOG="/tmp/planner-vndfi-boot.log"

log() { echo "[vndfi-ab $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }

cp "$CONFIG" "$BACKUP"
restore() {
  cp "$BACKUP" "$CONFIG" 2>/dev/null || true
  ./planner.sh kill-app >/dev/null 2>&1 || true
  log "solverConfig.xml restored from backup (no adoption)."
}
trap restore INT TERM EXIT

boot_prod() {
    local java_opts="$1"
    rm -f "$BOOT_LOG"
    PLANNER_JAVA_OPTS="$java_opts" nohup bash -c "cd '$ROOT' && ./planner.sh prod '$PROFILE'" > "$BOOT_LOG" 2>&1 &
    local pid=$!
    for i in $(seq 1 "$((WAIT_BOOT / 3))"); do
        if ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY"; then
            log "boot: ready (pid $pid)"
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            log "boot: prod process DIED (see $BOOT_LOG)"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
            return 1
        fi
        sleep 3
    done
    log "boot: TIMEOUT after ${WAIT_BOOT}s"; tail -15 "$BOOT_LOG" 2>/dev/null | tee -a "$SUMMARY" || true
    ./planner.sh kill-app >/dev/null 2>&1 || true
    return 1
}

verify_mechanism() {
    local ms="" gp=""
    for _ in 1 2 3 4 5; do
        ms="$(grep -aoE "Multi-start staged retry: enabled=[a-z]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        gp="$(grep -aoE "grasp=[a-z]+ seed=[0-9-]+" "$BOOT_LOG" 2>/dev/null | tail -1 || true)"
        [[ -n "$ms" && -n "$gp" ]] && break
        sleep 3
    done
    if [[ -z "$ms" || -z "$gp" ]]; then
        log "verify_mechanism: -D banners NOT found. ABORT run."; return 1
    fi
    log "verify_mechanism: OK - $ms; $gp"
    return 0
}

setup_profile() {
    log "=== SETUP: rebuild JAR (--build-only) ==="; ./scripts/core/rebuild.sh --build-only
    log "=== SETUP: fresh profile '$PROFILE' + boot (source=input) ==="
    boot_prod "$SRC_INPUT_FLAG" || { log "SETUP: boot FAILED"; exit 1; }
    log "=== SETUP: import-calendario ==="; ./scripts/api/import-calendario.sh
    log "=== SETUP: pre-assign (source=input) ==="; ./scripts/api/api.sh pre-assign
    local st=""
    for i in {1..120}; do
        st="$(./scripts/api/api.sh pre-assign-status 2>/dev/null | grep -oiE 'COMPLETE|ERROR|RUNNING|IDLE' | head -1 || true)"
        [[ "$st" == "COMPLETE" ]] && break
        [[ "$st" == "ERROR" ]] && { log "SETUP: pre-assign ERROR"; exit 1; }
        sleep 5
    done
    log "SETUP: pre-assign $st"; ./planner.sh kill-app
}

# VND-FirstImprovement: strip <acceptor> blocks (hill climbing) + set acceptedCountLimit=1
# (first improvement) in BOTH LS phases.
apply_vndfi() {
    python3 - "$CONFIG" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
before_tabu = len(re.findall(r'<entityTabuSize>', s))
before_acl  = len(re.findall(r'<acceptedCountLimit>[0-9]*</acceptedCountLimit>', s))
s2 = re.sub(r'\s*<acceptor>.*?</acceptor>', '', s, flags=re.DOTALL)
s2 = re.sub(r'<acceptedCountLimit>[0-9]*</acceptedCountLimit>',
            '<acceptedCountLimit>1</acceptedCountLimit>', s2)
open(p, 'w').write(s2)
print(f"apply_vndfi: removed {before_tabu} <entityTabuSize> (in {before_acl} acceptor blocks); set acl=1")
PY
    local na nb
    na="$(grep -c '<entityTabuSize>' "$CONFIG" || true)"
    nb="$(grep -c '<acceptedCountLimit>1</acceptedCountLimit>' "$CONFIG" || true)"
    if [[ "$na" -ne 0 || "$nb" -ne 2 ]]; then
        log "apply_vndfi: VERIFY FAIL (entityTabuSize=$na want 0, acl1=$nb want 2). ABORT"
        return 1
    fi
    log "apply_vndfi: OK (0 acceptor/entityTabuSize, 2x acceptedCountLimit=1)"
    return 0
}

run_one() {
    local label="$1"
    local out="$RESULTS/$label"
    local opts="$SRC_INPUT_FLAG $MULTISTART_OFF"
    local t0="$SECONDS"
    log "--- RUN $label ---"

    if [[ "$label" == "vnd-fi" ]]; then
        apply_vndfi || return 1
    fi
    # Rebuild per run so target/classes matches the (baseline-restored or vnd-fi-edited) source.
    log "RUN $label: rebuilding JAR (--build-only)..."
    if ! ./scripts/core/rebuild.sh --build-only >/tmp/vndfi-rebuild.log 2>&1; then
        log "RUN $label: REBUILD FAILED"; tail -15 /tmp/vndfi-rebuild.log | tee -a "$SUMMARY"; return 1
    fi
    # Verify the baked config matches the intent.
    local na nb
    na="$(grep -c '<entityTabuSize>' target/classes/solverConfig.xml 2>/dev/null || true)"
    nb="$(grep -c '<acceptedCountLimit>1</acceptedCountLimit>' target/classes/solverConfig.xml 2>/dev/null || true)"
    if [[ "$label" == "vnd-fi" ]]; then
        [[ "$na" -eq 0 && "$nb" -eq 2 ]] || { log "RUN $label: baked mismatch (tabu=$na acl1=$nb). ABORT"; return 1; }
    else
        local nt; nt="$(grep -c '<entityTabuSize>7</entityTabuSize>' target/classes/solverConfig.xml 2>/dev/null || true)"
        [[ "$nt" -eq 2 ]] || { log "RUN tabu-baseline: baked mismatch (entityTabuSize=7 x$nt want 2). ABORT"; return 1; }
    fi
    log "RUN $label: baked config verified"

    boot_prod "$opts" || { log "RUN $label: boot FAILED"; return 1; }
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged FAILED"; ./planner.sh kill-app; return 1; }
    verify_mechanism || { ./planner.sh kill-app; return 1; }
    log "RUN $label: verified, waiting for staged solve (cap ${SOLVE_TIMEOUT}s)..."
    ./scripts/api/api.sh wait "$SOLVE_TIMEOUT" || log "RUN $label: wait non-zero (often benign near completion)"
    local elapsed=$(( SECONDS - t0 ))
    PLANNER_LOG="$BOOT_LOG" ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

if (( SETUP == 1 )); then setup_profile; else log "=== --no-setup: reusing profile '$PROFILE' (source=input pins persist) ==="; fi

run_one "tabu-baseline" || log "RUN tabu-baseline: FAILED (skipped)"
# Restore the config before the variant edit (defensive: ensures a clean base for apply_vndfi).
cp "$BACKUP" "$CONFIG"
run_one "vnd-fi" || log "RUN vnd-fi: FAILED (skipped)"

log "=== A/B SUMMARY ==="
{
    echo "=== VND-FirstImprovement staged A/B - $(date) ==="
    echo "profile=$PROFILE  source=input  multi-start=OFF  GRASP=OFF  (single deterministic solve)"
    echo "single variable = LS acceptor/forager (both LS phases):"
    echo "  tabu-baseline = <entityTabuSize>7</entityTabuSize> + <acceptedCountLimit>1000</acceptedCountLimit> (current production)"
    echo "  vnd-fi       = no <acceptor> (hill climbing)        + <acceptedCountLimit>1</acceptedCountLimit> (first improvement)"
    echo
    printf "%-14s %-22s %-9s %-10s %s\n" "variant" "score(h/m/s)" "wall(s)" "full-day" "resemblance-unpinned"
    for v in tabu-baseline vnd-fi; do
        local_out="$RESULTS/$v"
        [[ -d "$local_out" ]] || continue
        sc="$(grep -m1 -oE '[-0-9]+hard/[-0-9]+medium/[-0-9]+soft' "$local_out/score.txt" 2>/dev/null || echo '?')"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$local_out/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        fd="$(grep -m1 -iE 'FULL-DAY RATE' "$local_out/kpi-fill.txt" 2>/dev/null | tr -d '\n' || echo '?')"
        re="$(grep -m1 -iE 'UNPINNED' "$local_out/kpi-resemblance.txt" 2>/dev/null | tr -d '\n' || echo '?')"
        printf "%-14s %-22s %-9s %-10s %s\n" "$v" "$sc" "$wc" "$fd" "$re"
    done
    echo
    echo "NO ADOPTION - solverConfig.xml reverted (Tabu-Size7 production acceptor stands)."
    echo "Verdict: claudedocs/spalmone-2026-27/task284-results/VERDICT-vnd-staged-*.md"
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
