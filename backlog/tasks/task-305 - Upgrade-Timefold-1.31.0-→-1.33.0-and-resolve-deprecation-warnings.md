---
id: TASK-305
title: Upgrade Timefold 1.31.0 → 1.33.0 and resolve deprecation warnings
status: Done
assignee: []
created_date: '2026-05-16 04:25'
updated_date: '2026-05-21 09:11'
labels:
  - upgrade
  - solver
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
First step of the Timefold upgrade path: move to latest 1.x to get deprecation warnings.\n\n**Changes needed:**\n- Update `timefold-solver.version` from 1.31.0 to 1.33.0 in pom.xml\n- Remove the IdentityHashSet patch (verify if fixed in 1.33.0)\n- Fix deprecation warnings:\n  - `@PlanningVariable(nullable=true)` → `allowsUnassigned=true` (Lesson.java:105)\n  - Verify constraint package usage\n  - Check for any other deprecation warnings in compilation output\n- Run OpenRewrite migration recipe if available for 1.x\n- Run full test suite\n\n**Acceptance criteria:**\n- Timefold 1.33.0 in pom.xml\n- No IdentityHashSet patch needed\n- Zero deprecation warnings in compilation\n- All tests pass\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Timefold 1.33.0 in pom.xml
- [x] #2 No deprecation warnings
- [x] #3 All tests pass
- [x] #4 IdentityHashSet patch removed
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Upgraded Timefold Solver from 1.31.0 to 1.33.0. All deprecation warnings resolved: strengthComparatorClass→comparatorFactoryClass (new TimeslotStrengthComparatorFactory), nullable→allowsUnassigned, withBestSolutionConsumer→withBestSolutionEventConsumer. IdentityHashSet bug verified fixed upstream (patch script kept for historical reference). Build passes with zero Timefold deprecation warnings. Added sdkman sourcing to build scripts for sandbox compatibility.
<!-- SECTION:FINAL_SUMMARY:END -->
