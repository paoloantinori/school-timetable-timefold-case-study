---
id: TASK-085
title: Fix text overflow/overlap in Timeline view timeslot cells
status: Done
assignee: []
created_date: '2026-02-23 14:07'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - timeline
  - css
  - bug
  - claude-planned
dependencies: []
priority: medium
ordinal: 129000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Timeline view cells show text overflow and overlap when lesson labels (teacher names, subjects, rooms) exceed their container width. The issue is most visible with:
- Long subject names (e.g., "Disegno edile ed impiantistico")
- Long teacher names
- Room names with descriptions

## Root Cause Analysis

The CSS uses `text-ellipsis` on `inline-block` spans, but **CSS `text-overflow: ellipsis` requires explicit width constraints**. Current implementation:

```css
/* cellRenderer.tsx - mutedSpan, faintSpan */
whitespace-nowrap overflow-hidden text-ellipsis text-center inline-block
```

Missing: `max-width` or explicit `width` on the spans.

## Design Solution

### 1. Fix CSS Text Truncation (Required)

**Approach A: CSS Grid sub-column width inheritance**
```css
.sub-col {
  min-width: 0; /* Critical for grid children */
}
.sub-col span {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
```

**Approach B: Explicit max-width per column type**
```tsx
// In cellRenderer.tsx, pass column width to render functions
const MAX_WIDTHS = {
  subject: '85px',
  teacher: '120px', 
  room: '80px',
  group: '100px'
};
```

### 2. Tooltip Enhancement (Recommended)

When text is truncated, show full content on hover:
- Native `title` attribute already exists but is basic
- Consider custom tooltip component for better UX

### 3. Responsive Font Sizing (Optional)

Current: `text-[0.65rem]` (10.4px) - very small but legible
Consider CSS clamp() for adaptive sizing in narrow columns.

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/webui/src/components/timeline/cellRenderer.tsx` | Add width constraints to spans |
| `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx` | Pass width info to cell renderer |
| `src/main/webui/src/components/timeline/types.ts` | Consider adding width classes to COLUMN_WIDTHS |
| `src/main/webui/src/index.css` | Optional: Add utility classes for text truncation |

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Long subject names are truncated with "..." ellipsis
- [x] #2 Long teacher names are truncated with "..." ellipsis  
- [x] #3 Long room names are truncated with "..." ellipsis
- [x] #4 No text overlaps adjacent cells or columns
- [ ] #5 Hover tooltip shows full text content
- [x] #6 Week view and Timeline view both fixed
- [x] #7 No horizontal scrollbar appears in cells
- [x] #8 Text remains legible at current font size
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Fix Implementation (2026-02-23)

### Root Cause
CSS `text-overflow: ellipsis` requires explicit width constraints. The `mutedSpan`, `faintSpan`, and `subjectBadge` functions use `inline-block` with `text-ellipsis` but no `max-width` or `width` inheritance.

### Solution
1. Add `w-full` class to spans in `cellRenderer.tsx` so they inherit parent width
2. Add `min-w-0` to grid cells in `VirtualizedDayRow.tsx` to allow shrinking
3. Apply similar fixes to `WeekViewGrid.tsx` table cells if needed

## Implementation Complete (2026-02-23)

### Changes Made

**1. cellRenderer.tsx** - Added `w-full` to spans for width inheritance:
- `subjectBadge()`: Added `w-full` class
- `mutedSpan()`: Added `w-full` class
- `faintSpan()`: Added `w-full` class
- `renderInlineCell()`: Added `min-w-0` to all 3 wrapper divs

**2. VirtualizedDayRow.tsx** - Added `min-w-0` for grid shrinking:
- Grid cell container: Added `min-w-0`
- Empty placeholder divs: Added `min-w-0` to all 3

**3. WeekViewGrid.tsx** - Added `min-w-0` to table cells

### Technical Notes
CSS `text-overflow: ellipsis` requires:
1. `w-full` (width: 100%) - spans inherit parent width
2. `min-w-0` (min-width: 0) - grid/table children can shrink below content size

### AC Status
- #5 (hover tooltip) - Already implemented via native `title` attribute
<!-- SECTION:NOTES:END -->
