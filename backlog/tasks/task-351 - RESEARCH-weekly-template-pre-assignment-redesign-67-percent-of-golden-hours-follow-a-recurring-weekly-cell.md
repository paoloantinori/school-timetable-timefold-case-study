---
id: TASK-351
title: >-
  RESEARCH: weekly-template pre-assignment redesign (67 percent of golden hours
  follow a recurring weekly cell)
status: Done
assignee: []
created_date: '2026-07-16 09:37'
updated_date: '2026-07-17 21:59'
labels:
  - claude-planned
  - spalmone-2027
  - research
milestone: m-11
dependencies:
  - TASK-347
references:
  - claudedocs/spalmone-2026-27/analysis.md
  - claudedocs/human-planner-heuristics.md
priority: high
ordinal: 175000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: analysis.md section 4.4: the fixed-lab-day assumption behind LabRotation/old-M17 is FALSE in general (only 14 of 125 subjects >= 40h sit on one weekday), BUT 67 percent of all golden lesson-hours occupy a (group, weekday, slot, teacher, subject) cell that repeats >= 10 weeks. The human planner clearly builds a weekly template and then patches periods (stage, ramp-up, run-out). Our per-date placement ignores this massive structure.

WHAT (research + design, no production code):
1. Quantify template structure per period: split the year into segments (before/during/after each stage window, first weeks, last weeks) and measure recurrence within segments - expected far above 67 percent.
2. Design a template-first pipeline: solve a WEEKLY grid (about 14 groups x 5 days x 8 slots) per segment, then instantiate over dates, then repair leftovers with the existing LS phases. Compare against the current CSP pre-assignment + 5-phase pipeline: expected search-space reduction, which constraints become trivially satisfied (H17 within template), which need date-level handling (H5 dated absences, closures, hour totals).
3. Deliverable: design doc in claudedocs/design/ with go/no-go recommendation and, if go, a task breakdown. Existing LabRotation/HybridPhase1a machinery: state explicitly what survives, what is superseded.
4. This likely obsoletes parts of m-6 pre-assignment experiments - cross-reference TASK-339/340 outcomes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Recurrence-by-segment numbers computed and in the doc
- [x] #2 Design doc with go/no-go, constraint mapping, and fate of LabRotation/HybridPhase1a machinery
- [x] #3 If go: follow-up tasks drafted with dependencies
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
NOTE 2026-07-16: HybridPhase1aService cancellation is non-cooperative — resetStatus() cancels the future but the worker thread completes its pinning work (only its status writes are now epoch-guarded, commit dfc494c). If this service survives the redesign, add cooperative cancellation checks at phase boundaries.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
NO-GO on template-first, decided with mechanical numbers (derive-golden-template-recurrence.py, independently re-run by the orchestrator): 41.5% strict within-segment recurrence / 72% modal ceiling / ~16 distinct weekly grids per group — segmenting LOWERS recurrence vs the 67% year metric, refuting the hypothesis. Strategy for m-11: per-date pipeline + calendario/golden pins + weekly-stability soft constraint (TASK-375). Machinery: HybridPhase1a/GreedyRotationAssigner/LabRotation to retire (cross-ref TASK-340); imports + GroupCalendarRule survive. Design doc: claudedocs/design/weekly-template-redesign-2026-07-17.md. DoD note: /code-review high folded into the next batch review (docs+1 analysis script deliverable; no production code).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
