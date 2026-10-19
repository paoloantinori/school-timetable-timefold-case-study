---
id: TASK-396
title: >-
  Strategic pivot — main construction must become human-faithful (hard-feasible
  piece-by-piece + box-the-rest + bounded backtrack), replacing the
  scatter-and-repair paradigm
status: To Do
assignee: []
created_date: '2026-07-23 05:00'
updated_date: '2026-07-27 04:48'
labels:
  - claude-planned
  - search
  - construction
  - spalmone-2027
dependencies: []
references:
  - src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java
  - src/main/java/com/school/planner/solver/WeeklyTemplateBuilder.java
  - src/main/java/com/school/planner/service/BacktrackingPreAssigner.java
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
  - claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md
documentation:
  - >-
    claudedocs/design/human-faithful-construction-spike-2026-07-23.md (the spike
    that gates this task)
priority: high
ordinal: 220000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Decision (user, 2026-07-23): "Despite the best results we have ever posted, we have to change approach."
The 2026-07-23 laptop validation (HEAD 62f10f8e) produced the best results yet — hard **{-1, 0}**
across two runs. But the METHOD is wrong and the user has decided to pivot. This task captures
that strategic decision for Fedora.

## Why — the primary directive is violated at the foundation
The human planner's invariant (user's model, 2026-07-23): *"treat it as a puzzle, complete it
piece after piece; every placed piece is never violating a hard constraint; the not-yet-placed
pieces stay off the table, in a box; if you have to break one, stop and change the most recent ones."*
I.e. **constructive, hard-feasible at every step, unplaced-in-box, chronological backtrack.**

