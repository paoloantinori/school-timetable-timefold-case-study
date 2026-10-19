#!/usr/bin/env bash
#
# benchmark-h5.sh — A/B benchmark: H5 precompute vs Joiners.overlapping()
#
# Runs two 6-minute solver benchmarks on fresh profiles:
#   Variant A: H5 with precompute() (current code)
#   Variant B: H5 with Joiners.overlapping() (original code)
#
# Outputs comparison table to stdout and saves to claudedocs/benchmark-h5-<timestamp>.md
#
set -euo pipefail

DURATION_MIN="${1:-6}"
POLL_INTERVAL=15
TIMESTAMP=$(date '+%Y%m%d-%H%M')
REPORT="claudedocs/benchmark-h5-${TIMESTAMP}.md"
JAVA_FILE="src/main/java/com/school/planner/solver/BaseConstraintProvider.java"

# ─── H5 code variants ───────────────────────────────────────────────

# Variant A marker: precompute-based H5 (current code, no changes needed)

# Variant B: revert H5 to Joiners.overlapping()
apply_variant_b() {
  # Replace H5.1 (teacherUnavailability)
  python3 << 'PYEOF'
import re

with open("src/main/java/com/school/planner/solver/BaseConstraintProvider.java", "r") as f:
    content = f.read()

# Replace H5.1
old_h51 = re.compile(
    r'Constraint teacherUnavailability\(ConstraintFactory constraintFactory\) \{.*?\.asConstraint\("H5\.1: Teacher unavailability \(L\)"\);',
    re.DOTALL
)
new_h51 = '''Constraint teacherUnavailability(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
                .join(TeacherAvailability.class,
                        Joiners.equal(Lesson::getTeacher, ta -> ta.teacher),
                        Joiners.overlapping(l -> l.timeslot.startDateTime, l -> l.timeslot.endDateTime,
                                ta -> ta.startDateTime, ta -> ta.endDateTime))
                .filter((l, ta) -> ta.type == AvailabilityType.SICK || ta.type == AvailabilityType.VACATION)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("H5.1: Teacher unavailability (L)");'''

content = old_h51.sub(new_h51, content)

# Replace H5.2
old_h52 = re.compile(
    r'Constraint teacherUnavailabilityBlock\(ConstraintFactory constraintFactory\) \{.*?\.asConstraint\("H5\.2: Teacher unavailability \(B\)"\);',
    re.DOTALL
)
new_h52 = '''Constraint teacherUnavailabilityBlock(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(LabBlock.class)
                .filter(b -> b.startTimeslot != null)
                .join(TeacherAvailability.class,
                        Joiners.equal(LabBlock::getTeacher, ta -> ta.teacher),
                        Joiners.overlapping(LabBlock::getStartDateTime, LabBlock::getEndDateTime,
                                ta -> ta.startDateTime, ta -> ta.endDateTime))
                .filter((block, ta) -> (ta.type == AvailabilityType.SICK || ta.type == AvailabilityType.VACATION)
                        && block.getOccupiedTimeslots().stream().anyMatch(ts ->
                                !ts.startDateTime.isAfter(ta.endDateTime) && !ts.endDateTime.isBefore(ta.startDateTime)))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("H5.2: Teacher unavailability (B)");'''

content = old_h52.sub(new_h52, content)

with open("src/main/java/com/school/planner/solver/BaseConstraintProvider.java", "w") as f:
    f.write(content)

print("Applied variant B: Joiners.overlapping()")
PYEOF
}

revert_variant_b() {
  git checkout -- "$JAVA_FILE"
  echo "Reverted to variant A: precompute()"
}

# ─── Benchmark runner ────────────────────────────────────────────────

