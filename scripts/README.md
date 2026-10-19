# Planner Utility Scripts

This directory contains utility scripts for development, benchmarking, validation, and git workflows.

## 📁 New Organized Structure

Scripts are now organized by category into subdirectories:

```
scripts/
├── core/           # Core build and lifecycle scripts
├── ui/             # Frontend-specific scripts
├── api/            # REST API and health check scripts
├── db/             # Database query and stats scripts
├── benchmark/      # Solver benchmarking scripts
├── validate/       # Validation and verification scripts
├── analyze/        # Analysis and audit scripts
└── container/      # Container (podman) management scripts
```

## 🚀 Quick Start - Common Tasks

| Task | Script |
|------|--------|
| **Check if app is running** | `./scripts/api/app-health.sh` |
| **Get solver status** | `./scripts/api/api.sh status` |
| **Get current score** | `./scripts/api/api.sh score` |
| **Run all tests** | `./scripts/core/test.sh` |
| **Quick TypeScript check** | `./scripts/ui/ui-typecheck.sh` |
| **Build frontend only** | `./scripts/ui/ui-build.sh` |
| **Full rebuild (UI + JAR + restart)** | `./scripts/core/rebuild-checked.sh` |
| **Quick rebuild (no preflight checks)** | `./scripts/core/rebuild.sh` |
| **Safe rebuild (with preflight checks)** | `./scripts/core/rebuild-checked.sh` |
| **Database status** | `python3 scripts/db/db-stats.py status` |
| **Start dev server and wait** | `./scripts/core/dev-start-and-wait.sh dev` |

## 📖 Script Reference by Category

### Core (`core/`)

| Script | Purpose |
|--------|---------|
| `rebuild.sh` | Quick rebuild: kill app → mvn package → restart |
| `rebuild-checked.sh` | Full rebuild with preflight checks |
| `test.sh` | Maven test runner with options |
| `dev-start-and-wait.sh` | Start dev server in background, poll for readiness |
| `prod-start-and-wait.sh` | Start prod server (laptop/production) in background |

### UI (`ui/`)

| Script | Purpose |
|--------|---------|
| `ui-build.sh` | Frontend build only (npm run build) |
| `ui-typecheck.sh` | TypeScript type check (no build artifacts) |
| `ui-test.sh` | Run Playwright e2e tests |
| `ui-dev.sh` | Full frontend cycle: typecheck → build → deploy → test |

### API (`api/`)

| Script | Purpose |
|--------|---------|
| `api.sh` | REST API client with built-in JSON handling |
| `api-get.sh` | Safe curl wrapper for GET requests |
| `app-health.sh` | Check if app is running/healthy |
| `constraint-violations.sh` | Check constraint violations by ID |
| `import-schedule.sh` | Import/export schedule JSON |
| `check-completion-info.sh` | Check student group completion |
| `check-lessons.sh` | Query lessons by criteria |

### DB (`db/`)

| Script | Purpose |
|--------|---------|
| `db-query.sh` | Safe psql wrapper for database queries |
| `db-stats.py` | Common database queries (status, unassigned, conflicts, etc.) |
| `data-integrity-check.py` | Data integrity checks |
| `update-stage-pattern.py` | Update stage patterns |

### Benchmark (`benchmark/`)

| Script | Purpose |
|--------|---------|
| `benchmark-branch.sh` | Single-branch benchmark |
| `run-benchmarker.sh` | Run Timefold benchmark suite from XML config |
| `benchmark-h5.sh` | A/B benchmark: H5 constraint variants |
| `benchmark-experiment.sh` | Two-branch experiment comparison |
| `capture-run.sh (+ capture-doctor.sh, launch-preflight.sh, laptop-send-mandate.sh: the TASK-579 three gates) <output-dir>` | Canonical post-solve artifact + KPI capture (spalmone A/B cycles) — see `claudedocs/scripts.md` "KPI golden-vs-run" section |

### Validate (`validate/`)

| Script | Purpose |
|--------|---------|
| `validate-solver-xml.sh` | Validate solver XML configs against XSD |
| `check-propagation.sh` | Cross-branch commit propagation audit |
| `json-check.sh` | Validate JSON files |
| `sync-docs.sh` | Sync constraint docs between Java and frontend |
| `ensure-timefold-patch.sh` | Ensure Timefold Solver is patched |

