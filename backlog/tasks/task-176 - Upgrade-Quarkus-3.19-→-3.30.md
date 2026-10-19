---
id: TASK-176
title: Upgrade Quarkus 3.19 → 3.30+
status: Done
assignee: []
created_date: '2026-03-05 15:26'
updated_date: '2026-03-09 19:50'
labels:
  - upgrade
  - infrastructure
  - claude-planned
milestone: Timefold Upgrade
dependencies: []
references:
  - claudedocs/research-timefold-rr-upgrade-2026-03-05.md
documentation:
  - 'https://github.com/quarkusio/quarkus/wiki/Migration-Guides'
  - 'https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.26'
  - 'https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.30'
priority: high
ordinal: 99000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Timefold 1.29+ requires Quarkus 3.30.0+. Our current Quarkus 3.19 (built with 3.18.2) must be upgraded first before upgrading Timefold.

## Approach

Use the automated `quarkus update` tool, then manually review breaking changes.

```bash
quarkus update --stream=3.30
```

## Key Breaking Changes (3.19 → 3.30)

- **3.26**: Legacy config classes retired (`@ConfigRoot` on classes → interfaces). Only affects extension developers — our app code should be fine.
- **3.26**: Hibernate ORM upgraded to 7.1 (mostly backward-compatible with 7.0).
- **3.26**: `.enable` → `.enabled` config property renames (auto-migrated).
- **3.30**: `quarkus.datasource.jdbc.enable-metrics` removed → use `.metrics.enabled`.
- **3.30**: Apache Derby removed (we use PostgreSQL, no impact).

## Risk Assessment

Low. All within Quarkus 3.x (no major boundary). Most changes are config renames handled by `quarkus update`. Hibernate ORM 7.1 is backward-compatible.

## Verification

After upgrade:
1. `mvn test` passes (all 192 tests)
2. App starts with `./scripts/start-and-wait.sh dev`
3. Solver can start/stop: `./scripts/api/api.sh solve` / `./scripts/api/api.sh stop`
4. Pre-assignment pipeline completes: `./scripts/api/api.sh pre-assign`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Quarkus version in pom.xml is 3.30.x or higher
- [x] #2 All 192 tests pass with `mvn test`
- [x] #3 App starts and serves /api/schedule correctly
- [x] #4 No deprecated config warnings in startup logs
- [x] #5 Pre-assignment pipeline completes successfully
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Upgrade Summary

Upgraded the full stack from Quarkus 3.19.4 + Timefold 1.19.0 to **Quarkus 3.31.4 + Timefold 1.31.0** (+ Quinoa 2.5.3 → 2.7.2).

### Why both together?
Timefold 1.19.0 is binary-incompatible with Quarkus 3.26+ due to the `@ConfigRoot` class→interface migration. The Timefold Quarkus extension injects runtime config the old way, which Quarkus 3.26+ rejects. Both must be upgraded together.

### Changes made:
- **pom.xml**: Quarkus 3.19.4→3.31.4, Timefold 1.19.0→1.31.0, Quinoa 2.5.3→2.7.2, quarkus-junit5→quarkus-junit, surefire argLine @{argLine}
- **Timefold API migration** (via OpenRewrite): `difficultyComparatorClass`→`comparatorClass`, `solveAndListen`→`solveBuilder()`, unused imports
- **Config property renames**: `hibernate-orm.database.generation`→`schema-management.strategy`, `http.cors`→`http.cors.enabled`
- **Docs updated**: CLAUDE.md, README.md, PROJECT_INDEX.md, planner.sh

### Test results: 194/194 pass, 0 failures
<!-- SECTION:FINAL_SUMMARY:END -->
