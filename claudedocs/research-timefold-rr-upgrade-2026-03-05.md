# Research: Timefold Ruin & Recreate + Upgrade Path

**Date**: 2026-03-05
**Depth**: Exhaustive
**Current stack**: Timefold 1.19.0, Quarkus 3.19 (built with 3.18.2)

## Executive Summary

Ruin & Recreate (R&R) with multi-entity support (`entitySelector`) was added in **Timefold 1.24.0**. Our current 1.19.0 does not support it. Two upgrade paths exist:

| Path | Timefold | Quarkus | R&R | precompute() | Risk |
|------|----------|---------|-----|-------------|------|
| **A: Conservative** | 1.28.0 | 3.19 (unchanged) | YES | YES | Medium — Quarkus version gap |
| **B: Full upgrade** | 1.31.0 | 3.30+ | YES | YES | Low — fully tested combo |

**Recommendation: Path B** (full upgrade). The user is open to upgrading, and Quarkus 3.30 is the tested+supported target for Timefold 1.29+.

## 1. R&R Multi-Entity Support

### Problem

In Timefold 1.19.0, `ruinRecreateMoveSelector` has NO `entitySelector` or `variableName` configuration. When multiple `@PlanningEntity` types exist (Lesson + LabBlock), the solver fails:

```
IllegalArgumentException: The config (EntitySelectorConfig(null)) has no entityClass configured
and because there are multiple in the entityClassSet
([class ...LabBlock, class ...Lesson]), it cannot be deduced automatically.
```

### Solution

Starting in **Timefold 1.24.0**, `RuinRecreateMoveSelectorConfig` added:
- `entitySelectorConfig` (EntitySelectorConfig) — `@XmlElement(name = "entitySelector")`
- `variableName` (String)

### Correct XML Config for Multi-Entity

```xml
<ruinRecreateMoveSelector>
  <fixedProbabilityWeight>0.01</fixedProbabilityWeight>
  <minimumRuinedCount>5</minimumRuinedCount>
  <maximumRuinedCount>15</maximumRuinedCount>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
</ruinRecreateMoveSelector>
```

**Notes:**
- `entitySelector` with `entityClass` is **mandatory** when multiple entity types exist
- `variableName` can optionally restrict which variable is ruined (e.g., only `timeslot`)
- For our problem, targeting Lesson makes sense (7,538 entities vs 1,135 LabBlocks, and LabBlocks are all pinned)
- Weight 0.01 = 1 R&R move per 100 fine-grained moves (recommended starting point, benchmark to tune)

### Version Evidence (Source Code Verification)

