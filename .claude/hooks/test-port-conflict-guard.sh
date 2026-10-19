#!/bin/bash
set -euo pipefail

# Test port conflict guard (maintainer, 9/09: "questo sembra una di quelle cose da
# prevenire con hooks" riferito al conflitto porta 8081): BLOCCA (exit 2) il
# lancio di test QuarkusTest quando la porta 8081 e' gia' occupata da un
# processo precedente (surefire fork, app prod, o altro).

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

# Only check commands that run QuarkusTest (test.sh with -c or -Dtest)
if echo "$command" | grep -qE 'test\.sh.*(-c|-Dtest|PLANNER_TEST)'; then
  PORT_PID=$(lsof -ti:8081 2>/dev/null || true)
  if [ -n "$PORT_PID" ]; then
    PORT_OWNER=$(ps -p "$PORT_PID" -o comm= 2>/dev/null || echo "unknown")
    echo "BLOCKED: porta 8081 gia' occupata da PID $PORT_PID ($PORT_OWNER)." >&2
    echo "Un test QuarkusTest precedente non ha rilasciato la porta." >&2
    echo "Fix: kill -9 $PORT_PID prima di rilanciare il test." >&2
    echo "Verifica: lsof -ti:8081 (vuoto = porta libera)" >&2
    exit 2
  fi
fi
exit 0
