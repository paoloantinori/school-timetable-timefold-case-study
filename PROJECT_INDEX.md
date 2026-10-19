# Project Index: Planner

Last reviewed: 2026-09-21 (file counts approximate; the zero-hard loop tooling below is
current as of 21/09, older counts may still drift).

**What this file is:** a navigation index (where things live, what to run). It is not a
specification. Authoritative rules live in `CLAUDE.md`; constraint semantics live in
`CLAUDE.md` section "## Canonical Constraint IDs". When this index and those disagree,
they win.

## ⚠️ CRITICAL: Use Wrapper Scripts, NOT Raw Commands

**NEVER use `curl`, `psql`, `podman exec`, or `mvn` directly.** These commands are NOT whitelisted.

| Instead of | Use |
|------------|-----|
| `curl ... \| jq` | `./scripts/api/api.sh status\|score\|health` |
| `curl localhost:8080/api/...` | `./scripts/api/api.sh status\|score\|analysis\|...` (named cmds; `raw` removed, see `api.sh --help`) |
| `podman exec ... psql` | `./scripts/db/db-query.sh "SQL"` |
| `podman ps` | `./scripts/container/podman-tools.sh list` |
| `mvn quarkus:dev` | `./planner.sh start <profile>` |
| `mvn clean package` | `./scripts/core/rebuild.sh` |
| `mvn test` | `./scripts/core/test.sh` |

See `claudedocs/scripts.md` for all available scripts.

## Repository Stats

Recounted 2026-08-05. Approximate by intent: expect drift, recount rather than trust.

| Category | Files |
|----------|-------|
| Java backend (`src/main/java`) | ~140 |
| Java tests (`src/test/java`) | ~120 (of which ~104 named `*Test.java`) |
| TypeScript/TSX frontend (`src/main/webui/src`) | ~85 (~53 `.tsx`, ~31 `.ts`) |
| Playwright e2e specs (`src/main/webui/e2e`) | ~15 |
| Python scripts (`scripts/`) | ~75 (hand-written; excludes `scripts/workbook/.venv`) |
| Bash scripts (`scripts/`) | ~68 (plus `./planner.sh`) |
| Documentation (`claudedocs/`) | 100+ |

## Project Overview

A **Quarkus + Timefold Solver** application for Italian vocational school timetable scheduling.
Uses **React 18** frontend (via Quinoa), **PostgreSQL** database, **Hibernate Panache** ORM.

The single planning entity is `Lesson` (one school hour). Lab sessions are ordinary `Lesson`
rows pre-pinned by the pre-assignment pipeline, not a separate entity.

## Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Backend | Quarkus | 3.40.1 |
| Language level | Java | 21 (`maven.compiler.release`) |
| Solver | Timefold Solver | 2.7.0 |
| Database | PostgreSQL (prod), H2 (tests) | - |
| ORM | Hibernate ORM with Panache | - |
| Frontend | React 18 + Vite (Quinoa 2.7.2) | - |
| Build | Maven (no wrapper) | - |
| Testing | JUnit 5, REST Assured, Playwright | - |
| i18n | ResourceBundle (backend), react-i18next (frontend) | - |

## Directory Structure

```
src/main/java/com/school/planner/
  domain/          # JPA entities (@PlanningEntity, Panache)
    constraints/   # ConstraintJustification classes (UI drill-down)
  solver/          # Constraint providers, custom phases, listeners, filters
  service/         # Business logic (pre-assignment, import, generation, i18n)
  rest/            # JAX-RS resources (CRUD + solver control)
  rest/dto/        # Data transfer objects (FeasibilityReport, LessonViolationAnalysis)
  bootstrap/       # DataGenerator (seed data, protected file)
  exception/       # Custom exceptions
  util/            # Shared helpers

src/main/webui/
  src/
    components/    # React components (timeline, admin, controls)
      admin/       # CRUD pages + FilterBar
      benchmark/   # Benchmark results views
      docs/        # ConstraintCard for docs page
      timeline/    # Timeline/Week view components
      ui/          # UI primitives (Icons, ActionButton, StatusBadge, Toast)
    api/           # REST client (scheduleApi.ts)
    contexts/      # React contexts
    hooks/         # Custom hooks (useScheduleData, useLessonViolations, etc.)
    data/          # Static data (constraints.ts, solverPhases.ts)
    i18n/          # Internationalization (en/it locales)
    types/         # TypeScript domain types
    utils/         # Utility functions
  e2e/             # Playwright end-to-end tests (NOTE: sibling of src/, not inside it)

src/main/resources/
  solverConfig.xml # Timefold solver configuration (5 phases)
  application.properties
  messages_en.properties  # English i18n
  messages_it.properties  # Italian i18n
  seed/            # incarichi.csv, calendario CSVs (real school data)

src/test/java/     # JUnit tests (H2 in-memory)
scripts/           # api, benchmark, core, db, analyze, ui, validate, container
claudedocs/        # Architecture + campaign documentation
  spalmone-2026-27/  # 2026/27 campaign: status docs, task*-results, golden data
backlog/           # Task management (tasks, completed, archive, milestones)
```

