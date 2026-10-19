---
id: TASK-313
title: Refactor H17 (vertically compact) with toConsecutiveSequences
status: Done
assignee: []
created_date: '2026-05-21 17:07'
updated_date: '2026-05-21 20:53'
labels:
  - solver
  - constraints
  - refactor
milestone: m-1
dependencies: []
priority: high
ordinal: 137000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace the custom gap detection logic in H17 (vertically compact) with Timefold 2.x `ConstraintCollectors.toConsecutiveSequences`.

**Current approach**: Manual gap detection — groups lessons by (studentGroup, date), sorts by slotIndex, iterates to find gaps.

**New approach**: Use `toConsecutiveSequences(Lesson::getSlotIndex)` which automatically groups into consecutive sequences and detects gaps between them. Flatten the sequences and penalize groups that have multiple sequences (i.e., gaps).

**Example pattern**:
```java
return constraintFactory.forEach(Lesson.class)
    .filter(l -> l.timeslot != null)
    .groupBy(l -> l.course.studentGroup, l -> l.timeslot.date,
        ConstraintCollectors.toConsecutiveSequences(l -> l.timeslot.slotIndex))
    .flattenLast(SequenceChain::getConsecutiveSequences)
    // penalize if more than one sequence exists (has gaps)
```

**Also evaluate for**: H15 (same-day same-subject consecutive), S7 (reward consecutive student lessons)

**Files**: `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` (H17 method)

**Reference**: claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H17 uses toConsecutiveSequences instead of manual gap detection
- [ ] #2 Constraint behavior is identical (same violations detected)
- [ ] #3 Move evaluation speed is equal or better
- [ ] #4 Evaluate and document applicability to H15 and S7
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
H17 and H15 already use `toConsecutiveSequences` (H15 was refactored in TASK-243, H17 was already using it). S7 was evaluated but should remain as-is because: (1) toConsecutiveSequences rewards sequences not pairs, changing the semantics; (2) S7 uses Duration-based adjacency with 30-min gap tolerance that toConsecutiveSequences can't express; (3) O(n²) join is trivial since n=5-6 lessons per group per day. No code changes needed.
<!-- SECTION:FINAL_SUMMARY:END -->
