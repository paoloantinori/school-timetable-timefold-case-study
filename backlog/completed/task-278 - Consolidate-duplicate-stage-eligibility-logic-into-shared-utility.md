---
id: TASK-278
title: Consolidate duplicate stage eligibility logic into shared utility
status: Done
assignee: []
created_date: '2026-03-20 19:33'
updated_date: '2026-03-20 19:54'
labels:
  - claude-planned
  - refactoring
  - code-quality
dependencies: []
references:
  - 'https://github.com/anthropics/claude-code/issues/'
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Code Reuse Issues to Fix

### CRITICAL: 4 duplicate `isStageBlackout` implementations
Found in:
- `GreedyRotationAssigner.java:1278`
- `AdaptiveSlotFinder.java:415`
- `RotationTestRunner.java:486`
- `DayClusteredConstructionPhase.java:413`

All implementations use the same logic:
```java
private boolean isStageBlackout(LocalDate date, StudentGroup group, List<StagePattern> stagePatterns) {
    if (group == null || stagePatterns == null) return false;
    DayOfWeek dow = date.getDayOfWeek();
    return stagePatterns.stream().anyMatch(sp ->
        sp.studentGroup.id.equals(group.id) &&
        !date.isBefore(sp.startDate) &&
        !date.isAfter(sp.endDate) &&
        sp.dayOfWeekMask.contains(dow)
    );
}
```

### HIGH: Duplicate professionalizzanti stage eligibility logic
Found in:
- `GreedyRotationAssigner.java:1118-1121` (newly added)
- `HybridPhase1aService.java:724` (newly added)

Subtle differences exist between implementations:
- `GreedyRotationAssigner`: Handles both prof and non-prof courses with a single boolean
- `HybridPhase1aService`: Only checks prof courses, skips entirely if `allowProfessionalizzantiDuringStage=false`

## Implementation Plan

1. Create `StageEligibilityUtil` class with static methods:
   - `isStageBlackout(date, group, stagePatterns)` - centralized stage day check
   - `shouldSkipStageDay(course, group)` - professionalizzanti eligibility check
   - `canScheduleOnStageDay(course, group, date, stagePatterns)` - combined check

2. Replace all 4 duplicate `isStageBlackout` implementations with calls to `StageEligibilityUtil.isStageBlackout()`

3. Update GreedyRotationAssigner and HybridPhase1aService to use `StageEligibilityUtil.shouldSkipStageDay()`

4. Add unit tests for the new utility class

## Files to Modify
- Create: `src/main/java/com/school/planner/util/StageEligibilityUtil.java`
- Modify: `GreedyRotationAssigner.java`, `AdaptiveSlotFinder.java`, `RotationTestRunner.java`, `DayClusteredConstructionPhase.java`, `HybridPhase1aService.java`
- Create: `src/test/java/com/school/planner/util/StageEligibilityUtilTest.java`
<!-- SECTION:DESCRIPTION:END -->
