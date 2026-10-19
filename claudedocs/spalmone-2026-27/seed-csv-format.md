# Seed CSV format (2026/27 school year, TASK-343)

`src/main/resources/seed/*.csv` are the runtime seed files `DataGenerator`
loads at startup (via `SeedCsv.load`, `com.school.planner.util.SeedCsv`).
Each one is a byte-identical copy of its counterpart in
`claudedocs/spalmone-2026-27/data/`, which is the raw output of
`scripts/analyze/spalmone2027-extract.py` run against the school's xlsx
("Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx"). See `analysis.md` in
this directory for the full extraction/validation report.

## Sync rule

`src/main/resources/seed/<name>.csv` must always be byte-identical to
`claudedocs/spalmone-2026-27/data/<name>.csv`. This is enforced by
`DataGeneratorSeedTest#seedCsvsAreByteIdenticalToClaudedocsCounterparts`
(skipped gracefully if the `claudedocs` directory is absent, e.g. a partial
CI checkout). If you need to update seed data, re-run the extractor script
and copy its output into both locations — never hand-edit only one side.

## Parser

`SeedCsv.load(resourceName, expectedColumns)` (`com.school.planner.util.SeedCsv`)
is a fail-loud, no-quoting CSV loader:
- Missing file, empty file, or a row with the wrong column count all throw
  `RuntimeException` with file/line context.
- A line containing a double-quote character also throws — the parser uses
  `line.split(",", -1)`, which has no CSV-quoting support, so a quoted field
  containing a literal comma would otherwise silently misalign columns.
- There is no silent skip-and-continue: these files are hand-verified
  extracts, and any shape mismatch indicates a real bug (bad copy, wrong
  extractor version).

## Schemas

### teachers.csv
| column | type | notes |
|---|---|---|
| `short` | string | display name; matches `incarichi.csv`'s `teacher` column exactly (see `DataGenerator.findTeacherByExactName`) |
| `fullname` | string | full name, display-only |
| `email` | string | school email, display-only |
| `maxDailyHours` | int, optional | per-teacher override of `H7`'s daily-hours cap; blank = default (`Teacher.DEFAULT_MAX_DAILY_HOURS` = 6). **Not** extractor-derived — hand-added (TASK-348) to model T01's real 8h/day (full-day lab crossing lunch, `analysis.md` section 4.2); only her row is non-blank (`8`). |
| `maxEarlyStartDaysPerWeek` | int, optional | per-teacher cap (M24) on distinct slot-0 (08:00) days/week; blank = uncapped. **Not** extractor-derived — hand-added (TASK-368) for T11's choice-day clause ("9-14 sempre e un giorno alle 8.00", `vincoli-in-parole.md`); only his row is non-blank (`1`). |
| `requiredFreeDaysPerWeek` | int, optional | per-teacher requirement (M25) that at least this many Mon-Fri days/week have zero lessons for the teacher; blank = unrequired. **Not** extractor-derived — hand-added (TASK-368) for T05's choice-day clause ("un giorno a scelta deve essere lasciato senza ore", `vincoli-in-parole.md`); only his row is non-blank (`2` — see `Teacher.requiredFreeDaysPerWeek`'s javadoc for why 2, not the docx's literal 1: his Friday is already a hard full-day `RecurringAvailabilityRule` block, so counting Mon-Fri with a requirement of 1 would always be trivially satisfied by Friday alone and never actually constrain Mon-Thu). |

All three hand-curated columns are preserved (never re-derived, never
blanked) by `scripts/analyze/spalmone2027-extract.py`'s short-name lookup
against the existing `claudedocs/spalmone-2026-27/data/teachers.csv` on
every re-run (TASK-366/348 code review D3, extended TASK-368). The script
prints a stdout warning if a teacher that had a non-blank override in the
existing file is missing from the freshly extracted `Docenti` sheet (the
override would otherwise be silently lost). If the extractor is ever
re-run against a fresh xlsx and a warning fires, re-apply the override(s)
by hand under the teacher's new/correct short name.

26 rows (26 teachers).

### groups.csv
| column | type | notes |
|---|---|---|
| `group` | string | e.g. `G03`, `G14`; matches `incarichi.csv`'s `group` column exactly |
| `course_code` | string | school course code, display-only |
| `enrolled` | int | enrollment count |

