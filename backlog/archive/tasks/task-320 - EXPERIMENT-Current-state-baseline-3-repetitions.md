---
id: TASK-320
title: 'EXPERIMENT: Current-state baseline, 3 repetitions'
status: To Do
assignee: []
created_date: '2026-07-12 17:41'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-2
dependencies:
  - TASK-319
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
  - claudedocs/research/dayclustered-phase-fix-20260313.md
priority: high
ordinal: 144000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: No benchmark exists for the CURRENT architecture (6-phase pipeline + RuinRecreate + diminishedReturns + Timefold 2.1, all introduced May-Jul 2026). Every documented number predates these changes. Without a baseline, no experiment in m-3/m-4/m-5/m-6 can be judged. See claudedocs/research/critical-assessment-and-roadmap-20260712.md section 1.

WHAT: Run the standardized protocol (TASK-319) three times on the current unmodified configuration, same dev profile, no code or config changes. Then:
1. Create claudedocs/research/experiment-log.md with a header table (date, task, config delta, hard, medium, soft, unassigned count, avg lessons/day worst group, wall-clock, verdict) and add one row per run plus a mean row.
2. Record run-to-run variance. If hard-score variance across the 3 runs exceeds 20 percent of the mean, note that all future experiments need 3+ repetitions to be meaningful; if smaller, 2 repetitions may suffice.
3. In the Final Summary, state the current numbers plainly: best/mean hard, medium, soft, unassigned lessons, avg lessons/day for each student group vs the spalmone targets (G01 5.53, see claudedocs/research/dayclustered-phase-fix-20260313.md for per-group targets), and which constraints dominate each score level.

This baseline becomes the reference for every A/B comparison. Do not change any configuration in this task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Three scorecard JSON files exist in local/experiments/ from unmodified current config
- [ ] #2 claudedocs/research/experiment-log.md created with 3 run rows plus mean row and variance note
- [ ] #3 Final Summary states per-constraint dominant contributors at hard, medium and soft levels
- [ ] #4 Per-group avg lessons/day compared against spalmone targets
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): eseguito in sostanza il 2026-07-14 (experiment-log: rep1/rep2, variance 3.5% < soglia 20%) su un'architettura ormai superata; il ruolo di baseline oggi e' dei protocolli val*/capture-run.
<!-- SECTION:NOTES:END -->
