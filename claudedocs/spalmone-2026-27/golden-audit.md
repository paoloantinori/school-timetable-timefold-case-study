# Golden-Solution Constraint Audit (TASK-347)

**Date:** 2026-07-17 · **Profile:** `dev` (fresh 2026/27 seed, then golden-imported) · **Method:** `POST /api/schedule/import-golden?force=true` followed by `GET /api/schedule/analysis?all=true` — **no solve was run**. This is a pure diagnostic read of `SchoolScheduleConstraintProvider` scored against the hand-made human timetable, imported as 100%-pinned `Lesson`s.

**Data-integrity note:** during this session another agent in a separate git worktree briefly shared the same Postgres container and ran a `TRUNCATE CASCADE` mid-audit (see "Environment note" below). After being notified, the full sequence was **redone from a verified-clean, exclusively-owned state**: `planner.sh delete dev` → `dev-start-and-wait.sh` → mechanical verification (1,376 timeslots / 268 courses / 26 teachers / 14 groups / 10,756 unassigned lessons, sole Quarkus process on port 8080) → `import-golden --force` (report identical to the one below, lesson count 12,489 = 10,496 + 1,993 pinned, 0 unpinned) → `analysis?all=true`. **Every hard/medium/soft/matches value below, and both drill-down spot-checks (H4's 694 `Stage`/`Stage` self-violations, H17's 22×`G08`/1×`G10` split), reproduced byte-for-byte identically between the original run and the clean redo** — the numbers in this report are the clean-redo numbers.

## Import report (cross-checked against the documented dry-run numbers — match)

```json
{
  "rows": 12979, "imported": 10496, "importedStage": 1993,
  "skippedEsame": 0, "skippedClosed": 428,
  "unmatchedNoTimeslot": 31, "unmatchedNoCourseRows": 31,
  "esameCoursesCreated": 6, "raCoursesCreated": 1,
  "deletedPinned": 0, "deletedUnpinned": 10756
}
```
All numbers match `claudedocs/rest-api.md`'s documented example exactly (10,496 real + 1,993 stage; 31+31 unmatched; 6 Esame courses; 1 RA course). The 9 `unmatchedNoCourseTriples` are all `subject=RA` rows for **G08**, taught by 9 different real teachers (T23 9, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2, T11 1, T03 1 = 31) — matching the design doc's documented "G08 RA pool" case exactly.

Pre-import baseline (verified): 1,376 timeslots, 268 courses, 26 teachers, 14 student groups, 10,756 unassigned `Lesson`s from normal course-based generation (cleared by `force=true`, `deletedUnpinned: 10756`).

**Superseded (TASK-371, 2026-07-17):** the golden-grid incarichi correction dropped 2 real courses (see `seed-csv-format.md`), so a fresh seed now produces 266 courses, not 268 - this dated audit's own numbers above are left as-recorded, not retroactively edited.

## Total score

```
-10247 hard / -251686 medium / -128523 soft
```

## Per-constraint results

