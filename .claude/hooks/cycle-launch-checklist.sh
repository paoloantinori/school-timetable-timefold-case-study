#!/bin/bash
set -euo pipefail

# Cycle-launch checklist (PostToolUse, matcher Bash): fires right after a
# command that launches a measurement cycle (solve-staged) or the app for one
# (prod-start-and-wait). The 8/09 incident class: incomplete launch contracts
# (PROFILE x2, checkpoint-capture x1) discovered late. This hook forces the
# verification into the launch turn itself - the doctor runs the checks, the
# operator reads them against the mandate's flag contract.

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'solve-staged|prod-start-and-wait'; then
  echo "LAUNCH-CHECKLIST (obbligatorio nel turno di lancio, non dopo):"
  echo "  1. Flag reali sulla JVM (ps, NON la riga che credi di aver scritto)"
  echo "  2. Banner attesi del mandato nel log (grep al volo)"
  echo "  3. Dir checkpoint dopo il primo confine (il substrato snapshot)"
  echo "  Strumento: ./scripts/benchmark/cycle-launch-doctor.sh [laptop]"
  echo "  Se un flag del contratto manca: FERMA ORA (il costo cresce col ciclo)."
fi
exit 0
