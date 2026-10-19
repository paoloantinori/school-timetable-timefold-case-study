---
id: TASK-195
title: Remove deprecated initScore() calls
status: Done
assignee: []
created_date: '2026-03-08 08:38'
updated_date: '2026-08-04 07:25'
labels:
  - technical-debt
  - timefold-migration
  - claude-planned
dependencies: []
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Remove all calls to deprecated Score.initScore() method. The method now always returns zero and has no purpose after Timefold removed support for uninitialized scores.

## Migration Strategy

This is a simple removal operation. The initScore() method always returns zero in current Timefold versions, so any code depending on it was likely already ineffective.

## Affected Files

- src/main/java/com/school/planner/service/ScheduleService.java (lines 235, 538, 549)
- src/main/java/com/school/planner/rest/ScheduleResource.java (line 108)

## Steps

1. Search for all occurrences of `.initScore()` in the codebase
2. For each occurrence:
   - If it's a conditional check (`if (score.initScore() != 0)`), remove the entire condition block OR simplify to always-true/false path
   - If it's a direct call, remove it
3. Run tests to verify no behavior change
4. Build and verify deprecation warnings are gone

## Example Transformations

```java
// BEFORE
if (score.initScore() != 0) {
    log.warn("Solution not initialized");
}

// AFTER - Remove entirely (condition was always false)
```

## Risk Assessment

**Risk Level**: LOW - The method has returned zero since earlier Timefold versions. Any code depending on non-zero init score was already broken.

## Time Estimate: 15-30 minutes
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All calls to score.initScore() are removed from the codebase
- [ ] #2 No conditional logic based on initScore() remains
- [ ] #3 Build completes without initScore deprecation warnings
- [ ] #4 Existing tests still pass (score initialization logic unchanged)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ALREADY SATISFIED (no-op, 2026-08-04). Repo-wide grep (case-insensitive, .java/.kt/.xml) finds ZERO initScore references. The deprecated Score.initScore() calls were removed by commit cc746264 "feat: Add solver timeout config UI and remove Timefold deprecated APIs" (which predates this task, 2026-03-08). The listed line numbers (ScheduleService 235/538/549, ScheduleResource 108) now hold comments/blanks - the code was refactored since.

AC #1-#4 all satisfied by the existing code state: no initScore() calls remain, no conditional logic on it, no deprecation warning possible (no calls), tests green (test.sh 988/0/0 at a605db34, nothing changed since). No code change made - nothing to remove. Marking Done to reflect reality rather than fabricate an edit.

Provenance: git log -S initScore -> cc746264 (removal). Verified by Claude (zai) 2026-08-04.
<!-- SECTION:FINAL_SUMMARY:END -->
