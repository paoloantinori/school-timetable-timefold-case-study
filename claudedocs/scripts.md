# Utility Scripts

## Utility Scripts (Claude Code Whitelist-Friendly)

These scripts are safe to whitelist in `~/.claude/settings.json` to reduce approval prompts.

### `scripts/api/app-health.sh`
Check if the planner app is running and healthy. Read-only HTTP GET.

```bash
./scripts/api/app-health.sh              # Check if running on port 8080
./scripts/api/app-health.sh -q           # Exit code only (0=healthy)
./scripts/api/app-health.sh -j           # Full JSON response
./scripts/api/app-health.sh -p 8081      # Different port
```

### `scripts/db/db-stats.py`
Common database queries for quick information retrieval. All queries are read-only.

```bash
python3 scripts/db/db-stats.py status    # Entity counts
python3 scripts/db/db-stats.py unassigned  # Lessons without timeslot/room
python3 scripts/db/db-stats.py conflicts   # Potential double-bookings
python3 scripts/db/db-stats.py pinned     # Pinned vs unpinned counts
python3 scripts/db/db-stats.py teacher 1  # Lessons for teacher ID
python3 scripts/db/db-stats.py group MODA # Lessons for student group
python3 scripts/db/db-stats.py date 2026-02-21  # Lessons on date
python3 scripts/db/db-stats.py status -f json   # JSON output
```

### `scripts/api/api.sh`
Common API queries with built-in JSON parsing. Supports GET, POST, DELETE operations.

```bash
# GET operations
./scripts/api/api.sh status              # Get solver status
./scripts/api/api.sh score               # Get current score
./scripts/api/api.sh analysis            # Get constraint analysis
./scripts/api/api.sh lessons 50          # List 50 lessons (default 20)
./scripts/api/api.sh teacher 1           # Lessons for teacher ID
./scripts/api/api.sh group MODA          # Lessons for student group
./scripts/api/api.sh health              # Health check
./scripts/api/api.sh raw /api/teachers   # Raw GET to path

# Solver control
./scripts/api/api.sh reset               # Clear all assignments (unpin + remove timeslot/room)
./scripts/api/api.sh solve               # Start solver (POST)
./scripts/api/api.sh stop                # Stop solver (DELETE)
./scripts/api/api.sh reschedule          # Reset and solve (POST)
./scripts/api/api.sh wait 300            # Poll until solver finishes (timeout in seconds, default 300)

# Pre-assignment pipeline (CSP-based)
./scripts/api/api.sh pre-assign          # Run pre-assignment (async, returns 202)
./scripts/api/api.sh pre-assign-status   # Get pre-assignment status
./scripts/api/api.sh pre-assign-reset    # Reset status to IDLE

# Options
./scripts/api/api.sh -p 8081 status      # Different port
./scripts/api/api.sh -j score            # Output raw JSON
./scripts/api/api.sh -t 300 pre-assign   # Extended timeout (5 min)
```

#### Full Solve Cycle

To run a complete solve cycle from scratch:

```bash
# Method 1: Step by step
./scripts/api/api.sh reset               # Clear all assignments
./scripts/api/api.sh pre-assign          # Run pre-assignment
./scripts/api/api.sh wait                # Wait for pre-assign to finish
./scripts/api/api.sh solve               # Start solver
./scripts/api/api.sh wait                # Wait for solver to finish

# Method 2: One-liner
./scripts/api/api.sh reset && ./scripts/api/api.sh pre-assign && ./scripts/api/api.sh wait && ./scripts/api/api.sh solve && ./scripts/api/api.sh wait
```

### `scripts/api/api-get.sh`
Safe curl wrapper for HTTP GET requests only.

```bash
./scripts/api/api-get.sh http://localhost:8080/api/schedule
./scripts/api/api-get.sh -j http://localhost:8080/api/teachers  # Validate JSON
./scripts/api/api-get.sh -t 60 http://example.com/api          # 60s timeout
```

