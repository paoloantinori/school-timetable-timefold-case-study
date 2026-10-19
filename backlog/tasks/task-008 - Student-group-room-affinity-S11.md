---
id: TASK-008
title: Student group room affinity (S11)
status: Done
assignee: []
created_date: '2026-02-17 16:32'
updated_date: '2026-03-10 05:16'
labels:
  - claude-planned
  - soft-constraint
  - student-group
  - room-assignment
dependencies: []
priority: medium
ordinal: 17750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Student groups currently get assigned to various rooms based on solver optimization. This causes unnecessary movement for students throughout the day as they may be assigned to different classrooms for different lessons. Lab rooms are excluded because they have specific requirements (handled by H-M2).

## Proposed Solution

Add a new soft constraint **S11: Student group room affinity** that rewards keeping student groups in the same room(s) throughout the day. This makes the schedule more student-friendly by minimizing classroom changes.

**Constraint ID**: S11 (Student group room affinity)

**Approach**: For each student group on each day, count the number of distinct rooms used by non-lab lessons. Penalize each additional room beyond the first.

**Formula**: For each (studentGroup, date) pair with non-lab lessons, penalty = (distinctRoomCount - 1) * weight

**Exclusions**: 
- Lab lessons (isLab() == true) are excluded - they have fixed room requirements via H-M2
- Pinned lessons are excluded - pre-assigned by human planners
- LabBlock entities are excluded - handled by lab-specific constraints
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a student group has 4 non-lab lessons on a single day, When all 4 are assigned to the same room, Then no S11 penalty is incurred
- [x] #2 Given a student group has 4 non-lab lessons on a single day, When 2 are in Room A and 2 are in Room B, Then S11 penalty of (2-1) * weight = weight is incurred
- [x] #3 Given a student group has lab lessons and non-lab lessons on the same day, When the lab lessons are in a lab room and non-lab lessons are in a single classroom, Then only the non-lab rooms are counted for S11 (lab rooms excluded)
- [x] #4 Given a student group has only lab lessons on a day, Then no S11 penalty is incurred (labs excluded from counting)

- [x] #5 Given two student groups share lessons on the same day in different rooms, When each group uses only one room for their non-lab lessons, Then no S11 penalty is incurred for either group
- [ ] #6 Given the solver runs with S11 enabled, When comparing to a baseline without S11, Then the average distinct rooms per student-group-day should decrease
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Phase 1: Implementation
1. [ ] Add `studentGroupRoomAffinity()` constraint method to SchoolScheduleConstraintProvider.java
2. [ ] Register constraint in `defineConstraints()` array (soft constraints section, after S10)
3. [ ] Build and verify compilation: `mvn compile -DskipTests`

### Phase 2: Testing
4. [ ] Add unit tests for S11 in SchoolScheduleConstraintProviderTest.java
5. [ ] Run tests: `mvn test`
6. [ ] Manual test: start dev profile, solve, check analysis panel for S11

### Phase 3: Validation
7. [ ] Run benchmark before: `./scripts/benchmark.sh feature/cartesian-block-entity 5`
8. [ ] Compare: verify S11 active, hard scores maintained, room count decreased

### Phase 4: Documentation
9. [ ] Update CLAUDE.md constraint tables with S11 entry
10. [ ] Update PROJECT_INDEX.md soft constraints list
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **2/5** (Low-Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 2 | Single constraint method |
| Risk | 1 | Soft constraint, no breaking changes |
| Testing | 2 | Constraint verifier test |
| Dependencies | 1 | None |
| **Total** | **2** | 2-4 hours |

---

## Priority: **MEDIUM** (Solver Quality Improvement)

Direct solver quality improvement (S11 constraint). Clear acceptance criteria, well-planned, low risk.
<!-- SECTION:NOTES:END -->
