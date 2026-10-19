---
id: task-037
title: Isolate build and run environment for parallel subagent execution
status: To Do
assignee: []
created_date: '2026-02-19 07:29'
updated_date: '2026-02-23 20:54'
labels:
  - claude-planned
  - infrastructure
  - podman
  - parallelization
  - workflow
dependencies: []
priority: medium
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

When leveraging subagents to work on code in parallel, several isolation challenges arise:

1. **File conflicts**: Multiple agents modifying the same files simultaneously will overwrite each other's changes
2. **Port conflicts**: Multiple agents trying to start the Quarkus app (default port 8080) or PostgreSQL (5432) will fail
3. **Database conflicts**: Multiple agents sharing the same PostgreSQL instance will corrupt data
4. **Maven cache corruption**: Concurrent writes to `~/.m2/repository` can cause corruption
5. **Merge complexity**: Changes made in isolated environments need to be merged back to the main project

## Proposed Solution: Podman Pod Isolation

Use **Podman Pods** (not bare containers) to provide each subagent with an isolated sandbox. Pods simplify port management because containers within a pod share a network namespace — they can communicate on `localhost` without port conflicts between pods.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Host System                               │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │  Pod "agent-0"  │  │  Pod "agent-1"  │  │  Pod "agent-2"  │  │
│  │  Network NS #0  │  │  Network NS #1  │  │  Network NS #2  │  │
│  │                 │  │                 │  │                 │  │
│  │  localhost:8080 │  │  localhost:8080 │  │  localhost:8080 │  │
│  │  localhost:5432 │  │  localhost:5432 │  │  localhost:5432 │  │
│  │                 │  │                 │  │                 │  │
│  │  ┌─────┐ ┌─────┐│  │  ┌─────┐ ┌─────┐│  │  ┌─────┐ ┌─────┐│  │
│  │  │ Quarkus │DB ││  │  │ Quarkus │DB ││  │  │ Quarkus │DB ││  │
│  │  │ :8080 │:5432││  │  │ :8080 │:5432││  │  │ :8080 │:5432││  │
│  │  └─────┘ └─────┘│  │  └─────┘ └─────┘│  │  └─────┘ └─────┘│  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │           │
│      Host:8080            Host:8081            Host:8082        │
│      Host:5432            Host:5433            Host:5434        │
└─────────────────────────────────────────────────────────────────┘
```

### Key Insight: Network Namespace Isolation

Each pod has its own network namespace. This means:
- Pod "agent-0" has its OWN `localhost:8080`
- Pod "agent-1" has its OWN `localhost:8080`
- **These are different network endpoints. No internal conflict possible.**

The only ports that need coordination are the **host-exposed** ports for debugging/external access.

### Port Allocation Strategy: Formula-Based (Option C)

Instead of a stateful port registry, use a deterministic formula based on the agent's numeric ID:

```
HOST_APP_PORT = 8080 + AGENT_ID
HOST_DB_PORT  = 5432 + AGENT_ID
```

**Examples:**
| Agent ID | Host App Port | Host DB Port |
|----------|---------------|--------------|
| 0 | 8080 | 5432 |
| 1 | 8081 | 5433 |
| 2 | 8082 | 5434 |
| ... | ... | ... |

**Why this works:**
1. **Deterministic**: Given an agent ID, you always know the ports. No state file to corrupt.
2. **No collisions**: As long as agent IDs are unique, ports are unique.
3. **Debuggable**: "Agent 3 is on port 8083" — easy to remember.
4. **No race conditions**: No need to lock a registry file.

### Implementation Details

#### Pod Creation
```bash
AGENT_ID=1
APP_PORT=$((8080 + AGENT_ID))
DB_PORT=$((5432 + AGENT_ID))

# Create pod with port mappings
podman pod create \
  --name agent-${AGENT_ID} \
  -p ${APP_PORT}:8080 \
  -p ${DB_PORT}:5432

# Add containers to pod
podman run -d --pod agent-${AGENT_ID} --name app-${AGENT_ID} ...
podman run -d --pod agent-${AGENT_ID} --name db-${AGENT_ID} \
  -e POSTGRES_DB=planner \
  -e POSTGRES_USER=planner \
  -e POSTGRES_PASSWORD=planner \
  docker.io/postgres:15
```

#### Internal Communication
Inside the pod, containers use `localhost`:
- Quarkus connects to DB: `jdbc:postgresql://localhost:5432/planner`
- Tests hit the API: `http://localhost:8080/api/...`
- No port configuration needed inside containers!

