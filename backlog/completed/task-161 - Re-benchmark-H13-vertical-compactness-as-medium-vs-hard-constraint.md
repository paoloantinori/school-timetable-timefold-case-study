---
id: TASK-161
title: Re-benchmark H13 vertical compactness as medium vs hard constraint
status: Done
assignee: []
created_date: '2026-03-04 05:27'
updated_date: '2026-07-14 05:31'
labels:
  - claude-planned
  - performance
  - benchmark
dependencies:
  - TASK-160
references:
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java:255-261
    (H13 constraint)
  - CLAUDE.md — Design Decisions section (S5 weight=500 benchmark)
  - claudedocs/research-timefold-optimizations-2026-03-03.md (section on H13)
priority: medium
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Rationale

H13 (Vertically compact) is currently a **hard constraint** that penalizes intra-day gaps for student groups. It replaced the earlier S5 (soft, weight=500) approach. However, CLAUDE.md documents an old benchmark showing **soft S5 gave 40% better hard score** than hard H13:

| Metric | Hard (H10/H13) | Soft (S5=500) |
|--------|----------|---------------|
| CH time | 231s | 155s |
| Final hard score | -1,975k | -1,185k |

The solver config has changed significantly since that benchmark (5-phase config, cartesian moves, entity-level VRPs, block entity architecture). The old results may no longer apply, but the concern remains valid: **H13 at hard level competes with H1/H2/H3 for solver attention.**

In the 7h53m run, H13 contributed only **-55 hard** from 1768 matches (mostly resolved). But during Phase 3, the solver spends significant effort avoiding gaps — time that could be spent on teacher/room/student conflicts instead.

A **medium constraint** (not soft — medium gives priority over S-constraints but doesn't block conflict resolution) could be the sweet spot.

## Proposed Experiment

Create an `experiment/h13-medium` branch from `feature/cartesian-block-entity` and change one line:

**File: `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java`**

```java
// Before (hard):
.penalize(HardMediumSoftScore.ONE_HARD,
        (group, date, lessons) -> countInternalGaps(lessons))
.asConstraint("H13: Vertically compact");

// After (medium):
.penalize(HardMediumSoftScore.ONE_MEDIUM,
        (group, date, lessons) -> countInternalGaps(lessons) * 100)  // weight 100 to dominate other mediums
.asConstraint("M13: Vertically compact");
```

### Weight rationale
Current medium constraints: S4=1000 per match, M17=1, M3=1, M4=~7. Using weight=100 per gap makes vertical compactness a strong medium preference that dominates M3/M4/M17 but doesn't compete with hard H1/H2/H3.

## Benchmark Protocol

Run both configs for the same duration (suggest 4h each, after TASK-160 config changes):

1. **Control**: current H13-hard config → record final score
2. **Experiment**: M13-medium config → record final score

Compare:
- Hard score (should improve with medium — fewer hard constraints competing)
- Medium score (will be worse with medium — gaps now appear here)
- Gap count (how many gaps remain in each)
- Phase transition times

## Verification

- All 193 tests pass with medium constraint
- Benchmark comparison documented
- Decision recorded: keep hard or switch to medium
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Experiment branch created with H13 changed to medium weight 100
- [ ] #2 Both control and experiment benchmarked for equal duration (4h minimum)
- [ ] #3 Comparison report documents: hard score, medium score, gap count, phase transitions
- [ ] #4 Decision documented: whether to keep H13-hard or adopt M13-medium
- [ ] #5 All 193 tests pass on experiment branch
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED by TASK-330. H13 (Vertically compact) was renamed/promoted to H17 in the current constraint set (see CLAUDE.md canonical table; S5/H10/H13 naming is dead). TASK-330 re-scopes this exact question — H17 hard vs heavy-medium — against the current 6-phase pipeline, under the standardized protocol (TASK-319).
<!-- SECTION:FINAL_SUMMARY:END -->