### `scripts/api/import-golden.sh [options]` (TASK-346)
Imports the hand-made golden 2026/27 timetable
(`src/main/resources/seed/lessons.csv`) as pinned Lessons via
`POST /api/schedule/import-golden`, and pretty-prints the resulting
`GoldenImportReport` (rows/imported/importedStage/skippedEsame/skippedClosed/
unmatchedNoTimeslot/unmatchedNoCourseRows/esameCoursesCreated/raCoursesCreated/
deletedPinned/deletedUnpinned + the distinct unmatched-course triples). Refuses
(HTTP 409) both when Lessons already exist without `--force`, and whenever the
solver is currently running.

```bash
./scripts/api/import-golden.sh                 # Refuses if any Lesson already exists (HTTP 409)
./scripts/api/import-golden.sh --force          # Deletes ALL existing Lessons (pinned + unpinned) first, then imports - see deletedPinned/deletedUnpinned in the report
./scripts/api/import-golden.sh -p 8081 -t 120   # Different port / longer timeout
./scripts/api/import-golden.sh --json           # Raw JSON (no pretty-printing, no python3 needed)
```

**Golden profile how-to** (a dedicated profile keeps the golden reference
schedule from ever mixing with solver experiments, per TASK-346 AC#2):
```bash
./planner.sh start golden                 # Fresh profile, seeded via DataGenerator
./scripts/core/dev-start-and-wait.sh dev   # (if the app isn't already up)
./scripts/api/import-golden.sh             # Import the golden timetable as pinned Lessons
```
Then open the UI timeline to browse the human-made schedule (AC#3), or hit
`./scripts/api/api.sh analysis` to see which constraints the human solution
violates (calibration use case, `claudedocs/spalmone-2026-27/analysis.md`
section 7).

### `scripts/api/import-calendario.sh [options]` (TASK-364)
Imports the school's pre-set "Calendario scolastico 2026/27"
(`src/main/resources/seed/calendario_moda.csv` + `calendario_fc.csv` +
`calendario_class_days.csv`) as pinned MODA lab Lessons via
`POST /api/schedule/import-calendario`, and pretty-prints the resulting
`CalendarioImportReport` (modaRows/modaPlanned/modaLessonsPinned/
modaSkippedNoTimeslot/modaFailures + the report-only FCDiscrepancies/
classDaysDiscrepancies). All-or-nothing: without `--dry-run`, ANY
`modaFailures` entry refuses (HTTP 409) and persists nothing; `--dry-run`
always returns the full report without ever touching the database. Also
refuses (HTTP 409) whenever the solver is currently running.

```bash
./scripts/api/import-calendario.sh --dry-run    # Inspect the report without persisting anything
./scripts/api/import-calendario.sh              # Persist - refuses (409) if any row fails validation
./scripts/api/import-calendario.sh -p 8081 -t 120  # Different port / longer timeout
./scripts/api/import-calendario.sh --json       # Raw JSON (no pretty-printing, no python3 needed)
```

On the current 2026/27 seed, a dry run reports `modaRows=313`,
`modaPlanned=310`, `modaLessonsPinned=922`, `modaSkippedNoTimeslot=2`, and
exactly 1 `modaFailures` entry (the 2027-06-09 G05 T16/T17
mixed-shift conflict - see `calendario-scolastico-format.md`'s "8 true
divergences" callout). Re-run extractor: `python3
scripts/analyze/calendario2027-extract.py` (regenerates the 3 CSVs under
`claudedocs/spalmone-2026-27/data/` from the source xlsx and prints a
coherence report cross-checked against the format doc's known facts) - if the
source xlsx changes, copy its output into `src/main/resources/seed/` too (see
`seed-csv-format.md`'s byte-identical sync rule).

### `scripts/db/db-query.sh`
Generic psql wrapper with read-only default.

```bash
./scripts/db/db-query.sh "SELECT count(*) FROM lesson"
./scripts/db/db-query.sh -f json "SELECT * FROM teacher LIMIT 5"
./scripts/db/db-query.sh -p production "SELECT 1"  # Different profile
```

### `scripts/validate/json-check.sh`
Validate JSON files. Read-only operation, requires jq.

```bash
./scripts/validate/json-check.sh src/main/webui/src/i18n/locales/en/docs.json
# Exit 0 = valid JSON, Exit 1 = invalid, Exit 2 = missing jq or file
```

### `scripts/validate/validate-solver-xml.sh [file.xml]`
Validate solver XML configs against Timefold's XSD schema. Extracts the XSD from the Timefold JAR in `~/.m2` and runs `xmllint` validation. **Run this after every modification to solver XML configs.**

```bash
./scripts/validate/validate-solver-xml.sh                              # Validate all solver configs
./scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml  # Validate specific file
```

Exit codes: 0 = all valid, N = number of failures, 2 = missing dependency.

## Frontend Development Scripts

### `scripts/ui/ui-typecheck.sh [--watch]`
Quick TypeScript type checking without producing build artifacts. Runs `tsc --noEmit` (~2-3s).

```bash
./scripts/ui/ui-typecheck.sh            # One-shot type check
./scripts/ui/ui-typecheck.sh --watch    # Watch mode (continuous)
```

### `scripts/ui/ui-build.sh [--quick]`
Frontend-only build (TypeScript + Vite). Produces `dist/` artifacts but does NOT rebuild the JAR or restart the app.

```bash
./scripts/ui/ui-build.sh            # Full build (tsc + vite)
./scripts/ui/ui-build.sh --quick    # Vite only (skip type check)
```

### `scripts/ui/ui-test.sh [options] [test-file]`
Run Playwright e2e tests with pre-flight checks (app running, browsers installed).

```bash
./scripts/ui/ui-test.sh                        # Run all e2e tests
./scripts/ui/ui-test.sh view-switch-perf       # Run specific test
./scripts/ui/ui-test.sh --headed               # Visible browser
./scripts/ui/ui-test.sh --list                 # List available tests
./scripts/ui/ui-test.sh --install              # Install Playwright browsers
```

### `scripts/ui/ui-dev.sh [options]`
Full frontend development cycle: typecheck -> build -> deploy -> test. Stops at first failure.

```bash
./scripts/ui/ui-dev.sh                # Full cycle
./scripts/ui/ui-dev.sh --check-only   # Just typecheck
./scripts/ui/ui-dev.sh --build-only   # Typecheck + build (no deploy)
./scripts/ui/ui-dev.sh --skip-test    # Deploy without running tests
./scripts/ui/ui-dev.sh --test-only    # Only run Playwright tests
```

## Testing Scripts

### `scripts/core/test.sh`
Runs the full backend test suite (H2 in-memory, no PostgreSQL needed). See `-h`/`--help` for class/method filters.

**Runtime note (TASK-355):** `GoldenSolutionRegressionTest` (`com.school.planner.bootstrap`) is the slowest single test in the suite - it seeds the full real 2026/27 domain graph (1,368 Timeslots, TASK-408 was 1,376; 26 Teachers, 14 StudentGroups, 266 Courses, StagePatterns, GroupCalendarRules, TeacherAvailability), runs the real `GoldenScheduleImportService` over the real 12,979-row `lessons.csv`, and scores the result via `ScheduleService.analyzeScore()`. Measured 6-15s for the test method itself across several runs (same figure as the class javadoc) (well under the ~90s fallback threshold the task brief allowed for), adding roughly that much to a full `test.sh` run. See the class javadoc for the full fixture-strategy rationale (why it doesn't call `DataGenerator.onStartup()`) and `claudedocs/spalmone-2026-27/golden-audit.md`'s "Living guard" section for what it pins.

## Build Scripts

### `scripts/core/rebuild.sh [--no-wait]`
Full rebuild (backend + frontend) with app restart. Quinoa bakes React into the JAR at build time, so this script ensures proper rebuild: kill app → clean build → restart → wait for readiness.

```bash
./scripts/core/rebuild.sh            # Full rebuild with wait
./scripts/core/rebuild.sh --no-wait  # Exit immediately after restart
```

Use this for frontend changes or when you want a complete fresh build with validation.

### `scripts/core/rebuild-checked.sh [--no-wait]`
Full rebuild with preflight checks (checks DB running, JAR exists, kills app). Slower but safer.

```bash
./scripts/core/rebuild-checked.sh            # Full rebuild with wait
./scripts/core/rebuild-checked.sh --no-wait  # Exit immediately after restart
```

**Note:** Both `rebuild.sh` and `rebuild-checked.sh` run `mvn clean package`, which builds **everything** (backend + frontend via Quinoa). The difference is in preflight checks, not what gets built.

Use this for Java code changes, constraint changes, or solver config changes when you know the database is running.

### `scripts/core/dev-start-and-wait.sh [profile] [timeout]`
Start planner app in background and poll until ready. Useful for automation and benchmarks.

```bash
./scripts/core/dev-start-and-wait.sh dev          # Start dev profile, wait up to 120s
./scripts/core/dev-start-and-wait.sh prod 60      # Start prod profile, wait up to 60s
```

Exit codes: 0 = ready, 1 = failed/timed out.

## Validation Scripts

### `scripts/analyze/capacity-check.py`
Verify lab room feasibility. Parses `DataGenerator.java`, counts hours per lab tag, compares against available room slots. Exits non-zero if any tag is over capacity. Run after changing seed data or room/tag assignments.

```bash
python3 scripts/analyze/capacity-check.py
```

### `scripts/analyze/tag-audit.py`
Audit `startsWith()` tag matching for false positives. Parses `DataGenerator.java`, applies tag matching logic, flags courses that accidentally match a lab prefix.

```bash
python3 scripts/analyze/tag-audit.py
```

### `scripts/analyze/alternanza-check.py`
Validate that stage calendar days × 6h ≥ Alternanza target hours per group. Requires running app with StagePattern data; reports SKIPPED if no data available.

```bash
python3 scripts/analyze/alternanza-check.py
```

## Benchmark Scripts

### `scripts/benchmark/benchmark-branch.sh [branch] [minutes]`
Single-branch benchmark. Starts a fresh profile, solves for the given duration, records score progression to CSV, and appends results to the tracker.

```bash
./scripts/benchmark/benchmark-branch.sh feature/cartesian-block-entity 5
```

### `scripts/benchmark/run-benchmarker.sh <config_xml>`
Run a Timefold benchmark suite from an XML config. Produces results in `local/benchmarkReport/` or `local/benchmarkReportPinned/`.

```bash
# Quick test (3 configs, ~45-60min)
./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTest.xml

# Full benchmark (23 configs, ~8 hours)
./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfig.xml

# Pinned metaheuristics comparison (9 configs)
./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTabuVsVNDPinned.xml

# Clean room-tier regime on the timeslot scoring (correttivo 28/08): the -D travels
# via BENCHMARK_SYSTEM_PROPERTIES (surefire passes it to the forked benchmark JVM);
# the runner prints the "ROOM-TIER OVERRIDE (benchmark battery): ... ARMED" banner
BENCHMARK_SYSTEM_PROPERTIES="-Dplanner.room-tier.override=zero" \
  ./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTabuVsVND.xml
```

### `scripts/benchmark/benchmark-experiment.sh [seconds]`
Compare two `experiment/*` branches side by side (default 360s each). Produces a comparison report in `claudedocs/benchmark-<timestamp>/`.

```bash
./scripts/benchmark/benchmark-experiment.sh 180
```

### `scripts/benchmark/run-experiment.sh NAME CONFIG_DELTA_DESCRIPTION`
Runs one standardized solver experiment on the currently-deployed `solverConfig.xml` (reset → pre-assign → solve-to-self-termination) and writes a machine-readable scorecard to `local/experiments/NAME-TIMESTAMP.json`. This is the shared measurement harness for all m-3/m-4/m-5/m-6 experiment tasks — every result is compared against a scorecard this script produces (see `claudedocs/research/critical-assessment-and-roadmap-20260712.md` §5).

Each scorecard records: `final_score` (string), `hard_score`/`medium_score`/`soft_score`, `unassigned_lessons`, `avg_lessons_per_day_per_group`, `constraint_breakdown`, `phase_progression` (per-phase durationMs + score), `wall_clock_seconds`, `git_commit`, and the `config_delta` description you pass in. Requires the app + DB running. Override the solve polling ceiling with `MAX_WAIT_SECONDS` (default 30600 = 8.5h).

```bash
# Requires: ./scripts/core/dev-start-and-wait.sh dev  (app + DB up)
./scripts/benchmark/run-experiment.sh baseline "current pipeline, no changes"
./scripts/benchmark/run-experiment.sh rr-20-80 "RuinRecreate ruinedCount 20-80"
```

### `scripts/benchmark/compare-experiments.py A.json B.json`
Prints a side-by-side comparison of two `run-experiment.sh` scorecards — identity, scores (closer to 0 is better), unassigned count, per-group avg lessons/day, top hard-impacting constraints, and per-phase timings — each with a signed delta tagged better/worse.

```bash
python3 scripts/benchmark/compare-experiments.py \
  local/experiments/baseline-*.json local/experiments/rr-20-80-*.json
```

## Git Workflow Scripts

### `scripts/validate/check-propagation.sh`
Audit cross-branch commit propagation. Prints a matrix of shared-file commits vs branches showing coverage gaps. Exits non-zero if any shared-file commit is missing from a branch.

```bash
./scripts/validate/check-propagation.sh
```

Output format shows ✓ for coverage and ✗ for gaps:
```
                    master  feature/a  feature/b
abc1234 DataGen fix    ✓        ✓          ✗
def5678 REST API       ✓        ✓          ✓
```

## Container Management Scripts

### `scripts/container/podman-tools.sh <command> [args]`
Safe podman operations for the planner. Most commands are read-only; `stop-all` and `clean-volumes` are destructive.

```bash
./scripts/container/podman-tools.sh list              # List planner containers
./scripts/container/podman-tools.sh logs dev          # Show container logs
./scripts/container/podman-tools.sh status dev        # Check if container running
./scripts/container/podman-tools.sh shell dev         # Open shell in container
./scripts/container/podman-tools.sh volumes           # List planner volumes
./scripts/container/podman-tools.sh stop-all          # Stop all planner containers (destructive)
./scripts/container/podman-tools.sh clean-volumes     # Remove unused volumes (DANGEROUS)
```

## Data Verification Scripts

### `scripts/analyze/verify-datagenerator.py [--csv PATH] [--java PATH] [--json] [--subjects]`
Comprehensive verification of DataGenerator.java course hours against spalmone.csv (the validated human schedule). Identifies discrepancies, missing teachers, and infeasible assignments.

```bash
python3 scripts/analyze/verify-datagenerator.py                          # Full verification
python3 scripts/analyze/verify-datagenerator.py --subjects              # Include subject breakdown
python3 scripts/analyze/verify-datagenerator.py --json                  # JSON output
python3 scripts/analyze/verify-datagenerator.py --csv /path/to/other.csv # Specify CSV file
```

**Output includes:**
- Hours comparison table (Spalmone vs DataGenerator) for all teacher/group pairs
- Discrepancy detection (>10h difference)
- Feasibility analysis (e.g., T28's 64h needing 10.7 blocks but only 9 slots available)
- Missing teacher identification

**Exit codes:** 0 = no issues, 1 = critical discrepancies found, 2 = error

See `claudedocs/datagenerator-verification-2026-02-26.md` for the latest verification results.

## Documentation Scripts

### `scripts/validate/sync-docs.sh [--ai] [--check]`
Synchronize frontend constraint documentation with Java constraint provider. Parses constraint IDs from `SchoolScheduleConstraintProvider.java` and compares with frontend TypeScript data files.

```bash
./scripts/validate/sync-docs.sh            # Check sync status only
./scripts/validate/sync-docs.sh --ai       # Use Claude to generate descriptions
./scripts/validate/sync-docs.sh --check    # Exit 1 if out of sync (CI use)
```

Options:
- `--ai` - Use Claude CLI to generate human-readable constraint descriptions
- `--check` - Only check if sync is needed, exit 1 if out of sync

Files affected:
- `src/main/webui/src/data/constraints.ts`
- `src/main/webui/src/i18n/locales/en/docs.json`
- `src/main/webui/src/i18n/locales/it/docs.json`

## Spalmone Analysis Scripts

Scripts for parsing, importing, and analyzing the school's master schedule ("spalmone") against our Timefold constraint model.

> The old `spalmone-analyze.sh` turnkey wrapper no longer exists (removed during the 2026/27
> campaign). The current entry points are the individual scripts below: `spalmone-parser.py`
> (CSV to JSON), then `spalmone-db-importer.py` / `spalmone-importer.py` (import),
> `spalmone-report.py` (analysis) and `spalmone-validate.py` (validation).

### `scripts/analyze/spalmone-parser.py <csv_path> [--output FILE]`
Parses the school's cross-tabular CSV (14 groups × 3 columns per timeslot row) into flat JSON. Handles Italian date format (`8 set`), subject abbreviation normalization, teacher name mapping, co-teaching patterns.

```bash
python3 scripts/analyze/spalmone-parser.py scripts/data/spalmone.csv                    # JSON to stdout
python3 scripts/analyze/spalmone-parser.py scripts/data/spalmone.csv -o /tmp/parsed.json # JSON to file
```

Output JSON: `{total_lessons, lessons: [{date, slot_index, student_group, subject, teacher, ...}], unique_groups, unique_teachers, unique_subjects}`

### `scripts/analyze/spalmone-validate.py --output FILE [--branch NAME] [--port PORT]`
Analysis engine that queries the running app's REST API and PostgreSQL database, generates a comprehensive markdown report with constraint score breakdown, detailed violation evidence, and interpretation.

```bash
python3 scripts/analyze/spalmone-validate.py --output report.md --branch feature/cartesian-block-entity
python3 scripts/analyze/spalmone-validate.py -o report.md -b main -p 8081   # Different port
```

Requires: running planner app + PostgreSQL container.

### `scripts/analyze/spalmone-db-importer.py <parsed_lessons.json>`
Bulk imports parsed spalmone JSON into the database. Generates SQL to create courses and lessons, matching against existing teachers, student groups, and timeslots.

```bash
python3 scripts/analyze/spalmone-db-importer.py /tmp/spalmone-parsed.json
```

Requires: running PostgreSQL container (planner-pg).

### `scripts/analyze/spalmone-report.py <analysis.json> [output.md]`
Simple report generator from raw constraint analysis JSON. Less comprehensive than `spalmone-validate.py` (no SQL-backed evidence). Retained for backward compatibility.

```bash
python3 scripts/analyze/spalmone-report.py /tmp/analysis.json report.md
```


## KPI golden-vs-run (campagna spalmone 2026/27)

- `golden-overflow-by-group.py` (TASK-395, standalone sul golden + calendario + incarichi, no DB):
  overflow per-gruppo oltre il target-end fact-based. Valida i cambi a TargetEndDateCalculator
  (TASK-394) prima/dopo: baseline fact-based 0.6% (63h/10.542, 11/14 gruppi a 0; il 29.3% storico
  era artefatto del flat-8h-model).

Batteria di script permanenti nati durante la campagna spalmone (task 385-414) per misurare
ogni run del solver contro la pratica reale della scuola (golden = `claudedocs/spalmone-2026-27/data/lessons.csv`,
il grid storico importato). Tutti gli script Python condividono lo stesso input — il dump
`group|date|hour|subject|teacher|pinned` prodotto dalla query SELECT documentata nel docstring
di `daily-fill-kpi.py` (identica in tutte le copie di `capture-run.sh`) — o gli artifact
`lessons.txt` già raccolti dal laptop. Ogni futuro coding agent che valida un cambio di modello/config
DEVE usare questa batteria (via `scripts/benchmark/capture-run.sh`, sotto) invece di reinventare
query/metriche ad-hoc.

### Gli 8 script KPI

```bash
python3 scripts/analyze/daily-fill-kpi.py  <lessons-dump.txt>   # full-day rate vs ore attese per-data (TASK-385; golden 85.4%)
python3 scripts/analyze/day-start-kpi.py   <lessons-dump.txt>   # % giorni che iniziano al primo slot calendario aperto (TASK-405; golden ~99.3%)
python3 scripts/analyze/resemblance-kpi.py <lessons-dump.txt>   # ricorrenza settimanale + istogramma blocchi + breakdown per gruppo (TASK-381/385/386/413; golden ricorrenza stretta 41.5%, 1.299 blocchi)
python3 scripts/analyze/moda-knot-count.py <lessons-dump.txt> [group]  # celle (gruppo,data,ora) impilate = conflitto H3; nodo G05 atteso 0 (TASK-391/392/393)
python3 scripts/analyze/cadence-kpi.py     <lessons-dump.txt> [-j] [--top N] [--min-hours N] [--burst-threshold X]  # cadenza settimanale per (gruppo,materia), burst ratio, gap-weeks (TASK-413; autodetect CSV golden o dump pipe)
python3 scripts/analyze/fingerprint-sharpness-kpi.py <lessons-dump.txt> [-j] [--hi X] [--lo X] [--top N]  # nitidezza fingerprint per (gruppo,weekday,slot) - quota celle decise >=0.9/<=0.1 sulle settimane attive per-weekday (TASK-411; golden 93.4% pooled, autodetect CSV golden o dump pipe)
python3 scripts/analyze/hole-topology-kpi.py <lessons-dump.txt> [-j]  # TOPOLOGIA delle ore mancanti per (gruppo,data): OK-pieno / fine-anticipata / inizio-tardivo / entrambi / BUCO-INTERNO, non solo la quantita' (2026-07-26; golden 1 buco interno/125 fine-anticipata/3 inizio-tardivo su 2.125 giorni attesi, autodetect CSV golden o dump pipe)
python3 scripts/analyze/teacher-tier-kpi.py <lessons-dump.txt> [--generic-min N] [--keep-stage]  # ritmo di consumo dei docenti GENERICI (>=8 gruppi) vs SPECIALIZZATI: mix per famiglia (invariante di input, e' un controllo), per slot e per mese, piu' baricentro temporale per docente; date di stage escluse di default (2026-07-29; golden piatto 41-47% tutto l'anno, run in deriva -6,6 giorni sui generici)
python3 scripts/analyze/golden-vs-run-viz.py <lessons-dump.txt> [-o out.html]  # 4 viste HTML (Stagione, Fingerprint settimanale, Docenti, Materie x settimana) golden vs run, per ispezione umana (TASK-407/408/412/413); vista Stagione marca in rosso (bordo 2px + glifo) le celle BUCO-INTERNO, G e N
```

`golden-vs-run-viz.py` genera un HTML autonomo (si apre da file o via http) con viste per
ispezione umana: Stagione (strisce G/N allineate per giorno, stato di riempimento vs le attese
per-data della scuola, click per il dettaglio materia-per-materia) e Fingerprint settimanale per
gruppo. Da rigenerare a ogni A/B rilevante e allegare agli artifact; ha già rivelato: giorni
attesi vuoti (M20 cieco, TASK-409), diffusione del template (411), dispersione giorni-docente
(412), burst di cadenza per materia (413).

### `scripts/benchmark/capture-run.sh <output-dir>` — cattura canonica post-solve

Wrapper che, dopo un solve completato sul profilo dev, cattura in una directory dedicata TUTTA
la batteria sopra (gli 8 script + score/analysis/phase-progression/export via `api.sh`, il dump
lessons.txt via `db-query.sh`, il fingerprint docenti Q22, le righe di log delle fasi) in un colpo
solo. Consolida 3 copie storiche quasi-duplicate (`claudedocs/spalmone-2026-27/task{406,410,414}-results/capture-run.sh`,
ognuna ricopiata e ritoccata a mano a ogni ciclo A/B) — quelle copie restano come istanze storiche
per provenienza, i nuovi cicli usano SEMPRE lo script canonico:

```bash
./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/taskNNN-results/valNNN-run1
```

Vedi anche `scripts/benchmark/laptop-RUNBOOK.md` (procedura A/B sul laptop) che la richiama al posto
della raccolta manuale riga-per-riga.

### Procedura di EROSIONE (ricorrenza persa fra costruzione e fine solve)

Il dump di fine costruzione (prima delle fasi di local search) è la metrica di riferimento per
capire quanta ricorrenza settimanale la costruzione produce PRIMA che le fasi successive la
erodano. Comandi esatti:

```bash
# 1. genera il dump di fine costruzione (scrive target/construction-lessons.txt)
./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest \
  -m constructionEndRecurrenceIsAtLeastFiftyPercentWithZeroConflicts

# 2. ricorrenza a fine costruzione (~76%)
python3 scripts/analyze/resemblance-kpi.py target/construction-lessons.txt

# 3. ricorrenza a fine solve, sullo stesso run (~37%) — confrontare i due numeri = erosione
python3 scripts/analyze/resemblance-kpi.py <output-dir>/lessons.txt
```

### Riferimenti golden pinnati

- `claudedocs/spalmone-2026-27/data/golden-cadence-reference.json` — output completo (`-j`,
  default `--min-hours 10 --burst-threshold 2.0`) di `cadence-kpi.py` sul golden, pinnato per
  confronto rapido senza dover ricalcolare dal CSV.
- `claudedocs/spalmone-2026-27/golden-cadence-reference.md`, lettura in prosa dello stesso
  (aggregati golden vs run1/run2, top-10 burst ratio, breakdown per gruppo, TASK-413); include
  anche la sezione "Hole topology" (golden vs smoke2/val408/val413, 2026-07-26).
- `claudedocs/spalmone-2026-27/data/golden-hole-topology.json`, output completo (`-j`) di
  `hole-topology-kpi.py` sul golden, pinnato (2026-07-26).


## I tre gate TASK-579 (2/09, obbligatorii per ogni ciclo/capture/mandato laptop)

- `scripts/benchmark/launch-preflight.sh`: pre-lancio (jar interno vs HEAD, classi nel
  jar, flag-existence) + post-lancio (`--expect-banner`: l'unica prova che il codice
  ha consumato il flag) + battery Darwin. Verdetto `PREFLIGHT-OK/FAIL` a una riga.
- `scripts/benchmark/capture-doctor.sh <dir>`: post-capture (integrita' export >= 1KB
  e gunzip, pushability 100MB, sanity presente, typo claudodocs). Chiamato da
  run-measure.sh a fine catena (NON da capture-run.sh: dopo una capture manuale il
  doctor va lanciato a mano). TASK-579.01: confronta il VETTORE hard COMPLETO (BOTH coordinate:
  Bendable `[-13/0]hard`, legacy plain normalizzato a secondo-zero) fra score.txt e
  analysis.json; divergenza su QUALSIASI coordinata = `DOCTOR-FAIL` exit 1 (mai
  quotabile). Verdetto a una riga con semantica esplicita:
  `DOCTOR-OK` SOLO per hard=[0/0] (unica capture citabile in una claim zero-hard);
  `DOCTOR-CONSISTENT-NONZERO hard=[N/M]` = internamente coerente ma NON zero-hard
     (token DISTINCT da DOCTOR-OK: una grep su DOCTOR-OK non deve mai matcharlo);
  `DOCTOR-WARN ... NOT-DELIVERY-CERTIFIABLE:<reasons>` = manca export / score
  evidence / sanity: NON presentabile come consegna certificata. Self-test:
  `python3 scripts/benchmark/test_capture_doctor.py` (include il caso
  prima-coordinata-zero con seconda NON zero, quello che l'estrazione
  first-coordinate-only non vedeva).

  **Le TRE accettazioni di una consegna sono SEPARATE (runbook TASK-579.01):**
  1. INTEGRITA' della capture = questo doctor (verdetto OK/CONSISTENT-NONZERO senza
     CERT_BLOCK, export integro, sanity presente);
  2. ZERO-HARD = ENTRAMBE le coordinate hard a zero DAL PROVIDER (analysis.json),
     non dalla sola prima coordinata e non dalla narrativa del VERDICT;
  3. SANITY PER-GRUPPO = il gate canonico senza rossi (solution-sanity-check.py).
  Una consegna certificata nomina inoltre il suo PERCORSO DI RESTAURA DIMOSTRATO:
  volume DB (reload + recount) o checkpoint apply (path-based). L'import JSON e'
  strutturalmente impedito oltre i 50M (server cap + curl -d OOM a 1.2GB,
  TASK-579.01 F1): import-schedule.sh rifiuta sopra soglia indicando i percorsi
  corretti. Disciplina narrativa (F4): la prosa di un VERDICT e' una CLAIM, il
  conteggio e' analysis.json (precedenti 9/09: 'H32=3 celle' vs provider 1;
  data G12; 'niente GRASP' senza il qualificatore attempt-1).
- `scripts/benchmark/laptop-send-mandate.sh <file>` (da Fedora): la procedura tmux
  5-passi in un comando, rifiuto mandati multi-voce, `--check <marker>` per la
  verifica di consegna.

Registro completo degli incidenti che li hanno generati: TASK-579 nel backlog.
