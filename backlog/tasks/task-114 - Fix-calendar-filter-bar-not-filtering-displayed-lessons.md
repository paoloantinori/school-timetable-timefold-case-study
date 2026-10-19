---
id: TASK-114
title: Fix calendar filter bar not filtering displayed lessons
status: Done
assignee: []
created_date: '2026-02-24 16:58'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: high
ordinal: 27750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
The filter bar in the calendar view (ScheduleFilterBar) doesn't affect the displayed lessons when filters are enabled. Users can select teachers, subjects, or student groups in the filter dropdowns, but the timeline grid continues to show all lessons unchanged.

## Affected Components
- `src/main/webui/src/components/ScheduleFilterBar.tsx` - Filter UI component
- `src/main/webui/src/hooks/useScheduleFilter.ts` - Filter state management hook
- `src/main/webui/src/hooks/useScheduleData.ts` - Data hook that applies filters
- `src/main/webui/src/components/ScheduleTimeline.tsx` - Main calendar component

## Data Flow Analysis
1. `useScheduleFilter` hook manages `filterState` (Sets for teachers, subjects, groups)
2. State is passed to `useScheduleData` hook
3. `useScheduleData.passesFilter()` function filters lessons
4. Filtered lessons populate `lessonLookup` maps
5. `getLesson()` returns filtered lessons for grid cells

## Suspected Root Causes
1. **Memoization issue**: `lessonLookup` useMemo may not recompute when `filterState` changes
2. **Reference stability**: `passesFilter` callback may not trigger useMemo recalculation
3. **Column filtering**: Columns (groups/rooms/teachers) may not be hidden when filtered out
4. **State propagation**: Filter state may not propagate correctly to data hook

## Debugging Steps
1. Add console.log in `toggleTeacher`/`toggleSubject`/`toggleGroup` to verify state changes
2. Add console.log in `passesFilter` to verify it's being called with correct state
3. Check if `lessonLookup` useMemo dependencies include `filterState`
4. Verify `useCallback`/`useMemo` dependency arrays are correct
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given I'm viewing the calendar, when I select a teacher filter, then only lessons taught by that teacher are visible
- [ ] #2 Given I'm viewing the calendar, when I select a subject filter, then only lessons with that subject are visible
- [ ] #3 Given I'm viewing the calendar, when I select a student group filter, then only lessons for that group are visible
- [ ] #4 Given multiple filters are active, then lessons must match ALL active filter categories (AND logic)
- [ ] #5 When I clear all filters, then all lessons are visible again
- [ ] #6 Filter state persists across page refreshes (localStorage)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Root Cause Analysis

After code review, the data flow appears correct:
1. `useScheduleFilter()` → `filterState` (object with Set properties)
2. `filterState` → `useScheduleData()` → `passesFilter` callback
3. `passesFilter` → `lessonLookup` useMemo

The suspected issues:
1. **React dependency tracking**: The `passesFilter` callback may not trigger useMemo recalculation correctly when Set contents change
2. **Closure staleness**: The callback might capture stale `filterState` references
3. **No debug visibility**: Currently no logging to verify if filtering is actually being applied

## Implementation Strategy

### Phase 1: Debug Logging (Verify Root Cause)
Add temporary console.log statements to trace the data flow:
- In `toggleTeacher/toggleSubject/toggleGroup` - log when state changes
- In `passesFilter` - log when called and what it's filtering
- In `lessonLookup` useMemo - log when it recalculates

This will confirm whether:
- State changes are happening
- `passesFilter` is being called with correct values
- `lessonLookup` is being recalculated when filter changes

### Phase 2: Fix Based on Findings

**If Phase 1 shows state changes but no recalculation:**
The issue is React's dependency tracking. Fix by:
- Flatten `filterState` into individual dependencies in `useCallback`
- OR use a serializable key to force recalculation

**If Phase 1 shows state not changing:**
The issue is in the state management. Fix the `useScheduleFilter` hook.

**If Phase 1 shows filtering working but columns not hiding:**
Additional fix needed in `columns` useMemo to also filter by `filterState.groups`.

### Phase 3: Remove Debug & Polish
- Remove debug logging
- Add proper TypeScript types if needed
- Ensure localStorage persistence works correctly

## Files to Modify

1. `src/main/webui/src/hooks/useScheduleFilter.ts`
   - Add debug logging (temporary)
   - Verify state update logic

2. `src/main/webui/src/hooks/useScheduleData.ts`
   - Add debug logging (temporary)
   - Fix dependency arrays if needed
   - Consider adding `filterState` as direct dependency to `lessonLookup`

3. `src/main/webui/src/components/ScheduleTimeline.tsx`
   - Pass any missing dependencies
   - Verify data flow

## Fix Completed (2026-02-26)

**Root Cause**: The `lessonLookup` useMemo was depending on a `passesFilter` callback, but React's dependency tracking wasn't properly detecting when the filter state changed because the callback was capturing stale closure values.

**Solution**:

1. Created a serializable `filterKey` that changes whenever filter state changes

2. Inlined the filter logic directly in the useMemo (no intermediate callback)

3. Added `filterKey`, `hasActiveFilters`, and `filterState` as direct dependencies

**Files Modified**:

- `src/main/webui/src/hooks/useScheduleData.ts` - Fixed filter dependency tracking

**Testing**:

- Teacher filter works: selecting shows badge, count indicator

- Clear all removes filters correctly

- Multiple filters can be combined (AND logic)
<!-- SECTION:NOTES:END -->
