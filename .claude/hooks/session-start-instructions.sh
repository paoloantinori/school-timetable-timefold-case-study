#!/bin/bash
# SessionStart hook: Proactively instruct Claude about wrapper scripts
# This runs at the start of each session to establish correct patterns

cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "## ⚠️ PROJECT CONVENTIONS - Read This First\n\n### Use Wrapper Scripts, NOT Raw Commands\n\n**NEVER use these directly - they will be BLOCKED:**\n- `curl` → use `./scripts/api/api.sh` instead\n- `psql` or `podman exec ... psql` → use `./scripts/db-query.sh` or `python3 scripts/db-stats.py`\n- `podman ps --filter` → use `./scripts/podman-tools.sh list`\n- Background `&` processes → use `./scripts/start-and-wait.sh`\n\n### Quick Reference\n\n| Instead of | Use |\n|------------|-----|\n| `curl ... \\| jq .solverStatus` | `./scripts/api/api.sh status` |\n| `curl ... \\| jq .score` | `./scripts/api/api.sh score` |\n| `curl -X POST .../pre-assign` | `./scripts/api/api.sh pre-assign` |\n| `podman exec ... psql -c \"...\"` | `./scripts/db-query.sh \"...\"` |\n| `podman ps` | `./scripts/podman-tools.sh list` |\n\n### Why?\nDirect commands with pipes are NOT whitelisted. Only wrapper scripts are."
  }
}
EOF
exit 0
