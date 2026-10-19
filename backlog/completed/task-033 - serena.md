---
id: task-033
title: serena
status: Done
assignee: []
created_date: '2026-02-18 22:27'
updated_date: '2026-02-19 07:33'
labels:
  - claude
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add documentation to CLAUDE.md specifying that Serena MCP is available and that the "planner" project should be activated before using Serena semantic code tools.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 CLAUDE.md has a dedicated 'Serena MCP Integration' section
- [x] #2 Section explicitly names 'planner' as the project to activate
- [x] #3 Section describes activation requirement and best practices
- [x] #4 Documentation is clear enough for new sessions to understand Serena availability
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation

**File to modify**: `CLAUDE.md`

Add a new section after "Development Workflow Lessons":

```markdown
## Serena MCP Integration

Serena MCP provides semantic code analysis tools (symbol search, references, etc.).

**Project name**: `planner`

**Activation**: Before using Serena tools, activate the project:
- The project is registered in `~/.serena/serena_config.yml`
- Configuration: `.serena/project.yml` (languages: typescript for Java/JS analysis)

**Best practices**:
- Use `find_symbol` and `get_symbols_overview` instead of reading entire files
- Use `search_for_pattern` for non-code files or unknown symbol paths
- Check `check_onboarding_performed` at session start
```

## Context

- Serena MCP is configured globally in `~/.serena/serena_config.yml`
- The planner project has `.serena/project.yml` with `project_name: "planner"`
- Currently Serena tools return "No active project" errors until activated
<!-- SECTION:NOTES:END -->
