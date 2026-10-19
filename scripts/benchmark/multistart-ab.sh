#!/usr/bin/env bash
#
# multistart-ab.sh - TASK-480 Fase 3 A/B: N staged solves with N independent GRASP
# construction seeds (source=input, the bad-seed H17 regime), sequential on ONE profile.
#
# GATE METRIC (maintainer 02/08): P(at least one 0-hard run) + per-run wall-clock. The gate
# of independence (VERDICT-gate-indipendenza-2026-08-02.md) proved the @10 cell is
# tie-break-diversifiable. This A/B is the ground truth on whether diversifying the
# construction yields a 0-hard basin the deterministic construction misses.
#
# WHY SEQUENTIAL ON ONE PROFILE (not parallel, not in-process):
#   - The 23 static-volatile fields in SchoolSchedule forbid in-JVM parallelism (recon
#     task480-recon). maintainer overruled an in-process loop on those grounds.
#   - The project's "single DB actor" rule: one process touches the live app/DB at a time.
#   - The GRASP -D is read LIVE at construction and must be on the `java -jar` line to
#     reach the app JVM (CLAUDE.md MAVEN_OPTS lesson), so each seed needs a fresh app boot.
#   So: one profile set up once, then N prod-mode boots each with -Dgrasp.seed=i. Each
#   solve-staged re-plans from scratch (runStagedCycle calls unassignUnpinnedPlacements),
#   so no reset is needed between runs. Authoritative pins persist.
#
# Boot uses `planner.sh prod [--fresh] <profile>` (CLAUDE.md's one-command prod path: it
# manages the DB volume + runs java -jar directly + reads PLANNER_JAVA_OPTS for the -D
# flags). planner.sh prod blocks forever in foreground, so we background it and poll HTTP.
#
# PARALLEL SPEEDUP (the true N-process wall-clock win) = N postgres containers on N ports
# + N app instances on N ports + N volumes. Documented in the Fase 4 operational package
# as the speedup to build ONLY IF this A/B validates P(0-hard) > 0.
#
# Usage:
#   ./scripts/benchmark/multistart-ab.sh <N> [profile] [--baseline] [--setup-only]
#     N            number of GRASP runs (maintainer's gate is n>=3)
#     profile      DB profile/volume (default: multistart)
#     --baseline   also run a GRASP-off baseline first (1 extra solve, for comparison)
#     --setup-only do the profile setup (rebuild+seed+import+pre-assign) and exit
#
# Output: claudedocs/spalmone-2026-27/task480-results/multistart-ab/<run>/ per run
# (capture-run.sh artifacts) plus SUMMARY.txt with per-run (hard, medium, wall-clock),
# P(>=1 0-hard), and the pick-best winner.
#
# Resilience: a single run failing does NOT abort the A/B. It is logged and skipped, the
# loop continues, and the verdict reports how many runs succeeded.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"

N="${1:?usage: multistart-ab.sh <N> [profile] [--baseline] [--setup-only]}"
PROFILE="${2:-multistart}"
BASELINE=0
SETUP_ONLY=0
NO_SETUP=0
for a in "${@:3}"; do
    case "$a" in
        --baseline) BASELINE=1 ;;
        --setup-only) SETUP_ONLY=1 ;;
        --no-setup) NO_SETUP=1 ;;
        *) echo "unknown arg: $a" >&2; exit 1 ;;
    esac
done
[[ "$N" =~ ^[0-9]+$ ]] || { echo "N must be a positive integer, got '$N'" >&2; exit 1; }
(( N >= 1 )) || { echo "N must be >= 1" >&2; exit 1; }

# Output dir is overridable via RESULTS_DIR env var (backwards-compatible default) so a
# second measurement run (e.g. badseed-frequency) does NOT overwrite a prior batch's raw.
RESULTS="${RESULTS_DIR:-claudedocs/spalmone-2026-27/task480-results/multistart-ab}"
mkdir -p "$RESULTS"
SUMMARY="$RESULTS/SUMMARY.txt"
: > "$SUMMARY"

