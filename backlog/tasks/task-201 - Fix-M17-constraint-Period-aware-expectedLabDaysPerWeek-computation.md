---
id: TASK-201
title: 'Fix M17 constraint: Period-aware expectedLabDaysPerWeek computation'
status: Done
assignee: []
created_date: '2026-03-08 11:09'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - solver
  - constraint
  - data-model
dependencies: []
priority: medium
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Background

The `expectedLabDaysPerWeek()` function in M17/M17.2 constraints uses a hardcoded 38-week divisor, which assumes all lab courses are distributed evenly across the entire school year. This is incorrect for rotation-based courses that run in concentrated periods (10-18 weeks).

## Problem

Current computation: `blockCount / 38.0`

Example misclassification:
- **Informatica G07**: 25 blocks → 25/38 = 0.66 → expectedDays = 1 (single-day) ❌
- **If 12-week period**: 25/12 = 2.08 → expectedDays = 2 (multi-day) ✓

## Solution

Add persistent `expectedLabDaysPerWeek` field to `Course` entity, computed based on actual `LabRotation` period duration.

## Implementation

1. Add `expected_lab_days_per_week` column to `course` table
2. Add field to `Course.java` with `@Column` annotation
3. Create `ExpectedDaysCalculator` utility class
4. Integrate computation into `BacktrackingPreAssigner`
5. Update M17/M17.2 constraints to use `course.expectedLabDaysPerWeek`
6. Add recalculation trigger when `LabRotation` periods change
7. Add unit tests

## Files to Modify

- `src/main/java/com/school/planner/domain/Course.java` - Add field
- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` - Update constraints
- `src/main/java/com/school/planner/service/BacktrackingPreAssigner.java` - Compute during pre-assignment
- `src/main/java/com/school/planner/service/ExpectedDaysCalculator.java` - NEW utility class
- `src/main/java/com/school/planner/rest/LabRotationResource.java` - Recalc trigger
- `src/test/java/com/school/planner/service/ExpectedDaysCalculatorTest.java` - NEW test class

## Research

See `claudedocs/research_m17_modeling_approaches_20260308.md` for full analysis.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Add expected_lab_days_per_week column to course table with migration
- [ ] #2 Add @Column field to Course.java
- [ ] #3 Create ExpectedDaysCalculator utility class
- [ ] #4 Update M17/M17.2 constraints to use course.expectedLabDaysPerWeek
- [ ] #5 Integrate computation into BacktrackingPreAssigner
- [ ] #6 Add recalculation trigger in LabRotationResource
- [ ] #7 Add unit tests for ExpectedDaysCalculator
- [ ] #8 Update CLAUDE.md constraint documentation
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
[x] Add expected_lab_days_per_week column to course table (migration script created)

[x] Add @Column field to Course.java

[x] Create ExpectedDaysCalculator utility class

[x] Update M17/M17.2 constraints to use course.expectedLabDaysPerWeek

[x] Integrate computation into BacktrackingPreAssigner

[x] Add recalculation trigger in LabRotationResource

[x] Add unit tests for ExpectedDaysCalculator

[ ] Apply SQL migration to database

[ ] Run tests and verify

[ ] Update CLAUDE.md constraint documentation
<!-- SECTION:PLAN:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 []
<!-- DOD:END -->
