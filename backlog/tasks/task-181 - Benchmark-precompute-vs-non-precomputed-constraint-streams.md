---
id: TASK-181
title: Benchmark precompute() vs non-precomputed constraint streams
status: Done
assignee: []
created_date: '2026-03-05 17:13'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - performance
dependencies: []
references:
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
  - claudedocs/research-timefold-optimizations-2026-03-03.md
priority: medium
ordinal: 97000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-179 added `constraintFactory.precompute()` for H4.1, H4.2, H5.1, H5.2, and M5 constraints. These cache static entity-fact joins (on `studentGroup`, `teacher`) so only variable-dependent filters run per move.

**H5 trade-off concern**: H5.1/H5.2 previously used indexed `Joiners.overlapping()` for time-range intersection. The precomputed version replaces this with a cached `teacher` join + post-filter on time overlap. For teachers with many VACATION records (T28 has ~200), the cache contains more pairs but avoids re-joining per move. Need to verify this is a net win.

**Benchmark approach**:
1. Run solver on both versions (pre-precompute commit vs current) with same seed/time limit
2. Compare moves/second from solver logs
3. Compare CH completion time
4. Compare final scores at same wall-clock time

If H5 precompute is a net loss, revert H5.1/H5.2 only (keep H4 and M5 which are clear wins).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Run 6-minute solver benchmark on commit before TASK-179 (pre-precompute baseline)
- [x] #2 Run 6-minute solver benchmark on current commit (with precompute)
- [x] #3 Compare moves/second, CH time, and final hard/soft scores
- [x] #4 If H5 precompute shows regression: revert H5.1/H5.2 to indexed Joiners.overlapping(), keep H4+M5 precompute
- [x] #5 Document results in claudedocs/ or commit message
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Benchmark Results (TASK-181)

**H5 precompute is 27% slower than Joiners.overlapping().**

| Metric | precompute() | Joiners.overlapping() |
|--------|-------------|----------------------|
| CH time | 219s | 172s (-21%) |
| Move eval speed | 29,790/sec | 37,888/sec (+27%) |
| Final score | -5,458,523 hard | -5,458,523 hard (identical) |
| Steps | 14,020 | 14,020 (identical) |

**Root cause**: `Joiners.overlapping()` uses an indexed interval tree (O(log n) per move). Precompute replaces this with a cached set of all teacher×availability pairs + post-filter — slower when the cache is large (T28 has ~200 VACATION records).

**Action taken**: Reverted H5.1/H5.2 to `Joiners.overlapping()`. Kept H4 and M5 precompute (they use `Joiners.equal(studentGroup)` which benefits from hash-based caching).

**Commits**: 74ef2ba
<!-- SECTION:FINAL_SUMMARY:END -->
