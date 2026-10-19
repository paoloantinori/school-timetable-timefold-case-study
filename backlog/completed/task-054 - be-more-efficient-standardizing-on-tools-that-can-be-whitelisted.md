---
id: task-054
title: be more efficient standardizing on tools that can be whitelisted
status: Done
assignee:
  - workflow-improvement
created_date: '2026-02-20 20:28'
updated_date: '2026-02-21 10:14'
labels:
  - workflow
  - claude-planned
  - developer-experience
  - security
dependencies: []
priority: medium
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Claude Code requires user approval for shell commands that access sensitive resources. Currently, common development tools (curl, jq, psql) require repeated approvals, interrupting workflow and slowing down agent productivity.

The goal is to identify which tools can be safely whitelisted (via Claude Code settings) and create standardized utility scripts that wrap these tools with consistent interfaces, reducing approval friction while maintaining security.

## Background

Claude Code's tool whitelist in `settings.json` allows certain commands to bypass approval prompts. By standardizing on tools that:
1. Are read-only or have predictable side effects
2. Have well-defined output formats
3. Are commonly used in development workflows

We can reduce approval prompts while maintaining security boundaries.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a JSON validation utility script exists, when invoked with 'scripts/json-check.sh data.json', then valid JSON returns exit 0 and invalid JSON returns exit 1 with error details
- [x] #2 Given a safe-curl wrapper script exists, when invoked for a GET request to a known API, then it returns the response without requiring Claude Code approval (if curl is whitelisted)
- [x] #3 Given Claude Code settings.json is updated, when the whitelist includes 'curl', 'jq', and 'psql', then these commands no longer prompt for approval
- [x] #4 Given a scripts/README.md documents all utility scripts, when a developer reads it, then they understand which scripts are available and how to use them
- [x] #5 Given the utility scripts follow a consistent pattern, when a new script is needed, then it follows the same structure (usage, error handling, output format)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Phase 1: Audit Current Tool Usage (P1)

1. Review recent Claude Code interactions to identify frequently approved commands
2. List commands that are candidates for whitelisting (curl, jq, psql, grep, etc.)
3. Assess security implications of each candidate

## Phase 2: Create Standardized Utility Scripts (P2)

1. `scripts/api-get.sh` - Safe curl wrapper for GET requests with:
   - Timeout handling
   - JSON output validation
   - Error message standardization
2. `scripts/json-check.sh` - Validate JSON files
3. `scripts/db-query.sh` - Safe psql wrapper with:
   - Read-only mode enforcement
   - Output format options (json, csv, table)

## Phase 3: Update Claude Code Configuration (P2)

1. Document whitelist entries in project CLAUDE.md
2. Create `~/.claude/settings.json` snippet with recommended whitelist:
   ```json
   {
     "allowedCommands": ["curl", "jq", "psql", "git", "npm", "mvn"]
   }
   ```
3. Add setup instructions to developer onboarding

## Phase 4: Documentation (P3)

1. Create `scripts/README.md` with:
   - Script catalog
   - Usage examples
   - Security considerations
2. Update `claudedocs/scripts.md` with new utilities
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Completed: 2026-02-21

**Scripts created:**
- `scripts/app-health.sh` - Check if app is running (read-only HTTP)
- `scripts/db-stats.py` - Common database queries (read-only SQL)
- `scripts/api-get.sh` - Safe curl wrapper for GET requests
- `scripts/db-query.sh` - Generic psql wrapper with read-only default
- `scripts/json-check.sh` - JSON file validation

**Documentation updated:**
- `scripts/README.md` - Full script catalog with whitelist guidance
- `claudedocs/scripts.md` - Added utility scripts section

**Whitelist recommendation** in `~/.claude/settings.json`:
```json
{
  "allowedCommands": [
    "curl", "jq", "grep",
    "python3 scripts/db-stats.py",
    "python3 scripts/capacity-check.py",
    "./scripts/app-health.sh"
  ]
}
```
<!-- SECTION:NOTES:END -->
