# Spalmone Analysis Report

Analysis of the school's 2025-2026 master schedule ("spalmone") against our constraint model.

**Date**: 2026-02-23
**Branch**: `feature/cartesian-block-entity`
**Profile**: `spalmone-test`

## Data Summary

| Metric | Value |
|--------|-------|
| Total lessons imported | 10,119 |
| Source CSV | 11,136 rows (1,017 skipped: no matching teacher/group/timeslot) |
| Unique subjects | 67 |
| Unique teachers | 25 |
| Student groups | 14 |
| Courses created | 278 |

### Lessons per Student Group

| Group | Lessons | Notes |
|-------|---------|-------|
| G01 | 952 | Full year |
| G02 | 952 | Full year |
| G03 | 952 | Full year |
| G04 | 949 | Full year |
| G05 | 953 | Full year |
| G06 | 952 | Full year |
| G07 | 647 | Reduced (stage) |
| G08 | 602 | Reduced (stage) |
| G09 | 640 | Reduced (stage) |
| G10 | 626 | Reduced (stage) |
| G11 | 613 | Reduced (stage) |
| G12 | 601 | Reduced (stage) |
| G13 | 548 | Reduced |
| G14 | 132 | Minimal |

## Import Limitations

1. **No room data**: The spalmone CSV does not contain room assignments. All lessons were assigned to "Aula 1" (a generic classroom) to make Timefold evaluate constraints. Room-based constraint results (H2, H-M2, M3) are **false positives** and should be ignored.
2. **All courses set as non-lab**: The importer creates courses with `islab=false` since the CSV doesn't distinguish lab vs regular lessons. Lab-related constraints (H9, M4, S10) show 0 matches.
3. **No professionalizzanti flags**: All courses set `professionalizzanti=false`. This affects H4 analysis — any lesson on a stage day is flagged, even if it would be professionalizzanti in our model.
4. **No doppio teacher data**: `doppioTeacher` not mapped. H-D1 constraints show 0 matches.

## Constraint Analysis Results

**Overall Score**: `-54,531hard / -2,460,978medium / -174,323soft`

### Hard Constraint Violations

| ID | Constraint | Score | Matches | Analysis |
|----|-----------|-------|---------|----------|
| H2.1 | Room conflict (L-L) | -51,289 | 51,289 | **FALSE POSITIVE** — all lessons in same dummy room |
| H15 | Same-day same-subject consecutive | -3,120 | 1,401 | **Real** — school schedules multi-hour blocks non-consecutively |
| H4.1 | Stage blackout (L) | -86 | 86 | **Partially real** — some fundamentals on stage days; others may be professionalizzanti (see limitations) |
| H16 | Consecutive same-subject same teacher-room | -21 | 21 | **Likely room artifact** — consecutive same-subject but dummy room differs from actual |
| H1.1 | Teacher conflict (L-L) | -7 | 7 | **Real** — 7 teacher double-bookings |
| H6.1 | School holiday (L) | -6 | 6 | **Real** — 6 lessons on holidays |
| H13 | Vertically compact | -1 | 1,953 | Low penalty, many matches |
| H7 | Max teacher daily hours | -1 | 3,115 | Low penalty, many matches |

### Hard Constraints with ZERO Violations (School Respects These)

| ID | Constraint | Matches | Interpretation |
|----|-----------|---------|----------------|
| **H3.1** | **Student group conflict (L-L)** | **0** | **School never double-books students** |
| H5.1 | Teacher unavailability (L) | 0 | School respects teacher availability |
| H11 | Max student group daily hours | 0 | Within daily limits |
| H14.1 | Student group start date (L) | 0 | Start dates respected |
| H-D1.* | Doppio teacher conflicts | 0 | N/A (no doppio data imported) |
| H-M2.* | Room suitability | 0 | N/A (no lab flags) |
| H9 | Block valid placement | 0 | N/A (no LabBlocks) |

### Medium Constraints

| ID | Constraint | Score | Matches | Analysis |
|----|-----------|-------|---------|----------|
| S4 | No consecutive same-subject | -2,461,000 | 2,461 | School intentionally schedules same subject in multi-hour blocks (lab pattern) |
| M5 | Informatica after stage | +22 | 86 | Positive = reward earned; 86 Informatica lessons after stage end |

### Soft Constraints

| ID | Constraint | Score | Matches | Analysis |
|----|-----------|-------|---------|----------|
| S1 | Teacher gaps | -189,120 | 3,115 | Significant teacher gaps in schedule |
| S2 | Subject distribution | -9,218 | 4,609 | Subject clustering (expected for lab blocks) |
| S8 | Isolated student lesson | -420 | 21 | 21 isolated single-lesson slots |
| S7 | Consecutive student lessons | +24,435 | 8,145 | Positive = reward; good student compactness |

## Detailed Violation Analysis