14 rows (14 student groups, including G14).

### rooms.csv
| column | type | notes |
|---|---|---|
| `room` | string | room name; matched against `DataGenerator.ROOM_TAG_MAP` for tag assignment |
| `capacity` | int | seats; one room (R18) is seeded with capacity 0 and imported as-is (R17 was corrected from 0 to 22 on 2026-07-29, TASK-456 ratification; only R18 remains cap 0) |

18 rows (18 rooms).

### incarichi.csv
| column | type | notes |
|---|---|---|
| `teacher` | string | must exactly match a `teachers.csv` short name, OR be one of `DataGenerator.PSEUDO_COURSE_TEACHERS` (`Stage`, `Esame`, `RA`, `Totale ore corso`) |
| `subject` | string | subject name; normalized via `com.school.planner.util.SubjectNormalization` for known case-duplicate spellings |
| `group` | string | must exactly match a `groups.csv` group name |
| `hours` | decimal | total course hours; rows sharing the same (teacher, subject, group) key are merged (summed) by `DataGenerator.aggregateCourseHours` |

295->303 rows: TASK-592 cure (11/09) replaced the 1 RA row with 9 real per-teacher rows (the golden's own split, 41h for G08) -> 28 pseudo-teacher rows (skipped: 8 Stage + 6 Esame + 14 Totale ore corso) + 275 real courses. All 275
real-course keys are unique on the current seed (verified in
`DataGeneratorSeedTest`); the merge in `aggregateCourseHours` exists to catch
future duplicates loudly (WARN log) rather than silently double-counting or
silently creating duplicate `Course` rows.

**Golden-grid correction (TASK-371, referente 17/07 Q3, "Orario in righe wins,
Incarichi stale"):** for the FC DUALE mismatches flagged in `analysis.md`
section 6, "Incarichi in riga" (this file) was confirmed stale against
"Orario in righe" (`lessons.csv`, the hand-made golden grid) - the school had
edited the grid but never propagated the change back to Incarichi. 14
real-teacher (teacher, subject, group) rows were corrected to the golden
scheduled-hours count (derived mechanically, not hand-tuned, by
`scripts/analyze/derive-golden-incarichi-corrections.py`), 2 real rows were
dropped because the golden grid schedules 0h for them (Q3) - `T13,
Orientamento, G12` (10h) and `T15, Controllo numerico, G04` (30h,
golden count 0) - and the stale `RA, Recuperi ed Approfondimenti,
G04, 50.0` pseudo-pool row was dropped because it duplicates T15's
real 50h course for the same (subject, group) - confirmed a duplicate, not an
extra pool, by Q7 combined with Q3's "Incarichi is stale" answer (see
`claudedocs/design/stage-esame-ra-modeling-2026-07-16.md` section C). The 2
FC `RA` pool row (40h) is unaffected - it is a genuine multi-teacher pool
per Q9, kept as-is. 14 corrected + 2 dropped real rows + 1 dropped pseudo row
= 17 total changed/removed rows, matching the commit's own count. Re-run the
derivation script any time `lessons.csv` or `incarichi.csv` changes to
re-verify zero remaining mismatches.

**Per-group residual after correction (assigned `Totale ore corso` vs sum of
real courses + Stage/Esame/RA-pool pseudo rows; not forced to reconcile,
honest real-world residuals):**

**Metric used in this table:** "Real+pool sum" is computed entirely from
`incarichi.csv` rows (real courses plus any Stage/Esame/RA pseudo-pool row
*that exists in incarichi.csv for that group*) - it never looks at
`lessons.csv`'s actual golden schedule.

**Update (TASK-434, 2026-07-30):** G04 now has an incarichi Stage row
(`Stage,Stage,G04,240.0`), so its Real+pool sum rises from 750 to 990 and it
reconciles (residual 0). Before TASK-434 it had no Stage row and showed +240
(the DUALE exception, design doc section A): the design doc's Q-STAGE-2
(`claudedocs/design/stage-esame-ra-modeling-2026-07-16.md`) reported -16 for the
same group because it added the 224h of Stage actually *scheduled in the golden
grid* (`lessons.csv`) on top of the 750 real-course hours - same root cause,
different metric (incarichi-only vs. incarichi-plus-actually-scheduled-stage),
now moot for G04 since the Stage row exists.

| Group | Totale | Real+pool sum | Residual | Why |
|---|---|---|---|---|
| G04 | 990 | 990 (750 real + 240 Stage) | 0 | TASK-434 added the G04 Stage row (240h), resolving the prior +240 (it had no Stage row before, the DUALE exception). The golden schedules 224h of Stage for G04. |
| G12 | 1000 | 990 | +10 | The dropped T13/Orientamento row (10h assigned, 0h ever scheduled per Q3) - expected, not a bug. |
| G14 | 120 | 115 | +5 | T14's Orientamento/G14 row corrected 20h -> 15h to match the golden count; G14 has no Stage/Esame/RA pseudo rows to absorb the difference. |
| all other groups | matches | matches | 0 | Unaffected by the correction; reconcile exactly as before. |

### lessons.csv (TASK-346)
| column | type | notes |
|---|---|---|
| `date` | ISO date (`YYYY-MM-DD`) | school day; range 2026-09-14..2027-06-15 (the last 5 days, 2027-06-11..15, are FC exam-week dates beyond `TimeslotSequence.SCHOOL_YEAR_END` = 2027-06-10 - no Timeslot exists for them) |
| `day` | string | Italian weekday name (`Lunedì`..`Venerdì`), display-only, not parsed |
| `slot` | string | `"HH:MM HH:MM"` (start and end time, space-separated); one of the 8 values matching `TimeslotSequence.SLOT_START_TIMES` |
| `closed` | `True`/`False` | per-group-slot closure (e.g. first-year groups have no afternoons) - `True` rows are skipped, not just global school-holiday closures. Parsed strictly: any value other than exactly `True` or `False` makes the golden import throw with file+line context (no case-insensitive fallback) |
| `group` | string | matches `groups.csv`'s `group` column, OR one of the Stage groups not in a StagePattern window (G04) |
| `teacher` | string | matches `teachers.csv`'s `short` column, OR one of the pseudo-teachers `Stage`/`Esame` (see below) |
| `subject` | string | subject name; normalized via `com.school.planner.util.SubjectNormalization` (the single source of truth - `DataGenerator` uses it too) |

12,979 data rows (the full "Orario in righe" sheet, `wc -l` minus header). Not
every row becomes a `Lesson`: see `GoldenScheduleImportService` (TASK-346) for
the exact categorization -
- `closed="True"` or blank `teacher` -> skipped (428 rows on the current seed)
- `teacher="Esame"` -> skipped (0 rows on the current seed - Esame is assigned
  in `incarichi.csv` but never scheduled in the golden grid, design doc
  `claudedocs/design/stage-esame-ra-modeling-2026-07-16.md` section B; the
  import still creates a per-group Esame **accounting Course** with zero
  Lessons from incarichi's Esame rows, plus one RA accounting Course for
  G08 only - section C)
- no matching `Timeslot` for `(date, slot-start-time)` -> unmatched (31 rows:
  the 2027-06-11..15 exam-week dates)
- `teacher="Stage"` -> imported as a pinned `Lesson` under a synthetic
  per-group Stage course (1,993 rows)
- otherwise: `(teacher, normalized subject, group)` matched against the real
  `Course` table -> imported as a pinned `Lesson` (10,496 rows), or counted as
  unmatched-no-course if no `Course` exists for that triple (31 rows across 9
  distinct triples - all `subject="RA"` for `G08`, whose real courses are
  keyed `Recuperi ed Approfondimenti` in `incarichi.csv`, not the grid's
  abbreviated `RA`)

### calendario_moda.csv / calendario_fc.csv / calendario_class_days.csv (TASK-364)

Extracted by `scripts/analyze/calendario2027-extract.py` from `Copy of Calendario
scolastico 2026_2027.xlsx` (the school's pre-set lab/stage day-structure input -
see `calendario-scolastico-format.md`, the binding reading key). Consumed by
`CalendarioImportService`.

#### calendario_moda.csv

From the `MODA TUTTI` sheet's 1/2/G09 blocks (PE/MU/BO columns only - G14's
`TOT|Au|La|St` block on the same sheet carries no teacher initials and is
already covered per-date by `calendario_class_days.csv`, so it is not
re-extracted here).

