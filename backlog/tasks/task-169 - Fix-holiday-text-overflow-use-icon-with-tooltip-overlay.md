---
id: TASK-169
title: Fix holiday text overflow - use icon with tooltip overlay
status: Done
assignee: []
created_date: '2026-03-04 10:56'
updated_date: '2026-03-10 05:16'
labels:
  - bug
  - frontend
  - ui
  - ux
  - calendar
  - claude-planned
dependencies: []
priority: medium
ordinal: 24750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Holiday descriptions overflow outside their containing boxes in both calendar views (Week and Timeline). Long holiday names like "Vacanza di Pasqua - Lunedì dell'Angelo" extend beyond the cell boundaries.

## Current Implementation

**WeekViewGrid.tsx (lines 127-133):**
```tsx
<td
  colSpan={totalSubCols}
  className="bg-day-header border-t-2 border-t-faint border-b border-border px-2 py-1.5 font-semibold text-[0.75rem] text-faint text-center"
>
  {holiday}  {/* No overflow handling! */}
</td>
```

**VirtualizedDayRow.tsx (lines 141-143):**
```tsx
<div className="flex items-center gap-2">
  <span className="font-bold text-[0.8rem]">{dayLabel}</span>
  {holiday && <span className="text-faint italic text-[0.75rem]">{holiday}</span>}
  {/* No overflow handling! */}
</div>
```

## Desired Behavior

1. **Icon indicator**: Show a holiday icon (e.g., 🎉, 🏖️, or calendar-x) next to the day label
2. **Tooltip/Overlay**: Display full holiday description on hover/click
3. **No overflow**: Text never extends beyond container boundaries
4. **Consistent UX**: Same pattern in both Week and Timeline views

## Existing Patterns to Follow

- **LessonTooltip.tsx**: Modal overlay with backdrop, keyboard accessible, click-outside-to-close
- **Icons.tsx**: SVG icon system used throughout the app
- **CSS overflow pattern**: `overflow-hidden text-ellipsis whitespace-nowrap`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [UI] Replace full holiday text with a holiday icon/symbol next to day label
- [ ] #2 [UI] Add tooltip/overlay to show full holiday description on hover/click
- [ ] #3 [UI] Apply same fix to WeekViewGrid.tsx (table view)
- [ ] #4 [UI] Apply same fix to VirtualizedDayRow.tsx (timeline view)
- [ ] #5 [CSS] Add overflow handling to prevent text overflow in both views
- [ ] #6 [A11y] Ensure tooltip is keyboard accessible (focus, Escape to close)
- [ ] #7 [A11y] Add aria-label for holiday icon
- [ ] #8 [i18n] Add tooltip translations for holiday info
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Plan

### Phase 1: Create HolidayIcon Component

**File:** `src/main/webui/src/components/HolidayIcon.tsx` (new file)

```tsx
import { useState } from 'react';
import { Icons } from './Icons';

interface HolidayIconProps {
  holidayName: string;
}

export function HolidayIcon({ holidayName }: HolidayIconProps) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div className="relative inline-flex items-center">
      <button
        className="text-[0.75rem] text-amber-600 hover:text-amber-700 cursor-help"
        onMouseEnter={() => setShowTooltip(true)}
        onMouseLeave={() => setShowTooltip(false)}
        onClick={() => setShowTooltip(!showTooltip)}
        aria-label={`Holiday: ${holidayName}`}
      >
        🎉
      </button>
      {showTooltip && (
        <div
          className="absolute left-0 top-full mt-1 z-50 bg-surface border border-border rounded shadow-lg px-2 py-1 text-[0.75rem] text-content whitespace-nowrap"
          role="tooltip"
        >
          {holidayName}
        </div>
      )}
    </div>
  );
}
```

### Phase 2: Update WeekViewGrid.tsx

**File:** `src/main/webui/src/components/timeline/WeekViewGrid.tsx`