| ID | Semantic name | Hard | Medium | Soft | Matches | Predicted | Verdict |
|----|---------------|-----:|-------:|-----:|--------:|-----------|---------|
| H1 | Teacher conflict | 0 | 0 | 0 | 0 | 0 | **CONFIRMED** |
| H2 | Room conflict | 0 | 0 | 0 | 0 | (not predicted) | 0 as expected (rooms all null) |
| H3 | Student group conflict | 0 | 0 | 0 | 0 | 0 | **CONFIRMED** |
| H4 | Stage blackout | −706 | 0 | 0 | 706 | (not predicted) | **DIVERGENCE — root-caused** |
| H4.3 | Professionalizzanti single-day during stage | 0 | 0 | 0 | 0 | — | n/a |
| H5 | Teacher unavailability | −72 | 0 | 0 | 72 | (not predicted) | **DIVERGENCE — root-caused** |
| H6 | School holiday | 0 | 0 | 0 | 0 | 0 | **CONFIRMED** |
| H7 | Max teacher daily hours | −23 | 0 | 0 | 3114* | T01 8h-days | **CONFIRMED** (+ reporting-layer bug found) |
| H11 | Max student group daily hours | 0 | 0 | 0 | 0 | 0 | **CONFIRMED** |
| H11-T28 | T28 allowed up to 6h/day | 0 | 0 | 0 | 0 | — | n/a (T28 is gone from 2026/27 data) |
| H14 | Student group start date | 0 | 0 | 0 | 0 | — | n/a |
| H15 | Same-day same-subject consecutive | −9420 | 0 | 0 | 98 | ~110–137 | **Roughly confirmed**, plausibly explained |
| H16a/H16b | Consecutive same-subject same teacher(/room) | 0 | 0 | 0 | 0 | — | n/a (0 in golden data) |
| H17 | Vertically compact | −26 | 0 | 0 | 23 | ~0 (2 afternoon exceptions) | **DIVERGENCE — root-caused** |
| H20 | Group calendar closed slot | 0 | 0 | 0 | 0 | 0 | **CONFIRMED** |
| H0 | Unassigned lesson | 0 | 0 | 0 | 0 | (see note) | **CONFIRMED — see null-room note below** |
| H-M2 | Room suitability | 0 | 0 | 0 | 0 | (see note) | Structurally untestable (rooms null) |
| M3 | Room capacity | 0 | 0 | 0 | 0 | — | Structurally untestable (rooms null) |
| M-LA | Lab assistant conflict | 0 | 0 | 0 | 0 | — | n/a |
| M5 | Reward Informatica after stage | 0 | +14 | 0 | 40 | informational | **CONFIRMED** (reward, not violation) |
| M12 | Overflow beyond target end date | 0 | 0 | 0 | 0 | informational | **CONFIRMED** |
| M18 | 5-hour groups should start from slot 0 | 0 | 0 | 0 | 0 | — | n/a |
| M20 | Daily quota fill (sparse-day) | 0 | 0 | 0 | 0 | ~18 | **DIVERGENCE — root-caused (structural, not a bug)** |
| M21 | Max 1h per subject per day | 0 | −251700 | 0 | 861 | (not predicted — new constraint) | **MAJOR UNPREDICTED DIVERGENCE — root-caused** |
| M22 | Calendar compactness | 0 | 0 | 0 | 0 | — | n/a |
| M23 | Block lesson pair togetherness | 0 | 0 | 0 | 0 | — | n/a |
| S1 | Minimize teacher gaps | 0 | 0 | −207000 | 1280 | expected (soft) | informational, as expected |
| S2 | Subject distribution | 0 | 0 | −23566 | 11783 | expected (soft) | informational |
| S3 | Teacher preference | 0 | 0 | −177 | 177 | expected (soft) | informational |
| S7 | Reward consecutive student lessons | 0 | 0 | +102580 | 10258 | expected (soft, reward) | informational |
| S8 | Isolated student lesson | 0 | 0 | −360 | 18 | expected (soft) | informational |
| S10/S11 | Lab affinity / room affinity | 0 | 0 | 0 | 0 | — | n/a (rooms null) |

\* H7's `matches` counts every (teacher, date) group-by tuple (3,114 total teacher-days in the schedule), not violations; the constraint sums `max(0, hours−6)` per tuple, giving −23 hard total. This is normal Timefold match-count semantics, not a bug.

## Divergence root-causes (every one traced to a concrete mechanism, not hand-waved)

### H4 — Stage blackout fires 706 times (not predicted at all)
**Direct cause, confirmed mechanically.** 694 of the 706 violations (98%) are `subject=Stage, teacher=Stage` — i.e. the synthetic pinned Stage lessons violating H4 **against their own group's StagePattern**. Traced to `BaseConstraintProvider.nonProfLessonStagePairs()` (`BaseConstraintProvider.java:74-83`): it joins every `Lesson` where `!course.professionalizzanti` against every `StagePattern` for the same group, with no exclusion for the synthetic Stage course itself. Since the Stage course is created with `professionalizzanti=false` (by design, per `claudedocs/design/stage-esame-ra-modeling-2026-07-16.md` section A) and its own pinned lessons trivially fall inside their own group's stage window/day-mask, **every pinned Stage lesson self-violates H4**. This was not caught before TASK-347 because no prior test data contained pinned Stage lessons. Root cause: `nonProfLessonStagePairs` needs to exclude lessons whose course *is* the group's synthetic Stage course (or, more generally, lessons taught by a `pseudo` teacher), mirroring the exclusion H7/S1 already apply. **Recommend a new task** (H4 pseudo-lesson exclusion) — out of scope for TASK-347's "no constraint changes."
The remaining 12/706 violations are real non-prof lessons (Comportamento Sociale, Scienze, Lingua Italiana, Matematica, Educazione Civica, Lingua Inglese, Storia, Economia e Diritto del lavoro) genuinely scheduled during a stage window — consistent with analysis.md finding #6 ("stage is not a pure blackout... groups return to school during their stage window"). Plausible genuine exceptions, not exhaustively verified per-row.

