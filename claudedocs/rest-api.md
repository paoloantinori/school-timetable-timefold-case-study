# REST API Endpoints

## Root / Index HTML

```
GET    /                           → serves index.html (React SPA)
GET    /assets/{path}              → serves static assets (JS, CSS, fonts)
```

**GET /** - Serves the React application index.html:
- Returns: HTML content with proper HTTP header ordering (headers before body)
- Cache-Control: `public, immutable, max-age=86400`
- Content-Type: `text/html;charset=UTF-8`
- **Dual-mode serving:**
  - **Production JAR mode**: Loads from classpath resources (`META-INF/resources/`)
  - **Dev mode**: Falls back to file system (`target/quinoa/build/`)
- Purpose: Works around Vert.x FileServer bug where body content can be sent before headers, causing curl "HTTP/0.9 when not allowed" errors

**GET /assets/{path}** - Serves static assets (JS bundles, CSS, fonts):
- Returns: Asset content with appropriate Content-Type
- Cache-Control: `public, immutable, max-age=31536000` (1 year)
- Supports: `.js`, `.css`, `.woff`, `.woff2`, `.ttf`, `.svg`, `.png`, `.jpg`, `.jpeg`
- Same dual-mode serving as index.html

## Schedule & Solver

```
GET    /api/schedule                    → get full schedule (lessons, labBlocks, score, solverStatus, stagedStage)
GET    /api/schedule/status           → lightweight status poll, no DB: solverStatus, score, stagedStage, stagedAttempt/MaxAttempts, refiningForDelivery, stagedOutcome (TASK-667: COMPLETE/CANCELLED/FAILED once the cycle ends, null while running), scoreRestorePending (TASK-667: async post-restart score restore in flight)
POST   /api/schedule/solve              → start solver (returns 202 Accepted)
POST   /api/schedule/solve-staged       → TASK-419 staged solve: construction → promote the template cells to pins → solve the tail/box on the timeslot phases → selective demotion → release → room phases (202; progress in `stagedStage`, on both GET /status and GET /api/schedule)
DELETE /api/schedule/solve              → stop solver early (also cancels a staged cycle: the running stage is terminated early, a cancel before the promotion skips it entirely, and a cancel at any point skips the room stage)
POST   /api/schedule/reset              → reset all assignments (clears timeslot/room, unpins all entities); 409 while a staged cycle runs
GET    /api/schedule/export             → export complete schedule as JSON (for A/B testing); 409 while a staged cycle runs
POST   /api/schedule/import             → import complete schedule from JSON (replaces all data); 409 while a staged cycle runs
GET    /api/schedule/analysis           → constraint score breakdown (?all=true to include zero-score). Score is BendableScore(2 hard + 2 soft) since TASK-538: entries and the top level expose `hard` (pure, level 0), `quasi` (level 1, H34/H32 when `planner.constraints.quasi-tier.enabled=true`, 0 otherwise), `medium`, `soft`; the `score` string is the bracket format (e.g. `[0/-12]hard/[-500/0]soft`)
GET    /api/schedule/analysis/lessons   → per-entity violation analysis (?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD)
GET    /api/schedule/feasibility        → binary validity verdict (hard == 0), message carries a stable code
POST   /api/schedule/reschedule         → reschedule (reset + solve for teacher on date)
POST   /api/schedule/import-golden      → import the hand-made golden 2026/27 timetable as pinned Lessons (?force=true|false)
POST   /api/schedule/import-calendario  → import the school's pre-set Calendario scolastico as pinned MODA Lessons (?dryRun=true|false&force=true|false)
```

**`stagedStage`** (TASK-419) - `null` unless a staged solve is running; otherwise one of
`A-CONSTRUCTION`, `PROMOTION`, `B-TIMESLOT-<n>`, `C-DEMOTION-<n>`, `RELEASE`, `B-ROOM`, in
that order (the two numbered ones repeat: the cycle can demote and re-solve, at most twice).
`B-TIMESLOT` runs the pipeline's timeslot phases with the promoted template pins held;
`RELEASE` hands those pins back, and only then does `B-ROOM` run the room CH + refinement -
a pin freezes the whole entity, room included, so the rooms can only be harmonized after the
release. Poll it instead of `solverStatus`: every stage runs on an ad-hoc solver, so
`solverStatus` reports `NOT_SOLVING` for the whole cycle. Match `B-TIMESLOT`/`C-DEMOTION` by
PREFIX (they carry the iteration number).

**`stagedAttempt`** / **`stagedMaxAttempts`** / **`stagedBudgetSeconds`** (TASK-483) - multi-start
retry state, all `0` after the solve ends. The retry is DEFAULT ON since the 2026-08-03 ratification
(opt-out with `-Dplanner.multistart.enabled=false`). When on, the whole staged cycle retries with
distinct GRASP construction seeds (attempt 1 deterministic, ≥2 randomized), keeping the best
solution and stopping at 0-hard; `stagedAttempt` is the current 1-based attempt and
`stagedMaxAttempts` the cap, so a UI can show "tentativo X di Y" alongside `stagedStage`;
`stagedBudgetSeconds` is the configured time-budget cap (`planner.multistart.budget-seconds`, 0
when unset) for an honest worst-case hint. The retry is sequential in-JVM and each attempt
self-cleans, so it is safe to drive the existing cycle N times.

**POST /api/schedule/import-golden** (TASK-346) - Import
`src/main/resources/seed/lessons.csv` (the hand-made "Orario in righe" grid,
12,979 rows) as pinned `Lesson`s via `GoldenScheduleImportService`:
- Refuses with `409 Conflict` while the solver is running (same status source
  as `ScheduleService#resetSchedule`), without changing anything.
- Query param: `force` (default `false`) - if any `Lesson` already exists and
  `force` is not `true`, returns `409 Conflict` without changing anything; if
  `force=true`, **ALL** existing `Lesson`s are deleted first - both pinned and
  unpinned, across every group, not just the golden rows being re-imported.
- Returns `200 OK` with a `GoldenImportReport`:
  ```json
  {
    "rows": 12979,
    "imported": 10542,
    "importedStage": 2009,
    "skippedEsame": 0,
    "skippedClosed": 117,
    "unmatchedNoTimeslot": 0,
    "unmatchedNoCourseRows": 0,
    "unmatchedNoCourseTriples": [],
    "esameCoursesCreated": 6,
    "raCoursesCreated": 0,
    "deletedPinned": 0,
    "deletedUnpinned": 0
  }
  ```
  (TASK-366: `imported` was `10496` and `unmatchedNoCourseRows` was `31` -
  9 real teachers' G08 "RA"-subject rows, e.g.
  `{"teacher": "T23", "subject": "RA", "group": "G08", "rowCount": 9}` -
  before those rows got their own per-teacher micro-courses instead of being
  dropped; see below.)
- Stage rows (`teacher="Stage"`) are pinned under a synthetic per-group Stage
  course (created lazily); Esame rows are never scheduled (skipped), but a
  synthetic per-group Esame accounting course (pseudo-teacher, zero scheduled
  Lessons) is created from `incarichi.csv`'s Esame rows so the hours still
  reconcile; the RA pool no longer gets an accounting course (`raCoursesCreated`
  is always 0 since the TASK-592 cure: the G08 RA row was replaced by 9 real
  per-teacher rows, so the golden's RA hours match real courses); rooms are always `null` (the golden grid has no room
  column). `deletedPinned`/`deletedUnpinned` are always `0` unless this call
  used `force=true`, in which case they report exactly how many pre-existing
  `Lesson`s of each kind were wiped. `Teacher.pseudo=true` marks Stage/Esame/RA
  so H1 (teacher conflict), H7 (max daily hours) and S1 (minimize teacher
  gaps) exclude them - see `claudedocs/design/stage-esame-ra-modeling-2026-07-16.md`
  for the modeling decisions and `claudedocs/spalmone-2026-27/seed-csv-format.md`
  for the lessons.csv schema and the exact bucket counts.
- **TASK-366:** lessons.csv rows with the raw subject abbreviation "RA" under a
  real teacher never real-match (the alias map has no RA entry), so they always
  route to the RA fallback: on the current seed all 41 rows (G08, 9 real
  teachers). Since the TASK-592 cure (11/09) the fallback's find-or-create
  REUSES the 9 real per-teacher incarichi courses (no new micro-courses,
  `raCoursesCreated` stays 0) and pins the golden rows under them, folded into
  `imported` - instead of being dropped into `unmatchedNoCourseTriples`, which
  used to punch mid-day holes in G08's schedule and fabricate 22 H17
  vertical-compactness violations (see `claudedocs/spalmone-2026-27/golden-audit.md`).
- Intended for a dedicated, otherwise-empty profile - see
  `./scripts/api/import-golden.sh` and the "golden profile" how-to in
  `claudedocs/scripts.md`.

**POST /api/schedule/import-calendario** (TASK-364) - Import the school's
pre-set "Calendario scolastico 2026/27" (the coordinator's pre-placed lab/stage
day structure - see `claudedocs/spalmone-2026-27/calendario-scolastico-format.md`,
the binding reading key) via `CalendarioImportService`:
- Refuses with `409 Conflict` (bare `{"error": ...}` body, no report) while the
  solver is running - same guard as `import-golden`; also refuses the same way
  if pinned Lessons already exist for the MODA target courses from a previous
  call, unless `force=true` (see below, TASK-364 code review K2).
- Query params:
  - `dryRun` (default `false`) - `true` always returns the full report and
    never persists (and never checks/touches prior pins, see `force` below).
    `false` persists ALL-OR-NOTHING: if every `calendario_moda.csv` row
    validates cleanly, pinned `Lesson`s are created and `200 OK` is returned
    with `"persisted": true`; if ANY row fails validation, NOTHING is
    persisted and `409 Conflict` is returned with the SAME report body
    (`"persisted": false`) so the caller still sees every failure, not just a
    generic error string.
  - `force` (default `false`, ignored when `dryRun=true`) - if pinned Lessons
    already exist for the MODA target courses (i.e. this endpoint was already
    called successfully against this profile), a plain re-run refuses with
    `409 Conflict`. `force=true` deletes those prior pins first and
    immediately restores an equal number of unpinned stub Lessons for the same
    courses, then re-plans and re-pins from the CSV - a clean force re-run
    reproduces the exact same final Lesson counts (`deletedPriorPins` in the
    report below is 0 unless a force re-run found something to clear; the
    restored-stub count is always identical to it by construction, so it
    isn't reported as a second field).
- Returns a `CalendarioImportReport`:
  ```json
  {
    "dryRun": true,
    "persisted": false,
    "modaRows": 313,
    "modaPlanned": 310,
    "modaLessonsPinned": 922,
    "modaSkippedNoTimeslot": 2,
    "modaFailures": [
      {"line": 311, "date": "2027-06-09", "group": "G05", "initial": "PE",
       "reason": "student group conflict: G05 already has 'Confezione' (T16) at 2027-06-09 slot 3"}
    ],
    "FCRows": 909,
    "FCDiscrepancies": [ {"group": "G08", "date": "2027-03-03", "reason": "stage indicator outside StagePattern window", "detail": "..."} ],
    "classDaysRows": 2413,
    "classDaysDiscrepancies": [
      {"group": "G14", "date": "2026-09-15", "reason": "exceeds calendar capacity", "detail": "..."},
      {"group": "G07", "date": "2027-04-12", "reason": "stage-pattern-without-calendario", "detail": "..."}
    ],
    "stubConsumptions": [
      {"teacher": "T17", "subject": "Modello (Moulage)", "group": "G01",
       "lessonsPinned": 143, "consumedStubs": 143, "stubShortfall": 0}
    ],
    "deletedPriorPins": 0
  }
  ```
- `calendario_moda.csv` (MODA TUTTI's PE/MU/BO columns for 1/2/G09) rows
  are the only ones ever pinned - each row's teacher initial resolves to a
  teacher name (`PE`=T17, `MU`=T16, `BO`=T07) and its signed
  shift decodes to a slot range (see `CalendarioImportService#computeSlotRange`
  javadoc for the full first/second-shift rule, verified against every odd
  cell magnitude 1/2/5/6; hours=4 is defined by the same fallback rule but
  never occurs in the real 2026/27 workbook - TASK-364 code review K8).
  `modaPlanned` counts CSV **rows** placed cleanly; `modaLessonsPinned` counts
  the individual **Lesson** hours those rows produce (a 3-hour row is 1
  planned row but 3 pinned Lessons). `modaSkippedNoTimeslot` is non-blocking
  (mirrors `unmatchedNoTimeslot` above - on the current seed it's exactly the
  2 rows dated after `TimeslotSequence.SCHOOL_YEAR_END`); `modaFailures`
  (calendar-closed slot, teacher/group conflict, unresolvable course) IS
  blocking - `modaPlanned + modaSkippedNoTimeslot + modaFailures.size()`
  always equals `modaRows`. On the real 2026/27 seed there is exactly 1 known
  failure (the 2027-06-09 G05 mixed-shift row pair - see the format doc's
  "8 true divergences" callout).
- `stubConsumptions` (TASK-364 code review K1, only populated when
  `"persisted": true`): `LessonGenerator` pre-creates `Course.totalHours`
  unpinned "stub" Lessons per course before pre-assignment runs. After N
  Lessons are pinned for a MODA course, N of its unpinned stubs (preferring
  ones with no timeslot yet) are deleted so the course's total Lesson count
  still equals `totalHours` - otherwise the solver would additionally place
  the leftover stubs, silently doubling that course's scheduled hours. A
  non-zero `stubShortfall` means fewer stubs existed than were pinned, so this
  course now has more total Lessons than its `totalHours` - a data issue
  worth investigating, not silently corrected.
- `calendario_fc.csv` (DEF FC TUTTI's G04/G08/G13 Au/La/St split) and
  `calendario_class_days.csv` (the 14 per-class sheets) are **report-only** -
  teacher resolution for FC/G13 lab hours is ambiguous (no teacher-initial
  columns), so these never become Lessons. Instead each (group, date) is
  cross-checked against `GroupCalendarRule` open-slot counts and `StagePattern`
  windows; mismatches land in `FCDiscrepancies`/`classDaysDiscrepancies` as the
  coherence signal the school's process evolution needs (this is NOT
  necessarily a bug in either the calendario or the seed - see
  `claudedocs/spalmone-2026-27/seed-csv-format.md` for the caveats, e.g. G14
  attending Mon-Wed per the calendario but modeled as closed those days per the
  golden-derived `GroupCalendarRule`). This check is one-directional
  (calendario row → does a `StagePattern`/`GroupCalendarRule` agree); the
  reverse direction is checked too, but only for `StagePattern` (TASK-364 code
  review K6): every `StagePattern` day-in-window with zero reported calendario
  stage hours lands in `classDaysDiscrepancies` as
  `"stage-pattern-without-calendario"`. The symmetric `GroupCalendarRule`
  reverse check is intentionally out of scope - see
  `CalendarioImportService#crossCheckDay`'s javadoc for why.
- Wrapper script: `./scripts/api/import-calendario.sh --dry-run`.

## Print Sheets (TASK-659)

```
GET    /print/{kind}/{id}       → the (b) dedicated printable view: a standalone XHTML sheet of the week, no app chrome
GET    /print/{kind}/{id}.pdf   → the (c) server-rendered PDF of the SAME sheet (openhtmltopdf)
```

`kind` is Italian on the wire because these URLs are read by school staff:
`docente` (a teacher's week), `classe` (a class's week), `aula` (a room's week).
`?week=YYYY-MM-DD` is any date inside the wanted week (Monday is derived;
defaults to today, malformed values fall back to today, never 500). The sheet
carries its own header (school, school year, subject name, week range), a
subject-color legend, and per-cell subject/group-or-teacher/room. The HTML view
includes a screen-only toolbar (browser print + "Scarica PDF" link to the `.pdf`
endpoint, hidden in print); the PDF omits it. Unknown kind or id: 404. The UI
links here from the timeline header ("Stampa", enabled when the filter has
exactly one selected teacher or class, regardless of the view grouping). Colors
follow the app's on-screen priority (teacher color, then course color, then a
stable per-subject pastel; the docente sheet keys on materia so it is not
monochrome). Cell colors are free-form admin input validated to strict hex at
render time. A naked URL without `?week` defaults to the server's today (server
timezone; the UI always passes the visible week).

## Debug Mode

Disable **ALL constraints** for pure Construction Heuristic (CH) testing. Used to verify CH placement behavior without scoring influence.

```
POST   /api/schedule/constraints/debug-mode   → set debug mode (?enabled=true|false)
GET    /api/schedule/constraints/debug-mode   → get current debug mode state
```

**POST /api/schedule/constraints/debug-mode** - Set debug mode:
- Query param: `enabled=true` to disable all constraints, `enabled=false` to restore normal operation
- Returns: `{"debugConstraintsDisabled": true|false}`
- Use case: Verify pure CH placement (e.g., early timeslot preference)

**GET /api/schedule/constraints/debug-mode** - Get debug mode state:
- Returns: `{"debugConstraintsDisabled": true|false}`

**What debug mode does:**
- Returns ZERO constraints from `defineConstraints()`
- CH places entities with zero score influence
- Resulting schedule will have conflicts (teacher double-booked, room double-booked)
- Score remains exactly `0hard/0medium/0soft` throughout

**Example usage:**
```bash
# Enable debug mode
./scripts/api/api.sh raw /api/schedule/constraints/debug-mode?enabled=true

# Run solver in debug mode
./scripts/api/api.sh solve

# Verify score is exactly zero
./scripts/api/api.sh score | jq
# Expected: {"hardScore":0,"mediumScore":0,"softScore":0}

# Disable debug mode
./scripts/api/api.sh raw /api/schedule/constraints/debug-mode?enabled=false
```

**See also**: `claudedocs/solver-debugging.md` for complete documentation on debug mode usage.

## Phase Progression

Track solver phase timing and score changes throughout the solving process.

```
GET    /api/phase-progression           → get all phase records (completed + current)
GET    /api/phase-progression/export    → export phase progression as JSON file download
GET    /api/phase-progression/config    → get solver timeout configuration
```

**Phase Record Structure:**
```json
{
  "phaseNumber": 1,
  "phaseName": "CH - Lab Block Placement",
  "startTime": "2026-03-07T10:30:00Z",
  "endTime": "2026-03-07T10:32:15Z",
  "startScore": "-5000hard/-200medium/-300soft",
  "endScore": "-100hard/-50medium/-20soft",
  "timeSpentMillis": 135000,
  "isComplete": true,
  "secondsSpentLimit": 0,
  "unimprovedSecondsSpentLimit": 0
}
```

**Timeout Configuration (GET /api/phase-progression/config):**
```json
{
  "overallSecondsSpentLimit": 28800
}
```

**Timeout Fields:**
- `secondsSpentLimit` — Hard timeout in seconds for this phase (0 = no explicit limit, inherits from overall)
- `unimprovedSecondsSpentLimit` — Smart termination threshold in seconds (0 = no limit)
- `overallSecondsSpentLimit` — Overall solver hard timeout in seconds (default: 28800 = 8 hours)

**Phases:**
1. **CH - Lab Block Placement**: Construction heuristic places multi-hour lab blocks
2. **CH - Lesson Placement**: Construction heuristic places single-hour lessons
3. **LS - Compactness**: Local search optimizes vertical/horizontal compactness
4. **LS - Conflict Resolution**: Local search resolves remaining medium violations
5. **LS - Room Refinement**: Local search optimizes soft constraints (teacher gaps, distribution)

## Schedule Response Timing Fields

`GET /api/schedule` returns cumulative solver timing that persists across stop/resume cycles:

| Field | Type | Description |
|-------|------|-------------|
| `cumulativeElapsedSeconds` | long | Total solver wall-clock time across all stop/resume cycles (includes live delta while solving) |
| `cumulativeChSeconds` | long | Total construction heuristic time across all sessions |
| `cumulativeLsSeconds` | long | Total local search time across all sessions |
| `preAssignmentDurationSeconds` | long | Duration of last pre-assignment run (0 if not run) |
| `solveStartTime` | Instant? | Current session start timestamp (null when not solving) |
| `chCompletionTime` | Instant? | CH phase completion timestamp in current session (null if CH not yet complete) |
| `currentPhase` | int | Current solver phase number (0 = not running, 1-5 = active phase) |
| `phaseName` | String? | Human-readable phase name (e.g. "CH1: LabBlock placement") |

**Lifecycle:**
- **Start solving** (`POST /api/schedule/solve`): `solveStartTime` set, cumulative values preserved from previous sessions
- **While solving**: `cumulativeElapsedSeconds` = accumulated past sessions + live delta from current session
- **Stop solving** (`DELETE /api/schedule/solve`): current session duration added to accumulators, `solveStartTime` cleared
- **Reset** (`POST /api/schedule/reset`): all cumulative fields zeroed

## Export/Import (A/B Testing)

Export and import endpoints enable A/B testing of solver configurations with identical starting conditions.

```
GET    /api/schedule/export             → Export complete schedule as JSON
POST   /api/schedule/import             → Import schedule from JSON (replaces all data)
```

**Use case**: Run pre-assignment once → Export → Test solver config A → Import same state → Test solver config B → Compare scores

**Export (`GET /api/schedule/export`)**:
- Returns complete schedule state as JSON (all entities: lessons, labBlocks, courses, teachers, rooms, timeslots, etc.)
- Response type: `application/json`
- Filename format: `schedule-export-YYYY-MM-DDTHH:MM:SS.sssZ.json`

**Import (`POST /api/schedule/import`)**:
- Accepts `SchoolSchedule` JSON in request body
- Replaces ALL existing data in a single transaction
- Validates all entity references before committing
- Returns `400 Bad Request` with error details if validation fails

**Validation**:
- Lesson timeslot/room/course references must exist in imported data
- LabBlock startTimeslot/room/course references must exist
- Course teacher/studentGroup references must exist

**Transaction safety**: If validation fails, no data is modified. Original schedule remains intact.

## Pre-Assignment Pipeline

The pre-assignment pipeline uses a CSP-based (Constraint Satisfaction Problem) approach that mimics human planner behavior: iterate rotation candidates, test each for 0hard violations, and pin successful ones.

**Phases run sequentially**: acconciatura → labs → professionalizzanti

**Zero-rotation no-op (TASK-367):** Phase 1/2 rely on `LabRotation` rows to know which day to place acconciatura/lab sessions on. When the table is empty (as for the 2026/27 seed pending TASK-364), the pipeline skips all phases and completes immediately with `status: COMPLETE`, zero candidates/pinned, and `message` set to `"no rotations configured - nothing to pre-assign"` instead of failing with `status: ERROR`. `currentPhase` stays `null`, same as any other successfully completed run - it only ever holds a phase code (e.g. `"1-ACCONCIATURA"`) while `RUNNING`.

```
POST   /api/schedule/pre-assign           → Run hybrid pre-assignment (auto-resets previous results, async, returns 202)
GET    /api/schedule/pre-assign/status    → Get pre-assignment status
POST   /api/schedule/pre-assign/reset     → Reset pre-assignment status to IDLE (manual reset, usually not needed)
GET    /api/schedule/pre-assign/phases    → Get pre-assignment phase history (from last completed run)
```

**Pre-Assignment Phase History (GET /api/schedule/pre-assign/phases):**

Returns detailed phase results from the last completed pre-assignment run. Returns `404 Not Found` if no pre-assignment has been completed yet.

```json
{
  "message": "Pre-assignment phase history",
  "report": {
    "success": true,
    "status": "COMPLETE",
    "totalDurationMillis": 51234,
    "totalCandidatesEvaluated": 578,
    "totalEntitiesPinned": 1234,
    "phaseResults": [
      {
        "phaseName": "1-ACCONCIATURA",
        "success": true,
        "candidatesEvaluated": 120,
        "durationMillis": 15000,
        "entitiesPinned": 45,
        "roomTag": "acconciatura"
      },
      {
        "phaseName": "2-LABS-moda",
        "success": true,
        "candidatesEvaluated": 180,
        "durationMillis": 12000,
        "entitiesPinned": 380,
        "roomTag": "moda"
      },
      // ... more phase results
    ]
  }
}
```

**Async behavior**: POST returns 202 Accepted immediately. Poll `/status` to monitor progress.

**Idempotency**: If already running, returns 409 Conflict with current status.

## Slot Schema (TASK-358)

Exposes the 8-slot/day schema (`TimeslotSequence.SLOT_START_TIMES`, TASK-341) so
the frontend renders time periods and the lunch-break separator from data
instead of a hardcoded list.

```
GET    /api/config/slots             → { slots: [{index, startTime, endTime, isAfternoon}, ...], lunchBreakStart, lunchBreakEnd }
```

## Analysis Reports (2026-07-29)

Serves the generated HTML reports (golden-vs-run comparator, day-start anatomy, any
other artifact the KPI scripts write) straight from the running app, so during
development they are one click away instead of a `file://` URL on whichever machine
produced them. Read-only, `.html` only, path traversal rejected. The directory is
`planner.reports.dir` (default `claudedocs/spalmone-2026-27`, walked recursively,
newest first).

```
GET    /reports                      → HTML index of the available reports
GET    /reports/list                 → [{path, name, sizeKb, modified}, ...]
GET    /reports/{path}               → the report itself (404 outside the root or if not .html)
```

## CRUD Resources

### Teachers

DELETE cascades to the teacher's `RecurringAvailabilityRule` and
`TeacherAvailability` rows before deleting the teacher itself (TASK-345
widened the teacher FK graph with these two tables).

`maxDailyHours` (TASK-348) is a nullable per-teacher override of H7's max
daily hours cap; null = default (6, `Teacher.DEFAULT_MAX_DAILY_HOURS`).
`PUT` uses null-keeps semantics (same pattern as StudentGroup's
`maxHoursPerDay`): omitting the field in a partial payload leaves the
existing value alone, it does not clear it. `0` is a distinct sentinel
(TASK-348 code review D1) meaning "clear the override back to the
default" - it's mapped server-side to a real `null`, since `null` itself
is already taken by the null-keeps semantics above. Both `POST` and `PUT`
accept `0` or `1`-`8`; any other value (negative, or > 8) returns 400.

`maxEarlyStartDaysPerWeek` and `requiredFreeDaysPerWeek` (TASK-368) are two
more nullable per-teacher overrides following the exact same null-keeps /
0-clears pattern (accept `0` or `1`-`5`, enforced by M24/M25 respectively -
see `SchoolScheduleConstraintProvider`). `maxEarlyStartDaysPerWeek` caps how
many distinct days/week a teacher may start at slot 0 (08:00); T11=1.
`requiredFreeDaysPerWeek` requires at least this many Mon-Fri days/week with
zero lessons for the teacher; T05=2 (see `Teacher.requiredFreeDaysPerWeek`
javadoc for why 2, not the docx's literal 1 - his Friday is already a hard
full-day block, making 1 a no-op).

```
GET    /api/teachers                 → list all teachers
POST   /api/teachers                 → create teacher {name, nickname?, color?, pattern?, maxDailyHours?, maxEarlyStartDaysPerWeek?, requiredFreeDaysPerWeek?}
PUT    /api/teachers/{id}            → update teacher (each of the 3 optional counts: null-keeps, 0-clears, out-of-range -> 400)
DELETE /api/teachers/{id}            → delete teacher (cascades RecurringAvailabilityRule + TeacherAvailability)
```

### Student Groups

```
GET    /api/student-groups           → list all student groups
POST   /api/student-groups           → create student group
PUT    /api/student-groups/{id}      → update student group
DELETE /api/student-groups/{id}      → delete student group
```

`PUT` accepts `maxHoursPerDay` (nullable Integer) - editable from the Group
Calendar admin page (TASK-358). This field used to be silently dropped by the
update handler (no field ever copied it onto the managed entity); fixed
alongside the new UI.

**Null-keeps semantics (TASK-358 code review N1):** a `maxHoursPerDay` that is
`null` or omitted from the PUT body leaves the existing value unchanged - it
does NOT clear it. Several admin forms send partial payloads that never touch
this field (StudentGroupsAdmin: name/size/allowProf only; SchoolSettingsAdmin:
startDate only); treating a missing field as "clear" wiped the cap on every
such edit. Explicitly clearing the cap needs a dedicated affordance (not yet
built) rather than overloading `null`. When non-null, the value must be within
`1..TimeslotSequence.SLOT_START_TIMES.length` (currently 1-8) or the request
is rejected with 400.

### Rooms

```
GET    /api/rooms                    → list all rooms
POST   /api/rooms                    → create room
PUT    /api/rooms/{id}               → update room
DELETE /api/rooms/{id}               → delete room
```

### Courses

```
GET    /api/courses                  → list all courses
POST   /api/courses                  → create course
PUT    /api/courses/{id}             → update course
PUT    /api/courses/batch            → batch update courses (TASK-356 grid editor; one transaction for the whole list)
DELETE /api/courses/{id}             → delete course, cascading its Lesson rows first (mirrors TASK-345's TeacherResource cascade; without this, deleting a course with generated lessons FK-violates on `lesson.course_id`)
POST   /api/courses/{id}/generate-lessons → generate lesson entities for course
```

### Lessons

```
PUT    /api/lessons/{id}/pin         → toggle pin status; 409 CONFLICT if pinning (not unpinning) a lesson whose timeslot is null — the timeslot planning variable disallows unassigned values, so pinning one corrupts the Timefold model and later 500s the analysis endpoint / validation watchdog. Unpinning is always allowed.
PUT    /api/lessons/{id}/assign      → manual assign (timeslot + room)
```

### Stage Patterns

```
GET    /api/stage-patterns           → list all stage patterns
POST   /api/stage-patterns           → create stage pattern
DELETE /api/stage-patterns/{id}      → delete stage pattern
```

### Lab Rotations

```
GET    /api/lab-rotations            → list all lab rotations
POST   /api/lab-rotations            → create lab rotation
PUT    /api/lab-rotations/{id}       → update lab rotation
DELETE /api/lab-rotations/{id}       → delete lab rotation
```

### Group Calendar Rules (TASK-342)

Per-group, per-weekday slot-level calendar opening pattern (which slot indices
are open for a student group on a given weekday, with optional date-scoped
overrides). No matching rule for a group/weekday means every slot is open
(backward compatible). See `GroupCalendarService` for resolution semantics.

```
GET    /api/group-calendar-rules            → list all group calendar rules
POST   /api/group-calendar-rules            → create rule {studentGroupId, dayOfWeek, minSlot, maxSlot, effectiveFrom?, effectiveTo?}
PUT    /api/group-calendar-rules/{id}       → update rule
DELETE /api/group-calendar-rules/{id}       → delete rule
GET    /api/group-calendar-rules/resolved?from=yyyy-MM-dd&to=yyyy-MM-dd → resolved per-group per-date ranges
```

**`GET /resolved` (TASK-358 code review N2):** replaces the frontend's client-side
port of the most-specific-rule-wins resolution algorithm (`utils/groupCalendar.ts`),
which had drifted risk against the backend and no tests of its own. `from`/`to`
are required ISO dates (400 if missing, malformed, or `to` before `from`).
Response is a flat array, one entry per (student group, school-weekday date) in
the range (weekends are omitted):

```json
[
  { "groupId": 3, "date": "2026-09-14", "minSlot": 0, "maxSlot": 7, "closed": false },
  { "groupId": 3, "date": "2026-09-15", "minSlot": 0, "maxSlot": -1, "closed": true }
]
```

`closed` is redundant with `maxSlot < minSlot` (the sentinel `GroupCalendarRule`
itself uses) but is included explicitly so the frontend never needs to
replicate that convention. Computed by `GroupCalendarService.resolveRange()`:
per (group, weekday) the range is resolved once and reused across every
matching date when no date-scoped rule exists for that weekday; only weekdays
with at least one date-scoped override are resolved per-date. A group with
zero rules at all for a weekday falls back to `[0, maxHoursPerDay - 1]` when
`maxHoursPerDay` is set (legacy-profile fallback, N3), reproducing the old
frontend maxHoursPerDay-based graying heuristic for groups never migrated to
explicit calendar rules - otherwise "fully open" like the pure
`effectiveRange`/`isOpen` used by the solver (which are intentionally left
unchanged; H11 already enforces maxHoursPerDay as its own hard constraint).

### Lab Assistants

```
GET    /api/lab-assistants           → list all lab assistant assignments
POST   /api/lab-assistants           → create lab assistant (teacher + roomTag)
DELETE /api/lab-assistants/{id}      → delete lab assistant
```

### Teacher Availability

```
GET    /api/teacher-availability     → list all teacher availability records
POST   /api/teacher-availability     → create availability record (VACATION/PREFERENCE/SICK)
DELETE /api/teacher-availability/{id} → delete availability record
```

### Recurring Availability Rules (TASK-345)

Weekly-recurring teacher availability templates (day-of-week + inclusive slot
range + optional date-scoped window + VACATION/PREFERENCE type), mirroring
Group Calendar Rules' shape but for teachers. H5/S3 never read these rules
directly — every create/update/delete re-runs `AvailabilityExpansionService`
for the affected teacher, which expands the rule set into dated
`TeacherAvailability` rows (tagged via `sourceRuleId` for idempotent
delete-and-regenerate). See `claudedocs/spalmone-2026-27/availability-not-yet-modeled.md`
for the transcription table and what could NOT be expressed this way.

Create/update return 400 on: slot range out of bounds, unknown/missing
teacherId, non-VACATION/PREFERENCE type, `effectiveFrom` after `effectiveTo`,
a single-date rule (`effectiveFrom == effectiveTo`) whose `dayOfWeek` doesn't
match that date's actual weekday (it would silently expand to zero rows), and
a rule that overlaps an existing rule for the same teacher (same day of week,
intersecting slot range, intersecting effective window) — overlaps are
rejected regardless of VACATION/PREFERENCE type, since the additive
expansion model has no way to resolve two rules firing for the same hour. The
same two checks (date coherence, overlap) also run at seed time in
`DataGenerator`, failing startup loudly instead of silently mis-seeding.

```
GET    /api/recurring-availability-rules            → list all recurring availability rules
POST   /api/recurring-availability-rules            → create rule {teacherId, dayOfWeek, minSlot, maxSlot, type (VACATION/PREFERENCE), effectiveFrom?, effectiveTo?, note?}
PUT    /api/recurring-availability-rules/{id}       → update rule
DELETE /api/recurring-availability-rules/{id}       → delete rule
```

### Holidays

```
GET    /api/holidays                 → list all school holidays
POST   /api/holidays                 → create holiday
DELETE /api/holidays/{id}            → delete holiday
```

### Professionalizzanti Days (H4.1 Support)

```
GET    /api/professionalizzanti-days              → list all professionalizzanti day markers
POST   /api/professionalizzanti-days              → create professionalizzanti day marker
DELETE /api/professionalizzanti-days/{id}         → delete professionalizzanti day marker
```

**Request Body (POST):**
```json
{
  "studentGroupId": 123,
  "date": "2026-04-09",
  "dayOfWeek": "WEDNESDAY",
  "description": "G07 stage - Wednesday only"
}
```

**Purpose:** Marks specific days during stage periods when professionalizzanti courses should be scheduled. Used by H4.1 constraint to enforce that professionalizzanti lessons are on a constant day of the week during stage.

### CSV Import

```
POST   /api/import/{entityType}      → bulk CSV import (entityType: teachers, groups, courses, holidays)
```

## Lesson Violation Analysis

```
GET    /api/schedule/analysis/lessons   → per-entity violation analysis
```

**Query Parameters:**
- `startDate` (optional) - Start of date range (inclusive), ISO format `YYYY-MM-DD`. Defaults to `1900-01-01`.
- `endDate` (optional) - End of date range (inclusive), ISO format `YYYY-MM-DD`. Defaults to `2100-12-31`.
- `lang` (optional) - Language for violation messages. Supported values: `en` (English), `it` (Italian). Defaults to `en`.

**Example:**
```
GET /api/schedule/analysis/lessons?startDate=2025-10-06&endDate=2025-10-10&lang=en
GET /api/schedule/analysis/lessons?startDate=2025-10-06&endDate=2025-10-10&lang=it
```

Returns a list of Lessons and LabBlocks that have constraint violations, along with details about which constraints are violated.

**Returns 409 CONFLICT** if solver is actively running.

**Response:**

```json
[
  {
    "lessonId": 123,
    "entityType": "Lesson",
    "teacher": "T06",
    "subject": "Informatica",
    "studentGroup": "G03",
    "timeslot": "2025-10-06T08:00:00",
    "room": "Lab Info 1",
    "hasViolations": true,
    "totalScore": "0hard/-2medium/0soft",
    "violations": [
      {
        "constraint": "M3.1",
        "level": "MEDIUM",
        "score": 2,
        "message": "Non-lab subject Informatica in lab room Lab Info 1 at Mon 08:00"
      }
    ]
  }
]
```

**Fields:**
- `lessonId` — ID of the Lesson or LabBlock
- `entityType` — "Lesson" or "LabBlock"
- `teacher`, `subject`, `studentGroup` — course info (null if course is null)
- `timeslot` — ISO datetime string or null if unassigned
- `room` — room name or null if unassigned
- `hasViolations` — always true (only entities with violations are returned)
- `totalScore` — the indictment score string
- `violations` — list of constraint matches affecting this entity
  - `constraint` — Constraint ID (e.g., "H1.1", "M3.1")
  - `level` — "HARD", "MEDIUM", or "SOFT"
  - `score` — Absolute violation score
  - `message` — Human-readable explanation of the violation (localized based on `lang` parameter)

## Student Group Completion Analysis

Per-student-group statistics on curriculum completion, actual vs ideal end dates. Used for verifying compaction requirements and identifying groups that finish too early/late.

```
GET    /api/completion                    → get completion stats for all student groups
GET    /api/completion/{groupName}      → get detailed stats for one group
GET    /api/completion/{groupName}/summary → get lightweight summary (actual, ideal, diff)
```

**GET /api/completion** — All groups completion stats:
```json
{
  "groups": [
    {
      "groupName": "G09",
      "totalCurriculumHours": 666,
      "stageDaysCount": 40,
      "hoursBeforeStage": 586,
      "hoursDuringStage": 42,
      "hoursAfterStage": 38,
      "actualDaysWithLessons": 132,
      "actualEndDate": "2026-06-09",
      "idealEndDate": "2026-04-30",
      "daysDifference": 40
    }
  ],
  "slotUtilization": [
    {
      "groupId": 1,
      "groupName": "G03",
      "totalValidSlots": 990,
      "assignedSlots": 950,
      "unassignedSlots": 40,
      "utilizationPercentage": 95.96
    }
  ],
  "totalGroups": 14
}
```

**Field descriptions:**
- `totalCurriculumHours` — Sum of all course.totalHours for this group
- `stageDaysCount` — Number of weekdays this group is on stage (not in class)
- `hoursBeforeStage` — Curriculum hours scheduled before stage period starts
- `hoursDuringStage` — Hours scheduled DURING stage (should be 0 due to H4 constraint)
- `hoursAfterStage` — Hours scheduled after stage period ends
- `actualDaysWithLessons` — Count of distinct calendar days with lessons/LabBlocks
- `actualEndDate` — Date when last lesson/LabBlock is scheduled
- `idealEndDate` — Calculated target end date (assumes 6 hours/day, no gaps)
- `daysDifference` — `idealEndDate` to `actualEndDate` (negative = finished early, positive = finished late)
- **`slotUtilization`** — Array of slot utilization data (one per group)
  - `groupId` — Student group ID
  - `groupName` — Student group name
  - `totalValidSlots` — Total valid timeslots for this group (respecting filters: ideal end date, holidays, pinned lessons)
  - `assignedSlots` — Number of timeslots with assigned lessons
  - `unassignedSlots` — Valid timeslots without lessons (`totalValidSlots - assignedSlots`)
  - `utilizationPercentage` — Utilization percentage (`assigned / total * 100`)

**GET /api/completion/{groupName}/summary** — Lightweight response:
```json
{
  "groupName": "G09",
  "actualEndDate": "2026-06-09",
  "idealEndDate": "2026-04-30",
  "daysDifference": 40,
  "status": "FINISHED TOO LATE (40 days)"
}
```

Status labels:
- `FINISHED TOO EARLY` — More than 14 days before ideal
- `Finished early` — 1-14 days before ideal
- `On time` — Exactly on target date
- `Finished late` — 1-7 days after ideal
- `FINISHED TOO LATE` — More than 7 days after ideal

**Use cases:**
- Verify compaction: `GET /api/completion` should show `daysDifference` close to 0 for all groups
- Identify problem groups: `hoursDuringStage > 0` indicates H4 violations (lessons during stage)
- Debug early finishers: `daysDifference << 0` means lessons are front-loaded
- Debug late finishers: `daysDifference >> 0` means lessons are dragged to end of year

## Reconciliation Dashboard (TASK-353)

Replicates the school's own "Classi" Excel sheet: per (teacher, subject,
group) course, compares assigned hours (`Course.totalHours`, "Ore Inc.")
against scheduled hours (lessons with a placed timeslot, "Ore Prg."), plus
per-teacher/per-group rollups and a global summary. Synthetic Stage/Esame/RA
accounting courses (pseudo-teacher, see `Teacher.pseudo`) are included but
flagged `pseudo: true` so the UI can style their expected deltas
differently — see `claudedocs/design/stage-esame-ra-modeling-2026-07-16.md`.

```
GET    /api/reconciliation                    → full reconciliation report
GET    /api/reconciliation/{courseId}/lessons → drill-down: scheduled lessons for one course
```

**GET /api/reconciliation**:
```json
{
  "rows": [
    {
      "courseId": 42,
      "teacherId": 3,
      "teacherName": "T13",
      "subject": "Orientamento",
      "groupId": 7,
      "groupName": "G12",
      "assignedHours": 10,
      "scheduledHours": 0,
      "pinnedHours": 0,
      "delta": 10,
      "status": "UNSCHEDULED",
      "pseudo": false
    }
  ],
  "teacherRollups": [
    { "id": 3, "name": "T13", "assignedHours": 10, "scheduledHours": 0, "delta": 10, "issueCount": 1 }
  ],
  "groupRollups": [
    { "id": 7, "name": "G12", "assignedHours": 10, "scheduledHours": 0, "delta": 10, "issueCount": 1 }
  ],
  "summary": { "okCount": 120, "mismatchCount": 8, "unscheduledCount": 3, "totalCourses": 131 }
}
```

**Field descriptions:**
- `assignedHours` — `Course.totalHours` ("Ore Inc." in the school's sheet)
- `scheduledHours` — count of that course's `Lesson` rows with a non-null timeslot ("Ore Prg.")
- `pinnedHours` — of those, how many are pinned
- `delta` — `assignedHours - scheduledHours`
- `status` — `OK` (delta is zero), `MISMATCH` (nonzero delta but something is scheduled), or `UNSCHEDULED` (assigned hours exist but nothing was ever placed)
- `pseudo` — true for synthetic Stage/Esame/RA accounting courses (pseudo-teacher); their deltas are expected by design, not scheduling bugs
- `teacherRollups` / `groupRollups` — assigned/scheduled/delta summed across all of a teacher's or group's course rows, plus `issueCount`
- `issueCount` (on a rollup) — number of non-`pseudo` rows in that rollup with `status != OK` (`ReconciliationService#isIssue`). Drives the UI's subtotal severity coloring instead of the summed `delta`'s sign, since two rows offsetting at +5/-5 would otherwise net to a healthy-looking zero delta while still being two unresolved mismatches
- `summary` — global counts of rows by status

**GET /api/reconciliation/{courseId}/lessons** — drill-down for one course:
```json
[
  { "lessonId": 501, "date": "2027-03-01", "startTime": "2027-03-01T08:00:00", "roomName": "Aula 1", "pinned": true }
]
```

**Use cases:**
- Spot-check against the school's own Classi sheet: any `MISMATCH`/`UNSCHEDULED` row where `pseudo=false` is a real discrepancy to investigate
- `pseudo=true` rows with a delta are expected (Stage/Esame partial or zero scheduling) — do not treat as alarms
- Drill down into a specific course's placed lessons to see exactly which dates/rooms account for `scheduledHours`

**Note on `summary` vs the nav badge:** `summary.mismatchCount`/`unscheduledCount` are literal totals across all rows, including pseudo ones (mirrors the Excel sheet, which lists every row). The frontend's Reconciliation nav badge (`useReconciliationBadge`) instead counts only non-`pseudo` rows with `status != OK`, so it reflects actionable issues and can reach zero even while Stage/Esame's permanent expected deltas remain in `summary`.

**Note on frontend caching:** the nav badge and the Reconciliation page share one in-flight request per `useReconciliationReport` (`src/main/webui/src/hooks/useReconciliationReport.ts`), cached for `RECONCILIATION_CACHE_TTL_MS` (30s). The page invalidates and refetches on every mount and via its "Refresh" button; the badge instead polls at the TTL cadence and tolerates up to 30s of staleness as a background indicator. `invalidateReconciliation()` is exported for any future mutation site (course edits, lesson pin/assign, solve/reset) that should force the next read to be fresh.

## Feasibility Check

```
GET    /api/schedule/feasibility        → binary validity verdict (hard == 0), message carries a stable code
```

**Purpose**: The ONE binary verdict on the solution: hard constraints satisfied (`hardScore == 0`). The medium/soft values ride along as quality context, never as verdict. (TASK-660: the old medium threshold of -50000 "total gap minutes" was calibrated for a tier that now aggregates every medium constraint, so it failed structurally on every real delivery and was removed; the constraint-match breakdown also left this endpoint, it lives in GET /analysis, because a verdict needs only the score and a full match recompute costs minutes.)

**Feasibility Criteria**:
- `hardScore == 0`: all hard constraints satisfied; anything else is infeasible

**Response:**

```json
{
  "feasible": true,
  "message": "feasible",
  "score": "[0/-1200]hard/[-5.2M/0]soft",
  "hard": 0,
  "medium": -1200,
  "soft": -5200000
}
```

**Infeasible response (hard constraints violated):**

```json
{
  "feasible": false,
  "message": "hard_violated",
  "score": "[-5/-1200]hard/[-5.2M/0]soft",
  "hard": -5,
  "medium": -1200,
  "soft": -5200000
}
```

**Fields:**
- `feasible`: `true` iff `hard == 0`
- `message`: stable code the frontend localizes, one of `feasible`, `hard_violated`, `not_scored`, `no_solution` (`no_solution` is defensive: loadSchedule currently never returns null)
- `score`: full score string (e.g. `"[0/-1200]hard/[-5.2M/0]soft"`)
- `hard`: hard score value (must be 0 for feasibility)
- `medium`: medium score value (quality context, not a verdict)
- `soft`: soft score value (informational only)

**Use cases**:
- Show users whether the current schedule is valid (0 hard) or has residual conflicts
- Compare multiple solver runs by feasibility status

## Solution Legality Watchdog (TASK-354)

```
GET    /api/schedule/validation/latest  → latest validation snapshot + newly-illegal diff
POST   /api/schedule/validation/run     → force an immediate (non-debounced) re-validation
```

**Purpose**: constraint-relevant data (teacher availability, holidays, stage windows, calendars, course hours, room/group changes) can be edited independently of the solver. A previously-legal schedule can silently become illegal — the watchdog re-scores the CURRENT schedule (via the same open-source ScoreDirector snapshot machinery as `/api/schedule/analysis`, WITHOUT running the solver) and reports which constraints got worse since the last check.

**Trigger**: `DataMutationTrackingFilter` (a single `ContainerResponseFilter`, `rest/DataMutationTrackingFilter.java`) fires a CDI `DataMutationEvent` after any successful (2xx) POST/PUT/DELETE/PATCH to a constraint-relevant CRUD path prefix: `/api/teachers`, `/api/teacher-availability`, `/api/recurring-availability-rules`, `/api/holidays`, `/api/stage-patterns`, `/api/group-calendar-rules`, `/api/courses`, `/api/rooms`, `/api/student-groups`, `/api/lessons`, `/api/import`, `/api/lab-assistants`, `/api/professionalizzanti-days`, `/api/schedule/reset`, `/api/schedule/import-golden`, `/api/schedule/import-calendario`. `ValidationService` observes the event and debounces it 2 seconds (single-threaded scheduler; a burst of edits collapses into one validation run).

A second trigger source (code review B2) is solve completion: `ScheduleService.saveFinalSolution` — reached whether the solver stopped naturally or was stopped early — fires the same event internally with `trigger = "solve-completed"`, so the watchdog re-scores the just-saved schedule without waiting for an unrelated CRUD mutation.

Several mutating endpoints are deliberately **excluded** (`DataMutationTrackingFilter.WATCHED_EXCLUSIONS`, each with an inline comment explaining why): the validation endpoints themselves (`/api/schedule/validation`, self-trigger guard), solver start/stop (`/api/schedule/solve` — captured by the solve-completion hook instead), `/api/schedule/reschedule` (synchronously starts solving before the response returns), `/api/schedule/import` (whole-schedule JSON replace used only for A/B-testing solver configs, not real data entry), `/api/schedule/pre-assign` (async CSP pipeline pins Lessons from a background thread, bypassing this filter), `/api/schedule/constraints` (debug/dev-only constraint-provider toggles), and `/api/lab-rotations` (feeds the pre-assignment pipeline only, not read by the constraint provider directly). A test (`DataMutationWatchedPathExhaustivenessTest`) scans every JAX-RS resource in `com.school.planner.rest` and fails if a mutating endpoint is covered by neither list.

Validation is skipped while the solver `isBusy()` (any status other than `NOT_SOLVING`, including `SOLVING_SCHEDULED`), and short-circuited (zero score, no violations, not persisted as the latest snapshot) when no `Lesson` has an assigned timeslot yet.

**Response** (`ValidationResult`):

```json
{
  "timestamp": "2026-07-16T10:15:30Z",
  "trigger": "POST /api/holidays",
  "score": "-1hard/0medium/0soft",
  "hard": -1,
  "medium": 0,
  "soft": 0,
  "perConstraint": [
    { "constraintId": "H6", "matchCount": 1, "hard": -1, "medium": 0, "soft": 0 }
  ],
  "newlyViolated": [
    { "constraintId": "H6", "previousCount": 0, "currentCount": 1, "delta": 1, "hard": -1 }
  ],
  "baseline": false
}
```

**Fields:**
- `timestamp` — when this validation ran
- `trigger` — what caused it (`"POST /api/holidays"`, `"solve-completed"`, or `"manual (POST /api/schedule/validation/run)"`)
- `score` / `hard` / `medium` / `soft` — same score shape as `/api/schedule/analysis`
- `perConstraint` — non-zero constraints only, with their match count and score contribution
- `newlyViolated` — constraints whose `matchCount` increased versus the previous snapshot (the "newly-illegal" diff); each entry carries its own `hard` (the constraint's hard-score contribution) so consumers can tell hard violations from medium/soft ones without re-joining against `perConstraint`. Always empty on a clean run — **and always empty on a baseline run** (see below), regardless of how many pre-existing violations the schedule already has.
- `baseline` — true when this run had no previous snapshot to diff against (fresh validation history, or the first real run after an app restart with an empty `ValidationSnapshot` table). A baseline run persists its snapshot normally (so the *next* run has something real to diff against) but never reports pre-existing matches as "newly" violated — there's nothing legitimate to compare them to yet. The frontend badge ignores baseline results for its red ("newly illegal") state.

`GET /latest` returns `204 No Content` if no validation has run yet (fresh DB with no persisted `ValidationSnapshot` rows). The latest 20 snapshots are persisted to the `ValidationSnapshot` table so the diff survives an app restart.

**Frontend**: a header badge polls `GET /latest` every ~15s — hidden when `hard >= 0`, amber when hard violations exist but none are new, red (with the newly-illegal count) when `newlyViolated` contains a hard constraint. Clicking it opens a panel listing per-constraint match counts with newly-violated rows highlighted.

## Request/Response Bodies

### POST /api/courses

```json
{
  "subject": "Matematica",
  "teacherId": 1,
  "studentGroupId": 2,
  "totalHours": 66,
  "isLab": false,
  "labBlockSize": 0,
  "requiredRoomTags": ["informatica"],
  "color": "#3b82f6"
}
```

### PUT /api/courses/batch

Body is a JSON array of the same shape as `POST /api/courses`, plus `id`. Unlike the
single-row `PUT /api/courses/{id}`, a `null` `teacherId`/`studentGroupId` here actually
unassigns the teacher/group (the grid editor's dropdowns offer an explicit blank option).
Returns the updated `Course` list. All rows are saved in one transaction.

```json
[
  { "id": 5, "subject": "Matematica", "teacherId": 1, "studentGroupId": 2,
    "totalHours": 66, "isLab": false, "labBlockSize": 0,
    "requiredRoomTags": ["informatica"], "color": "#3b82f6" }
]
```

### POST /api/lab-rotations

```json
{
  "teacherId": 1,
  "studentGroupId": 2,
  "dayOfWeek": "THURSDAY",
  "slotStart": 0,
  "blockSize": 6,
  "periodStart": "2025-10-06",
  "periodEnd": "2025-12-22",
  "roomTag": "acconciatura"
}
```

### POST /api/stage-patterns

```json
{
  "studentGroupId": 1,
  "startDate": "2025-11-01",
  "endDate": "2026-04-30",
  "dayOfWeekMask": ["MONDAY", "WEDNESDAY"]
}
```

### POST /api/teacher-availability

```json
{
  "teacherId": 1,
  "startDateTime": "2025-10-06T08:00:00",
  "endDateTime": "2025-10-06T14:00:00",
  "type": "VACATION"
}
```

### PUT /api/lessons/{id}/assign

```json
{
  "timeslotId": 123,
  "roomId": 5
}
```

### POST /api/schedule/reschedule

```json
{
  "teacherId": 1,
  "date": "2025-10-15"
}
```

### GET /api/schedule/pre-assign/status Response

**Success response:**
```json
{
  "message": "Pre-assignment completed",
  "status": "COMPLETE",
  "currentPhase": null,
  "candidatesTried": 47,
  "error": null,
  "failureAnalysis": null
}
```

**Error response (with detailed analysis):**
```json
{
  "message": "ERROR",
  "status": "ERROR",
  "currentPhase": "1-ACCONCIATURA",
  "candidatesTried": 6,
  "error": "Phase 1 (Acconciatura) failed",
  "failureAnalysis": {
    "phase": "1-ACCONCIATURA",
    "reason": "CSP backtracking exhausted all possibilities",
    "conflictingTeacherNames": ["T28"],
    "conflictingGroupNames": ["G02", "G06", "G10", "G12"],
    "periodRange": "2025-09-10 - 2026-06-10",
    "suggestion": "Review teacher availability and room constraints. Consider adding more rotation days or adjusting period boundaries.",
    "conflictPatterns": [
      {
        "type": "NO_VALID_DAY",
        "description": "T28 with 4 groups has no valid day assignment",
        "occurrenceCount": 6,
        "examples": ["Valid days tried: [THURSDAY]"]
      },
      {
        "type": "INSUFFICIENT_CAPACITY",
        "description": "T28 needs 11 blocks (64 hours) but only 9 slots available on [THURSDAY]",
        "occurrenceCount": 2,
        "examples": [
          "Blocks needed (6h each): 11",
          "Total slots available: 9",
          "IMPOSSIBLE: Need 2 more slots"
        ]
      }
    ]
  }
}
```

**Status values**: `IDLE`, `RUNNING`, `COMPLETE`, `ERROR`

**currentPhase values**: `null`, `1-ACCONCIATURA`, `2-LABS-moda`, `2-LABS-meccanica`, `2-LABS-informatica`, `2-LABS-termoidraulica`, `3-PROFESSIONALIZZANTI`

**failureAnalysis fields**:
- `phase` — The phase that failed
- `reason` — High-level reason for failure
- `conflictingTeacherNames` — Human-readable teacher names involved
- `conflictingGroupNames` — Human-readable group names involved
- `periodRange` — Date range of the rotation period
- `suggestion` — Actionable suggestion for resolving the issue
- `conflictPatterns` — List of specific conflict patterns with examples

**Note**: POST /pre-assign automatically resets previous pre-assignment results before starting. Manual reset via POST /pre-assign/reset is usually not necessary.

### POST /api/schedule/pre-assign Response (202 Accepted)

```json
{
  "message": "Pre-assignment started",
  "status": "RUNNING",
  "currentPhase": "1-ACCONCIATURA",
  "candidatesTried": 0,
  "error": null,
  "failureAnalysis": null
}
```

### POST /api/schedule/pre-assign Response (409 Conflict)

```json
{
  "message": "Pre-assignment already running",
  "status": "RUNNING",
  "currentPhase": "2-LABS-moda",
  "candidatesTried": 15,
  "error": null,
  "failureAnalysis": null
```



## Constraints Inventory (TASK-528)

```
GET /api/constraints/summary    -> aggregate inventory of every INPUT constraint
```

One-shot aggregate for the 'Vincoli' page: categories (teacher availability,
recurring weekly rules, group calendar windows, daily quotas, stage patterns,
lab rotations, professionalizzanti days, lab assistants, teacher caps) each
with count, provenance, source and optional rows; EMPTY categories carry an
explicit `emptyMessage` (silence is information); known input-vs-golden
divergences are listed under `daConfermare`.
