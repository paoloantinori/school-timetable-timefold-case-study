# TASK-407 - Past-ideal-end overflow: golden-faithful or stamp gap?

**Status:** READ-ONLY investigation, no code changes. Today: 2026-07-27.
**Verdict (up top):** **GOLDEN-FAITHFUL for the bulk pattern; one MARGINAL edge case at the 2-day year-horizon boundary (G11, 9-14 lesson-hours) that does NOT warrant a construction change under the primary directive.**

The school's own golden timetable overflows the fact-based ideal end substantially for 3 of the 4 cited groups (G04, G08, G14). M12 (`overflowBeyondTargetEndDate`, `ONE_MEDIUM/day` since 2026-07-19, fact-based targets since TASK-394) is the calibrated lever for that overflow, exactly as its javadoc states. The run is consistently MORE COMPACT than the golden on every group where the golden overflows. The single group where the run overflows and the golden does not (G11) is bounded to the last 2 days of the school year (2027-06-09 and 2027-06-10) and totals 9-14 lesson-hours: an artifact of the plan running out of in-horizon fact rows 2 days before the school year ends, not a recurring-cell extension the school's own practice contradicts.

---

## Question

Is the past-ideal-end overflow (G11 labs ~2 days after ideal end; G04 many lessons past ideal end) GOLDEN-FAITHFUL (the golden also places lessons past ideal end, so intended + M12 handles it) or a HEURISTIC GAP (the weekly-template stamp extends recurring cells past the ideal end where the golden does NOT)?

## Method

Per the task's primary directive (judge on golden practice), I computed the fact-based ideal end per group independently in Python, mirroring `TargetEndDateCalculator.computeFactBasedIdealEnd` (lines 268-292 of `service/TargetEndDateCalculator.java`):

1. Sum `Course.totalHours` per group from `incarichi.csv` (excluding the `Totale ore corso` summary row, which is not a persisted `Course`).
2. Cumulate `DailyActivityQuota` facts in date order (`aula+lab+stage`) until `cumulative >= totalHours`. Source rows:
   - `calendario_fc.csv` for G04 / G08 / G13 (the finer FC source, `FCQuota=true`).
   - `calendario_class_days.csv` for every other group (TASK-385 sibling, `FCQuota=false`).
3. Out-of-horizon rows are filtered (`dt < 2026-09-14 || dt > 2027-06-10`), matching `DataGenerator.generateExpectedDailyHours` lines 949-953.
4. If the facts never reach `totalHours`, the calculator returns the LAST in-horizon plan date (line 290: `LocalDate ideal = last.isAfter(yearEnd) ? yearEnd : last`).

I used the TIGHT ideal end (`idealEndDateInfo.idealEndDate()`), not `idealEndDateVrpBuffered` (the one M12 actually filters on). The latter is the former plus `ensureEnoughSlots` 1-week extensions when slot availability is short. Since this is a RELATIVE comparison (run vs golden on the same date), the conclusion is invariant to which of the two I pick. Where the absolute date matters I note it explicitly.

Sources:
- **RUN** (m-12 live solve): `claudedocs/spalmone-2026-27/m12-results/m12-run1/lessons.txt` (12,287 lesson rows, pipe format).
- **RUN** (val406, the original evidence the task body cites for the G11 observation): `claudedocs/spalmone-2026-27/task406-results/val406-run{1,2}/lessons.txt`.
- **GOLDEN**: `claudedocs/spalmone-2026-27/data/lessons.csv` (12,979 rows).
- Lab classification: `FC_QUOTA_BOUND_LAB_SUBJECTS` per CLAUDE.md (G04 / G08 / G13 sets) plus a subject-name marker list (Informatica, Disegno, Cad, Taglio, Moulage, Confezione, Saldatura, Lavorazioni, Tessil, Meccan, Stilist, Acconciatura, Tricologia, Chimica, Fisica, Termoidraulico, Elettrotecn, Lab).

## Ideal-end dates per group

| Group | totalH | planH (in-horizon) | plan days | reaches totalHours? | idealEnd | source |
|-------|-------:|-------------------:|----------:|---------------------|----------|--------|
| **G11** | 990 | 966 | 170 | NO (cum=966) | **2027-06-08** | class_days.csv |
| **G04** | 750 | 752 | 130 | YES | **2027-04-09** | fc.csv |
| **G08** | 990 | 966 | 171 | NO (cum=966) | **2027-06-07** | fc.csv |
| G13 | 990 | 978 | 171 | NO | 2027-06-10 | fc.csv |
| G14 | 115 | 120 | 21 | YES | 2026-10-12 | class_days.csv |
| G02 | 990 | 990 | 171 | YES | 2027-06-10 | class_days.csv |
| G01 | 990 | 990 | 171 | YES | 2027-06-10 | class_days.csv |
| G03 | 990 | 969 | 171 | NO | 2027-06-10 | class_days.csv |
| G12 | 990 | 970 | 177 | NO | 2027-06-10 | class_days.csv |

Two distinct shapes:

