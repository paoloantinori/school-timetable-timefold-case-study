---
id: task-036
title: change the style of Friday's 6th timeslot in darkmode
status: Done
assignee: []
created_date: '2026-02-18 22:35'
updated_date: '2026-02-19 09:05'
labels:
  - ui
  - claude-planned
  - 'complexity:low'
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Bug Description

Friday's 6th timeslot (13:00-14:00) is rendered with a "hashed" unavailable pattern that uses hardcoded light-gray colors. In dark mode, these colors appear bright/washed out instead of matching the dark theme palette.

### Root Cause

The `unavailableCell` style in `ScheduleTimeline.tsx` uses hardcoded hex colors:
```javascript
unavailableCell: {
  background: 'repeating-linear-gradient(45deg, #f3f4f6, #f3f4f6 5px, #e5e7eb 5px, #e5e7eb 10px)',
  cursor: 'not-allowed',
},
```

These colors (`#f3f4f6` and `#e5e7eb`) are light grays that:
- Work correctly in light mode
- Appear too bright in dark mode (they don't use CSS variables)
- Break the visual consistency of the dark theme

### Fix Approach

Replace the hardcoded hex colors with CSS variables from `theme.tsx`:
- Use `var(--surface-hover)` or `var(--surface-alt)` for the base color
- Use `var(--border-light)` or `var(--border)` for the stripe color

This ensures the unavailable cell pattern adapts to the current theme (light/dark/school).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Friday 6th slot displays dark hashed pattern in dark mode
- [x] #2 Friday 6th slot displays light hashed pattern in light mode (current behavior preserved)
- [x] #3 Friday 6th slot displays appropriate pattern in school theme
- [x] #4 Infinite view (Timeline) also renders unavailable slots with theme-aware colors
- [x] #5 Holiday rows with unavailable slots also respect theme
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Files to Modify

### 1. `/home/user/data/repo/personal/planner/src/main/webui/src/components/ScheduleTimeline.tsx`

**Change 1: Update the `unavailableCell` style object** (around line 233)

Replace:
```javascript
unavailableCell: {
  background: 'repeating-linear-gradient(45deg, #f3f4f6, #f3f4f6 5px, #e5e7eb 5px, #e5e7eb 10px)',
  cursor: 'not-allowed',
},
```

With:
```javascript
unavailableCell: {
  background: 'repeating-linear-gradient(45deg, var(--surface-hover), var(--surface-hover) 5px, var(--border-light) 5px, var(--border-light) 10px)',
  cursor: 'not-allowed',
},
```

**Change 2: Update the inline style in `renderDayRow`** (around line 615)

The infinite view has an inline style that duplicates the hardcoded pattern. Replace:
```javascript
background: 'repeating-linear-gradient(45deg, #f3f4f6, #f3f4f6 5px, #e5e7eb 5px, #e5e7eb 10px)',
```

With:
```javascript
background: 'repeating-linear-gradient(45deg, var(--surface-hover), var(--surface-hover) 5px, var(--border-light) 5px, var(--border-light) 10px)',
```

## Testing

1. Start the app with a profile: `./planner.sh start dev`
2. Open the schedule view in a week that includes Friday
3. Switch to dark mode via the theme dropdown
4. Verify Friday's 6th slot (13:00-14:00) displays dark gray hashed pattern
5. Switch to light mode and verify the pattern remains visible but light
6. Switch to Timeline view and verify the same behavior

## CSS Variable Reference

From `theme.tsx`:
- `--surface-hover`: Light: `#f3f4f6`, Dark: `#334155`, school: `#ebe3ce`
- `--border-light`: Light: `#e5e7eb`, Dark: `#1e293b`, school: `#e8dcc8`
<!-- SECTION:NOTES:END -->
