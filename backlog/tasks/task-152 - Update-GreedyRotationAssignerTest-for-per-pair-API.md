---
id: TASK-152
title: Update GreedyRotationAssignerTest for per-pair API
status: Done
assignee: []
created_date: '2026-03-03 11:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - testing
dependencies:
  - TASK-150
references:
  - test/GreedyRotationAssignerTest.java
  - service/GreedyRotationAssigner.java
priority: medium
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Update existing tests and add new tests for the per-pair refactored GreedyRotationAssigner.

**Update existing tests**:
- testExternalTeacherOrdering: use pair-level result to verify T28/group → THURSDAY
- testValidDayFilteringByH5: verify pair-level day filtering
- testNoValidAssignmentReturnsEmpty: verify failure when no valid days for pair

**Add new test**:
- Multi-day teacher: T26 with 2 groups, different LabRotation days → each pair placed on its correct day
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All 3 existing tests updated for pair-level API and passing
- [ ] #2 New multi-day teacher test verifies different days per group
- [ ] #3 mvn test passes with all tests green
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by LabRotation removal (commit e71bc85). Depends on TASK-150 which is now obsolete. Current tests pass (193/193) with the simplified LabRotation set.
<!-- SECTION:FINAL_SUMMARY:END -->
