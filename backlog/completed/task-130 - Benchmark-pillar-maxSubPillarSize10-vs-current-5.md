---
id: TASK-130
title: Benchmark pillar maxSubPillarSize=10 vs current 5
status: Done
assignee: []
created_date: '2026-02-27 10:29'
updated_date: '2026-07-14 05:31'
labels:
  - benchmark
  - solver
  - research
  - claude-planned
dependencies: []
priority: low
ordinal: 56000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run A/B benchmark comparing current `maximumSubPillarSize=5` with `maximumSubPillarSize=10` to validate if the additional exploration justifies the computational cost.

**Test matrix:**
| Test | maxSubPillarSize | Duration |
|------|------------------|----------|
| Baseline | 5 | 10 min |
| Test A | 10 | 10 min |

**Success criteria:**
- 5% or better hard score improvement
- Soft score should not degrade significantly
- Solve time should not increase by more than 20%

**Note:** Computational cost grows exponentially (2^5=31 vs 2^10=1,023 sub-pillars per pillar).

**Reference:** `claudedocs/research-pillar-move-size-2026-02-27.md`
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED by TASK-332. A clean apples-to-apples pillar-moves A/B (incl. sub-pillar size) on the current pipeline, under the standardized protocol. This task's ad-hoc 10-min protocol with no shared baseline is exactly the methodological problem the roadmap (TASK-319/320) fixes.
<!-- SECTION:FINAL_SUMMARY:END -->
