#!/usr/bin/env bash
# Benchmark two experiment branches side by side
# Usage: ./scripts/benchmark-experiment.sh [solve_seconds]
set -euo pipefail
cd "$(dirname "$0")/.."

SOLVE_SECS=${1:-360}  # default 6 minutes
POLL_INTERVAL=15
BRANCHES=("experiment/hard-compact" "experiment/strong-s5")
PROFILES=("bench-hard-compact" "bench-strong-s5")
RESULTS_DIR="claudedocs/benchmark-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULTS_DIR"

ORIGINAL_BRANCH=$(git branch --show-current)

cleanup() {
  echo "[cleanup] Stopping app and DB..."
  ./planner.sh kill-app 2>/dev/null || true
  ./planner.sh stop 2>/dev/null || true
  git checkout "$ORIGINAL_BRANCH" 2>/dev/null || true
}
trap cleanup EXIT

for idx in "${!BRANCHES[@]}"; do
  BRANCH="${BRANCHES[$idx]}"
  PROFILE="${PROFILES[$idx]}"
  LABEL=$(echo "$BRANCH" | sed 's|experiment/||')
  LOG_FILE="$RESULTS_DIR/${LABEL}.csv"

  echo "============================================================"
  echo "  BENCHMARK: $BRANCH"
  echo "  Profile:   $PROFILE"
  echo "  Duration:  ${SOLVE_SECS}s"
  echo "============================================================"

  # Switch branch and build
  echo "[${LABEL}] Checking out $BRANCH..."
  git checkout "$BRANCH"

  echo "[${LABEL}] Building..."
  mvn package -DskipTests -Dnet.bytebuddy.experimental=true -q

  # Delete old profile if exists, start fresh
  yes 2>/dev/null | ./planner.sh delete "$PROFILE" 2>/dev/null || true
  ./planner.sh start "$PROFILE" > /tmp/planner-bench.log 2>&1 &
  ./planner.sh wait

  # Start solver
  echo "[${LABEL}] Starting solver..."
  curl -s -X POST http://localhost:8080/api/schedule/solve > /dev/null

  # Write CSV header
  echo "elapsed_s,hard,medium,soft,assigned,total,status" > "$LOG_FILE"

  # Poll
  START_TIME=$(date +%s)
  CH_DONE_AT=""
  while true; do
    sleep "$POLL_INTERVAL"
    NOW=$(date +%s)
    ELAPSED=$(( NOW - START_TIME ))

    curl -s http://localhost:8080/api/schedule -o /tmp/bench-schedule.json
    ROW=$(python3 -c "
import json, re
with open('/tmp/bench-schedule.json') as f:
    data = json.load(f)
score = data.get('score', '') or ''
status = data.get('solverStatus', 'UNKNOWN')
lessons = data.get('lessons', [])
assigned = sum(1 for l in lessons if l.get('timeslot'))
total = len(lessons)
m = re.match(r'(-?\d+)hard/(-?\d+)medium/(-?\d+)soft', str(score))
if m:
    print(f'${ELAPSED},{m.group(1)},{m.group(2)},{m.group(3)},{assigned},{total},{status}')
else:
    print(f'${ELAPSED},,,,{assigned},{total},{status}')
")
    echo "$ROW" >> "$LOG_FILE"
    # Extract fields for display
    HARD=$(echo "$ROW" | cut -d, -f2)
    MED=$(echo "$ROW" | cut -d, -f3)
    SOFT=$(echo "$ROW" | cut -d, -f4)
    ASSIGNED=$(echo "$ROW" | cut -d, -f5)
    TOTAL=$(echo "$ROW" | cut -d, -f6)

    # Detect CH completion (first time assignments appear)
    if [ -z "$CH_DONE_AT" ] && [ -n "$ASSIGNED" ] && [ "$ASSIGNED" != "0" ]; then
      CH_DONE_AT="$ELAPSED"
      echo "[${LABEL}] >>> CH completed at ${ELAPSED}s — local search started <<<"
    fi

    printf "[%s] %4ds  hard=%-12s med=%-8s soft=%-12s assigned=%s/%s\n" "$LABEL" "$ELAPSED" "$HARD" "$MED" "$SOFT" "$ASSIGNED" "$TOTAL"

    if [ "$ELAPSED" -ge "$SOLVE_SECS" ]; then
      break
    fi
  done

  # Record CH completion time
  if [ -n "$CH_DONE_AT" ]; then
    echo "$CH_DONE_AT" > "$RESULTS_DIR/${LABEL}.ch_time"
    echo "[${LABEL}] CH completed at: ${CH_DONE_AT}s"
  else
    echo "N/A" > "$RESULTS_DIR/${LABEL}.ch_time"
    echo "[${LABEL}] CH did not complete within benchmark window"
  fi

  # Stop solver
  echo "[${LABEL}] Stopping solver..."
  curl -s -X DELETE http://localhost:8080/api/schedule/solve > /dev/null 2>&1 || true
  sleep 2

  # Capture final score
  curl -s http://localhost:8080/api/schedule -o /tmp/bench-schedule.json
  FINAL=$(python3 -c "
import json
with open('/tmp/bench-schedule.json') as f:
    data = json.load(f)
print(data.get('score', 'N/A'))
")
  echo "[${LABEL}] Final score: $FINAL"
  echo ""

  # Stop app and DB
  ./planner.sh kill-app 2>/dev/null || true
  ./planner.sh stop 2>/dev/null || true
  sleep 2
done

# Generate comparison report
echo "============================================================"
echo "  COMPARISON REPORT"
echo "============================================================"

python3 - "$RESULTS_DIR" <<'PYEOF'
import csv, sys, os

results_dir = sys.argv[1]
files = sorted([f for f in os.listdir(results_dir) if f.endswith('.csv')])

data = {}
ch_times = {}
for f in files:
    label = f.replace('.csv', '')
    rows = []
    with open(os.path.join(results_dir, f)) as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            if row['hard']:
                rows.append(row)
    data[label] = rows
    ch_file = os.path.join(results_dir, f"{label}.ch_time")
    if os.path.exists(ch_file):
        ch_times[label] = open(ch_file).read().strip()

print(f"\nResults saved to: {results_dir}/")
print()

# Summary table
print(f"{'Metric':<30}", end="")
for label in data:
    print(f"  {label:>20}", end="")
print()
print("-" * (30 + 22 * len(data)))

# CH completion time
print(f"{'CH completion (s)':<30}", end="")
for label in data:
    val = ch_times.get(label, 'N/A')
    print(f"  {val:>20}", end="")
print()

# First hard score (right after CH)
print(f"{'Hard after CH':<30}", end="")
for label in data:
    rows = data[label]
    print(f"  {rows[0]['hard'] if rows else 'N/A':>20}", end="")
print()

for metric_name, field in [("Final hard score", "hard"), ("Final medium score", "medium"), ("Final soft score", "soft")]:
    print(f"{metric_name:<30}", end="")
    for label in data:
        rows = data[label]
        if rows:
            val = rows[-1][field]
            print(f"  {val:>20}", end="")
        else:
            print(f"  {'N/A':>20}", end="")
    print()

# Hard score improvement
print(f"{'Hard improvement':<30}", end="")
for label in data:
    rows = data[label]
    if len(rows) >= 2:
        start = int(rows[0]['hard'])
        end = int(rows[-1]['hard'])
        pct = ((start - end) / abs(start) * 100) if start != 0 else 0
        print(f"  {f'{pct:.1f}%':>20}", end="")
    else:
        print(f"  {'N/A':>20}", end="")
print()

# Local search time (total - CH time)
print(f"{'Local search time (s)':<30}", end="")
for label in data:
    rows = data[label]
    ch = ch_times.get(label, 'N/A')
    if rows and ch != 'N/A':
        ls_time = int(rows[-1]['elapsed_s']) - int(ch)
        print(f"  {ls_time:>20}", end="")
    else:
        print(f"  {'N/A':>20}", end="")
print()

# Hard score at key timestamps
print()
print("Hard score progression:")
print(f"{'Time':>8}", end="")
for label in data:
    print(f"  {label:>20}", end="")
print()

timestamps = [60, 90, 120, 150, 180, 240, 300, 360]
for ts in timestamps:
    print(f"{ts:>6}s ", end="")
    for label in data:
        rows = data[label]
        closest = None
        for row in rows:
            if int(row['elapsed_s']) <= ts:
                closest = row
        if closest:
            print(f"  {closest['hard']:>20}", end="")
        else:
            print(f"  {'--':>20}", end="")
    print()

print()
PYEOF

echo "Done. Results in: $RESULTS_DIR/"
