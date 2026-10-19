#!/bin/bash
set -euo pipefail

# tmux window guard (pattern #3: finestra per indice = 5 tick persi + 4 turni morti):
# BLOCK (exit 2) target ccbot:N (cifra) invece del nome finestra.
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE '(ccbot|shconsole):[0-9]'; then
  echo "BLOCKED: tmux target per INDICE (ccbot:N). Gli indici slittano a ogni riavvio." >&2
  echo "La forma corretta: -t ccbot:<nome-finestra> (vedi CLAUDE.local.md)." >&2
  exit 2
fi
exit 0
