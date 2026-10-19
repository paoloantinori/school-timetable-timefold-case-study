---
id: TASK-173
title: bug. validation overlay overlaps the day and time column
status: Done
assignee: []
created_date: '2026-03-05 10:57'
updated_date: '2026-03-10 05:16'
labels:
  - ui
  - bug
dependencies: []
ordinal: 25750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
/home/user/Pictures/Screenshots/Screenshot From 2026-03-05 11-55-11.png

this happens when scrolling left. check the screenshot. you will see the 3 validation tooltip moving over a column while it shouldn't
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added z-index:0 to .violation-cell to create a stacking context, preventing violation badges from overlapping sticky day/time columns (z-[5]). Commit 3c75d64.
<!-- SECTION:FINAL_SUMMARY:END -->