## Entry Points

| Type | Path | Description |
|------|------|-------------|
| Backend Main | `src/main/java/com/school/planner` | Quarkus app (port 8080) |
| Frontend Main | `src/main/webui/src/main.tsx` | React app entry |
| Tests | `src/test/java/` | `./scripts/core/test.sh` (H2) |
| CLI | `./planner.sh` | DB + app management |
| Solver Config | `src/main/resources/solverConfig.xml` | 5-phase pipeline |

## Domain Entities

`src/main/java/com/school/planner/domain/` holds 17 `@Entity` classes (one of which,
`TestEntity`, is test scaffolding) plus enums, comparators and value objects.

| Entity | Purpose | Key Fields |
|--------|---------|------------|
| `SchoolSchedule` | Planning solution container | score, solverStatus, lessons |
| `Lesson` | Single school hour, the ONLY `@PlanningEntity` | timeslot, room, pinned, course |
| `Timeslot` | Date + time slot | date, slotIndex, dayOfWeek, startDateTime |
| `Room` | Classroom | name, capacity, tags |
| `Teacher` | Instructor | name, external, pseudo, maxDailyHours |
| `StudentGroup` | Class cohort | name, year, section, maxHoursPerDay |
| `Course` | Subject offering | subject, teacher, studentGroup, totalHours |
| `LabRotation` | Lab rotation pattern | teacher, group, dayOfWeek, period |
| `StagePattern` | Internship periods | group, dayOfWeekMask, startDate-endDate |
| `DailyActivityQuota` | Per-(group, date) expected hours facts | aulaHours, labHours, stageHours, FCQuota |
| `GroupCalendarRule` | Per-group open-slot calendar | group, dayOfWeek, minSlot, maxSlot |
| `RecurringAvailabilityRule` | Weekly teacher availability template | teacher, dayOfWeek, slots |
| `TeacherAvailability` | Dated teacher constraints | type (VACATION/PREFERRED/SICK) |
| `ProfessionalizzantiDay` | Reserved professionalizzanti weekday | group, dayOfWeek |
| `LabAssistant` | Lab assistant assignment | teacher, roomTag |
| `SchoolHoliday` | Non-school days | date |
| `ValidationSnapshot` | Persisted validation run | timestamp, payload |
| `PreAssignmentStatus` | Enum | IDLE, RUNNING, COMPLETE, ERROR |
| `AvailabilityType` | Enum | VACATION, PREFERRED, SICK |
| `IdealEndDateInfo` | Target date calc | targetEndDate, periodOverride |
| `CompletionInfo` | Per-group completion summary | scheduled, expected |

**No `LabBlock`.** The class was deleted with TASK-228/267. Labs are pinned `Lesson` rows
(`isLabHour`). Remaining `LabBlock` occurrences in the codebase are comments and javadoc
recording the removal, plus one stale sentence in `solverConfig.xml`; there is no class, no
field, no planning entity. Do not reintroduce it in docs.

### Comparators and weight factories
- `LessonDifficultyComparator`, `LessonDifficultyWeightFactory` - entity ordering for CH
- `TimeslotStrengthComparator`, `TimeslotStrengthComparatorFactory` - value ordering for CH

## REST API Resources

23 `*Resource` classes in `src/main/java/com/school/planner/rest/`, plus
`PreAssignmentExceptionMapper`, `DataMutationTrackingFilter` and `RootPathHandler`.

