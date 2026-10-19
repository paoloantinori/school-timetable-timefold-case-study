# Research: Timefold Solver Deprecation Warnings

**Date**: 2026-03-08
**Version**: Timefold Solver 1.31.0
**Research Method**: Tavily search + Timefold documentation analysis

---

## Executive Summary

Three deprecation categories appear in the build logs. All are **non-blocking** but will be **removed in Timefold 2.0 (March 2026)**. Migration effort ranges from trivial (initScore) to moderate (VariableListener).

---

## 1. Score.initScore() Deprecation

**Affected locations**:
- `ScheduleService.java:235, 538, 549`
- `ScheduleResource.java:108`

**Status**: ✅ **Trivial migration** - Can be safely removed

### What Changed

Timefold removed support for uninitialized scores. The `initScore()` method now **always returns zero** and has no purpose.

> *"It is no longer possible to create a score which would have any other init value than zero... All such methods were deprecated and will be removed in a future major version."*
> — [Timefold Upgrade Guide](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version)

### Migration

Simply **remove the calls**. They have no effect:

```java
// OLD (deprecated)
if (score.initScore() != 0) { ... }

// NEW (remove entirely)
// The condition was always false anyway
```

---

## 2. SolverJobBuilder Consumer Deprecations

**Affected locations**:
- `ScheduleService.java:309, 310` (withBestSolutionConsumer, withFinalBestSolutionConsumer)

**Status**: ⚠️ **Moderate migration** - API change to event consumers

### What Changed

The consumer-based API for listening to best solutions is deprecated in favor of a **new event consumer API** with better throttling support.

### New API: BestSolutionEventConsumer

```java
// OLD (deprecated)
solverManager.solveBuilder()
    .withBestSolutionConsumer(solution -> { ... })
    .withFinalBestSolutionConsumer(solution -> { ... })

// NEW (with throttling support)
import ai.timefold.solver.enterprise.core.api.ThrottlingBestSolutionEventConsumer;

var bestSolutionEventConsumer = ThrottlingBestSolutionEventConsumer.of(
    event -> {
        // Handle best solution changed
        SchoolSchedule solution = event.getNewBestSolution();
    },
    Duration.ofSeconds(1) // Throttle to 1 event/second
);

solverManager.solveBuilder()
    .withProblemId(jobId)
    .withProblem(problem)
    .withBestSolutionEventConsumer(bestSolutionEventConsumer)
    .run();
```

**Note**: The new `ThrottlingBestSolutionEventConsumer` is **Enterprise Edition only**. For Community Edition, use `SolverManager.solveAndListen()` instead:

```java
// Community Edition alternative
solverManager.solveAndListen(
    problemId,
    problem,
    (newBestSolution) -> { /* called for each improvement */ },
    (finalBestSolution) -> { /* called once at end */ }
);
```

---

## 3. VariableListener Deprecation

**Affected locations**:
- `OccupiedTimeslotsListener.java:17` (implements VariableListener)

**Status**: ⚠️ **Moderate-to-high migration** - Requires architectural change

### What Changed

The `@VariableListener` API is **deprecated for removal in Timefold 2.0**. The replacement is **Custom Shadow Variables** using declarative annotations.

> *"With the introduction of custom shadow variables, we have deprecated the variable listener API and will be removing the mechanism entirely in Timefold Solver 2.0."*
> — [Timefold Upgrade Guide](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version)

### Two Migration Paths

#### Path A: Cascading Update Shadow Variable (Simple Cases)

For chain-based shadow updates (e.g., updating all elements after a change):

```java
// OLD (VariableListener)
public class OccupiedTimeslotsListener implements VariableListener<SchoolSchedule, Lesson> {
    @Override
    public void afterVariableChanged(ScoreDirector<SchoolSchedule> scoreDirector, Lesson lesson) {
        // Manually update related entities
    }
}

// NEW (@CascadingUpdateShadowVariable)
@PlanningEntity
public class Lesson {
    @CascadingUpdateShadowVariable
    public LocalDateTime updateCascadingFields() {
        // Method called automatically when genuine variables change
        // Update shadow variables here
    }
}
```

#### Path B: Custom Shadow Variable (Advanced Cases)

For complex shadow variable logic:

```java
// Use @ShadowVariable with a custom listener class
@ShadowVariable(variableListenerClass = MyCustomShadowVariableListener.class, ...)
private Object myShadowVariable;
```

**Note**: The migration to Custom Shadow Variables **cannot be fully automated** and requires manual refactoring.

---

## 4. Additional Deprecated Methods

### getJustificationList() → ConstraintMatch

**Affected location**: `ScheduleService.java:672`

The `getJustificationList()` method on `ConstraintMatch` is deprecated. Migration path not yet documented — likely needs investigation of the replacement API.

---

## Timeline & Risk

| Milestone | Date | Impact |
|-----------|------|--------|
| **Now** | 2026-03-08 | Warnings appear in build; code still works |
| **Timefold 2.0 Release** | March 2026 | Deprecated methods removed; code will break |
| **Recommended Migration** | Before Feb 2026 | Allows testing and fallback to 1.x |

**Current Version**: 1.31.0 (released Feb 2026)
**2.0 Timeline**: "Coming March 2026" per [Timefold blog](https://timefold.ai/blog/introducing-timefold-solver-2-0)

---

## Migration Priority

| Priority | Deprecation | Effort | Risk if Delayed |
|----------|-------------|--------|-----------------|
| **LOW** | initScore() | 5 min | None — just remove calls |
| **MEDIUM** | SolverJobBuilder consumers | 1-2 hours | Code breakage in 2.0 |
| **HIGH** | VariableListener | 4-8 hours | Architectural change needed |

---

## Automated Migration Tool

Timefold provides **OpenRewrite recipes** for automated migration:

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:6.28.1:run \
  -Drewrite.recipeArtifactCoordinates=ai.timefold.solver:timefold-solver-migration:1.31.0 \
  -Drewrite.activeRecipes=ai.timefold.solver.migration.ToLatest
```

This handles:
- API renames (e.g., `ScoreManager` → `SolutionManager`)
- Constraint stream method renames (e.g., `from()` → `forEach()`)
- Property name changes

**Limitation**: Does **not** handle VariableListener → Custom Shadow Variable migration (must be manual).

---

## Sources

1. [Timefold Upgrade to Latest Version](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version)
2. [Timefold 1.31.0 Javadoc - Deprecated List](https://javadoc.io/doc/ai.timefold.solver/timefold-solver-core/1.31.0/deprecated-list.html)
3. [Timefold 2.0 Announcement](https://timefold.ai/blog/introducing-timefold-solver-2-0)
4. [Cascading Update Shadow Variables](https://timefold.ai/blog/cascading-update-shadow-var)
5. [Enterprise Edition Event Consumers](https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition)
6. [GitHub Discussion: Timefold Solver 2.0](https://github.com/TimefoldAI/timefold-solver/discussions/2036)

---

## Confidence Level

**Overall**: 85%

- initScore migration: 95% (well-documented)
- SolverJobBuilder migration: 80% (Enterprise vs Community split)
- VariableListener migration: 70% (complex, manual migration required)
