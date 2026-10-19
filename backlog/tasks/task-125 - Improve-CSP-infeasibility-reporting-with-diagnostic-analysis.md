---
id: TASK-125
title: Improve CSP infeasibility reporting with diagnostic analysis
status: Done
assignee:
  - '@claude'
created_date: '2026-02-26 14:21'
updated_date: '2026-03-09 19:50'
labels:
  - enhancement
  - csp
  - error-reporting
dependencies: []
priority: high
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When the CSP backtracking algorithm detects an infeasible assignment, report a clear diagnostic message explaining WHY it failed. Currently it just says "FAILURE: No valid assignment found".

The error should include:
- Which teacher/group/room combination failed
- Constraint analysis: "Teacher X needs N blocks on Day-Y but only M slots available"
- Which periods are affected
- Suggested remediation (e.g., "Reduce course hours" or "Add more available days")

Example desired output:
```
INFEASIBLE: T28/G10/acconciatura
  Reason: 64 hours / 6h blocks = 11 blocks needed
  Period: Apr 5 - Jun 10 (9 Thursdays available)
  Constraint: Only 9 slots for 11 blocks
  Suggestion: Reduce course to 54 hours or extend rotation period
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Error includes teacher/group names (not just IDs)
- [ ] #2 Error includes period date range
- [ ] #3 Error includes specific capacity calculation (blocks needed vs available)
- [ ] #4 Error includes actionable suggestion with specific values
- [ ] #5 Output matches the desired format in task description
<!-- AC:END -->
