---
id: TASK-124
title: Create DataGenerator verification script against spalmone
status: Done
assignee: []
created_date: '2026-02-26 14:21'
updated_date: '2026-03-09 19:50'
labels:
  - validation
  - data-quality
  - tooling
dependencies: []
priority: high
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a verification script that compares DataGenerator course/rotation hours against the spalmone.csv (validated human solution). The script should:

1. Parse spalmone.csv and extract hours per teacher per group
2. Parse DataGenerator.java and extract course hours per teacher per group
3. Compare and report discrepancies (e.g., "T28 G10: CSV=32h, DataGenerator=64h")
4. Run as part of CI or manual verification

This helps catch data inconsistencies before they cause infeasibility issues in the solver.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed: scripts/verify-datagenerator.py implemented (commit 926958a). Script parses spalmone.csv and DataGenerator.java, compares hours per teacher/group, reports discrepancies with >10h or >20% threshold, and provides exit codes for CI integration.
<!-- SECTION:NOTES:END -->
