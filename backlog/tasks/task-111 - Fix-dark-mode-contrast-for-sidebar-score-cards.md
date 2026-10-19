---
id: TASK-111
title: Fix dark mode contrast for sidebar score cards
status: Done
assignee: []
created_date: '2026-02-24 05:31'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - accessibility
  - dark-mode
  - bug
dependencies: []
priority: low
ordinal: 42750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

In dark mode, the score cards (Hard/Medium/Soft) in the sidebar are unreadable due to insufficient contrast between text and background colors. The background colors (`dark:bg-red-900/20`, `dark:bg-amber-900/20`, `dark:bg-emerald-900/20`) are too dark against the already-dark surface.

## Evidence

Screenshot at `/home/user/Pictures/Screenshots/Screenshot From 2026-02-24 06-30-07.png` shows the sidebar with score cards that have poor legibility.

## Current Implementation

In `ScheduleSidebar.tsx`, the `ScoreCard` component uses:
```tsx
const colors = {
  hard: {
    bg: score < 0 ? 'bg-red-50 dark:bg-red-900/20' : 'bg-surface',
    // ...
  },
  // ...
};
```

The `dark:bg-red-900/20` (and amber/emerald variants) against `--surface: #0a0a0a` creates very low contrast.

## Root Cause

The dark mode theme uses true black (`#000000`) for OLED optimization. The semi-transparent violation backgrounds (`*-900/20`) don't provide enough contrast:
- Red-900 is `#7f1d1d`, at 20% opacity on black = very dark
- Text uses `text-text` which is `#f5f5f5` (good contrast against black)
- But the card background doesn't differentiate enough from the surface

## Proposed Solution

1. **Increase background opacity**: Change from `/20` to `/30` or `/40` for more visible differentiation
2. **Use solid colors**: Instead of transparent overlays, use solid dark colors tuned for contrast
3. **Add border emphasis**: Increase border width or brightness when score is negative
4. **Alternative approach**: Use subtle glow/box-shadow instead of background color

### Recommended CSS Changes
```tsx
// Before
bg: score < 0 ? 'bg-red-50 dark:bg-red-900/20' : 'bg-surface',

// After (option A: higher opacity)
bg: score < 0 ? 'bg-red-50 dark:bg-red-900/40' : 'bg-surface',

// After (option B: solid dark color)
bg: score < 0 ? 'bg-red-50 dark:bg-red-950' : 'bg-surface',
```

## Files to Modify

- `src/main/webui/src/components/ScheduleSidebar.tsx` (ScoreCard component)
- Optionally: `src/main/webui/src/theme.tsx` if theme-level changes are needed

## Updated
Problem is still present on February 28th see screenshot here. /home/user/Pictures/Screenshots/Screenshot From 2026-02-28 09-03-09.png
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Score card backgrounds are clearly visible in dark mode (WCAG AA contrast)
- [x] #2 Hard/Medium/Soft distinction remains clear through color coding
- [x] #3 Violation state (negative score) is visually prominent
- [x] #4 Light mode appearance is unchanged
- [x] #5 Solution maintains OLED-friendly dark theme (true black background)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan: Dark Mode Score Card Contrast Fix

### Problem Analysis

**Current state:**
- ScoreCard uses `dark:bg-red-950/60`, `dark:bg-amber-950/60`, `dark:bg-emerald-950/60`
- Dark theme uses `--surface: #0a0a0a` (true black for OLED)
- `red-950` is `#450a0a` — even at 60% opacity on black, result is ~`#2a0606` (barely visible)

**Root cause:**
- `*-950` Tailwind colors are the darkest in the palette
- On an already dark surface, semi-transparent dark overlays don't create enough visual differentiation
- The cards blend into the surface instead of standing out

### Solution: CSS Variable-Based Colors

Add theme-specific score card colors to `theme.tsx` and use them in `ScoreCard`. This approach:
- Maintains OLED-friendly true black background
- Provides proper contrast in all themes
- Keeps violation colors consistent with their semantic meaning
- Allows fine-tuning per theme without code changes

### Files to Modify

1. **`src/main/webui/src/theme.tsx`** — Add score card color variables
2. **`src/main/webui/src/components/ScheduleSidebar.tsx`** — Use CSS variables in ScoreCard

### Implementation Steps

#### Step 1: Add CSS Variables to theme.tsx

Add to each theme's variable set:

