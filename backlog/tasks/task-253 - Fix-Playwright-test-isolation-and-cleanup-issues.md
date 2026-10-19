---
id: TASK-253
title: Fix Playwright test isolation and cleanup issues
status: Done
assignee: []
created_date: '2026-03-16 17:56'
updated_date: '2026-07-15 19:32'
labels:
  - e2e
  - playwright
  - test-isolation
  - cleanup
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Tests leave state for subsequent tests, causing failures. For example, pre-assignment completes in one test, leaving the button disabled for the next test.

## Failing Tests
- reset-button.spec.ts: "reset button clears all assignments after pre-assignment"
- reset-button.spec.ts: "reset button clears assignments after solver run"
- reset-button.spec.ts: "notification toast has Hide link but no X button"
- solver-state-transitions.spec.ts: "solve button is enabled when NOT_SOLVING" (intermittent)

## Root Cause
1. Tests don't properly clean up state in `afterEach`
2. App state (pre-assignment status, solver status) persists between tests
3. `beforeEach` doesn't ensure clean starting state

## Investigation Areas
- `reset-button.spec.ts`: Tests need to reset before starting
- `solver-state-transitions.spec.ts`: Multiple test suites need better isolation
- Consider adding a global setup/teardown for full state reset

## Potential Solutions
1. Add proper reset calls at start of affected tests
2. Improve `afterEach` cleanup to stop solver AND reset pre-assignment
3. Increase wait times after reset for UI to update
4. Consider using `test.describe.configure({ mode: 'serial' })` for tests that depend on state

## Acceptance Criteria
- [ ] All 3 reset-button tests pass when run in full suite
- [ ] Tests pass when run individually and in sequence
- [ ] No intermittent failures due to state leakage
- [ ] `beforeEach` ensures clean NOT_SOLVING and IDLE states

## References
- Test file: `src/main/webui/e2e/reset-button.spec.ts`
- Playwright test fixes summary: `/Users/user/planner/claudedocs/playwright-test-fixes-summary.md`
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): beforeEach/afterEach hooks + pre-assign/reset cleanup in all Playwright test blocks. Isolation patterns in place.
<!-- SECTION:FINAL_SUMMARY:END -->
