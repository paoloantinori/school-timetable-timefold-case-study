# Weekly-template pre-assignment redesign — research + go/no-go (TASK-351)

**Date:** 2026-07-17
**Author:** research pass for milestone m-11
**Evidence:** `scripts/analyze/derive-golden-template-recurrence.py` (re-runnable; numbers below are its output verbatim)
**Cross-refs:** TASK-340 (solve without pre-assignment), TASK-364 (calendario importer), TASK-347 (dep), analysis.md §4.4, calendario-scolastico-format.md §5, vincoli-in-parole.md

## Recommendation (3 lines)

1. **NO-GO on a template-first pipeline rewrite.** The golden grid is not a stamp-and-patch weekly template: within homogeneous segments only **41.5%** of real lesson-hours sit in a strictly-recurring cell, the stamp-the-modal-week **ceiling is 72.0%**, and each group needs **~16 materially different weekly grids** across the year — so a template solve would still hand ~28% of hours (and the cross-group teacher/room conflicts it cannot see) to a full date-level repair, buying little over the pipeline we already have.
2. **GO on "constraint-first, template-later":** keep the current per-date pipeline, lean on the calendario/golden **pins** (TASK-364) that already fix the one genuinely stable layer — the lab/stage day skeleton — and add a cheap **weekly-stability soft constraint** that rewards identical (weekday, slot, teacher, subject) cells across consecutive weeks. This captures the real 40–70% recurrence as a preference without freezing anything.
3. For m-11's first solve, ship the pins + weekly-stability soft constraint on the existing 6-phase solver; do **not** build a weekly-grid entity/instantiation/repair stack.

## 1. The load-bearing numbers

