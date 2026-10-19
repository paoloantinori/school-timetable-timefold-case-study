# Timefold sorter workaround: TimSort crash in LessonEntitySorter (RuinRecreate Phase 4)

> 📌 **Session memory** (2026-03-28, bug investigation). Verify against current `solverConfig.xml` and `LessonEntitySorter` — the crash may or may not still reproduce.

## Rule

Do NOT revert `LessonEntitySorter` (custom `SelectionSorter`) back to `ComparatorFactorySelectionSorter` (Timefold's built-in) on the RuinRecreate entitySelector. Also, do NOT simplify the comparator in `LessonEntitySorter` — the current two-level comparator (availableTimeslotCount + entity ID tie-breaker) is needed for correctness.

## Symptom

Phase 4 (RuinRecreate) crashed with `"Comparison method violates its general contract!"` at `LessonEntitySorter.sort():35` → `TimSort.mergeHi`. The crash happened through the custom `LessonEntitySorter` (not through `ComparatorFactorySelectionSorter`), so the root cause is in the comparator logic itself, not the `reverseOrder()` wrapping theory.

## Bug found and fixed (2026-03-28)

`sort(Set)` returned an empty TreeSet — it never added the input elements. Fixed to `sortedSet.addAll(set)`, following Timefold's `TestdataObjectSorter` pattern.

## Comparator analysis (mathematically correct)

- **Sign consistency:** `Integer.compare` and `Long.compare` are consistent
- **Transitivity:** both primary (count) and secondary (ID) are total orders
- **Substitution:** `compare(a,a) == 0` holds
- Direct sorting stress test (8000 entities × 100K shuffles) passed without violations
- The static map is never modified during solving (set once before solve starts)

## How to apply

The RuinRecreate entitySelector in `solverConfig.xml` uses `<sorterClass>com.school.planner.domain.LessonEntitySorter</sorterClass>`. The `@PlanningEntity` annotation on `Lesson` uses `comparatorFactoryClass = LessonDifficultyWeightFactory.class` for other phases (Phase 2, Phase 5).

## Production crash (2026-03-28)

Phase 3 ended at -357hard; Phase 4 started and crashed ~3 minutes later. The crash is intermittent — previous smoke tests with a shorter Phase 3 (60s) did not trigger it, suggesting the entity list ordering at Phase 4 start matters.

## Remaining hypothesis

The crash may require the full domain model's specific entity/comparator/value-range interaction. The reproducer in the Timefold repo (`reproducer/comparatorfactory-timsort-crash` branch) uses a faithful domain model (two nullable planning variables, static map pattern, STEP cache, custom sorterClass) but had not reproduced the crash in limited testing at the time of capture.