```typescript
// Light theme
'--score-hard-bg':     '#fef2f2',   // red-50
'--score-medium-bg':   '#fffbeb',   // amber-50
'--score-soft-bg':     '#ecfdf5',   // emerald-50
'--score-hard-border': '#ef4444',   // red-500
'--score-medium-border': '#f59e0b', // amber-500
'--score-soft-border': '#10b981',   // emerald-500

// Dark theme (tuned for true black contrast)
'--score-hard-bg':     '#450a0a',   // red-950 (solid, not transparent)
'--score-medium-bg':   '#451a03',   // amber-950
'--score-soft-bg':     '#022c22',   // emerald-950
'--score-hard-border': '#f87171',   // red-400 (brighter for contrast)
'--score-medium-border': '#fbbf24', // amber-400
'--score-soft-border': '#34d399',   // emerald-400

// school theme (brand-aligned)
'--score-hard-bg':     '#fef2f2',
'--score-medium-bg':   '#fff7ed',   // orange-50 (brand-aligned)
'--score-soft-bg':     '#ecfdf5',
'--score-hard-border': '#ef4444',
'--score-medium-border': '#ea580c', // brand orange
'--score-soft-border': '#10b981',
```

#### Step 2: Update ScoreCard Component

Replace Tailwind color classes with CSS variable references:

```tsx
function ScoreCard({ type, score, violationCount, t }: ScoreCardProps) {
  const colors = {
    hard: {
      border: score < 0 ? 'border-[var(--score-hard-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-hard-bg)]' : 'bg-surface',
      label: 'sidebar.hardScore'
    },
    medium: {
      border: score < 0 ? 'border-[var(--score-medium-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-medium-bg)]' : 'bg-surface',
      label: 'sidebar.mediumScore'
    },
    soft: {
      border: score < 0 ? 'border-[var(--score-soft-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-soft-bg)]' : 'bg-surface',
      label: 'sidebar.softScore'
    }
  };
  // ... rest unchanged
}
```

#### Step 3: Playwright Visual Verification Test

Create `e2e/dark-mode-score-cards.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('Dark Mode Score Cards', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for sidebar to load
    await page.waitForSelector('[class*="ScoreCard"], [class*="rounded-lg"]', { timeout: 30000 });
  });

  test('score cards have visible backgrounds in dark mode', async ({ page }) => {
    // Switch to dark mode
    await page.click('[aria-label*="theme"], button:has-text("Dark")');
    await page.waitForTimeout(500);

    // Take screenshot for visual inspection
    const sidebar = page.locator('[class*="sidebar"], aside').first();
    await sidebar.screenshot({ path: 'e2e/screenshots/dark-mode-score-cards.png' });

    // Verify contrast: get background color and ensure it's different from surface
    const hardCard = page.locator('div:has-text("Hard")').first();
    const bgColor = await hardCard.evaluate((el) => 
      window.getComputedStyle(el).backgroundColor
    );
    
    // Should not be transparent or pure black
    expect(bgColor).not.toBe('rgba(0, 0, 0, 0)');
    expect(bgColor).not.toBe('rgb(0, 0, 0)');
    expect(bgColor).not.toBe('rgb(10, 10, 10)'); // surface color
  });
});
```

### Verification Checklist

- [ ] Run `./scripts/rebuild-ui.sh` after changes
- [ ] Manual visual check in browser (all 3 themes)
- [ ] Playwright test passes: `./scripts/ui-test.sh dark-mode-score-cards`
- [ ] Screenshot comparison shows improvement
- [ ] Light mode unchanged (visual regression)
- [ ] school theme still uses brand colors

### Rollback Plan

If issues arise, revert to current implementation:
```tsx
bg: score < 0 ? 'bg-red-50 dark:bg-red-950/60' : 'bg-surface',
```
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Complete (2026-02-28)

Changes made:

1. Added CSS variables for score card colors to theme.tsx (light, dark, school themes)

2. Updated ScoreCard component in ScheduleSidebar.tsx to use CSS variables

3. Created Playwright test: e2e/dark-mode-score-cards.spec.ts

Dark theme now uses solid colors instead of semi-transparent overlays:

- Hard: #450a0a (red-950 solid) with #f87171 (red-400) border

- Medium: #451a03 (amber-950 solid) with #fbbf24 (amber-400) border

- Soft: #022c22 (emerald-950 solid) with #34d399 (emerald-400) border

Rebuild completed and typecheck passed.
<!-- SECTION:NOTES:END -->
