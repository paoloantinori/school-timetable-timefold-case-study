#!/usr/bin/env bash
#
# benchmark.sh — Automated solver benchmarking for branch comparison
#
# Usage: ./scripts/benchmark/benchmark-branch.sh [branch-name] [duration-minutes]
#
# Starts a fresh profile, triggers Solve, polls score at intervals,
# and appends results to the tracker markdown.
#
# Requirements: curl, python3, jq (optional)
#

set -euo pipefail

BRANCH="${1:-$(git branch --show-current)}"
DURATION_MIN="${2:-10}"
POLL_INTERVAL=10  # seconds between polls
TRACKER="$HOME/.claude/projects/-home-user-data-repo-personal-planner/lab-block-contiguity-tracker.md"
PROFILE="bench-$$"
LOG="/tmp/benchmark-${BRANCH//\//-}.log"
RESULTS="/tmp/benchmark-${BRANCH//\//-}.csv"

echo "=== Benchmark: $BRANCH ==="
echo "Duration: ${DURATION_MIN} minutes"
echo "Profile: $PROFILE"
echo "Log: $LOG"
echo "Results: $RESULTS"
echo ""

# Ensure we're on the right branch
CURRENT=$(git branch --show-current)
if [ "$CURRENT" != "$BRANCH" ]; then
  echo "[ERROR] Not on branch '$BRANCH' (currently on '$CURRENT'). Switch first."
  exit 1
fi

# Build
echo "[1/6] Building..."
mvn package -DskipTests -Dnet.bytebuddy.experimental=true > /tmp/bench-build.log 2>&1
if [ $? -ne 0 ]; then
  echo "[ERROR] Build failed. See /tmp/bench-build.log"
  exit 1
fi
echo "  Build OK"

# Start fresh — planner.sh start BLOCKS (runs mvn quarkus:run in foreground),
# so we must background it and poll readiness separately with wait.
echo "[2/6] Starting fresh profile '$PROFILE'..."
./planner.sh start "$PROFILE" > "$LOG" 2>&1 &
START_PID=$!
./planner.sh wait
echo "  App ready"

# Run pre-assignment pipeline first
echo "[3/6] Running pre-assignment..."
PRE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/schedule/pre-assign)
if [ "$PRE_HTTP" != "200" ] && [ "$PRE_HTTP" != "204" ]; then
  echo "  WARNING: Pre-assignment returned HTTP $PRE_HTTP (continuing anyway)"
else
  echo "  Pre-assignment complete (HTTP $PRE_HTTP)"
fi
sleep 2  # Let pre-assignment settle

# Trigger solve
echo "[4/6] Triggering solver..."
SOLVE_START=$(date +%s)
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/schedule/solve)
if [ "$HTTP" != "202" ]; then
  echo "[ERROR] Solve returned HTTP $HTTP"
  ./planner.sh stop
  yes | ./planner.sh delete "$PROFILE" 2>/dev/null
  exit 1
fi
echo "  Solve started (HTTP $HTTP)"

# Poll loop
echo "[5/6] Polling for ${DURATION_MIN} minutes..."
echo "elapsed_sec,status,score,hard,medium,soft,assigned_pct" > "$RESULTS"

DURATION_SEC=$((DURATION_MIN * 60))
FIRST_FEASIBLE=""
POLLS=0

