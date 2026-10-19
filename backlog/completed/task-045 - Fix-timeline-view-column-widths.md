---
id: task-045
title: Fix timeline view column widths
status: Done
assignee: []
created_date: '2026-02-19 21:14'
updated_date: '2026-02-21 08:45'
labels:
  - claude-planned
  - ui
  - bug
dependencies: []
priority: high
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The timeline view (infinite mode) does not use fixed-width columns. This causes columns to resize based on content, making it very hard to read as users scroll through days. Data becomes misaligned with headers, and the visual consistency is broken.

## User Value

- Consistent visual alignment when scanning the schedule
- Headers stay aligned with data columns
- Professional, polished appearance
- Easier to compare schedules across different days/weeks

## Current Behavior

Looking at `ScheduleTimeline.tsx`, the week view has some fixed widths defined in `COLUMN_WIDTHS`:
```typescript
const COLUMN_WIDTHS = {
  timeColumn: '95px',
  subjectCol: '85px',
  teacherCol: '120px',
  roomCol: '80px',
};
```

However, the infinite/timeline view (around line 700+) uses `flex: 1` for data columns, which allows them to resize based on content. The `tableLayout: 'fixed'` is set in the week view but the infinite view uses a different structure (div-based with flexbox).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Each column has fixed width that does not change based on content
- [ ] #2 #2 Headers remain aligned with data cells during horizontal scroll
- [ ] #3 #3 Long text is truncated with ellipsis
- [ ] #4 #4 Both week and infinite modes use consistent column widths
- [ ] #5 #5 Fixed column widths are preserved on browser resize
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Files to Modify
- `src/main/webui/src/components/ScheduleTimeline.tsx` (primary)

### Root Cause Analysis

The week view uses an HTML `<table>` with `tableLayout: 'fixed'` and explicit column widths. The infinite view uses a div-based layout with `flex: 1` for data columns, which causes variable widths.

### Solution

Convert the infinite view to use fixed widths consistent with the week view:

1. **Define fixed column widths for all view modes**

Add to existing `COLUMN_WIDTHS` constant:
```typescript
const COLUMN_WIDTHS = {
  timeColumn: '95px',
  subjectCol: '85px',
  teacherCol: '120px',
  roomCol: '80px',
  groupCol: '100px',   // Add if needed for room/teacher group-by modes
};
```

2. **Update infinite view header cells**

In the infinite view header section (around line 710), change from `flex: 1` to fixed width:
```tsx
// Before
<div style={{ flex: 1, ... }}>Time</div>

// After  
<div style={{ minWidth: COLUMN_WIDTHS.timeColumn, maxWidth: COLUMN_WIDTHS.timeColumn, flex: 'none', ... }}>Time</div>
```

3. **Update infinite view data cells**

In `renderDayRow`, change cells from `flex: 1` to fixed width matching header:
```tsx
// In the cell mapping, replace:
<div style={{ flex: 1, ... }}>

// With:
<div style={{ 
  minWidth: CELL_WIDTH, 
  maxWidth: CELL_WIDTH, 
  flex: 'none',
  ...
}}>
```

4. **Calculate total width per group-by mode**

Each group-by mode shows different sub-columns:
- `studentGroup`: subject (85px) + teacher (120px) + room (80px) = 285px per group
- `room`: subject (85px) + group (100px) + teacher (120px) = 305px per room
- `teacher`: subject (85px) + group (100px) + room (80px) = 265px per teacher

5. **Add horizontal scroll if content exceeds viewport**

The wrapper should allow horizontal scroll:
```tsx
<div style={{ 
  overflowX: 'auto',
  width: '100%',
  ...
}}>
```

### Code Locations

1. **Week view table** (line ~290): Already uses fixed layout - OK
2. **Infinite view header** (line ~710): Needs fixed widths
3. **Infinite view `renderDayRow`** (line ~580): Needs fixed widths for time column and data cells

### Testing

1. Open timeline view
2. Verify columns don't resize when scrolling through days
3. Verify headers align with data cells
4. Test all three group-by modes (studentGroup, room, teacher)
5. Test with long teacher/group names - verify truncation with ellipsis
6. Test browser resize - columns should maintain fixed width, container should scroll

### CSS for text truncation

Ensure cells have overflow handling:
```css
.dataCell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

### Risks

| Risk | Mitigation |
|------|------------|
| Total width exceeds viewport | Add horizontal scroll to container |
| Columns too narrow for some names | Adjust widths based on real data; use reasonable truncation |
| Breakpoint issues on mobile | Consider responsive widths or collapse columns on small screens |
<!-- SECTION:NOTES:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Completed Implementation

### Changes Made

**TimelineView.tsx** (`src/main/webui/src/components/timeline/TimelineView.tsx`):
1. Added `groupCol: '100px'` to COL_WIDTHS constant
2. Created `getColumnWidth()` helper function that calculates total column width based on groupBy mode:
   - studentGroup: subject (85px) + teacher (120px) + room (80px) = 285px
   - room: subject (85px) + group (100px) + teacher (120px) = 305px
   - teacher: subject (85px) + group (100px) + room (80px) = 265px
3. Updated column headers to use fixed widths with `flex-shrink-0` and `overflow-hidden`
4. Updated data cells in renderDayRow to use fixed widths with `flex-shrink-0`
5. Added `overflow-auto` to the main container for horizontal scrolling

**types.ts** (`src/main/webui/src/components/timeline/types.ts`):
1. Added `groupCol: '100px'` to exported COLUMN_WIDTHS constant

**tailwind.config.js** (`src/main/webui/tailwind.config.js`):
1. Added `group-col` to width, minWidth, and maxWidth utilities
2. Added `group-cell: '90px'` to maxWidth for group name truncation

**TimelineCell.tsx** (`src/main/webui/src/components/timeline/TimelineCell.tsx`):
1. Fixed the subject cell background color (changed from invalid `bg-(--subject-color)` to proper style with `backgroundColor`)
2. Updated group cells to use `max-w-group-cell` instead of `max-w-teacher-cell`

## Complexity Score: **2/5** (Low-Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 2 | Single component, CSS changes |
| Risk | 2 | Visual only, no logic |
| Testing | 2 | Visual verification |
| Dependencies | 1 | None |
| **Total** | **2** | 1-2 hours |

---

## Priority: **HIGH** (Bug - Core Usability)

Column misalignment makes timeline unreadable. Affects core functionality daily.
<!-- SECTION:NOTES:END -->
