---
name: full-test
description: |
  Run the complete local testing cycle to verify code changes. Use this skill whenever:
  - User asks to "test locally", "run a full test", "verify changes work"
  - User wants to see how recent code changes behave
  - User mentions "test cycle", "full cycle", or "complete test"
  - After making code changes that need verification
  - User says "try it" or "run it" after code modifications

  The cycle includes: kill app → reset profile → (bootstrap or rebuild) → import-calendario → pre-assign → solve
---

# Full Local Test Cycle

Run the complete testing workflow on the local machine to verify code changes.

## Prerequisites

- No other critical work depending on the current running app
- Willing to reset the `dev` profile (all data will be deleted)

## Important: Bootstrap vs Iterative

This skill detects whether you're in a **bootstrap scenario** (first run, no JAR) or **iterative scenario** (rebuild existing), and adapts the workflow accordingly.

## Workflow

### Step 0: Pre-flight Check (Automatic)

The skill first checks:
1. If JAR exists at `target/quarkus-app/quarkus-run.jar`
2. If database container `planner-pg` is running
3. Current git branch (for reporting)

Based on these checks, it selects the appropriate workflow:

**Bootstrap Scenario** (no JAR):
```bash
# Use bootstrap workflow
./planner.sh stop >/dev/null 2>&1 || true
podman rm -f planner-pg >/dev/null 2>&1 || true
podman volume rm planner-data-dev >/dev/null 2>&1 || true

# Start database and build fresh
./scripts/core/dev-start-and-wait.sh dev
```

**Iterative Scenario** (JAR exists):
```bash
# Use standard rebuild workflow
./planner.sh kill-app
yes | ./planner.sh delete dev
./scripts/core/rebuild.sh
```

### Step 1: Import the Calendario pins (MANDATORY before any solve)

```bash
./scripts/api/import-calendario.sh
```

Since TASK-381 "stage on the grid", a fresh profile seeds 2,080 unpinned
Stage stub Lessons (plus the MODA lab courses' stubs) that ONLY this import
pins/accounts. Solving without it scatters Stage hours across arbitrary
days and produces meaningless resemblance numbers — never skip this step.
Expect the report to show ~866 MODA pins, ~1,800 stage pins and ~280
`stageOffGridHours` on the 2026/27 seed.

### Step 2: Run pre-assignment

```bash
./scripts/api/api.sh pre-assign
```

Expected response: `{"message":"Pre-assignment started","status":"RUNNING",...}`

### Step 3: Wait for pre-assignment completion

Poll until status is COMPLETE:

```bash
for i in {1..60}; do
    STATUS=$(./scripts/api/api.sh pre-assign-status | jq -r '.status')
    echo "[$i] Status: $STATUS"
    [ "$STATUS" = "COMPLETE" ] && break
    [ "$STATUS" = "ERROR" ] && { echo "Failed!"; exit 1; }
    sleep 2
done
```

Expected flow: `RUNNING` → `COMPLETE` (typically 5-15 seconds)

### Step 4: Start solver

```bash
./scripts/api/api.sh solve
```

Expected response: `Solver started (202 Accepted)`

### Step 5: Monitor solver progress

```bash
for i in {1..60}; do
    echo "=== Check $i ==="
    ./scripts/api/api.sh status
    ./scripts/api/api.sh score

    # Stop when solver finishes
    if ./scripts/api/api.sh status | grep -qi "NOT_SOLVING"; then
        break
    fi
    sleep 5
done
```

### Step 6: Analyze results

```bash
# Get final score breakdown
./scripts/api/api.sh analysis | jq '.constraints | sort_by(.hard) | .[0:10]'

# Check phase progression
./scripts/api/api.sh phase-progression | jq '.[] | {phase, score, durationMs}'
```

## Critical Rules

### ALWAYS Use Wrapper Scripts

| ❌ Wrong | ✅ Right |
|----------|---------|
| `podman ps --filter` | `./scripts/container/podman-tools.sh list` |
| `podman exec ... psql` | `./scripts/db/db-query.sh "SQL"` |
| `curl localhost:8080/...` | `./scripts/api/api.sh <named-cmd>` (no generic GET) |
| `mvn clean package` | `./scripts/core/rebuild.sh` |
| `lsof -i :8080` | `./scripts/api/api.sh health` |

### NEVER Background Pipe Chains

| ❌ Wrong | ✅ Right |
|----------|---------|
| `cmd \| python3 &` | Run synchronously OR use wrapper script |
| `cmd1 && cmd2 &` | Use `./scripts/core/rebuild.sh` (handles background properly) |
| `./planner.sh start &` | Use `./scripts/core/dev-start-and-wait.sh dev` |

## Common Issues

| Issue | Solution |
|-------|----------|
| **Timeout starting app** | Check if JAR exists - if not, use bootstrap workflow |
| **Database not running** | `./planner.sh start dev` (for bootstrap) or `./scripts/core/rebuild.sh` (requires DB) |
| **Pre-assignment ERROR** | Check `/api/schedule/pre-assign/status` for failure analysis |
| **Solver already running** | `./scripts/api/api.sh stop` first |
| **App not responding** | `./scripts/api/api.sh health` - may need to wait longer |
| **rebuild.sh fails** | Ensure database is running first |

## Quick Reference

```bash
# Bootstrap (first run, no JAR):
./scripts/core/dev-start-and-wait.sh dev

# Iterative (JAR exists, quick rebuild):
./planner.sh kill-app
yes | ./planner.sh delete dev
./scripts/core/rebuild.sh

# Then continue with both:
./scripts/api/import-calendario.sh   # MANDATORY: pins MODA + Stage (TASK-381)
./scripts/api/api.sh pre-assign
# Wait for COMPLETE...
./scripts/api/api.sh solve
# Monitor with: ./scripts/api/api.sh score
```

## Troubleshooting Script Paths

| What You Want | Script Path |
|---------------|-------------|
| Rebuild (backend + frontend) | `./scripts/core/rebuild.sh` |
| Rebuild with more checks | `./scripts/core/rebuild-checked.sh` |
| Start app and wait | `./scripts/core/dev-start-and-wait.sh <profile>` |
| Container management | `./scripts/container/podman-tools.sh list|stop|start` |
| API calls | `./scripts/api/api.sh status|score|health|solve|stop` |
| Raw API requests | `./scripts/api/api.sh <named-cmd>` (see `api.sh --help`) |
| Pre-assignment | `./scripts/api/api.sh pre-assign` |
| Pre-assignment status | `./scripts/api/api.sh pre-assign-status` |
| Constraint analysis | `./scripts/api/api.sh analysis` |

## Notes

- **JAR location**: `target/quarkus-app/quarkus-run.jar`
- **Database container**: `planner-pg`
- **Database volume**: `planner-data-dev` (for dev profile)
- **Typical build time**: 20-40 seconds (varies by hardware)
- **Typical pre-assignment**: 5-15 seconds
- **Typical solver convergence**: 2-5 minutes
