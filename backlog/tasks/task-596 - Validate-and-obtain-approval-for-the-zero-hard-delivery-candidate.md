---
id: TASK-596
title: Validate and obtain approval for the zero-hard delivery candidate
status: To Do
assignee: []
created_date: '2026-09-09 10:13'
updated_date: '2026-09-09 14:37'
labels:
  - claude-planned
  - zero-hard-recovery
  - adoption-gate
milestone: m-14
dependencies:
  - TASK-595
  - TASK-579.01
  - TASK-592
  - TASK-594
references:
  - scripts/benchmark/capture-run.sh
  - scripts/benchmark/laptop-RUNBOOK.md
  - src/main/java/com/school/planner/solver/ScoreCompat.java
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run the selected, locally proven candidate through the canonical staged production protocol. TASK-595 supplies the reviewed patch and manifest; TASK-579.01 supplies full-vector validation; TASK-592 and TASK-594 supply explicit RA/G14 dispositions. This is stricter than historical TASK-537's nonzero quasi-hard target, so that task is not reused. All required code gates precede the laptop cycle. Input changes since control capture require renewed control/bench evidence. One accepted run proves one delivery, not reliability.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Before launch, required review/tests pass and the exact build, flags, input hashes, pre-assignment, budget, and expected banners are recorded and verified.
- [ ] #2 Only the bench-qualified candidate pays the full laptop cycle; checkpoints and canonical capture are retained.
- [ ] #3 Delivered, persisted, and reloaded provider scores agree with both hard coordinates zero and no hidden constraint disabling or demotion.
- [ ] #4 Canonical per-group sanity has no red group; inventory and authoritative pins reconcile.
- [ ] #5 Canonical fill, start, holes, recurrence, cadence, and room KPIs use the same delivery; actual rendered comparison is inspected and observations recorded.
- [ ] #6 Quality limits are specified before the run; medium disagreement is explained before quality judgment.
- [ ] #7 maintainer's explicit adoption decision is recorded; any failed gate remains a blocker and is not disguised as successful delivery.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-09-09, from TASK-539.01 execution] PROTOCOL DECISION REQUIRED at this gate (finding F2): the entire measured v-series ran WITHOUT import-calendario (slotretry first launch pre-assign loaded 0 pinned lessons; v132 all 5 attempts start from the same 274-pin pre-assign state, pin-set sha 55ed4ae0a7a9ed5a), while the documented flow (laptop-test/full-test skills) includes import-calendario (3077-pin start incl. 2009 stage pins + 295 MODA lab pins). The adoption cycle must declare WHICH protocol is canonical for the school; the two start states are materially different search problems. Evidence: claudedocs/spalmone-2026-27/m14-results/CONTROL-MANIFEST-2026-09-09.md section 'Pre-construction fixtures' + REPRODUCTION-LOG.md steps 13-15. Two ready-made fixture volumes exist on the laptop (m14-precon-b = measured protocol, m14-precon = documented flow).
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Verify /simplify and high-effort code review evidence on the final candidate before any deployment.
- [ ] #2 Run final main-session wrapper tests with exit codes; long solve runs only on laptop within approved budget.
- [ ] #3 Run capture validation and independently recount full hard vector and sanity from the stored result.
- [ ] #4 Preserve the export/checkpoint and checksum-verified manifest in the campaign evidence directory.
<!-- DOD:END -->
