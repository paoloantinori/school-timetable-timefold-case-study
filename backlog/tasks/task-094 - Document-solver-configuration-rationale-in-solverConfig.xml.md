---
id: TASK-094
title: Document solver configuration rationale in solverConfig.xml
status: Done
assignee: []
created_date: '2026-02-23 17:25'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - documentation
dependencies: []
priority: medium
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices recommend documenting solver configuration choices. The planner's `solverConfig.xml` has some comments (phase names, weight explanations) but lacks rationale for key numerical parameters.

**Current undocumented parameters**:

| Parameter | Value | Location | Question |
|-----------|-------|----------|----------|
| `entityTabuSize` | 5 (Phase 3), 7 (Phase 4-5) | `<acceptor>` | Why 5 vs 7? Benchmark result? |
| `lateAcceptanceSize` | 200 | Phase 3 `<acceptor>` | Why 200? Tried other values? |
| `acceptedCountLimit` | 1 (Phase 3), 1000 (Phase 4-5) | `<forager>` | Why such a drastic difference? |
| `minimumSubPillarSize` | 2 | Phase 4 pillar selector | Why minimum 2? |
| `maximumSubPillarSize` | 5 | Phase 4 pillar selector | Why maximum 5? |
| `fixedProbabilityWeight` | Various (0.5-5.0) | Move selectors | Were these benchmarked? |
| Global `secondsSpentLimit` | 28800 (8h) | Top-level | Why 8 hours? Safety net? |
| `unimprovedSecondsSpentLimit` | 3600 (Phase 3-4), 600 (Phase 5) | Per-phase | Why 1h vs 10min? |

**What to document**: Add XML comments explaining:
1. **Rationale**: Why this value was chosen (benchmark result, rule of thumb, Timefold docs recommendation)
2. **Trade-off**: What happens if increased/decreased
3. **Source**: Benchmark date/branch if applicable

**Format**: XML comments above each parameter block. Keep comments concise (1-2 lines per parameter).

**Example**:
```xml
<!-- entityTabuSize=5: Smaller tabu during compactness-focused phase allows more 
     timeslot exploration. Benchmarked 2025-12: 5 outperformed 3 and 7 on hard score. -->
<entityTabuSize>5</entityTabuSize>
```

**Propagation**: `solverConfig.xml` is architecture-specific — each branch has a different config. Document each branch's config separately.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All numerical solver parameters have XML comments explaining rationale
- [x] #2 Comments mention benchmark results or design reasoning where applicable
- [x] #3 Comments are concise (1-2 lines each)
- [x] #4 No functional changes to solver behavior
- [ ] #5 Documentation added on all 3 active branches (adapted per config)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-27: All 3 solver XML files documented on feature/cartesian-block-entity branch only. Propagation to other branches deferred (criterion #5 not met).
<!-- SECTION:NOTES:END -->