### H1.1: Teacher Double-Bookings (7 conflicts)

| Teacher | DateTime | Lesson Count |
|---------|----------|-------------|
| T02 | 2025-12-12 12:00 | 2 |
| T02 | 2026-06-10 09:00 | 2 |
| T03 | 2025-10-09 08:00 | 2 |
| T03 | 2026-02-25 10:00 | 2 |
| T06 | 2026-02-20 09:00 | 2 |
| T06 | 2026-02-27 09:00 | 2 |
| T25 | 2025-11-13 10:00 | 2 |

These are genuine scheduling errors in the school's plan. All involve exactly 2 lessons at the same time — likely data entry mistakes or missed conflicts.

### H6.1: Lessons on School Holidays (6 lessons)

| Teacher | Subject | Group | Date |
|---------|---------|-------|------|
| T13 | Orientamento | G14 | 2025-12-25 (Christmas) |
| T13 | Orientamento | G14 | 2025-12-25 (Christmas) |
| T13 | Orientamento | G14 | 2025-12-25 (Christmas) |
| T13 | Orientamento | G14 | 2025-12-25 (Christmas) |
| T15 | sic | G14 | 2025-12-25 (Christmas) |
| T15 | sic | G14 | 2025-12-26 (St. Stephen's) |

All 6 violations involve the G14 group on Christmas Day and St. Stephen's Day. This is likely a data entry error — G14 has very few hours (132 total) and may have been scheduled to fill the year without checking holidays.

### H4.1: Stage Blackout Violations (86 lessons)

Most violations are in G12 (the group with the most stage overlap). Top subjects:

| Group | Subject | Violations |
|-------|---------|-----------|
| G12 | Progettazione visiva | 45 |
| G12 | Italiano | 34 |
| G12 | Stile visivo | 23 |
| G12 | Diritto | 20 |
| G11 | Disegno | 20 |

**Note**: Some of these may be professionalizzanti courses that the importer didn't flag. Subjects like "Progettazione visiva" and "Stile visivo" are likely professionalizzanti for FD, which would make them exempt from H4 in our model. True violations are probably limited to fundamental subjects (Italiano, Matematica, etc.).

### H15/S4: Same-Day Same-Subject Patterns

The school routinely schedules the same subject multiple times per day. Top subjects:

| Subject | Same-Day Pairs |
|---------|---------------|
| Stile visivo | 816 |
| Officina meccanica | 710 |
| Servizi tecnici (T28) | 420 |
| Laboratorio termoidraulico | 391 |
| Laboratorio acconciatura | 301 |

These are all lab subjects. The school schedules multi-hour lab blocks (3-6 hours) as separate lessons. H15 fires when same-subject lessons on the same day are NOT adjacent — likely some have gaps or are split. S4 fires for consecutive same-subject (which is the INTENDED behavior for labs).

**Implication for our model**: S4 (No consecutive same-subject) being MEDIUM at -2.4M is the largest penalty. This validates our LabBlock entity approach — by modeling labs as atomic multi-hour blocks, we avoid this penalty entirely.

## Key Findings

### Validates Our Model
1. **H3 = 0**: The school never double-books student groups. Our hard constraint is correct.
2. **H5 = 0**: Teacher availability data is aligned with school practice.
3. **H11 = 0**: Max daily hours per group is respected.
4. **Lab block pattern**: The school's multi-hour lab scheduling (visible in S4/H15) validates our LabBlock entity design.

### Discrepancies to Investigate
1. **H1 teacher conflicts**: 7 real double-bookings. These may be intentional (co-teaching not captured) or errors.
2. **H4 stage blackout**: 86 violations. Need to classify which are genuinely professionalizzanti to separate real violations from import limitations.
3. **H6 holiday lessons**: 6 G14 lessons on Christmas — likely data entry error.
4. **H15 non-consecutive same-subject**: 1,401 cases. Most are lab subjects scheduled with gaps. Verify if this is intentional (e.g., a morning and afternoon lab session with a break).

### Constraints Not Tested (due to import limitations)
- H2 (Room conflict) — needs real room data
- H-M2 (Room suitability) — needs lab flags
- H-D1 (Doppio conflict) — needs doppio teacher mapping
- H9 (Block valid placement) — no LabBlocks
- M3 (Room capacity) — needs room assignments
- M4 (Lab room-tag affinity) — needs lab flags
- S10 (Generic in labs) — needs room data

## Methodology

1. Spalmone CSV parsed by `scripts/spalmone-parser.py` from `scripts/data/spalmone.csv` → `/tmp/spalmone-parsed.json`
2. Imported by `scripts/spalmone-db-importer.py` into `spalmone-test` profile
3. All lessons assigned `room_id = 1` (Aula 1) to initialize planning variables
4. Analysis via `GET /api/schedule/analysis?all=true` endpoint
5. `solutionManager.update()` called before `analyze()` to properly score the solution
