---
id: TASK-101.06
title: M5 Informatica After Stage Test
status: Done
assignee: []
created_date: '2026-02-27 17:59'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - constraints
dependencies: []
parent_task_id: TASK-101
priority: medium
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add constraint verifier tests for M5 (Informatica after stage) medium constraint.

**Constraint to test:**
- M5: Reward Informatica after stage

This constraint rewards (+1 MEDIUM) Informatica lessons scheduled after a student group's stage period ends.

**Test file:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

**Pattern:** Two tests — one that gets the reward and one that doesn't.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 rewardInformaticaAfterStage_rewarded test passes
- [ ] #2 rewardInformaticaAfterStage_notRewarded test passes
<!-- AC:END -->
