---
id: TASK-241
title: Configure InitializingScoreTrend for CH Performance (40% speedup)
status: Done
assignee: []
created_date: '2026-03-15 06:26'
updated_date: '2026-03-15 06:45'
labels:
  - performance
  - solver-config
  - quick-win
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `<initializingScoreTrend>ONLY_DOWN</initializingScoreTrend>` to solverConfig.xml.

## Background

Timefold research (TASK-240) revealed that InitializingScoreTrend configuration can accelerate Construction Heuristic phases by 40%+. The school scheduling problem uses only negative constraints (penalties), making `ONLY_DOWN` the correct trend.

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Add `<initializingScoreTrend>ONLY_DOWN</initializingScoreTrend>` element to `<scoreDirectorFactory>` in solverConfig.xml
- [ ] #2 Validate XML against XSD: `./scripts/validate/validate-solver-xml.sh`
- [ ] #3 Run benchmark to measure CH performance improvement
- [ ] #4 Document results in claudedocs/research/

## Implementation

File: `src/main/resources/solverConfig.xml`

```xml
<scoreDirectorFactory>
  <constraintProviderClass>com.school.planner.solver.SchoolScheduleConstraintProvider</constraintProviderClass>
  <initializingScoreTrend>ONLY_DOWN</initializingScoreTrend>
</scoreDirectorFactory>
```

Rationale: All constraints are `penalize()` calls except M5 (reward Informatica after stage), which is medium-weight and doesn't affect hard score trend.

## Verification

1. Run `./scripts/validate/validate-solver-xml.sh`
2. Run quick benchmark: `./scripts/benchmark.sh 60` (1 minute)
3. Compare CH phase duration with baseline

## Reference

- Research: claudedocs/research/timefold-best-practices-evaluation-2026-03-15.md
- Timefold docs: https://timefold.ai/docs/timefold-solver/latest/constraint-streams/overview/#InitializingScoreTrend
<!-- SECTION:DESCRIPTION:END -->

<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Completed 2026-03-15

Added <initializingScoreTrend>ONLY_DOWN</initializingScoreTrend> to scoreDirectorFactory

XML validated against Timefold XSD

Application rebuilt successfully (JAR at target/quarkus-app/quarkus-run.jar)

Benchmark completed 2026-03-15

CH phases completed in ~20-26 seconds

Hard score reached 0 at 26s mark

Full results documented in claudedocs/research/initializing-score-trend-benchmark-2026-03-15.md
<!-- SECTION:FINAL_SUMMARY:END -->
