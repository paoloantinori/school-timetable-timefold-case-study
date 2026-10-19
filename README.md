# School Timetable Solver

A complete annual timetable for an Italian vocational school: 14 student groups,
26 teachers, roughly 12,500 lesson-hours to place across 174 school days, under
constraints the school cares about (no teacher or room in two places at once,
internship blackouts, per-group weekly calendars, daily quotas the school itself
declares). Building that timetable by hand takes the school's planners weeks
every year. This project builds it automatically, and validates the result
against the school's own hand-made solution.

## The problem

School timetabling at annual granularity is not the classic weekly university
problem. The plan must satisfy hard constraints (teacher, room and group
conflicts; internships that remove a group from the grid for weeks; declared
per-date hour quotas), stay close to the school's practice (which day each
group starts, how hours spread across the year, weekly recurrence), and remain
human-editable: planners pin lessons, move them by hand, and reschedule around
a sick teacher. A solver that ignores any of this produces a timetable the
school will not accept, whatever its score says.

The distinguishing asset of this repository: the school's hand-made timetable
(the "golden") exists and is valid. The whole project is built around using it
as a calibration oracle rather than a guess.

## What it does

- Produces a complete annual timetable (every lesson placed in a date, hour
  and room) from the school's input sheets: teaching assignments, calendars,
  per-date quotas, teacher availability.
- Scores and repairs the result through a staged pipeline: a construction
  heuristic that stamps a weekly template across the year, local search, then
  a chain of deterministic repair passes and multi-start retry that drives
  hard violations to zero.
- Validates every run against the golden timetable with a battery of KPIs
  (recurrence, pacing, daily fill) and a per-group sanity gate, so "better"
  means closer to what the school actually does, not just a higher score.
- Provides the web application the school's planners use: weekly and timeline
  views, pinning, manual placement by drag and drop, a reactive fix that
  reschedules a teacher's day, import of the school's sheets, constraint
  documentation and per-constraint score analysis.

## The approaches that made it work

- **Golden-first calibration.** Every constraint is checked against what the
  golden actually does; rules stricter than the school's own practice are
  demoted, practices invisible in the rules are promoted. The constraint set
  you see is the residue of that conversation, each change backed by a
  measured count. Full history: `claudedocs/constraint-recalibration-history.md`.
- **An orchestration layer around the solver.** The metaheuristic alone
  exhausted early; the durable wins came from deterministic repair passes
  adopted under an improve-or-noop oracle, multi-start over randomized
  constructions, and finishers targeting specific residual families.
  Architecture: `claudedocs/orchestration-layer-architecture.md`.
- **Agent-driven development with human gates.** Most of the model was
  discovered by AI agents measuring and refuting against the golden, with a
  human ratifying every adoption from pre-registered A/B evidence. The
  method is distilled in `docs/ai-solver-workflow.md`; the uncurated work
  journal (700+ task files with verdicts) is in `backlog/`.

## What you can take from it

If you are facing a similar problem (automating a plan that a human already
does well with a validated manual solution available), the transferable parts
are exactly those three: use the human artifact as the oracle before tuning
anything; build repair passes with per-move gates instead of trusting one
optimizer; and demand pre-registered evidence before adopting any change. The
[STORY.md](STORY.md) walks the whole arc; [LIMITS.md](LIMITS.md) bounds the
claims (single dataset, seed-dependent outcomes); [REJECTED.md](REJECTED.md)
indexes the dead ends so you can skip them. Everything here runs: CI builds
the JAR and runs the test suite on every push.

License: [MIT](LICENSE).

## Architecture

```
React Frontend (vis-timeline)  <-->  Quarkus REST API  <-->  Timefold Solver
                                           |
                                    PostgreSQL (Panache)
```

The solver assigns lessons to timeslots and rooms. Hard constraints cover teacher, room and student group conflicts, internship blackouts, teacher unavailability, room suitability, school holidays, per-group calendars, day start at the first open slot, daily quota fill against the school's declared plan, and a per-group daily hour floor. Medium and soft constraints cover daily fill against the school's per-date plan, compactness, weekly recurrence, teacher gaps and teacher preferences.

A lesson is one school hour. Lab sessions are ordinary lessons pre-pinned by the
pre-assignment pipeline, not a separate entity type.

