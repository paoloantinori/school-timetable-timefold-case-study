---
id: task-067
title: Align Timeline View Styling with Week View
status: Done
assignee:
  - claude
created_date: '2026-02-22 07:04'
updated_date: '2026-02-22 07:46'
labels:
  - ui
  - refactoring
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The Timeline view feels cramped compared to Week view after the CSS Grid alignment changes (task-065). This task aligns the visual styling between the two views and improves code reuse.

## Current Differences

| Aspect | Week View | Timeline View |
|--------|-----------|---------------|
| Cell height | 33px | 25px |
| Cell padding | `py-1 px-1` (4px) | none |
| Column borders | `borderRight: 1px solid var(--border-faint)` between each sub-column | No borders between grid columns |

## Implementation Approach

### Step 1: Add padding and height to Timeline cell container

In `TimelineView.tsx`, modify the grid cell container:
- Change `border-faint` to `border-light` for consistency with Week view
- Add `py-1 px-1` for padding (4px)
- Add `min-h-[33px]` to match Week view height

### Step 2: Add vertical borders between columns

Add borders to TimelineCell children for clearer separation:
- Use `border-r border-faint` on first two columns
- No border on last column

### Step 3: Extract shared styling constants

Create shared constants or a shared component for cell styling that both WeekViewGrid and TimelineView can use.

### Step 4: Adjust column widths if needed

With borders and padding, may need to adjust `COL_WIDTHS` to maintain total width.

## Files to Modify

1. `src/main/webui/src/components/timeline/TimelineView.tsx` - Container styling, padding, height
2. `src/main/webui/src/components/timeline/TimelineCell.tsx` - Border classes on child elements
3. Consider extracting shared styling to a common location

## Required Skill

Load the `frontend-design` skill before implementation:
```
/frontend-design:frontend-design
```

## Verification

Use Playwright to validate:
1. Cell height is ~33px
2. Padding is 4px  
3. Vertical borders exist between columns
4. All 3 groupBy modes (By Class, By Room, By Teacher) render correctly
5. Stage day styling still works

## Complexity Estimation

**Complexity: Medium** (3/5)

Rationale:
- CSS-only changes, no logic modification
- Requires coordination between two components
- Need to maintain visual consistency while adding code reuse
- Playwright validation adds testing overhead
- Risk of breaking existing layout is low but needs visual verification
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Timeline view cell height matches Week view (33px)
- [x] #2 Timeline view has 4px padding like Week view (py-1 px-1)
- [x] #3 Vertical borders between columns (subject | teacher/group | room) visible in Timeline view
- [x] #4 All 3 groupBy modes render correctly (By Class, By Room, By Teacher)
- [x] #5 Stage day styling preserved
- [x] #6 Playwright validation passes for height, padding, and borders
- [x] #7 Shared styling constants or utilities extracted for code reuse between Week and Timeline views
- [x] #8 Both views have consistent look and feel - visually indistinguishable styling for lesson cells
<!-- AC:END -->
