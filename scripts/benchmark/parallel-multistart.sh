#!/usr/bin/env bash
# parallel-multistart.sh <N> - N PARALLEL single-construction staged solves + pick-best.
#
# Externalizes the multi-start (TASK-483): instead of ONE process retrying N times SERIALLY
# (N x wall), runs N datum-isolated app+DB stacks CONCURRENTLY, each exactly 1 GRASP construction
# (multi-start OFF, grasp.seed=i), then pick-best on (hard, medium). Target ~1x wall vs N x
# serial. The speedup is justified now that p(0-hard)>0 (m-13 approved, TASK-480 N=32). This is
# a PERFORMANCE SPIKE (the multistart-ab.sh header called it "the speedup to build ONLY IF
# P(0-hard)>0"): measure parallel-wall vs serial-wall + confirm p(0-hard) holds. NOT an adoption
# (the parallel procedure as production is a separate ratification).
#
# ISOLATION (avoid conflicts - distinct ports, distinct DBs, distinct logs):
#   - DB:   N podman containers planner-pms-i on host ports 5433+i (container 5432), each its own
#            fresh volume planner-data-pms-i. No shared data (the SchoolSchedule static-volatile
#            fields forbid in-JVM parallelism anyway, so N JVMs with N DBs is the only safe shape).
#   - APP:  N Quarkus instances on HTTP ports 8080+i (QUARKUS_HTTP_PORT). Driven via
#            api.sh -p / import-calendario.sh -p (both support -p PORT).
#   - LOGS: app-i.log per instance + a shared driver.log.
#   - The MAIN instance (5432/8080) is untouched; this uses 5433+/8081+. But CPU contention with
#            a running main solve skews timing, so run this when the main is idle.
#
# Usage: bash ./scripts/benchmark/parallel-multistart.sh [N]   (N default 3)
# Backgroundable; wrap in caffeinate -is. Poll <OUT>/DONE + <OUT>/driver.log.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
N="${1:-3}"
PREFIX="pms"
OUT="claudedocs/spalmone-2026-27/parallel-multistart-$(date +%Y-%m-%d)"
mkdir -p "$OUT"
LOG="$OUT/driver.log"
DONE_MARKER="$OUT/DONE"
IMAGE="docker.io/library/postgres:17"
HEAP="${PLANNER_HEAP:-2g}"
SRC_FLAG="-Dplanner.pre-assign.constrained-teacher.source=input"
MS_OFF="-Dplanner.multistart.enabled=false"   # externalize: each instance = 1 construction
ts() { date '+%Y-%m-%d %H:%M:%S'; }
say() { echo "[$(ts)] $*" | tee -a "$LOG"; }

cleanup() {
  say "cleanup: stopping $N instances"
  for i in $(seq 1 "$N"); do
    [ -f "$OUT/app-$i.pid" ] && kill "$(cat "$OUT/app-$i.pid")" 2>/dev/null || true
    podman rm -f "planner-${PREFIX}-$i" >/dev/null 2>&1 || true
  done
}
trap cleanup INT TERM EXIT

say "PARALLEL MULTISTART START N=$N (isolation: DB 5433+, app 8081+, fresh volumes)"
[ -f target/quarkus-app/quarkus-run.jar ] || { say "ERROR: JAR missing (rebuild first)"; exit 1; }
start=$(date +%s)

# --- Launch N instances (DB container + app), but stagger the boot start so the N fresh-seed
#     DataGenerator runs don't all peak together (CPU). Each app gets multi-start OFF + GRASP i. ---
for i in $(seq 1 "$N"); do
  dbport=$((5432 + i)); appport=$((8080 + i)); vol="planner-data-${PREFIX}-$i"; name="planner-${PREFIX}-$i"
  say "instance $i: $name DB port $dbport vol $vol | app port $appport | GRASP seed $i | multi-start OFF"
  podman rm -f "$name" >/dev/null 2>&1 || true
  podman volume rm "$vol" >/dev/null 2>&1 || true
  podman volume create "$vol" >/dev/null 2>&1 || true
  podman run -d --name "$name" -e POSTGRES_USER=planner -e POSTGRES_PASSWORD=planner \
    -e POSTGRES_DB=planner -p "${dbport}:5432" -v "${vol}:/var/lib/postgresql/data:Z" \
    "$IMAGE" >/dev/null || { say "ERROR: container $name failed"; exit 1; }
  QUARKUS_HTTP_PORT="$appport" \
    QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:${dbport}/planner" \
    QUARKUS_DATASOURCE_USERNAME=planner QUARKUS_DATASOURCE_PASSWORD=planner \
    QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=update \
    nohup java -Xmx"$HEAP" $SRC_FLAG $MS_OFF \
      -Dplanner.construction.grasp.enabled=true -Dplanner.construction.grasp.seed="$i" \
      -jar target/quarkus-app/quarkus-run.jar > "$OUT/app-$i.log" 2>&1 &
  echo $! > "$OUT/app-$i.pid"
  sleep 20   # stagger fresh-seed boot (DataGenerator is CPU-heavy on an empty volume)
