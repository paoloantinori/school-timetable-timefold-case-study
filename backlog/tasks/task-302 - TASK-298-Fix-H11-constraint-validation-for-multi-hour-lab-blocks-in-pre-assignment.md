---
id: TASK-302
title: >-
  TASK-298: Fix H11 constraint validation for multi-hour lab blocks in
  pre-assignment
status: Done
assignee: []
created_date: '2026-05-12 08:56'
updated_date: '2026-07-15 19:33'
labels:
  - bug
  - pre-assignment
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Critical bug where pre-assignment validates only the starting slot of multi-hour lab blocks against maxHoursPerDay limits, not the entire block. This causes blocks to be placed in invalid positions (e.g., 2-hour blocks starting at slot 12 exceed the limit at slot 13).\n\n**Key Issue**: G12 Informatica blocks being placed at slots 12-13 (12:00-14:00) when maxHoursPerDay=5\n\n**Files to modify**: HybridPhase1aService.java, GreedyRotationAssigner.java\n\n**Acceptance criteria**:\n- Validation checks ALL slots in a block, not just the starting slot\n- G12 Informatica blocks no longer placed in invalid positions\n- Pre-assignment feasibility check catches oversized blocks before solving
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): LabBlock deleted; block-aware wouldExceedDailyLimit(blockSize) at HybridPhase1aService:1044-1094 (references the G12 Informatica bug). GreedyRotationAssigner also validates with blockSize.
<!-- SECTION:FINAL_SUMMARY:END -->
