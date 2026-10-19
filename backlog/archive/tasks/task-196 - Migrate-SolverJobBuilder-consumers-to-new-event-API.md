---
id: TASK-196
title: Migrate SolverJobBuilder consumers to new event API
status: To Do
assignee: []
created_date: '2026-03-08 08:38'
updated_date: '2026-07-25 05:55'
labels:
  - technical-debt
  - timefold-migration
  - claude-planned
dependencies: []
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition
  - >-
    https://docs.timefold.ai/timefold-solver/latest/responding-to-change/responding-to-change
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Migrate from deprecated SolverJobBuilder consumer methods (withBestSolutionConsumer, withFinalBestSolutionConsumer) to the new BestSolutionEventConsumer API.

## Affected Files

- src/main/java/com/school/planner/service/ScheduleService.java (lines 309-310)

## Community vs Enterprise Edition

**IMPORTANT**: The new `ThrottlingBestSolutionEventConsumer` is **Enterprise Edition only**. For Community Edition, use `SolverManager.solveAndListen()` instead.

## Pattern A: Community Edition (solveAndListen)

```java
// BEFORE
solverManager.solveBuilder()
    .withProblemId(problemId)
    .withProblem(schedule)
    .withBestSolutionConsumer(bestSolution -> { ... })
    .withFinalBestSolutionConsumer(finalBest -> { ... })
    .run();

// AFTER
solverManager.solveAndListen(
    problemId,
    schedule,
    bestSolution -> { /* same as withBestSolutionConsumer */ },
    finalBest -> { /* same as withFinalBestSolutionConsumer */ }
);
```

## Pattern B: Enterprise Edition (ThrottlingBestSolutionEventConsumer)

```java
// BEFORE
solverManager.solveBuilder()
    .withBestSolutionConsumer(bestSolution -> { ... })
    .run();

// AFTER
import ai.timefold.solver.enterprise.core.api.ThrottlingBestSolutionEventConsumer;

var eventConsumer = ThrottlingBestSolutionEventConsumer.of(
    event -> {
        SchoolSchedule bestSolution = event.getNewBestSolution();
        // Handle best solution changed
    },
    Duration.ofSeconds(1) // Throttle to 1 event/second
);

solverManager.solveBuilder()
    .withProblemId(problemId)
    .withProblem(schedule)
    .withBestSolutionEventConsumer(eventConsumer)
    .run();
```

## Key Differences

| Old API | New API |
|---------|---------|
| Consumer functional interface | Event object with more context |
| No throttling | Built-in throttling support |
| Separate final consumer | Single consumer, check `event.isSolverTerminating()` |

## Risk Assessment

**Risk Level**: MEDIUM - API change affects solver event handling. Need to verify event timing still meets requirements.

## Time Estimate: 1-2 hours
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All withBestSolutionConsumer() calls migrated
- [ ] #2 All withFinalBestSolutionConsumer() calls migrated
- [ ] #3 Solver functionality unchanged (same events fired)
- [ ] #4 Event throttling implemented if needed
- [ ] #5 Tests verify best solution handling works as before
- [ ] #6 Build completes without SolverJobBuilder deprecation warnings
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): migrazione gia' fatta: ScheduleService usa withBestSolutionEventConsumer/withFinalBestSolutionEventConsumer (:562-563); vecchia API assente dal codebase.
<!-- SECTION:NOTES:END -->
