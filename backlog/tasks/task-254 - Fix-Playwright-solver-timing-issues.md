---
id: TASK-254
title: Fix Playwright solver timing issues
status: Done
assignee: []
created_date: '2026-03-16 17:56'
labels:
  - e2e
  - playwright
  - timing
  - solver
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Tests that start the solver time out waiting for the Stop button to appear, suggesting the solver is not transitioning to SOLVING_ACTIVE state within the expected time window.

## Failing Tests
- solver-state-transitions.spec.ts: "button changes to Stop when SOLVING_ACTIVE"
- solver-state-transitions.spec.ts: "solving complete toast appears after transition to NOT_SOLVING"
- progression-button.spec.ts: "progression button opens modal after solver run"
- progression-button.spec.ts: "per-phase timeout limits are displayed after solver run"

## Root Cause
The solver's phase transition timing may be longer than expected:
1. Construction Heuristic phase may take >5 seconds to complete
2. Transition to SOLVING_ACTIVE may not happen immediately
3. Tests wait only 5 seconds for the button to change

## Investigation Needed
1. Measure actual time from solve click to Stop button appearance
2. Check if solver configuration has proper phase timeouts
3. Verify solver is actually starting (check logs)
4. Determine if 30-second wait is sufficient or needs adjustment

## Current State
- Wait time increased from 5s → 30s
- Some tests still timing out after 60 seconds
- Actionability timeout increased to 10 seconds

## Acceptance Criteria
- [ ] Determine actual time needed for solver to reach SOLVING_ACTIVE
- [ ] Update wait times to appropriate values (or use polling)
- [ ] All 4 solver timing tests pass consistently
- [ ] Tests are robust against variations in solver startup time

## References
- Test files: `src/main/webui/e2e/solver-state-transitions.spec.ts`, `src/main/webui/e2e/progression-button.spec.ts`
- Playwright test fixes summary: `/Users/user/planner/claudedocs/playwright-test-fixes-summary.md`
<!-- SECTION:DESCRIPTION:END -->

2026-10-03 triage batch: DUPLICATO di TASK-367 (stessi spec e2e solver-timing; la root cause era il polling del payload 16MB, curata dal TASK-380 light endpoint; TASK-367 e' piu' avanti con 13 green + 2 red residui tracciati).