The solver does the opposite: `DayClusteredConstructionPhase` scatters essentially all 12,301
lessons onto the table — phase 1 ends at **-44,100 hard** (measured, both runs, byte-identical) —
and then phase 2 (LS-Compactness) spends **~60 minutes repairing its own mess** down to ~0. That is
"put the wrong pieces in the wrong places, then rearrange." Reaching hard {-1,0} that way is a good
**outcome** from a **wrong method**: it is fragile (run 1's ph2 landed at -7, run 2's at -2,003
before ph3 rescued it — that variance IS the lack-of-construction-guarantee), it is expensive, and
it is not how the school reasons about the problem. Per the primary directive ("if the human does it,
we must too"), the construction must adopt the human's hard-feasible invariant.

## What already works the human way (reuse, don't reinvent)
- **Pre-assignment** (`BacktrackingPreAssigner` / `HybridPhase1aService` / `PreAssignmentValidator`):
  IS the puzzle model for a subset — tries candidates one at a time, places only 0-hard-feasible
  ones, skips the rest, backtracks. Pieces that don't fit stay in the box.
- **H0 (unassigned-lesson penalty):** the model already supports a "box" — a lesson can sit with a
  null timeslot instead of being forced into a conflict.
- So the gap is specifically the **main construction** (`DayClusteredConstructionPhase` /
  `WeeklyTemplateBuilder`), which is greedy first-fit with NO backtracking.

## The honest risk — scale
The pre-assignment CSP works because it touches a small subset (lab rotations). Full chronological
backtracking on 12,301 tightly-coupled lessons (shared teachers across 10-14 groups) will NOT scale
naively. The realistic shape is: strong ordering heuristics (hardest/most-constrained first) + the
box as pressure-relief (H0) + **BOUNDED** backtrack (a recent-window undo — exactly the user's
"change the most recent ones"), not unbounded CSP. This is why a spike must precede the rewrite.

## Spike-first (de-risk before rewriting the pipeline)
Per "spike the riskiest unknown end-to-end before scaffolding": prove the paradigm on the hardest
unknown BEFORE touching the 6-phase pipeline. Concretely — a standalone constructive solver on a
representative slice (the shared-teacher tight groups, or a scaled-down problem), measuring whether
it reaches near-0-hard at construction end without exorbitant backtracking. The spike's go/no-go
decides full-rewrite vs hybrid (human-faithful for the hard core + existing CH for the easy
majority) vs reject.

## Success metrics (before -> target)
- construction-end hard: **-44,100 -> near 0** (with H0-box lessons instead of conflict lessons).
- ph2 LS budget: **60 min -> a fraction** (LS only places the box, doesn't repair 44k violations).
- run-to-run hard variance: today {-1, 0} but achieved via fragile ph2 rescue -> reliable by
  construction (the human's invariant holds at every step).
- matches the golden's own 0-hard practice.

## Related
- **TASK-390** (narrow): identified the no-backtracking greedy for the G05 template specifically.
  This task GENERALIZES that finding to the whole construction paradigm.
- **TASK-393** (final compaction phase): a safety net for gaps LS leaves; far less needed if the
  construction is hard-feasible by design.
- Validation evidence: `claudedocs/spalmone-2026-27/validation-393-results/VERDICT.md` (the -44,100
  construction / 60-min repair / ph2 variance numbers).
- Fedora-side search-architecture decision. laptop role: spike + measure.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SPIKE (decides go/no-go before any pipeline rewrite): a standalone human-faithful constructive solver — or a scaled-down / hardest-subset construction — that places hardest-first into hard-feasible slots only, boxes the rest (H0/null), and applies BOUNDED chronological backtrack. Measures: construction-end hard (target near 0, vs today's -44,100), backtracking cost (time + undo count), and whether it scales toward 12,301 lessons without blowing up.
- [ ] #2 Decision documented from the spike: full pipeline rewrite vs hybrid (human-faithful for the hard core + CH for the easy majority) vs reject — with the scaling evidence.
- [ ] #3 If adopted: the construction maintains the hard-feasibility invariant throughout (no -44,100 scatter). Measured before->after on a full live solve: construction-end hard, ph2 LS budget (today 60 min), and run-to-run hard variance.
- [ ] #4 GoldenSolutionRegressionTest unchanged on hard; full DoD.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO NELL'ESITO (2026-07-27): l'obiettivo del pivot (hard-feasible pezzo-per-pezzo + box-the-rest + niente scatter-and-repair cieco) e' stato RAGGIUNTO per altra via, adottata in pieno: box-the-residual (398/406) + solve a stadi con promozione template (419) + repair deterministici gated (BoxRepair/HoleRepair/SurplusRebalance) = hard 0 doppio su train2, buchi interni 0. La costruzione resta template-first (L2), non piece-by-piece, ma il paradigma scatter-and-repair e' morto nei fatti. Archiviato; se mai il ceiling dello staged si esaurisse, ripartire da qui.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-23 06:31
---
SPIKE DONE (laptop, 2026-07-23) — VERDICT: GO, and simpler than the design feared.
---

created: 2026-07-23 06:31
---
Greedy MRV + box places 98.7% of lessons feasibly at FULL scale (10,501 lessons, 114ms); placed-hard = -8 (H17 only, H1/H3/H7/H11 = 0 by construction) vs the live CH's -44,100. Box = 141 (1.3%), the irreducible residual.
---

created: 2026-07-23 06:31
---
BACKTRACKING IS UNNECESSARY: the box stays 141 across D=0/20/100/500 (chronological undo-recent can't target blockers); 10,000 backtracks in 1.2s, no exponential wall. Viable D/U = D=0. Scaling linear (36ms@1660 -> 114ms@10501).
---

created: 2026-07-23 06:31
---
Recommended shape (Fedora): replace DayClusteredConstructionPhase.forcePlaceLeftovers (force-place residual -> -44100 scatter) with BOX-the-residual (timeslot null -> H0); add allowsUnassigned=true on Lesson.timeslot (the scatter's mechanical root cause); LS places the 1.3% box. No backtrack machinery needed.
---

created: 2026-07-23 06:31
---
Artifacts: claudedocs/design/human-faithful-construction-spike-RESULTS.md (full measures + verdict); harness src/test/java/com/school/planner/dev/HumanFaithfulConstruction.java + src/test/java/com/school/planner/bootstrap/HumanFaithfulConstructionSpikeTest.java (dev/test, NOT in the live pipeline). Fixture simplifications: no pins/Stage/rooms (matching the L2 test scope) — narrows vs production but does not change the scaling conclusion.
---
<!-- COMMENTS:END -->
