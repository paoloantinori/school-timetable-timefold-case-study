---
id: TASK-108
title: Fix sticky date header in TimelineView horizontal scroll
status: Done
assignee: []
created_date: '2026-02-23 22:04'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: medium
ordinal: 37750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

When scrolling the TimelineView horizontally (left/right), the day/date header row (e.g., "Lunedì 06/10/2025") scrolls out of view. The time column labels (08:00-14:00) are correctly sticky with `sticky left-0`, but the day header row above them is NOT sticky.

**Screenshot:** `/home/user/Pictures/Screenshots/Screenshot From 2026-02-23 23-02-29.png`

## Root Cause

In `VirtualizedDayRow.tsx`, the day header div (lines ~130-140) does not have `sticky left-0` positioning, while the time label div (lines ~162-170) does.

**Current day header (NOT sticky):**
```tsx
<div className="flex items-center justify-between py-[0.3rem] px-2" ...>
  <span className="font-bold text-[0.8rem]">{dayLabel}</span>
  ...
</div>
```

**Current time label (IS sticky):**
```tsx
<div className="... sticky left-0 z-[5]" style={{ background: 'var(--surface-alt)' }} ...>
  {pd.period.label}
</div>
```

## Solution

Make the day header row sticky on the left side, matching the time column's behavior:

1. Add `sticky left-0 z-[5]` to the day header container
2. Add matching background color to prevent content showing through
3. Ensure proper z-index hierarchy with the time column
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [ ] Day/date header row remains visible when scrolling horizontally left/right
- [ ] #2 [ ] Day header aligns visually with the sticky time column below it
- [ ] #3 [ ] No visual artifacts (content bleeding through, z-index conflicts)
- [ ] #4 [ ] Works in both Light and Dark themes (uses CSS variables)
- [ ] #5 [ ] No regression in Week view mode (which doesn't have horizontal scroll)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Location

`src/main/webui/src/components/timeline/VirtualizedDayRow.tsx`

## Required Changes

The day header div (around line 130) needs sticky positioning:

```tsx
{/* Day header - ADD sticky positioning */}
<div
  className="flex items-center justify-between py-[0.3rem] px-2 sticky left-0 z-[5]"
  style={{
    background: 'var(--surface-alt)',
    borderTop: isMonday ? '3px solid var(--border)' : '2px solid var(--text-faint)',
  }}
>
  <div className="flex items-center gap-2">
    <span className="font-bold text-[0.8rem]">{dayLabel}</span>
    {holiday && <span className="text-faint italic text-[0.75rem]">{holiday}</span>}
  </div>
</div>
```

**Key additions:**
1. `sticky left-0 z-[5]` to the className
2. `background: 'var(--surface-alt)'` moved from parent style to this element

## Z-Index Hierarchy

Both the day header and time labels should use `z-[5]` to stay above scrolling content.
<!-- SECTION:NOTES:END -->
