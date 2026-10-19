---
id: TASK-090
title: Add environmentMode FAST_ASSERT for dev/test profiles
status: Done
assignee: []
created_date: '2026-02-23 17:24'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
dependencies: []
priority: high
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices recommend configuring `environmentMode` to catch score corruption during development. Currently, neither `solverConfig.xml` nor `application.properties` sets an explicit environment mode, which defaults to `REPRODUCIBLE` (no corruption detection).

**What**: Add `FAST_ASSERT` environment mode for dev/test to catch score corruption bugs early. `FAST_ASSERT` runs assertions after each move to verify the incremental score matches a full recalculation. It's slower than `REPRODUCIBLE` but catches constraint bugs that would otherwise go undetected.

**Options**:
1. **Via `application.properties`** (preferred for profile-specific config):
   - Add `%dev.quarkus.timefold.solver.environment-mode=FAST_ASSERT` to main properties
   - Add `quarkus.timefold.solver.environment-mode=FAST_ASSERT` to test properties (`src/test/resources/application.properties`)
2. **Via `solverConfig.xml`**: Add `<environmentMode>FAST_ASSERT</environmentMode>` — but this applies to all profiles equally

Option 1 is better because production should stay on `REPRODUCIBLE` (faster).

**Risk**: `FAST_ASSERT` slows the solver significantly (5-10x). This is fine for tests (which run with `spent-limit=10s`) and acceptable during interactive development. If dev runs are too slow, the team can switch to `REPRODUCIBLE` locally.

**Note on FULL_ASSERT**: Even more thorough than `FAST_ASSERT` — validates after every step, not just every move. Too slow for routine use but could be added as a separate Maven profile for CI validation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 FAST_ASSERT configured for %test profile in src/test/resources/application.properties
- [x] #2 FAST_ASSERT configured for %dev profile in src/main/resources/application.properties
- [x] #3 Production profile remains REPRODUCIBLE (no explicit override = default)
- [x] #4 mvn test passes with FAST_ASSERT enabled (proves no score corruption)
<!-- AC:END -->
