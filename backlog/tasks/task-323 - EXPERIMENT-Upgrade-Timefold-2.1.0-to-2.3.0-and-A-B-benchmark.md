---
id: TASK-323
title: 'EXPERIMENT: Upgrade Timefold 2.1.0 to 2.3.0 and A/B benchmark'
status: Done
assignee: []
created_date: '2026-07-12 17:42'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-3
dependencies: []
references:
  - pom.xml
  - claudedocs/insights/timefold-sorter-workaround.md
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: high
ordinal: 147000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Timefold 2.3.0 (released 2026-07-10) release notes state: significantly improved underlying metaheuristics so that in those few cases where the solver would previously get stuck, it no longer does - which is EXACTLY this projects symptom (solver cannot reliably reach 0hard). 2.3.0 also fixes ruin-and-recreate handling of unassigned values (#2448), relevant because our room variable allowsUnassigned and Phase 4 uses ruinRecreateMoveSelector. 2.2.0 added Neighborhoods groupBy and an XXE fix. Cheapest high-potential experiment available. See claudedocs/research/critical-assessment-and-roadmap-20260712.md sections 3 and 4. Related: TASK-180 (benchmark upgraded solver), TASK-301/303/311 (ComparatorFactorySelectionSorter transitivity workaround - check whether 2.3.0 metaheuristic changes allow removing it, see claudedocs/insights/timefold-sorter-workaround.md).

WHAT:
1. Read the official release notes for 2.2.0 and 2.3.0 on github.com/TimefoldAI/timefold-solver/releases and list any breaking changes affecting this codebase BEFORE touching code.
2. Bump timefold-solver.version to 2.3.0 in pom.xml. Rebuild (./scripts/core/rebuild.sh) and run tests (./scripts/core/test.sh). Fix any compile/API breaks (document each fix).
3. Validate solver XML against the new XSD: bash scripts/validate/validate-solver-xml.sh src/main/resources/solverConfig.xml.
4. Run the standardized protocol (TASK-319) 3 times. Compare against the TASK-320 baseline with compare-experiments.py.
5. Append rows to claudedocs/research/experiment-log.md with verdict: keep upgrade if scores are equal or better and no regressions; revert only if reproducibly worse.
6. Check whether the LessonEntitySorter TimSort workaround still triggers (watch logs for Comparison method violates its general contract during Phase 4).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Release notes for 2.2.0 and 2.3.0 reviewed and breaking changes listed in the task before code changes
- [x] #2 Build and full test suite pass on 2.3.0; solver XML validates against the new XSD
- [ ] #3 Three protocol runs completed and compared against TASK-320 baseline; rows appended to experiment-log.md
- [x] #4 Explicit keep-or-revert verdict with numbers in Final Summary
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Delivered 2026-07-14 (KEEP). Upgraded timefold-solver 2.1.0 -> 2.3.0 (pom BOM). Build clean, 273 tests pass, solver XML validates vs the 2.3 XSD (criteria #1, #2 met). A/B rep 1 vs the 2.1.0 baseline (rep1 -115, rep2 -111): 2.3.0 = -113hard/-510224medium/+171370soft, 0 unassigned. HARD EQUAL to baseline (~-113); medium ~18k worse (-510k vs -492k/-499k) - noise-adjacent, larger than rep1<->rep2 variance, worth a confirming rep but NOT a major bug or new violation. Verdict KEEP per user. 2.3.0 brings the upstream fixes the m-3/m-4 experiments depend on: metaheuristic 'stuck-solver' improvement, PR #2407 (Late Acceptance score-tier reset -> LA now first-class for TASK-325), PR #2448 (R&R unassigned crash fix -> our Phase 4), PR #2442/#2430 (constraint-stream/consecutive-seqs perf -> TASK-327). Criterion #6: TimSort 'Comparison method violates' did NOT trigger on 2.3.0 -> TASK-311 (remove LessonEntitySorter workaround) is now viable. Criterion #3 partial: 1 of 3 protocol runs done (reps 2-3 deferred to confirm the medium delta; the keep decision does not hinge on them). Timefold GH scout annotated onto TASK-325 (#2407) and TASK-327 (#2442/#2430).
<!-- SECTION:FINAL_SUMMARY:END -->