- **Plan reaches `totalHours` in horizon** (G04, G14, G02, G01, and the FC/G13 groups whose facts cumulate cleanly): ideal end is the date the plan crosses the threshold. G04 at 2027-04-09, G14 at 2026-10-12.
- **Plan runs short of `totalHours` in horizon** (G11, G08, G13, G03, G12): the calculator returns the LAST in-horizon plan date. For most of these that is the year horizon end (2027-06-10); for G11 it is 2027-06-08 (the school's own class-days sheet simply stops on 2027-06-08 for G11, even though the school year ends 2027-06-10).

The G11 "2-day gap" Fedora noted (ideal 08/06 vs year end 10/06) is this second shape: a fact-sheet / horizon mismatch, not a model rule.

## Run vs golden: lesson-hours past ideal end

### m-12 run1 (the run the user prompt pointed me at)

| Group | idealEnd | RUN lab | RUN aula | RUN total | GOLD lab | GOLD aula | GOLD total | Verdict |
|-------|----------|--------:|---------:|----------:|---------:|----------:|-----------:|---------|
| **G11** | 2027-06-08 | 0 | 0 | **0** | 0 | 0 | **0** | clean (m-12 run does not reproduce the val406 observation) |
| **G04** | 2027-04-09 | 61 | 62 | **123** | 30 | 292 | **322** | golden overflows (golden > run) |
| G08 | 2027-06-07 | 0 | 0 | 0 | 0 | 10 | 10 | golden overflows |
| G13 | 2027-06-10 | 0 | 0 | 0 | 0 | 0 | 0 | clean |
| G14 | 2026-10-12 | 47 | 42 | 89 | 60 | 60 | 120 | golden overflows |
| G02 / G01 / G03 / G12 | 2027-06-10 | 0 | 0 | 0 | 0 | 0 | 0 | clean |

### val406 run1 and run2 (the task body's original evidence for the G11 observation)

The m-12 run does not exhibit the G11 overflow. The val406 runs (the original evidence) DO, and the count matches the task spec's "~9h / 7 Progettazione Termoidraulica":

| Group | idealEnd | RUN (val406-r1) past-end | RUN (val406-r2) past-end | GOLDEN past-end |
|-------|----------|--------------------------|--------------------------|-----------------|
| **G11** | 2027-06-08 | **9** (0 lab + 9 aula; 6 are Progettazione Termoidraulica) | **14** (2 lab + 12 aula) | **0** |
| **G04** | 2027-04-09 | 62 (26 lab + 36 aula) | 63 (29 lab + 34 aula) | **322** (30 lab + 292 aula, of which 240 Stage) |

The G11 run rows on val406-r1 (all on 2027-06-09 and 2027-06-10, the last 2 school days):

```
2027-06-09  aula  Gestione aziendale             T06
2027-06-09  aula  Personalizzazione              T11
2027-06-09  aula  Progettazione Termoidraulica   T05      x4
2027-06-10  aula  Progettazione Termoidraulica   T05      x3
```

The G11 golden has zero placements after 2027-06-08 (the school ended G11 on 2027-06-07 in the golden timetable).

### G04: golden overflows, run compacts (monthly distribution)

| Month | RUN past-end | GOLDEN past-end |
|-------|-------------:|----------------:|
| 2027-04 | 61 | 89 |
| 2027-05 | 56 | 156 |
| 2027-06 | 6 | 77 |
| **Total** | **123** | **322** |

The golden spreads G04 all the way to June (77h in June alone). The run is strictly more compact than the golden in every month. The golden's past-end volume is dominated by Stage (240h, the pseudo-teacher Stage course placed on dates past 2027-04-09) plus 18h Scienze, 16h Recuperi ed Approfondimenti, 12h Informatica, 11h Lingua Italiana.

## Mechanism confirmation (read-only)

The task's root-cause candidate is correct as stated.

- `DayClusteredConstructionPhase.stampWeeklyTemplate` (line 1102) iterates `allSchoolDates` across the full school year with **NO** `getIdealEndDate` cap. The javadoc at lines 1091-1100 states this explicitly, citing the TASK-379 golden-first calibration: the golden spreads ~29.3% of hours beyond computed target ends ("lo spalmone è lo spalmare"), M12 at `ONE_MEDIUM/day` is the calibrated counterweight, and S12 rewards year-long weekly subject presence.
- The repair tail (`findFirstAvailableDate` line 478, `findNextAvailableDay` line 496) DOES stop at `getIdealEndDate(group)` (lines 480, 498). The asymmetry is intentional and documented.
- M12 (`BaseConstraintProvider.overflowBeyondTargetEndDate` lines 936-954) reads `SchoolSchedule.getIdealEndDateVrpBuffered` and penalizes `ONE_MEDIUM * daysBeyond` per past-end lesson, pinned-exempt via `!isAuthoritativePin`.
- `TargetEndDateCalculator.computeFactBasedIdealEnd` (lines 268-292) returns the LAST in-horizon plan date when the plan's cumulative hours never reach `totalHours`.

## Per-group verdict

- **G04: GOLDEN-FAITHFUL.** Golden 322h past ideal end (240 Stage + 82 other), run 62-123h past end. The run is MORE COMPACT than the school's own timetable. M12 is the right lever; the construction stamp is correctly free to place recurring cells into April-May-June because the golden does too.
- **G08: GOLDEN-FAITHFUL.** Golden 10h past end, run 0h. Same conclusion.
- **G14: GOLDEN-FAITHFUL.** Golden 120h past end (essentially the group's entire demand; G14 is the special case CLAUDE.md documents: its real window is gen-mag, the fact-based ideal 2026-10-12 is "earliest possible", not the planned end). Run 89h. The marker-UI confusion Fedora flagged is a separate UX follow-up, not a model/construction change.
- **G11: MARGINAL GAP, NOT a stamp gap in the volumetric sense.** Run 9-14h past end (only on val406 runs; m-12 run has 0), golden 0h. This is the ONLY group where the run overflows and the golden does not. The mechanism is bounded and structural:
  - G11's `calendario_class_days.csv` rows stop on 2027-06-08 with cumulative 966h short of the 990h `totalHours` (the school's own plan literally does not reach `totalHours` inside the in-horizon window).
  - The fact-based ideal-end calculator therefore returns 2027-06-08 (last in-horizon plan date), leaving 2027-06-09 and 2027-06-10 as valid in-horizon school days AFTER the ideal end.
  - The stamp places recurring cells on those 2 days because they are valid school days with no cap pulling it back. The golden does not, because the school's own timetable ended G11 on 2027-06-07.
  - 0-2 of the 9-14 past-end rows are labs (the rest are aula: Progettazione Termoidraulica, Gestione aziendale, Personalizzazione, Lingua, Matematica, Scienze). Even the "labs" characterization is borderline: Progettazione Termoidraulica is a theory subject taught by T05. The lab subject is the separate "Laboratorio Termoidraulico" course (54h), which the run does NOT place past end.
  - The entire gap is bounded to a 2-day window at the absolute end of the school year.

## Final verdict and recommendation

**GOLDEN-FAITHFUL overall. No construction change is warranted.**

The school's own golden timetable overflows the fact-based ideal end for every cited group except G11, and the overflow is substantial (G04: 322h; G14: 120h; G08: 10h). The run is consistently MORE COMPACT than the golden on every one of those groups. M12 (`ONE_MEDIUM/day`, fact-based targets since TASK-394) is the calibrated lever for this overflow, and the construction stamp's lack of an ideal-end cap (javadoc at `DayClusteredConstructionPhase` lines 1091-1100) is the intended design under the TASK-379 golden-first directive ("lo spalmone è lo spalmare").

The G11 edge case (9-14h past end where golden has 0) is real but MARGINAL and bounded:

- It is a 2-day year-horizon boundary effect (2027-06-09 / 2027-06-10), not a recurring-cell extension the golden contradicts in any volumetric sense.
- It does not reproduce on the m-12 run (0h past end there).
- It is rooted in a fact-sheet / `totalHours` mismatch (G11's in-horizon plan sums to 966h against 990h `totalHours`), not in a missing cap on the stamp's recurring logic.
- Per the primary directive, a model rule is judged on whether it reflects golden practice: the golden practices substantial overflow for 3 of the 4 cited groups, so the construction stamp correctly follows that practice. Capping the stamp at the ideal end to eliminate G11's 9-14h would also cap G04/G08/G14, where the golden itself overflows, deviating from golden practice. That trade is not justified by a 9-14h edge case.

If a future run sees the G11 past-end count grow beyond the 2-day year-horizon window (i.e. placements in May or early June where the golden has none), that would be a genuine stamp gap and the right fix would be to extend the repair tail's `getIdealEndDate` cap (already in place at lines 480/498) onto the stamp loop as well. Today's evidence does not meet that bar.

The standing recommendation from Fedora's preliminary note stands: the `idealEndDateInfo.idealEndDate()` UI marker can be confusing for groups whose `totalHours < planTotal` (G04, G14) because it displays "earliest possible" rather than the school's planned end. That is a UX follow-up, not a model or construction change.

## Artefacts

- `/Users/user/planner/src/main/java/com/school/planner/service/TargetEndDateCalculator.java` (lines 117-250 `computeGroupTargetDate`, 268-292 `computeFactBasedIdealEnd`).
- `/Users/user/planner/src/main/java/com/school/planner/solver/BaseConstraintProvider.java` (lines 917-954 M12).
- `/Users/user/planner/src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java` (lines 478-498 repair-tail ideal-end cap; lines 1091-1100 stamp no-cap javadoc; line 1102 `stampWeeklyTemplate`).
- `/Users/user/planner/src/main/java/com/school/planner/bootstrap/DataGenerator.java` (lines 860-965 quota seeding).
- Data: `claudedocs/spalmone-2026-27/m12-results/m12-run1/lessons.txt`, `claudedocs/spalmone-2026-27/task406-results/val406-run{1,2}/lessons.txt`, `claudedocs/spalmone-2026-27/data/lessons.csv`, `data/calendario_fc.csv`, `data/calendario_class_days.csv`, `data/incarichi.csv`.
