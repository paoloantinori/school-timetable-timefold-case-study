---
id: TASK-362
title: >-
  Admin completeness: LabRotation admin page, edit-mode for add/delete-only
  pages, expose missing fields
status: To Do
assignee: []
created_date: '2026-07-16 09:40'
updated_date: '2026-07-17 12:31'
labels:
  - claude-planned
  - spalmone-2027
  - ux
milestone: m-10
dependencies:
  - TASK-351
  - TASK-352
priority: low
ordinal: 186000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: verified gaps (frontend review 2026-07-16): LabRotation has NO admin UI at all (DB-only) despite driving pre-assignment; Holidays, StagePatterns, TeacherAvailability, ProfessionalizzantiDays, LabAssistants are add/delete-only (no edit); StudentGroupsAdmin does not expose maxHoursPerDay; CoursesAdmin does not expose doppioTeacher.

WHAT:
1. New LabRotationsAdmin page (CRUD, follows existing admin patterns, wired into AdminLayout) - NOTE: check TASK-351 outcome first; if the weekly-template redesign supersedes LabRotation, downscope to read-only or skip with a note.
2. Add edit capability to the add/delete-only pages (reuse useAdminList edit pattern from TeachersAdmin).
3. Expose StudentGroup.maxHoursPerDay (unless TASK-358 already did) and Course.doppioTeacher (pending TASK-352 doppio answer - if doppio is dead in 2026/27, remove the field instead).
4. Holiday range entry: allow entering a date range that expands to individual holiday rows (Christmas break is 15 rows by hand today).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every persisted entity type has a UI CRUD path (or a documented deliberate exclusion)
- [ ] #2 All previously add/delete-only pages support edit
- [ ] #3 Holiday range entry works; ui-typecheck.sh clean
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
FOLLOW-UP 2026-07-17 (from TASK-348 D1 review): StudentGroupResource.maxHoursPerDay has the identical cannot-clear-via-null gap (its own comment admits it) — apply the same 0-sentinel clear pattern used by TeacherResource.maxDailyHours.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