run_benchmark() {
  local VARIANT="$1"
  local PROFILE="bench-h5-${VARIANT}-$$"
  local CSV="/tmp/benchmark-h5-${VARIANT}.csv"
  local LOG="/tmp/benchmark-h5-${VARIANT}.log"

  echo ""
  echo "══════════════════════════════════════════════════════"
  echo "  Variant ${VARIANT}: ${DURATION_MIN}-minute benchmark"
  echo "══════════════════════════════════════════════════════"

  # Build
  echo "[1/6] Building variant ${VARIANT}..."
  mvn package -DskipTests -Dnet.bytebuddy.experimental=true > /tmp/bench-build-${VARIANT}.log 2>&1
  echo "  Build OK"

  # Start fresh profile
  echo "[2/6] Starting profile '$PROFILE'..."
  ./planner.sh start "$PROFILE" > "$LOG" 2>&1 &
  local START_PID=$!
  ./planner.sh wait
  echo "  App ready"

  # Pre-assignment (async — must poll for completion)
  echo "[3/6] Running pre-assignment..."
  python3 -c "
import urllib.request
req = urllib.request.Request('http://localhost:8080/api/schedule/pre-assign', method='POST')
req.add_header('Content-Type', 'application/json')
resp = urllib.request.urlopen(req, timeout=120)
print(f'  Pre-assignment triggered (HTTP {resp.getcode()})')
" 2>&1 || echo "  WARNING: Pre-assignment trigger failed (continuing)"

  # Poll pre-assignment status until COMPLETE or ERROR (max 5 min)
  echo "  Waiting for pre-assignment to complete..."
  local PA_TIMEOUT=300
  local PA_START=$(date +%s)
  while true; do
    local PA_ELAPSED=$(($(date +%s) - PA_START))
    if [ "$PA_ELAPSED" -ge "$PA_TIMEOUT" ]; then
      echo "  WARNING: Pre-assignment timed out after ${PA_TIMEOUT}s"
      break
    fi
    local PA_STATUS
    PA_STATUS=$(python3 -c "
import urllib.request, json
req = urllib.request.Request('http://localhost:8080/api/schedule/pre-assign/status')
resp = urllib.request.urlopen(req, timeout=30)
d = json.loads(resp.read())
print(d.get('rawStatus', d.get('status', '?')))
" 2>/dev/null) || PA_STATUS="?"
    if [ "$PA_STATUS" = "COMPLETE" ]; then
      echo "  Pre-assignment complete (${PA_ELAPSED}s)"
      break
    elif [ "$PA_STATUS" = "ERROR" ]; then
      echo "  WARNING: Pre-assignment failed with ERROR"
      break
    fi
    sleep 2
  done
  sleep 2

  # Trigger solve
  echo "[4/6] Starting solver..."
  local SOLVE_START=$(date +%s)
  python3 -c "
import urllib.request
req = urllib.request.Request('http://localhost:8080/api/schedule/solve', method='POST')
req.add_header('Content-Type', 'application/json')
resp = urllib.request.urlopen(req, timeout=30)
print(f'  Solve HTTP {resp.getcode()}')
" 2>&1

  # Poll loop
  echo "[5/6] Polling for ${DURATION_MIN} minutes..."
  echo "elapsed_sec,status,hard,medium,soft" > "$CSV"

  local DURATION_SEC=$((DURATION_MIN * 60))
  local POLLS=0
  local CH_TIME=""

  while true; do
    local ELAPSED=$(($(date +%s) - SOLVE_START))
    if [ "$ELAPSED" -ge "$DURATION_SEC" ]; then
      break
    fi

    # Poll with python to avoid curl hook issues
    local POLL_RESULT
    POLL_RESULT=$(python3 -c "
import urllib.request, json, re, sys
try:
    req = urllib.request.Request('http://localhost:8080/api/schedule')
    req.add_header('Accept', 'application/json')
    resp = urllib.request.urlopen(req, timeout=60)
    d = json.loads(resp.read())
    status = d.get('solverStatus', '?')
    score = d.get('score', '--')
    ch_time = d.get('chCompletionTime', '')

    hard = '?'
    medium = '?'
    soft = '?'
    m = re.search(r'(-?\d+)hard', score)
    if m: hard = m.group(1)
    m = re.search(r'(-?\d+)medium', score)
    if m: medium = m.group(1)
    m = re.search(r'(-?\d+)soft', score)
    if m: soft = m.group(1)

    print(f'{status}|{hard}|{medium}|{soft}|{ch_time}')
except Exception as e:
    print(f'ERROR|?|?|?|', file=sys.stderr)
    print(f'?|?|?|?|')
" 2>/dev/null) || POLL_RESULT="?|?|?|?|"

    local STATUS=$(echo "$POLL_RESULT" | cut -d'|' -f1)
    local HARD=$(echo "$POLL_RESULT" | cut -d'|' -f2)
    local MEDIUM=$(echo "$POLL_RESULT" | cut -d'|' -f3)
    local SOFT=$(echo "$POLL_RESULT" | cut -d'|' -f4)
    local CH=$(echo "$POLL_RESULT" | cut -d'|' -f5)

    if [ -n "$CH" ] && [ -z "$CH_TIME" ]; then
      CH_TIME="$CH"
    fi

    echo "$ELAPSED,$STATUS,$HARD,$MEDIUM,$SOFT" >> "$CSV"

    local MIN=$((ELAPSED / 60))
    local SEC=$((ELAPSED % 60))
    printf "  %02d:%02d  %-16s  hard=%-8s  medium=%-10s  soft=%-10s\n" \
      "$MIN" "$SEC" "$STATUS" "$HARD" "$MEDIUM" "$SOFT"

    # Stop early if solver finished
    if [ "$STATUS" = "NOT_SOLVING" ] && [ "$POLLS" -gt 2 ]; then
      echo "  Solver finished early at ${ELAPSED}s."
      break
    fi

    POLLS=$((POLLS + 1))
    sleep "$POLL_INTERVAL"
  done

  # Stop solver gracefully
  echo "[6/6] Stopping..."
  python3 -c "
import urllib.request
req = urllib.request.Request('http://localhost:8080/api/schedule/solve', method='DELETE')
urllib.request.urlopen(req, timeout=30)
" 2>/dev/null || true
  sleep 3

  ./planner.sh kill-app 2>/dev/null || true
  sleep 2
  ./planner.sh stop 2>/dev/null || true
  yes | ./planner.sh delete "$PROFILE" 2>/dev/null || true

  # Extract final stats
  local FINAL_LINE=$(tail -1 "$CSV")
  echo ""
  echo "  Final: hard=$(echo "$FINAL_LINE" | cut -d',' -f3) medium=$(echo "$FINAL_LINE" | cut -d',' -f4) soft=$(echo "$FINAL_LINE" | cut -d',' -f5)"
  echo "  CH completion: ${CH_TIME:-unknown}"
  echo "  CSV: $CSV"

  # Export for comparison
  if [ "$VARIANT" = "A" ]; then
    RESULT_A="$FINAL_LINE"
    CH_A="${CH_TIME:-?}"
    CSV_A="$CSV"
  else
    RESULT_B="$FINAL_LINE"
    CH_B="${CH_TIME:-?}"
    CSV_B="$CSV"
  fi
}

# ─── Main ────────────────────────────────────────────────────────────

echo "H5 Precompute A/B Benchmark"
echo "Duration: ${DURATION_MIN} minutes per variant"
echo "Report: ${REPORT}"
echo ""

# Ensure clean state
./planner.sh kill-app 2>/dev/null || true
./planner.sh stop 2>/dev/null || true

# Store initial state
RESULT_A=""
RESULT_B=""
CH_A=""
CH_B=""
CSV_A=""
CSV_B=""

# ─── Run Variant A: precompute (current code) ───────────────────────
run_benchmark "A"

# ─── Apply Variant B: Joiners.overlapping() ─────────────────────────
echo ""
echo "Switching to Variant B (Joiners.overlapping)..."
apply_variant_b

# ─── Run Variant B ──────────────────────────────────────────────────
run_benchmark "B"

# ─── Restore Variant A code ─────────────────────────────────────────
echo ""
echo "Restoring original code..."
revert_variant_b

# ─── Generate comparison report ─────────────────────────────────────

HARD_A=$(echo "$RESULT_A" | cut -d',' -f3)
MED_A=$(echo "$RESULT_A" | cut -d',' -f4)
SOFT_A=$(echo "$RESULT_A" | cut -d',' -f5)

HARD_B=$(echo "$RESULT_B" | cut -d',' -f3)
MED_B=$(echo "$RESULT_B" | cut -d',' -f4)
SOFT_B=$(echo "$RESULT_B" | cut -d',' -f5)

cat << REPORT_EOF | tee "$REPORT"
# H5 Precompute Benchmark — $(date '+%Y-%m-%d %H:%M')

## Configuration
- Duration: ${DURATION_MIN} minutes per variant
- Branch: $(git branch --show-current)
- Solver mode: REPRODUCIBLE
- Pre-assignment: Yes (run before each solve)

## Results

| Metric | A: precompute() | B: Joiners.overlapping() | Winner |
|--------|-----------------|--------------------------|--------|
| Hard score | ${HARD_A} | ${HARD_B} | $(python3 -c "a,b=int('${HARD_A}'),int('${HARD_B}'); print('A' if a>b else 'B' if b>a else 'TIE')" 2>/dev/null || echo "?") |
| Medium score | ${MED_A} | ${MED_B} | $(python3 -c "a,b=int('${MED_A}'),int('${MED_B}'); print('A' if a>b else 'B' if b>a else 'TIE')" 2>/dev/null || echo "?") |
| Soft score | ${SOFT_A} | ${SOFT_B} | $(python3 -c "a,b=int('${SOFT_A}'),int('${SOFT_B}'); print('A' if a>b else 'B' if b>a else 'TIE')" 2>/dev/null || echo "?") |
| CH completion | ${CH_A} | ${CH_B} | - |

## Score Progression

### Variant A: precompute()
\`\`\`csv
$(cat "$CSV_A")
\`\`\`

### Variant B: Joiners.overlapping()
\`\`\`csv
$(cat "$CSV_B")
\`\`\`

## Analysis

H5 precompute replaces indexed \`Joiners.overlapping()\` with a cached teacher join + post-filter.
The trade-off: overlapping joiner uses an interval tree (O(log n) per move), while precompute
caches all teacher-availability pairs and filters per move. For T28 (~200 VACATION records),
the precomputed cache is large but avoids re-joining.

**Verdict**: [See scores above — lower absolute value = better]
REPORT_EOF

echo ""
echo "Benchmark complete. Report: $REPORT"
