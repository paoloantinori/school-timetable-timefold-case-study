# Timefold Solver Upgrade Research: 1.31.0 → 2.1.0

**Date**: 2026-05-16
**Current version**: 1.31.0 (patched)
**Target version**: 2.1.0 (latest)
**Confidence**: HIGH (official docs, blog posts, GitHub releases)

## Version Timeline

| Version | Date | Type |
|---------|------|------|
| 1.31.0 | ~Dec 2025 | Final feature release of 1.x |
| 1.32.0 | Q1 2026 | Maintenance (bug fixes, Quarkus bump) |
| 1.33.0 | Q1 2026 | Latest 1.x maintenance release |
| 2.0.0 | April 22, 2026 | Major release |
| 2.1.0 | May 2026 | Latest release |

## Recommended Upgrade Path

```
1.31.0 → 1.33.0 → 2.0.0 → 2.1.0
         (deprecation   (OpenRewrite   (latest)
          cleanup)       + manual)
```

Timefold recommends upgrading to the latest 1.x first to get deprecation warnings, then using OpenRewrite for automated 2.0 migration.

## Breaking Changes Impact on This Project

### CRITICAL: Java 17 → 21 Required
- Project uses `maven.compiler.release=17`
- Timefold 2.x requires Java 21+
- Quarkus 3.33+ LTS required

### High Impact (Multiple Files)

| Change | Files Affected | Migration Effort |
|--------|---------------|-----------------|
| `ScoreDirector` removed from public API | 5 files (PhaseTransitionMarker, DayClusteredConstructionPhase, CompositeMoveFilter, UnpinnedLessonFilter, GapPreservingMoveFilter) | Medium — PhaseCommand → PhaseCommandContext, move filters → new API |
| `nullable=true` → `allowsUnassigned=true` | Lesson.java:105 | Trivial — rename attribute |
| `timefold-solver-test` dependency removed | pom.xml, test imports | Low — classes moved to core API |
| `@PlanningId` required on all entities | Multiple domain entities | Low — add annotation |

### Medium Impact

| Change | Details |
|--------|---------|
| Constraint packages deprecated | Our constraints use string names only — minimal impact |
| `initScore` always returns 0 | Our project uses nullable vars — already zero |
| `penalizeLong/rewardLong` removed | Check if used — likely not |
| EnvironmentMode renames | REPRODUCIBLE→NO_ASSERT, FAST_ASSERT→STEP_ASSERT |
| `autoDiscoverMemberType` removed | Verify SchoolSchedule uses explicit annotations |

### Low Impact

| Change | Details |
|--------|---------|
| Score packages flattened | Import changes only |
| `ValueSorterManner` renamed | DECREASING_STRENGTH → DESCENDING etc. |
| `noChangePhase` removed | Not used in our solverConfig.xml |
| Constraint Collector `int` variants | `countLong()→count()` etc. |

## New Features Relevant to This Project

### 1. Precomputed Joins (HIGH VALUE)
Cache static parts of Constraint Streams that only depend on problem facts.
Our H1/H2/H3 constraints join on teacher/room/group equality — precomputing
the static parts would reduce per-move evaluation cost significantly.

### 2. toConsecutiveSequences Collector (MEDIUM VALUE)
Built-in collector for consecutive sequence constraints. Could simplify:
- H17 (Vertically Compact) — currently uses custom gap detection
- H15 (Same-day same-subject consecutive) — currently manual pair joins
- M20 (No sparse days) — could use sequences to detect gaps

### 3. Neighborhoods API (MEDIUM VALUE - Preview)
Declarative custom move definition, replacing complex MoveSelector configs.
Could replace our CompositeMoveFilter and GapPreservingMoveFilter with
cleaner, incremental-evaluated move streams. Still a Preview feature.

### 4. Constraint Profiling (LOW VALUE - Enterprise Only)
Shows which constraints cost the most time. Would help optimize our 30+
constraints but requires Enterprise license.

### 5. Shadow Variable Overhead Reduction (LOW VALUE)
General performance improvement for shadow variable updates. Benefits our
OccupiedTimeslotsListener indirectly.

### 6. RuinRecreate Improvements (LOW VALUE)
Better native support in 2.0. We're already using RuinRecreate in Phase 4.

## Bug Fixes Potentially Related to Our Issues

1. **ComparatorFactory/SelectionSorter issues** — TASK-301 workaround may
   become unnecessary with upstream fixes in 2.x
2. **IdentityHashSet bug** — Our 1.31.0 patch may be fixed in 2.x
3. **Value range identity** — Related bugs fixed in later 1.x releases

## Open Source Changes

- Score analysis and recommended assignment moved to Enterprise Edition
- Solution diff API requires Enterprise on classpath
- Community edition remains Apache 2.0, free for commercial use
- Free licenses available for non-profits and academic use

## Estimated Migration Effort

| Phase | Effort | Risk |
|-------|--------|------|
| Java 21 upgrade | 1-2 hours | Low (Quarkus supports 21) |
| 1.31.0 → 1.33.0 | 1-2 hours | Low (deprecation cleanup) |
| Deprecation removal | 2-4 hours | Low (rename attributes, imports) |
| ScoreDirector migration | 4-8 hours | Medium (5 files, new API patterns) |
| 2.0.0 upgrade + OpenRewrite | 2-4 hours | Medium (automated + manual fixes) |
| 2.0.0 → 2.1.0 | <1 hour | Low |
| Testing and validation | 4-8 hours | Medium (regression testing) |
| **Total** | **~2-3 days** | |

## Sources
- https://timefold.ai/blog/timefold-solver-2.0-release
- https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1
- https://docs.timefold.ai/timefold-solver/1.x/upgrading-timefold-solver/upgrade-to-latest-version
- https://github.com/TimefoldAI/timefold-solver/discussions/2036
- https://github.com/timefoldai/timefold-solver/releases