| column | type | notes |
|---|---|---|
| `date` | ISO date | |
| `group` | string | `G01`, `G05`, or `G09` |
| `initial` | string | `PE`, `MU`, or `BO` - raw teacher initial, NOT resolved to a name here. `CalendarioImportService.INITIAL_TO_TEACHER` owns the (configurable) initial-to-teacher mapping: PE=T17, MU=T16, BO=T07 |
| `hours` | int | lab hours that teacher teaches that group that day |
| `shift` | string | `first` (was a negative cell value in the sheet - slots 0-2, 08-11) or `second` (positive - slots 3-5, 11-14); see `CalendarioImportService` for the full shift-to-slot rule including hours != 3 |

312 rows (nonzero PE/MU/BO teacher-day cells across 1/2/G09; 313 before the 2027-06-09 divergence-#8 exclusion, see below). Yearly totals
verified against the sheet's own row-3 reference cells: G01 = PE 143 + MU 180
+ BO 24 (347h); T07's 72h total (24h x 3 groups) confirms her sole subject
for MODA groups is `Tecnologia e creatività` (`incarichi.csv`, the only
T07/MODA rows).

**313 vs 225, two different metrics (TASK-364 code review K4):** this 313 is
every nonzero teacher-day cell extracted for the full 2026/27 school year -
the full row count of this CSV. `calendario-scolastico-format.md` section 4
separately reports 225 (185 exact + 32 mirror swaps + 8 true divergences) -
that is the smaller subset of teacher-day predictions this file's original
cross-validation pass actually compared against the golden spalmone
timetable, not a different (and disagreeing) count of the same rows. Both
numbers are correct; they just measure different things.

#### calendario_fc.csv

From the `DEF FC TUTTI` sheet's G04 / G08 / G13 blocks (the sheet's two
extra unnamed 4-column blocks are unused and skipped). **Report-only** (TASK-364
scoping decision): teacher resolution for FC/G13 lab hours is ambiguous from
this sheet alone (no teacher-initial columns, unlike MODA TUTTI), so these rows
are never pinned as Lessons - they feed a coherence cross-check against
`GroupCalendarRule`/`StagePattern` instead.

