---
id: TASK-055
title: 'create specialized agents for psql and curl, and docs on github'
status: Done
assignee: []
created_date: '2026-02-20 20:29'
updated_date: '2026-03-10 05:16'
labels:
  - claude-planned
  - agents
  - documentation
  - developer-experience
dependencies:
  - task-054
  - task-056
  - task-057
priority: low
ordinal: 39750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Common operations in the planner project require specialized knowledge:
1. **psql queries**: Direct database inspection requires knowing connection strings, table schemas, and SQL
2. **curl API calls**: Testing REST endpoints requires knowing URLs, payloads, and response formats
3. **GitHub documentation**: Project knowledge is scattered across CLAUDE.md files, not centralized

Creating specialized Claude Code agents for these domains would:
- Encapsulate domain expertise
- Reduce context needed in main agent sessions
- Enable parallel work by domain-specialist subagents
- Provide a foundation for future automation

## Related Work

- Existing agent: `.claude/agents/project-manager-backlog.md` for task management
- Task-037 defines parallel agent isolation infrastructure
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given a .claude/agents/database-query.md agent exists, when tasked with 'show all lessons for teacher X on date Y', then it produces correct SQL and formats results clearly
- [ ] #2 Given a .claude/agents/api-tester.md agent exists, when tasked with 'test the schedule solve endpoint', then it constructs correct curl commands and interprets responses
- [ ] #3 Given a GitHub repository exists, when documentation is pushed to .github/instructions/ or a wiki, then external developers can understand project setup without reading CLAUDE.md
- [ ] #4 Given all three agents are defined, when listed via 'ls .claude/agents/', then database-query.md, api-tester.md, and project-manager-backlog.md are present
- [ ] #5 Given the agents follow consistent structure, when a new agent is created, then it includes: name, description, color, and domain-specific prompts
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Phase 1: Database Query Agent (P1)

Create `.claude/agents/database-query.md`:
- Name: `database-query`
- Description: PostgreSQL queries for planner database
- Color: green
- Knowledge: Schema, common queries, psql connection
- Tools: Bash (psql), Read

## Phase 2: API Tester Agent (P1)

Create `.claude/agents/api-tester.md`:
- Name: `api-tester`  
- Description: REST API testing for planner endpoints
- Color: orange
- Knowledge: Endpoints, payloads, response schemas
- Tools: Bash (curl, jq), Read

## Phase 3: GitHub Documentation (P2)

1. Create `.github/instructions/` directory with:
   - `setup.md` - Development environment setup
   - `api.md` - REST API reference (derived from claudedocs/rest-api.md)
   - `constraints.md` - Constraint documentation summary
2. Or push to GitHub Wiki:
   - Home page with project overview
   - Architecture decisions
   - Development workflow

## Phase 4: Agent Testing (P3)

1. Test database-query agent with real queries
2. Test api-tester agent with running app
3. Verify agent loading works correctly
4. Document usage patterns in CLAUDE.md
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Agent files + GitHub docs |
| Risk | 2 | Documentation only |
| Testing | 3 | Agent invocation testing |
| Dependencies | 3 | Multiple dependencies |
| **Total** | **3** | 1-2 days |

---

## Priority: **LOW** (Nice-to-Have)

Depends on task-054, task-056, task-057. Specialized agents for specific workflows.

## Dependencies
- task-054 (tool whitelisting)
- task-056 (DB schema knowledge)
- task-057 (psql commands knowledge)

Archived: Superseded by wrapper scripts (scripts/api.sh, scripts/db-query.sh, scripts/db-stats.py) which provide simpler solutions for common psql and curl operations. Specialized agents are no longer necessary.
<!-- SECTION:NOTES:END -->