| Resource | Purpose | Key Endpoints |
|----------|---------|---------------|
| `ScheduleResource` | Solver control, analysis, import/export | GET /api/schedule, POST /solve, POST /solve-staged, DELETE /solve |
| `PreAssignmentResource` | CSP pre-assignment | POST /pre-assign, GET /status |
| `PhaseProgressionResource` | Phase tracking | GET /phase-progression, GET /export |
| `ValidationResource` | Data validation runs | GET/POST /api/validation |
| `ReconciliationResource` | Hours reconciliation report | GET /api/reconciliation |
| `LessonResource` | Lesson operations | PUT /pin, PUT /assign |
| `TeacherResource` | Teacher CRUD | CRUD endpoints |
| `StudentGroupResource` | Student group CRUD | CRUD endpoints |
| `StudentGroupCompletionResource` | Completion tracking | GET /completion |
| `RoomResource` | Room CRUD | CRUD endpoints |
| `CourseResource` | Course CRUD | CRUD + /generate-lessons |
| `LabRotationResource` | Lab rotation CRUD | CRUD endpoints |
| `LabAssistantResource` | Lab assistant CRUD | CRUD endpoints |
| `HolidayResource` | Holiday CRUD | CRUD endpoints |
| `StagePatternResource` | Stage pattern CRUD | CRUD endpoints |
| `TeacherAvailabilityResource` | Availability CRUD | CRUD endpoints |
| `RecurringAvailabilityRuleResource` | Weekly availability CRUD | CRUD endpoints |
| `GroupCalendarRuleResource` | Group calendar CRUD | CRUD endpoints |
| `ProfessionalizzantiDayResource` | Professionalizzanti day CRUD | CRUD endpoints |
| `SlotConfigResource` | Slot schema config | GET /api/config/slots |
| `CsvImportResource` | Bulk CSV import | POST /import/{entityType} |
| `IndexResource` | Static index / health | GET / |

Full endpoint reference: `claudedocs/rest-api.md`.

## Service Layer

37 files in `src/main/java/com/school/planner/service/`.

| Service | Purpose |
|---------|---------|
| `ScheduleService` | Core solver orchestration, schedule management, analysis drill-down |
| `GoldenScheduleImportService` | Import the school's hand-made golden timetable as pinned lessons |
| `CalendarioImportService` | Import calendario sheets (MODA lab pins, Stage pins, off-grid hours) |
| `HybridPhase1aService` | CSP-based lab rotation pre-assignment |
| `PreAssignmentValidator` | Validates pre-assignment feasibility |
| `PreAssignmentContext` | Context for pre-assignment batches |
| `PreAssignmentEntityExtractor` | Extract entities for pre-assignment |
| `PreAssignmentFeasibilityReport` | Pre-assignment feasibility analysis |
| `DayAssignmentCsp` | Day-level CSP used by pre-assignment |
| `GreedyRotationAssigner` | Greedy rotation assignment |
| `AdaptiveSlotFinder` | Smart slot finding with validation |
| `RotationTestRunner`, `RotationCandidate`, `SlotCandidate`, `TeacherDayAssignment` | Rotation search support |
| `LessonGenerator` | Generate Lesson entities from Courses |
| `ImportService` | Schedule import/export for A/B testing |
| `PhaseProgressionService` | Track phase timing and scores |
| `SolverConfigParser` | Parse solver XML configuration (phase names, timeouts) |
| `ConstraintMessageFormatter` | I18n violation messages (ResourceBundle) |
| `ValidationService`, `ValidationSnapshotRepository` | Data validation runs and persistence |
| `ReconciliationService` | Assigned vs scheduled vs expected hours reconciliation |
| `GroupCalendarService` | Group calendar resolution |
| `AvailabilityExpansionService` | Expand recurring availability rules into dated records |
| `TargetEndDateCalculator`, `ExpectedDaysCalculator` | Target end date and expected school days |
| `StudentGroupCompletionService` | Track student group completion |
| `FailureAnalysis`, `HybridPhase1aReport`, `GoldenImportReport`, `CalendarioImportReport`, `LabSession`, `DataMutationEvent` | Reports and value objects |

## Solver Components

`src/main/java/com/school/planner/solver/` (25 files).

