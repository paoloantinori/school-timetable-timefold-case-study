# Script usage gotchas

> 📌 **Session memory** (~March 2026, operational). Several corrections here were folded into `CLAUDE.md` / `PROJECT_INDEX.md` afterward — verify any specific path against the live filesystem (`ls scripts/`) and the current docs before relying on it.

## Rule: verify script paths — docs drift

Always verify paths with `ls` before using them. Trust the actual filesystem, not the docs.

### Script path corrections (as captured)

| Doc said | Actual path | Notes |
|---------|------------|-------|
| `./scripts/api.sh` | `./scripts/api/api.sh` | Subdirectory `api/` |
| `./scripts/app-health.sh` | `./scripts/api/app-health.sh` | Subdirectory `api/` |
| `./scripts/api-get.sh` | `./scripts/api/api-get.sh` | Subdirectory `api/` |
| `./scripts/db-query.sh` | `./scripts/db/db-query.sh` | Subdirectory `db/` |
| `./scripts/rebuild.sh` | `./scripts/core/rebuild.sh` | Subdirectory `core/` |
| `./scripts/rebuild-quick.sh` | `./scripts/core/rebuild.sh --quick` | Flag, not separate file |
| `./scripts/rebuild-checked.sh` | `./scripts/core/rebuild-checked.sh` | Subdirectory `core/` |
| `./scripts/test.sh` | `./scripts/core/test.sh` | Subdirectory `core/` |
| `./scripts/start-and-wait.sh` | `./scripts/core/dev-start-and-wait.sh` | Different name, in `core/` |
| `./scripts/validate-solver-xml.sh` | `./scripts/validate/validate-solver-xml.sh` | Subdirectory `validate/` |
| `./scripts/podman-tools.sh` | `./scripts/container/podman-tools.sh` | Subdirectory `container/` |
| `./scripts/db-stats.py` | `python3 scripts/db/db-stats.py` | Subdirectory `db/` |
| `scripts/benchmark.sh` | `./scripts/benchmark/benchmark.sh` | Subdirectory `benchmark/` |
| `scripts/check-propagation.sh` | `scripts/validate/check-propagation.sh` | Subdirectory `validate/` |
| `scripts/sync-docs.sh` | `scripts/validate/sync-docs.sh` | Subdirectory `validate/` |
| `scripts/json-check.sh` | `scripts/validate/json-check.sh` | Subdirectory `validate/` |

### `db-query.sh`: read-only by default, use `-w` for writes

Opens `SET TRANSACTION READ ONLY` by default. Any INSERT/UPDATE/DELETE will fail with "cannot execute INSERT in a read-only transaction".

```bash
./scripts/db/db-query.sh -w "INSERT INTO ..."
```

### Server startup: use `dev-start-and-wait.sh`, never background `&`

Hooks block `planner.sh run` and `planner.sh start` with background `&`. Always use:

```bash
./scripts/core/dev-start-and-wait.sh dev
```

### `db-query.sh`: column names use Java camelCase (lowercase, no underscores)

Hibernate Panache generates lowercase column names. Use the actual names from `information_schema.columns`:
- `studentgroup_id` not `student_group_id`
- `dayofweek` not `day_of_week`
- `slotstart` not `slot_start`
- `periodstart` not `period_start`
- `maxhoursperday` not `max_hours_per_day`
- `blocksize` not `block_size`

### ImportService silently deletes LabRotations

`ImportService.java` deletes all `LabRotation` records during import (comment says "will be re-seeded by DataGenerator"). But DataGenerator only runs when `Timeslot.count() == 0`. After import, timeslots exist → DataGenerator skips → LabRotations are gone forever. After any import/export, verify LabRotations exist; if missing, re-seed or do a full profile reset.

### `planner.sh` has known bugs (`cmd_stop_quiet`)

`planner.sh` called `cmd_stop_quiet` at lines 112 and 312, but bash failed with "command not found" despite the function being defined. Root cause unclear — possibly a bash parsing issue with function ordering and `set -e`. Prefer `dev-start-and-wait.sh` in automated contexts.