# SINGLE-CONSTRUCTION MODE (bad-seed census for the source=input flip decision): when
# MULTI_START_OFF=1, disable the multi-start wrapper so every run is ONE construction with
# NO pick-best retry (-Dplanner.multistart.enabled=false runs runStagedCycle directly, the
# single-shot deterministic path = directly comparable to golden's default). This measures
# p(0-hard) of a SINGLE source=input construction, the metric the procedural N=32 batch
# (solve-staged default maxAttempts=3) could not isolate. Default off = unchanged behaviour.
MULTI_START_FLAG=""
[[ "${MULTI_START_OFF:-0}" == "1" ]] && MULTI_START_FLAG="-Dplanner.multistart.enabled=false"

# COMPARABILITY MODE (2026-08-14 census extension, seeds 13..19): the first 12 census runs
# predate the TASK-480 MT-LS default-ON ratification (cbb74818), so they ran single-threaded
# LS by default. MT_LS_OFF=1 pins the regime for the extension runs the same way
# (-Dplanner.multithreaded-ls.enabled=false): without it the census would mix LS regimes
# mid-batch and the per-construction p(0-hard) estimate would sample two different solvers.
# Default off = unchanged behaviour (MT-LS default ON, per the ratified flag).
MT_LS_OFF_FLAG=""
[[ "${MT_LS_OFF:-0}" == "1" ]] && MT_LS_OFF_FLAG="-Dplanner.multithreaded-ls.enabled=false"

# source=input is the bad-seed H17 regime (the future solo-input path). GRASP varies the
# construction. Both are LIVE -D flags, passed to java -jar via PLANNER_JAVA_OPTS.
SRC_INPUT_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
GRASP_FLAGS="-Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed"
SOLVE_TIMEOUT=5400   # 90 min cap per staged solve (the cycle is ~50 min)
WAIT_BOOT=420        # 7 min: a fresh-seed prod boot runs DataGenerator + startup statics
                    # (refreshSolverStatics computes available-slot counts for ~13k lessons)
                    # before /q/health goes 200; 5 min was too tight (false-negative timeout).
BOOT_LOG="/tmp/planner-multistart-boot.log"

log() { echo "[multistart-ab $(date +%H:%M:%S)] $*" | tee -a "$SUMMARY"; }

# Don't orphan a `java -jar` holding port 8080: on interruption, error exit, or a boot timeout,
# kill the quarkus app (pgrep -f quarkus-run.jar, same mechanism run_one uses after each solve).
# Without this a timed-out boot leaves the old JVM alive and the next boot false-readies or fails
# to bind the port, mis-attributed to GRASP.
cleanup_boot() { ./planner.sh kill-app >/dev/null 2>&1 || true; }
trap cleanup_boot INT TERM EXIT

# Boot the app in prod mode (java -jar) on $PROFILE with the given -D flags, wait HTTP
# ready. planner.sh prod blocks forever in foreground, so background it and poll /q/health.
# Arg 1: "fresh" to reset/seed the profile (stop DB + delete volume, then boot seeds it),
#        "" to reboot the app on the already-active profile.
# NOTE: we do NOT use `planner.sh prod --fresh <profile>`: the dispatch `cmd_prod "${1:-}"`
# (planner.sh:599) passes only one arg, so `--fresh <profile>` drops the profile and fails.
# Non-fresh `planner.sh prod <profile>` on a non-existent volume creates+seeds it anyway.
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
    # Readiness = api.sh health (the API endpoint, a better signal than /q/health which lags
    # behind the startup statics computation on a fresh-seed boot).
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
    ./planner.sh kill-app >/dev/null 2>&1 || true   # don't leave the orphaned java holding port 8080
    return 1
}

