---
id: TASK-172
title: Replace Course.doppioTeacher with LabAssistant entity
status: Done
assignee: []
created_date: '2026-03-05 09:27'
updated_date: '2026-03-05 10:28'
labels:
  - claude-planned
  - refactor
  - solver
dependencies: []
references:
  - src/main/java/com/school/planner/domain/Course.java
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
  - src/main/java/com/school/planner/bootstrap/DataGenerator.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace the per-course HARD doppio constraint (Course.doppioTeacher) with a per-room MEDIUM constraint using a new LabAssistant entity. The school defines doppio as teacher↔room ("assistenti di laboratorio"), not teacher↔course. The current model creates 858h of mandatory doppio for T14 when only 1,004 slots exist — structurally infeasible, causing 280 H-D1 violations (-500hard).

**What changes:**
- New `LabAssistant` entity (teacher + roomTag) replaces `Course.doppioTeacher` FK
- 6 HARD constraints (H-D1.1-4, H5.3-4) → 4 MEDIUM constraints (M-LA.1-4)
- Solver decides which sessions the assistant attends (not all-or-nothing)
- Pre-assignment pipeline simplified (no doppio checks needed for MEDIUM)

**Files:** 14 modified, 3 new (LabAssistant.java, LabAssistantResource.java, LabAssistantsAdmin.tsx)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 LabAssistant entity created with teacher FK + roomTag field
- [ ] #2 REST CRUD at /api/lab-assistants (GET/POST/DELETE)
- [ ] #3 4 M-LA MEDIUM constraints replace 6 H-D1/H5 HARD constraints
- [ ] #4 Course.doppioTeacher field removed
- [ ] #5 Doppio VRP filtering removed from Lesson and LabBlock
- [ ] #6 Pre-assignment pipeline cleaned of doppio checks
- [ ] #7 DataGenerator seeds 4 LabAssistant records (T18/acconciatura, T10/meccanica, T14/moda, T03/termoidraulica)
- [ ] #8 Frontend admin page for LabAssistants
- [ ] #9 doppioTeacher UI patterns removed from cellRenderer and useScheduleData
- [ ] #10 Constraint tests updated (doppio tests removed, M-LA tests added)
- [ ] #11 mvn test passes
- [ ] #12 TypeScript compiles clean
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Replaced Course.doppioTeacher (per-course HARD constraint) with LabAssistant entity (per-room MEDIUM constraint). Result: 0hard (was -500hard), 51 medium M-LA violations as solver preferences. 27 files changed across backend, frontend, and tests. Commit: 03cb515.
<!-- SECTION:FINAL_SUMMARY:END -->
