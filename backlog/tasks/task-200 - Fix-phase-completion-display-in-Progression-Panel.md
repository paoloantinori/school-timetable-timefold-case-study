---
id: TASK-200
title: Fix phase completion display in Progression Panel
status: Done
assignee: []
created_date: '2026-03-08 10:01'
updated_date: '2026-03-09 19:50'
labels:
  - bug
  - ui
  - solver
dependencies: []
priority: high
ordinal: 87000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bug: Completed phases showing as "in progress" (blue background) in Progression Panel.

## Root Cause
In `PhaseProgressionService.getProgression()`, when creating `currCopy` for the current phase, the code used the `PhaseRecord` constructor which always sets `isComplete=false`. This caused phases that had actually completed (with `endTime` set) to display as "in progress".

## Fix
Check if the current phase has an `endTime` set, and if so, mark `currCopy` as complete:

```java
// Mark as complete if end time is set (solver finished or phase transitioned)
if (curr.getEndTime() != null) {
    currCopy.setComplete(true);
}
```

## Files Modified
- `src/main/java/com/school/planner/service/PhaseProgressionService.java`

## Verification
1. Start solver and let it run through multiple phases
2. Click "Progression" button
3. Verify only the current active phase shows blue background
4. Completed phases should show gray background
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Fixed phase completion display bug. Added @JsonProperty("isComplete") annotation to PhaseRecord.isComplete() getter. Jackson's default serialization strips "is" prefix from boolean getters (isComplete() → "complete"), but TypeScript expected "isComplete" field. The annotation forces correct JSON field name. Now completed phases display correctly with gray background and no "In progress..." text, while active phases show blue background with live status.
<!-- SECTION:FINAL_SUMMARY:END -->
