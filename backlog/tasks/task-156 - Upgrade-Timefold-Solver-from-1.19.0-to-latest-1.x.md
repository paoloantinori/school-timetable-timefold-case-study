---
id: TASK-156
title: Upgrade Timefold Solver from 1.19.0 to latest 1.x
status: Done
assignee: []
created_date: '2026-03-03 20:47'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - performance
  - infrastructure
milestone: Solver Performance
dependencies: []
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - 'pom.xml:16'
documentation:
  - 'https://github.com/timefoldai/timefold-solver/releases'
  - >-
    https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/configuration
priority: medium
ordinal: 79000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Upgrade Timefold Solver from 1.19.0 to the latest 1.x release (currently 1.31.0, the final 1.x feature release). This unlocks several performance features:

1. **Constraint Streams `precompute()` API** (since 1.29.0 / solver 1.13.0) — precompute static joins for H4, H5, M5 constraints
2. **Entity-level VRP for list variables** (since 1.25.0) — not directly applicable but shows ongoing VRP improvements
3. **Parameterized VRP methods** (late 2025) — pass solution context without static refs
4. **Performance improvements** accumulated across 12 minor versions
5. **Bug fixes** for value ranges and move selection

**Risks:**
- API deprecations/removals between 1.19 and 1.31
- Timefold 2.0 (March 2026) will remove legacy APIs — 1.31 is the final 1.x, so this upgrade positions us for the 2.0 migration path
- Quarkus compatibility — verify `timefold-solver-quarkus` version matches our Quarkus 3.19

**Upgrade steps:**
1. Update `pom.xml` `timefold-solver.version` property
2. Check for deprecated API usage in constraint provider and domain entities
3. Run all tests
4. Benchmark against baseline to verify no regression
5. Check Timefold migration guide for breaking changes

**Note:** This is a prerequisite for TASK-156 (Constraint Streams precompute).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 pom.xml updated to latest compatible Timefold 1.x version
- [ ] #2 No deprecated API warnings in build output (or documented and tracked)
- [ ] #3 All 193 tests pass
- [ ] #4 Pre-assignment achieves 100% pinning
- [ ] #5 Benchmark shows no regression vs 1.19.0 baseline
- [ ] #6 Quarkus compatibility verified (no startup errors)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Completed as part of TASK-176. Timefold upgraded from 1.19.0 to 1.31.0 alongside Quarkus 3.19.4→3.31.4. All 194 tests pass. New features unlocked: R&R entitySelector (1.24+), precompute() API (1.25+).
<!-- SECTION:FINAL_SUMMARY:END -->