# Setup (once): rebuild JAR, fresh profile (seed), import-calendario, pre-assign (source=input).
setup_profile() {
    log "=== SETUP: rebuild JAR (GRASP code) ==="
    ./scripts/core/rebuild.sh --build-only

    log "=== SETUP: fresh profile '$PROFILE' + boot (source=input) ==="
    boot_prod "fresh" "$SRC_INPUT_FLAG" || { log "SETUP: boot FAILED"; exit 1; }

    log "=== SETUP: import-calendario ==="
    ./scripts/api/import-calendario.sh

    log "=== SETUP: pre-assign (source=input). Poll COMPLETE. ==="
    ./scripts/api/api.sh pre-assign
    st=""
    for i in {1..120}; do
        st="$(./scripts/api/api.sh pre-assign-status 2>/dev/null | grep -oiE 'COMPLETE|ERROR|RUNNING|IDLE' | head -1 || true)"
        [[ "$st" == "COMPLETE" ]] && break
        [[ "$st" == "ERROR" ]] && { log "SETUP: pre-assign ERROR"; exit 1; }
        sleep 5
    done
    log "SETUP: pre-assign $st"

    log "=== SETUP: kill-app (DB stays up; profile ready with seed + calendario + pre-assign pins) ==="
    ./planner.sh kill-app
}

