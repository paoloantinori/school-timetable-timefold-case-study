---
id: TASK-237
title: Add Construction Heuristic entity filter for pinned entities
status: To Do
assignee: []
created_date: '2026-03-14 21:46'
updated_date: '2026-07-25 05:55'
labels:
  - timefold
  - performance
  - solver-optimization
dependencies: []
references:
  - src/main/resources/solverConfig.xml
  - src/main/java/com/school/planner/domain/Lesson.java
documentation:
  - >-
    https://timefold.ai/docs/timefold-solver/latest/configuration#constructionHeuristicType
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold best practice: "Use entityClass and entityFilter in construction heuristic phases to skip entities that should not be initialized (e.g., already-assigned or pinned entities)."

Current Issue: The CH phases in solverConfig.xml initialize all entities with null planning variables, including entities that are already pinned (Lesson.pinned = true). This wastes computation evaluating moves for entities that shouldn't move.

Impact: Adding an entity filter to exclude pinned lessons from CH phases would:
- Reduce wasted computation during initialization
- Speed up the CH phase (fewer entities to process)
- Preserve pinned assignments (already guaranteed, but more explicit)

Note: The filter should check Lesson.pinned == false to only initialize unpinned entities.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 entityFilter added to CH phases in solverConfig.xml
- [ ] #2 Filter excludes pinned lessons (Lesson.pinned == false)
- [ ] #3 CH phase completes faster with fewer entities to initialize
- [ ] #4 Pinned assignments remain unchanged after CH phase
- [ ] #5 Solver still produces valid solutions
- [ ] #6 Performance improvement documented (CH phase time comparison)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): obiettivo coperto dal @PlanningPin nativo (Lesson.java:84-86): le entita' pinnate sono gia' escluse da CH/LS; la costruzione custom filtra !isPinned() esplicitamente.
<!-- SECTION:NOTES:END -->
