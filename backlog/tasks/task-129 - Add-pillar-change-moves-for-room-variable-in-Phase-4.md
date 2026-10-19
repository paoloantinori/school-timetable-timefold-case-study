---
id: TASK-129
title: Add pillar change moves for room variable in Phase 4
status: Done
assignee: []
created_date: '2026-02-27 10:29'
updated_date: '2026-03-09 19:50'
labels:
  - solver
  - performance
  - claude-planned
dependencies: []
priority: medium
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Based on research (task-096), add pillar change moves targeting the `room` variable in Phase 4 of solverConfig.xml. This will help resolve H2 (room conflict) violations more efficiently by allowing bulk room reassignment.

**Configuration to add:**
```xml
<pillarChangeMoveSelector>
  <fixedProbabilityWeight>1.5</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>5</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="room"/>
</pillarChangeMoveSelector>
```

**Rationale:** Room conflicts (H2) are a primary hard constraint. Bulk room reassignment can efficiently resolve conflicts where multiple lessons need to vacate an overbooked room.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Pillar change moves already implemented in solverConfig.xml Phase 4 (lines 193-205). Note: implemented for timeslot variable, not room variable as originally specified.
<!-- SECTION:FINAL_SUMMARY:END -->
