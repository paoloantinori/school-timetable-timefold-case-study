---
id: TASK-136
title: 'Classes filter should hide unselected columns, not just filter lessons'
status: Done
assignee:
  - claude
created_date: '2026-02-28 13:44'
updated_date: '2026-03-09 19:50'
labels:
  - bug
  - ui
  - filter
  - claude-planned
dependencies: []
priority: medium
ordinal: 111000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
The "Classes" (student groups) dropdown filter in `ScheduleFilterBar` only filters which lessons appear in the grid cells, but does NOT hide the columns for unselected groups. Users expect selecting specific classes to hide all other columns.

## Current Behavior
- All student group columns remain visible regardless of filter selection
- Only lessons in cells are filtered (empty cells appear for unselected groups)

## Expected Behavior
- When classes are selected in the filter, ONLY those columns should be visible
- Unselected group columns should be completely hidden from the grid

## Root Cause
In `useScheduleData.ts`, the `columns` computation only considers `isHidden()` (from `useHiddenGroups`), but ignores `filterState.groups`:

```typescript
const columns = useMemo(() => {
  if (groupBy !== 'studentGroup') return allColumns;
  return allColumns.filter(col => !isHidden(col.id));  // Missing filterState.groups check
}, [allColumns, groupBy, isHidden]);
```

## Solution Direction
Modify the column filtering logic to also consider `filterState.groups`:
- If `filterState.groups.size > 0`, only show columns whose IDs are in the set
- If `filterState.groups.size === 0`, show all columns (respecting `isHidden`)

## Files Affected
- `src/main/webui/src/hooks/useScheduleData.ts` - column filtering logic
- Possibly `src/main/webui/src/hooks/useScheduleFilter.ts` - may need to expose additional helper

## Acceptance Criteria
1. Selecting classes in the Classes filter hides unselected columns
2. Clearing the filter shows all columns again
3. Works in both Week view and Timeline view
4. Existing hide/show per-column functionality (useHiddenGroups) continues to work
5. Teachers/Subjects filters should NOT hide columns (they only filter cell content)
<!-- SECTION:DESCRIPTION:END -->
