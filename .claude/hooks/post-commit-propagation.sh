#!/usr/bin/env bash
#
# post-commit-propagation.sh — PostToolUse hook for Bash
#
# Reads JSON from stdin (Claude Code hook protocol), checks if the
# command was a "git commit", and if so, outputs a JSON response
# with additionalContext reminding Claude to check for cross-branch
# propagation needs.
#
# Exit 0 with no output = no-op (non-commit commands).
# Exit 0 with JSON output = inject context into Claude's session.
#

set -euo pipefail

# Read the hook input from stdin
INPUT=$(cat)

# Extract the command that was executed
COMMAND=$(echo "$INPUT" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('command', ''))
except:
    print('')
" 2>/dev/null || echo "")

# Only trigger on git commit commands
case "$COMMAND" in
  *"git commit"*)
    ;;
  *)
    exit 0
    ;;
esac

# Output the reminder as additionalContext
cat <<'EOF'
{
  "additionalContext": "PROPAGATION CHECK: You just committed changes. Check if any modified files are shared across branches (DataGenerator.java, application.properties, shared domain entities, shared REST resources). If so, cherry-pick this commit to all other feature/* branches or ask the user. Run ./scripts/check-propagation.sh to verify. After propagating DataGenerator changes, remind about database profile recreation."
}
EOF
