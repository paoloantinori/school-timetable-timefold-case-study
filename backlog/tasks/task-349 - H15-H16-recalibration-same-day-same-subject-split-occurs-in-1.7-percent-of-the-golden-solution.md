---
id: TASK-349
title: >-
  H15/H16 recalibration: same-day same-subject split occurs in 1.7 percent of
  the golden solution
status: Done
assignee: []
created_date: '2026-07-16 09:35'
updated_date: '2026-07-18 01:03'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies:
  - TASK-341
  - TASK-347
priority: medium
ordinal: 173000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: analysis.md section 4.1: the humans split a same-day same-subject pair into non-adjacent runs 137 of 7908 times (e.g. Scienze at slots 3 and 5). H15 as HARD rejects the golden solution 137 times. Also 27 teaching blocks cross the lunch break, which naive adjacency treats as a split.

WHAT:
1. First make H15/H16 use the shared adjacency utility from TASK-341 so lunch-crossing blocks are NOT counted as splits; re-measure on the golden profile - the true violation count should drop below 137 (110 morning-only splits expected).
2. Then decide with numbers: demote H15 to medium with a weight that tolerates rare splits, OR keep hard and accept that our solutions are stricter than human ones (defensible since the solver CAN comply; the school never complained about consecutiveness). Write the decision + rationale into the task and CLAUDE.md constraint table.
3. Update ConstraintVerifier tests to the decided semantics.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Lunch-crossing blocks no longer counted as H15/H16 violations; measured count on golden documented
- [x] #2 Tier/weight decision made with numbers and reflected in code, tests and CLAUDE.md constraint table
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANSWER 2026-07-17 (Q18): the no-adjacent rule is SOFT with whitelist = labs + Informatica + Disegno (+ Competenze Digitali). H15/H16 recalibration can proceed without further school input; keep H15's must-be-adjacent for whitelisted subjects, and the 1h/day preference lives in M21 (TASK-365).

DECISION 2026-07-18 (implemented): lunch-crossing handling (item 1) was already DONE prior to this task, since TASK-341 - `sameDaySameSubjectMustBeConsecutive`/H16a/H16b already use `TimeslotSequence.slotIndexOf`/`isAdjacent`, confirmed by the existing `..._notPenalized_acrossLunchBreak` tests for both. golden-audit.md's measured H15 was 98 matches/-9420 hard (not the task brief's predicted 110-137) - already lunch-aware, so no further drop was possible.

Item 2 decision: **H15 kept HARD, made break-level pinned-exempt** (option (b) from the task brief, not a demotion to medium). A break (gap between two same-day same-subject lessons) is exempt only when BOTH the lesson before and the lesson after are `pinned` - the golden/historical import case. This is NOT the same shape as M20/H21's per-entity `!lesson.pinned` filter (H15 is pairwise/aggregate, not per-entity); see the constraint's own javadoc and CLAUDE.md's "H15/H16 recalibration (task-349)" design-decision section for the full mechanism and why a flat medium demotion was rejected. H16a/H16b measured 0 matches on the golden (both before and after, re-confirmed live) - no recalibration needed, left as plain hard.

Golden measurement (`GoldenSolutionRegressionTest`): H15 -9420 hard/98 matches -> **0 hard/0 matches** (moved to the zero-tolerance assertion group, alongside M20/H21). H16a/H16b: 0/0, newly added as explicit zero-tolerance assertions.

Code-review (high) surfaced one confirmed real bug from this change: `ScheduleService.addH15Violations` (the `/api/schedule/analysis/lessons` per-lesson drill-down) independently re-implements H15's gap detection and had no pinned-awareness, so it would have kept reporting the golden's 98 splits in the UI even though the score now reports 0 - fixed with the same both-sides-pinned exemption, covered by 2 new `GroupByViolationAnalysisTest` cases. Known, deliberately NOT fixed (out of scope, no correctness regression): `PreAssignmentValidator.wouldViolateH15` and `HybridPhase1aService` have their own independent H15 gap checks used during pre-assignment CSP, which remain stricter than the recalibrated constraint (they still refuse to pin a lesson non-adjacent to another pinned same-subject lesson) - this is conservative/safe, not a hole, and orthogonal to golden import (which bypasses pre-assignment). Flag for a future task if the school wants pre-assignment to also tolerate pinned-pinned splits.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
H15 stays HARD with a break-level pinned exemption (both sides pinned = historical/manual split, excused; any solver-placed split fires). Golden -9420/98 -> 0/0 (zero-tolerance guard pin); H16a/b measured 0/0 and pinned. Lunch-crossing was already handled by TASK-341's shared adjacency. Drill-down mirror (addH15Violations) aligned with the same both-pinned check — in-agent code-review catch. PreAssignmentValidator's stricter internal H15 check left as-is (conservative, flagged). Full DoD; 553/553.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
