#!/bin/bash
# TASK-655: lifecycle readout unico. Una chiamata, tutta la verità.
#
# Usage: ./scripts/api/lifecycle.sh [--file <path>] [--tail N]
#
# Legge lifecycle.jsonl (default: planner.lifecycle.file o ./lifecycle.jsonl)
# e lo status endpoint, e stampa la schermata operativa:
#   profilo, processo, attempt/stage corrente, score, deliveries, near-miss,
#   stall-check (secondi dall'ultima riga del log).
#
# This script is part of the api wrapper family: it reads, never writes.

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Defaults
LIFECYCLE_FILE="${PLANNER_LIFECYCLE_FILE:-lifecycle.jsonl}"
TAIL_COUNT=10
API_BASE="${PLANNER_API_BASE:-http://localhost:8080}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)   LIFECYCLE_FILE="$2"; shift 2 ;;
    --tail)   TAIL_COUNT="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: $0 [--file <path>] [--tail N]"
      echo "  --file   path to lifecycle.jsonl (default: \$PLANNER_LIFECYCLE_FILE or ./lifecycle.jsonl)"
      echo "  --tail   number of recent events to show (default: 10)"
      exit 0 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ ! -f "$LIFECYCLE_FILE" ]]; then
  echo "ERROR: lifecycle file not found: $LIFECYCLE_FILE"
  echo "Set PLANNER_LIFECYCLE_FILE or pass --file <path>"
  exit 1
fi

echo "═══ LIFECYCLE READOUT ═══"
echo "File: $LIFECYCLE_FILE ($(wc -l < "$LIFECYCLE_FILE" | tr -d ' ') events)"
echo ""

# ── Current status (from API) ──
echo "── STATUS (API) ──"
if STATUS_JSON=$(curl -s -m 3 "$API_BASE/api/schedule/status" 2>/dev/null); then
  python3 -c "
import json, sys
try:
  d = json.loads('''$STATUS_JSON''')
  solver = d.get('solverStatus', '?')
  stage = d.get('stagedStage') or '(idle)'
  attempt = d.get('stagedAttempt', 0)
  max_att = d.get('stagedMaxAttempts', 0)
  outcome = d.get('stagedOutcome') or '(running)'
  score = d.get('score') or '(unscored)'
  restoring = d.get('scoreRestorePending', False)
  print(f'  solver: {solver}  stage: {stage}  attempt: {attempt}/{max_att}')
  print(f'  outcome: {outcome}  score: {score}')
  if restoring: print(f'  ⚠ score restore in flight')
except Exception as e:
  print(f'  (API parse error: {e})')
" 2>/dev/null || echo "  (API unreachable)"
else
  echo "  (API unreachable at $API_BASE)"
fi
echo ""

# ── Recent lifecycle events ──
echo "── LAST $TAIL_COUNT EVENTS ──"
tail -"$TAIL_COUNT" "$LIFECYCLE_FILE" | python3 -c "
import sys, json
for line in sys.stdin:
  line = line.strip()
  if not line: continue
  try:
    e = json.loads(line)
    et = e.get('type', '?')
    ts = e.get('ts', '?')
    parts = [f'{k}={v}' for k, v in e.items() if k not in ('type', 'ts')]
    print(f'  [{ts}] {et}: {\" \".join(parts)}')
  except json.JSONDecodeError:
    print(f'  [raw] {line[:80]}')
" 2>/dev/null || tail -"$TAIL_COUNT" "$LIFECYCLE_FILE"
echo ""

# ── Deliveries this session (SOLVE_END events) ──
echo "── DELIVERIES (SOLVE_END) ──"
SOLVE_ENDS=$(grep '"SOLVE_END"' "$LIFECYCLE_FILE" | tail -5)
if [[ -n "$SOLVE_ENDS" ]]; then
  echo "$SOLVE_ENDS" | python3 -c "
import sys, json
for line in sys.stdin:
  try:
    e = json.loads(line.strip())
    outcome = e.get('outcome', '?')
    score = e.get('score', '?')
    wall = int(e.get('wallMillis', -1))
    wall_s = f'{wall//1000}s' if wall >= 0 else '?'
    hard = e.get('hardScore', '?')
    # NEAR-MISS check: |hard residual| small
    nm = ''
    try:
      hard_val = int(hard.split('/')[0].strip('['))
      if 0 < abs(hard_val) <= 3:
        nm = ' ← NEAR-MISS (scatta il blind puzzle)'
    except (ValueError, IndexError):
      pass
    print(f'  {outcome:10s} hard={hard:15s} wall={wall_s:6s}{nm}')
  except Exception:
    pass
" 2>/dev/null
else
  echo "  (no deliveries yet)"
fi
echo ""

# ── Stall check ──
echo "── STALL CHECK ──"
if [[ -n "$STATUS_JSON" ]] && echo "$STATUS_JSON" | python3 -c "
import json, sys
d = json.loads(sys.stdin.read())
sys.exit(0 if d.get('stagedStage') else 1)
" 2>/dev/null; then
  # Solver is running: check the age of the last lifecycle line
  LAST_TS=$(tail -1 "$LIFECYCLE_FILE" | python3 -c "
import json, sys
e = json.loads(sys.stdin.readline())
print(e.get('ts', ''))
" 2>/dev/null || echo "")
  if [[ -n "$LAST_TS" ]]; then
    STALL_SECS=$(python3 -c "
from datetime import datetime, timezone
try:
  last = datetime.fromisoformat('$LAST_TS'.replace('Z', '+00:00'))
  now = datetime.now(timezone.utc)
  print(int((now - last).total_seconds()))
except Exception:
  print(-1)
" 2>/dev/null || echo "-1")
    if [[ "$STALL_SECS" -gt 120 ]]; then
      echo "  ⚠ STALL: last lifecycle event was ${STALL_SECS}s ago (stage running but no events)"
      echo "  (the true stall detector is the age of the log, not the phase label)"
    else
      echo "  OK: last event ${STALL_SECS}s ago"
    fi
  else
    echo "  (no parseable last event)"
  fi
else
  echo "  (solver idle: no stall check needed)"
fi
echo "══════════════════════════"