### Analyze (`analyze/`)

| Script | Purpose |
|--------|---------|
| `verify-datagenerator.py` | Verify DataGenerator against spalmone.csv |
| `capacity-check.py` | Lab room feasibility analysis |
| `tag-audit.py` | Tag matching false positive detection |
| `alternanza-check.py` | Stage hours validator |
| `g13-*.py` | G13 group analysis scripts |
| `spalmone-*.py` | Spalmone CSV analysis scripts |
| `detailed-subject-breakdown.py` | Subject hours breakdown |
| `analyze-group-discrepancies.py` | Group discrepancy analysis |
| `daily-fill-kpi.py` | Full-day rate vs expected hours (golden-vs-run KPI battery — see `claudedocs/scripts.md`) |
| `day-start-kpi.py` | Day-start anchoring rate KPI (golden-vs-run battery) |
| `resemblance-kpi.py` | Weekly recurrence + block histogram KPI (golden-vs-run battery) |
| `moda-knot-count.py` | Stacked-cell (H3 conflict) count KPI (golden-vs-run battery) |
| `cadence-kpi.py` | Weekly cadence / burst-ratio KPI per (group,subject) (golden-vs-run battery) |
| `fingerprint-sharpness-kpi.py` | Weekly fingerprint sharpness ("nitidezza") KPI per (group,weekday,slot) (golden-vs-run battery) |
| `hole-topology-kpi.py` | Topology of missing hours per (group,date): OK-full / early-end / late-start / both / INTERNAL-HOLE, not just the deficit quantity (golden-vs-run battery) |
| `golden-vs-run-viz.py` | HTML side-by-side golden vs run comparator (golden-vs-run battery); Season view now marks INTERNAL-HOLE cells with a red border and glyph, on both G and N |

### Container (`container/`)

| Script | Purpose |
|--------|---------|
| `podman-tools.sh` | Safe podman operations (list, logs, shell, volumes) |

## 🔧 Detailed Usage Examples

### API Client (`api/api.sh`)

```bash
./scripts/api/api.sh status                 # Get solver status
./scripts/api/api.sh score                  # Get current score
./scripts/api/api.sh analysis               # Get constraint analysis
./scripts/api/api.sh lessons                # List lessons (default 20)
./scripts/api/api.sh teacher 1              # Get teacher by ID
./scripts/api/api.sh group MODA              # Lessons for student group
./scripts/api/api.sh health                  # Health check
./scripts/api/api.sh get /api/schedule       # Raw GET to path
./scripts/api/api.sh solve                   # Start solver
./scripts/api/api.sh stop                    # Stop solver
./scripts/api/api.sh reset                   # Clear all assignments
./scripts/api/api.sh pre-assign              # Run hybrid pre-assignment
./scripts/api/api.sh pre-assign-status       # Get pre-assignment status
./scripts/api/api.sh wait                    # Poll until solver/pre-assign finishes
```

### Database Queries (`db/db-stats.py`)

```bash
python3 scripts/db/db-stats.py status       # Entity counts
python3 scripts/db/db-stats.py unassigned   # Lessons without timeslot/room
python3 scripts/db/db-stats.py conflicts    # Potential double-bookings
python3 scripts/db/db-stats.py pinned       # Pinned vs unpinned counts
python3 scripts/db/db-stats.py teacher 1    # All lessons for teacher ID 1
python3 scripts/db/db-stats.py group MODA   # Lessons for student group
python3 scripts/db/db-stats.py date 2026-02-21  # Lessons on specific date
python3 scripts/db/db-stats.py status -f json   # JSON output
```

### Test Runner (`core/test.sh`)

```bash
./scripts/core/test.sh                              # Run all tests
./scripts/core/test.sh -c ExpectedDaysCalculatorTest  # Specific test class
./scripts/core/test.sh -c ExpectedDaysCalculatorTest -m testSingleDayCourse  # Specific method
./scripts/core/test.sh -u                              # Unit tests only
./scripts/core/test.sh -i                              # Integration tests only
./scripts/core/test.sh -v                              # Verbose output
./scripts/core/test.sh -s                              # Skip tests (compile only)
```

### Rebuild Scripts

