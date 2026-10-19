---
id: TASK-367
title: E2E suite triage for the 2026/27 world (legacy specs assume the old seed)
status: In Progress
assignee: []
created_date: '2026-07-17 01:47'
updated_date: '2026-07-19 12:33'
labels:
  - claude-planned
  - spalmone-2027
  - test
milestone: m-9
dependencies: []
priority: medium
ordinal: 191000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: first full e2e run against the 2026/27 seed + golden import (2026-07-17 night): the two new suites pass (course-grid 4/4+1 by-design skip, group-calendar 3/3) but ~15 legacy tests fail for DATA-STATE reasons, not UI regressions: (1) pre-assignment returns status ERROR when LabRotation is empty (TASK-343 emptied it pending TASK-364) — preassign-toast/progression/reset specs cascade-fail; empirically answers the TASK-346 review question: zero rotations is an ERROR, not a clean no-op — decide and fix the service behavior (no-op with clear message is probably right); (2) specs assume unpinned solver-generated lessons while the golden profile is 100 percent pinned; (3) t28-h7-violation.spec.ts is obsolete by name and data (T28 no longer exists); (4) solver-state-transition specs time out mid-solve on the pinned profile.

WHAT: triage each failing spec: fix data assumptions (create their own fixtures via API like the new suites do), rewrite or delete obsolete ones (t28), fix HybridPhase1aService zero-rotation behavior (clean no-op + informative status instead of ERROR), and make the suite runnable on EITHER a fresh seed or a golden-imported profile. Full log of the failing run: /tmp/e2e-full.log (transient — re-run ./scripts/ui/ui-test.sh to reproduce).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Full e2e suite green on a fresh 2026/27 profile
- [x] #2 Zero-rotation pre-assignment is a clean no-op with informative status (service + test)
- [x] #3 Obsolete specs removed or rewritten with self-created fixtures
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
PUNCH LIST SESSION 2026-07-19 (autonomous day): root cause of most reds was PRODUCT, not specs - the UI polled the 16MB /api/schedule (8.7s each!) at 500ms-2s; requests piled up, state lagged minutes, fast transitions never observed. Fixed via TASK-380 light endpoint + change-driven heavy fetch. Spec fixes: toast waits instead of fixed sleeps; honest budgets for 10.5k-row resets; getByRole exact 'Reset' (has-text matched 'Reset All'!); light endpoint in waitForFunctions (the old 5s waits were shorter than one heavy fetch); Hide-link toast test rewritten to the SHIPPED contract (x close button - the Hide design never existed in code); COMPLETE=>disabled tested via reload (poll-window independent). SCORE: preassign-toast 2/2 green, reset-button 3/3 green, solver-state-transitions 13 green + 2 residual reds: (:135 stop-shows-Solve, :263 disabled-when-COMPLETE - both suspected inter-test state interference under suite load, both pass logic-wise in variants; next session: run each in isolation and trace). progression/timeline chunks not yet rerun this session.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
