---
id: TASK-079
title: 'Timeline view: border rendering issues when scrolling horizontally'
status: Done
assignee: []
created_date: '2026-02-23 11:05'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: medium
ordinal: 133000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The Timeline view uses `react-window` for vertical virtualization. When scrolling horizontally to columns not in the initial viewport, cell borders appear missing or broken.

## Technical Analysis

### Current Border Implementation (VirtualizedDayRow.tsx)

**Cell container** (line 107):
```tsx
className={`border-r border-faint grid items-center flex-shrink-0 ...`}
```

**Empty cell sub-columns** (lines 114-117):
```tsx
<div className="text-center border-r border-faint" />
<div className="text-center border-r border-faint" />
<div className="text-center" />
```

### Root Causes

1. **CSS `border` vs `box-shadow`**: Borders add to element dimensions. During fast scrolls, subpixel rounding can cause borders to disappear.

2. **Tailwind `border-faint`**: Uses CSS variable `var(--border-faint)` which may not render consistently during scroll repaints.

3. **No GPU acceleration**: The cells don't have `will-change` or `transform: translateZ(0)` to force GPU compositing.

4. **Grid layout with borders**: CSS Grid's `gridTemplateColumns` calculates column widths, but borders add extra pixels that can cause misalignment during scroll.

## Solution Design

### Option A: Replace border with box-shadow (Recommended)

`box-shadow` doesn't affect layout and renders more consistently:

```tsx
// Replace border-r border-faint with:
style={{
  boxShadow: 'inset -1px 0 0 var(--border-faint)',
}}
```

### Option B: Add GPU acceleration

Add `will-change: transform` to force GPU compositing:

```tsx
style={{
  minWidth: getColumnWidth(groupBy),
  maxWidth: getColumnWidth(groupBy),
  willChange: 'transform',  // Force GPU layer
}}
```

### Option C: Use pseudo-element borders

Create CSS for consistent border rendering:

```css
.timeline-cell::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--border-faint);
}
```

## Recommended Approach

**Option A (box-shadow)** — Most reliable for scroll scenarios. Update:

1. Cell container (line 107): Replace `border-r border-faint` with inline `boxShadow`
2. Empty cell sub-columns (lines 114-117): Replace `border-r border-faint` with `boxShadow`

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/webui/src/components/timeline/VirtualizedDayRow.tsx` | Replace border with box-shadow |

## Visual Comparison

| Approach | Scroll Performance | Border Consistency | Code Change |
|----------|-------------------|-------------------|-------------|
| Current (border) | Artifacts | Inconsistent | — |
| box-shadow | Smooth | Consistent | Small |
| will-change | Smooth | May still artifact | Minimal |
| pseudo-element | Smooth | Consistent | Medium |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All cell borders render correctly when scrolling horizontally in Timeline view
- [ ] #2 Left border of each column is visible using box-shadow technique
- [ ] #3 Right border of sticky time column remains visible during scroll
- [ ] #4 No visual artifacts or missing borders in newly-visible columns after horizontal scroll
- [ ] #5 Border rendering is consistent in both Light and Dark themes
- [ ] #6 60fps scroll performance maintained (no frame drops)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Steps

### Step 1: Update cell container (line ~107)

**Before:**
```tsx
className={`border-r border-faint grid items-center flex-shrink-0 py-1 px-1 ...`}
```

**After:**
```tsx
className={`grid items-center flex-shrink-0 py-1 px-1 ...`}
style={{
  boxShadow: 'inset -1px 0 0 var(--border-faint)',
  background: isStageDay ? 'var(--stage-cell)' : 'transparent',
  gridTemplateColumns: getGridTemplateColumns(groupBy),
  minWidth: getColumnWidth(groupBy),
  maxWidth: getColumnWidth(groupBy),
}}
```

### Step 2: Update unavailable slot cell (line ~88)

**Before:**
```tsx
className="flex items-center justify-center border-r border-faint flex-shrink-0"
```

**After:**
```tsx
className="flex items-center justify-center flex-shrink-0"
style={{
  boxShadow: 'inset -1px 0 0 var(--border-faint)',
  background: 'repeating-linear-gradient(...)',
  minWidth: getColumnWidth(groupBy),
  maxWidth: getColumnWidth(groupBy),
}}
```

### Step 3: Update empty sub-columns (lines 114-117)

**Before:**
```tsx
<div className="text-center border-r border-faint" />
<div className="text-center border-r border-faint" />
<div className="text-center" />
```

**After:**
```tsx
<div className="text-center" style={{ boxShadow: 'inset -1px 0 0 var(--border-faint)' }} />
<div className="text-center" style={{ boxShadow: 'inset -1px 0 0 var(--border-faint)' }} />
<div className="text-center" />
```

## Testing

1. Open Timeline view with populated schedule
2. Scroll horizontally to right edge
3. Verify borders are visible on newly revealed columns
4. Scroll back and forth rapidly
5. Verify no border flickering or missing borders
6. Test in both Light and Dark themes
<!-- SECTION:NOTES:END -->
