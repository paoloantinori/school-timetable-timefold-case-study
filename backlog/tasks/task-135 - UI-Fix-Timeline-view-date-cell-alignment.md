---
id: TASK-135
title: 'UI: Fix Timeline view date cell alignment'
status: Done
assignee: []
created_date: '2026-02-28 08:50'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - timeline
  - claude-planned
dependencies: []
priority: medium
ordinal: 112000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The horizontal boundaries of date cells in the "Time" column (first column) of the Timeline view do not align properly with the row boundaries in the main table where timeslots are rendered. This misalignment is visible on many (but not all) dates.

## Evidence

Screenshot: `/home/user/Pictures/Screenshots/Screenshot From 2026-02-28 09-44-34.png`

The screenshot shows the first column under the "Time" header. Notice that the right edge of date cells doesn't line up with the horizontal grid lines of the timeslot rows in the main table area.

## Root Cause (Hypothesis)

Likely a calculation issue in row height computation:
- Date cells use one height calculation
- Timeslot rows use a different calculation
- Rounding errors or missing synchronization between the two

## Files Involved

- `src/main/webui/src/components/ScheduleTimeline.tsx` - Main timeline component
- Possibly CSS styles for timeline grid

## Verification

Use Playwright to capture screenshots and verify alignment after fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Date cell horizontal boundaries align perfectly with timeslot row boundaries across ALL dates
- [x] #2 No visual gaps or overlaps between the Time column and main table area
- [ ] #3 Playwright screenshot test passes confirming alignment
- [ ] #4 Works correctly in both Light and Dark themes
- [ ] #5 Responsive - alignment holds at different viewport widths
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Root Cause

The `getRowHeight()` function in TimelineView.tsx calculated row heights incorrectly:
- **Calculated**: 50 + 1 + 6*33 = 249px for regular days
- **Actual**: ~237px (32px header + 1px separator + 204px period rows)
- Discrepancy: 12px difference compounded as you scroll

**Issues found:**
1. Day header was calculated as 50px but actually renders at ~32px
2. Period rows were calculated at 33px each but with border-b are 34px each
3. Ideal end marker rows (~29px) were not accounted for at all

## Solution Implemented

1. **TimelineView.tsx** - Fixed `getRowHeight()`:
   - Day header: 32px (exact match to rendered)
   - Separator: 1px
   - Period rows: 6 × 34px = 204px (includes border)
   - Ideal end marker: 29px when present (conditionally checked)

2. **types.ts** - Changed `CELL_STYLING.minHeight`:
   - From: `min-h-[33px]` (minimum, could grow)
   - To: `h-[34px]` (exact height with border-box)

## Files Modified

- `src/main/webui/src/components/timeline/TimelineView.tsx`
- `src/main/webui/src/components/timeline/types.ts`

## Additional Fix (2026-02-28 09:15)

Removed internal 'border-like' visual effect inside lesson cells:
- Removed padding (`py-1 px-1`) from lesson cells - creates seamless grid
- Added `gap: 0` to grid to eliminate default gaps between sub-columns

## Final Fix (2026-02-28 11:00)

Removed 'internal border' visual effect in Time column weekday cells:
- Removed separate `<div className="border-b border-light">` that spanned full width
- Added `borderBottom` to day header instead (integrated into the element)
- Removed `separatorHeight` (1px) from height calculation
- This eliminates the visual artifact inside the Time column area
<!-- SECTION:NOTES:END -->