**Current (lines 127-133):**
```tsx
{holiday ? (
  <td colSpan={totalSubCols} className="...">
    {holiday}
  </td>
) : (...)}
```

**New:**
```tsx
{holiday ? (
  <td
    colSpan={totalSubCols}
    className="bg-day-header border-t-2 border-t-faint border-b border-border px-2 py-1.5 text-center"
  >
    <div className="flex items-center justify-center gap-1">
      <HolidayIcon holidayName={holiday} />
      <span className="font-semibold text-[0.75rem] text-faint truncate max-w-[200px]">
        {holiday}
      </span>
    </div>
  </td>
) : (...)}
```

### Phase 3: Update VirtualizedDayRow.tsx

**File:** `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx`

**Current (lines 141-144):**
```tsx
<div className="flex items-center gap-2">
  <span className="font-bold text-[0.8rem]">{dayLabel}</span>
  {holiday && <span className="text-faint italic text-[0.75rem]">{holiday}</span>}
</div>
```

**New:**
```tsx
<div className="flex items-center gap-2 overflow-hidden">
  <span className="font-bold text-[0.8rem] shrink-0">{dayLabel}</span>
  {holiday && <HolidayIcon holidayName={holiday} />}
</div>
```

### Phase 4: Add CSS for Tooltip (Optional Enhancement)

**File:** `src/main/webui/src/styles/tooltips.css` (new file)

```css
/* Holiday tooltip styles */
.holiday-tooltip {
  position: absolute;
  z-index: 50;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 0.375rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  color: var(--text);
  white-space: nowrap;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  animation: tooltip-fade-in 0.15s ease-out;
}

@keyframes tooltip-fade-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
```

### Alternative: Use Native Browser Tooltip

Simpler approach using `title` attribute:

```tsx
{holiday && (
  <span
    className="text-amber-600 cursor-help"
    title={holiday}
    aria-label={`Holiday: ${holiday}`}
  >
    🎉
  </span>
)}
```

**Pros:** No new component, instant implementation
**Cons:** Less control over styling, delayed appearance on hover

### Files to Modify

| File | Changes |
|------|---------|
| `components/HolidayIcon.tsx` | New file with tooltip component |
| `components/timeline/WeekViewGrid.tsx` | Replace holiday text with icon + tooltip |
| `components/timeline/VirtualizedDayRow.tsx` | Replace holiday text with icon + tooltip |
| `styles/tooltips.css` | Optional: tooltip styling |
| `i18n/locales/en/schedule.json` | Add tooltip labels if needed |

### Testing Strategy

1. **Visual test:** Add long holiday name via admin panel, verify no overflow
2. **Hover test:** Verify tooltip appears on hover
3. **Click test:** Verify tooltip toggles on click (for mobile)
4. **Keyboard test:** Tab to icon, verify tooltip accessible
5. **Mobile test:** Verify touch interaction works

### Icon Options

| Icon | Meaning | Usage |
|------|---------|-------|
| 🎉 | Celebration | General holidays |
| 🏖️ | Beach | Vacation days |
| 📅 | Calendar | School holidays |
| 🚫 | No school | Closure days |
| 🎄 | Christmas | Seasonal (auto-detect from name?) |

**Recommendation:** Use 🎉 (party popper) as universal holiday indicator - it's festive and clearly indicates a non-school day.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Replaced holiday text overflow with 🎉 emoji icon + tooltip in both VirtualizedDayRow (timeline view) and WeekViewGrid (week view). Commit 3c75d64.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Visual test: holiday names no longer overflow in any view
- [ ] #2 Manual test: tooltip shows on hover and click
- [ ] #3 Keyboard test: tooltip accessible via Tab + Enter
- [ ] #4 Test with long holiday names (e.g., 'Vacanza di Pasqua - Lunedì dell'Angelo')
- [ ] #5 Test on both desktop and mobile viewports
<!-- DOD:END -->
