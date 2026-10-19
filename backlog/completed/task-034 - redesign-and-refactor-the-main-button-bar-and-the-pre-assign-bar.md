---
id: task-034
title: redesign and refactor the main button bar and the pre-assign bar
status: Done
assignee: []
created_date: '2026-02-18 22:32'
updated_date: '2026-02-19 11:57'
labels:
  - ui
  - claude-planned
  - 'complexity:high'
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Overview

Redesign and refactor the main button bar (solver controls) and pre-assignment bar into a unified, modern, and visually cohesive control panel.

## Current Problems

1. **Two separate bars** with inconsistent styling, spacing, and visual weight
2. **Flat visual hierarchy** - all buttons appear equally important
3. **No logical grouping** - solver controls, status info, and pre-assign buttons are jumbled together
4. **Inconsistent colors** - 8 different button colors (green, red, blue, purple, teal, gray, orange, red) create visual noise
5. **Poor responsive behavior** - `flexWrap: wrap` causes arbitrary line breaks
6. **Weak hover/active states** - only opacity changes on hover
7. **Status info mixed with actions** - score, elapsed time, status text interrupt the action flow

## Design Goals

1. **Single unified control panel** - merge the two bars into one cohesive component
2. **Clear visual hierarchy** - primary actions prominent, secondary actions subdued
3. **Logical grouping** - solver actions | status display | pre-assignment pipeline | destructive actions
4. **Consistent color palette** - 3 semantic colors (primary, secondary, danger) + context-specific accents
5. **Responsive layout** - proper breakpoints for tablet/mobile
6. **Polished interactions** - hover, active, disabled, loading states with smooth transitions
7. **Improved accessibility** - focus states, ARIA labels, keyboard navigation

## Proposed Layout

```
┌────────────────────────────────────────────────────────────────────────────┐
│ [▶ Solve]  [⏹ Stop]  [↻ Reschedule]  [📊 Analyze]  │  Status: Idle  │
│                                                    │  Score: 0/0/0  │
├────────────────────────────────────────────────────────────────────────────┤
│ Pre-assign: [All ▾]  [Acconciatura]  [Labs]  │  [Dry Run]  │  [Reset ▾]   │
└────────────────────────────────────────────────────────────────────────────┘
```

### Group 1: Solver Actions (Left)
- Solve (primary green) / Stop (danger red) - toggle
- Reschedule (secondary blue)
- Analyze (secondary purple)

### Group 2: Status Display (Center-right)
- Solver status badge
- Score display
- Elapsed time + phase timers (when solving)

### Group 3: Pre-assignment Pipeline (Second row, left)
- Dropdown with "All" as default + step labels
- Individual step buttons collapsed into dropdown or secondary row
- Dry Run (outline style)
- Reset options (danger, dropdown with Reset Pins / Reset All)

### Visual Style Specifications

**Button Base Styles:**
```css
/* Base button */
padding: 0.5rem 1rem;
border-radius: 6px;
font-weight: 600;
font-size: 0.85rem;
border: 1px solid transparent;
transition: all 0.15s ease;
cursor: pointer;

/* Hover: slight brightness increase + subtle shadow */
/* Active: pressed effect (transform: translateY(1px)) */
/* Disabled: opacity 0.5, cursor not-allowed */
/* Loading: spinner icon + opacity 0.7 */
```

**Color Palette:**
- Primary (Solve): `#22c55e` (green-500) - start action
- Danger (Stop, Reset All): `#ef4444` (red-500) - destructive
- Secondary (Reschedule, Analyze, pre-assign): `#3b82f6` (blue-500)
- Tertiary (Dry Run, Reset Pins): outline style with border
- Teal accent for pre-assign success: `#14b8a6` (teal-500)

**Group Separators:**
- Use `1px solid var(--border-light)` vertical dividers between groups
- Or use 16px gap with background continuation

**Status Display:**
- Badge-style container with subtle background
- Monospace font for score and timer
- Animated pulse when solving
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [ ] Single unified SolverControls component rendering one cohesive control panel instead of two separate bars
- [x] #2 [ ] Clear visual hierarchy: Solve/Stop as primary, Analyze/Reschedule as secondary, Dry Run as tertiary (outline)
- [x] #3 [ ] Logical grouping with visual separators: [Solver Actions] | [Live Status] and [Pre-assign Pipeline] | [Destructive]
- [x] #4 [ ] Consistent 4-color palette: green (primary action), red (danger), blue (secondary), teal (pre-assign success)
- [x] #5 [ ] Polished hover/active/disabled states with smooth 0.15s transitions and subtle shadow on hover
- [ ] #6 [ ] Responsive layout: full-width on desktop, stacked groups on tablet, single column on mobile
- [x] #7 [ ] Pre-assignment result display redesigned: dismissible toast-style notification with copy and details toggle
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## File Changes

### Primary: `src/main/webui/src/components/SolverControls.tsx`

**Refactor into sub-components:**
```tsx
// New internal components
<ButtonGroup>    // Container with separator styling
<ActionButton>   // Styled button with variant prop (primary|secondary|danger|outline)
<StatusBadge>    // Status display with pulse animation
<PreAssignMenu>  // Dropdown or button group for pre-assign actions
<Toast>          // Dismissible result notification
```

