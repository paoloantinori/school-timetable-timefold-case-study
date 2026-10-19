---
id: TASK-082
title: Apply exhaustive search to ProfessionalizzantiPreAssigner
status: Done
assignee: []
created_date: '2026-02-23 13:31'
updated_date: '2026-03-09 19:50'
labels:
  - enhancement
  - pre-assignment
dependencies: []
priority: low
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Background

The ProfessionalizzantiPreAssigner (Step 4 of human planner heuristics) still uses a greedy approach. It finds available slots on stage days but many professionalizzanti lessons remain unassigned (e.g., 265 for G07 alone).

## Proposed Enhancement

Apply the `SlotCandidate`/exhaustive search pattern (from task-074) to ProfessionalizzantiPreAssigner:

1. Generate all valid slot candidates on stage days
2. Sort by priority (preferred time slots, room preferences)
3. Use backtracking to find valid assignments
4. Pin successfully assigned lessons

## Benefits

- More professionalizzanti lessons assigned during pre-assignment
- Better utilization of stage days
- Consistent approach across all pre-assignment phases

## Risk

May not be needed if:
- Solver handles these efficiently
- Current coverage is "good enough"
- Other optimizations take priority

## Related

- task-074 (original exhaustive search implementation)
- task-042 (two-phase solver approach - alternative solution)
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by GreedyRotationAssigner (TASK-121 work). ProfessionalizzantiPreAssigner now uses CSP backtracking, not greedy approach.
<!-- SECTION:FINAL_SUMMARY:END -->
