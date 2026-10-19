# Modeling Stage, Esame and RA hours (2026/27) — Decision Record

**Date:** 2026-07-16 · **Task:** TASK-344 · **Depends on:** TASK-343 (importer), consumed by TASK-346 (golden import), TASK-353 (reconciliation), TASK-352 (school questions).

## Problem

In the real 2026/27 data (analysis.md §1, §4.6, §6) the per-group yearly total (990h, 1000 for G12, 120 G14) is a single "Totale ore corso" that **already includes** Stage, Esame and RA. Three pseudo-teachers carry those hours in `incarichi.csv` (`Stage`, `Esame`, `RA`), and Stage is additionally scheduled in the grid as 8h/day pseudo-lessons. Our current model does the opposite: `StudentGroupCompletionService.getTotalCurriculumHours` = Σ`Course.totalHours` (excludes stage/esame/RA), and stage days are a pure H4 blackout excluded from the total. If we import the golden solution and reconcile assigned-vs-scheduled as-is, FC groups show permanent mismatches that are actually correct-by-design. This doc fixes the accounting model.

### Ground-truth numbers (from `lessons.csv`, open/non-closed rows; `incarichi.csv`)

| Group | Assigned (Totale) | incarichi Stage / Esame / RA | Scheduled total | of which Stage |
|---|---|---|---|---|
| G08 | 990 | 300 / 24 / 40 | 943 | 276 |
| G13 | 990 | 295 / 12 / — | 978 | 295 |
| G04 (DUALE) | 990 | — / — / — | 974 | 224 |

G08 reconciles cleanly: assigned 990 = real courses 626 + Stage 300 + Esame 24 + RA 40; scheduled 943 = (real+RA) 667 + Stage 276 + Esame 0. The −47 delta is **Stage −24** (300 assigned, 276 placed) plus **Esame −24** (assigned, never placed) — exactly what the school's own `Classi` sheet shows. G04 does **not** reconcile like the others (see Q-STAGE-2).

**Update 2026-07-17 (TASK-373, referente Q8):** that Stage −24 for G08 is not "24 hours dropped" — it is 24 real stage rows that happen to fall on the 2027-03-25/26/30 Easter closure ("lo stage può essere fatto anche nei giorni di chiusura", confirmed by the school). `scripts/analyze/derive-golden-stage-on-closure.py` measures this per group directly from `lessons.csv` (closed="True" AND teacher="Stage"), independent of `incarichi.csv`:

**Update 2026-07-18 (code review H2/H4):** the script is now horizon-aware too — some "open" (closed=False) Stage rows are dated past `TimeslotSequence.SCHOOL_YEAR_END` (2027-06-10) and have no matching Timeslot, exactly like a closure-day row (see the "Why no closure Timeslots" paragraph below, which now applies equally to both). The table splits "open" into within-horizon (`within`, actually pinned as Lessons) and `beyond` (no Timeslot — folded into `expectedUnscheduledHours` alongside `closed`), and adds a `course totalHours` column computed with the exact same formula `GoldenScheduleImportService.reconcileStageCourseHours` uses (`within + closed + beyond`), so the script and the Java code can never silently drift apart without the script's own `formulaDrift` check catching it:

```
========================================================================================================================
Stage-on-closure / beyond-horizon rows per group (lessons.csv)
========================================================================================================================
group         within  beyond  closed  grid total  course totalHours  incarichi  match?
--------------------------------------------------------------------------------------
G04            208      16      16         240                240          -     n/a
G07           295       0       0         295                295        295      OK
G08            276       0      24         300                300        300      OK
G09           292       0       8         300                300        300      OK
G10            228       0      72         300                300        300      OK
G11           287       0       8         295                295        295      OK
G12           112       0     183         295                295        295      OK
G13             295       0       0         295                295        295      OK
--------------------------------------------------------------------------------------
TOTAL           1993      16     311        2320               2320

course totalHours (Java formula: within-horizon matches + closure + beyond-horizon) equals the raw grid
total for every group, by construction.
Every group with an incarichi.csv Stage row has incarichi hours == grid total (within-horizon +
beyond-horizon + closed) - the Stage course's totalHours can be computed straight from the grid without
relying on incarichi.csv at all (see reconcileStageCourseHours).

Groups with NO incarichi.csv Stage row (DUALE-style fallback, totalHours computed purely from the grid): ['G04']

Total stage-on-closure rows across all groups: 311
Total stage-beyond-horizon rows across all groups: 16
```

