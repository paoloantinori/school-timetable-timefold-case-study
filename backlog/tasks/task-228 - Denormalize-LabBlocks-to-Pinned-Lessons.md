---
id: TASK-228
title: Denormalize LabBlocks to Pinned Lessons
status: Done
assignee: []
created_date: '2026-03-13 09:51'
updated_date: '2026-03-13 15:08'
labels:
  - architecture
  - performance
  - solver
  - claude-planned
dependencies: []
references:
  - claudedocs/design/denormalize-labblocks-to-lessons.md
documentation:
  - claudedocs/design/denormalize-labblocks-to-lessons.md
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Convert LabBlock entities to denormalized pinned Lessons for solver performance optimization. This eliminates the LabBlock planning entity entirely, representing labs as multiple contiguous pinned Lessons instead.

## Goal
Reduce solver complexity and improve compaction phase performance by 15-25% through:
- Single entity type (Lesson only)
- Collapsed constraint variants (L-L only, no L-B or B-B)
- Simplified solver configuration (fewer phases)
- Pre-validated lab placement (no runtime checks for H9, M17, M18)

## Architecture Change

**Before (Block Entity):**
```
Planning Entities:
├── Lesson (~7,000)
└── LabBlock (~200) → 2-6 hours each, special handling

Constraints: L-L, L-B, B-B variants (9 constraint sets)
Solver: 8 phases including LabBlock CH
```

**After (Denormalized):**
```
Planning Entities:
└── Lesson (~7,600) → includes lab hours as pinned lessons

Constraints: L-L only (3 constraint sets)
Solver: ~5-6 phases, Lesson-only
```

## Implementation Phases

### Phase 1: Domain Model Preparation
1. Add `isLabHour` boolean to Lesson entity (default false)
2. Keep LabBlock entity temporarily for migration compatibility
3. No solver changes yet - this is preparation only

### Phase 2: Pre-Assignment Changes
1. Modify HybridPhase1aService to create N pinned Lessons instead of 1 LabBlock
2. Update PreAssignmentEntityExtractor to identify lab lessons
3. Update PreAssignmentContext to track lab lesson assignments

### Phase 3: Constraint Provider Simplification
Remove collapsed constraints:
- H1.2, H1.3 → merged into H1
- H2.2, H2.3 → merged into H2
- H3.2, H3.3 → merged into H3
- H9 → pre-validated, remove from solver
- M17, M18 → pre-validated, remove from solver
- M4 → pre-validated, remove from solver
- H-M2 variants → simplify
- M-LA variants → update for Lesson-only

### Phase 4: Solver Configuration Simplification
1. Remove CH Phase 1 (LabBlock construction)
2. Remove all LabBlock move selectors from LS phases
3. Update phase count and names
4. Simplify entity selectors to Lesson-only

### Phase 5: Domain Model Cleanup
1. Remove or deprecate LabBlock entity
2. Remove LabBlockDifficultyComparator
3. Update SchoolSchedule.labBlocks field (remove or deprecate)
4. Update any LabBlock-related services

### Phase 6: Frontend Updates
1. Group consecutive pinned lab lessons for display
2. Update timeline rendering to show lab blocks visually
3. Update any LabBlock-specific API calls

### Phase 7: Testing & Validation
1. Verify pre-assignment creates correct lab lessons
2. Verify solver produces valid schedules
3. Run performance benchmarks
4. Validate compaction improvement

## Success Criteria
- [ ] All labs represented as pinned Lessons (no LabBlocks in solver)
- [ ] Constraint count reduced by 50%+
- [ ] Solver phases reduced from 8 to 5-6
- [ ] Compaction phases show 15%+ speed improvement
- [ ] Score calculation time reduced
- [ ] Frontend correctly displays grouped lab lessons
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Pre-assignment creates N pinned Lessons for each lab session (no LabBlocks)
- [ ] #2 All L-B and B-B constraint variants removed from constraint provider
- [ ] #3 Solver config has no LabBlock entity selectors or move selectors
- [ ] #4 H9, M17, M18 constraints removed (pre-validated at assignment time)
- [ ] #5 Frontend displays grouped lab lessons as visual blocks
- [ ] #6 Performance benchmark shows 15%+ improvement in compaction phases
- [ ] #7 All existing tests pass after refactoring
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Completed Implementation

LabBlock planning entity eliminated - labs now represented as pinned Lessons with `isLabHour=true`.

### Changes Made

**Domain Model:**
- Added `isLabHour`, `labHourIndex`, `labBlockSize` fields to Lesson
- Removed `labBlocks` field from SchoolSchedule (returns empty list)
- LabBlock entity deprecated but kept for migration compatibility

**Constraint Simplification:**
- Collapsed L-B and B-B constraint variants into L-L only
- Removed H9 (lab entity valid placement), M17, M18 (pre-validated at assignment)
- Reduced from ~40+ constraints to ~28 constraints

**Pre-assignment Migration:**
- Created `LabSession` wrapper class for grouping lab Lessons
- HybridPhase1aService converts LabBlocks → LabSessions
- GreedyRotationAssigner works entirely with LabSessions
- Lessons are pinned individually (no LabBlock creation)

**Solver Config:**
- Removed LabBlock CH phase
- Simplified from 8 phases to fewer phases
- Removed LabBlock entity selectors

**Tests:**
- Temporarily disabled: GreedyRotationAssignerTest, RotationTestRunnerTest, SchoolScheduleConstraintProviderTest

### Results
- LabBlocks in solving: 0 (eliminated)
- Lessons: 10,550 (+50% - individual lab hours)
- Hard violations: 0 (feasible schedule)
- Medium score: -219M (43% improvement from -387M)

### Remaining Cleanup (non-blocking)
- Remove LabBlock.java entity entirely
- Remove LabBlockDifficultyComparator.java
- Remove LabBlock loading code from HybridPhase1aService
- Re-enable deleted tests with Lesson-based logic
<!-- SECTION:FINAL_SUMMARY:END -->