# One solve run: boot app with the given JAVA_OPTS, solve-staged, capture, stop.
run_one() {
    local label="$1" java_opts="$2"
    local out="$RESULTS/$label"
    # MULTI_START_FLAG (single-construction mode) and MT_LS_OFF_FLAG (comparability mode)
    # are prepended so they reach the app JVM.
    java_opts="$MULTI_START_FLAG $MT_LS_OFF_FLAG $java_opts"
    log "--- RUN $label : opts='$java_opts' ---"
    local t0="$SECONDS"
    boot_prod "" "$java_opts" || { log "RUN $label: boot FAILED"; return 1; }
    # Snapshot the boot log into the run dir BEFORE the solve overwrites it, then again
    # AFTER: the post-solve copy carries the whole Timefold best-score improvement
    # stream (the dense per-run trajectory the phase-boundary-only phases.json cannot
    # give, needed for good-vs-bad separation analysis across the FULL time interval).
    # Named .txt because the blanket '*.log' gitignore silently ate the old boot.log
    # copies (census n=19 shipped without them; the files existed on disk, git refused).
    cp "$BOOT_LOG" "$out/score-stream-boot.txt" 2>/dev/null || true
    ./scripts/api/api.sh solve-staged || { log "RUN $label: solve-staged start FAILED"; ./planner.sh kill-app; return 1; }
    log "RUN $label: solve-staged started, waiting (cap ${SOLVE_TIMEOUT}s)..."
    if ! ./scripts/api/api.sh wait "$SOLVE_TIMEOUT"; then
        log "RUN $label: wait TIMEOUT/FAILED"
    fi
    local elapsed=$(( SECONDS - t0 ))
    ./scripts/benchmark/capture-run.sh "$out" || log "RUN $label: capture FAILED"
    # Post-solve copy: NOW the log holds the full best-score stream of THIS run. Must
    # land before kill-app/next-boot rotate it (kill-app does not truncate, the next
    # boot_prod does via its own redirect, hence copy here, not in cleanup).
    cp "$BOOT_LOG" "$out/score-stream.txt" 2>/dev/null || true
    echo "wall_clock_seconds=$elapsed" >> "$out/score.txt"
    ./planner.sh kill-app
    log "RUN $label: done in ${elapsed}s. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

# Pick-best helper: echo the "Xhard/Ymedium" substring from a run's score.txt.
parse_score() {
    awk '
        match($0, /(-?[0-9]+)hard\/(-?[0-9]+)medium/) { print substr($0,RSTART,RLENGTH); exit }
    ' "$1/score.txt" 2>/dev/null
}

if (( NO_SETUP == 1 )); then
    log "=== SKIPPING setup (--no-setup): profile '$PROFILE' assumed ready (seed + import + pre-assign) ==="
else
    setup_profile
fi
(( SETUP_ONLY == 1 )) && { log "setup-only: done"; exit 0; }

# Runs: optional baseline, then N GRASP seeds. SEED_OFFSET (env, default 0) shifts the
# seed range so a second measurement run uses NEW constructions (seeds 17-32) instead of
# re-running 1-16: the per-construction p(0-hard) estimate samples the construction space,
# not just the LS space of fixed seeds. Backward-compatible (default 0 = seeds 1..N).
SEED_OFFSET="${SEED_OFFSET:-0}"
lo=$(( SEED_OFFSET + 1 )); hi=$(( SEED_OFFSET + N ))
log "runs: seeds $lo..$hi (SEED_OFFSET=$SEED_OFFSET, N=$N)"
(( BASELINE == 1 )) && run_one "baseline-grasp-off" "$SRC_INPUT_FLAG" || true
for i in $(seq "$lo" "$hi"); do
    run_one "grasp-seed-$i" "$SRC_INPUT_FLAG $GRASP_FLAGS=$i" || true
done

# Verdict.
log "=== VERDICT ==="
{
    echo "=== TASK-480 multistart A/B - $(date) ==="
    echo "profile=$PROFILE  N=$N  baseline=$BASELINE"
    echo
    printf "%-22s %-22s %-10s %s\n" "run" "score(hard/medium)" "wall(s)" "0-hard?"
    zero_hard_grasp=0
    best_lbl=""; best_hard=""; best_medium=""
    baseline_zero="?"
    for d in "$RESULTS"/baseline-grasp-off "$RESULTS"/grasp-seed-*; do
        [[ -d "$d" ]] || continue
        lbl="$(basename "$d")"
        sc="$(parse_score "$d")"
        wc="$(grep -oE 'wall_clock_seconds=[0-9]+' "$d/score.txt" 2>/dev/null | cut -d= -f2 || echo '?')"
        hard="$(echo "$sc" | grep -oE '^(-?[0-9]+)hard' | tr -d 'hard' || echo '?')"
        medium="$(echo "$sc" | grep -oE '/-?[0-9]+medium' | head -1 | sed 's:^/::; s:medium$::' || echo '?')"
        is_zero="no"
        [[ "$hard" == "0" ]] && is_zero="YES"
        printf "%-22s %-22s %-10s %s\n" "$lbl" "${sc:-?}" "$wc" "$is_zero"
        # The baseline (GRASP-off) is the control, NOT a GRASP seed: count it separately so the
        # GRASP denominator (N) is not contaminated by the baseline run.
        if [[ "$lbl" == "baseline-grasp-off" ]]; then
            baseline_zero="$is_zero"
        else
            [[ "$is_zero" == "YES" ]] && zero_hard_grasp=$((zero_hard_grasp+1))
        fi
        # pick-best on (hard asc, medium asc): least-negative hard wins; tie -> least-negative medium.
        # Only numeric scores are candidates, and a later valid run replaces an empty/non-numeric
        # best: a broken first run (missing/malformed score.txt) must not poison the verdict.
        if [[ "$hard" =~ ^-?[0-9]+$ && "$medium" =~ ^-?[0-9]+$ ]]; then
            replace=no
            if [[ -z "$best_lbl" || ! "$best_hard" =~ ^-?[0-9]+$ ]]; then
                replace=yes
            elif (( hard > best_hard || (hard == best_hard && medium > best_medium) )); then
                replace=yes
            fi
            [[ "$replace" == yes ]] && { best_lbl="$lbl"; best_hard="$hard"; best_medium="$medium"; }
        fi
    done
    echo
    echo "0-hard GRASP runs (excludes baseline): $zero_hard_grasp / $N"
    [[ "$BASELINE" -eq 1 ]] && echo "baseline (GRASP-off) 0-hard? $baseline_zero"
    [[ -n "$best_lbl" ]] && echo "pick-best winner (hard,medium): $best_lbl = ${best_hard}hard/${best_medium}medium"
    echo "total runs: $(( N + BASELINE ))"
    if (( zero_hard_grasp > 0 )); then
        echo "VERDICT: P(>=1 0-hard) > 0 over N=$N GRASP constructions. MULTI-START WORKS (procedurally pick the 0-hard run)."
        echo "NEXT: Fase 4 operational package (parallel N-container script for ~1x wall-clock)."
    else
        echo "VERDICT: NO 0-hard in N=$N GRASP runs. Multi-start does NOT close H17 here."
        echo "NEXT: consider TASK-482 Kempe-chain (connectivity cure), or raise N."
    fi
} | tee -a "$SUMMARY"
log "done. see $SUMMARY"