| Component | Purpose |
|-----------|---------|
| `BaseConstraintProvider` | Shared constraint stream building blocks |
| `SchoolScheduleConstraintProvider` | Full constraint set (H/M/S) |
| `DayClusteredConstructionPhase` | Phase 1: weekly-template stamp + round-robin repair tail |
| `WeeklyTemplatePlanner` | Pre-solve template reference (also feeds M32) |
| `WeeklyTemplateBuilder` | Pure-Java greedy weekly template builder |
| `CompactionPhase` | Deterministic compaction custom phase |
| `StudentDayCompaction` | Left-anchor / compaction pass |
| `BoxRepair`, `HoleRepair`, `LabDayConsolidation`, `AnchorRepair`, `SurplusRebalance`, `QuotaFillRepair` | Deterministic repair passes (staged cycle, post-B-TIMESLOT, in this order). QuotaFillRepair (TASK-533) closes H32 under-quota days moving hours from surplus days with border-aware moves, at TWO occasions (post-RELEASE and post-B-ROOM) |
| `ExactFinisher` | Exact completion of the boxed H0 residual, post-SurplusRebalance/pre-B-ROOM (TASK-478) |
| `CompositionHoleCloser` | Hole-closer building block, committed DEFAULT-OFF (shell-game guard, TASK-481) |
| `CompositeSwapFinisher` | Delivery stage, H0 composite relay (TASK-609, ratified) |
| `EmptyDayFinisher` | Declared-empty-day closer with relay family + H32 targets (TASK-560/612, ratified) |
| `HoleRelayFinisher` | H17/H7 repair: rotation-with-permission, import cross-date, x-swap, compact-swap, h7cap pair, h7-depth3 chain, h7swap prototype (TASK-613/619/620/621/629/632), the delivery stage last mile |
| `RoomTailFinisher` | H29 room-tag tail closer (TASK-550, ratified) |
| `MultiStartRunner`, `PortfolioPickBest` | Multi-start retry wrapper + its deterministic pick-best core (hard, medium), DEFAULT ON (TASK-483) |
| `RotationEnumerator` | TASK-467 fase-2 skeleton: exhaustive enumerator of regular lab rotations |
| `StagedPinPolicy` | Staged solve pin promotion/demotion policy |
| `PhaseTransitionMarker` | Phase boundary marker custom phase |
| `PhaseTrackingListener` | Track current solver phase |
| `SolverProgressionListener` | Detect phase transitions from score patterns |
| `UnpinnedLessonFilter` | Filter unpinned entities |
| `GapPreservingMoveFilter`, `CompositeMoveFilter` | Move filtering during local search |
| `TimeslotSequence` | Timeslot ordering, adjacency, ISO week utilities |

### Solver pipeline (5 phases, since TASK-420)

1. CH - Lesson Placement (`DayClusteredConstructionPhase`)
2. LS - Compactness
3. Repair + Compaction (`HoleRepair`/`LabDayConsolidation`/`AnchorRepair`/`SurplusRebalance`/`QuotaFillRepair`/`ExactFinisher` + `CompactionPhase`)
4. CH - Room Assignment
5. LS - Room Refinement

Phase names are authoritative in `SolverConfigParser.PHASE_NAMES`. The inert LS RuinRecreate
phase was removed by TASK-420. Full architecture: `claudedocs/solver-phases.md`.

**Reference solve mode for the 2026/27 campaign is STAGED** (`POST /api/schedule/solve-staged`,
adopted 2026-07-27), not the plain `POST /api/schedule/solve`. Since TASK-483 (2026-08-03) the
staged cycle is wrapped by `MultiStartRunner`: GRASP-randomized retry, pick-best on the
attempts' boundary timeslot-tier hard (comparable axis; full score as tie-break, TASK-498
corner-b 21/08),
stop at first 0-hard (DEFAULT ON). The orchestration layer (multi-start + repair + ExactFinisher)
secures hard=0; the LS metaheuristic is a medium-quality knob (`claudedocs/orchestration-layer-architecture.md`).

## Frontend Components

`src/main/webui/src/components/` (top level, plus `admin/`, `benchmark/`, `docs/`, `timeline/`, `ui/`).

### Main Components
| Component | Purpose |
|-----------|---------|
| `App.tsx` (in `src/`) | Navigation, theme/lang switcher |
| `ScheduleTimeline.tsx` | Weekly timetable (Week/Timeline toggle) |
| `UnifiedSolverControls.tsx` | Solve, Analyze, Progression, Feasibility buttons |
| `AnalysisPanel.tsx` | Modal: constraint score breakdown |
| `ProgressionPanel.tsx` | Modal: phase progression timeline |
| `ValidationPanel.tsx`, `ValidationBadge.tsx` | Data validation results |
| `ReconciliationPage.tsx` | Hours reconciliation view |
| `BenchmarkResultsPage.tsx` | Benchmark results view |
| `DocsPage.tsx` | Constraint documentation |
| `SolverPhasesPage.tsx` | Solver phase documentation |
| `ComplexityPage.tsx` | Complexity analysis |
| `LessonTooltip.tsx` | Hover tooltip for lessons |
| `PreAssignErrorNotification.tsx` | Pre-assignment error UI |
| `ScheduleFilterBar.tsx` | Filter by teacher/group/room |
| `ScheduleSidebar.tsx` | Legend + stats |
| `UnassignedSidebar.tsx` | Draggable unassigned lessons |
| `ThemeSelector.tsx` | Light/Dark/school theme toggle |

