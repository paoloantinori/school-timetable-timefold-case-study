---
id: task-030
title: ui
status: Done
assignee: []
created_date: '2026-02-18 09:24'
updated_date: '2026-02-18 21:35'
labels:
  - ui
  - bug
  - layout
dependencies: []
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Fix the size of the columns in the UI instead of having them determined by the length of the strings. Having them of variable size results in a distracting jumping left and right of the main content after each table update.

---

## Redesign Plan

### Original Implementation
- **Commit**: `8723a8e` (Add stage day highlighting and copyable pre-assignment report)
- **Files affected**: `ScheduleTimeline.tsx`
- **Current state**: Columns use `width: max-content` with no fixed sizing. Column widths jump as content changes during solver updates.

### Design Direction
- **Aesthetic**: Swiss Precision - locked-in, unshakeable grid that feels like a professional scheduling tool
- **Key differentiator**: Columns that never, ever move - even when data updates
- **Typography**: Fixed-width cells with consistent padding
- **Color**: No change needed
- **Motion**: No animation on column resize (because resize is impossible)

### Implementation Steps

1. **Set fixed column widths in table CSS**
   ```tsx
   const COLUMN_WIDTHS = {
     timeColumn: '95px',     // Fixed for time labels
     subjectCol: '85px',     // Subject name
     teacherCol: '120px',    // Teacher name
     roomCol: '80px',        // Room name
   };
   ```

2. **Update thSub and cell styles**
   ```tsx
   thSub: {
     // ...existing...
     minWidth: COLUMN_WIDTHS.subjectCol,
     maxWidth: COLUMN_WIDTHS.subjectCol,
     overflow: 'hidden',
     textOverflow: 'ellipsis',
   },
   ```

3. **Add text truncation with tooltip for overflow**
   - Cells that overflow show `...` with full text on hover
   - Use native `title` attribute for simplicity

4. **Make subject badge width proportional**
   ```tsx
   subjectCell: {
     // ...existing...
     maxWidth: '75px',
     display: 'inline-block',
     overflow: 'hidden',
     textOverflow: 'ellipsis',
   }
   ```

5. **Set table layout to fixed**
   ```tsx
   table: {
     borderCollapse: 'collapse',
     fontSize: '0.7rem',
     width: 'max-content',
     minWidth: '100%',
     tableLayout: 'fixed', // Add this
   },
   ```

6. **Calculate total grid width once**
   - Store computed width in state
   - Prevents recalculation on every render

### Testing Checklist
- [ ] Columns stay same width regardless of content
- [ ] Long text is truncated with ellipsis
- [ ] Hover shows full text in tooltip
- [ ] Table doesn't jump during solver updates
- [ ] Horizontal scrollbar still works for many columns
- [ ] Print layout isn't broken

### Files to Modify
- `src/main/webui/src/components/ScheduleTimeline.tsx`
<!-- SECTION:DESCRIPTION:END -->
