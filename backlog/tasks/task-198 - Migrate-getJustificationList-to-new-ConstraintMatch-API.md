---
id: TASK-198
title: Migrate getJustificationList() to new ConstraintMatch API
status: Done
assignee: []
created_date: '2026-03-08 08:38'
labels:
  - technical-debt
  - timefold-migration
  - investigation-needed
dependencies: []
documentation:
  - >-
    https://javadoc.io/doc/ai.timefold.solver/timefold-solver-core/latest/ai/timefold/solver/core/api/score/constraint/ConstraintMatch.html
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace deprecated getJustificationList() calls on ConstraintMatch with the new API. The replacement method is not yet well-documented and requires investigation.

## Affected Files

- src/main/java/com/school/planner/service/ScheduleService.java (line 672)

## Investigation Required

Before implementing, need to determine the correct replacement:

1. Check Timefold 1.31.0 Javadoc for ConstraintMatch class
2. Look for renamed methods (e.g., `getJustificationList()` → `getJustifications()`)
3. Check if ConstraintJustification interface has new methods
4. Review Timefold GitHub issues/PRs for this deprecation

## Expected Patterns (Verify before implementing)

```java
// Option A: Simple rename
constraintMatch.getJustifications()

// Option B: Stream-based API
constraintMatch.getJustificationStream().toList()

// Option C: Different access pattern
constraintMatch.getConstraintJustification()
```

## Steps

1. **Investigation Phase**: Read Javadoc, search docs, check git history
2. **Implementation Phase**: Apply correct replacement pattern
3. **Verification Phase**: Test constraint analysis endpoint

## Risk Assessment

**Risk Level**: LOW-MEDIUM - Simple API change but requires investigation first.

## Time Estimate: 1-2 hours (mostly investigation)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 getJustificationList() call replaced with new API
- [ ] #2 Constraint justification still accessible after migration
- [ ] #3 Build completes without getJustificationList deprecation warning
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
