---
id: TASK-293
title: Add performance shadow variables for daily constraint evaluation
status: Done
assignee: []
created_date: '2026-03-22 05:39'
updated_date: '2026-08-16 08:10'
labels:
  - claude-planned
  - ls-optimization
  - solver
  - performance
milestone: solver-optimization
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `@CustomShadowVariable` annotations to track daily lesson counts, teacher daily hours, and daily gap counts — making M20, H7, H11, H17 constraint evaluation O(1) instead of O(n).

## Why This Matters

We currently have **zero shadow variables** in the codebase. Several hard constraints use `groupBy()` on (entity, date) pairs, which requires iterating all lessons for each group/teacher during every move evaluation:

| Constraint | Groups By | Currently O(n) per move | With Shadow O(1) |
|-----------|-----------|------------------------|-------------------|
| M20 (no sparse days) | (StudentGroup, Date) | Counts all lessons for group on date | `dailyLessonCount[group][date]` |
| H7 (max teacher daily hours) | (Teacher, Date) | Counts all lessons for teacher on date | `dailyTeacherHours[teacher][date]` |
| H11 (max group daily hours) | (StudentGroup, Date) | Counts all lessons for group on date | Same as M20 |
| H17 (vertically compact) | (StudentGroup, Date) | Sorts + scans all lessons for group | `dailyGapCount[group][date]` |

With ~8000 lessons and ~10000 moves evaluated per step, this is significant. Each H17 evaluation currently sorts and scans all lessons for a group — with a shadow variable it's a map lookup.

**Note**: TASK-197 covers migrating the existing `OccupiedTimeslotsListener` (deprecated VariableListener) to shadow variables. This task is about ADDING NEW shadow variables for constraint evaluation speed, not migrating existing ones. They are complementary but independent.

## Implementation

Candidates for `SchoolSchedule`:

```java
@CustomShadowVariable(
    variableListenerClass = DailyLessonCountShadowVariableListener.class,
    sourceVariableName = "timeslot"
)
private Map<Long, Map<LocalDate, Integer>> dailyLessonCountByGroup;

@CustomShadowVariable(
    variableListenerClass = DailyTeacherHoursShadowVariableListener.class,
    sourceVariableName = "timeslot"
)
private Map<Long, Map<LocalDate, Integer>> dailyTeacherHours;

@CustomShadowVariable(
    variableListenerClass = DailyGapCountShadowVariableListener.class,
    sourceVariableName = "timeslot"
)
private Map<Long, Map<LocalDate, Integer>> dailyGapCountByGroup;
```

Each shadow variable listener must handle increment/decrement logic when a lesson's timeslot changes. Timefold automatically calls these during every move.

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Shadow variables for constraint evaluation speed
- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Declarative shadows (Timefold 1.31 feature)
- Existing: TASK-197 — Migrate OccupiedTimeslotsListener to Custom Shadow Variables (complementary)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Daily lesson count shadow variable added with CustomShadowVariableListener
- [ ] #2 Daily teacher hours shadow variable added with CustomShadowVariableListener
- [ ] #3 Daily gap count shadow variable added with CustomShadowVariableListener (or merged with lesson count)
- [ ] #4 Shadow variables incrementally updated during solver moves (verified via logging)
- [ ] #5 Constraint provider updated to use shadow variables for M20, H7, H11, H17 evaluation
- [ ] #6 Solver produces identical scores to current implementation (correctness verification)
- [ ] #7 Performance benchmark: moves-per-second improvement measured
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Cross-ref (2026-08-16, Filone 2 mandato): lo spike H28 (claudedocs/design/h28-shadow-variable.md) conferma ed estende VERDICT-293 con due prove nuove: (1) quantitativa: il leave-one-out 15/08 mette H28 a +438 calcs/s SOPRA il pavimento no-op (+1203, rumore di traiettoria) = soffitto 3,1% se il vincolo diventasse gratis, barra del mandato >10% mancata 3x; (2) meccanicistica: Timefold 2.3.0 e' supplier-based (@ShadowVariable(supplierName) + @ShadowSources, verificato su jar+docs), NON esiste CustomShadowVariable/VariableListener pubblico, e la semantica cross-entita' a chiave composta dinamica (gruppo, materia, data) del blocco H28 non ha espressione dichiarativa pulita. Shadow variables per perf dei constraint: chiuso anche per H28.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
VERDICT-293 (2026-08-04, read-only): NON implementare le shadow variables. Premessa refutata: il task confonde i Java Streams nativi (O(n) per mossa, non incrementali) con le Constraint Streams che questo progetto usa (incrementali, gia' O(1)/O(k) sui 4 vincoli elencati). H7=count, H11=count, M20=sum, H17=toConsecutiveSequences, tutti collector built-in gia' incrementali. Il team aveva gia' corretto il pattern non-incrementale (toList) due volte prima che il task fosse creato: M18 (2026-03-19, 3 giorni prima) e TASK-243 (M15/H17). Shadow vars sarebbero ridondanti col grouping del motore + rischio score corruption + nodi al reference graph (speedup atteso ~0). Unico toList() residuo in un groupBy: M29 riga 984, ma popolazione minuscola (solo G12 Wed/Thu), non hotspot; eventuale fix = pattern toList->incremental-collector gia' usato da M18/M15, NON shadow var (bassa priorita', cosmetica). Doc: claudedocs/spalmone-2026-27/task293-results/VERDICT-293-2026-08-04.md
<!-- SECTION:FINAL_SUMMARY:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
