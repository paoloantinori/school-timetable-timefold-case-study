#!/bin/bash
set -euo pipefail

# Production-volume guard (maintainer 8/09): BLOCK (exit 2) launches of
# prod-start-and-wait / planner.sh prod without an explicit PROFILE.
# The default is "production" = the PUBLIC SITE's data volume.
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

# Only check commands that actually LAUNCH these tools (not greps that mention them)
if echo "$command" | grep -qE '^\s*(PROFILE=\S+\s+)?(export\s+PATH=[^;]+;\s*)?(cd\s+\S+\s+&&\s*)?(PLANNER_JAVA\S*\s+\S+\s+)?\./(scripts/core/prod-start-and-wait|planner\.sh)\s' 2>/dev/null; then
  # Explicit PROFILE= in env?
  if echo "$command" | grep -qE 'PROFILE=[a-zA-Z0-9_-]+'; then
    exit 0
  fi
  # planner.sh prod <profile> or planner.sh start <profile> as argument (not a flag)?
  if echo "$command" | grep -qE '\./(planner\.sh) (prod|start) [a-zA-Z][a-zA-Z0-9_-]*' && ! echo "$command" | grep -qE '(prod|start) --'; then
    exit 0
  fi
  echo "BLOCKED: this launch targets prod-start-and-wait / planner.sh prod without" >&2
  echo "an explicit PROFILE. The default is production = the PUBLIC SITE's data" >&2
  echo "volume. State the profile explicitly:" >&2
  echo "  PROFILE=<profile> ... prod-start-and-wait.sh ..." >&2
  echo "  ./planner.sh prod <profile> [--fresh]" >&2
  echo "  ./planner.sh start <profile>" >&2
  exit 2
fi
exit 0
