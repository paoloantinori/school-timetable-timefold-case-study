---
id: TASK-100
title: Add solver integration tests (feasibility verification)
status: Done
assignee: []
created_date: '2026-02-23 19:47'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - solver
  - claude-planned
dependencies: []
priority: high
ordinal: 55000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create end-to-end solver integration tests that run the actual Timefold solver on representative datasets and verify it finds feasible solutions. Currently we only have constraint verifier tests (unit-level isolation) — no tests verify that constraints work correctly in combination.

Inspired by bavobbr/dmon-scheduler which runs 3 solver scenarios (small/realistic/stress) with 5-second time limits and asserts isFeasible().

**Key files:**
- New: `src/test/java/.../SchoolScheduleSolverIntegrationTest.java`
- Reference: `src/test/java/.../SchoolScheduleConstraintProviderTest.java` (existing patterns)
- Reference: `bootstrap/DataGenerator.java` (seed data to subset)

**Approach:**
- Use `@QuarkusTest` + `@Inject SolverManager<SchoolSchedule, Long>`
- Set `%test.quarkus.timefold.solver.termination.spent-limit=10s`
- Generate test data programmatically (not hardcoded) to reduce maintenance
- Three scenarios:
  1. Minimal: 2 teachers, 2 groups, 1 room, ~20 lessons → assert feasible
  2. Representative: subset of DataGenerator (~100 lessons, lab blocks) → assert feasible
  3. Stress: tight room capacity with many lab blocks → verify score within bounds
- Helper assertions: assertAllLessonsAssigned(), assertNoTeacherConflicts(), assertLabBlocksContiguous()

**Risk:** Test data must evolve with constraint changes — mitigate by generating from parameters, not literals.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Minimal scenario test passes: solver finds feasible solution for ~20 lessons in ≤10s
- [x] #2 Representative scenario test passes: solver finds feasible solution for ~100 lessons with lab blocks
- [x] #3 Stress scenario test verifies score is within defined bounds under tight capacity
- [x] #4 All tests use programmatic data generation (no hardcoded entity IDs)
- [x] #5 Tests run with H2 in-memory DB (no Docker required)
- [x] #6 mvn test still passes with new tests included
<!-- AC:END -->
