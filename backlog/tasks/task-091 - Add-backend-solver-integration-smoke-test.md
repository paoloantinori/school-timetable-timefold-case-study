---
id: TASK-091
title: Add backend solver integration smoke test
status: Done
assignee: []
created_date: '2026-02-23 17:24'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - testing
dependencies: []
priority: high
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter's employee-scheduling example includes a REST-level integration test that verifies the solver produces a feasible solution from seed data. The planner has comprehensive constraint unit tests (`ConstraintVerifier`) and Playwright e2e tests, but no backend integration test that exercises the full solve pipeline.

**What**: Create a `@QuarkusTest` that:
1. Loads the schedule via `GET /api/schedule`
2. Triggers a short solve via `POST /api/schedule/solve`
3. Polls until solver completes (or timeout)
4. Fetches the final schedule and verifies the score is not catastrophic (e.g., hard score improved from initial)

**Key design decisions**:
- **Test termination**: Use `%test` profile's `spent-limit=10s` — enough to verify the pipeline works, not enough for a good solution
- **Database**: H2 in-memory (existing test infrastructure). The test relies on `DataGenerator` seeding entities on startup
- **Assertion**: Don't assert feasibility (10s is insufficient for ~7,000 entities). Assert that:
  - Solver status transitions correctly (NOT_SOLVING → SOLVING_ACTIVE → NOT_SOLVING)
  - Final score exists and has `hardScore > initial hardScore` (solver improved something)
  - No 500 errors from any endpoint

**File location**: `src/test/java/com/school/planner/rest/ScheduleResourceTest.java`

**Why this matters**: Without this test, a regression in `ScheduleService`, `SchoolSchedule` wiring, or `SolverManager` configuration would only be caught by manual testing or Playwright e2e tests (which are slower and less reliable).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 New @QuarkusTest class ScheduleResourceTest exists in src/test/java/
- [ ] #2 Test verifies solver can start, run, and stop without errors
- [ ] #3 Test verifies score exists after solving completes
- [ ] #4 Test uses %test profile with H2 (no external DB)
- [ ] #5 Test completes within 30 seconds (10s solve + polling overhead)
- [ ] #6 mvn test passes including the new test
<!-- AC:END -->
