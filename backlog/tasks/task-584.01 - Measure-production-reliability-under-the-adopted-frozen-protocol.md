---
id: TASK-584.01
title: Measure production reliability under the adopted frozen protocol
status: To Do
assignee: []
created_date: '2026-09-09 10:13'
labels:
  - claude-planned
  - zero-hard-recovery
  - reliability
milestone: m-14
dependencies:
  - TASK-596
references:
  - claudedocs/spalmone-2026-27/task584-results/VERDICT-raw.md
documentation:
  - >-
    backlog/docs/doc-001 -
    Zero-hard-recovery-evidence-and-recommended-sequence-2026-09-09.md
parent_task_id: TASK-584
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up to TASK-584 after the candidate passes TASK-596. Measure the actual adopted production wrapper including winner-tail behavior, rather than extrapolating the historical single-attempt forced-GRASP/SurplusHarvest-OFF batch. Freeze model, inputs, build, flags, and attempt protocol. Keep historical m-13 evidence intact and append a dated, scoped reliability conclusion. This task plans and measures an approved budget; it does not authorize unlimited live runs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Before launching, protocol specifies production configuration, sample size, uncertainty method, success definition, seeds, stopping rules, and maintainer-approved compute budget.
- [ ] #2 Every run retains provenance, actual exit/status, full hard-vector concordance, sanity result, elapsed cost, and attempt count; failures are not omitted.
- [ ] #3 Observed production delivery success rate and uncertainty are reported directly; any modeled multi-start probability states independence assumptions and limitations.
- [ ] #4 Results from different models, inputs, builds, or protocols are not pooled silently.
- [ ] #5 The report states whether evidence supports the required reliability level or remains inconclusive; zero observed failures is not called a guarantee.
- [ ] #6 A dated scope-qualified conclusion is linked from m-14 and recorded with the historical TASK-584/m-13 evidence without rewriting old results.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Review the statistical protocol before the budgeted laptop campaign; no unreviewed code deployment.
- [ ] #2 Run applicable main-session verification commands and record exit codes; code changes require /simplify and high-effort review.
- [ ] #3 Independently recompute statistics from raw per-run records and verify persisted links and artifacts.
<!-- DOD:END -->
