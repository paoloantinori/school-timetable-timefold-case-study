---
id: TASK-103
title: Add human-readable violation messages to constraint analysis
status: Done
assignee: []
created_date: '2026-02-23 19:48'
updated_date: '2026-03-10 05:16'
labels:
  - backend
  - ux
  - analysis
  - claude-planned
dependencies:
  - task-102
priority: low
ordinal: 34750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Generate contextual, human-readable messages for constraint violations instead of just showing constraint IDs and scores. Currently the AnalysisPanel shows "H1.1: Teacher conflict: -50" but doesn't explain which teacher, which groups, or when.

Inspired by dmon-scheduler's createViolationMessage() pattern.

**Example messages:**
- H1: "Teacher T06 is double-booked on Mon 08:00 with G01 and G07"
- H2: "Room Lab Info 1 has 2 lessons at Mon 08:00: Informatica (G01) and CAD (G11)"
- H4: "Matematica (G01) is a fundamental subject scheduled on a stage day (Mon 2026-03-02)"
- S1: "Teacher T06 has a 2-hour gap between slots 2 and 5 on Monday"
- S5: "Student group G01 has a 3-slot gap on Tuesday"

**Approach:** Create a message formatter class that takes a constraint match (from Indictment API) and produces a human-readable string based on the constraint ID and the justification objects involved.

**Pairs well with:** task-102 (per-lesson violation analysis) — messages would be included in the per-lesson response.

**Key files:**
- New: message formatter utility class
- `rest/ScheduleResource.java` or `service/ScheduleService.java` (integrate messages into analysis response)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Message formatter produces readable text for all hard constraints (H1-H16, H-D1, H-M2)
- [ ] #2 Message formatter produces readable text for medium and soft constraints
- [ ] #3 Messages include entity-specific details (teacher name, group name, timeslot, room)
- [ ] #4 Messages are integrated into the analysis response (either aggregate or per-lesson)
- [ ] #5 Fallback to constraint ID + score if message generation fails for unknown constraints
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Blocker Assessment (2026-02-26)

**TASK-102 is a hard dependency.** The per-lesson violation analysis endpoint (task-102) must exist first — without it, there's no response payload to decorate with human-readable messages.

DataGenerator hour mismatches do NOT block this task. They affect schedule quality, not message formatting capability.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented multilingual violation messages with ResourceBundle support

Created English (messages_en.properties) and Italian (messages_it.properties) templates

Added ?lang query parameter to /api/schedule/analysis/lessons

Frontend automatically passes i18n.language to API
<!-- SECTION:FINAL_SUMMARY:END -->
