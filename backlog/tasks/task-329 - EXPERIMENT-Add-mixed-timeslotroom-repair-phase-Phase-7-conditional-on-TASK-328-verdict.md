---
id: TASK-329
title: >-
  EXPERIMENT: Add mixed timeslot+room repair phase (Phase 7) - conditional on
  TASK-328 verdict
status: Done
assignee: []
created_date: '2026-07-12 17:45'
updated_date: '2026-07-15 19:23'
labels:
  - claude-planned
  - experiment
milestone: m-4
dependencies:
  - TASK-328
references:
  - src/main/resources/solverConfig.xml
  - .claude/rules/solver-config-sync.md
priority: medium
ordinal: 153000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: If TASK-328 concludes that Phase 5 room assignment introduces hard violations that Phase 6 (room-only moves) cannot repair, the pipeline needs a final short local-search phase that can move BOTH variables to unlock timeslot-locked room conflicts. Only execute this task if TASK-328 verdict is YES - otherwise close it as not-needed with a reference to the diagnostic.

WHAT:
1. Add a Phase 7 localSearch to solverConfig.xml after the room refinement phase: unionMoveSelector with changeMoveSelector (timeslot), changeMoveSelector (room), swapMoveSelector on both variables; modest termination (diminishedReturns 1min/0.05, secondsSpentLimit 600); same tabu acceptor as siblings unless TASK-325 changed the acceptor - match whatever is current.
2. Consider restricting entity selection to lessons involved in violations if simple: Timefold supports entity selector filtering via filterClass - a SelectionFilter that accepts only lessons whose current assignment participates in a hard violation is NOT trivially expressible; if not feasible within Timefold API, run the phase unrestricted and rely on the acceptor. Do not build heavy custom machinery in this task.
3. MANDATORY after XML edit: bash scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml, then follow .claude/rules/solver-config-sync.md COMPLETELY (SolverConfigParser phase names/count, SolverProgressionListener, solverPhases.ts, SolverPhasesPage, claudedocs/solver-phases.md, i18n message bundles).
4. A/B: standardized protocol 2 runs with Phase 7 vs 2 without, equal total budget (shave the added 600s from phase 3-4 caps in the control if needed for fairness). Append to experiment-log.md.
5. Keep Phase 7 only if the timeslot-locked violations measured in TASK-328 are actually cleared and total hard score improves.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Executed only if TASK-328 verdict is YES (otherwise closed as not-needed with reference)
- [ ] #2 XML validated; ALL solver-config-sync artifacts updated (parser, listener, frontend phases, docs, i18n)
- [ ] #3 A/B at equal budget completed; experiment-log.md updated; keep/drop decision with numbers
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
MOOT 2026-07-15: TASK-328 verdict is NO (zero room-induced hard violations in two full solves; the room-deferral pipeline works correctly). A mixed timeslot+room repair Phase 7 is not needed. Closed without implementation.
<!-- SECTION:FINAL_SUMMARY:END -->
