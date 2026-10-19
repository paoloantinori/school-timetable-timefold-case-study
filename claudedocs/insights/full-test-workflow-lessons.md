# Full-test workflow lessons

> 📌 **Session memory** (~March 2026, operational). Workflow details may have evolved — cross-check with `.claude/skills/full-test/SKILL.md`.

## Problem: bootstrapping vs iteration

The original `full-test` skill assumed an iterative scenario (JAR exists), but the workflow includes `./planner.sh delete dev` which destroys the working state — creating a **bootstrap scenario** (no JAR) the workflow wasn't designed for.

**Bootstrap scenario** (first run, after clean, or after delete):
- No JAR at `target/quarkus-app/quarkus-run.jar`
- DB may or may not be running
- Need to: start DB → build JAR → start app → continue

**Iterative scenario** (normal development):
- JAR already exists, DB running
- Need to: kill app → rebuild → restart → continue

### Fix applied

Updated `.claude/skills/full-test/SKILL.md` to detect the scenario:
```bash
if [ ! -f "target/quarkus-app/quarkus-run.jar" ]; then
    ./scripts/core/dev-start-and-wait.sh dev   # bootstrap
else
    ./planner.sh kill-app                        # iterative
    yes | ./planner.sh delete dev
    ./scripts/core/rebuild.sh
fi
```

## Bug fixed: `rebuild-checked.sh` wrong PROJECT_ROOT

**Symptom:** error showed `/Users/user/planner/scripts/target/quarkus-app/quarkus-run.jar`

```bash
# WRONG (only one level up from scripts/core/)
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"            # = .../planner/scripts
# CORRECT (two levels up)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"    # = .../planner
```

## Hook violations encountered

| Attempt | Why blocked | Correct approach |
|---------|-------------|------------------|
| `podman ps --filter` | use podman-tools.sh | `./scripts/container/podman-tools.sh list` |
| `mvn clean package` | use helper scripts | `./scripts/core/rebuild.sh` |
| `curl localhost:8080` | use api.sh | `./scripts/api/api.sh health` |
| `cmd \| python3 &` | no background pipes | run synchronously or use a wrapper script |

## Chicken-and-egg dependency

- `rebuild.sh` requires the DB running (checked via podman ps)
- `dev-start-and-wait.sh` starts DB + app but times out if the JAR doesn't exist and the Maven build takes too long
- For bootstrap, `dev-start-and-wait.sh` handles the full sequence

## Key wrapper scripts

| Purpose | Script |
|---------|--------|
| Rebuild (backend + frontend) | `./scripts/core/rebuild.sh` |
| Start app and wait | `./scripts/core/dev-start-and-wait.sh <profile>` |
| Container management | `./scripts/container/podman-tools.sh list` |
| API calls | `./scripts/api/api.sh status\|score\|health\|solve` |
| Pre-assignment | `./scripts/api/api.sh pre-assign` |
| Constraint analysis | `./scripts/api/api.sh analysis` |
