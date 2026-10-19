---
id: TASK-303
title: >-
  TASK-301: Investigate Timefold ComparatorFactorySelectionSorter transitivity
  bug
status: To Do
assignee: []
created_date: '2026-05-12 08:56'
updated_date: '2026-07-25 05:55'
labels:
  - upstream
  - solver
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Investigate TimSort crashes when using ComparatorFactorySelectionSorter with RuinRecreate's internal CH entity ordering. The underlying comparator is provably transitive, but crashes occur specifically through the ComparatorFactorySelectionSorter class.\n\n**Current workaround**: LessonDifficultyWeightFactory + LessonEntitySorter bypass the issue.\n\n**Acceptance criteria**:\n- Reproduce the bug in isolation\n- File upstream report with Timefold\n- Determine if LessonEntitySorter can be removed once fixed upstream
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): duplicato di TASK-301 (stesso testo, creato 2026-05-12).
<!-- SECTION:NOTES:END -->
