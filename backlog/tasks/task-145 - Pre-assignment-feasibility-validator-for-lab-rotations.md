---
id: TASK-145
title: Pre-assignment feasibility validator for lab rotations
status: Done
assignee: []
created_date: '2026-03-02 19:27'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - rotation
  - wont-fix
dependencies: []
references:
  - scripts/verify-datagenerator.py
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
documentation:
  - claudedocs/human-planner-heuristics.md
priority: high
ordinal: 77000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a validation check that runs BEFORE pre-assignment to detect infeasible rotation plans. This should catch issues like:
- Rotation periods that are too short for the required hours
- Teachers with insufficient available days for their assigned groups
- Room capacity conflicts for lab sessions

## Problem
Currently, pre-assignment fails after trying all candidates, which takes time and produces a generic error. The user should be notified immediately if a rotation plan is mathematically impossible.

## Example Issue Found
T28 (external, Thursday-only) had rotation periods like:
- G06: Sep 18 - Oct 16 (5 Thursdays) with 60h total → needs 10 blocks → impossible (5 < 10)
- G10: May 28 - Jun 4 (2 Thursdays) with 48h total → needs 8 blocks → impossible (2 < 8)

## Acceptance Criteria
<!-- AC:BEGIN -->
1. New endpoint or pre-check in `/api/schedule/pre-assign` that validates feasibility before running
2. Check for each teacher+group+rotation combination:
   - Available days in period (considering teacher availability/VACATION records)
   - Required blocks = ceil(totalHours / blockSize)
   - If required > available → return specific error with details
3. Return structured error listing all infeasible combinations with:
   - Teacher name
   - Group name  
   - Period dates
   - Hours required vs hours possible
   - Specific constraint causing infeasibility
4. Integration test with intentionally infeasible rotation data
5. Run automatically at pre-assignment start, fail fast with clear message
<!-- SECTION:DESCRIPTION:END -->

- [ ] #1 New endpoint or pre-check validates feasibility before pre-assignment runs
- [ ] #2 For each teacher+group+rotation: compare required blocks vs available days
- [ ] #3 Return structured error with teacher, group, period, hours required vs possible
- [ ] #4 Integration test with intentionally infeasible rotation data
- [ ] #5 Fail fast with clear message instead of running full CSP search
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Won't fix. With only 4 T28 entries remaining after LabRotation removal (commit e71bc85), scope is minimal. Existing failureAnalysis in pre-assign-status endpoint already reports capacity issues adequately. Pre-assignment achieves 100% pinning without a dedicated validator.
<!-- SECTION:FINAL_SUMMARY:END -->
