# Spalmone Analysis Report

**Generated:** 2026-03-01 11:56:46
**Branch:** `feature/cartesian-block-entity`
**Source:** Human planner's master schedule (spalmone 2025-2026)
**API Port:** 8080

## Executive Summary

**Overall Score:** `-54531hard/-2460978medium/-174323soft`

| Category | Score | Status |
|----------|-------|--------|
| Hard     | -54,531 | Violations |
| Medium   | -2,460,978 | Violations |
| Soft     | -174,323 | Violations |

**Hard constraints:** 8 violated, 21 satisfied

## Data Summary

| Entity | Count |
|--------|-------|
| courses | 278 |
| holidays | 34 |
| lab_blocks | 0 |
| lessons | 10,119 |
| rooms | 19 |
| student_groups | 14 |
| teachers | 30 |
| timeslots | 1,137 |

**Pinned:** 10,119 | **Unpinned:** 0

### Lessons per Student Group

| Group | Lessons |
|-------|---------|
| G01 | 952 |
| G02 | 952 |
| G03 | 952 |
| G04 | 949 |
| G05 | 953 |
| G06 | 952 |
| G07 | 647 |
| G08 | 602 |
| G09 | 640 |
| G10 | 626 |
| G11 | 613 |
| G12 | 601 |
| G13 | 548 |
| G14 | 132 |

## Import Limitations

1. **No room data**: All lessons assigned to Aula 1 (placeholder). Room-based constraints (H2, H-M2, M3) are **false positives**.
2. **All courses non-lab**: `islab=false` for all. Lab constraints (H9, M4, S10) show 0 matches.
3. **No professionalizzanti flags**: All `professionalizzanti=false`. H4 violations include courses that would be exempt in our model.
4. **No doppio teacher data**: H-D1 constraints show 0 matches.

## Hard Constraint Violations

| ID | Constraint | Score | Matches | Analysis |
|----|-----------|-------|---------|----------|
| H2.1 | H2.1: Room conflict (L-L) | -51,289 | 51,289 | **FALSE POSITIVE** — all lessons in same dummy room |
| H15 | H15: Same-day same-subject must be consecutive | -3,120 | 1,401 | School schedules multi-hour blocks non-consecutively |
| H4.1 | H4.1: Stage blackout (L) | -86 | 86 | Partially real — some may be professionalizzanti |
| H16 | H16: Consecutive same-subject same teacher-room | -21 | 21 | Likely room artifact (dummy room) |
| H1.1 | H1.1: Teacher conflict (L-L) | -7 | 7 | Real teacher double-bookings |
| H6.1 | H6.1: School holiday (L) | -6 | 6 | Real — lessons on holidays |
| H13 | H13: Vertically compact | -1 | 1,953 |  |
| H7 | H7: Max teacher daily hours | -1 | 3,115 | Teacher exceeds daily limit |

### Hard Constraints with ZERO Violations

| ID | Matches | Interpretation |
|----|---------|----------------|
| H-D1.1 | 0 |  |
| H-D1.2 | 0 |  |
| H-D1.3 | 0 |  |
| H-D1.4 | 0 |  |
| H-M2.1 | 0 |  |
| H-M2.2 | 0 |  |
| H1.2 | 0 |  |
| H1.3 | 0 |  |
| H11 | 0 | Max daily hours respected |
| H14.1 | 0 | Start dates respected |
| H14.2 | 0 |  |
| H2.2 | 0 |  |
| H2.3 | 0 |  |
| H3.1 | 0 | **School never double-books students** |
| H3.2 | 0 |  |
| H3.3 | 0 |  |
| H4.2 | 0 |  |
| H5.1 | 0 | School respects teacher availability |
| H5.2 | 0 |  |
| H6.2 | 0 |  |
| H9 | 0 | N/A (no LabBlocks imported) |

## Medium Constraints

| ID | Constraint | Score | Matches |
|----|-----------|-------|---------|
| S4 | S4: No consecutive same-subject (high priority) | -2,461,000 | 2,461 |
| M12 | M12: Overflow beyond target end date (B) | 0 | 0 |
| M12 | M12: Overflow beyond target end date (L) | 0 | 0 |
| M17.2 | M17.2: Lab recurring pattern (multi-day) | 0 | 0 |
| M17 | M17: Lab recurring pattern | 0 | 0 |
| M3.1 | M3.1: Room capacity (L) | 0 | 0 |
| M3.2 | M3.2: Room capacity (B) | 0 | 0 |
| M4 | M4: Lab room-tag affinity | 0 | 0 |
| M5 | M5: Reward Informatica after stage | 22 | 86 |

## Soft Constraints

| ID | Constraint | Score | Matches |
|----|-----------|-------|---------|
| S1 | S1: Minimize teacher gaps | -189,120 | 3,115 |
| S2 | S2: Subject distribution | -9,218 | 4,609 |
| S8 | S8: Isolated student lesson | -420 | 21 |
| S3 | S3: Teacher preference | 0 | 0 |
| S11 | S11: Student group room affinity | 0 | 0 |
| S9 | S9: Dilute hours over year | 0 | 0 |
| S10 | S10: Discourage generic lessons in labs | 0 | 0 |
| S7 | S7: Reward consecutive student lessons | 24,435 | 8,145 |