## Prerequisites

- **Java 21+** (the build targets Java 21)
- **Maven 3.8+** (there is no Maven wrapper in this repo)
- **Node.js 18+** and **npm** (for the React frontend)
- **PostgreSQL 14+** (or use Podman/Docker for a container)

## Quickstart

`./planner.sh` manages both the database container and the app. A profile is a named
Podman volume, so data survives restarts.

```bash
./planner.sh start dev     # start DB + app, seeding a fresh profile on first run
./planner.sh status        # show what is running
./planner.sh run           # restart the app only (DB keeps running)
./planner.sh kill-app      # stop the app
./planner.sh stop          # stop the DB container
./planner.sh delete dev    # delete a profile's data
```

The app is at **http://localhost:8080**.

## Database Setup (manual, if you are not using `planner.sh`)

### Option A: Podman (recommended)

```bash
podman run -d \
  --name planner-db \
  -e POSTGRES_USER=planner \
  -e POSTGRES_PASSWORD=planner \
  -e POSTGRES_DB=planner \
  -p 5432:5432 \
  docker.io/library/postgres:17
```

### Option B: Docker

```bash
docker run -d \
  --name planner-db \
  -e POSTGRES_USER=planner \
  -e POSTGRES_PASSWORD=planner \
  -e POSTGRES_DB=planner \
  -p 5432:5432 \
  postgres:17
```

### Option C: System PostgreSQL

Create the database and user:

```sql
CREATE USER planner WITH PASSWORD 'planner';
CREATE DATABASE planner OWNER planner;
```

## Development

```bash
# Install frontend dependencies (first time only)
cd src/main/webui && npm install && cd ../../..

# Rebuild backend + frontend, restart the app, wait for readiness
./scripts/core/rebuild.sh

# Frontend-only dev server with hot reload (UI work)
./scripts/ui/ui-dev.sh
```

Quinoa bakes the React bundle into the JAR at package time, so **frontend changes need a
rebuild**, not just a restart. `rebuild.sh` handles kill, build, restart and wait.

Do NOT run `mvn quarkus:dev` on a profile you care about: it uses `drop-and-create` and
wipes the data.

## Running Tests

```bash
./scripts/core/test.sh        # backend (JUnit 5, H2 in-memory)
./scripts/ui/ui-typecheck.sh  # TypeScript typecheck
./scripts/ui/ui-test.sh       # Playwright end-to-end tests
```

Backend tests use an H2 in-memory database (no Docker/Podman required). They cover the
constraint set via Timefold's `ConstraintVerifier` plus real-seed regression guards, most
notably `GoldenSolutionRegressionTest`, which pins each constraint's score against the
school's own hand-made ("golden") timetable.

## Building for Production

```bash
./scripts/core/rebuild.sh          # or, for a clean build with preflight checks:
./scripts/core/rebuild-checked.sh
```

The resulting JAR at `target/quarkus-app/quarkus-run.jar` includes the bundled React frontend.

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

For production, configure the datasource via environment variables:

```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db-host:5432/planner
export QUARKUS_DATASOURCE_USERNAME=planner
export QUARKUS_DATASOURCE_PASSWORD=secure-password
export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=update
```

## Configuration