| Version | `entitySelectorConfig` field | Source |
|---------|---------------------------|--------|
| 1.19.0 | NO | [v1.19.0 source](https://github.com/TimefoldAI/timefold-solver/blob/v1.19.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/generic/RuinRecreateMoveSelectorConfig.java) |
| 1.20.0 | NO | Verified |
| 1.22.0 | NO | Verified |
| 1.23.0 | NO | Verified |
| **1.24.0** | **YES** | [v1.24.0 source](https://github.com/TimefoldAI/timefold-solver/blob/v1.24.0/core/src/main/java/ai/timefold/solver/core/config/heuristic/selector/move/generic/RuinRecreateMoveSelectorConfig.java) |
| 1.28.0 | YES | Verified |

## 2. Timefold Version Matrix

| Timefold | Release Date | Built With Quarkus | Key Feature |
|----------|-------------|-------------------|-------------|
| 1.19.0 | 2025-02-11 | 3.18.2 | Current version |
| 1.20.0 | 2025-03-11 | ~3.20.x | EnvironmentMode enum renamed |
| 1.22.0 | 2025-04-28 | ~3.22.x | Init score removed |
| 1.23.0 | 2025-06-10 | ~3.23.x | @PlanningEntity pinningFilter deprecated |
| **1.24.0** | **2025-07-08** | **3.24.2** | **R&R entitySelector added** |
| 1.25.0 | 2025-08-12 | ~3.25.x | **precompute() API introduced** |
| 1.26.0 | 2025-09-10 | 3.26.2 | Nullness annotations |
| 1.27.0 | 2025-10-14 | 3.28.3 | Value range improvements |
| 1.28.0 | 2025-11-11 | 3.28.5 | SolverJobBuilder API changes |
| **1.29.0** | **2025-12-09** | **3.30.2** | **Quarkus 3.30+ required** |
| 1.30.0 | 2026-01-14 | 3.30.6 | Performance improvements |
| 1.31.0 | 2026-02-11 | ~3.31.x | precompute() improvements, latest |

## 3. Breaking Changes: 1.19.0 → 1.31.0

### High Impact (Likely Affects Our Code)

| Version | Change | Impact | Automated? |
|---------|--------|--------|-----------|
| 1.20.0 | `EnvironmentMode` enum renamed: `REPRODUCIBLE` → `NO_ASSERT`, `FAST_ASSERT` → `STEP_ASSERT` | Check `application.properties` | Yes (OpenRewrite) |
| 1.22.0 | `Score.initScore()` always returns 0; init score removed | Low — we don't use nullable variables | Auto |
| 1.23.0 | `@PlanningEntity(pinningFilter=...)` deprecated → use `@PlanningPin` | We already use `@PlanningPin` | N/A |
| 1.28.0 | `SolverJobBuilder` consumer methods renamed | Check `ScheduleService.java` | Yes |
| 1.28.0 | `SelectionSorterWeightFactory` deprecated → `ComparatorFactory` | Check `LabBlock` difficulty | Yes |
| 1.28.0 | `flattenLast` uses object equality | Check constraint streams | Manual |
| 1.29.0+ | Requires Quarkus 3.30.0+ | Quarkus upgrade needed | N/A |

### Low Impact

| Version | Change | Notes |
|---------|--------|-------|
| 1.20.0 | `CustomPhaseCommand` renamed to `PhaseCommand` | We don't use custom phases |
| 1.27.0 | Variable listeners deprecated | We don't use custom variable listeners |
| 1.31.0 | Chained shadow variables deprecated | We don't use chained variables |

### Automated Migration Tool

Timefold provides an OpenRewrite-based migration tool:

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:6.23.0:run \
  -Drewrite.recipeArtifactCoordinates=ai.timefold.solver:timefold-solver-migration:1.31.0 \
  -Drewrite.activeRecipes=ai.timefold.solver.migration.ToLatest
```

## 4. Quarkus Upgrade: 3.19 → 3.30+

### Key Breaking Changes

| Quarkus Version | Change | Impact |
|----------------|--------|--------|
| 3.26 | Legacy config classes retired (`@ConfigRoot` on classes → interfaces) | Extension-level only |
| 3.26 | Hibernate ORM upgraded to 7.1 | Mostly backward-compatible |
| 3.26 | `.enable` → `.enabled` property rename | Auto-migrated |
| 3.30 | `quarkus.datasource.jdbc.enable-metrics` removed | Use `.metrics.enabled` |
| 3.30 | Apache Derby integration removed | We use PostgreSQL, no impact |

### Automated Migration

```bash
# Quarkus provides automated upgrade tooling
quarkus update --stream=3.30
```

### Risk Assessment

The Quarkus 3.19 → 3.30 upgrade spans 11 minor versions but:
- All within Quarkus 3.x (no major version boundary)
- Most changes are config property renames (auto-migrated)
- Hibernate ORM 7.1 is backward-compatible with 7.0
- `quarkus update` handles most changes automatically

## 5. Additional Performance Features Unlocked by Upgrade

### precompute() API (1.25.0+)

Allows static constraint stream joins to be precomputed once, improving move evaluation speed. Candidate constraints in our codebase:

| Constraint | Why Precomputable | Expected Benefit |
|-----------|-------------------|------------------|
| H4 (Stage blackout) | StagePattern data is static | Medium — avoids re-joining on every move |
| H5 (Teacher unavailability) | TeacherAvailability is static | Medium — avoids re-joining on every move |
| H6 (School holiday) | SchoolHoliday is static | Low — small dataset |
| M5 (Informatica after stage) | StagePattern.endDate is static | Low — few matches |

### Improved Value Range Handling (1.25.0+)

Eliminates useless moves earlier. For entity-based value ranges, the solver won't waste time evaluating moves that are known to be invalid before scoring.

### R&R Move (1.24.0+)

As detailed above — destroys 5-15 entities and rebuilds from construction heuristic. Particularly effective for escaping local optima in Phase 4 (conflict resolution) where swap/pillar moves have plateaued.

## 6. Recommended Upgrade Plan

### Step 1: Upgrade Quarkus 3.19 → 3.30+

```bash
quarkus update --stream=3.30
```

Then manually review:
- `application.properties` for deprecated config keys
- Any Hibernate ORM API changes

### Step 2: Upgrade Timefold 1.19.0 → 1.31.0

```bash
# Run automated migration
mvn org.openrewrite.maven:rewrite-maven-plugin:6.23.0:run \
  -Drewrite.recipeArtifactCoordinates=ai.timefold.solver:timefold-solver-migration:1.31.0 \
  -Drewrite.activeRecipes=ai.timefold.solver.migration.ToLatest

# Then update pom.xml version
# <timefold-solver.version>1.31.0</timefold-solver.version>
```

Then manually review:
- `SolverJobBuilder` consumer methods in `ScheduleService.java`
- `LabBlock` difficulty comparator (if using `SelectionSorterWeightFactory`)
- `flattenLast` usages in constraint streams

### Step 3: Add R&R to solver config

```xml
<!-- In Phase 4 unionMoveSelector -->
<ruinRecreateMoveSelector>
  <fixedProbabilityWeight>0.01</fixedProbabilityWeight>
  <minimumRuinedCount>5</minimumRuinedCount>
  <maximumRuinedCount>15</maximumRuinedCount>
  <entitySelector>
    <entityClass>com.school.planner.domain.Lesson</entityClass>
  </entitySelector>
</ruinRecreateMoveSelector>
```

### Step 4: Add precompute() for static constraints

Candidate: H4, H5, M5 constraint streams.

### Step 5: Run full test suite + benchmark

```bash
mvn test
./scripts/benchmark.sh feature/cartesian-block-entity 10
```

## 7. Effort Estimate

| Step | Effort | Risk |
|------|--------|------|
| Quarkus upgrade | 2-4 hours | Low (automated tooling) |
| Timefold upgrade | 1-2 hours | Low (automated migration) |
| R&R config | 15 minutes | None (XML only) |
| precompute() | 2-4 hours | Low (additive, no breaking changes) |
| Testing + benchmarking | 2-4 hours | — |
| **Total** | **7-14 hours** | **Low** |

## Sources

- [Timefold Move Selector Reference (1.30.0)](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference)
- [Timefold Upgrade Guide](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version)
- [Timefold GitHub Releases](https://github.com/TimefoldAI/timefold-solver/releases)
- [Timefold Backwards Compatibility](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/backwards-compatibility)
- [Quarkus Migration Guide 3.30](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.30)
- [Quarkus Migration Guides Index](https://github.com/quarkusio/quarkus/wiki/Migration-Guides)
- [Timefold Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance)
- [Timefold Score Calculation (precompute)](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation)
