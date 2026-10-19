---
id: TASK-301
title: >-
  Investigate Timefold ComparatorFactorySelectionSorter transitivity bug for
  upstream report
status: To Do
assignee: []
created_date: '2026-03-28 16:02'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Timefold Solver 1.31.0's `ComparatorFactorySelectionSorter` causes TimSort "Comparison method violates its general contract!" crashes when used as the sorter for RuinRecreate's internal Construction Heuristic entity ordering.

## Reproduction

1. Entity with `@PlanningEntity(comparatorFactoryClass = SomeFactory.class)` that returns a provably transitive `Comparator<T>`
2. Entity selector configured with `<selectionOrder>SORTED</selectionOrder>` and `<comparatorFactoryClass>...`
3. RuinRecreate move evaluation temporarily nullifies 2-5 entities' planning variables
4. Internal CH calls `SortingEntitySelector.constructCache()` → `ComparatorFactorySelectionSorter.sort()` → `ArrayList.sort()` → TimSort crash

## Key Observation

The underlying comparator is provably transitive (static pre-computed weights + entity ID tie-breaker guarantees uniqueness). The crash occurs specifically through `ComparatorFactorySelectionSorter.sort()` which wraps the comparator with `Collections.reverseOrder()` based on `SelectionSorterOrder`. Bypassing this class entirely with a custom `SelectionSorter` implementation that calls `list.sort(comparator)` directly fixes the crash.

## Hypothesis

The `Collections.reverseOrder()` wrapping in `getAppliedComparator()` may interact badly with TimSort's merge logic in certain edge cases. Alternatively, the `SelectionSorterOrder` enum handling may create a comparator that doesn't properly delegate to the underlying factory's comparator.

## Files for Reference

- `LessonDifficultyWeightFactory.java` — the transitive comparator (provably correct)
- `LessonEntitySorter.java` — the workaround custom SelectionSorter that bypasses the crash
- Stack trace: `ComparatorFactorySelectionSorter.sort:45` → `TimSort.mergeLo/mergeHi` → `SortingEntitySelector.constructCache` → `RuinRecreateMove.doMoveOnGenuineVariables`

## Steps to Investigate

1. Create minimal reproducer: simple entity with static weight factory + RuinRecreate + sorted entity selector
2. Test if `SelectionSorterOrder.ASCENDING` (no reversal) also crashes
3. Test if `ComparatorSelectionSorter` (non-factory variant) also crashes
4. Check if `Collections.reverseOrder()` on a lambda comparator has known TimSort issues
5. Report findings to Timefold GitHub (https://github.com/TimefoldAI/timefold)
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): la causa era in casa, non upstream: comparatore che leggeva stato mutabile a meta' sort, fixato con lo snapshot TASK-378 (LessonDifficultyComparator.java:21-27); LessonEntitySorter non esiste piu'; nessun bug Timefold da segnalare.
<!-- SECTION:NOTES:END -->