Key settings in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `quarkus.timefold.solver.solver-config-xml` | `solverConfig.xml` | Solver pipeline definition |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/planner` | Database URL |
| `quarkus.hibernate-orm.schema-management.strategy` | `update` | Schema generation strategy (never use `drop-and-create` on a profile you care about: it wipes the data) |

Solver termination is **not** set in `application.properties`. It is configured per phase
(plus a global limit) in `src/main/resources/solverConfig.xml`.

## Usage Workflow

### 1. Seed Data (automatic on first boot)

On startup, if the database is empty, `DataGenerator` seeds the real 2026/27 school year:
- **26 teachers** plus one pseudo teacher ("Stage") used to account internship hours
- **14 student groups** (1-G11, 1-G10, G12, 1-G09, 1-G08, G13, G14)
- **1,392 timeslots** (174 school days, Mon-Fri, 2026-09-14 to 2027-06-15, 8 slots/day)
- **29 closure dates** (Christmas, Easter, Carnevale, national holidays)
- **18 rooms** (one non-assignable, R18), and **~275 course entries** from
  `src/main/resources/seed/incarichi.csv` plus 8 Stage pseudo-courses

`DataGenerator.java` holds real school data and is a protected file: do not edit it without
asking. Seeding is idempotent (skipped when timeslots already exist).

### 2. Review and adjust via the Admin UI

The seed already covers rooms, courses, lessons, calendars and availability for 2026/27,
so this step is normally review and correction rather than data entry. Navigate to the
**Administration** tab:

1. **Rooms**: capacity and tags (a tag such as `informatica` marks a lab room)
2. **Courses**: student group, teacher and total hours per course
3. **Generate Lessons**: regenerate a course's lesson rows after changing its hours
4. **Stage Patterns**: internship blackout periods per student group
5. **Teacher Availability**: sick days, vacations, recurring weekly rules, preferences
6. **Group Calendars**: which slots are open for each group on each weekday

On a fresh profile, run the calendario import (`./scripts/api/import-calendario.sh`) before
the first solve: it pins the lab and internship hours the school has already committed to.

### 3. Solve

On the **Schedule** tab:
1. Click **Solve** to start the constraint solver
2. Watch the timeline populate as the solver finds solutions
3. Score updates show hard/medium/soft constraint violations
4. Click **Stop** when satisfied, or let the configured time limit elapse

The solver runs a 5-phase pipeline (lesson placement, compactness, repair and compaction,
room assignment, room refinement), documented in `claudedocs/solver-phases.md`. There is
also a **staged** solve mode (`POST /api/schedule/solve-staged`) that solves in successive
stages, promoting and demoting pins between them; it is the reference mode used for the
2026/27 scheduling campaign. Since 2026-08-03 the staged cycle is wrapped by a **multi-start**
retry (randomized construction, then pick the best of several attempts by hard score then
medium score, stopping at the first feasible schedule), which reliably produces a fully
feasible schedule on the 2026/27 data.

#### Results on the 2026/27 data

The staged + multi-start solve delivers **fully feasible schedules** (zero hard-constraint
violations) with high reliability. The live series alternates zero-hard deliveries with
near misses of one or two points, which the delivery-stage repair passes (composite swap,
empty-day relay, hole relay) close at the delivery stage. Typical quality across the school
year: about **91% of student-days filled** to the school's own per-date expected hours,
**~99% of days starting at the first slot**, and **~70% weekly recurrence** of the weekly
template. These are campaign-reference figures; the local search is intentionally
non-reproducible, so individual runs vary, and the multi-start wrapper exists to absorb
that variance and deliver a feasible schedule. Latest status and detail:
`claudedocs/spalmone-2026-27/campaign-status-2026-08-13.md`.

The development loop that produces those numbers is written down in the
**zero-hard-loop skill** (`.claude/skills/zero-hard-loop/SKILL.md`). Every delivered
residual is decomposed into families and cells, checked against the school's golden
timetable, and either cured at the source or cataloged as structural.

### 4. Adjust

- **Pin lessons**: Click a lesson to toggle pinned status (solver won't move pinned lessons)
- **Reactive Fix**: Use "Reactive Fix" to reschedule a sick teacher's lessons for a specific day
- **Manual assignment**: Drag-drop lessons on the timeline

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/schedule` | Full schedule with score and solver status |
| `POST` | `/api/schedule/solve` | Start solver (single-shot) |
| `POST` | `/api/schedule/solve-staged` | Start staged solve |
| `DELETE` | `/api/schedule/solve` | Stop solver |
| `GET` | `/api/schedule/analysis` | Constraint score breakdown |
| `POST` | `/api/schedule/reschedule` | Reactive reschedule `{teacherId, date}` |
| `PUT` | `/api/lessons/{id}/pin` | Toggle pinned status |
| `PUT` | `/api/lessons/{id}/assign` | Manual assignment `{timeslotId, roomId}` |
| `GET/POST/PUT/DELETE` | `/api/rooms` | Room CRUD |
| `GET/POST/PUT/DELETE` | `/api/teachers` | Teacher CRUD |
| `GET/POST/PUT/DELETE` | `/api/courses` | Course CRUD |
| `GET/POST/DELETE` | `/api/holidays` | Holiday CRUD |
| `GET/POST/PUT/DELETE` | `/api/student-groups` | Student group CRUD |
| `GET/POST/DELETE` | `/api/stage-patterns` | Stage pattern CRUD |
| `GET/POST/DELETE` | `/api/teacher-availability` | Teacher availability CRUD |
| `POST` | `/api/import/{entityType}` | CSV import (multipart) |
| `POST` | `/api/courses/{id}/generate-lessons` | Generate lessons for a course |
| `POST` | `/api/schedule/import-golden` | Import the school's golden timetable (pinned) |
| `POST` | `/api/schedule/import-calendario` | Import calendario sheets (lab and stage pins) |

