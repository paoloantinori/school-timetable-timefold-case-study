---
id: task-093
title: 'M4: Evaluate replacing toList() with countDistinct() in labRoomTagAffinity'
status: To Do
assignee: []
created_date: '2026-02-23 17:25'
updated_date: '2026-07-25 05:55'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
dependencies: []
priority: medium
ordinal: 21562.5
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices audit flagged `toList()` usage in M4 (`labRoomTagAffinity`). This constraint groups lab blocks by room-tag+student-group+day, then counts distinct subjects and computes gap minutes. The `toList()` collector is used because the lambda needs both the distinct-subject count (for filtering) and the gap-minute calculation (for penalty).

**Current code** (lines 549-562):
```java
.groupBy(
    block -> block.course.requiredRoomTags.get(0) + "|" + block.getStudentGroup().id,
    block -> block.getDate(),
    toList())
.filter((key, date, blocks) -> {
    long distinctSubjects = blocks.stream()
            .map(LabBlock::getSubject)
            .distinct()
            .count();
    return distinctSubjects >= 2;
})
.penalize(HardMediumSoftScore.ONE_MEDIUM,
        (key, date, blocks) -> calculateBlockGapMinutes(blocks))
```

**Analysis**: Unlike H7 and S9, this is NOT a simple size check. The `toList()` is needed because:
1. The filter checks `distinctSubjects >= 2` (needs subject access)
2. The penalty calls `calculateBlockGapMinutes(blocks)` which sorts blocks by start time and computes temporal gaps

**Possible refactoring**: Split into two constraints:
- One using `countDistinct(LabBlock::getSubject)` for the filter condition
- One using `toList()` only for the gap penalty computation

However, this may be **worse** — it doubles the number of groupBy operations and the gap penalty already needs the full list.

**Recommendation**: Investigate whether `countDistinct()` combined with a separate join pattern could replace this. If not feasible without making the code more complex, document the justification and close as "won't fix".
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Investigation documented: either refactored or justified as-is
- [ ] #2 If refactored: mvn test passes
- [ ] #3 If refactored: constraint semantics unchanged (same penalty for same inputs)
- [ ] #4 If closed as won't-fix: add code comment explaining why toList() is necessary here
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): M4/labRoomTagAffinity non esiste piu' (rimossa TASK-228; provider :237 lo dichiara) - il codice da ottimizzare e' sparito.
<!-- SECTION:NOTES:END -->
