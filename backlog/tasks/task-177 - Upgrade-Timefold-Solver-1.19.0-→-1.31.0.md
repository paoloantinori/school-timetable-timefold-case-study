---
id: TASK-177
title: Upgrade Timefold Solver 1.19.0 → 1.31.0
status: Done
assignee: []
created_date: '2026-03-05 15:26'
updated_date: '2026-03-09 19:50'
labels:
  - upgrade
  - solver
  - claude-planned
milestone: Timefold Upgrade
dependencies:
  - TASK-176
references:
  - claudedocs/research-timefold-rr-upgrade-2026-03-05.md
  - src/main/java/com/school/planner/service/ScheduleService.java
  - src/main/java/com/school/planner/domain/LabBlock.java
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version
  - >-
    https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/backwards-compatibility
priority: high
ordinal: 98000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Timefold 1.31.0 (Feb 2026) is the latest release. Upgrading from 1.19.0 unlocks R&R multi-entity support (1.24+), precompute() API (1.25+), and numerous performance improvements.

## Approach

1. Run the automated OpenRewrite migration tool:

```bash
mvn org.openrewrite.maven:rewrite-maven-plugin:6.23.0:run \
  -Drewrite.recipeArtifactCoordinates=ai.timefold.solver:timefold-solver-migration:1.31.0 \
  -Drewrite.activeRecipes=ai.timefold.solver.migration.ToLatest
```

2. Update `pom.xml`:
```xml
<timefold-solver.version>1.31.0</timefold-solver.version>
```

3. Manually review and fix items the migration tool can't handle.

## Breaking Changes Requiring Manual Review

| Version | Change | File to Check |
|---------|--------|---------------|
| 1.20.0 | `EnvironmentMode` enum renamed | `application.properties` |
| 1.28.0 | `SolverJobBuilder` consumer methods renamed | `ScheduleService.java` |
| 1.28.0 | `SelectionSorterWeightFactory` deprecated → `ComparatorFactory` | `LabBlock.java` difficulty |
| 1.28.0 | `flattenLast` uses object equality | Constraint streams |

## What NOT to change

- `solverConfig.xml` — R&R addition is a separate task
- Constraint provider — precompute() is a separate task
- Domain entities — no changes expected

## Verification

1. `mvn test` passes
2. App starts and solver runs
3. Score calculation matches pre-upgrade (same constraints, same penalties)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Timefold version in pom.xml is 1.31.0
- [ ] #2 OpenRewrite migration tool ran successfully
- [ ] #3 SolverJobBuilder consumer API updated in ScheduleService.java
- [ ] #4 LabBlock difficulty comparator uses non-deprecated API
- [ ] #5 All 192 tests pass with `mvn test`
- [ ] #6 Solver starts and produces valid scores
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Completed as part of TASK-176. Timefold 1.19.0 was binary-incompatible with Quarkus 3.26+, so both were upgraded together: Quarkus 3.19.4→3.31.4, Timefold 1.19.0→1.31.0. See TASK-176 for full details.
<!-- SECTION:FINAL_SUMMARY:END -->
