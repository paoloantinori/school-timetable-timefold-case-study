---
id: TASK-318
title: >-
  CRITICAL: Fix constraint ID validation failures - migrate "Hxx: Name"
  convention to allowed characters
status: Done
assignee: []
created_date: '2026-06-16 08:48'
updated_date: '2026-06-16 09:14'
labels:
  - bug
  - timefold
  - convention
milestone: m-0
dependencies: []
priority: high
ordinal: 142000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**URGENT**: 130+ tests have been failing since the Timefold 2.0.0 upgrade (commit 02f05a6, TASK-306) due to strict constraint ID validation that was added in 2.0.0. This was not caught at upgrade time because tests weren't run.

**Symptom:**
```
java.lang.IllegalArgumentException: The id (H0: Unassigned lesson) must only contain
alphanumeric characters, spaces, underscores, hyphens, apostrophes ("'"),
parentheses ("(", ")") or full stops (".").
```

**Root cause:**
Timefold 2.0.0's `AbstractConstraintBuilder.sanitize()` rejects the colon character (`:`) in constraint IDs. Our project convention (documented in CLAUDE.md "Canonical Constraint IDs") uses `"H1: Teacher conflict"` format across 44+ constraints in:
- `BaseConstraintProvider.java` (15 constraints)
- `SchoolScheduleConstraintProvider.java` (29+ constraints)

**Scope of migration:**
1. All `.asConstraint("Hxx: Name")` calls → `.asConstraint("Hxx - Name")` (replace `: ` with ` - `)
2. `messages_en.properties` / `messages_it.properties` — constraint IDs are keys
3. Frontend `src/main/webui/src/data/constraints.ts` — constraint reference data
4. Test assertions that reference constraint IDs by string
5. CLAUDE.md "Canonical Constraint IDs" section — update convention doc
6. `claudedocs/*.md` — any docs referencing constraint IDs

**Failing tests:**
- SchoolScheduleConstraintProviderTest: 111 errors
- JustificationTest: 14 errors  
- ScheduleResourceTest: 5 failures
- SchoolScheduleSolverIntegrationTest: 3 errors

**Verification:**
After migration, `./scripts/core/test.sh` should pass cleanly on Timefold 2.0.0.

**Reference:**
- claudedocs/research_timefold-2.x-features-evaluation_20260521.md
- Stack trace: `ai.timefold.solver.core.impl.score.stream.common.AbstractConstraintBuilder.sanitize(AbstractConstraintBuilder.java:47)`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Constraint IDs use only allowed characters (no colons)
- [x] #2 ./scripts/core/test.sh passes with 0 failures on Timefold 2.0.0
- [x] #3 Frontend violation display still works (constraint IDs match)
- [x] #4 i18n message bundles updated to match new IDs
- [x] #5 CLAUDE.md Canonical Constraint IDs section updated
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Migrated 34 constraint IDs from `"Hxx: Name"` to `"Hxx - Name"` format across 5 files. All 273 tests pass on Timefold 2.0.0. Convention change was required because Timefold 2.0.0+ rejects colons in constraint IDs via `AbstractConstraintBuilder.sanitize()`.

**Files changed:**
- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` (14 constraints)
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java` (20 constraints)
- `src/main/java/com/school/planner/service/ConstraintMessageFormatter.java` (parser updated)
- `src/main/java/com/school/planner/service/ScheduleService.java` (parser updated)
- `src/main/webui/src/components/LessonTooltip.tsx` (parser updated)
- `CLAUDE.md` (convention doc updated)

**Test results (before → after):**
- SchoolScheduleConstraintProviderTest: 111 errors → 111 pass
- JustificationTest: 14 errors → 20 pass
- ScheduleResourceTest: 5 failures → 6 pass
- SchoolScheduleSolverIntegrationTest: 3 errors → 3 pass

**Not changed (verified safe):**
- i18n bundles (`messages_en/it.properties`) — use `constraint.H1.1=...` keys, not affected
- Test assertions — use method references (`::teacherConflict`), not string IDs
- Frontend fallback map — uses short codes (`H1.1`), not full IDs

Unblocks TASK-317 (2.0.0 → 2.1.0 bump).
<!-- SECTION:FINAL_SUMMARY:END -->