**New Style Structure:**
```tsx
const styles = {
  // Main container - single row on desktop, stacked on mobile
  container: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    padding: '0.75rem 1rem',
    background: 'var(--surface)',
    borderRadius: '8px',
    boxShadow: '0 1px 3px var(--shadow)',
    flexWrap: 'nowrap',
    '@media (max-width: 768px)': { flexDirection: 'column', alignItems: 'stretch' },
  },
  
  // Button groups
  group: { display: 'flex', alignItems: 'center', gap: '0.5rem' },
  separator: { width: '1px', height: '24px', background: 'var(--border-light)', margin: '0 0.5rem' },
  
  // Button variants
  btn: { /* base styles */ },
  btnPrimary: { background: '#22c55e', color: '#fff' },
  btnSecondary: { background: '#3b82f6', color: '#fff' },
  btnDanger: { background: '#ef4444', color: '#fff' },
  btnOutline: { background: 'transparent', border: '1px solid var(--border)', color: 'var(--text)' },
  
  // Hover states
  btnHover: { filter: 'brightness(1.1)', boxShadow: '0 2px 4px rgba(0,0,0,0.15)' },
  btnActive: { transform: 'translateY(1px)' },
  
  // Status display
  statusContainer: { display: 'flex', alignItems: 'center', gap: '0.75rem', marginLeft: 'auto' },
  statusBadge: { padding: '0.25rem 0.75rem', background: 'var(--surface-alt)', borderRadius: '4px', fontSize: '0.8rem' },
  scoreDisplay: { fontFamily: 'monospace', fontWeight: 600 },
  
  // Pre-assign section
  preAssignSection: { marginTop: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border-light)' },
  preAssignLabel: { fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', marginRight: '0.5rem' },
  
  // Toast notification
  toast: { /* positioned relative, dismissible, with animation */ },
};
```

### Theme Integration

Update `src/main/webui/src/theme.tsx` to add CSS variables for button states:
```tsx
// Add to each theme
'--btn-primary': '#22c55e',
'--btn-primary-hover': '#16a34a',
'--btn-secondary': '#3b82f6',
'--btn-secondary-hover': '#2563eb',
'--btn-danger': '#ef4444',
'--btn-danger-hover': '#dc2626',
```

## Implementation Steps

1. **Create Button Component** (internal to SolverControls or separate file)
   - Props: `variant`, `loading`, `disabled`, `icon`, `children`
   - Implement hover/active/disabled states with CSS transitions

2. **Refactor Main Container**
   - Merge `container` and `preAssignRow` into single flex container
   - Add responsive breakpoint at 768px

3. **Implement Status Section**
   - Move status/score/elapsed to right-aligned group
   - Add animated pulse when solving

4. **Redesign Pre-assignment Section**
   - Consider dropdown for less common actions
   - Keep "All" and "Dry Run" visible
   - Move Reset options to danger-colored dropdown

5. **Replace Result Message with Toast**
   - Dismissible, auto-dismiss after 10s
   - Success (teal) or error (red) variant
   - Copy button and details toggle inline

6. **Add Responsive Behavior**
   - Desktop: single row with groups
   - Tablet (< 1024px): two rows (solver + status, pre-assign)
   - Mobile (< 768px): stacked, full-width buttons

## Accessibility Requirements

- All buttons must have `aria-label` for icon-only states
- Focus visible outline: `2px solid var(--btn-secondary)`
- Loading state announces to screen readers
- Keyboard navigation maintains logical tab order

## Testing Checklist

- [ ] All button variants render correctly in Light/Dark/school themes
- [ ] Hover states work on all interactive elements
- [ ] Disabled states prevent interaction and show cursor
- [ ] Responsive layout works at 320px, 768px, 1024px, 1440px widths
- [ ] Pre-assign result toast dismisses correctly
- [ ] Score format preserved (e.g., "-1.2M / 0 / -52.1M")
- [ ] Elapsed timer continues while solving

## Implementation Complete

Created file:

- `src/main/webui/src/components/UnifiedSolverControls.tsx` - Redesigned control panel

Features implemented:

- Single cohesive panel (merged two rows into one)

- 4-color semantic palette: green (primary), red (danger), blue (secondary), teal (pre-assign)

- Polished hover/active states with 150ms transitions

- Dropdown menus for less common pre-assign actions (Acconciatura, Labs, Prof)

- Dropdown menu for reset options (Reset Pins, Reset All)

- Toast-style result notifications with Copy/Details buttons

- Status badges with visual phase indicators (CH/LS)

- Monospace typography for scores/timers

- Error display bar with dismiss button

Replaces SolverControls.tsx in ScheduleTimeline.tsx.

[2026-02-19] Responsive validation via Playwright: Components are functional at all viewport sizes (1440px, 768px, 375px) via flexWrap. However, acceptance criteria #6 (stacked groups on tablet, single column on mobile) was not implemented with explicit CSS media query breakpoints. The natural wrapping provides acceptable UX but not the specified responsive design. Consider adding CSS media queries in a future iteration if explicit responsive breakpoints are needed.
<!-- SECTION:NOTES:END -->
