#!/bin/bash
set -euo pipefail

# Read JSON input from stdin
input=$(cat)

# Get the file path from the tool input
file_path=$(echo "$input" | jq -r '.tool_input.file_path // .tool_input.path // empty')

# Check if it's a REST resource file
if [[ "$file_path" == *"rest/"*"Resource.java"* ]]; then
  # Extract the resource name for context
  resource_name=$(basename "$file_path" .java 2>/dev/null || echo "unknown")

  # Output a reminder message
  cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PostToolUse",
    "additionalContext": "📢 REMINDER: You modified REST resource '$resource_name'. If you added, removed, or changed any endpoints, please update the documentation at: claudedocs/rest-api.md"
  }
}
EOF
  exit 0
fi

# No output for non-REST files
exit 0
