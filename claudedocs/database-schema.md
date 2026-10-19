# PostgreSQL Database Schema

Generated from the actual database schema on 2026-03-14, then **reconciled against the live
schema on 2026-08-05**: Entity Summary corrected, the deleted `LabBlock` removed (TASK-228/267),
four missing entities added, and per-table columns completed so every live column of every
documented table is now listed. For exact DDL query `information_schema`
via `./scripts/db/db-query.sh` or see the `domain/` entity classes. Note: `SchoolSchedule` is
the in-memory `@PlanningSolution` (built from the persisted entities), so it has no table and
its runtime fields (score, `stagedStage`, `stagedAttempt`, ...) are not persisted columns.

## Overview

The database uses **PostgreSQL** with the following characteristics:
- **Schema**: All tables in the `public` schema
- **Primary Keys**: All tables use `id BIGINT` with IDENTITY generation
- **Naming Conventions**:
  - Tables: lowercase JPA entity names (e.g., `Lesson` → `lesson`)
  - Columns: camelCase Java field names preserved
  - Foreign Keys: `{fieldName}_id` convention
  - ElementCollection tables: `{entity}_{collection}` (e.g., `course_requiredroomtags`)

---

## Entity Summary

| Entity | Table | Type | Description |
|--------|-------|------|-------------|
| Lesson | `lesson` | @PlanningEntity | Single-hour class instances (labs are pinned Lesson rows, `islabhour`) |
| Course | `course` | Reference | Subject offering (teacher + group + hours) |
| Timeslot | `timeslot` | Reference | Date + time slot for scheduling |
| Room | `room` | Reference | Classroom with optional tags |
| Teacher | `teacher` | Reference | Instructor |
| StudentGroup | `studentgroup` | Reference | Class cohort |
| StagePattern | `stagepattern` | Pattern | Internship periods |
| LabRotation | `labrotation` | Pattern | Lab rotation patterns for pre-assignment |
| TeacherAvailability | `teacheravailability` | Pattern | Teacher unavailability/preferences |
| RecurringAvailabilityRule | `recurringavailabilityrule` | Pattern | Weekly teacher availability template (dated effective window) |
| SchoolHoliday | `schoolholiday` | Reference | Non-school days |
| LabAssistant | `labassistant` | Reference | Lab assistant assignments |
| GroupCalendarRule | `groupcalendarrule` | Pattern | Per-group, per-weekday open-slot calendar (TASK-342) |
| DailyActivityQuota | `dailyactivityquota` | Fact | Per-(group, date) expected hours aula/lab/stage (TASK-385) |
| ProfessionalizzantiDay | `professionalizzantiday` | Pattern | Reserved professionalizzanti weekday per group (TASK-370) |
| ValidationSnapshot | `validationsnapshot` | Runtime | Persisted score/validation snapshot for the legality watchdog (TASK-354) |

---

## Tables (16)

### Core Planning Entities

#### lesson

Single-hour class instances. Primary planning entity assigned to timeslots and rooms by the solver.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| course_id | BIGINT | YES | course.id | Associated course |
| timeslot_id | BIGINT | YES | timeslot.id | Planning variable (slot assignment) |
| room_id | BIGINT | YES | room.id | Planning variable (room assignment) |
| pinned | BOOLEAN | NO | — | Solver cannot move if true |
| **islabhour** | BOOLEAN | NO | — | Part of a lab block |
| **labhourindex** | INTEGER | NO | — | Index within lab block (0-based) |
| **labtotalhours** | INTEGER | NO | — | Total hours in parent lab block |

| **promotedpin** | BOOLEAN | NO | - | Staged-solve promoted pin (TASK-419): authoritative pin promoted from the construction template |
| **sessionindex** | INTEGER | NO | - | Session index (ordered within the day) |

**Indexes**: `lesson_pkey` (id)

**Foreign Keys**:
- `fktchhed520n3y73e7p9audtyj`: course_id → course(id)
- `fksq105pp66nwq8nv9c5009bm91`: room_id → room(id)
- `fkpvgn83efo4cnlehgoerxgqh4`: timeslot_id → timeslot(id)

---

### Reference Entities

#### course

Subject offering linking teacher, student group, and hours.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| subject | VARCHAR | YES | — | Subject name (e.g., "Matematica") |
| teacher_id | BIGINT | YES | teacher.id | Primary teacher |
| studentgroup_id | BIGINT | YES | studentgroup.id | Student group |
| totalhours | INTEGER | NO | — | Total hours for this course |
| islab | BOOLEAN | NO | — | Lab course flag |
| labblocksize | INTEGER | NO | — | Block size for labs (0 if not lab) |
| professionalizzanti | BOOLEAN | NO | — | Stage-related course flag |
| color | VARCHAR(7) | YES | — | Display color (hex, e.g., "#3b82f6") |
| **expected_lab_days_per_week** | INTEGER | YES | — | Target lab frequency (NEW) |

