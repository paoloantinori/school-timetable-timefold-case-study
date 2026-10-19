#!/usr/bin/env bash
# TASK-499 bad-seed escape test: does the nearby template filter block the moves a bad
# GRASP basin needs to escape? Paired runs on the KNOWN bad seed grasp-seed-17 (the H17
# G13 Wednesday signature, reproduced twice in reproduction-2026-08-15):
#   arm A (control): multi-start OFF, GRASP seed 17, NO filter  -> expect the census result (-6hard)
#   arm B (treatment): same, filter ON (keep 0.20)              -> 0hard or no-worse = concern closed
# Both arms MT-LS ON (current regime) and skip-doomed-room as ratified; multi-start OFF so
# the single construction's fate is visible (with multi-start ON a retry would mask it).
set -u
ROOT=/Users/user/planner
OUT="$ROOT/claudedocs/spalmone-2026-27/task499-results/badseed-2026-08-15"
mkdir -p "$OUT"
LOG="$OUT/badseed-driver.log"
cd "$ROOT"
ts() { date '+%H:%M:%S'; }
log() { echo "[$(ts)] $*" | tee -a "$LOG"; }

run_one() {   # label, extra -D flags
    local label="$1" extra="$2"
    local out="$OUT/$label"
    mkdir -p "$out"
    log "--- $label : opts='$extra' ---"
    ./planner.sh kill-app >/dev/null 2>&1 || true
    PLANNER_JAVA_OPTS="$extra" nohup ./planner.sh prod bsinput14 > "$OUT/$label-app.log" 2>&1 &
    local pid=$!
    for i in $(seq 1 140); do
        ./scripts/api/api.sh health 2>&1 | grep -q "^HEALTHY" && break
        sleep 3
    done
    log "$label: app ready (pid $pid)"
    cp "$OUT/$label-app.log" "$out/boot.log" 2>/dev/null || true
    ./scripts/api/api.sh solve-staged >> "$LOG" 2>&1 || { log "$label: solve start FAILED"; ./planner.sh kill-app; return 1; }
    if ! ./scripts/api/api.sh wait 5400; then log "$label: wait TIMEOUT (continuing to capture)"; fi
    ./scripts/benchmark/capture-run.sh "$out" >> "$LOG" 2>&1 || log "$label: capture FAILED"
    cp "$OUT/$label-app.log" "$out/score-stream.txt" 2>/dev/null || true
    rm -f "$OUT/$label-app.log"
    ./planner.sh kill-app >/dev/null 2>&1 || true
    log "$label: done. score: $(grep -m1 hard "$out/score.txt" 2>/dev/null || echo '?')"
}

BASE="-Dplanner.multistart.enabled=false -Dplanner.pre-assign.constrained-teacher.source=input -Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed=17"
run_one "bad17-filter-off" "$BASE"
run_one "bad17-filter-on"  "$BASE -Dplanner.ls.nearby-template-filter.enabled=true"
log "=== BAD-SEED ESCAPE TEST COMPLETE ==="
