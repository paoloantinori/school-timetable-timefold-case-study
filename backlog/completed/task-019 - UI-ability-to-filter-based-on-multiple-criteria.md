---
id: task-019
title: UI - ability to filter based on multiple criteria
status: Done
assignee: []
created_date: '2026-02-18 05:58'
updated_date: '2026-02-19 11:50'
labels:
  - ui
  - claude-planned
dependencies: []
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
i would like the ability, in the calendar, to show on screen only the lessons, or the teachers i want, choosing from a comprehensive list. This would allow me to spot violations easilty
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Open schedule view
- [x] #2 Click 'Teachers' dropdown, select 2 teachers
- [x] #3 Verify only their lessons appear
- [x] #4 Add subject filter, verify AND logic works
- [x] #5 Remove badges individually, verify filter updates
- [x] #6 Click 'Clear all', verify all lessons return
- [x] #7 Refresh page, verify filter state persists from localStorage
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Component Architecture

**New File**: `src/main/webui/src/components/FilterBar.tsx`

```
┌─────────────────────────────────────────────────────────────────┐
│ [👤 Teachers ▾] [📚 Subjects ▾] [👥 Groups ▾] [🗑 Clear all]   │
│                                                                 │
│ Active: [× T06] [× Informatica] [× G01]             │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation

### Filter State Interface
```typescript
interface FilterState {
  teachers: Set<number>;    // Teacher IDs to show
  subjects: Set<string>;    // Subject names to show
  groups: Set<number>;      // Student group IDs to show
}
```

### Component Structure
1. **FilterDropdown**: Reusable dropdown with search + checkboxes
2. **FilterBadge**: Removable pill showing active filter
3. **FilterBar**: Container managing dropdowns + badges

### Integration Points
- Place below `SolverControls`, above the schedule grid
- Connect to `ScheduleTimeline` state
- Filter lessons before rendering:
  ```typescript
  const filteredLessons = lessons.filter(l => {
    if (filterState.teachers.size && !filterState.teachers.has(l.course?.teacher?.id)) return false;
    if (filterState.subjects.size && !filterState.subjects.has(l.course?.subject)) return false;
    if (filterState.groups.size && !filterState.groups.has(l.course?.studentGroup?.id)) return false;
    return true;
  });
  ```

### Behavior
- Empty filter state = show all (default)
- Multiple filters = AND logic (show lessons matching ALL criteria)
- Badges are removable via × button
- "Clear all" button when any filter active
- Persist to localStorage for session continuity

## Files to Create
- `src/main/webui/src/components/FilterBar.tsx` - Multi-select filter with badges
- `src/main/webui/src/components/FilterDropdown.tsx` - Reusable dropdown component

## Files to Modify
- `src/main/webui/src/components/ScheduleTimeline.tsx` - Integrate FilterBar

## Implementation Complete

Created files:

- `src/main/webui/src/hooks/useScheduleFilter.ts` - Filter state hook with localStorage persistence

- `src/main/webui/src/components/ScheduleFilterBar.tsx` - Multi-criteria filter component

Features implemented:

- Dropdowns for Teachers, Subjects, Student Groups with search

- Checkbox selection with visual feedback

- Active filters shown as colored badges (blue=teacher, purple=subject, teal=group)

- Individual badge removal and Clear all button

- localStorage persistence across sessions

- AND logic for multiple filters

Integrated into ScheduleTimeline.tsx with filtering logic.
<!-- SECTION:NOTES:END -->
