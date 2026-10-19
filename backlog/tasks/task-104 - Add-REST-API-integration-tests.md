---
id: TASK-104
title: Add REST API integration tests
status: Done
assignee:
  - claude
created_date: '2026-02-23 19:48'
updated_date: '2026-03-09 19:50'
labels:
  - testing
  - api
  - claude-planned
dependencies: []
priority: medium
ordinal: 66000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add @QuarkusTest integration tests for REST API endpoints using RestAssured. Currently we have zero endpoint tests — contract correctness, error handling, and pre-assignment pipeline behavior are verified only through manual testing.

**Key endpoints to test:**

1. **Solver lifecycle:**
   - POST /api/schedule/solve → returns 202
   - DELETE /api/schedule/solve → stops solver
   - GET /api/schedule → returns schedule with score

2. **Pre-assignment pipeline:**
   - POST /api/schedule/pre-assign → runs full pipeline
   - POST /api/schedule/pre-assign/reset → clears pinned lessons
   - POST /api/schedule/pre-assign/reset-all → clears all assignments

3. **CRUD operations (sample):**
   - GET/POST/PUT/DELETE /api/teachers
   - GET/POST/PUT/DELETE /api/courses
   - POST /api/courses/{id}/generate-lessons

4. **Error cases:**
   - PUT /api/teachers/999999 → 404
   - POST /api/teachers with invalid data → 400
   - DELETE solver when not running → appropriate response

**Key files:**
- New: `src/test/java/.../rest/` test classes
- Uses H2 in-memory DB (no Docker)
- Uses RestAssured (already a Quarkus test dependency)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Solver lifecycle tests verify start (202), status polling, and stop
- [ ] #2 Pre-assignment pipeline test verifies lessons get pinned after running
- [ ] #3 Pre-assignment reset test verifies pinned lessons are cleared
- [ ] #4 At least one CRUD resource has full GET/POST/PUT/DELETE tests
- [ ] #5 Error cases return appropriate HTTP status codes (404, 400)
- [ ] #6 All tests use H2 in-memory DB and pass with mvn test
<!-- AC:END -->