#### Host Access (Debugging)
From the host machine:
```bash
# Agent 0's API
./scripts/api/api.sh -p 8080 status

# Agent 1's API
./scripts/api/api.sh -p 8081 status

# Connect to Agent 2's database (use db-query.sh wrapper with profile)
# Note: db-query.sh uses planner.sh profiles, not direct psql
python3 scripts/db-stats.py -p agent2 status
```

### Key Design Decisions

1. **Pods (not bare containers)**: App + DB share network namespace → communicate on localhost
2. **Formula-based ports**: `8080 + agent_id` eliminates stateful registry
3. **No custom Containerfile**: Use standard images with volume mounts
4. **Clone instead of worktree**: `git clone --local --shared` for space-efficient isolation
5. **Ephemeral containers**: Use `--rm` flag; named volumes persist what matters
6. **Formula-based volume naming**: `agent-${ID}-target`, `agent-${ID}-node_modules`
7. **Final Merge Step**: Orchestrator merges completed work back to main project
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given a Podman pod with isolated environment, when an agent runs 'mvn compile', then it uses the shared read-only Maven cache without corrupting it
- [ ] #2 Given two agent pods (agent-0 on 8080/5432, agent-1 on 8081/5433), when both start the Quarkus app, then neither fails due to port conflicts
- [ ] #3 Given an agent completes work in an isolated environment, when the orchestrator runs 'agent-isolate.sh merge 1', then changes are successfully integrated into the main project
- [ ] #4 Given a cloned workspace for agent-1, when an agent modifies files, then the host project repository is not affected until explicit merge
- [ ] #5 Given the isolation script, when invoked with 'scripts/agent-isolate.sh start 3', then a fresh pod 'agent-3' is created with ports 8083/5435 and volumes agent-3-*
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Phases (Simplified)

### Phase 1: Foundation (P1) — Minimal
- [ ] Add `.agents/` to `.gitignore`
- [ ] ~~Create `.container/AgentIsolation.Containerfile`~~ → **DELETED** (use standard images)
- [ ] ~~Create `scripts/port-registry.sh`~~ → **DELETED** (use formula)

### Phase 2: Core Isolation (P2)
- [ ] Create `scripts/agent-isolate.sh` with start/stop/list/merge commands
- [ ] Implement `git clone --local --shared` for workspace isolation
- [ ] Configure pod-based isolation with formula ports: `8080 + AGENT_ID`
- [ ] Implement volume mounts:
  - `~/.m2/repository` → read-only (shared cache)
  - `agent-${ID}-m2-local` → read-write (local resolution)
  - `agent-${ID}-target` → build artifacts
  - `agent-${ID}-node_modules` → frontend deps

### Phase 3: Database Isolation (P2)
- [ ] Add PostgreSQL container to each pod (standard `postgres:15` image)
- [ ] Configure `jdbc:postgresql://localhost:5432/planner` (works inside pod)
- [ ] Map host port: `5432 + AGENT_ID`

### Phase 4: Merge Workflow (P3)
- [ ] Implement `agent-isolate.sh merge <id>` command
- [ ] Add conflict detection and notification
- [ ] Create orchestrator callback for final `mvn install`

### Phase 5: Operations (P3)
- [ ] Add `agent-isolate.sh cleanup <id>` for pod + volume removal
- [ ] Add `agent-isolate.sh list` for status visibility
- [ ] Document usage in `claudedocs/agent-isolation.md`

## Dependencies

- Podman 4.x installed on host
- Sufficient disk space for clones (~500MB per agent)
- Port range 8080-8099 and 5432-5449 available (supports up to 20 agents)
- Standard images available: `maven:3.9-eclipse-temurin-17`, `postgres:15`, `node:18`

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Maven cache write attempts | Read-only mount + per-agent `m2-local` volume |
| Port exhaustion | Limit max concurrent agents (default: 20) |
| Disk space exhaustion | Cleanup script runs on schedule |
| Merge conflicts | Orchestrator notification; manual resolution path |
| Orphaned volumes | `podman volume prune` or `agent-isolate.sh cleanup --all` |
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium) — Simplified from 5/5

Eliminating the Containerfile and port registry significantly reduces scope.

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Single script, no custom images |
| Risk | 3 | Infrastructure but reversible |
| Testing | 3 | Integration testing |
| Dependencies | 3 | None blocking |
| **Total** | **3** | ~3-5 days |