### Admin Components (`components/admin/`)
`AdminLayout.tsx`, `FilterBar.tsx`, `styles.tsx`, `CoursesAdmin.tsx`, `CourseGrid.tsx`,
`TeachersAdmin.tsx`, `StudentGroupsAdmin.tsx`, `RoomsAdmin.tsx`, `HolidaysAdmin.tsx`,
`StagePatternsAdmin.tsx`, `TeacherAvailAdmin.tsx`, `LabAssistantsAdmin.tsx`,
`GroupCalendarAdmin.tsx`, `ProfessionalizzantiDaysAdmin.tsx`, `SchoolSettingsAdmin.tsx`,
`CsvImportWizard.tsx`.

### Timeline Components (`components/timeline/`)
`TimelineView.tsx`, `WeekViewGrid.tsx`, `TimelineHeader.tsx`, `VirtualizedDayRow.tsx`,
`IdealEndMarker.tsx`, `CompletionMarker.tsx`, `cellRenderer.tsx`, `types.ts`, `index.ts`.

### UI Components (`components/ui/`)
`Icons.tsx`, `ActionButton.tsx`, `StatusBadge.tsx`, `ToastContainer.tsx`, `variants.tsx`, `index.ts`.

### Hooks (`src/main/webui/src/hooks/`)
`useAdminList.ts`, `useHiddenGroups.ts`, `useLessonViolations.ts`, `useScheduleData.ts`,
`useScheduleFilter.ts`, `useScheduleNavigation.ts`, `useSlotSchema.ts`,
`useReconciliationBadge.ts`, `useReconciliationReport.ts`. Theme context lives in `src/main/webui/src/theme.tsx`.

## Configuration Files

| File | Purpose |
|------|---------|
| `pom.xml` | Maven dependencies, build config |
| `src/main/resources/application.properties` | Quarkus configuration |
| `src/main/resources/solverConfig.xml` | Timefold solver config (5 phases) |
| `src/main/resources/messages_en.properties` | English translations (violation messages) |
| `src/main/resources/messages_it.properties` | Italian translations (violation messages) |
| `src/main/webui/package.json` | Frontend dependencies |
| `src/main/webui/vite.config.ts` | Vite build config |
| `src/main/webui/tsconfig.json` | TypeScript config |
| `src/main/webui/tailwind.config.js` | Tailwind CSS config |
| `src/main/webui/playwright.config.ts` | E2E test config |
| `solver.xsd` | Timefold solver XML schema (for validation) |

## Utility Scripts

Roughly 50 bash and 40 python scripts under `scripts/`, organised by directory. Full
documentation: `claudedocs/scripts.md` and `scripts/README.md`. Highlights only below.

### Bash
| Script | Purpose |
|--------|---------|
| `./planner.sh` | DB profile + app lifecycle |
| `./scripts/api/api.sh` | REST API calls (status, score, analysis, ...) |
| `./scripts/api/app-health.sh` | Health check endpoint |
| `./scripts/api/constraint-violations.sh` | Safe constraint violation drill-down |
| `./scripts/api/import-golden.sh`, `import-calendario.sh`, `import-schedule.sh` | Data imports, A/B export |
| `./scripts/db/db-query.sh` | PostgreSQL queries (read-only unless `-w`) |
| `./scripts/core/rebuild.sh` | Quick rebuild (backend + frontend) |
| `./scripts/core/rebuild-checked.sh` | Full rebuild with preflight checks |
| `./scripts/core/test.sh` | Run tests |
| `./scripts/core/dev-start-and-wait.sh` | Background start + poll readiness |
| `./scripts/core/prod-start-and-wait.sh` | Background start prod + poll |
| `./scripts/benchmark/capture-run.sh` | Canonical post-solve capture (score, export, all KPIs, viz) |
| `./scripts/benchmark/run-benchmarker.sh` | Timefold benchmark harness for metaheuristic A/B (Tabu vs VND, ACL sweeps; driven by `solverBenchmarkConfig*.xml`, repaired 2026-08-04) |
| `./scripts/benchmark/laptop-RUNBOOK.md` | Reference procedure for long solves on the laptop |
| `./scripts/benchmark/run-honest-ab.sh`, `overnight-benchmark.sh` | A/B and overnight campaigns |
| `./scripts/ui/ui-build.sh`, `ui-dev.sh`, `ui-test.sh`, `ui-typecheck.sh` | Frontend build/test/typecheck |
| `./scripts/validate/validate-solver-xml.sh` | XSD validation of solver XML |
| `./scripts/container/podman-tools.sh` | Container management |

