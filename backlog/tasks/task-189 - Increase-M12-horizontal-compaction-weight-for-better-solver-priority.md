---
id: TASK-189
title: Increase M12 horizontal compaction weight for better solver priority
status: Done
assignee: []
created_date: '2026-03-07 07:06'
updated_date: '2026-03-09 19:50'
labels:
  - solver
  - constraints
  - compaction
  - claude-planned
dependencies: []
priority: high
ordinal: 93000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Increase the weight of M12 (Horizontally compact) constraint to make compaction a stronger priority during solving.

**Current State:**
- M12 uses `HardMediumSoftScore.ONE_MEDIUM` (weight = 1)
- M12 accounts for 76% of medium penalty (-554,663)
- Average penalty per lesson: ~80 days beyond grace period

**Proposed Changes:**
1. Change M12 from `ONE_MEDIUM` to a custom score with weight 100-500
2. Evaluate impact on:
   - Solver convergence time
   - Hard constraint satisfaction (ensure 0 hard is maintained)
   - Final M12 penalty reduction

**Acceptance Criteria:**
- [ ] M12 weight increased to at least 100x current value
- [ ] Benchmark comparison shows M12 penalty reduced by at least 50%
- [ ] Solver still reaches 0 hard within reasonable time
- [ ] Document weight change in claudedocs/solver-configurations.md

**Implementation Notes:**
Consider using `HardMediumSoftScore.of(0, 100, 0)` or `HardMediumSoftScore.of(0, 500, 0)` depending on desired intensity. May need to balance against M13 which already has weight 500.

**References:**
- claudedocs/research-timefold-compaction-constraint-tuning-2026-03-07.md
- Timefold docs: "Give each constraint weight a default value... notice how it defaults the 'Content conflict' constraint as ten times more important"
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by TASK-191 (hybrid approach includes both weight increase and compaction phase)
<!-- SECTION:FINAL_SUMMARY:END -->