while true; do
  ELAPSED=$(($(date +%s) - SOLVE_START))
  if [ "$ELAPSED" -ge "$DURATION_SEC" ]; then
    break
  fi

  # Poll schedule API
  RESP=$(curl -s http://localhost:8080/api/schedule 2>/dev/null || echo "{}")
  STATUS=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('solverStatus','?'))" 2>/dev/null || echo "?")
  SCORE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('score','--'))" 2>/dev/null || echo "--")

  # Parse score components
  HARD=$(echo "$SCORE" | python3 -c "
import sys,re
s=sys.stdin.read().strip()
m=re.search(r'(-?\d+)hard', s)
print(m.group(1) if m else '?')
" 2>/dev/null || echo "?")

  MEDIUM=$(echo "$SCORE" | python3 -c "
import sys,re
s=sys.stdin.read().strip()
m=re.search(r'(-?\d+)medium', s)
print(m.group(1) if m else '?')
" 2>/dev/null || echo "?")

  SOFT=$(echo "$SCORE" | python3 -c "
import sys,re
s=sys.stdin.read().strip()
m=re.search(r'(-?\d+)soft', s)
print(m.group(1) if m else '?')
" 2>/dev/null || echo "?")

  # Check if init variables remain
  HAS_INIT=$(echo "$SCORE" | grep -c "init" || true)

  # Calculate assignment percentage
  ASSIGNED_PCT=$(echo "$RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)
lessons=d.get('lessons',[])
total=len(lessons)
assigned=sum(1 for l in lessons if l.get('timeslot'))
print(round(assigned/total*100,1) if total>0 else 0)
" 2>/dev/null || echo "?")

  # Record first feasible (no init, 0 hard). TASK-447 note: since room-required
  # became hard (H29, weight 1) and H0 weight 2, "0 hard" means "every lesson placed
  # AND every lesson has a room", not just "no boxed lesson" - it is only reachable
  # after the room phase, and stays > 0 while the lab-room shortage lasts.
  if [ -z "$FIRST_FEASIBLE" ] && [ "$HAS_INIT" -eq 0 ] && [ "$HARD" = "0" ]; then
    FIRST_FEASIBLE="$ELAPSED"
  fi

  echo "$ELAPSED,$STATUS,$SCORE,$HARD,$MEDIUM,$SOFT,$ASSIGNED_PCT" >> "$RESULTS"

  # Print progress
  MIN=$((ELAPSED / 60))
  SEC=$((ELAPSED % 60))
  printf "  %02d:%02d  %-16s  score=%-40s  assigned=%s%%\n" "$MIN" "$SEC" "$STATUS" "$SCORE" "$ASSIGNED_PCT"

  # Stop early if solver finished
  if [ "$STATUS" = "NOT_SOLVING" ] && [ "$POLLS" -gt 2 ]; then
    echo "  Solver finished early."
    break
  fi

  POLLS=$((POLLS + 1))
  sleep "$POLL_INTERVAL"
done

# Stop solver
echo "[6/6] Stopping..."
curl -s -X DELETE http://localhost:8080/api/schedule/solve > /dev/null 2>&1 || true
sleep 2
./planner.sh stop 2>/dev/null || true
yes | ./planner.sh delete "$PROFILE" 2>/dev/null || true

# Get final stats
FINAL_LINE=$(tail -1 "$RESULTS")
FINAL_SCORE=$(echo "$FINAL_LINE" | cut -d',' -f3)
FINAL_HARD=$(echo "$FINAL_LINE" | cut -d',' -f4)
FINAL_MEDIUM=$(echo "$FINAL_LINE" | cut -d',' -f5)
FINAL_SOFT=$(echo "$FINAL_LINE" | cut -d',' -f6)
FINAL_ASSIGNED=$(echo "$FINAL_LINE" | cut -d',' -f7)
TOTAL_ELAPSED=$(($(date +%s) - SOLVE_START))

echo ""
echo "=== Results ==="
echo "Branch:          $BRANCH"
echo "Duration:        ${DURATION_MIN} min"
echo "First fully feasible (0 hard, rooms included):  ${FIRST_FEASIBLE:-never}s"
echo "Final score:     $FINAL_SCORE"
echo "Final assigned:  ${FINAL_ASSIGNED}%"
echo "CSV:             $RESULTS"

# Append to tracker
if [ -f "$TRACKER" ]; then
  cat >> "$TRACKER" << BENCH_EOF

## Benchmark: \`$BRANCH\` — $(date '+%Y-%m-%d %H:%M')

| Metric | Value |
|---|---|
| Duration | ${DURATION_MIN} min |
| First fully feasible (0 hard, rooms included) | ${FIRST_FEASIBLE:-never} sec |
| Final score | \`$FINAL_SCORE\` |
| Hard violations | $FINAL_HARD |
| Medium | $FINAL_MEDIUM |
| Soft | $FINAL_SOFT |
| Assignment % | ${FINAL_ASSIGNED}% |

<details>
<summary>Score progression (CSV)</summary>

\`\`\`csv
$(cat "$RESULTS")
\`\`\`
</details>
BENCH_EOF
  echo ""
  echo "Results appended to tracker: $TRACKER"
fi

echo "Done."
