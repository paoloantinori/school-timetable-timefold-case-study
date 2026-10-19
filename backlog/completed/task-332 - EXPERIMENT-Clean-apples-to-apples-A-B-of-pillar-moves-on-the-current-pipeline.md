---
id: TASK-332
title: 'EXPERIMENT: Clean apples-to-apples A/B of pillar moves on the current pipeline'
status: Done
assignee: []
created_date: '2026-07-12 17:46'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - experiment
milestone: m-4
dependencies:
  - TASK-280
references:
  - src/main/resources/solverConfig.xml
  - claudedocs/research/pillar-moves-benchmark-results-20260317.md
priority: medium
ordinal: 230
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The only pillar-moves benchmark (claudedocs/research/pillar-moves-benchmark-results-20260317.md) was CONFOUNDED: the baseline stalled in Phase 3 from an unrelated bug, so the two configs stopped at different phases and the comparison is invalid - yet pillar moves were merged on that basis and sit in Phase 3 today (pillarChange weight 2, pillarSwap weight 1). Their actual value on the current 6-phase, Timefold 2.x architecture is unknown; pillar moves are also O(n squared) to generate, so if they do not help they cost throughput. Related open task: TASK-130 (benchmark pillar maxSubPillarSize) - absorb it. See roadmap doc section 2, item 1.

WHAT:
1. Variant A = current config (with pillar selectors in Phase 3). Variant B = identical config with the two pillar selectors removed and their probability weight redistributed to change/swap proportionally. Equal termination.
2. Two standardized protocol runs per variant.
3. If A wins, run variant C: tune maxSubPillarSize (e.g. limit to 4) to cap pillar generation cost, and compare throughput (moves per second from logs) as well as score.
4. Validate XML after every edit; append all rows to experiment-log.md; final config reflects the measured winner; follow .claude/rules/solver-config-sync.md if the phase description shown in the UI changes.
5. Close TASK-130 with a cross-reference to this result.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A/B at equal termination, 2 runs per variant, via standardized protocol
- [ ] #2 Throughput (moves per second) recorded alongside scores
- [ ] #3 experiment-log.md updated; winner applied; TASK-130 closed with cross-reference
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05): l'A/B pulito dei pillar moves e' coperto dal synthesis (5 knob medium flat, ACL non-robust, HoleRepair al ceiling); la domanda sperimentale e' chiusa con verdetto esausto. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
