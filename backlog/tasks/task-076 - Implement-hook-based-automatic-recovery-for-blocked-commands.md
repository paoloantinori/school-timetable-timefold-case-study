---
id: TASK-076
title: Implement hook-based automatic recovery for blocked commands
status: Done
assignee: []
created_date: '2026-02-23 09:39'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - hooks
  - automation
dependencies: []
priority: medium
ordinal: 135000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

When PreToolUse hooks block `curl`, `psql`, or server commands, Claude stops instead of automatically using the correct wrapper scripts. This requires manual user intervention.

## Solution

Implement **Solution A** from the research: JSON output with `additionalContext` (exit 0, not exit 2).

The hook should:
1. Detect blocked command patterns (curl pipes, psql, server start)
2. Return JSON with `additionalContext` pointing to the correct wrapper script
3. Use exit 0 (not exit 2) to allow recovery

## Reference Documentation

- `memory/hook-blocking-recovery-patterns.md` - Full patterns & solutions
- `claudedocs/research-hook-recovery-2026-02-23.md` - Original research

## Existing Wrapper Scripts

See MEMORY.md "Utility Scripts" section for the complete list:
- `./scripts/api/api.sh` - API calls (replaces curl)
- `./scripts/db-query.sh`, `scripts/db-stats.py` - Database queries
- `./scripts/start-and-wait.sh`, `scripts/rebuild-ui.sh` - Server lifecycle
- `./scripts/podman-tools.sh`, `scripts/app-health.sh` - Container ops

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Create/update PreToolUse hook for Bash commands
- [x] #2 Hook detects common blocked patterns (curl|psql|podman exec psql)
- [x] #3 Hook returns JSON with `additionalContext` showing correct alternative
- [x] #4 Hook uses exit 0 (not exit 2)
- [x] #5 Test: blocked curl command triggers automatic retry with api.sh
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Design Document

See `claudedocs/design-pretooluse-recovery-hook.md` for complete design.

## Implementation Steps

1. Create `~/.claude/hooks/command-redirect.sh` with the pattern detection logic
2. Register hook in `~/.claude/settings.json` under `PreToolUse.Bash`
3. Test with blocked curl command, verify automatic recovery

## Pattern Detection Matrix

| Blocked Pattern | Wrapper Script |
|-----------------|----------------|
| `curl ... \| jq` | `./scripts/api/api.sh` |
| `podman exec ... psql` | `./scripts/db-query.sh` |
| `podman ps --filter` | `./scripts/podman-tools.sh` |
| `./planner.sh start ... &` | `./scripts/start-and-wait.sh` |
| `curl ... health` | `./scripts/app-health.sh` |

## Key Implementation Details

- **Use exit 0, NOT exit 2** - this is critical for recovery
- Return JSON with `additionalContext` field
- Include specific command examples in the context
- Keep hook execution under 100ms
<!-- SECTION:NOTES:END -->