The full endpoint reference is in `claudedocs/rest-api.md`.

## Constraints

The constraint set has grown well past the original handful and is split across three
score levels (hard, medium, soft). It is documented in one place rather than duplicated
here:

- **The list of constraint IDs and their meaning:** `CLAUDE.md`, section
  "Canonical Constraint IDs".
- **Why each one is weighted the way it is:** `claudedocs/constraint-recalibration-history.md`.
- **In the running app:** the **Docs** tab renders every constraint with its description,
  and the **Analyze** button breaks the current score down per constraint.
- **In code:** `solver/SchoolScheduleConstraintProvider.java` and
  `solver/BaseConstraintProvider.java`.

## How this project is developed (and how to work on it with an AI agent)

Most of the constraint model in here was not designed on paper. It was discovered by
measuring a real hand-made timetable and refuting hypotheses against it, with an AI agent
doing the exploration and a human ratifying every adoption. The method, including the traps
that are specific to Timefold (constraints that go blind on uninitialised variables, score
arbitrage between tiers, silent zeros), is written up in
**[docs/ai-solver-workflow.md](docs/ai-solver-workflow.md)**.

If you are an AI agent that has just been pointed at this repository, read that document
first: it tells you which test to run for which kind of change, why a full solve is never a
development loop, and what evidence a change needs before it can be adopted.

## Internationalization

The frontend supports English and Italian. Switch languages using the dropdown in the header. All UI strings are externalized via `react-i18next`.

## Project Structure

```
planner/
├── pom.xml
├── planner.sh               # DB profile + app lifecycle
├── scripts/                 # api, core, db, benchmark, analyze, ui, validate
├── claudedocs/              # Architecture + campaign documentation
├── backlog/                 # Task management
├── src/main/
│   ├── java/com/school/planner/
│   │   ├── domain/          # JPA entities + planning model
│   │   ├── solver/          # Constraint providers + custom solver phases
│   │   ├── service/         # Schedule service, imports, pre-assignment
│   │   ├── rest/            # JAX-RS resources
│   │   └── bootstrap/       # Startup data generator (protected)
│   ├── resources/
│   │   ├── application.properties
│   │   ├── solverConfig.xml # Solver pipeline
│   │   └── seed/            # Real school CSVs
│   └── webui/               # React frontend (Quinoa)
│       ├── src/
│       │   ├── components/  # Timeline, admin CRUD pages
│       │   ├── api/         # API client
│       │   ├── i18n/        # English + Italian translations
│       │   └── types/       # TypeScript domain types
│       ├── e2e/             # Playwright tests
│       └── package.json
└── src/test/
    └── java/                # JUnit + constraint verifier + real-seed guards
```

## Documentation

| Where | What |
|-------|------|
| `PROJECT_INDEX.md` | Navigation index: where everything lives |
| `CLAUDE.md` | Working rules and the canonical constraint tables |
| `claudedocs/solver-phases.md` | The solver pipeline, phase by phase |
| `claudedocs/rest-api.md` | Full REST endpoint reference |
| `claudedocs/database-schema.md` | PostgreSQL schema |
| `claudedocs/scripts.md` | Every helper script, including the KPI battery |
| `claudedocs/human-planner-heuristics.md` | How the school's human planners actually work |

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Quarkus 3.40.1, Java 21 |
| Solver | Timefold Solver 2.7.0 |
| Database | PostgreSQL + Hibernate ORM Panache |
| Frontend | React 18, TypeScript 5, vis-timeline |
| Frontend Serving | Quarkus Quinoa 2.8.3 (Vite 5) |
| i18n | ResourceBundle (backend), react-i18next (frontend) |
| Testing | JUnit 5, ConstraintVerifier, REST Assured, H2, Playwright |