| column | type | notes |
|---|---|---|
| `group` | string | `G04`, `G08`, or `G13` |
| `date` | ISO date | every calendar date in the school year, including weekends (0 hours) |
| `aula_hours` | int | Aula (classroom) hours that date |
| `lab_hours` | int | Laboratorio (officina) hours that date |
| `stage_hours` | int | Stage hours that date |

909 rows. Yearly per-group totals verified exactly against the sheet's own
reference cells: G04 990 = Au 745 + La 245; G08 990 = Au 584 + La 106 +
St 300; G13 990 = Au 485 + La 210 + St 295.

#### calendario_class_days.csv

From the 14 authoritative per-class sheets (`G03`, `G07`, `G11`
[the source sheet name has a literal leading space, stripped here], `G02`,
`G06`, `G10`, `G12`, `G01`, `G05`, `G09`, `G13`, `G04`,
`G08`, `G14`). Discarded variants (`MODA NON USARE`, `FC duale piano B`,
`FC TUTTI` (non-DEF), `Copia di ...`, `def. G13`, `G14 2`, `G12NEW`,
`Base`, bare `FA`, `Calendario`/`Calendario base`/`Istruzioni`) are excluded.
**Report-only** (TASK-364 scoping decision, same rationale as calendario_fc.csv):
these rows cross-check `GroupCalendarRule` open-slot counts and `StagePattern`
windows per (group, date); they are never pinned as Lessons.

| column | type | notes |
|---|---|---|
| `group` | string | matches `groups.csv`'s `group` column |
| `date` | ISO date | only real numeric-hours teaching days are emitted - weekends (blank) and holidays (text name, e.g. `Natale`) are skipped, since the sheet's modality column is pre-filled with a stale default even on those non-teaching rows |
| `hours` | int | teaching hours that day |
| `modality` | string | `A` = Aula, `S` = Stage, `L` = Laboratorio |

2,277 rows. Per-group yearly hour totals cluster around 990h (the school's
standard yearly load), with small real-world variances (e.g. G03 969h,
G13 978h) - not extraction bugs, since several other groups hit exactly 990h.

#### Cross-check findings (real 2026/27 seed, TASK-364 dry run)

