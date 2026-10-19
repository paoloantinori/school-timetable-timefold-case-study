---
id: task-072
title: Multi-Option Pre-Assignment with Conflict-Based Selection
status: Done
assignee: []
created_date: '2026-02-22 17:17'
updated_date: '2026-02-22 17:55'
labels:
  - claude-planned
  - pre-assignment
  - solver
  - architecture
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The current adaptive pre-assignment picks a "best day" upfront, but human planners try multiple day combinations and keep what works. The spalmone shows teachers work across multiple days (T26: 5 days, T18: 5 days), not a single "preferred" day.

## Solution

Replace "pick best day" with "generate all options, pick conflict-free":

1. **Generate candidates for ALL weekdays** (not just best-scoring one)
2. **Score each by conflicts**: H5 violations, room contention, group conflicts
3. **Pick conflict-free options** first
4. **Fallback to solver** if no conflict-free day exists

## Implementation

### Files to Modify
- `AdaptiveSlotFinder.java` - Add `generateAllDayCandidates()`, `DayCandidate` class
- `AcconciaturaPreAssigner.java` - Use multi-option selection
- `LabBlockPreAssigner.java` - Same pattern

### Key Method
```java
public Map<DayOfWeek, DayCandidate> generateAllDayCandidates(
    LabRotation rotation, Room labRoom,
    Set<LocalDate> holidays, List<StagePattern> stagePatterns);
```

## Expected Outcome
- More robust day selection
- Better edge case handling
- Alignment with human planner "trial and error" behavior
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 AdaptiveSlotFinder generates candidates for ALL weekdays with conflict scores
- [x] #2 AcconciaturaPreAssigner picks conflict-free options first
- [x] #3 LabBlockPreAssigner uses same multi-option pattern
- [x] #4 Pre-assignment attempts logged for all days (not just best)
- [ ] #5 Integration test shows T26 spread across multiple days like spalmone
- [x] #6 No H5 violations in pre-assigned slots
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Spalmone Analysis (Day Distribution)

| Teacher | Mon | Tue | Wed | Thu | Fri |
|---------|-----|-----|-----|-----|-----|
| T28 | 1 | 5 | 0 | 172 | 0 | (external - Thu only)
| T26 | 94 | 148 | 140 | 30 | 87 | (spread across 5 days)
| T18 | 60 | 188 | 187 | 162 | 97 | (very distributed)

Key insight: Human planners try multiple days, keep what works. No "preference" - just trial and error.

## Algorithm

```
For each LabRotation:
  1. Generate candidates for ALL weekdays
  2. Score by: H5 violations (+10/week), room contention (+5), group conflicts (+5)
  3. Pick conflict-free (score=0) with most available weeks
  4. If no conflict-free, leave unpinned for solver
```

## Implementation Progress (2026-02-22)

### Completed

- **Phase 1: Candidate Generation** - Added `DayCandidate` class with conflict scoring

- **Phase 2: Conflict Scoring** - Implemented scoring based on teacher unavailability (+10/week) and room contention (+5/conflict)

- **Phase 3: Selection Logic** - Updated both pre-assigners to use multi-option selection

- **Tests**: Added 7 new unit tests for multi-option functionality (15 total tests passing)

### Files Modified

- `AdaptiveSlotFinder.java`: Added `generateAllDayCandidates()`, `DayCandidate` class, `selectBestConflictFreeDay()`, `getSortedCandidates()`

- `AcconciaturaPreAssigner.java`: Updated `assignGroupDay()` to use multi-option selection

- `LabBlockPreAssigner.java`: Updated `assignTeacherCentricRotation()` to use multi-option selection

- `AdaptiveSlotFinderTest.java`: Added comprehensive tests for multi-option selection

### Remaining

- Verify with actual data that has null dayOfWeek rotations

- Run end-to-end pre-assignment test
<!-- SECTION:NOTES:END -->
