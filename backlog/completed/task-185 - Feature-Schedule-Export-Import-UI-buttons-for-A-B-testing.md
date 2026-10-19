---
id: TASK-185
title: 'Feature: Schedule Export/Import UI buttons for A/B testing'
status: Done
assignee: []
created_date: '2026-03-06 13:24'
updated_date: '2026-03-06 23:10'
labels: []
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

For A/B testing different solver configurations and pre-assignment strategies, I need to:
1. Export the current schedule state (partial or complete solution) at any point
2. Import it back to test different solver configurations
3. Do this easily from the UI without using API calls manually

Currently there's no UI for this - I'd have to use curl/api.sh to export/import JSON.

## Proposed Solution

Add **Export** and **Import** buttons to the main UI that:

**Export button:**
- Downloads current schedule state as JSON file
- Includes all entities, assignments, pinned state
- Works for partial solutions (e.g., after pre-assignment, mid-solve)
- Filename includes timestamp: `schedule-export-20260306-134526.json`

**Import button:**
- Opens file picker to select JSON file
- Replaces current schedule with imported state
- Transactional (all-or-nothing, rolls back on error)
- Validates before importing (referential integrity checks)

**Use cases for A/B testing:**
1. Run pre-assignment → Export → Test solver config A
2. Import same pre-assigned state → Test solver config B
3. Compare scores between runs with identical starting conditions
4. Share problematic states for debugging

Acceptance Criteria:
- [ ] #1 Given I'm on the schedule page, when I click Export button, then browser downloads current schedule as JSON file
- [ ] #2 Given the solver is running or pre-assigning, when I click Export button, then it exports the current partial state (pinned entities only)
- [ ] #3 Given I have a JSON export file, when I click Import button and select it, then the schedule is replaced with imported state
- [ ] #4 Given import has invalid data (broken references), when importing, then it shows an error message and doesn't modify the schedule
- [ ] #5 Given I import a previously exported state, when I refresh the page, then all assignments and pinned states match the export
- [ ] #6 Export button is disabled when no schedule exists (fresh DB with no data)

References:
- Extends TASK-106 (backend API for export/import)
- UI: src/main/webui/src/components/SolverControls.tsx (add Export/Import buttons)
- API: GET /api/schedule/export, POST /api/schedule/import (to be implemented)
- Format: JSON with all entities (Lessons, LabBlocks, Teachers, Rooms, etc.)
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented schedule export/import feature for A/B testing:
- Backend: `/api/schedule/export` and `/api/schedule/import` endpoints
- Frontend: Export/Import buttons in SolverControls
- ImportService uses JDBC batch inserts for 7000+ entities
- Handles LabRotation FK constraints via truncate/re-seed pattern
<!-- SECTION:FINAL_SUMMARY:END -->
