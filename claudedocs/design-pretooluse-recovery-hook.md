# Design: PreToolUse Hook for Automatic Command Recovery

**Created**: 2026-02-23
**Status**: Design Complete
**Task**: task-076

## Overview

A PreToolUse hook that detects blocked command patterns and returns JSON with `additionalContext` to guide Claude to the correct wrapper script. Uses **exit 0** (not exit 2) to ensure automatic recovery.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Claude Code Tool Call                        │
│                   (Bash: curl | jq ...)                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PreToolUse Hook                                │
│                 (command-redirect.sh)                           │
│                                                                  │
│  1. Parse command from stdin JSON                               │
│  2. Match against blocked patterns                              │
│  3. Generate JSON response with:                                │
│     - permissionDecision: "deny"                                │
│     - additionalContext: "CORRECTIVE ACTION: ..."              │
│  4. Exit 0 (not exit 2!)                                        │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Claude Receives Context                        │
│                                                                  │
│  "CORRECTIVE ACTION: Instead of 'curl | jq', use:              │
│   ./scripts/api/api.sh <command>                                    │
│   Available: status, score, health, analysis, teacher, etc."   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Claude Retries Correctly                       │
│                                                                  │
│   ./scripts/api/api.sh status   ✓                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Pattern Detection Matrix

| Blocked Pattern | Detected By | Wrapper Script | Example Alternative |
|-----------------|-------------|----------------|---------------------|
| `curl ... \| jq` | `curl.*\|.*jq` | `./scripts/api/api.sh` | `api.sh status` |
| `curl localhost:8080/api` | `curl.*localhost:8080/api` | `./scripts/api/api.sh` | `api.sh score` |
| `podman exec ... psql` | `podman exec.*psql` | `./scripts/db-query.sh` | `db-query.sh "SELECT..."` |
| `podman ps --filter` | `podman ps.*--filter` | `./scripts/podman-tools.sh` | `podman-tools.sh list` |
| `podman exec planner-pg` | `podman exec planner-pg` | `./scripts/db-query.sh` | `db-query.sh "..."` |
| `./planner.sh start ... &` | `planner.sh start.*&` | `./scripts/start-and-wait.sh` | `start-and-wait.sh dev` |
| `curl ... health` | `curl.*health\|curl.*localhost.*-f` | `./scripts/app-health.sh` | `app-health.sh` |

## Command Mapping

### API Calls → scripts/api.sh

```bash
# Blocked:
curl -s http://localhost:8080/api/schedule | jq .solverStatus
curl -s http://localhost:8080/api/schedule | jq .score

# Correct:
./scripts/api/api.sh status
./scripts/api/api.sh score
./scripts/api/api.sh analysis
./scripts/api/api.sh teacher 1
./scripts/api/api.sh lessons 50
./scripts/api/api.sh health
```

### Database Queries → scripts/db-query.sh or db-stats.py

```bash
# Blocked:
podman exec planner-pg psql -U planner -d planner -c "SELECT * FROM teacher"
podman exec -it planner-pg psql -c "SELECT count(*) FROM lesson"

# Correct:
./scripts/db-query.sh "SELECT * FROM teacher"
./scripts/db-query.sh -f json "SELECT count(*) FROM lesson"
python3 scripts/db-stats.py status
python3 scripts/db-stats.py unassigned
python3 scripts/db-stats.py conflicts
```

### Server Lifecycle → scripts/start-and-wait.sh

```bash
# Blocked (background without wait):
./planner.sh start dev &
./planner.sh run > /tmp/log 2>&1 &

# Correct (encapsulated):
./scripts/start-and-wait.sh dev
./scripts/rebuild-ui.sh
```

### Container Ops → scripts/podman-tools.sh

```bash
# Blocked:
podman ps --filter name=planner
podman logs planner-pg

# Correct:
./scripts/podman-tools.sh list
./scripts/podman-tools.sh logs dev
```

