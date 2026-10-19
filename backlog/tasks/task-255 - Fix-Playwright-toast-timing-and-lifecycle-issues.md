---
id: TASK-255
title: Fix Playwright toast timing and lifecycle issues
status: Done
assignee: []
created_date: '2026-03-16 17:56'
labels:
  - e2e
  - playwright
  - toast
  - timing
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Toast elements appear and disappear too quickly for tests to catch them, or toasts have different text/content than expected.

## Failing Tests
- solver-state-transitions.spec.ts: "pre-assign toast appears after completion (success or error)"
- progression-button.spec.ts: "progression shows pre-assignment completion message"

## Root Cause
1. Toasts may auto-dismiss before tests can verify them
2. Toast text format has changed (e.g., "Pre-assignment failed" vs expected pattern)
3. Multiple toasts exist, test selects wrong one
4. Timing between status change and toast appearance

## Investigation Needed
1. Capture actual toast text and timing
2. Check toast auto-dismiss timeout in UI code
3. Verify toast element lifecycle
4. Test both COMPLETE and ERROR pre-assignment outcomes

## Current State
- Tests wait 2-3 seconds after status change
- Toast selector uses `[role="alert"]`
- Regex patterns may not match actual toast text

## Acceptance Criteria
- [ ] Toast tests reliably find and verify toasts
- [ ] Tests handle both success and error toast variants
- [ ] Toast timing is consistent (or tests wait appropriately)
- [ ] All toast-related tests pass

## References
- Test files: `src/main/webui/e2e/solver-state-transitions.spec.ts`, `src/main/webui/e2e/preassign-toast.spec.ts`
- UI toast component: `src/main/webui/src/components/UnifiedSolverControls.tsx`
- Playwright test fixes summary: `/Users/user/planner/claudedocs/playwright-test-fixes-summary.md`
<!-- SECTION:DESCRIPTION:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
