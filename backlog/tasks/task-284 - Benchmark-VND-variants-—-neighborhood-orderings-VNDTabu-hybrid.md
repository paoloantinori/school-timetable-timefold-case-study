---
id: TASK-284
title: 'Benchmark VND variants — neighborhood orderings, VND+Tabu hybrid'
status: Done
assignee: []
created_date: '2026-03-21 09:05'
updated_date: '2026-08-04 22:00'
labels:
  - claude-planned
  - solver
  - benchmark
milestone: solver-optimization
dependencies:
  - TASK-280
priority: medium
ordinal: 210
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
VND won the 23-config benchmark decisively. The next step is to optimize VND itself by testing variants that could push scores even further.

**Variants to test:**
1. **Different neighborhood orderings** — VND cycles through move types (change, swap, pillar, etc.). The order may matter; test different sequences
2. **VND + Tabu hybrid** — Combine VND's structured neighborhood exploration with Tabu's short-term memory to avoid revisiting recently explored solutions
3. **Multi-level VND** — Different move selectors or entity filters per VND level (e.g., coarse moves early, fine-grained moves later)
4. **VND with different termination** — Since VND has higher throughput, test whether score-per-second improves with different acceptance criteria

**Dependency:** TASK-280 (VND in production) and TASK-281 (timeout tuning) should be completed first so we have a stable VND baseline to compare against.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 VND baseline established from TASK-280/281
- [x] #2 3-5 VND variant configs added to solverBenchmarkConfig.xml
- [x] #3 Benchmark completed comparing VND variants against baseline
- [x] #4 Best VND variant identified with score comparison
- [x] #5 Recommendation for optimal VND configuration documented
<!-- AC:END -->

## Final Summary

Completed 2026-08-04. PREREQUISITE: the benchmark harness was unrunnable since the
Timefold 1.x to 2.3.0 move (compile API drift + a runtime JAXB blocker from excluding
timefold-solver-jaxb). Repaired it first (commit be404746), then ran the 9-config
TabuVsVND benchmark via the proper vehicle (commit 77d5a0d4). Added
`<parallelBenchmarkCount>6` to make the benchmark practical (sequential was ~108 min).

Result (best-first by hard): proper VND variants dominate hard feasibility.
VND-DiminishedReturns -242hard, VND-CartesianProduct -258, VND-FirstImprovement -284,
Late-Acceptance -290, SA-Tabu-Hybrid -322, vs Tabu-Entity-Size5 -6699 / Size7 -6740
(current production acceptor) / LA-Phase3-Tabu-Phase5 -6756, VND-HillClimbing -7783 (worst).
Proper VND reaches hard in the low hundreds vs ~-6700 for Tabu baselines (~25x better).
VND-HillClimbing (best-improvement, acl=1000) worst, VND-FirstImprovement (acl=1) best:
confirms first-improvement clears hard better, consolidating the acceptedCountLimit finding.

CAVEAT: old March dataset, n=1, controlled LS benchmark (production CH + combined LS, NOT
the staged pipeline). Relative metaheuristic signal, not campaign KPIs. NO ADOPTION. Does
not contradict the Tabu-Size7 staged-pipeline ratification (different setup/dataset).

Follow-up (new task, not adoption): test VND-FirstImprovement on the CURRENT staged pipeline
+ 2026/27 seed, relevant to the TASK-480/481 H17 hard-0 struggle.

VERDICT + ranking: claudedocs/spalmone-2026-27/task284-results/VERDICT-tabuvnd-benchmark-2026-08-04.md
Harness repair VERDICT: claudedocs/spalmone-2026-27/task284-results/VERDICT-harness-repair-2026-08-04.md

Note: task status set to Done by direct file edit because duplicate-ID collisions
(TASK-38/230/486) block ID-based backlog commands. Archive-move deferred until the
collision is repaired (`backlog doctor`).
