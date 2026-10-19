# Research: Automatic Recovery from Hook Blocking Errors

**Date**: 2026-02-23
**Topic**: How Claude Code (ZAI) can automatically recover from PreToolUse hook blocks
**Depth**: Exhaustive

## Executive Summary

**Problem**: When a PreToolUse hook blocks a tool call (exit code 2), Claude Code stops and waits for user input instead of acting on the stderr feedback and retrying with corrections.

**Root Cause**: This is a **known regression** documented in [GitHub issue #24327](https://github.com/anthropics/claude-code/issues/24327). The model interprets "blocked tool call" the same as "user denial" rather than "fixable quality gate with feedback".

**Current State**: Intermittent behavior - sometimes Claude recovers, sometimes it stops. The inconsistency suggests model interpretation issues rather than deterministic client bugs.

**Confidence**: High (direct documentation from Anthropic, multiple community reports, ZAI system prompt analysis)

---

## Detailed Findings

### 1. The Core Issue (GitHub #24327)

The bug report describes exactly what you're experiencing:

> When a `PreToolUse` hook blocks a tool call by exiting with code 2, Claude stops responding and waits for user input instead of acting on the error feedback provided via stderr.

**Expected behavior**:
1. Claude receives stderr content as actionable feedback ✓ (works)
2. Claude continues its turn and acts on feedback ✗ (broken)
3. Claude does NOT treat hook block as user denial ✗ (broken)

**Regression timeline**: First noticed around Claude Opus 4.6 (v2.1.32), early February 2026.

### 2. How Hook Blocking Works (ZAI Perspective)

From ZAI's system prompt templates:

```
${ATTACHMENT_OBJECT.hookName} hook stopped continuation: ${ATTACHMENT_OBJECT.message}
```

The message goes to the model, but the model interprets "stopped continuation" conservatively and defers to the user.

### 3. Why Standard Approaches Don't Work

**Attempted workarounds** (from research):
- `<critical_directives>` tags in stderr - works sometimes, not reliable
- `--dangerously-skip-permissions` mode - no effect on hook blocks
- Explicit instructions in stderr like "DO NOT STOP" - model ignores them

The issue is that the "blocked tool call" signal appears to override embedded directives.

---

## Potential Solutions

### Solution A: JSON Output with `additionalContext` (Recommended)

Instead of using exit code 2 + stderr, use exit code 0 + JSON structured output:

```bash
#!/bin/bash
# Hook that blocks but provides recovery context

# ... validation logic ...

if validation_fails; then
    cat <<EOF
{
  "decision": "block",
  "reason": "Use ./scripts/api/api.sh instead of curl. This wrapper script handles authentication and JSON parsing.",
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Direct curl commands with pipes are not whitelisted. Use the provided wrapper script.",
    "additionalContext": "ALTERNATIVE_COMMAND: Use ./scripts/api/api.sh for API calls instead of curl. The wrapper scripts in /scripts/ folder are whitelisted and handle JSON parsing automatically."
  }
}
EOF
    exit 0  # NOT exit 2!
fi
```

**Why this works**: JSON output allows `additionalContext` which is injected directly into the model's context, giving Claude explicit instructions on what to do next.

### Solution B: Use `updatedInput` to Auto-Correct

For cases where there's a known alternative command:

```bash
#!/bin/bash
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command')

# Detect curl to localhost:8080/api
if echo "$COMMAND" | grep -qE 'curl.*localhost:8080/api'; then
    # Extract the API path
    API_PATH=$(echo "$COMMAND" | grep -oE '/api/[^ ]+' | head -1)

    cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "allow",
    "updatedInput": {
      "command": "./scripts/api/api.sh ${API_PATH#/api/}"
    }
  }
}
EOF
    exit 0
fi
```

**Why this works**: The `updatedInput` field modifies the tool input before execution, transparently redirecting to the correct command.

### Solution C: System Prompt Modification (ZAI-specific)

Modify ZAI's system prompt templates to differentiate hook blocks:

```markdown
<!-- In system-reminder-hook-stopped-continuation.md -->
${ATTACHMENT_OBJECT.hookName} hook stopped continuation: ${ATTACHMENT_OBJECT.message}

CRITICAL: This is an automated quality gate, NOT a user denial. You MUST:
1. Read the feedback above carefully
2. Determine the appropriate corrective action
3. Retry with the corrected approach
4. Do NOT stop or ask for user permission to continue
```

### Solution D: Stop Hook to Force Continuation

Add a Stop hook that detects when Claude stops after a hook block:

```json
{
  "hooks": {
    "Stop": [{
      "matcher": "*",
      "hooks": [{
        "type": "command",
        "command": "~/.claude/hooks/check-recent-block.sh"
      }]
    }]
  }
}
```

Where `check-recent-block.sh` checks if the last action was a hook block and outputs:

```json
{
  "decision": "block",
  "reason": "You just received hook feedback. Apply the correction and retry immediately. Do not stop."
}
```

**Warning**: This can cause infinite loops if not carefully implemented. Use the `stop_hook_active` field to prevent loops.

---

## Recommended Implementation for Planner Project

For your specific use case (blocking curl commands in favor of wrapper scripts):

### Step 1: Create a Smart PreToolUse Hook

```bash
#!/bin/bash
# ~/.claude/hooks/api-redirect.sh

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command')

# Check for curl to localhost:8080
if echo "$COMMAND" | grep -qE 'curl.*localhost:8080'; then
    # Parse what kind of API call it is
    if echo "$COMMAND" | grep -q 'api/schedule'; then
        SUGGESTION="./scripts/api/api.sh status|score|health|analysis"
    elif echo "$COMMAND" | grep -q 'api/teachers'; then
        SUGGESTION="./scripts/api/api.sh teacher <id>"
    else
        SUGGESTION="./scripts/api/api.sh <endpoint>"
    fi

    cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Direct curl commands are not whitelisted. Use wrapper scripts in /scripts/ folder.",
    "additionalContext": "CORRECTIVE ACTION: Instead of running the curl command, use ${SUGGESTION}. See MEMORY.md for the full list of wrapper scripts."
  }
}
EOF
    exit 0
fi

# Allow other commands
exit 0
```

### Step 2: Register the Hook

```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "Bash",
      "hooks": [{
        "type": "command",
        "command": "~/.claude/hooks/api-redirect.sh"
      }]
    }]
  }
}
```

---

## Limitations

1. **Model behavior is non-deterministic**: Even with the best prompts, the model may still stop occasionally
2. **ZAI customization limited**: While ZAI has custom system prompts, the core hook handling is still Claude Code's behavior
3. **Regression may be fixed**: Watch GitHub issue #24327 for official fixes

---

## Sources

1. [GitHub Issue #24327](https://github.com/anthropics/claude-code/issues/24327) - Primary bug report
2. [Claude Code Hooks Documentation](https://code.claude.com/docs/en/hooks-guide) - Official reference
3. [disler/claude-code-hooks-mastery](https://github.com/disler/claude-code-hooks-mastery) - Community patterns
4. ZAI system prompt templates at `~/.cc-mirror/zai/tweakcc/system-prompts/`
5. [Claude Code Hooks Guide (DataCamp)](https://www.datacamp.com/tutorial/claude-code-hooks) - Exit code behavior
