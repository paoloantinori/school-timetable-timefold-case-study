---
id: TASK-063
title: Fix week view day header column widths
status: Done
assignee: []
created_date: '2026-02-21 09:52'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: high
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

In the WeekViewGrid component, the day header row cells (which show "STAGE" badges) don't have explicit widths set. This causes column width inconsistency, particularly visible on Monday which appears narrower than other days.

## Root Cause

Looking at `WeekViewGrid.tsx`, the sub-column cells (`<th>` and `<td>` in period rows) have explicit widths:

```tsx
style={{ minWidth: COL_WIDTHS.subjectCol, maxWidth: COL_WIDTHS.subjectCol }}
```

However, the **day header row cells** (lines 101-122) that show stage badges have NO width constraints:

```tsx
<td
  key={`${col.id}-${si}`}
  className="border-t-2 border-t-faint border-b border-border px-1 py-0.5 text-[0.6rem] text-center"
  // MISSING: width constraints
>
```

This causes the first visible day's columns to collapse differently than subsequent days.

## Visual Evidence

See screenshot: Monday columns appear narrower than Tue-Fri columns due to missing width constraints on the day header row cells.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given I view the schedule in Week mode, When I look at the day header rows, Then all day columns have equal widths
- [ ] #2 Given I navigate between weeks, When the grid renders, Then column widths remain consistent across all days
- [ ] #3 Given a day has a STAGE badge, When the badge is displayed, Then the column width is not affected by the badge presence
- [ ] #4 Given the grid has 5 days (Mon-Fri), When rendered, Then each day's columns sum to the same total width
- [ ] #5 Given I resize the browser window, When the grid reflows, Then column widths remain proportional and consistent
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Files to Modify

- `src/main/webui/src/components/timeline/WeekViewGrid.tsx` (primary)

## Solution

Add explicit width constraints to the day header row cells (lines 101-122).

### Step 1: Add width to day header cells

Find the day header row cells that currently lack width constraints:

```tsx
// Current (line ~110)
<td
  key={`${col.id}-${si}`}
  className="border-t-2 border-t-faint border-b border-border px-1 py-0.5 text-[0.6rem] text-center"
  style={isStageDay ? { ... } : { ... }}
>
```

Add the same width constraints used by sub-column header cells:

```tsx
// Fixed
<td
  key={`${col.id}-${si}`}
  className="border-t-2 border-t-faint border-b border-border px-1 py-0.5 text-[0.6rem] text-center"
  style={{ 
    minWidth: COL_WIDTHS.subjectCol, 
    maxWidth: COL_WIDTHS.subjectCol,
    ...(isStageDay ? {
      background: 'linear-gradient(180deg, var(--stage-bg) 0%, var(--stage-highlight) 100%)',
      borderTop: '2px solid var(--stage-border)',
      borderBottom: '1px solid var(--stage-border)',
    } : {
      background: 'var(--day-header-bg)',
      borderTop: '2px solid var(--text-faint)',
      borderBottom: '1px solid var(--border)',
    })
  }}
>
```

### Step 2: Also verify holiday row cells

If the holiday row (lines 170-190) has similar cells without width constraints, apply the same fix.

### Testing

1. Start the app and navigate to Schedule view
2. Switch to Week mode
3. Verify all day columns have equal widths
4. Navigate between weeks
5. Verify consistency persists
6. Check days with STAGE badges
7. Verify badge doesn't affect column width
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Single file, CSS fix |
| Risk | 1 | Visual only |
| Testing | 1 | Visual verification |
| Dependencies | 1 | None |
| **Total** | **1** | 30 minutes |
<!-- SECTION:NOTES:END -->