| **expectedunscheduledhours** | INTEGER | YES | - | Off-grid hours (closures/Saturdays) accounted out of the course total (TASK-381) |

**ElementCollection**: `course_requiredroomtags` (course_id, requiredroomtags)

**Indexes**: `course_pkey` (id)

**Foreign Keys**:
- `fk90kt2haj2fb3guifaq4rgy8m4`: teacher_id → teacher(id)
- `fkg5vmn9oa5xxf3bmnyv0rq80t3`: studentgroup_id → studentgroup(id)

---

#### timeslot

Date + time slot for scheduling. Pre-generated for entire school year.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| startdatetime | TIMESTAMP | YES | — | Start of the slot |
| enddatetime | TIMESTAMP | YES | — | End of the slot |

**Computed in JPA** (not persisted):
- `date` — LocalDate from startDateTime
- `dayOfWeek` — DayOfWeek from startDateTime

**Indexes**: `timeslot_pkey` (id)

---

#### room

Classroom with optional lab tags.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| name | VARCHAR | YES | — | Room name (e.g., "Lab Info 1") |
| capacity | INTEGER | NO | — | Seating capacity |

**ElementCollection**: `room_tags` (room_id, tags) — Lab type tags (e.g., "acconciatura", "informatica")

**Indexes**: `room_pkey` (id)

---

#### teacher

Instructor with display properties.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| name | VARCHAR | YES | — | Full name |
| nickname | VARCHAR(20) | YES | — | Optional display name |
| color | VARCHAR(7) | YES | — | Display color (hex) |
| **pattern** | VARCHAR | YES | — | Scheduling pattern attribute (NEW) |

| **external** | BOOLEAN | NO | - | External teacher (e.g., T28, Thursday-only) |
| **pseudo** | BOOLEAN | NO | - | Pseudo-teacher (e.g., "Stage"), not a real person |
| **maxdailyhours** | INTEGER | YES | - | Max teaching hours per day (default 6, T01 8). Drives H7 |
| **maxearlystartdaysperweek** | INTEGER | YES | - | Max slot-0 days per week (T11=1). Drives M24 (TASK-368) |
| **requiredfreedaysperweek** | INTEGER | YES | - | Required free days Mon-Fri (T05=2). Drives M25 (TASK-368) |

**Indexes**: `teacher_pkey` (id)

---

#### studentgroup

Class cohort with optional schedule constraints.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| name | VARCHAR | YES | — | Group name (e.g., "G01") |
| size | INTEGER | NO | — | Number of students |
| maxhoursperday | INTEGER | YES | — | Max daily hours (null = unlimited) |
| startdate | DATE | YES | — | Start date for late-joining groups |

**Transient in JPA** (not persisted):
- `targetEndDate` — Computed before solving for horizontal compactness

| **allow_professionalizzanti_during_stage** | BOOLEAN | YES | - | If true, professionalizzanti may be scheduled on stage days (H4 exemption) |

**Indexes**: `studentgroup_pkey` (id)

---

#### schoolholiday

Non-school days (Christmas, Easter, national holidays).

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| date | DATE | YES | — | Holiday date |
| description | VARCHAR | YES | — | Holiday name |

**Indexes**: `schoolholiday_pkey` (id)

---

#### labassistant (NEW)

Lab assistant assignments linking teachers to room tags.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| teacher_id | BIGINT | YES | teacher.id | Lab assistant teacher |
| roomtag | VARCHAR | YES | — | Room tag they assist in |

**Indexes**: `labassistant_pkey` (id)

**Foreign Keys**:
- `fkg2kbu0e7tchqj2os3bf1tsrpt`: teacher_id → teacher(id)

---

### Pattern/Configuration Entities

#### stagepattern

Internship periods for student groups (alternanza/stage).

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| studentgroup_id | BIGINT | YES | studentgroup.id | Associated group |
| startdate | DATE | YES | — | Period start |
| enddate | DATE | YES | — | Period end |

**ElementCollection**: `stagepattern_dayofweekmask` (stagepattern_id, dayofweekmask) — Days of week (MONDAY-FRIDAY integers)

**Indexes**: `stagepattern_pkey` (id)

**Foreign Keys**:
- `fkh8y43nw2i5gqlrkiw92jxtay1`: studentgroup_id → studentgroup(id)
- `fkfedood5mkbs9j9o2diwtv0ouf`: (from stagepattern_dayofweekmask) → stagepattern(id)

---

