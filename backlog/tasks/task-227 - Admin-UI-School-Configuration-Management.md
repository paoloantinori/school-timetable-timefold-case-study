---
id: TASK-227
title: 'Admin UI: School Configuration Management'
status: Done
assignee: []
created_date: '2026-03-11 21:28'
updated_date: '2026-03-11 21:38'
labels:
  - admin
  - ui
  - data-entry
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create an admin page to manage school configuration data:

## Requirements

### 1. School Settings Page
A new admin page (e.g., `/admin/school-settings`) that allows managing:

#### A. Student Group Start Dates
- Display all student groups with their current `startDate`
- Allow editing `startDate` for each group
- Pre-populate with `2025-09-01` (default school year start)

#### B. Vacation Dates (School Holidays)
- List all school holidays
- Allow CRUD operations on holidays
- Pre-populate from DataGenerator (Christmas, Easter, national holidays)

#### C. Stage Patterns per Student Group
- Display stage patterns (student group, start date, end date)
- Allow CRUD operations
- Pre-populate from DataGenerator data

### 2. Data Pre-population
- Load initial data from DataGenerator constants
- Allow reset to defaults
- Allow manual override of any entry

### 3. UI Components
- Use existing admin layout pattern (AdminLayout.tsx)
- Date pickers for date fields
- Table/list view with edit capabilities
- Save/Cancel actions

## Technical Notes
- Backend: New REST endpoints or extend existing ones
  - `PUT /api/student-groups/{id}` - update startDate
  - CRUD for school holidays (existing)
  - CRUD for stage patterns (existing)
- Frontend: New page `src/main/webui/src/components/admin/SchoolSettingsAdmin.tsx`
- Add navigation link in admin menu

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Admin can view all student groups with their start dates
- [ ] #2 Admin can edit start date for any student group
- [ ] #3 Admin can view/add/edit/delete school holidays
- [ ] #4 Admin can view/add/edit/delete stage patterns
- [ ] #5 Data is pre-populated from DataGenerator defaults
- [ ] #6 Changes persist across app restarts
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Plan

Created plan file: /home/user/.cc-mirror/zai/config/plans/typed-shimmying-aurora.md

Plan covers:
- Backend: Extend StudentGroupResource.update() to include startDate
- Frontend: New SchoolSettingsAdmin.tsx page
- Routes, navigation, translations
- Verification steps

## Verification Steps

1. Build: `./scripts/rebuild.sh`
2. Test backend with curl (or api/api.sh raw)
3. Test frontend navigation and CRUD
4. Verify persistence

## Files

| File | Action |\------|--------|
| StudentGroupResource.java | EDIT - add startDate |
| SchoolSettingsAdmin.tsx | CREATE |
| App.tsx | EDIT - add route |
| AdminLayout.tsx | EDIT - add nav |
| admin.json (en/it) | EDIT - add i18n |

Plan created for TASK-227
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