Segments per group are cut from the golden data itself: stage windows come from the `Stage` pseudo-teacher rows in `lessons.csv` (authoritative per analysis.md, and complete for all 8 stage groups — `vincoli_classi.csv` only dates 3 of them, so I derive them from the grid instead; **deviation from the task's "use vincoli_classi.csv dates", justified**). Edge segments = first 2 and last 2 teaching weeks; the rest split before/during/after stage. Recurrence is measured over **real** lessons only — the Stage/Esame/RA pseudo-teachers are pinned from the calendar, not placed by the solver.

Two closure-robustness fixes were applied after a first pass gave a misleadingly low 38.5% / 23.5-template result: the 2026/27 calendar has 29 closures scattered as bridge days, so ~9 of 38 weeks per group are "short". (a) A cell's recurrence denominator is the weeks in which **that weekday was actually open for teaching** (a bridge-day Monday is not held against a Monday cell); (b) the template-change metric compares consecutive weeks only over the **weekdays open in both**. Without these, closures masquerade as template churn.

Two metrics per (group, segment):
- **recurring %** — hours whose (weekday, slot, teacher, subject) cell appears in ≥80% of that weekday's open weeks (strict; understates long segments where a cell stable in 25/34 weeks scores 0).
- **modal %** — hours matching the single stamped modal weekly grid = the **template-first ceiling** (fair; a 25/34 cell still wins its slot and counts as covered).

```
group   segment        weeks  hours recurring %   modal %
---------------------------------------------------------
G01  first-2-weeks      2     56       42.9%     73.2%
G01  middle            34    899       45.9%     66.6%
G01  last-2-weeks       2     35       71.4%     85.7%
G02   first-2-weeks      2     56       46.4%     75.0%
G02   middle            34    899       16.7%     62.2%
G02   last-2-weeks       2     35       65.7%     82.9%
G03  first-2-weeks      2     56       64.3%     83.9%
G03  middle            34    899       53.5%     79.1%
G03  last-2-weeks       2     35       88.6%     94.3%
G04   first-2-weeks      2     49       20.4%     61.2%
G04   before-stage      29    671       34.9%     72.3%
G04   during-stage       4     20        0.0%     55.0%
G04   last-2-weeks       2     10      100.0%    100.0%
G05  first-2-weeks      2     56       67.9%     85.7%
G05  middle            34    899       19.5%     60.8%
G05  last-2-weeks       2     35       94.3%     97.1%
G06   first-2-weeks      2     55       36.4%     70.9%
G06   middle            34    911        6.4%     53.9%
G06   last-2-weeks       2     24      100.0%    100.0%
G07  first-2-weeks      2     51       31.4%     66.7%
G07  before-stage      24    567       61.2%     82.0%
G07  during-stage       9     54       64.8%     83.3%
G07  last-2-weeks       2     23      100.0%    100.0%
G08   first-2-weeks      2     47       42.6%     74.5%
G08   before-stage      21    465       55.5%     80.6%
G08   during-stage       2      7      100.0%    100.0%
G08   after-stage        6    133       66.2%     82.0%
G08   last-2-weeks       2     15       33.3%     66.7%
G09  first-2-weeks      2     51       54.9%     78.4%
G09  before-stage      20    462       52.2%     71.9%
G09  during-stage       3     15       73.3%    100.0%
G09  after-stage        4    107       33.6%     75.7%
G09  last-2-weeks       2     31       80.6%     90.3%
G10   first-2-weeks      2     45       57.8%     80.0%
G10   before-stage      21    495       20.8%     62.0%
G10   during-stage       1      4      100.0%    100.0% (<2 wk: trivial)
G10   after-stage        5    108        7.4%     55.6%
G10   last-2-weeks       2     18      100.0%    100.0%
G11  first-2-weeks      2     51       70.6%     86.3%
G11  before-stage      14    312       63.8%     86.5%
G11  during-stage       9     63      100.0%    100.0%
G11  after-stage       10    227       65.2%     87.7%
G11  last-2-weeks       2     18      100.0%    100.0%
G12  first-2-weeks      2     43       79.1%     90.7%
G12  before-stage       7    153       60.8%     81.0%
G12  during-stage      19    244       69.3%     83.6%
G12  after-stage        8    206       44.7%     83.0%
G12  last-2-weeks       2     29       89.7%     96.6%
G13    first-2-weeks      2     51       58.8%     80.4%
G13    before-stage      25    573       49.6%     74.5%
G13    during-stage       8     40       47.5%     80.0%
G13    last-2-weeks       2     19       63.2%     84.2%
G14   first-2-weeks      2     16      100.0%    100.0%
G14   middle            11     88       34.1%     68.2%
G14   last-2-weeks       2     11       27.3%     72.7%
---------------------------------------------------------
WEIGHTED within-segment, segments >=2 weeks, over 10538 real lesson-hours:
  strict recurrence (>=80% of weeks):     41.5%
  modal-grid coverage (template ceiling): 72.0%

Distinct weekly templates per group across the year
(1 + consecutive-week pairs differing by >30% of cells, over shared open days):
  G01 24 | G02 16 | G03 10 | G04 21 | G05 24 | G06 18 | G07 9
  G08 12 | G09 23 | G10 22 | G11 5 | G12 17 | G13 22 | G14 7
  mean 16.4  (out of ~38 teaching weeks)
```

### What the numbers say

- The task predicted segmenting would push recurrence **"far above 67%"**. The data **refutes** that. Within homogeneous segments, strict recurrence is **41.5%** and the fair modal ceiling is **72.0%** — *lower* than, or at best comparable to, the whole-year 67%, not above it.
- **Reconciliation with analysis.md's 67%:** that figure counts hours whose cell "repeats in ≥10 weeks *somewhere* in the year" — a loose absolute-count threshold (a cell present in 10 of 38 weeks qualifies) that also includes the trivially-recurring stage grid, and those 10 weeks can be scattered across non-contiguous periods rather than forming one stampable template. It is not a claim that a single weekly template covers the year; measured properly (relative recurrence within a segment) the template structure is much weaker. **No source is wrong; they measure different things.** The task read 67% as evidence of a stampable weekly template and inferred segmenting would push it higher; the segmented measurement shows the opposite — that was the optimistic reading.
- **The churn is genuine subject-grid variation, not teacher rotation.** Sensitivity check (whole-year single-grid ceiling): (teacher, subject) 65.0%, subject-only 65.1%, teacher-only 69.3%. Coarsening the cell does not lift the ceiling — the human really does move *which subject* sits at a (weekday, slot) week to week. This kills the "the subject grid is stable, only teachers rotate" escape hatch.
- **~16 distinct templates per group** means the grid materially changes roughly every second teaching week. The value proposition of template-first ("solve one small weekly grid, stamp it many times") does not hold: you would solve ~16 grids per group and still repair ~28% of hours.
- **Where recurrence IS real, it is the coarse lab/stage day skeleton**, not the fine academic fill. During-stage segments template trivially (few return-day lessons); first-year academic groups (G02 / G06 *middle*: 62%/54% modal) are the *least* template-like, because vincolo #9 ("≥1h/week per subject") combined with M21/H15 (no adjacent same-subject → effectively max 1h/day/subject outside the Informatica/Disegno whitelist) forces many subjects to be sprinkled one hour per day at shifting slots — structurally antithetical to a stable weekly cell. The stable skeleton is exactly what the calendario import (TASK-364) already pins.

## 2. Honest design of a template-first pipeline (the rejected option)

Sketched fully so the rejection is on the merits, not on vagueness.

**Stage A — weekly-grid solve.** Entity = a `(group, weekday, slot)` cell over a one-**week** domain; value = a course/teacher (or empty). Not 14 independent 5×8 grids: teachers and rooms are shared across every group, so H1/H2/H7/M-LA/S1 only exist *across* group-sets. Concretely, if teacher T is placed with group A on Monday slot 3 in the template, T is thereby reserved and unavailable to group B on Monday slot 3 — a conflict invisible to a per-group solve. You must therefore solve **all 14 groups together** — one 14×5×8 = **560-cell** weekly CSP with the shared-resource constraints — once **per segment** (≥5 segments/group, and ~16 distinct grids/group over the year → order 16 such solves, not one). Seeds: calendario/golden pins become fixed template cells; `GroupCalendarRule` supplies the per-weekday open/closed domain directly.

**Stage B — instantiation.** Stamp each segment's grid across its dates, dropping cells on closures and stage days.

**Stage C — date-level repair.** Fix everything the template cannot express: closures, dated teacher absences, per-subject hour-total remainders (the template gives a weekly rate; yearly totals must land exactly — vincolo #8), choice-day disjunctions (T11/T05 one free day — TASK-368), G12 edge-of-day professionalizzanti (TASK-370), and the ~28% of hours the modal grid never covered. This stage is a full local-search over dates — i.e. most of the current pipeline, rebuilt.

### Constraint mapping (template-level / date-level / both)

| Constraint | Level | Note |
|---|---|---|
| H0 unassigned | date | each concrete lesson instance must be placed |
| H1 teacher conflict | **both** | within-week only if all groups solved together; still re-checked at date level after instantiation |
| H2 room conflict | date | rooms assigned at date level (template carries no room) |
| H3 group conflict | template | structural in a group's own grid |
| H4 stage blackout | both | window is a date range; per-weekday pattern in-template |
| H5 teacher unavail | both | recurring availability = template; dated absences = date (repair) |
| H6 school holiday | date | closures are dates |
| H7 max teacher daily hrs | both | per-weekday cap in-template, but teacher spans group-sets → date |
| H11 max group daily hrs | template | per weekday |
| H14 group start date | date | |
| H15 same-day same-subj consecutive | template | within a template day |
| H16 consecutive same-subj teacher-room | both | room part is date-level |
| H17 vertically compact | template | **becomes trivially satisfied inside a template day** — the one real win |
| H20 group calendar closed slot | template | `GroupCalendarRule` already *is* a weekly-template domain |
| H-D1 doppio | template | co-teaching within the weekly cell |
| H-M2 room suitability | date | room assignment is date-level |
| M3 room capacity | date | |
| M5 informatica after stage | date | compares lesson date to stage end |
| M12 overflow beyond target | date | |
| M18 5-hour groups start slot 0 | template | per-day |
| M20 daily quota fill | template | per-day |
| M-LA lab assistant conflict | date | shared across groups |
| S1 teacher gaps | both | teacher spans group-sets → date |
| S2 subject distribution | both | |
| S3 teacher preference | template | recurring |
| S4 no consecutive same-subj | template | |
| S7 reward consecutive student | template | |
| S8 isolated student lesson | template | |
| S9 dilute hours over year | **date (anti-template)** | spreading hours across the year is by definition cross-week — directly fights a stamped template |
| S10 generic in labs | template | |
| S11 group room affinity | date | |

**Only one hard constraint (H17) and a handful of soft ones become "free" inside the template. Every shared-resource constraint (H1, H2, H7, M-LA, S1) and every date-anchored one (H4, H6, H14, M5, M12, S9) still needs full date-level handling** — and S9 actively opposes templating. The search-space reduction the task hoped for is small because the template cannot be solved per-group-set (shared resources force the whole problem into Stage A) and cannot be stamped cleanly (16 grids, 72% ceiling, S9 pulling the other way).

### Fate of existing machinery (both options)

| Component | Under template-first | Under recommended (constraint-first) |
|---|---|---|
| `CalendarioImportService` / `GoldenScheduleImportService` | **survives, central** — pins = template seeds | **survives, central** — pins the stable skeleton |
| `GroupCalendarRule` + H20 | survives — the template open/closed **domain** | survives unchanged |
| `RecurringAvailabilityRule` + `AvailabilityExpansionService` | survives — already the "weekly template → dated rows" pattern | survives unchanged |
| `DayClusteredConstructionPhase` | superseded by Stage B instantiation + Stage C repair | **survives** — the per-date dense CH |
| `HybridPhase1aService` + `PreAssignmentValidator` + `PreAssignmentEntityExtractor` | superseded by the weekly-grid solve | **retire** — its job (compute lab placements) is now done by the calendario pins; increasingly vestigial. Migration: gate the `/pre-assign` endpoint behind "no pins present", then remove. (Note the non-cooperative-cancellation defect flagged in TASK-351 notes — do not invest in fixing it; retire instead.) |
| `LabRotation` + `LabRotationResource` | its weekly-pattern role is subsumed by the template domain — but it is **already retired** (`generateLabRotations()` is empty, TASK-364) | stays retired |
| `GreedyRotationAssigner` / `AdaptiveSlotFinder` / `RotationCandidate` / (`BacktrackingPreAssigner`, already deprecated TASK-228) | superseded | **retire with HybridPhase1a** — all reference the dead LabRotation path |

Net: the same components survive under **both** options (imports, GroupCalendarRule, availability expansion, the day-clustered CH). The template-first option's *only* structural addition is a weekly-grid entity + instantiation + repair stack; everything it would retire (HybridPhase1a, GreedyRotationAssigner, LabRotation) is **already dead or should be retired regardless of this decision.** So template-first buys no cleanup that constraint-first doesn't also get.

## 3. The simpler alternative — weekly-stability soft constraint

Keep the current per-date pipeline. Add one soft constraint, **S-WS "weekly stability"**: reward a lesson whose (group, weekday, slot, teacher, subject) matches the lesson in the same (weekday, slot) of the adjacent week(s). Implementation is a self-join on Lesson over `timeslot.date ± 7 days`, same shape as the existing S4/S7/S8 pair constraints — a few dozen lines in `BaseConstraintProvider`, one weight to tune, no new entity, no new phase, no import changes.

- **What it captures:** the genuine 40–72% recurrence, expressed as a *preference*. The solver gravitates toward repeating cells where that is cheap and breaks the pattern where date-level constraints (closures, hour totals, S9) demand it — which is exactly what the human does.
- **What it risks:** low. Worst case it is weight-tuned to zero and removed; it cannot make a feasible schedule infeasible (soft). One watch-item: it partially opposes S9 (dilute over year); pick the weight so stability wins *within* a segment and S9 wins *across* segments (or scope S-WS to same-segment week pairs).

## 4. Cost / risk comparison

| | Template-first pipeline | Weekly-stability soft constraint |
|---|---|---|
| New code | weekly-grid entity, `@PlanningVariable` over week domain, a solver config/phase for Stage A, instantiation service, repair phase, reconciliation of template↔date hour totals | one soft constraint (self-join) + one weight |
| Machinery retired | HybridPhase1a et al. (but those should go anyway) | same (independent of this) |
| Ceiling quality | 72% stamped, 28% always repaired at date level; shared resources force a 560-cell whole-school weekly solve ×~16 | unbounded — the date solver still explores the full space, just biased toward recurrence |
| Risk | high — a second solver stage, a new failure mode (template infeasible / instantiation conflicts), S9 conflict baked into the architecture | low — additive, reversible, familiar constraint shape |
| Effort | 5–8 tasks, multi-week, touches solverConfig sync surface (see `.claude/rules/solver-config-sync.md`) | 1–2 tasks |
| Payoff | one hard constraint (H17) trivialized; modest search-space cut | captures the real recurrence as guidance; keeps optionality |

The recurrence numbers cap the template-first payoff at ~72% coverage with ~16 grids/group; that ceiling does not justify the architecture. The soft constraint gets most of the same benefit (biasing toward the recurring 40–72%) at a fraction of the cost and risk.

## 5. Go/No-Go and what to do for m-11

**NO-GO** on the template-first rewrite. **GO** on constraint-first (weekly-stability soft + rely on existing pins). This is the "constraint-first, template-later" verdict: if, after m-11's first real solves, the weekly-stability constraint proves the recurrence is worth hard-templating a *specific* segment type (e.g. during-stage return days, which template near-100%), revisit a *narrow* template for that case only — not a whole-pipeline rewrite.

**For m-11's first solve:** current 6-phase per-date pipeline + calendario/golden pins (TASK-364, already the source of the stable skeleton) + the new weekly-stability soft constraint. Do not build a weekly-grid entity/instantiation/repair stack.

### Task breakdown (recommended path)

| # | Task | Size | Depends |
|---|---|---|---|
| 1 | Implement S-WS weekly-stability soft constraint (self-join on Lesson over ±7-day same weekday/slot; reward matching teacher+subject) + ConstraintProvider test | S | — |
| 2 | Scope/weight S-WS vs S9 (same-segment week pairs or tuned weight); document in CLAUDE.md constraint table | S | 1 |
| 3 | Execute TASK-340 (solve with vs without pins) now that the recurrence picture is known; confirm pins help the stable skeleton and do not freeze the churny academic fill (its concern #4) | M | 1 |
| 4 | Retire HybridPhase1a + GreedyRotationAssigner + AdaptiveSlotFinder + LabRotation resource/entity: gate `/pre-assign` behind "no pins", then remove; keep DayClusteredConstructionPhase | M | 3 |
| 5 | Fix `claudedocs/solver-phases.md` termination drift (doc says `unimprovedSecondsSpentLimit`; XML uses `diminishedReturns` — Timefold-2.x residue) | XS | — |
| 6 | After first m-11 solves, measure realized weekly stability vs golden 72% ceiling; decide whether a narrow during-stage template is worth it | S | 3 |

If instead the team wants template structure sooner, the *minimum* honest scope is tasks 1–2 only; the full pipeline is tasks that do not exist here because the recommendation is not to build them.

## 6. Cross-references

- **TASK-340** (solve without pre-assignment): this analysis predicts its verdict — pins help on the stable lab/stage skeleton (which the human genuinely repeats) and would *harm* the academic fill if extended there (freezing suboptimal placements, fragmenting slots — its stated concern #4). Task 3 above executes it.
- **TASK-364** (calendario importer): supplies the pins that are the real, stable weekly structure; this doc reframes it as the *entirety* of the worthwhile "template", not a precursor to a bigger one.
- **analysis.md §4.4**: its 67% is an absolute-count recurrence, not evidence for a stampable template; reconciled in §1.
- **vincoli-in-parole.md**: vincolo #9 (≥1h/week/subject) + M21/H15 (max 1h/day/subject outside whitelist) is the structural reason the academic grid churns and cannot be templated.
```