### Python
| Script | Purpose |
|--------|---------|
| `./scripts/db/db-stats.py` | Database statistics, profile listing |
| `./scripts/db/data-integrity-check.py` | Data integrity checks |
| `./scripts/analyze/daily-fill-kpi.py` | Full-day rate vs expected hours |
| `./scripts/analyze/day-start-kpi.py` | First-hour anchoring |
| `./scripts/analyze/resemblance-kpi.py` | Weekly recurrence (with per-group breakdown) |
| `./scripts/analyze/cadence-kpi.py` | Cadence, burst ratio, gap-weeks per (group, subject) |
| `./scripts/analyze/hole-topology-kpi.py` | Internal holes vs early finish |
| `./scripts/analyze/fingerprint-sharpness-kpi.py` | Teacher week sharpness |
| `./scripts/analyze/residual_decomposition.py` | Zero-hard loop step 1: families + cells of a delivered residual, cure-registry lookup, auto-generates its viz layer |
| `./scripts/analyze/post_ls_catalog.py` | Post-LS solution catalog (TASK-634): add/status/list entries with seed-hash freshness guard |
| `./scripts/analyze/course_pacing_viz.py` | Golden-vs-run course hour distribution by month (the 636-class finder) |
| `./scripts/analyze/day_anatomy_viz.py` | One (group, date) golden vs run per slot, EMPTY highlighted |
| `./scripts/analyze/family_matrix_viz.py` | Runs x hard-families migration matrix (628 insight, visual) |
| `./scripts/analyze/sanity_stoplight_viz.py` | Sanity report as per-group stoplight strip |
| `./scripts/analyze/golden-vs-run-viz.py` | Golden vs run HTML comparison (4 views) |
| `./scripts/analyze/teacher-tier-kpi.py` | Generic vs specialised teacher pacing (`--html` for the visual report) |
| `./scripts/analyze/shared.py` | Shared parsing/classification used by the KPI scripts |
| `./scripts/workbook/generate_workbook.py` | Build the school-year input workbook draft (TASK-445) |
| `./scripts/workbook/xlsx_to_csv.py` | Workbook sheets back to the canonical CSVs |
| `./scripts/workbook/availability_compress.py` | Teacher availability to concise rules and back |
| `./scripts/workbook/test_*.py` | The workbook identity gates (run them, they exit non-zero on any drift) |
| `./scripts/analyze/verify-datagenerator.py` | DataGenerator verification |
| `./scripts/analyze/capacity-check.py`, `feasibility-audit.py`, `box-feasibility.py` | Capacity and feasibility analyses |
| `./scripts/analyze/derive-golden-*.py` | Independent Python cross-checks of golden-derived facts |

KPI usage and the golden-vs-run protocol are documented in `claudedocs/scripts.md`
(section "KPI golden-vs-run") and in `CLAUDE.md`.

## Test Coverage

Recounted 2026-08-05. Roughly 120 Java files under `src/test/java` (about 104 named
`*Test.java`), of which ~78 are `@QuarkusTest` and 8 use Timefold's `ConstraintVerifier`.
Nine more are benchmark/IT harnesses that do not run in the normal suite.

| Package | Focus |
|---------|-------|
| `bootstrap/` | Real-seed fixtures, golden regression guard, snapshot/checkpoint harnesses |
| `solver/` | Constraint verification, custom phase behaviour |
| `service/` | Import services, pre-assignment, reconciliation |
| `rest/` | REST endpoints (`@QuarkusTest` + REST Assured) |
| `domain/` | VRP filters, entity validation |
| `integration/` | Full solve integration |
| `benchmark/` | Benchmark harnesses (not part of the normal suite) |
| `testsupport/`, `util/` | Shared fixtures and helpers |

Guards worth knowing by name: `GoldenSolutionRegressionTest` (constraint pins against the
school's golden timetable), `CalendarioImportRealSeedStageTest` (stage pin/off-grid
conservation), `WeeklyTemplateConstructionRealSeedTest` (construction-end recurrence and
full-day rate), plus the `*SnapshotTest` / `*CheckpointTest` family described in
`claudedocs/localized-testing-guidelines.md`.

