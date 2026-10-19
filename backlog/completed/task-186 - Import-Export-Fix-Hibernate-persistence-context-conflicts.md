---
id: TASK-186
title: 'Import/Export: Fix Hibernate persistence context conflicts'
status: Done
assignee: []
created_date: '2026-03-06 19:56'
updated_date: '2026-03-06 23:10'
labels:
  - claude-planned
  - import-export
  - solver-testing
dependencies: []
references:
  - claudedocs/design-import-export.md
  - src/main/java/com/school/planner/service/ImportService.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Import endpoint fails with "Row was already updated or deleted by another transaction for entity [Timeslot with id '1']" when importing a schedule with 7000+ entities.

## Root Cause (CORRECTED)
The error occurs because `merge()` on detached entities (deserialized from JSON with IDs) fails when those IDs were deleted by `deleteAll()`. Hibernate's `merge()` tries to:
1. Load the entity from DB to compare states
2. Detect the entity doesn't exist (was deleted)
3. Attempt to INSERT, but conflicts with version tracking

**Key insight:** The issue is NOT "stale references in persistence context" - it's `merge()` behavior with deleted detached entities.

## Solution
Refactor ImportService to use native SQL with explicit IDs:
1. TRUNCATE tables (faster than DELETE for large tables) with `RESTART IDENTITY CASCADE`
2. Use JDBC batch inserts with explicit IDs (bypasses Hibernate merge entirely)
3. **MANDATORY:** Reset PostgreSQL sequences after import (not optional!)
4. Insert entities in dependency order (parents before children)
5. Handle `@ElementCollection` fields separately (Room.tags, Course.requiredTags)

## Critical Details (from research)
- `ON CONFLICT DO UPDATE` is unnecessary after TRUNCATE (table is empty) - adds overhead
- `em.flush()` + `em.clear()` after delete is CORRECT and essential
- `GenerationType.IDENTITY` silently disables JDBC batching (use native SQL)
- ElementCollection fields require separate INSERT statements into collection tables

## Design Doc
See `claudedocs/design-import-export.md` for full specification.
See `claudedocs/research-import-export-validation-20260306.md` for validation against Hibernate best practices.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Export produces valid JSON with all entities
- [ ] #2 Import succeeds with 7000+ entities (7.1MB payload)
- [ ] #3 Roundtrip preserves all data (export → import → export = same)
- [ ] #4 Import validates FK references before database operations
- [ ] #5 Import is atomic (all or nothing)
- [ ] #6 Import completes in < 10 seconds for 7000 entities
- [ ] #7 Error messages are clear and actionable
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed Hibernate persistence context conflicts:
- Replaced JPA merge() with native SQL + JDBC batch
- TRUNCATE CASCADE clears all tables atomically
- Sequence reset after explicit ID inserts
- LabRotation preserved via truncate order (seed data re-created on restart)

Note: TASK-187 created for DayOfWeek constraint issue (SUNDAY=7 violates DB constraint 0-6)
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Unit tests for validation logic
- [ ] #2 Integration test for roundtrip
- [ ] #3 Manual test with production-sized payload
- [ ] #4 Update REST API documentation
<!-- DOD:END -->