Every group's incarichi.csv Stage value already equals its full grid total (within-horizon + beyond-horizon + closed) — that was true by coincidence of the source data, not by any code guarantee, so it was fragile: the pre-TASK-373 import used the incarichi value directly and would have silently continued reporting whatever number incarichi carried even if a future re-extraction broke that equality. TASK-373 removes the fragility by computing Stage `totalHours` straight from the grid (see the updated Mechanism/Hour-accounting paragraphs below), and keeps incarichi only as a non-blocking drift check (previously `LOG.warnf`-only; code review H6 also surfaces it as `GoldenImportReport.stageDriftWarnings`, API-visible instead of log-only, if the two ever disagree again).

---

## A. STAGE — decision: **hybrid (option c)**

Keep the existing **H4 / StagePattern blackout as the solver-facing mechanism** (cheap: it drives the VRP value-range filtering in `Lesson.getValueRange`, `calculateIdealEndDate`, and forbids real non-prof lessons on stage days), **and additionally materialize stage as pinned pseudo-lessons** that occupy the calendar and count in accounting/reconciliation.

**Why not the alternatives.** Pure (a) pinned-only would drop the cheap VRP pre-filter and the ideal-end-date logic that already depend on StagePattern. Pure (b) blackout-with-totals-adjusted-downward is what we have today, and it is exactly what breaks reconciliation: the school counts stage in 990, so subtracting it makes every stage group show a large "unexplained" negative delta and the timeline shows an empty stage window instead of "Stage". The hybrid keeps solver cost near zero (pseudo-lessons are pinned → no planning variable) while making the timeline and the numbers match the school.