Playwright e2e specs live in `src/main/webui/e2e/` (about 15 specs, run via `./scripts/ui/ui-test.sh`).

## Documentation Map

`claudedocs/` holds 100+ files and rots quickly. The maintained, load-bearing ones:

| File | Topic |
|------|--------|
| `CLAUDE.md` | Project instructions, constraint tables, working rules (authoritative) |
| `PROJECT_INDEX.md` | This file (navigation index) |
| `README.md` | Human-facing project overview and quickstart |
| `claudedocs/constraint-recalibration-history.md` | Full constraint recalibration history |
| `claudedocs/solver-phases.md` | 5-phase solver pipeline |
| `claudedocs/orchestration-layer-architecture.md` | Orchestration layer: operators, contracts, stochastic vs exact, literature |
| `claudedocs/solver-configurations.md` | Solver configuration analysis |
| `claudedocs/scripts.md` | Script reference, incl. KPI golden-vs-run battery |
| `claudedocs/rest-api.md` | REST endpoint reference |
| `claudedocs/database-schema.md` | PostgreSQL schema |
| `claudedocs/human-planner-heuristics.md` | Human planner 5-step pipeline |
| `claudedocs/localized-testing-guidelines.md` | Which test to run for which change |
| `claudedocs/branch-families.md` | Branch families and solver config variants |
| `claudedocs/pre-assignment-algorithms.md` | Pre-assignment algorithms |
| `claudedocs/spalmone-2026-27/` | 2026/27 campaign: status docs, `task*-results/`, golden data |

Everything else under `claudedocs/` (research notes, dated bug reports, benchmark dumps,
`claudedocs/design/`, `claudedocs/research/`) is historical: browse with `ls`, do not
assume any of it is current.

## REST API Endpoints (Key)

### Schedule & Solver
```
GET    /api/schedule                    → Full schedule
POST   /api/schedule/solve              → Start solver (single-shot)
POST   /api/schedule/solve-staged       → Start staged solve (campaign reference mode)
DELETE /api/schedule/solve              → Stop solver
GET    /api/schedule/status             → Solver status
POST   /api/schedule/reset              → Clear assignments
GET    /api/schedule/analysis           → Score breakdown
GET    /api/schedule/analysis/lessons   → Per-entity violations (?lang=en|it)
GET    /api/schedule/feasibility        → Feasibility check
POST   /api/schedule/reschedule         → Reschedule for teacher
GET    /api/schedule/export             → Export JSON (A/B testing, checkpoints)
POST   /api/schedule/import             → Import JSON
POST   /api/schedule/import-golden      → Import the golden timetable (pinned)
POST   /api/schedule/import-calendario  → Import calendario sheets (MODA + Stage pins)
```

### Phase Progression
```
GET    /api/phase-progression           → Phase history
GET    /api/phase-progression/export    → Export JSON
```

### Pre-Assignment
```
POST   /api/schedule/pre-assign           → Run pre-assignment
GET    /api/schedule/pre-assign/status    → Get status
POST   /api/schedule/pre-assign/reset     → Reset status
```

### Student Group Completion
```
GET    /api/student-groups/completion    → Get completion status
```

### CRUD
```
GET/POST/PUT/DELETE /api/teachers
GET/POST/PUT/DELETE /api/student-groups
GET/POST/PUT/DELETE /api/rooms
GET/POST/PUT/DELETE /api/courses
GET/POST/PUT/DELETE /api/lab-rotations
GET/POST/PUT/DELETE /api/lab-assistants
GET/POST/PUT/DELETE /api/holidays
GET/POST/PUT/DELETE /api/stage-patterns
GET/POST/PUT/DELETE /api/teacher-availability
GET/POST/PUT/DELETE /api/recurring-availability-rules
GET/POST/PUT/DELETE /api/group-calendar-rules
GET/POST/PUT/DELETE /api/professionalizzanti-days
```

## How to Run

```bash
# Create/start profile with seed data
./planner.sh start <profile>

# Restart app only (DB persists)
./planner.sh run

# Wait for app to be ready
./planner.sh wait

# Show what is running
./planner.sh status

# Kill the running Quarkus process
./planner.sh kill-app

# Stop DB container
./planner.sh stop

# Delete profile data
./planner.sh delete <profile>
```

`./planner.sh list` lists profiles; `python3 scripts/db/db-stats.py profiles` adds sizes and timestamps.

For automated startup in scripts, use `./scripts/core/dev-start-and-wait.sh dev`
(hooks block `planner.sh run`/`start` with a background `&`).

