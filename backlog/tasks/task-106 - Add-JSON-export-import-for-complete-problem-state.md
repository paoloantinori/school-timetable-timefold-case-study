---
id: TASK-106
title: Add JSON export/import for complete problem state
status: Done
assignee: []
created_date: '2026-02-23 19:48'
updated_date: '2026-03-09 19:50'
labels:
  - backend
  - api
  - tooling
  - claude-planned
dependencies: []
priority: low
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add endpoints to export and import the complete scheduling problem as a single JSON document. Currently we have CSV import for individual entity types but no way to snapshot or restore a full problem state.

**Endpoints:**
- `GET /api/schedule/export` → returns full problem as JSON (all entities, assignments, pinned state)
- `POST /api/schedule/import` → accepts JSON, replaces current problem state

**Use cases:**
- Create reproducible bug reports (export problem state that triggers a constraint issue)
- Share problem instances across branches for benchmarking
- Backup before destructive operations (pre-assignment reset, re-solve)
- Enable "save/load" workflow for experimenting with different solver configurations

**Scope includes:**
- Teachers, StudentGroups, Rooms, Courses, Timeslots, SchoolHolidays
- StagePatterns, LabRotations, TeacherAvailability
- Lessons (with timeslot/room assignments and pinned state)
- LabBlocks (with startTimeslot/room assignments and pinned state)
- ElementCollection fields (room tags, course required tags, stage day masks)

**Key files:**
- `rest/ScheduleResource.java` (add endpoints)
- New DTO classes for import/export format
- Service layer for transactional import (delete-all + insert)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GET /api/schedule/export returns JSON containing all entity types with assignments
- [ ] #2 Exported JSON can be imported back with POST /api/schedule/import to reproduce exact state
- [ ] #3 Import is transactional (all-or-nothing, rolls back on error)
- [ ] #4 ElementCollection fields (room tags, course tags, stage day masks) are preserved in round-trip
- [ ] #5 Pinned state and solver assignments are preserved in round-trip
- [ ] #6 Import validates referential integrity before committing
<!-- AC:END -->