Running `POST /api/schedule/import-calendario?dryRun=true` against a fully
seeded 2026/27 profile surfaces two real, pre-existing coherence gaps between
the calendario input and the currently-modeled `GroupCalendarRule`/`StagePattern`
data (not bugs in the importer - this cross-check is exactly what it's for):

- **G14 attends Monday-Wednesday per the calendario** (171 teaching days
  spread across all 5 weekdays, 5-6h/day), but the seeded `GroupCalendarRule`
  pattern (derived from the golden spalmone timetable, see
  `DataGenerator.GROUP_CALENDAR_PATTERNS`) models G14 as fully closed
  Mon-Wed and open only Thu (5 slots)/Fri (3 slots). This produces 171
  "exceeds calendar capacity" `classDaysDiscrepancies` for G14 alone - the
  calendario (the school's evolved INPUT process) and the golden spalmone (the
  OUTPUT after final adjustments) disagree on which days G14 actually meets,
  exactly the kind of drift `calendario-scolastico-format.md` section 4-5
  anticipates. Needs a human decision on which source is authoritative going
  forward.
- **G08 and G13 have stage hours in calendario_fc.csv with no matching
  `StagePattern`** (38 + 37 = 75 `FCDiscrepancies`, all "stage indicator
  outside StagePattern window") - `DataGenerator` currently only seeds
  `StagePattern` rows for G07, G11, and G12 (3 rows total); FC/G13
  groups have real stage windows in the calendario input that aren't modeled
  as `StagePattern` entities yet.

Neither finding blocks the MODA pin import (which is unaffected - these two
CSVs are report-only, see above); both are flagged here as the reconciliation
work the school's process evolution needs, per TASK-364's WHY.

### availability_rules.csv (TASK-345)

Hand-transcribed from the "Vincoli Docenti" sheet (see `analysis.md` section 5
and `availability-not-yet-modeled.md` in this directory for the clauses that
could NOT be transcribed this way). Loaded by
`DataGenerator.generateAvailabilityRules()` into `RecurringAvailabilityRule`
rows, which `AvailabilityExpansionService.expandAll()` then expands into the
dated `TeacherAvailability` rows H5/S3 actually read — see that service's
Javadoc for the expansion mechanism (delete-and-regenerate via
`TeacherAvailability.sourceRuleId`) and the lunch-spanning-range safety
argument.

| column | type | notes |
|---|---|---|
| `teacher` | string | must exactly match a `teachers.csv` short name |
| `dayOfWeek` | string | Java `DayOfWeek` enum name (`MONDAY`..`FRIDAY`) |
| `minSlot` | int | inclusive first slot index (0-7, see `TimeslotSequence.SLOT_START_TIMES`) |
| `maxSlot` | int | inclusive last slot index (0-7); a full day is `0`-`7` |
| `type` | string | `VACATION` (hard-unavailable, H5) or `PREFERENCE` (soft-avoid, S3) |
| `effectiveFrom` | date or empty | ISO date; empty means "always" (no lower bound) |
| `effectiveTo` | date or empty | ISO date; empty means "always" (no upper bound); a single-date row uses the same value for both |
| `note` | string | free-text provenance; must not contain a comma (the parser has no CSV quoting) |

42 rows (T12 7, T11 5, T05 5, T04 5, T24 4, T07 4,
T01 4, T19 3, T22 2, T17 1, T16 1, T10 1).
See `availability-not-yet-modeled.md` for the TASK-374 (2026-07-17)
golden-derivation rationale behind the T10/T12/T16 rows, and the
TASK-368 (2026-07-17 docx) revision behind the T04/T05/T11 rows
(T04/T05 extended to 14:00 Mon-Thu, T05's Tue-afternoon asymmetry
dropped, T11's daily slot-0 block removed in favor of the weekly M24 cap).
Grew from the 23-row TASK-368 baseline (when T11 went from 10 rows to 5) as
additional teachers' weekly availability was transcribed (T01, T07,
T19, T22, T17 added; T24 expanded).

**Exclusion (2026-07-18):** the `2027-06-09, G05, PE, 1, second` row (school divergence #8 per calendario-scolastico-format.md §4) is excluded from `calendario_moda.csv`: it self-conflicts with the same day's `MU, 5, first` row and the golden resolved that day differently — per the school's own principle (Q3: "Orario in righe wins"). 313 → 312 rows.