## Build Commands

**⚠️ IMPORTANT: This project does NOT have a Maven wrapper (`mvnw`). Direct `mvn` commands are NOT whitelisted. Use the provided scripts instead.**

```bash
# Quick rebuild (backend + frontend)
./scripts/core/rebuild.sh

# Full rebuild with preflight checks
./scripts/core/rebuild-checked.sh

# Frontend-only build
./scripts/ui/ui-build.sh

# Run tests
./scripts/core/test.sh

# TypeScript typecheck
./scripts/ui/ui-typecheck.sh

# Frontend dev server (hot reload, UI-only)
./scripts/ui/ui-dev.sh
```

## Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Quarkus | 3.40.1 | Framework |
| Timefold Solver | 2.7.0 | Constraint solver |
| Hibernate ORM | - | JPA/Panache |
| PostgreSQL | - | Production DB |
| H2 | - | Test DB |
| Quinoa | 2.7.2 | React integration |
| React | 18.3.1 | Frontend |
| Vite | 5.3.0 | Frontend build |
| TypeScript | 5.4.5 | Frontend language |
| Playwright | 1.58.2 | E2E tests |
| react-i18next | 14.1.0 | Frontend i18n |
| vis-timeline | 7.7.3 | Timeline visualization |
| react-window | 2.2.7 | Virtualization |
| Tailwind CSS | 3.4.3 | Styling |

## Branch Families

Historical experiment axis. All three families now share the same single planning entity
(`Lesson`) and the same constraint provider; the "Shadow" and "Block Entity" extra entities
(`LabFollower`, `LabBlock`) were removed by TASK-228/267, so the family names survive only as
branch names and solver-config variants.

| Family | Planning entities today | Constraint Provider |
|--------|-------------------------|---------------------|
| Baseline | `Lesson` | Shared |
| Shadow | `Lesson` (no `LabFollower` class remains) | Shared |
| Block Entity | `Lesson` (no `LabBlock` class remains) | Shared |

Details: `claudedocs/branch-families.md`.

## Canonical Constraint IDs

Not duplicated here (the copy that used to live in this file rotted and disagreed with reality).

- **Current tables (H/M/S, per branch family):** `CLAUDE.md`, section "## Canonical Constraint IDs".
- **Recalibration history and rationale:** `claudedocs/constraint-recalibration-history.md`.
- **In-code source of truth:** the `.asConstraint("<ID> - <description>")` strings in
  `SchoolScheduleConstraintProvider` and `BaseConstraintProvider`.

## Campaign Status

The active work is the 2026/27 "spalmone" campaign (closing the golden-vs-run gap).

- Latest status: the newest `claudedocs/spalmone-2026-27/campaign-status-*.md` (at the time of
  writing, `campaign-status-2026-08-05.md`: metaheuristic+repair investigation EXHAUSTED, pivot to the MODEL).
- Per-task evidence: `claudedocs/spalmone-2026-27/task*-results/` plus the pinned golden
  references under `claudedocs/spalmone-2026-27/data/`.
- Open milestones and tasks: `backlog/milestones/` (m-12 golden-gap capofila TASK-424; m-13
  genericity/multi-year gate, BLOCKED on source=input H17 bad-seed) and `backlog/tasks/`.

## Important Notes

1. **Frontend changes require rebuild**: Quinoa bakes React into the JAR at package time
2. **Use wrapper scripts**: direct `curl`/`psql`/`mvn` commands are NOT whitelisted
3. **No Maven wrapper**: this project does NOT have `mvnw`, use the provided scripts only
4. **Timefold**: on 2.7.0 (community edition). The old 1.31.0 IdentityHashSet patch is obsolete (fixed upstream in 1.32.0).
5. **DataGenerator**: contains real school data, DO NOT modify without permission (see `.claude/rules/datagenerator-protection.md`)
6. **Solver config**: 5-phase pipeline (CH lesson placement → LS Compactness → Repair + Compaction → CH room assignment → LS room refinement; the inert LS RuinRecreate was removed by TASK-420). Labs are pinned `Lesson` rows: there is no `LabBlock` entity (removed by TASK-228/267). See `claudedocs/solver-phases.md`.
7. **Staged solve**: `POST /api/schedule/solve-staged` is the campaign reference mode (adopted 2026-07-27)
8. **I18n**: backend uses ResourceBundle, frontend uses react-i18next
9. **Current branch**: `feature/cartesian-block-entity` (the name is historical, see Branch Families above)
