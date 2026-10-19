---
id: TASK-078
title: Reset button doesn't trigger a reset operation
status: Done
assignee: []
created_date: '2026-02-23 11:04'
updated_date: '2026-03-09 19:50'
labels:
  - ui
  - bug
  - claude-planned
dependencies: []
priority: high
ordinal: 134000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The UI has two reset-related buttons in the Pre-assign controls row (in `UnifiedSolverControls.tsx`):
1. **"Reset Pins"** — Calls `POST /api/schedule/pre-assign/reset` to unpin pre-assigned entities only
2. **"Reset All"** — Calls `POST /api/schedule/pre-assign/reset-all` to clear ALL assignments (pinned + solver-assigned)

## Problem Analysis

### Code Flow (lines 279-304 in UnifiedSolverControls.tsx)
```
Reset button → onClick → handlePreAssign('reset', api.preAssignReset)
                                   ↓
                          setPreAssignLoading('reset')
                          api.preAssignReset()
                          onRefresh() ← calls fetchSchedule
```

### Root Cause
The reset buttons share the **same `canPreAssign` disabled logic** as pre-assign buttons (line 280):
```typescript
disabled={!canPreAssign}
```

This is problematic because `canPreAssign` includes checks that don't apply to reset:
- `recentlyCompleted` — blocks for 5 seconds after pre-assign completes (reset should ALWAYS be available)
- `hasLessons` — reset should work even if no lessons

### Actual Bug Location
Lines 278-305 in `UnifiedSolverControls.tsx`:
```tsx
<button
  onClick={() => setShowResetMenu(!showResetMenu)}
  disabled={!canPreAssign}  // ← BUG: Should use separate canReset logic
>
```

## Solution Design

### Step 1: Create separate `canReset` computed value

Add after line 95 (after `canPreAssign`):
```typescript
// Reset should only be blocked by active solving/pre-assigning
const canReset = !isSolving && !isPreAssigning && !preAssignLoading;
```

### Step 2: Update reset button and menu items

Replace `!canPreAssign` with `!canReset` on:
- Line 280: Reset dropdown button `disabled={!canReset}`
- Line 289: "Reset Pins" menu item `disabled={!canReset}`
- Line 296: "Reset All" menu item `disabled={!canReset}`

### Step 3: Remove title from reset buttons (optional)

The `preAssignDisabledReason` tooltip doesn't make sense for reset. Consider removing `title` prop or creating a separate reset-specific reason.

## Files to Modify

| File | Changes |
|------|---------|
| `src/main/webui/src/components/UnifiedSolverControls.tsx` | Add `canReset`, update disabled props |

## API Contract (No Changes Needed)

The API calls already work correctly:
- `api.preAssignReset()` → `POST /schedule/pre-assign/reset`
- `api.preAssignResetAll()` → `POST /schedule/pre-assign/reset-all`
- Both return `PreAssignmentResult` with `summary` and `count`
- `onRefresh()` is called after success to refresh UI
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clicking 'Reset Pins' button triggers POST /api/schedule/pre-assign/reset API call
- [ ] #2 Clicking 'Reset All' button triggers POST /api/schedule/pre-assign/reset-all API call
- [ ] #3 Reset buttons are enabled immediately after pre-assign completes (no 5-second wait)
- [ ] #4 Reset buttons are only disabled during active solving or pre-assignment
- [ ] #5 UI automatically refreshes (calls onRefresh) after reset completes
- [ ] #6 Console shows no errors when reset buttons are clicked
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Checklist

- [ ] Add `canReset` computed value after `canPreAssign` (line ~96)
- [ ] Replace `!canPreAssign` with `!canReset` on reset dropdown button (line 280)
- [ ] Replace `!canPreAssign` with `!canReset` on "Reset Pins" menu item (line 289)
- [ ] Replace `!canPreAssign` with `!canReset` on "Reset All" menu item (line 296)
- [ ] Remove `title={preAssignDisabledReason}` from reset buttons (or create reset-specific reason)

## Testing

1. Run pre-assign and wait for completion
2. Immediately click Reset — should work (previously blocked for 5 seconds)
3. Verify API call in Network tab
4. Verify UI refreshes after reset completes
5. Verify reset works even with empty schedule
<!-- SECTION:NOTES:END -->
