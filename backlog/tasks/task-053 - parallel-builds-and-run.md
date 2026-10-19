---
id: TASK-053
title: parallel builds and run
status: Done
assignee: []
created_date: '2026-02-20 20:27'
updated_date: '2026-03-10 05:16'
labels:
  - workflow
  - claude-planned
  - infrastructure
  - parallelization
dependencies:
  - task-037
priority: low
ordinal: 36750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

When multiple Claude Code subagents work on the planner project simultaneously, they conflict on:
1. **Java process**: Only one Quarkus app can bind to port 8080 at a time
2. **Maven builds**: Concurrent `mvn` processes can corrupt `~/.m2/repository` or cause lock contention
3. **Podman resources**: Database profiles share the same Podman volume namespace

This limits the ability to run parallel subagent work (e.g., one agent running tests while another builds frontend).

## Goal

Enable multiple isolated build and run environments so that parallel subagents can work without resource conflicts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given planner.sh supports --port-offset flag, when invoked with '--port-offset 1', then Quarkus binds to port 8081 and PostgreSQL to 5441
- [ ] #2 Given two terminal sessions with different port offsets, when both run './planner.sh start dev --port-offset 0' and './planner.sh start dev --port-offset 1', then both apps start successfully without port conflicts
- [ ] #3 Given MAVEN_LOCAL_REPO environment variable is set, when running 'mvn compile', then Maven uses the specified local repository instead of ~/.m2/repository
- [ ] #4 Given a workspace isolation script, when invoked with 'scripts/workspace-isolate.sh agent-foo', then a new directory with isolated maven cache is created at .workspaces/agent-foo/
- [ ] #5 Given an isolated workspace exists, when an agent runs builds in that workspace, then the host's ~/.m2/repository is not modified
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Phase 1: Port Offset Support (P1)

1. Modify `planner.sh` to accept `--port-offset N` flag
2. Calculate derived ports: `APP_PORT=8080+N`, `DB_PORT=5432+N`
3. Pass ports to Quarkus via `-Dquarkus.http.port=${APP_PORT}`
4. Pass DB port to Podman via `-p ${DB_PORT}:5432`
5. Update `planner.sh wait` to use correct port for health check

## Phase 2: Maven Cache Isolation (P2)

1. Create `scripts/workspace-isolate.sh` with commands:
   - `create <name>` - creates `.workspaces/<name>/` with symlinked project and local `.m2/`
   - `destroy <name>` - removes workspace
   - `list` - shows active workspaces
2. Set `MAVEN_OPTS=-Dmaven.repo.local=${WORKSPACE}/.m2/repository`
3. Add `.workspaces/` to `.gitignore`

## Phase 3: Podman Volume Isolation (P2)

1. Modify `planner.sh` to use profile-specific Podman volume names
2. Change `planner-data-${profile}` to `planner-data-${profile}-${offset}`
3. Or use `--name` flag for container naming isolation

## Phase 4: Integration Testing (P3)

1. Test parallel startup with two different offsets
2. Test parallel Maven builds in isolated workspaces
3. Document usage in `claudedocs/parallel-workflows.md`
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Script changes + port management |
| Risk | 3 | Affects build system |
| Testing | 3 | Integration testing |
| Dependencies | 3 | Superseded by task-037 |
| **Total** | **3** | 1-2 days |

---

## Priority: **LOW** (Superseded)

Consider closing as duplicate/superseded by task-037 which is more comprehensive.

## Dependencies
- task-037 (or skip if task-037 is done)

Archived: Superseded by task-037 which provides comprehensive Podman pod isolation. The formula-based port allocation (8080 + agent_id) from this task was incorporated into task-037's design.
<!-- SECTION:NOTES:END -->