#### labrotation

Lab rotation patterns for pre-assignment. Defines weekly patterns like "T28 teaches G01 on Thursdays in acconciatura lab for trimester 1".

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| teacher_id | BIGINT | YES | teacher.id | Teacher assigned |
| studentgroup_id | BIGINT | YES | studentgroup.id | Student group |
| dayofweek | SMALLINT | YES | — | Day of week (DayOfWeek ordinal) |
| slotstart | INTEGER | NO | — | 0-based start slot (0 = 08:00) |
| blocksize | INTEGER | NO | — | Hours per session |
| periodstart | DATE | YES | — | Rotation period start |
| periodend | DATE | YES | — | Rotation period end |
| roomtag | VARCHAR | YES | — | Room tag to match |
| subjectprefix | VARCHAR | YES | — | Optional subject filter |

**Indexes**: `labrotation_pkey` (id)

**Foreign Keys**:
- `fk2lwifrwu8n8lv7twn9ayr4abp`: teacher_id → teacher(id)
- `fkbgpv4gw6xwoxo16711d85ce78`: studentgroup_id → studentgroup(id)

---

#### groupcalendarrule

Per-group, per-weekday slot-level calendar opening pattern (TASK-342): which slot indices
[minSlot, maxSlot] (inclusive) are open for a student group on a given weekday, with
optional date-scoped overrides. A fully closed weekday is represented as maxSlot < minSlot
(e.g. minSlot=0, maxSlot=-1) rather than a separate boolean flag. No matching rule for a
(group, weekday) means fully open (backward compatible with the pre-TASK-342 model).

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| studentgroup_id | BIGINT | YES | studentgroup.id | Associated group |
| dayofweek | SMALLINT | YES | — | Day of week (DayOfWeek ordinal) |
| minslot | INTEGER | NO | — | Inclusive first open slot index (0-7) |
| maxslot | INTEGER | NO | — | Inclusive last open slot index (0-7); maxslot < minslot = fully closed |
| effectivefrom | DATE | YES | — | Null = no lower bound (always) |
| effectiveto | DATE | YES | — | Null = no upper bound (always) |

**Indexes**: `groupcalendarrule_pkey` (id)

**Foreign Keys**:
- studentgroup_id → studentgroup(id)

---

#### teacheravailability

Teacher unavailability and preference periods.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | — | Primary key (IDENTITY) |
| teacher_id | BIGINT | YES | teacher.id | Associated teacher |
| startdatetime | TIMESTAMP | YES | — | Period start |
| enddatetime | TIMESTAMP | YES | — | Period end |
| type | VARCHAR | YES | — | SICK, VACATION, or PREFERENCE |

| **sourceruleid** | BIGINT | YES | - | Provenance: the RecurringAvailabilityRule this was expanded from (null if manual) |

**Indexes**: `teacheravailability_pkey` (id)

**Foreign Keys**:
- `fkhmdk7ldy113vos0wx276uilsa`: teacher_id → teacher(id)

---

### ElementCollection Tables

These are JPA @ElementCollection tables for multi-valued attributes:

| Table | Parent | Column | Description |
|-------|--------|--------|-------------|
| `course_requiredroomtags` | course | requiredroomtags | Lab room tag requirements |
| `room_tags` | room | tags | Lab type tags |
| `stagepattern_dayofweekmask` | stagepattern | dayofweekmask | Days of week integers |

---

### Additional Entities (added 2026-08-05 reconciliation)

The following four entity tables were missing from the original document and are verified
against the live schema. `SchoolSchedule` is intentionally absent: it is the in-memory
`@PlanningSolution` (no table).

#### dailyactivityquota

Per-(student group, date) expected-hours facts (classroom / lab / stage). TASK-385 universal
expected-hours; the `omcquota` flag distinguishes the FC calendar rows from the class-day rows.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | (none) | Primary key (IDENTITY) |
| studentgroup_id | BIGINT | YES | studentgroup.id | Owning group |
| date | DATE | NO | (none) | School date |
| aulahours | INTEGER | NO | (none) | Expected classroom hours |
| labhours | INTEGER | NO | (none) | Expected lab hours |
| stagehours | INTEGER | NO | (none) | Expected internship hours |
| omcquota | BOOLEAN | NO | (none) | True for FC-calendar rows (TASK-385) |

#### professionalizzantiday

Reserved professionalizzanti weekday (or date) per student group (TASK-370, feeds M29).

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | (none) | Primary key (IDENTITY) |
| studentgroup_id | BIGINT | YES | studentgroup.id | Owning group |
| date | DATE | YES | (none) | Specific date (optional) |
| dayofweek | SMALLINT | NO | (none) | Weekday |
| description | VARCHAR | YES | (none) | Free-text note |

