---
id: TASK-311
title: Remove TASK-301 workaround after verifying Timefold 2.x fix
status: Done
assignee: []
created_date: '2026-05-16 04:26'
updated_date: '2026-07-16 06:52'
labels:
  - cleanup
  - upstream
milestone: m-0
dependencies:
  - TASK-306
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
After upgrading to Timefold 2.x, verify if the ComparatorFactorySelectionSorter transitivity bug (TASK-303) is fixed upstream. If so, remove LessonDifficultyWeightFactory.java, LessonEntitySorter.java, and revert Lesson.java to use LessonDifficultyComparator.\n\n**Reference:** TASK-303, claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Bug verification complete
- [ ] #2 Workaround removed OR upstream bug filed
- [ ] #3 Solver stable with RuinRecreate
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DONE 2026-07-15: LessonEntitySorter.java deleted (dead code — sorterClass already removed from solverConfig.xml, no references in src/). The TimSort transitivity crash (Comparison method violates its general contract) does not trigger on Timefold 2.3.0 (confirmed by TASK-323 A/B). 273 tests pass. LessonDifficultyWeightFactory is still actively used by @PlanningEntity(comparatorFactoryClass) — not deleted (the task description was outdated on that point).
<!-- SECTION:FINAL_SUMMARY:END -->
