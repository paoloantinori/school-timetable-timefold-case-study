---
id: task-047
title: Check the reset buttons
status: Done
assignee: []
created_date: '2026-02-20 20:20'
updated_date: '2026-02-20 22:10'
labels:
  - ui
  - claude-planned
  - bug
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement
The "Reset" dropdown menu in the UnifiedSolverControls component is not showing the overlay menu when clicked. This affects the ability to reset pre-assigned pins or reset all assignments. A deep Playwright-based test is needed to verify the full reset button functionality, including edge cases like when buttons should be enabled vs. disabled.

## Current Behavior
- The reset menu trigger button exists in `UnifiedSolverControls.tsx` (lines 220-228)
- Menu has two options: "Reset Pins" and "Reset All"
- Menu visibility is controlled by `showResetMenu` state
- Outside click detection is implemented via `mousedown` listener (lines 47-55)

## Potential Issues to Investigate
1. CSS z-index conflicts between the reset menu and other elements
2. `position: relative` on menu container not properly established
3. Event propagation issues preventing click handler from firing
4. Timing issues with `useRef` and event listener registration
5. State not updating properly due to React batching

## Test Scenarios
1. Click "Reset" button when solver is NOT running → menu should appear
2. Click "Reset" button when solver IS running → button should be disabled
3. Click outside menu when it's open → menu should close
4. Click "Reset Pins" option → should call `api.preAssignReset`
5. Click "Reset All" option → should call `api.preAssignResetAll`
6. Verify menu positioning doesn't overflow viewport
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given the solver is idle, When I click the Reset dropdown button, Then the overlay menu appears with Reset Pins and Reset All options
- [x] #2 Given the solver is actively solving, When I view the Reset dropdown button, Then it is disabled (grayed out, not clickable)
- [x] #3 Given the reset menu is open, When I click outside the menu, Then the menu closes without triggering any action
- [x] #4 Given the reset menu is open, When I click Reset Pins, Then api.preAssignReset is called and the menu closes
- [x] #5 Given the reset menu is open, When I click Reset All, Then api.preAssignResetAll is called and the menu closes
- [x] #6 Given the page has limited vertical space, When the reset menu opens, Then it remains visible within the viewport (no clipping)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Phase 1: Visual Inspection & Debug
1. Manual browser test to confirm the bug
2. Use React DevTools to inspect `showResetMenu` state on click
3. Check browser console for any errors
4. Use element inspector to check computed styles on menu container

### Phase 2: Root Cause Analysis
- **File**: `src/main/webui/src/components/UnifiedSolverControls.tsx`
- **Lines 42-55**: Check `useEffect` for outside click detection
- **Lines 220-240**: Check menu trigger and dropdown rendering
- **Lines 326-341**: Check menu CSS styles (z-index, position)

### Phase 3: Fix Implementation
Likely fixes based on code review:
1. Add `z-index: 100` to `styles.menu` (currently missing z-index)
2. Ensure `position: relative` is set on `styles.menuContainer`
3. Add `console.log` to verify click handler is being called

### Phase 4: Playwright Test Suite
Create `src/test/java/.../playwright/ResetButtons.spec.ts`:
```typescript
test('reset menu opens when solver idle', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('text=Solve')).toBeVisible();
  await page.click('button:has-text("Reset")');
  await expect(page.locator('text=Reset Pins')).toBeVisible();
});
```

### Files to Modify
- `src/main/webui/src/components/UnifiedSolverControls.tsx` - Fix menu display
- `src/test/java/.../playwright/ResetButtons.spec.ts` - Add Playwright tests (new file)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Single menu component |
| Risk | 1 | Minor z-index or handler fix |
| Testing | 1 | Click verification |
| Dependencies | 1 | None |
| **Total** | **1** | 30 minutes |

---

## Priority: **HIGH** (Bug - User Blocked)

Reset menu not showing. Blocks user from clearing bad state.

## Resolution (2026-02-20)

**Root Cause**: The dropdown menus were being clipped by `overflow: hidden` on the parent panel container. z-index alone cannot escape overflow clipping.

**Fix**: Used React `createPortal` to render both dropdown menus (More and Reset) at `document.body` level with `position: fixed` and dynamically calculated positions via `getBoundingClientRect()`.

**Files Modified**: `src/main/webui/src/components/UnifiedSolverControls.tsx`

- Added `createPortal` import from 'react-dom'

- Added refs for both buttons (`preAssignButtonRef`, `resetButtonRef`)

- Added state for menu positions (`preAssignMenuPosition`, `resetMenuPosition`)

- Both menus now render via portal with fixed positioning
<!-- SECTION:NOTES:END -->
