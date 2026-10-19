---
id: task-065
title: Fix Timeline View Tabular Alignment
status: Done
assignee:
  - Claude
created_date: '2026-02-22 06:26'
updated_date: '2026-02-22 06:40'
labels:
  - ui
  - timeline
  - css-grid
  - layout
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The timeline view currently centers the three sub-cells (subject, teacher/group, room) within each lesson cell using `justify-center`. This causes visual misalignment when content lengths vary - short subjects like "Matematica" don't line up with longer ones like "Informatica", and the same issue applies to teacher names and room names.

## Solution (CSS Grid Approach)

### 1. Change Cell Container to CSS Grid

**File**: `src/main/webui/src/components/timeline/TimelineView.tsx`

Replace flex with CSS grid, defining explicit column widths:

```tsx
// Before:
<div
  className={`px-[0.2rem] py-[0.1rem] border-r border-faint flex items-center justify-center gap-1 flex-shrink-0 ${lesson ? 'cursor-pointer' : 'cursor-default'}`}
  style={{
    background: isStageDay ? 'var(--stage-cell)' : 'transparent',
    minWidth: getColumnWidth(groupBy),
    maxWidth: getColumnWidth(groupBy),
  }}
>

// After:
<div
  className={`border-r border-faint grid items-center flex-shrink-0 ${lesson ? 'cursor-pointer' : 'cursor-default'}`}
  style={{
    background: isStageDay ? 'var(--stage-cell)' : 'transparent',
    gridTemplateColumns: groupBy === 'studentGroup'
      ? '85px 120px 80px'
      : groupBy === 'room'
      ? '85px 100px 120px'
      : '85px 100px 80px',
    minWidth: getColumnWidth(groupBy),
    maxWidth: getColumnWidth(groupBy),
  }}
>
```

### 2. Center Text in TimelineCell

**File**: `src/main/webui/src/components/timeline/TimelineCell.tsx`

Add `text-center` to each span. Remove `max-w-*` classes since grid handles sizing:

```tsx
// Subject cell:
<span
  className="inline-block rounded-sm px-[0.3rem] py-[0.1rem] text-[0.65rem] font-semibold text-white whitespace-nowrap overflow-hidden text-ellipsis text-center"
  ...
>

// Other cells (teacher/group/room):
<span
  className="text-[0.65rem] text-muted whitespace-nowrap overflow-hidden text-ellipsis text-center inline-block"
  ...
>
```

## Files to Modify

1. `src/main/webui/src/components/timeline/TimelineView.tsx` - Change container to CSS Grid
2. `src/main/webui/src/components/timeline/TimelineCell.tsx` - Add text-center, remove max-w classes

## Implementation Instructions

Use the frontend-design plugin for implementation:
```
/frontend-design:frontend-design
```

When prompted, specify:
- Apply CSS Grid layout to TimelineView.tsx cell containers
- Add text-center alignment to TimelineCell.tsx spans
- Remove max-width constraints (grid handles column sizing)

## Playwright Validation

After implementation, validate with Playwright:

```javascript
// Navigate to timeline view
await page.goto('http://localhost:8080');
await page.click('button:has-text("Timeline")');

// Check that subject cells have consistent left positions
const subjectCells = await page.$$eval('[data-testid="subject-cell"]', cells => 
  cells.map(c => c.getBoundingClientRect().left)
);
const allSubjectsAligned = subjectCells.every(x => x === subjectCells[0]);

// Check teacher/group cells alignment
const teacherCells = await page.$$eval('[data-testid="teacher-cell"]', cells => 
  cells.map(c => c.getBoundingClientRect().left)
);
const allTeachersAligned = teacherCells.every(x => x === teacherCells[0]);

// Check room cells alignment  
const roomCells = await page.$$eval('[data-testid="room-cell"]', cells => 
  cells.map(c => c.getBoundingClientRect().left)
);
const allRoomsAligned = roomCells.every(x => x === roomCells[0]);

console.log({ allSubjectsAligned, allTeachersAligned, allRoomsAligned });
```

If cells don't have data-testid attributes, add them during implementation for validation.

## Manual Verification

1. Start app: `./planner.sh start dev && ./planner.sh wait`
2. Open browser to `http://localhost:8080`
3. Switch to Timeline view
4. Verify:
   - Subject column aligns vertically across all rows
   - Teacher/Group column aligns vertically
   - Room column aligns vertically
   - Text is centered within each column
5. Test all three groupBy modes (By Class, By Room, By Teacher)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Subject column aligns vertically across all rows in Timeline view
- [x] #2 Teacher/Group column aligns vertically across all rows
- [x] #3 Room column aligns vertically across all rows
- [x] #4 Text is centered within each column cell
- [x] #5 All three groupBy modes (By Class, By Room, By Teacher) work correctly
- [x] #6 Playwright validation passes - all cells in same column have identical left position
<!-- AC:END -->