## Hook Script Design

### File Location
```
~/.claude/hooks/command-redirect.sh
```

### Script Structure

```bash
#!/bin/bash
#
# command-redirect.sh - PreToolUse hook for automatic command recovery
#
# Detects blocked command patterns and returns JSON with corrective actions.
# Uses exit 0 (not exit 2) to ensure Claude can act on the feedback.
#
# Reference: memory/hook-blocking-recovery-patterns.md

set -euo pipefail

# Read hook input
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# If no command, allow
[[ -z "$COMMAND" ]] && exit 0

#===========================================
# Pattern Detection Functions
#===========================================

check_curl_api() {
    # Detect: curl ... localhost:8080/api ... | jq
    if echo "$COMMAND" | grep -qE 'curl.*localhost:8080/api'; then
        # Extract what they're trying to get
        local suggestion="./scripts/api/api.sh"

        if echo "$COMMAND" | grep -q 'solverStatus\|/api/schedule[^/]'; then
            suggestion="./scripts/api/api.sh status"
        elif echo "$COMMAND" | grep -q 'score'; then
            suggestion="./scripts/api/api.sh score"
        elif echo "$COMMAND" | grep -q 'analysis'; then
            suggestion="./scripts/api/api.sh analysis"
        elif echo "$COMMAND" | grep -q 'health'; then
            suggestion="./scripts/api/api.sh health"
        elif echo "$COMMAND" | grep -q 'teachers'; then
            suggestion="./scripts/api/api.sh teacher <id>"
        elif echo "$COMMAND" | grep -q 'lessons'; then
            suggestion="./scripts/api/api.sh lessons [limit]"
        else
            suggestion="./scripts/api/api.sh status|score|health|analysis"
        fi

        output_deny "curl with pipes not whitelisted" \
            "Instead of 'curl ... | jq', use: ${suggestion}

Available api.sh commands:
  status          - Get solver status
  score           - Get current score
  analysis        - Get constraint analysis
  health          - Health check
  teacher ID      - Get teacher by ID
  lessons [N]     - List N lessons (default 20)

Example: ./scripts/api/api.sh score"
        return 0
    fi
    return 1
}

check_psql() {
    # Detect: podman exec ... psql
    if echo "$COMMAND" | grep -qE 'podman exec.*psql|psql.*-U planner'; then
        output_deny "Direct psql commands not whitelisted" \
            "Instead of 'podman exec ... psql', use wrapper scripts:

For queries:
  ./scripts/db-query.sh \"SELECT * FROM teacher\"
  ./scripts/db-query.sh -f json \"SELECT count(*) FROM lesson\"

For common stats (Python):
  python3 scripts/db-stats.py status      # Entity counts
  python3 scripts/db-stats.py unassigned  # Lessons without slot
  python3 scripts/db-stats.py conflicts   # Double-bookings
  python3 scripts/db-stats.py teacher 1   # Teacher's lessons

The wrapper scripts are whitelisted and safe to use."
        return 0
    fi
    return 1
}

check_podman() {
    # Detect: podman ps, podman logs, etc (except exec for non-psql)
    if echo "$COMMAND" | grep -qE 'podman (ps|logs|inspect).*planner' && \
       ! echo "$COMMAND" | grep -q 'psql'; then
        output_deny "Direct podman commands not whitelisted" \
            "Instead of direct podman, use:

  ./scripts/podman-tools.sh list       # List containers
  ./scripts/podman-tools.sh logs dev   # Container logs
  ./scripts/podman-tools.sh status dev # Check if running
  ./scripts/app-health.sh              # App health check

The wrapper scripts are whitelisted and safe to use."
        return 0
    fi
    return 1
}

check_server_start() {
    # Detect: ./planner.sh start/run ... &
    if echo "$COMMAND" | grep -qE 'planner.sh (start|run).*&'; then
        output_deny "Background start without wait detected" \
            "Instead of 'planner.sh start ... &', use the encapsulated script:

  ./scripts/start-and-wait.sh [profile] [timeout]

This starts the app in background AND polls until ready.
Default: start-and-wait.sh dev (120s timeout)

For frontend changes:
  ./scripts/rebuild-ui.sh   # Kill, rebuild, restart, wait

These scripts handle the background+poll pattern correctly."
        return 0
    fi
    return 1
}

#===========================================
# Output Functions
#===========================================

output_deny() {
    local reason="$1"
    local context="$2"

    # Escape for JSON
    reason=$(echo "$reason" | jq -Rs '.')
    context=$(echo "$context" | jq -Rs '.')

    cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": ${reason},
    "additionalContext": "CORRECTIVE ACTION: ${context}"
  }
}
EOF
    exit 0  # NOT exit 2!
}

#===========================================
# Main
#===========================================

# Check patterns in order (most specific first)
check_curl_api && exit 0
check_psql && exit 0
check_podman && exit 0
check_server_start && exit 0

# Allow everything else
exit 0
```