---

## Simplifications Applied

### 1. No Custom Containerfile
**Before:** Build custom image with `.container/AgentIsolation.Containerfile`
**After:** Use standard images with volume mounts

```bash
podman run --rm --pod agent-${ID} \
  -v ~/.m2/repository:/root/.m2/repository:ro \
  -v agent-${ID}-m2-local:/root/.m2/repository-local \
  -v agent-${ID}-workspace:/workspace \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 \
  mvn compile
```

### 2. Clone Instead of Worktree
**Before:** `git worktree add .agents/agent-foo` (requires pruning, state management)
**After:** `git clone --local --shared` (full repo, shared objects, simple cleanup)

```bash
git clone --local --shared /path/to/planner /agents/agent-${ID}
# Cleanup: rm -rf /agents/agent-${ID}
```

### 3. Ephemeral Containers with Persistent Volumes
**Before:** Long-running containers, manual cleanup
**After:** `--rm` flag + named volumes

```bash
podman run --rm ...  # Container dies after command
podman volume ls     # Volumes persist: agent-1-target, agent-1-node_modules
```

### 4. Formula-Based Volume Naming
Consistent with port formula:

| Resource | Formula | Example (ID=2) |
|----------|---------|----------------|
| Pod name | `agent-${ID}` | `agent-2` |
| App port | `8080 + ${ID}` | `8082` |
| DB port | `5432 + ${ID}` | `5434` |
| Target volume | `agent-${ID}-target` | `agent-2-target` |
| Node modules | `agent-${ID}-node_modules` | `agent-2-node_modules` |
| Maven local | `agent-${ID}-m2-local` | `agent-2-m2-local` |
| Workspace | `agent-${ID}-workspace` | `agent-2-workspace` |

**Cleanup pattern:** `podman volume rm agent-${ID}-*`

### 5. Numeric-Only Agent IDs
**Before:** `agent-isolate.sh start agent-foo` (requires name→ID mapping)
**After:** `agent-isolate.sh start 3` (ID is the name)

---

## Port Allocation Logic (Detailed)

### The Problem
Multiple agents running Quarkus apps each need:
- A Quarkus HTTP server (internal port 8080)
- A PostgreSQL database (internal port 5432)

Without isolation, they'd conflict on the same ports.

### The Solution: Network Namespaces + Formula

**Step 1: Network Namespace Isolation**
Each Podman pod creates a NEW network namespace. Think of it as a "virtual localhost":
- Pod "agent-0" → Network NS #0 → has its own 127.0.0.1
- Pod "agent-1" → Network NS #1 → has its own 127.0.0.1

Inside any pod, `localhost:8080` refers to THAT pod's port 8080. No cross-pod interference.

**Step 2: Host Port Mapping**
To access a pod's services from the host, we map:
```
HOST_PORT → POD_LOCALHOST:INTERNAL_PORT
```

Using the formula:
```
HOST_APP_PORT = 8080 + AGENT_ID
HOST_DB_PORT  = 5432 + AGENT_ID
```

**Step 3: No State Required**
Traditional approaches use a "port registry" — a file that tracks which ports are in use. This requires:
- Atomic file locking
- Heartbeat/lease to detect stale entries
- Cleanup on crash

Formula-based allocation needs NONE of this. The formula IS the registry:
- Want to know agent 3's port? `8080 + 3 = 8083`. No lookup needed.
- Want to know which agent owns port 8085? `8085 - 8080 = 5`. It's agent 5.

### Edge Cases

**Q: What if we need more than 20 agents?**
A: The formula works up to agent ID 99 (port 8179). Beyond that, expand the range or use `-P` auto-assignment.

**Q: What if host ports 8080-8099 are already in use?**
A: Change the base: `BASE_APP_PORT=9080`, `BASE_DB_PORT=6432`.

**Q: What if an agent crashes and doesn't clean up?**
A: With `--rm`, containers auto-remove. Pods persist but are idempotent — re-running `start` recreates them.

**Q: Can two agents accidentally get the same ID?**
A: The orchestrator (Claude Code) assigns IDs sequentially. As long as the orchestrator is the single source of truth, no collision.

---

## Priority: **MEDIUM** (Strategic Infrastructure)

Enables parallel subagent execution — strategic productivity multiplier. Simplifications reduce implementation to ~3-5 days.
<!-- SECTION:NOTES:END -->
