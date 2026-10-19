---
id: TASK-310
title: Evaluate Neighborhoods API to replace custom move selectors
status: Done
assignee: []
created_date: '2026-05-16 04:26'
updated_date: '2026-05-21 20:56'
labels:
  - solver
  - preview
milestone: m-0
dependencies:
  - TASK-306
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold 2.0 introduced the Neighborhoods API (Preview) — declarative custom move definition. Could replace CompositeMoveFilter and GapPreservingMoveFilter with cleaner move streams.\n\n**Risks:** Preview feature — API may change. No probability weighting yet.\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 API evaluation document
- [ ] #2 Prototype implementation
- [ ] #3 Recommendation documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by TASK-316. Neighborhoods API is Preview in 2.x. Our custom move selectors are too specialized for current built-ins. Re-evaluate when it graduates from Preview.
<!-- SECTION:FINAL_SUMMARY:END -->