## Detailed Violation Evidence

### H1: Teacher Double-Bookings

**7 conflicts found.**

| Teacher | DateTime | Lesson Count |
|---------|----------|-------------|
| T02 | 2025-12-12 12:00:00 | 2 |
| T02 | 2026-06-10 09:00:00 | 2 |
| T03 | 2025-10-09 08:00:00 | 2 |
| T03 | 2026-02-25 10:00:00 | 2 |
| T06 | 2026-02-20 09:00:00 | 2 |
| T06 | 2026-02-27 09:00:00 | 2 |
| T25 | 2025-11-13 10:00:00 | 2 |

These are genuine scheduling errors. All involve exactly 2 lessons at the same time — likely data entry mistakes or missed conflicts.

### H6: Lessons on School Holidays

**6 lessons on holidays.**

| Teacher | Subject | Group | Date | Holiday |
|---------|---------|-------|------|---------|
| T13 | Orientamento | G14 | 2025-12-25 | Vacanze di Natale |
| T13 | Orientamento | G14 | 2025-12-25 | Vacanze di Natale |
| T13 | Orientamento | G14 | 2025-12-25 | Vacanze di Natale |
| T13 | Orientamento | G14 | 2025-12-25 | Vacanze di Natale |
| T15 | sic | G14 | 2025-12-25 | Vacanze di Natale |
| T15 | sic | G14 | 2025-12-26 | Vacanze di Natale |


### H4: Stage Blackout Violations

**144 lessons on stage days (top subjects):**

| Group | Subject | Violations |
|-------|---------|-----------|
| G11 | Disegno | 20 |
| G12 | stT28 | 18 |
| G07 | Disegno | 15 |
| G11 | Progettazione termoidraulica | 12 |
| G11 | Informatica | 9 |
| G13 | Storia | 8 |
| G13 | Vendita e servizi | 7 |
| G13 | Inglese | 6 |
| G13 | Clienti | 6 |
| G07 | Gestione | 5 |
| G13 | Personal image | 5 |
| G13 | Scienze | 5 |
| G07 | Diritto | 4 |
| G07 | Economia | 4 |
| G07 | Scienze | 4 |
| G11 | Matematica | 4 |
| G11 | prep | 4 |
| G13 | Matematica | 4 |
| G13 | persvivi | 2 |
| G12 | Informatica | 2 |

**Note**: Some may be professionalizzanti courses that the importer didn't flag. True violations are limited to fundamental subjects (Italiano, Matematica, etc.).

### H7: Teacher Daily Hours Exceeding 6

**1 cases (top entries):**

| Teacher | Date | Hours |
|---------|------|-------|
| T03 | 2026-02-25 | 7 |


### H15/S4: Same-Day Same-Subject Patterns

| Subject | Same-Day Pairs |
|---------|---------------|
| Stile visivo | 816 |
| Officina meccanica | 710 |
| Servizi tecnici (T28) | 420 |
| Laboratorio termoidraulico | 391 |
| Laboratorio acconciatura | 301 |
| Laboratorio T03 | 264 |
| Confezione | 234 |
| Sart | 120 |
| Disegno | 119 |
| LARSA | 105 |
| Modello | 98 |
| stT28 | 90 |
| Informatica | 88 |
| Tecnologia tessile (T07) | 68 |
| Progettazione visiva | 67 |

These are mostly lab subjects scheduled as multi-hour blocks. The school intentionally schedules same subject multiple times per day. This validates our LabBlock entity approach.

## Interpretation

### Data-Import Artifacts (False Positives)

- **H2 (Room conflict)**: All lessons share Aula 1 placeholder — every timeslot with >1 lesson triggers a false H2 violation.
- **H-M2 (Room suitability)**: No lab flags set, so lab courses in Aula 1 are technically violations but not meaningful.
- **M3 (Room capacity)**: All lessons in same room, meaningless.
- **S10 (Generic in labs)**: No lab rooms used, 0 matches expected.

### Genuine Findings

- **H1 teacher conflicts**: Real double-bookings in the school's plan.
- **H3 = 0**: School never double-books student groups (validates constraint).
- **H6 holiday violations**: Likely data entry errors.
- **H4 stage blackout**: Mix of real violations and import artifacts (professionalizzanti not flagged).
- **H15/S4**: Lab subjects with multi-hour blocks confirm LabBlock design.

## Methodology

1. Spalmone CSV parsed by `scripts/spalmone-parser.py`
2. Imported by `scripts/spalmone-db-importer.py`
3. All lessons assigned `room_id = 1` (Aula 1) to initialize planning variables
4. Analysis via `GET /api/schedule/analysis?all=true` endpoint
5. Detailed violations via SQL queries on planner-pg database

---

*Generated by spalmone-validate.py*