```bash
# Frontend-only
./scripts/ui/ui-build.sh                           # Build frontend

# Full rebuild options (backend + frontend)
./scripts/core/rebuild.sh                           # Quick: kill + mvn package + restart
./scripts/core/rebuild-checked.sh [--no-wait]       # Full: preflight + build + restart
./scripts/core/rebuild.sh                           # Quick: kill + mvn package + restart
./scripts/core/rebuild-checked.sh                     # Full: preflight + build + restart
```

### UI Development (`ui/ui-dev.sh`)

```bash
./scripts/ui/ui-dev.sh                    # Full cycle: check + build + deploy + test
./scripts/ui/ui-dev.sh --check-only       # Just typecheck
./scripts/ui/ui-dev.sh --build-only       # Typecheck + build (no deploy/test)
./scripts/ui/ui-dev.sh --skip-test        # Deploy but skip Playwright tests
./scripts/ui/ui-dev.sh --test-only        # Only run tests (app must be running)
```

## 📋 Breaking Changes from Reorganization

If you have scripts or documentation referencing the old paths, update them:

| Old Path | New Path |
|----------|----------|
| `scripts/rebuild-quick.sh` | `scripts/core/rebuild.sh` |
| `scripts/rebuild-full.sh` | `scripts/core/rebuild-checked.sh` |
| `scripts/rebuild.sh` | ~~DELETED~~ (was confusing alias) |
| `scripts/ui-build.sh` | `scripts/ui/ui-build.sh` |
| `scripts/ui-typecheck.sh` | `scripts/ui/ui-typecheck.sh` |
| `scripts/ui-test.sh` | `scripts/ui/ui-test.sh` |
| `scripts/ui-dev.sh` | `scripts/ui/ui-dev.sh` |
| `scripts/start-and-wait.sh` | `scripts/core/dev-start-and-wait.sh` |
| `scripts/laptop-start-and-wait.sh` | `scripts/core/prod-start-and-wait.sh` |
| `scripts/api.sh` | `scripts/api/api.sh` |
| `scripts/api-get.sh` | `scripts/api/api-get.sh` |
| `scripts/app-health.sh` | `scripts/api/app-health.sh` |
| `scripts/db-query.sh` | `scripts/db/db-query.sh` |
| `scripts/db-stats.py` | `scripts/db/db-stats.py` |
| `scripts/podman-tools.sh` | `scripts/container/podman-tools.sh` |
| `scripts/benchmark.sh` | `scripts/benchmark/benchmark-branch.sh` |
| `scripts/benchmark-all.sh` | `scripts/benchmark/run-benchmarker.sh` |
| `scripts/validate-solver-xml.sh` | `scripts/validate/validate-solver-xml.sh` |
| `scripts/check-propagation.sh` | `scripts/validate/check-propagation.sh` |
| `scripts/sync-docs.sh` | `scripts/validate/sync-docs.sh` |

## 🎯 Decision Tree: Which Script Should I Use?

```
What do you want to do?

├─ Check app status
│  └─ ./scripts/api/app-health.sh
│
├─ Check solver status/score
│  └─ ./scripts/api/api.sh status (or score)
│
├─ Run tests
│  └─ ./scripts/core/test.sh
│
├─ Build/Rebuild
│  ├─ Frontend only?
│  │  └─ ./scripts/ui/ui-build.sh
│  │
│  ├─ Full rebuild (backend + frontend)?
│  │  ├─ ./scripts/core/rebuild.sh            # Quick (no preflight)
│  │  └─ ./scripts/core/rebuild-checked.sh     # With preflight checks
│
├─ Query database
│  ├─ Common queries (status, conflicts, etc.)
│  │  └─ python3 scripts/db/db-stats.py <command>
│  │
│  └─ Custom SQL query
│     └─ ./scripts/db/db-query.sh "SELECT ..."
│
├─ Start/Stop app
│  ├─ Start dev server
│  │  └─ ./scripts/core/dev-start-and-wait.sh dev
│  │
│  ├─ Start prod server
│  │  └─ ./scripts/core/prod-start-and-wait.sh
│  │
│  └─ Stop server (kill app + DB)
│     └─ ./planner.sh stop
│
└─ Benchmark solver
   ├─ Single branch
   │  └─ ./scripts/benchmark/benchmark-branch.sh <branch> <minutes>
   │
   └─ Multi-branch
      └─ ./scripts/benchmark/run-benchmarker.sh <config.xml>
```