#### recurringavailabilityrule

Weekly teacher availability template with a dated effective window (minslot/maxslot per weekday).

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | (none) | Primary key (IDENTITY) |
| teacher_id | BIGINT | YES | teacher.id | Owning teacher |
| dayofweek | SMALLINT | NO | (none) | Weekday |
| minslot | INTEGER | NO | (none) | First open slot |
| maxslot | INTEGER | NO | (none) | Last open slot |
| type | VARCHAR | YES | (none) | Rule type |
| effectivefrom | DATE | YES | (none) | Effective window start |
| effectiveto | DATE | YES | (none) | Effective window end |
| note | VARCHAR | YES | (none) | Free-text note |

#### validationsnapshot

Persisted score/validation snapshot used by the legality watchdog (TASK-354) to diff the current
schedule against the previous run.

| Column | Type | Nullable | FK → | Description |
|--------|------|----------|------|-------------|
| id | BIGINT | NO | (none) | Primary key (IDENTITY) |
| timestamp | TIMESTAMPTZ | NO | (none) | When the snapshot was taken |
| trigger | VARCHAR | YES | (none) | What triggered the snapshot |
| scorestring | VARCHAR | YES | (none) | Score as text |
| hardscore | BIGINT | NO | (none) | Hard score |
| mediumscore | BIGINT | NO | (none) | Medium score |
| softscore | BIGINT | NO | (none) | Soft score |
| countsjson | TEXT | YES | (none) | Per-constraint violation counts (JSON) |

---

## Enumerations

### AvailabilityType

| Value | Description |
|-------|-------------|
| SICK | Teacher is sick |
| VACATION | Teacher is on vacation/unavailable |
| PREFERENCE | Teacher prefers this time |

### PreAssignmentStatus

| Value | Description |
|-------|-------------|
| IDLE | No pre-assignment running |
| ACCONCIATURA | Running acconciatura rotation |
| LABS | Running lab block assignment |
| PROFESSIONALIZZANTI | Running professionalizzanti assignment |
| COMPLETE | Pre-assignment finished |
| ERROR | Pre-assignment failed |

---

## Entity Relationship Diagram

```
                    ┌─────────────┐
                    │   Teacher   │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┬─────────────────┐
         │                 │                 │                 │
         ▼                 ▼                 ▼                 ▼
┌─────────────────┐ ┌─────────────┐ ┌──────────────┐ ┌───────────────┐
│TeacherAvail.    │ │   Course    │ │   LabRotation│ │  LabAssistant │
└─────────────────┘ └──────┬──────┘ └───────┬──────┘ └───────────────┘
                           │                 │
         ┌─────────────────┼─────────────────┤
         │                 │                 │
         ▼                 ▼                 ▼
┌─────────────┐    ┌─────────────┐
│StudentGroup │    │   Lesson    │
└──────┬──────┘    └──────┬──────┘
       │                  │
       │                  │
       ▼                  ▼
┌─────────────┐    ┌─────────────┐
│StagePattern │    │   Timeslot  │
└─────────────┘    └──────┬──────┘
                         │
                    ┌────┴────┐
                    │         │
                    ▼         ▼
              ┌─────────┐ ┌─────────┐
              │  Room   │ │ Holiday │
              └─────────┘ └─────────┘
                                         │
                                    ┌────┴────┐
                                    │         │
                                    ▼         ▼
                              ┌─────────┐ ┌─────────┐
                              │  Room   │ │ Holiday │
                              └─────────┘ └─────────┘
```

---

## Changes from Previous Documentation

| Table | Change | Description |
|-------|--------|-------------|
| `course` | Added | `expected_lab_days_per_week` column |
| `course` | Removed | `doppioteacher_id` (was incorrectly documented) |
| `lesson` | Added | `islabhour`, `labhourindex`, `labtotalhours` columns |
| `teacher` | Added | `pattern` column |
| `labassistant` | NEW | Entire table added for lab assistants |
| `stagepattern_dayofweekmask` | Fixed | Table name was incorrectly `stagepattern_dayofweek_mask` |
| `labblock` | REMOVED (2026-08-05 reconciliation) | Table no longer exists; `LabBlock` was deleted by TASK-228/267. Labs are now pinned `Lesson` rows. Removed from the Entity Summary, table section and ERD. |
| `dailyactivityquota`, `professionalizzantiday`, `recurringavailabilityrule`, `validationsnapshot` | ADDED (2026-08-05 reconciliation) | Were missing from the Entity Summary; now listed (table sections added for each). Verified against the live schema. |

---

## Common Queries

See `claudedocs/psql-cheatsheet.md` for example queries using this schema.
