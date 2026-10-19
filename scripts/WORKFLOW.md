# Scripts Workflow Guide

## 🎯 Quick Decision Tree

```
START
  │
  ├─ Check app health?
  │  └─→ ./scripts/api/app-health.sh
  │
  ├─ Check solver/status/score?
  │  └─→ ./scripts/api/api.sh status | score | health
  │
  ├─ Database query?
  │  ├─→ python3 scripts/db/db-stats.py <command>
  │  └─→ ./scripts/db/db-query.sh "SQL"
  │
  ├─ Build/Rebuild?
  │  ├─ Frontend only? → ./scripts/ui/ui-build.sh
  │  ├─ Full rebuild? → ./scripts/rebuild-ui.sh
  │  └─ Quick rebuild? → ./scripts/core/rebuild.sh
  │
  ├─ Run tests?
  │  └─→ ./scripts/core/test.sh
  │
  ├─ Start/Stop server?
  │  ├─ Start dev → ./scripts/core/dev-start-and-wait.sh dev
  │  ├─ Start prod → ./scripts/core/prod-start-and-wait.sh
  │  └─ Stop → ./planner.sh stop
  │
  └─ Benchmark?
     ├─ Single branch → ./scripts/benchmark/benchmark-branch.sh
     └─ Multi-branch → ./scripts/benchmark/benchmark-branches.sh
```

## 📋 Common Workflows

### 1. Java Code Change Workflow
```bash
# After editing Java files:
./scripts/core/rebuild.sh
# OR for thorough validation:
./scripts/core/rebuild-checked.sh
```

### 2. Frontend Change Workflow
```bash
# After editing React/TypeScript files:
./scripts/ui/ui-build.sh          # Build only
./scripts/rebuild-ui.sh            # Full rebuild (recommended)
```

### 3. Full Development Cycle
```bash
# Type check → Build → Deploy → Test
./scripts/ui/ui-dev.sh
```

### 4. Quick Status Check
```bash
./scripts/api/app-health.sh       # App running?
./scripts/api/api.sh status        # Solver status?
python3 scripts/db/db-stats.py status  # DB status?
```

### 5. Starting Fresh
```bash
# New profile with seed data:
./planner.sh start <profile> && ./planner.sh wait

# Or use the helper:
./scripts/core/dev-start-and-wait.sh <profile>
```

## 🔧 Script Categories

### Core Infrastructure
- `rebuild.sh` - Quick rebuild (mvn package + restart)
- `rebuild-checked.sh` - Full rebuild with preflight checks
- `test.sh` - Maven test runner
- `dev-start-and-wait.sh` - Start dev server & poll
- `prod-start-and-wait.sh` - Start prod server & poll

### Frontend/UI
- `ui-build.sh` - Frontend build only
- `ui-typecheck.sh` - TypeScript type check
- `ui-test.sh` - Playwright e2e tests
- `ui-dev.sh` - Full frontend cycle

### API & Health
- `api.sh` - REST API client
- `api-get.sh` - Safe GET wrapper
- `app-health.sh` - Health check

### Database
- `db-query.sh` - SQL query wrapper
- `db-stats.py` - Common DB queries

### Benchmarking
- `benchmark-branch.sh` - Single branch
- `benchmark-branches.sh` - Multi-branch
- `benchmark-h5.sh` - H5 constraint A/B test
- `benchmark-experiment.sh` - Two-branch comparison

### Validation
- `validate-solver-xml.sh` - XML schema validation
- `check-propagation.sh` - Commit propagation audit
- `json-check.sh` - JSON validation
- `sync-docs.sh` - Docs synchronization
- `ensure-timefold-patch.sh` - Timefold patch verification

### Analysis
- `verify-datagenerator.py` - DataGenerator verification
- `capacity-check.py` - Lab room feasibility
- `tag-audit.py` - Tag matching audit
- `alternanza-check.py` - Stage hours validation
- `g13-*.py` - G13 group analysis
- `spalmone-*.py` - Spalmone CSV analysis

### Container
- `podman-tools.sh` - Podman container management