## Hook Registration

Add to `~/.claude/settings.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "$HOME/.claude/hooks/command-redirect.sh",
            "timeout": 5
          }
        ]
      }
    ]
  }
}
```

## Testing Plan

### Test Case 1: curl | jq
```bash
# Input command:
curl -s http://localhost:8080/api/schedule | jq .solverStatus

# Expected hook output:
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "curl with pipes not whitelisted",
    "additionalContext": "CORRECTIVE ACTION: Instead of 'curl ... | jq', use: ./scripts/api/api.sh status\n\nAvailable api.sh commands:..."
  }
}

# Expected Claude behavior:
# - Receives additionalContext
# - Retries with: ./scripts/api/api.sh status
```

### Test Case 2: podman exec psql
```bash
# Input command:
podman exec planner-pg psql -U planner -d planner -c "SELECT * FROM teacher"

# Expected hook output:
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Direct psql commands not whitelisted",
    "additionalContext": "CORRECTIVE ACTION: Instead of 'podman exec ... psql', use wrapper scripts:..."
  }
}

# Expected Claude behavior:
# - Receives additionalContext
# - Retries with: ./scripts/db-query.sh "SELECT * FROM teacher"
```

### Test Case 3: background start
```bash
# Input command:
./planner.sh start dev &

# Expected hook output:
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Background start without wait detected",
    "additionalContext": "CORRECTIVE ACTION: Instead of 'planner.sh start ... &', use: ./scripts/start-and-wait.sh dev..."
  }
}

# Expected Claude behavior:
# - Receives additionalContext
# - Retries with: ./scripts/start-and-wait.sh dev
```

## Edge Cases

### Allow List (pass through without blocking)

| Pattern | Reason |
|---------|--------|
| `./scripts/*` | Already using wrapper scripts |
| `git *` | Git operations are safe |
| `mvn *` | Maven build commands |
| `ls`, `cat`, `head` (no pipes) | Simple file reading |
| `echo` | Output only |

### Multiple Pipes

The hook should detect any curl with pipe, regardless of what follows:
- `curl ... | jq .` → block
- `curl ... | head` → block
- `curl ... | grep` → block

## Success Metrics

| Metric | Target |
|--------|--------|
| Blocked commands recovered automatically | > 90% |
| False positives (allowed commands blocked) | < 5% |
| Hook execution time | < 100ms |

## Related Files

| File | Purpose |
|------|---------|
| `memory/hook-blocking-recovery-patterns.md` | Pattern reference |
| `claudedocs/research-hook-recovery-2026-02-23.md` | Original research |
| `scripts/api.sh` | API wrapper |
| `scripts/db-query.sh` | Database wrapper |
| `scripts/db-stats.py` | Database stats |
| `scripts/start-and-wait.sh` | Server lifecycle |
| `scripts/podman-tools.sh` | Container ops |
| `scripts/app-health.sh` | Health checks |
