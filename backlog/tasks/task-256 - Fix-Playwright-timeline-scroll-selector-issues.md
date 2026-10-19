---
id: TASK-256
title: Fix Playwright timeline scroll selector issues
status: Done
assignee: []
created_date: '2026-03-16 17:56'
labels:
  - e2e
  - playwright
  - timeline
  - selectors
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
Timeline scroll tests fail because selectors don't match actual DOM elements, or boundingBox calculations return null/NaN.

## Failing Tests
- timeline-scroll.spec.ts: "header syncs with content when scrolling horizontally"
- timeline-scroll.spec.ts: "time column stays sticky during horizontal scroll"

## Root Cause
1. Timeline view has different DOM structure than expected
2. `th:has-text("Time")` selector doesn't find any element
3. `text=/08:00/` selector not matching actual time labels
4. Elements may not render immediately or at all in Timeline view
5. `boundingBox()` returns null for elements that exist but aren't rendered

## Investigation Needed
1. Inspect actual Timeline view DOM structure using Playwright inspector or browser DevTools
2. Verify which view the tests are targeting (Week vs Timeline)
3. Check if elements have different data-testid attributes
4. Determine if virtualization is hiding elements

## Current State
- Tests attempt to switch to Timeline view before testing
- Selectors simplified from complex combinations
- Null checks added for graceful skipping
- Tests currently skip when elements not found

## Potential Solutions
1. Update selectors to match actual DOM structure
2. Use more specific data-testid attributes if available
3. Add explicit waits for elements to render
4. Consider using `page.locator().or()` for multiple selector options
5. May need to add test-specific data-testid attributes to components

## Acceptance Criteria
- [ ] Timeline scroll tests find required elements
- [ ] Tests verify sticky header behavior works correctly
- [ ] Tests verify horizontal scroll sync works
- [ ] No NaN values in position calculations
- [ ] Tests pass consistently without skipping

## References
- Test file: `src/main/webui/e2e/timeline-scroll.spec.ts`
- Timeline components: `src/main/webui/src/components/timeline/`, `src/main/webui/src/components/WeekViewGrid.tsx`
- Playwright test fixes summary: `/Users/user/planner/claudedocs/playwright-test-fixes-summary.md`
<!-- SECTION:DESCRIPTION:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
