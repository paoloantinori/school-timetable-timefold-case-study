---
id: TASK-101
title: Complete constraint verifier test coverage
status: Done
assignee:
  - Claude
created_date: '2026-02-23 19:47'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - constraints
  - claude-planned
dependencies: []
priority: high
ordinal: 57000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Systematically add constraint verifier tests for all ~21 untested constraints. Currently 19 out of ~40+ constraints have tests — the gap grows with each new constraint added.

**Currently tested (19):** H1.1, H1.2, H2.1, H3.1, H4.1, H5.1, H6.1, H7, H9, H11, H-D1, H-M2, H-M2b, M3.1, M3.2, M4, S4, S7, S8

**Missing tests — Hard constraints:**
- H1.3 (Teacher conflict B-B)
- H2.2, H2.3 (Room conflict L-B, B-B)
- H3.2, H3.3 (Student group conflict L-B, B-B)
- H4.2 (Stage blackout LabBlock)
- H5.2 (Teacher unavailability LabBlock)
- H6.2 (School holiday LabBlock)
- H14.1, H14.2 (Student group start date)
- H15 (Same-day same-subject consecutive)
- H16 (Consecutive same-subject same teacher-room)
- H-D1.2, H-D1.3, H-D1.4 (Doppio teacher variants)

**Missing tests — Medium:**
- M5 (Informatica after stage)

**Missing tests — Soft:**
- S1 (Teacher gap minimization)
- S2 (Subject distribution)
- S3 (Teacher preference)
- S5 (Student gap minimization)
- S10 (Discourage generic lessons in labs)

**Key file:** `src/test/java/.../SchoolScheduleConstraintProviderTest.java`

**Pattern:** Each constraint gets at least two tests — one that triggers the violation (penalizes) and one that doesn't (no penalty). Use existing factory methods at bottom of test file.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All LabBlock B-B variant constraints (H1.3, H2.3, H3.3) have positive and negative test cases
- [ ] #2 All LabBlock L-B variant constraints (H2.2, H3.2, H4.2, H5.2, H6.2) have test cases
- [ ] #3 H14.1, H14.2 (student group start date) have test cases
- [ ] #4 H15, H16 (consecutive same-subject constraints) have test cases
- [ ] #5 H-D1.2, H-D1.3, H-D1.4 (doppio variants) have test cases
- [ ] #6 M5 (Informatica after stage) has test cases
- [ ] #7 All soft constraints S1, S2, S3, S5, S10 have test cases
- [ ] #8 mvn test passes with all new tests
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Subtasks Created (2026-02-27)

| Task | Focus | Constraints | Tests |
|------|-------|-------------|-------|
| task-101.01 | B-B Variants | H1.3, H2.3, H3.3 | 6 |
| task-101.02 | L-B Variants | H2.2, H3.2, H4.2, H5.2, H6.2 | 10 |
| task-101.03 | Start Date | H14.1, H14.2 | 4 |
| task-101.04 | Consecutive | H15, H16 | 4 |
| task-101.05 | Doppio Variants | H-D1.2, H-D1.3, H-D1.4 | 6 |
| task-101.06 | M5 | M5 | 2 |
| task-101.07 | Soft Constraints | S1, S2, S3, S5, S10 | 10 |

**Total: ~42 tests across 7 subtasks**

This task now serves as a tracking/parent task. Complete the subtasks to fulfill the acceptance criteria.
<!-- SECTION:NOTES:END -->
