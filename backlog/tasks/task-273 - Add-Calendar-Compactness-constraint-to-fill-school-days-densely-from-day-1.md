---
id: TASK-273
title: Add Calendar Compactness constraint to fill school days densely from day 1
status: Done
assignee: []
created_date: '2026-03-19 10:06'
updated_date: '2026-03-20 05:31'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Our compaction constraints (H17 vertical, M20 horizontal) address daily density but miss calendar density entirely. The solver spreads lessons across the full school year instead of filling days densely from day 1. The human planner achieves dense fill because they work chronologically; the solver has zero temporal bias.

## Root Cause
1. **No temporal bias**: Sep 1 and Mar 30 are equally valid for any unassigned lesson
2. **S9 (Dilute hours)** actively SPREADS lessons apart — penalizes >1 lesson/subject/week, the #1 antagonist to dense fill
3. **S2 (Subject distribution)** reduces daily density by pushing subjects apart

## Solution
1. **New constraint: Calendar Compactness** — penalize empty school days within each group's calendar span (firstDate → lastDate)
   - Metric: (span_days - used_days) per student group
   - Tier: Medium (~500/empty day)
   - Must exclude: pinned lessons, stage blackout days, holidays
   
2. **Rebalance S9** — reduce weight from 1→0.1 or replace with max-per-week cap (e.g., max 3h/subject/week) instead of penalizing >1h/week

## Implementation
1. Prototype in compaction-only mode for isolation testing
2. Benchmark: measure empty days within span before/after
3. Tune weight balance vs S9
4. Integrate into full constraint set

## Files to modify
- `SchoolScheduleConstraintProvider.java` — new constraint method
- `BaseConstraintProvider.java` — if constraint belongs in base
- `SchoolScheduleConstraintProviderTest.java` — constraint verifier tests
- Compaction-only mode list — add new constraint
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Completed (2026-03-20)

### M22: Calendar Compactness (new hard constraint)
- Penalizes empty school days within each group's calendar span
- Uses `countDistinct(days) - ceil(placedHours / dailyQuota)` as sparse-day count
- Hard tier — empty days between first and last lesson are unacceptable
- Exemptions: pinned lessons, G14 group

### M20: Daily Quota Fill (upgraded)
- Changed from hardcoded 4h minimum to per-group `maxHoursPerDay` quota
- Penalty = quota shortfall per day (was: fixed 4h threshold)

### S9: Dilute Hours (disabled)
- Commented out because it actively opposes M22 calendar compactness
- Restoration plan captured in task-274 (max-per-week cap approach)

### M18: 5-hour group start slot (optimized)
- Replaced `toList()` with `min()` collector for O(1) incremental scoring
- Eliminates full group rescan per entity placement during CH

### Benchmark config
- Doubled time limits: 900→1800s, 180→600s (M22 hard requires more optimization)
- Fixed whitespace in VND config

### Key technical decision
Initial `compose(min, max, countDistinct)` approach caused CH to take 300s+ and only place ~4,600 of 10,550 entities. Replaced with simpler `countDistinct + count` using `ceil(hours/quota)` as minimum expected days — fully incremental and avoids weekend-counting bugs.
<!-- SECTION:FINAL_SUMMARY:END -->
