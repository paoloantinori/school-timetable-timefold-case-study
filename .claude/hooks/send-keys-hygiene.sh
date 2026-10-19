#!/bin/bash
set -euo pipefail

# send-keys hygiene (pattern #4 del mining 8/09: paste con newline incorporati
# = submit parziali, turni morti multi-voce): `tmux send-keys` con testo
# contenente newline incorporati spezza il messaggio in N submit parziali.
# La sequenza corretta e': load-buffer + paste-buffer -p + verify marker + Enter
# in comandi SEPARATI. REMINDER (non block) perche' casi legittimi esistono
# (send-keys Enter dopo un paste verificato).

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

# send-keys with literal \n or actual newlines in the text argument
if echo "$command" | grep -qE 'send-keys.*["'"'"'][^"'"'"']*\n[^"'"'"']*["'"'"']' 2>/dev/null; then
  cat <<'EOF'
{
  "reminder": "SEND-KEYS CON NEWLINE: il testo contiene newline incorporati che diventano submit parziali. La sequenza corretta: (1) scp file, (2) tmux load-buffer file, (3) tmux paste-buffer -p (bracketed paste), (4) VERIFICA il marker [Pasted text #N] nel pane, (5) tmux send-keys Enter in un comando separato. CLAUDE.local.md 'Contromisure procedurali'."
}
EOF
  exit 0
fi
exit 0
