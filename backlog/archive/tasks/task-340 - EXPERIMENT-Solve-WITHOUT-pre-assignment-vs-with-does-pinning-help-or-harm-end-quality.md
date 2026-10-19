---
id: TASK-340
title: >-
  EXPERIMENT: Solve WITHOUT pre-assignment vs with (does pinning help or harm
  end quality?)
status: To Do
assignee: []
created_date: '2026-07-12 17:49'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-6
dependencies:
  - TASK-320
references:
  - claudedocs/insights/preassignment-csp-limits.md
  - claudedocs/research/solver-config-analysis-20260313.md
priority: medium
ordinal: 164000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: claudedocs/insights/preassignment-csp-limits.md explicitly recommended benchmarking the solver WITHOUT phase-2 pre-assignment to test whether Timefold handles lab placement directly - never executed. Pinning shrinks the search space but also freezes potentially suboptimal placements and fragments available slots (identified as compaction obstacle number 4 in claudedocs/research/solver-config-analysis-20260313.md). The question is empirical and has never been answered on any architecture. See roadmap doc section 2.

WHAT:
1. Arm A (control): standard protocol run (reset, pre-assign, solve) x2.
2. Arm B: reset, SKIP pre-assign entirely, solve x2 - same termination. Note: H9-class validity and rotation-pattern semantics that pre-assignment guaranteed are partially unconstrained now (M17 was removed) - so besides the scorecard, verify lab placement sanity manually: lab lessons on lab-tagged rooms (H-M2 should enforce), acconciatura on days T28 can attend (H5 should enforce via VACATION records), and report whether weekly-recurrence of lab sessions is lost (expected - quantify how scattered lab sessions become: distinct days-of-week per lab course).
3. Compare scorecards plus the lab-scatter metric. The trade-off to surface: raw score vs schedulability of human-required rotation patterns.
4. Verdict options to state explicitly: keep full pre-assignment / keep only deterministic rotations (acconciatura) and let the solver place other labs / drop pre-assignment. Append to experiment-log.md.
5. This is a measurement task - do NOT change the production pipeline default here; open a follow-up if the verdict says change it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Both arms run twice at equal termination through the standardized protocol
- [ ] #2 Lab-scatter metric (distinct days-of-week per lab course) and lab placement sanity checks reported for arm B
- [ ] #3 Explicit verdict among the three stated options; experiment-log.md updated; production default unchanged
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): esperimento vacuo sull'architettura attuale: HybridPhase1a e' no-op esplicito con LabRotation.count()==0 e generateLabRotations() e' intenzionalmente vuoto dal TASK-364 - i due bracci sarebbero identici by construction.
<!-- SECTION:NOTES:END -->
