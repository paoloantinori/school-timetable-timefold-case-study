#!/bin/bash
set -euo pipefail

# Read JSON input from stdin
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

# Helper function to extract the core npm command from a complex command line
extract_npm_command() {
    local cmd="$1"
    # Remove trailing pipes and redirects (everything after | or >)
    cmd=$(echo "$cmd" | sed 's/[\|>].*//')
    # Remove cd prefixes (e.g., "cd src/main/webui &&")
    cmd=$(echo "$cmd" | sed 's/cd [^&]* && *//g')
    # Remove bash -c wrapper prefixes
    cmd=$(echo "$cmd" | sed 's/bash -[^ ]* *//g')
    echo "$cmd"
}

# Pattern: npm run build (anywhere in command) → use ./scripts/ui/ui-build.sh
core_cmd=$(extract_npm_command "$command")
if echo "$core_cmd" | grep -qE "npm\s+run\s+build"; then
    # Check if already using the wrapper script
    if echo "$command" | grep -qE "scripts/ui/ui-build\.sh|scripts/rebuild-ui\.sh|scripts/core/rebuild\.sh"; then
        # Already using wrapper - allow
        echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
        exit 0
    fi

    cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/ui/ui-build.sh instead of npm run build",
    "additionalContext": "CORRECTIVE ACTION: Replace 'npm run build' with ./scripts/ui/ui-build.sh. For a full rebuild (kill app + build + restart), use ./scripts/rebuild-ui.sh instead."
  }
}
EOF
    exit 0
fi

# Pattern: curl | jq → use api/api.sh (except for schedule export)
if echo "$command" | grep -qE "curl.*\|.*jq"; then
  # Allow curl for schedule export (large JSON responses)
  if echo "$command" | grep -qE "/api/schedule/export"; then
    echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
    exit 0
  fi

  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/api/api.sh instead of curl | jq",
    "additionalContext": "CORRECTIVE ACTION: Replace your curl | jq command with ./scripts/api/api.sh. Available commands: ./scripts/api/api.sh status, ./scripts/api/api.sh score, ./scripts/api/api.sh health, ./scripts/api/api.sh teacher N, ./scripts/api/api.sh group NAME. For schedule export, use: curl -s http://localhost:8080/api/schedule/export > local/data/schedule.json"
  }
}
EOF
  exit 0
fi

# Pattern: curl to localhost:8080 without jq → use api/api.sh (except for schedule export)
if echo "$command" | grep -qE "curl.*localhost:8080"; then
  # Allow curl for schedule export (large JSON responses)
  if echo "$command" | grep -qE "/api/schedule/export"; then
    echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
    exit 0
  fi

  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/api/api.sh instead of curl to localhost:8080",
    "additionalContext": "CORRECTIVE ACTION: Replace your curl command with ./scripts/api/api.sh. Available commands: ./scripts/api/api.sh status, ./scripts/api/api.sh score, ./scripts/api/api.sh health. For schedule export, use: curl -s http://localhost:8080/api/schedule/export > local/data/schedule.json"
  }
}
EOF
  exit 0
fi

# Pattern: podman ps --filter → use container/podman-tools.sh
if echo "$command" | grep -qE "podman\s+ps.*--filter"; then
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/container/podman-tools.sh instead of podman ps --filter",
    "additionalContext": "CORRECTIVE ACTION: Replace podman ps --filter with ./scripts/container/podman-tools.sh list to see planner containers."
  }
}
EOF
  exit 0
fi

# Pattern: podman exec ... psql → use db/db-query.sh or db/db-stats.py
if echo "$command" | grep -qE "podman.*exec.*psql|psql.*-c"; then
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/db/db-query.sh or python3 scripts/db/db-stats.py instead of psql",
    "additionalContext": "CORRECTIVE ACTION: Replace psql with one of these whitelisted scripts:\n\n• For common queries: python3 scripts/db/db-stats.py status|unassigned|conflicts|pinned|teacher N|group NAME|date YYYY-MM-DD\n• For arbitrary SQL: ./scripts/db/db-query.sh \"SELECT ...\" (use -f json for JSON output)"
  }
}
EOF
  exit 0
fi

# Pattern: background process & → check if using whitelisted scripts
if echo "$command" | grep -qE "&\s*$|&\s*(wait|sleep)"; then
  # Allow background processes for whitelisted helper scripts (api/api.sh, db/db-query.sh, etc.)
  # These are safe to run in background for long-running API calls
  if echo "$command" | grep -qE "(api/api\.sh|db/db-query\.sh|db/db-stats\.py|api/app-health\.sh|container/podman-tools\.sh)"; then
    # Allow - these are safe background operations
    echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
    exit 0
  fi

  # Block non-whitelisted background processes (e.g., mvn quarkus:run &)
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/core/dev-start-and-wait.sh for server startup instead of background processes",
    "additionalContext": "CORRECTIVE ACTION: For starting the server, use ./scripts/core/dev-start-and-wait.sh dev instead of backgrounding with &. For API calls, using ./scripts/api/api.sh in background is allowed."
  }
}
EOF
  exit 0
fi

# Pattern: podman logs → use container/podman-tools.sh logs
if echo "$command" | grep -qE "podman\s+logs"; then
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/container/podman-tools.sh logs instead of podman logs",
    "additionalContext": "CORRECTIVE ACTION: Replace podman logs with ./scripts/container/podman-tools.sh logs <profile> to see container logs."
  }
}
EOF
  exit 0
fi

# Pattern: podman exec ... (any exec except psql handled above) → check if container/podman-tools.sh has it
if echo "$command" | grep -qE "podman\s+exec" && ! echo "$command" | grep -qE "podman.*exec.*psql"; then
  cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Consider using ./scripts/container/podman-tools.sh shell instead of podman exec",
    "additionalContext": "CORRECTIVE ACTION: For interactive shell, use ./scripts/container/podman-tools.sh shell <profile>. For database queries, use ./scripts/db/db-query.sh or python3 scripts/db/db-stats.py."
  }
}
EOF
  exit 0
fi

# Pattern: helper scripts (check FIRST, before any mvn checks)
if echo "$command" | grep -qE "scripts/(ui/|core/|api/|db/|benchmark/|validate/|analyze/|container/)"; then
    echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
    exit 0
fi

# Pattern: direct mvn invocation → use helper scripts or ./mvnw
core_cmd=$(extract_npm_command "$command")
if echo "$core_cmd" | grep -qE "^\s*mvn\s"; then
    # Allow ./mvnw (Maven wrapper)
    if echo "$core_cmd" | grep -qE "^\s*\./mvnw\s"; then
        echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
        exit 0
    fi

    # Check if using planner.sh (which wraps mvn quarkus:run)
    if echo "$command" | grep -qE "planner\.sh"; then
        echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
        exit 0
    fi

    cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use helper scripts instead of direct mvn invocation",
    "additionalContext": "CORRECTIVE ACTION: Replace 'mvn <command>' with appropriate helper:\n\n• Frontend build: ./scripts/ui/ui-build.sh\n• Full rebuild (kill + build + restart): ./scripts/rebuild-ui.sh\n• Backend-only rebuild: ./scripts/core/rebuild.sh\n• Quick test: ./scripts/core/test.sh\n• Benchmark: ./scripts/benchmark/benchmark-branch.sh\n• Maven wrapper: ./mvnw <command> (preferred for direct Maven access)\n• Server management: ./planner.sh start/run/stop"
  }
}
EOF
    exit 0
fi

# Default: allow execution
echo '{"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "allow"}}'
