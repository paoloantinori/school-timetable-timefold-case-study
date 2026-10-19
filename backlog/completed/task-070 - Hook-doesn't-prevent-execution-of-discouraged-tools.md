---
id: task-070
title: Hook doesn't prevent execution of discouraged tools
status: Done
assignee: []
created_date: '2026-02-22 16:17'
updated_date: '2026-02-23 13:53'
labels:
  - workflow
  - claude-planned
dependencies: []
priority: high
ordinal: 31500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The hook was previously only showing a SUGGESTION and allowing the command to execute. This has been fixed.

**Current Behavior (Fixed)**:
The `check-bash-suggestions.sh` hook now properly denies execution with `"permissionDecision": "deny"` for:
- `curl | jq` → redirects to `./scripts/api/api.sh`
- `curl ... localhost:8080` → redirects to `./scripts/api/api.sh raw`
- `podman ps --filter` → redirects to `./scripts/podman-tools.sh`
- `podman exec ... psql` → redirects to `python3 scripts/db-stats.py`
- Background processes (`&`) → redirects to `./scripts/start-and-wait.sh`

**Bypass Mechanism**: Add `# bypass-hook` to any command if you really need the original behavior.

**Evidence of Fix**: The hook file was updated on Feb 23, 2026 at 11:36, the day after this task was created.
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 PreToolUse hook denies curl | jq commands with clear corrective action
- [ ] #2 Hook provides bypass mechanism (# bypass-hook) for edge cases
- [ ] #3 Hook denies curl to localhost:8080, podman ps --filter, psql commands, and background processes
- [ ] #4 Hook returns permissionDecision: 'deny' (not 'suggest') for blocked patterns
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Verification (2026-02-23)

**Status**: ✅ COMPLETE - Hook was fixed after this task was created.

**Timeline**:
- Task created: 2026-02-22 16:17 (documented the problem)
- Hook fixed: 2026-02-23 11:36 (check-bash-suggestions.sh updated)

**Current Hook Behavior**:
```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use ./scripts/api/api.sh instead of curl | jq",
    "additionalContext": "CORRECTIVE ACTION: ..."
  }
}
```

The hook now properly denies execution with helpful error messages.
<!-- SECTION:NOTES:END -->
