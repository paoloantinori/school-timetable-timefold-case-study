#!/bin/bash
set -euo pipefail

# MAVEN_OPTS guard (pattern #1 del mining 8/09: 5+ episodi in 4 settimane):
# `-Dplanner.*` flags passati via MAVEN_OPTS NON raggiungono mai la JVM forked
# da `mvn quarkus:run`. BLOCK (exit 2) quando -Dplanner. e' DENTRO l'assegnazione
# MAVEN_OPTS (non altrove nel comando: heap in MAVEN_OPTS + flag sulla riga java e' la forma corretta).

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

if echo "$command" | grep -qE 'MAVEN_OPTS=[^ ]*-Dplanner\.'; then
  echo "BLOCKED: MAVEN_OPTS contiene -Dplanner.* che NON raggiunge la JVM forked da quarkus:run" >&2
  echo "La forma corretta: flag sulla riga java -jar, o PLANNER_JAVA_OPTS con prod-start-and-wait" >&2
  echo "Vedi CLAUDE.md MAVEN_OPTS gap e TASK-579 launch-preflight" >&2
  exit 2
fi
exit 0