**Mechanism.** A synthetic per-group **Stage course** (pseudo-teacher `Stage`, subject `Stage`, `professionalizzanti=false`, no room tag, `totalHours` = grid total: open-day Stage rows (pinned as Lessons) **plus** closure-day Stage rows (TASK-373 `stageOnClosure`, never pinned — see "why not generate closure Timeslots" below), computed straight from `lessons.csv` and cross-checked (non-blocking `LOG.warnf`) against `incarichi.csv`'s Stage value). Its `Lesson`s are `pinned=true` on the open-day stage dates. Because they carry a real `studentGroup` (via the synthetic course), H3 structurally blocks real lessons from those slots even without H4 — so for groups the pinned lessons cover, H4 is belt-and-suspenders, retained only for the VRP/ideal-end machinery.

**dayMask is preserved.** H4 already reads `StagePattern.dayOfWeekMask`; G11's 4-days/week stage (Mon stays a school day → mask = Tue–Fri) works unchanged, and pseudo-lessons are generated only on `expandDates()` days, leaving Mondays open.

**DUALE exception (G04).** G04's 224 stage hours are spread across **all five weekdays, April–June, ~28 scattered days**, with **no stage window** in `Vincoli Classi` and **no incarichi Stage row**. This does not fit StagePattern's contiguous window + weekly mask. For G04, model stage **only** as pinned pseudo-lessons on the exact golden dates (a structural per-date blackout), and create **no** StagePattern / H4 for it. See Q-STAGE-1 / Q-STAGE-2.

**Update 2026-07-17 (TASK-373):** G04 also has 16 Stage rows on closure days (of its 240-row grid total: 224 open + 16 closed — see the table above). Because G04 has no incarichi Stage row to fall back on, its pre-TASK-373 `totalHours` was whatever got pinned (208 Lessons — 16 of the 224 "open" CSV rows fall on 2027-06-11/14, beyond the Timeslot horizon). TASK-373's initial `openMatches + stageOnClosure` formula raised that to 224 (208 + 16), folding in the closure hours but leaving the beyond-horizon 16h unaccounted for.

**Update 2026-07-18 (code review H4):** that beyond-horizon 16h is structurally unschedulable for the exact same reason a closure-day row is — no Timeslot exists for 2027-06-11..14 and none is generated for one (see "Why no closure Timeslots" below, which now covers both). `categorize()` gained a `stageBeyondHorizonByGroup` bucket (an open-day Stage row with no matching Timeslot, checked before the generic `unmatchedNoTimeslot` bucket so it isn't silently absorbed there — `unmatchedNoTimeslot` shrinks from 31 to 15 on the current seed, all G04's). `reconcileStageCourseHours`'s formula is now `openMatches + expectedUnscheduledHours` where `expectedUnscheduledHours = stageOnClosure + stageBeyondHorizon`, persisted onto a new `Course.expectedUnscheduledHours` field (generic name, reusable by any future accounting course with a structurally-expected shortfall). G04's Stage `totalHours` is now 240 (208 + 16 + 16) — the group's true full grid total, no longer silently 16h short of it — and its own delta (240 − 208 = 32) equals `expectedUnscheduledHours` exactly, same identity as before, just complete.

**Hour-accounting formula.** `assigned(group) = Σ incarichi rows including Stage/Esame/RA = Totale`. `scheduled(group) = Σ golden lessons including Stage pseudo-lessons`. The completion service's `totalCurriculumHours` must include the synthetic Stage course, and the ideal-end walk keeps computing on **non-stage** hours over non-stage days (stage sits in its own window). **G08:** total 990, scheduled 943 of which 276 stage → dashboard delta −47 = Stage −24 + Esame −24, flagged **expected**, not "Analizzare".

**Side effect noted (code review H4, not itself changed):** `StudentGroupCompletionService.getTotalCurriculumHours` sums `Course.totalHours` across every course for a group, including the Stage course — so G04's `totalHours` bump (224 → 240, see below) also raises that group's completion-service total by 16h (750 real + 240 stage = 990, exactly `Totale`, closing the −16 residual noted in Q-STAGE-2 below as a side effect rather than a deliberate fix). `StudentGroupCompletionService` itself is untouched — this is a consequence of the Course row it already reads, not a new code path — but is called out here since it changes what Q-STAGE-2 documents as an "accepted residual".

**Update 2026-07-17 (TASK-373):** the Stage course's own assigned-vs-scheduled delta (the `−24` above) is no longer an opaque "expected mismatch" — it now equals `stageOnClosure` **exactly**, by construction: `assigned` = `openMatches + stageOnClosure` (the grid total) and `scheduled` = `openMatches` (`Lesson.count` with a non-null timeslot — closure-day rows are never pinned, see below), so `delta = assigned − scheduled = stageOnClosure`. This fell out of `GoldenScheduleImportService.reconcileStageCourseHours`/`ReconciliationService.fetchScheduledCounts` as-is at the time; no `ReconciliationService` code change was made yet.

**Update 2026-07-18 (code review H1, supersedes the "no code change was needed" claim above):** the frontend's own 50%-of-assigned `isPseudoCheck` heuristic (`ReconciliationPage.tsx`) doesn't know a Stage row's delta is *entirely* expected — it flagged G12's Stage row (183 of 295h delta, 62%) as an amber "Check" even though the whole delta is closure hours, not a real shortfall. The frontend can't tell "fully expected" from "partially expected" apart without a number from the backend. `ReconciliationReport.Row` now carries `expectedUnscheduledHours` (from `Course.expectedUnscheduledHours`, 0 for ordinary courses); `isPseudoCheck` judges the **actionable** delta (`delta − expectedUnscheduledHours`) instead of the raw delta — zero for every Stage row by construction (the raw delta always equals `expectedUnscheduledHours` exactly, per the identity above), so no Stage row can ever render "Check" purely from its own closure/beyond-horizon shortfall; a row would only escalate if a *real* mismatch pushed the actionable delta away from zero.

**TASK-346 does:** create the synthetic Stage course + pseudo-teacher once (idempotent, `totalHours` = incarichi Stage, e.g. 300 for G08); for each `Stage` row in `lessons.csv` create a pinned `Lesson` under that course at the row's date/slot. Solver value ranges exclude these (pinned). ~2,000 extra pinned rows across 8 groups — negligible search-space impact, marginal constraint-stream cardinality.

**Update 2026-07-17 (TASK-373):** `totalHours` is now computed as `openMatches(group) + stageOnClosure(group)` straight from `lessons.csv` (categorize()'s `stageMatches`/`stageOnClosureByGroup` buckets), not read from `incarichi.csv` — see the Mechanism paragraph above and `GoldenScheduleImportService.reconcileStageCourseHours`'s javadoc. `GoldenImportReport` gains `stageOnClosure` (int, total) and `stageOnClosureByGroup` (`Map<String,Integer>`); `skippedClosed` shrinks by the same total (311 on the current seed: 428 → 117) since closure-day Stage rows are no longer lumped in with genuinely-skipped closed rows.

**Update 2026-07-18 (code review H4):** `totalHours` is now `openMatches(group) + expectedUnscheduledHours(group)`, where `expectedUnscheduledHours = stageOnClosure + stageBeyondHorizon` (both persisted, see the DUALE-exception update above). `GoldenImportReport` gains `stageBeyondHorizonByGroup`/`getStageBeyondHorizon()` alongside the existing `stageOnClosureByGroup`; `unmatchedNoTimeslot` shrinks by the same total (31 → 15 on the current seed, all G04's) since beyond-horizon Stage rows are no longer lumped in with genuinely-out-of-calendar rows for other subjects.

**Why no closure Timeslots (TASK-373 WHAT #4; extended to beyond-horizon rows, code review H4).** Closure-day Stage rows are never turned into pinned `Lesson`s, and no `Timeslot` is ever generated for a closure date. The same is true of open-day Stage rows dated beyond `TimeslotSequence.SCHOOL_YEAR_END` — no Timeslot exists there either, and none is generated for one. The solver never places anything on either kind of date — there is no planning variable that could point there, and creating ~300 extra closure Timeslots (or extending the calendar past its year end) purely so these rows could be pinned would mean every other constraint stream (H1–H19, M-series) has to special-case "is this Timeslot on a real school day" everywhere it currently assumes "every Timeslot is a school day", for zero solver-facing benefit (the hours are pinned/pseudo either way). Stage-on-closure and stage-beyond-horizon are pure **data/accounting facts** — the school ran stage on those days, and the synthetic Stage course's `totalHours` must reflect that — not a scheduling fact the solver needs to reason about. Keeping it accounting-only (counts in `GoldenImportReport`/the Stage course's `totalHours`/`expectedUnscheduledHours`) gets the correct numbers with zero changes to the Timeslot model, the constraint provider, or the solver's search space.

---

## B. ESAME — decision: **interim accounting-only stub; defer scheduling to Q6**

Esame hours (12–24h per graduating group) are **assigned but never scheduled** in the golden grid, and FC exams land **2027-06-11..15, beyond the 2027-06-10 year end** and beyond every timeslot and the M12 horizon.

**Why.** Pinned pseudo-lessons are impossible — there are no exam dates/slots in the grid, and the FC ones fall outside the calendar entirely. Extending the FC calendar to 06-15 via date-scoped `GroupCalendarRule` is premature: whether we schedule exams at all is the open school question **Q6** (Q-ESAME-1). Interim: represent Esame as a synthetic **Esame course** (`totalHours` from incarichi) with **zero scheduled lessons**, excluded from solver value ranges. This keeps 990 reconciling while the dashboard shows Esame as assigned-not-scheduled — matching the school's own sheet, which also leaves it unscheduled.

**Hour-accounting.** G08 Esame: assigned 24, scheduled 0 → dashboard delta −24, expected.

**TASK-346 does:** create the synthetic Esame course (`totalHours` from incarichi) and generate **no** lessons for it; log skipped. If Q6 answers "schedule exams," a new follow-up task adds the 06-11..15 FC calendar extension (date-scoped `GroupCalendarRule`) + pinned exam lessons.

---

## C. RA (Recuperi ed Approfondimenti) — decision: **per-teacher micro-courses for G08's unmatched rows (TASK-366, superseding the original "skip as a solver entity" call below); carry G08's incarichi pool as a separate reconciliation assigned-line**

RA is not uniform. For MODA/FA it is **already assigned to real teachers** in `incarichi.csv` (T02, T16, T17, T14 RA; T26 "Ra lab aperto") — those are ordinary courses, handled by TASK-343 like any other. **G04 (50h) and G08 (40h)** both carry an `RA` pseudo-teacher row in `incarichi.csv` — but they are not symmetric (see below).

**Why not the alternatives (superseded 2026-07-17, TASK-366).** This section originally rejected per-teacher micro-courses, reasoning they'd "over-commit to a split the school has not fixed (Q-RA-1)" and create "brittle 5–19h courses" — so G08's 31 unmatched RA rows were dropped at import instead. In practice, dropping them punched mid-day holes in G08's otherwise-continuous schedule, fabricating 22 spurious H17 (vertically-compact) violations (see `claudedocs/spalmone-2026-27/golden-audit.md`'s H17 root-cause). TASK-366 reverses this call: each of the 9 real teachers now gets its own "Recuperi ed Approfondimenti" micro-course (find-or-create, sized to exactly the rows it covers - `T23` 9h, `T15` 6h, `T10` 5h, `T05` 3h, `T06` 2h, `T14` 2h, `T21` 2h, `T11` 1h, `T03` 1h), and its Lessons are pinned on the golden dates - pending school answer Q9 on whether this per-teacher split is the intended long-term shape (the micro-courses carry a provenance comment citing Q9). The "brittle" hours don't disappear either way; keeping them out of the solver just meant losing schedule fidelity for no accuracy gain.

**G04's RA row is a duplicate, not a pool — verified mechanically (2026-07-17, TASK-346 code review M3), row dropped from the seed (2026-07-17, TASK-371).** `incarichi.csv` used to have *both* `T15,Recuperi ed Approfondimenti,G04,50.0` (an ordinary real course) *and* `RA,Recuperi ed Approfondimenti,G04,50.0` (the pseudo-teacher pool row) — same subject, same group, same 50h. `lessons.csv` confirms it: all 50 of G04's golden "Recuperi ed Approfondimenti" lesson-hours are taught by T15 (0 rows under any other teacher for that group/subject). So G04's RA hours were **not** an unassigned pool spread across many teachers the way G08's is — they were T15's real course, already fully accounted for by his ordinary Course row; a separate G04 RA accounting stub would have double-counted those 50h. This was also the mechanical explanation for the "1040 vs 990" anomaly flagged for G04 (Q-STAGE-2 below, Q7 in domande-per-la-scuola.md) — referente confirmed via Q3 ("Incarichi in riga is stale, Orario in righe wins") that the duplicate pool row was never cleaned up on the school's side, so TASK-371 dropped it from both seed CSVs; G04's incarichi real-course sum is now 750 (not 1040), and the group carries its own separate, expected residual against `Totale ore corso` (990) documented in `seed-csv-format.md` (the DUALE stage exception, section A above, not this duplicate). **G08 is the genuine case**: its 40h RA row has **no** matching real course and is delivered in the grid by many different teachers (T23 9, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2, T11 1, T03 1 — all imported as `unmatchedNoCourseTriples`, since none of them has a Course keyed to subject "RA").

**Decision: only G08 gets an RA accounting Course.** Mechanism matches Stage/Esame (a pseudo-teacher, not "no fixed teacher" as originally drafted here): `findOrCreatePseudoTeacher("RA")` creates a `Teacher{pseudo=true}`, and the rule is data-derived rather than a hardcoded group whitelist — an incarichi RA pool row becomes an accounting `Course` **unless a real (non-pseudo) course already exists for the same (subject, group)**, in which case the pool row is a duplicate and is skipped (logged). On the current seed that yields exactly one course, `Course{subject="Recuperi ed Approfondimenti", teacher=RA, studentGroup=G08, totalHours=40}`; G04's row is skipped because T15's real course covers it. A future re-extraction that changes which groups carry genuine pools is picked up automatically and surfaces in the import report's `raCoursesCreated`.

**Hour-accounting (updated 2026-07-17, TASK-366).** G08: `assigned` still includes the 40h pseudo-teacher RA pool line, created first and untouched by the micro-courses below - `createAccountingCourses`'s find-or-create guard runs before the micro-course step for exactly this reason, so it can't mistake a partial micro-course for full pool coverage. The 9 new real per-teacher "Recuperi ed Approfondimenti" micro-courses (31h total) are separate Course rows, each with `assigned=scheduled` (sized to exactly the rows they cover), so they simply report `OK` on the reconciliation dashboard (TASK-353) rather than reconciling against the pool - the pool keeps reporting `UNSCHEDULED-Expected` (pseudo, 40h assigned vs 0h scheduled), same shape as Stage/Esame's pseudo-teacher rows. No double-counting: the pool's 40h and the micro-courses' 31h are two different Course rows, not one total split two ways. G04: unchanged - no separate RA line, its 50h are inside T15's Course total, exactly as any other real course.

**TASK-346/366 does:** import golden `Recuperi ed Approfondimenti` rows under their real teachers as ordinary lessons (this already covers G04's 50h via T15); create **one** accounting-only-in-spirit-but-pseudo-teacher RA Course, for G08 only, from `incarichi.csv`'s RA row; create **no** RA course for G04; for G08's remaining 31 rows (raw subject "RA", no matching Course), create one real per-teacher micro-course per (teacher, group) pair and pin their Lessons (TASK-366, superseding the original "skip and log" behavior above).

---

## Implementation plan (ordered, each tagged with the absorbing task)

1. **[TASK-343 follow-up patch]** Where the importer currently *skips* `Stage`/`Esame`/`RA`/`Totale ore corso` rows for Course creation, instead create synthetic **accounting courses**: per-group Stage & Esame courses (pseudo-teacher, `professionalizzanti=false`, `totalHours` from incarichi) and the RA accounting course **for G08 only** (pseudo-teacher `RA` — G04's RA row is a duplicate of T15's real course, see section C). Keep StagePattern seeding for window groups; do **not** seed a StagePattern for G04.
2. **[TASK-346]** Golden import: for each `Stage` `lessons.csv` row create a pinned `Lesson` under the group's Stage course at that date/slot; create **no** lessons for Esame courses; import `Recuperi ed Approfondimenti` rows under their real teachers. Assert per-group `scheduled + stage` reconciles with incarichi (AC#2; G08: 990 assigned, 943 scheduled, deltas Stage −24 / Esame −24).
3. **[new follow-up]** `StudentGroupCompletionService`: `totalCurriculumHours` includes Stage (+ Esame/RA assigned pool); ideal-end walk stays on non-stage hours; stop treating stage as reducing the total. Add a test on G08.
4. **[TASK-353]** Reconciliation dashboard: assigned line per group includes Stage/Esame/RA; scheduled includes Stage pseudo-lessons; mark the expected Esame-unscheduled and Stage-shortfall deltas "A posto", not "Analizzare".
5. **[new follow-up, gated on Q6]** If school wants exams scheduled: extend FC calendar to 2027-06-15 via date-scoped `GroupCalendarRule` + pinned Esame lessons.
6. **[TASK-352]** Append the questions below.

## Questions to add (route to TASK-352)

- **Q-STAGE-1:** G04 (DUALE) has 224 stage hours spread across all five weekdays, Apr–Jun, with no stage window in `Vincoli Classi` and no incarichi Stage row. Is the apprenticeship day pattern fixed weekdays, alternating weeks, or genuinely irregular per student? (Determines StagePattern vs per-date pinned model.)
- **Q-STAGE-2 (answered, referente 17/07, Q7 combined with Q3):** G04's incarichi real-course hours used to sum to 1040 while Totale ore corso = 990 — explained as a duplicate (the RA pool row double-counting T15's real 50h course, dropped TASK-371, see section C) rather than a genuine second problem. Post-correction the real-course sum is 750; the −16 residual against Totale noted here originally (750 real + 224 stage = 974) was itself an artifact of the Stage course's pre-code-review-H4 `totalHours` silently excluding G04's 16 beyond-horizon rows (see section A's 2026-07-18 update) — with those folded in, Stage `totalHours` is 240 and 750 + 240 = 990 exactly, closing the residual as a side effect rather than a genuine second discrepancy needing referente's confirmation.
- **Q-STAGE-3:** Confirm stage assigned-vs-scheduled deltas are expected (G08: 300 assigned, 276 scheduled) so the dashboard can mark them "A posto".
- **Q-ESAME-1 (= existing Q6):** Should exam hours (12–24h/graduating group) be scheduled by the planner, or are they administrative-only? If scheduled, confirm FC exam dates 2027-06-11..15 (beyond the 06-10 year end) and whether to extend the calendar.
- **Q-RA-1:** For G04 (50h) and G08 (40h), RA is a pool taught by many teachers with no fixed assignment. Should the planner distribute it to specific teachers, or leave it as a floating pool the coordinator fills manually?
