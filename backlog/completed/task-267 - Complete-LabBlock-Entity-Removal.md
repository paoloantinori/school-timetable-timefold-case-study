---
id: TASK-267
title: Complete LabBlock Entity Removal
status: Done
assignee: []
created_date: '2026-03-17 10:07'
updated_date: '2026-03-17 11:09'
labels:
  - cleanup
  - architecture
  - technical-debt
dependencies: []
documentation:
  - claudedocs/design/denormalize-labblocks-to-lessons.md
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Remove deprecated LabBlock entity and all related code after TASK-228 migration to pinned Lessons.

## Background

TASK-228 (Denormalize LabBlocks to Pinned Lessons) was completed, but the actual LabBlock entity was kept for migration compatibility. This task completes the cleanup by removing all deprecated LabBlock code.

## Cleanup Items

### Domain Model
- [ ] Remove `src/main/java/com/school/planner/domain/LabBlock.java` entirely
- [ ] Remove `LabBlockDifficultyComparator.java`
- [ ] Remove deprecated `getLabBlocks()` / `setLabBlocks()` from `SchoolSchedule.java`
- [ ] Remove `LabBlock` TypeScript interface from `src/main/webui/src/types/domain.ts`

### Services
- [ ] Remove LabBlock loading/creation code from `HybridPhase1aService`
- [ ] Remove any remaining `LabBlock` imports across services
- [ ] Update JavaDoc references to LabBlock (if any)

### Tests
- [ ] Re-enable `GreedyRotationAssignerTest` with Lesson-based logic
- [ ] Re-enable `RotationTestRunnerTest` with Lesson-based logic
- [ ] Update `SchoolScheduleConstraintProviderTest` for Lesson-only constraints
- [ ] Remove any LabBlock-specific test cases

### Documentation
- [ ] Update CLAUDE.md to remove LabBlock references
- [ ] Update PROJECT_INDEX.md to remove LabBlock from entity count
- [ ] Update solver-phases.md to reflect Lesson-only architecture
- [ ] Update any architecture docs mentioning LabBlocks

### Validation
- [ ] Verify app builds without LabBlock dependency
- [ ] Verify solver produces valid schedules (hard score = 0)
- [ ] Run full test suite and ensure all pass
- [ ] Quick benchmark to confirm no performance regression

## Success Criteria

- [ ] LabBlock.java completely removed from codebase
- [ ] All tests pass (no LabBlock references)
- [ ] App builds and runs successfully
- [ ] Solver produces feasible schedules (0 hard violations)
- [ ] No LabBlock imports remain in any Java/TypeScript files

## Notes

- This is safe cleanup: LabBlocks are no longer used by the solver
- All labs are now represented as pinned Lessons with `isLabHour=true`
- Pre-assignment uses LabSession wrapper, not LabBlock entities
- SchoolSchedule.getLabBlocks() already returns empty list (deprecated)
<!-- SECTION:DESCRIPTION:END -->