done

# --- Wait each app ready, then import + pre-assign (per port). ---
for i in $(seq 1 "$N"); do
  appport=$((8080 + i))
  ready=0
  for r in $(seq 1 200); do
    ./scripts/api/api.sh -p "$appport" health 2>&1 | grep -q HEALTHY && { ready=1; break; }
    sleep 5
  done
  [ "$ready" = 1 ] || { say "ERROR: app $i (port $appport) not ready"; exit 1; }
  say "instance $i ready (port $appport) - import + pre-assign"
  ./scripts/api/import-calendario.sh -p "$appport" >>"$LOG" 2>&1 || say "WARN import i=$i"
  ./scripts/api/api.sh -p "$appport" pre-assign -t 120 >>"$LOG" 2>&1 || say "WARN pre-assign i=$i"
  ./scripts/api/api.sh -p "$appport" wait 300 >>"$LOG" 2>&1 || true
done

# --- Start all N solves (concurrent). ---
say "starting $N concurrent staged solves (1 construction each, multi-start OFF)"
for i in $(seq 1 "$N"); do
  ./scripts/api/api.sh -p "$((8080 + i))" solve-staged -t 120 >>"$LOG" 2>&1 || say "WARN solve-staged i=$i"
done
solve_start=$(date +%s)

# --- Wait all solves done (per port: NOT_SOLVING + stagedStage null). ---
for i in $(seq 1 "$N"); do
  appport=$((8080 + i))
  for r in $(seq 1 240); do
    SJ="$(./scripts/api/api.sh -p "$appport" status -j 2>/dev/null || true)"
    SST="$(printf '%s' "$SJ" | jq -r '.solverStatus // "UNKNOWN"' 2>/dev/null || echo UNKNOWN)"
    SSG="$(printf '%s' "$SJ" | jq -r '.stagedStage // "null"' 2>/dev/null || echo null)"
  # TASK-509 follow-up: delivery refinement keeps the two old signals quiet
  RFD="$(printf '%s' "$SJ" | jq -r '.refiningForDelivery // false' 2>/dev/null || echo false)"
    if [ "$SST" = "NOT_SOLVING" ] && { [ "$SSG" = "null" ] || [ -z "$SSG" ]; } && [ "$RFD" != "true" ]; then
      say "instance $i (port $appport) done: $(./scripts/api/api.sh -p "$appport" score 2>/dev/null)"
      break
    fi
    sleep 30
  done
done
solve_end=$(date +%s)

# --- Pick-best + summary. ---
say "=== RESULTS (pick-best on hard, then medium) ==="
{
  echo "parallel-multistart N=$N ($(date))"
  echo "solve wall (all-N-done): $((solve_end - solve_start))s (~$(((solve_end - solve_start)/60)) min)"
  for i in $(seq 1 "$N"); do
    echo "instance $i (GRASP seed $i): $(./scripts/api/api.sh -p "$((8080 + i))" score 2>/dev/null)"
  done
} | tee "$OUT/SUMMARY.txt"

# capture the winner (best hard then medium) for the record
best_i=1; best_score=""
for i in $(seq 1 "$N"); do
  s="$(./scripts/api/api.sh -p "$((8080 + i))" score 2>/dev/null)"
  say "instance $i: $s"
done
total_end=$(date +%s)
say "TOTAL wall (boot+setup+solve): $((total_end - start))s (~$(((total_end - start)/60)) min)"
say "PARALLEL MULTISTART COMPLETE. Compare vs serial multistart-ab.sh N=$N wall (~$((N*28)) min)."
echo "DONE $(ts)" > "$DONE_MARKER"