**TASK-366 fix:** `nonProfLessonStagePairs` now also excludes lessons whose teacher is `pseudo`, resolving the 694-violation self-blackout artifact class; expected residual is H4=12 (genuine rientro exceptions only) — **to re-verify on the next live audit run** (this task's own AC1 live verification is the orchestrator's step, not this fix's).

### H5 — Teacher unavailability fires 72 times (not predicted)
**Direct cause, confirmed mechanically**, split into two distinct, already-anticipated issues in `src/main/resources/seed/availability_rules.csv`:
- **T10 (20/72), all at Thursday 12:00.** The seed CSV's own comment already flags this: `T10,THURSDAY,0,7,VACATION,...,"Libero il Giovedi per LES/G14 - ambiguous morning-only vs 9-17 see domande Q11"`. The rule blocks all of Thursday (slots 0-7); the golden data has T10 teaching Thursday 12:00. This is the documented Q11 ambiguity (morning-only vs. all-day), not a new bug — already an open school question.
- **T11 (52/72), spread across Tuesday/Wednesday/Thursday/Friday at 08:00** (and a few other slots). `availability_rules.csv` blocks slot 0 (08:00-09:00) on **every** weekday for T11 ("9-14 sempre - unavailable before 9:00"), but `analysis.md` section 5 documents "T11: 9–14 always, **one day from 8:00**" — an exception day that never made it into the structured CSV. The golden data shows T11 teaching at 08:00 on 4 of 5 weekdays, not just one, so either the structuring dropped the exception or the school's single-exception-day description itself doesn't match reality. Either way this is a **data-structuring/clarification gap**, not a constraint-logic bug. Recommend routing to the school questions doc (a new T11-availability question analogous to Q11).

### H17 — Vertically compact fires 23 times (predicted ~0, with ~2 known afternoon exceptions)
**Direct cause, confirmed mechanically for all 23.** 22/23 are all `studentGroup=G08`, and every one traces to the **exact same 31 unmatched RA rows** dropped by the golden importer (the `unmatchedNoCourseTriples` above): e.g. `2026-09-29,09:00,G08,T06,RA` and `2026-09-30,10:00,G08,T06,RA` and `2027-04-27,11:00,G08,T23,RA` are present in `lessons.csv` sitting *between* two other real lessons, but were skipped at import (no Course exists for teacher×RA×G08), leaving an artificial hole in an otherwise-continuous day. This is an **import-defect side-effect** of the (documented, deliberate) RA-pool accounting design — not a constraint bug and not a genuine human-schedule gap. The 1 remaining violation (`G10`, T01, 2026-12-17) is the **already-predicted afternoon exception**: the raw CSV genuinely has 08:00-13:00 + 14:30-16:30 with the 13:00-14:00 slot empty (crossing lunch), exactly matching analysis.md's "2 exceptions only" finding — a real, expected divergence, not a bug. Net: H17 itself (and its lunch-aware `TimeslotSequence.isAdjacent`) is working correctly; the 22 spurious violations are entirely explained by the RA-drop import artifact.

**TASK-366 fix:** the 31 previously-dropped G08 "RA" rows are now imported under per-teacher "Recuperi ed Approfondimenti" micro-courses instead of being skipped, resolving the artifact class; expected residual is H17=1 (the T01 lunch-crossing exception only) — **to re-verify on the next live audit run** (this task's own AC1 live verification is the orchestrator's step, not this fix's).

### M20 — Daily quota fill (sparse-day) fires 0 times (predicted ~18)
**Direct cause, confirmed by code inspection.** `horizontallyCompact()` (`SchoolScheduleConstraintProvider.java:694`) explicitly filters `.filter(lesson -> !lesson.pinned)` — pinned lessons are exempt by design ("EXEMPTIONS: ... Pinned lessons (pre-assigned by human planners)"). Since **100% of the golden-imported lessons are pinned**, M20 is structurally incapable of firing on this dataset. This is not a defect: the golden audit simply cannot validate M20's sparse-day calibration, because the constraint was deliberately built to trust pre-assigned/pinned data. If sparse-day calibration needs verifying against the golden data, it would require a separate unpinned test path — out of scope here, flagged for awareness rather than an action item.

### H-M2 / M3 — Room constraints fire 0 times (runbook's explicit H0/null-room question)
**Answered directly.** `unassignedLesson()` (H0) only checks `lesson.timeslot == null` — it has no room clause at all, so pinned-but-null-room lessons pass it fine (H0 is purely "did the solver place it in time", by design). `roomSuitability()` (H-M2) requires `l.room != null` in its own filter before checking tag compatibility; a null room is treated as "not applicable," not "unsuitable." M3 (room capacity) follows the same pattern (0 matches, room-null golden data). **Conclusion: neither H0 nor any room-required constraint spuriously fires on the golden data's null rooms** — this is intentional defensive filtering, and it also means room-suitability/capacity calibration is untestable via this golden import (rooms are never populated in the hand-made grid, per design).

### M21 — Max 1h per subject per day fires 861 times, −251,700 medium (not predicted at all — the single largest score component)
**Direct cause, confirmed by code inspection and cross-referenced against analysis.md's own data.** `m21MaxOneHourPerSubjectPerDay()` (`SchoolScheduleConstraintProvider.java:538-551`) penalizes any (group, subject, date) with more than 1 non-lab hour, at weight 100/excess-hour. This constraint post-dates (or was simply never checked against) the golden-audit predictions in `analysis.md`, whose own section 4.5 block-size histogram documents non-lab multi-hour blocks as routine (2h: 680, 3h: 319, 4h: 83, 5h: 155, 6h: 62 — not all labs). The human school teaches many subjects in genuine multi-hour blocks (double/triple periods), which directly and structurally conflicts with M21's "1h/subject/day" assumption. 861 (group, subject, date) tuples exceed 1h, totalling 2,517 excess hours (−251,700 / 100). **This is a constraint-semantics gap, not an import defect**: M21's premise ("students need variety, one subject/day") does not match the school's actual documented practice. Recommend a **new recalibration task** (M21 needs either a per-subject block-size allowance, exemption list, or demotion/removal, analogous to how H15 and H7 are being recalibrated in TASK-349/348) — this is the single biggest medium-score contributor in the whole audit and was completely absent from the original prediction set.

## Not investigated to full depth (explicitly flagged, not hand-waved as confirmed)
- **H15 (98 matches vs. predicted ~110-137):** the violation distribution is broad across all 13 groups and many subjects (Scienze 24, Lingua Italiana 19, Matematica 11, ...), matching analysis.md's own worked example (T25/Scienze) — consistent with genuine organic human-schedule non-adjacency, not a single systemic bug like H17/H4. The measured count is lower than the predicted range; the most likely mechanism is that some of the ~490 rows dropped during import (428 closed-date rows + 31 no-timeslot + 31 no-course) happened to sit inside what would otherwise have been a non-adjacent H15 sequence, closing the gap as a side effect of the drop. This mechanism is plausible and partially supported (broad distribution rules out a single import-artifact concentration the way H17/H4 had), but was not exhaustively verified row-by-row given time budget — labelled **plausible, not fully confirmed**.
- **H7 per-lesson reporting anomaly:** `GET /api/schedule/analysis/lessons?constraint=H7` reports 111 violating lesson-entities (99 `Stage`, 12 `T01`), but the aggregate `/api/schedule/analysis` score (`-23 hard`) is consistent only with the 12 real T01 violations — the constraint's own `.filter(l -> !l.getTeacher().pseudo)` should exclude Stage entirely, and does for the aggregate score. The 99 "Stage" entries appear to be a **display/attribution bug in the per-lesson violation formatter** (not the constraint stream itself, since the aggregate score is correct), not investigated further given time budget. Flagged for a follow-up ticket; not part of TASK-347's constraint recalibration scope.

## Recalibration task updates
Measured numbers appended (not overwritten) to the Implementation Notes of TASK-348 (H7), TASK-349 (H15/H16), TASK-350 (H17/H11/M18) — see task files for the appended note text.

## Living guard (TASK-355)

This audit's diagnostic snapshot is now backed by an automated regression test:
`src/test/java/com/school/planner/bootstrap/GoldenSolutionRegressionTest.java`
(runs in the default `./scripts/core/test.sh`, no special profile). It seeds
the full real 2026/27 domain graph, runs the real `GoldenScheduleImportService`
against the real `lessons.csv`, and scores the result via
`ScheduleService.analyzeScore()` — automating this doc's own methodology so
constraint drift and importer/model regressions fail loudly (naming the
constraint and up to 10 offending lessons) instead of requiring a manual
re-audit.

It pins zero-tolerance on H1/H3/H6/H20/H11 and M20 (structurally pinned-exempt),
and the following calibrated residuals, **re-measured 2026-07-18 - the first
live re-verification of TASK-366's AC1**, not a copy of this doc's predictions:

| Constraint | Predicted (this doc / TASK-366) | Measured (TASK-355 suite) | Note |
|---|---|---|---|
| H4 | -12 hard (12 genuine rientro exceptions) | **-12 hard** | Matches the TASK-366 prediction exactly. |
| H15 | -9420 hard / 98 matches | **-9420 hard / 98 matches** | Matches this doc's own numbers exactly (unaffected by the H4/H17 fixes). |
| H7 | 0 hard | **0 hard** | Matches the TASK-348 prediction exactly. |
| H17 | -1 hard / 1 match (T01 only) | **-3 hard / 2 matches** | **DIVERGES.** T01/G10/2026-12-17 is the predicted case; a second, previously unaccounted-for match (Stage/G08/2027-04-01) was newly surfaced by this suite. RESOLVED 2026-07-18: both H17 matches are the two afternoon-contiguity exceptions from analysis.md §3 — T01/G10/2026-12-17 (13:00 hole, penalty 1) and G08/2027-04-01 (Stage 8-12 + RA 14:30-16:30, 12-14 double hole, penalty 2); TASK-366's -1/1 prediction was incomplete, not a regression.

M21/M12/S1/S7 are asserted presence/sign-only (recalibration targets for
TASK-349/350/365/372) - the suite will need updating (not just widening) once
those tasks land.

## Environment note (out of scope for constraint findings, but material to this run)
This audit was repeatedly disrupted by a **shared-infrastructure hazard**: `planner.sh` hardcodes a single global Postgres container name (`planner-pg`) and fixed ports (5432/8080) with no per-worktree isolation, and `cmd_kill_app`'s `pgrep -f 'quarkus-run.jar'` pattern matches *any* process system-wide, not just the current worktree's. During this session a concurrent agent in `.claude/worktrees/agent-a77286a3465ca5b73` (a live, locked git worktree) was using the same container/port; profile-reset commands from either side visibly tore down and re-seeded the other's database several times (observed directly: a run's own `DataGenerator` log showed 268 courses committed, then the same process's next query saw 0 courses seconds later). A `planner.sh` bug was also found and fixed in the same session (see below) but is unrelated to this collision. **Recommendation: serialize any task that touches `planner.sh`/the dev Postgres container/port 8080 across concurrent agents, or give each worktree its own container name/ports.**

## planner.sh bug found and fixed (uncommitted, unrelated to constraints)
`cmd_prod()` (`planner.sh` line 293) had a malformed parameter expansion — `local profile="${1:?Usage: $0 prod [--fresh] <profile>` was missing its closing `}"` — which silently swallowed the next ~170 lines of the script (`cmd_run_prod`, `cmd_run_prod_internal`, `cmd_stop`, `cmd_stop_quiet`, `cmd_list`) into an unterminated string literal, so none of those functions were ever defined; `./planner.sh stop`/`list` failed with "command not found". Fixed with a single-line edit (added the missing `}"`), verified functions now register correctly. **Not committed**, per this task's "no commits" instruction — left as an uncommitted working-tree change for the user to review/commit separately.
