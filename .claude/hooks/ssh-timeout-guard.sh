#!/bin/bash
set -euo pipefail

# SSH timeout guard (pattern #7 del mining 8/09: ssh appeso senza timeout,
# 2-3 episodi in 4 settimane): qualsiasi ssh verso "laptop" senza ConnectTimeout
# puo' restare appeso per il timeout TCP di default (fino a 120s) su un
# tunnel rotto. REMINDER (non block) per aggiungere ConnectTimeout.
# La recipe consolidata: -o ConnectTimeout=20 -o ClearAllForwardings=yes.

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'ssh.*\bmac\b' && ! echo "$command" | grep -q 'ConnectTimeout'; then
  cat <<'EOF'
{
  "reminder": "SSH-SENZA-TIMEOUT verso laptop: aggiungi -o ConnectTimeout=20 (un tunnel rotto blocca fino a 120s di TCP timeout). La recipe consolidata completa: -o ConnectTimeout=20 -o ClearAllForwardings=yes (ClearAllForwardings evita i forward remoti orfani che intasano le connessioni successive: incidente 8/09)."
}
EOF
  exit 0
fi
exit 0
