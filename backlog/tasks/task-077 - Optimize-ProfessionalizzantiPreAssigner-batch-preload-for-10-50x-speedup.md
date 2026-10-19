---
id: TASK-077
title: 'Optimize ProfessionalizzantiPreAssigner: batch preload for 10-50x speedup'
status: Done
assignee: []
created_date: '2026-02-23 10:01'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - performance
  - pre-assignment
dependencies: []
priority: high
ordinal: 53000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The ProfessionalizzantiPreAssigner is slow due to **database query amplification**. The algorithm is already greedy, but `PreAssignmentValidator.canPin()` runs 10+ separate DB queries per validation attempt. Called in a nested loop (groups × dates × slots × attempts), this creates hundreds of thousands of queries.

## Root Cause Analysis

Each `canPin()` call triggers ~10-12 DB queries:
- H1 Teacher conflict: 2 queries
- H2 Room conflict: 2 queries  
- H3 Student group conflict: 2 queries
- H5 Teacher unavailability: 1 query
- H10 Preparazione before stage: 1+ query
- H15 Same-subject consecutive: 1 query
- H16 Consecutive teacher-room: 1 query
- H-D1 Doppio conflict: 1+ query

Worst case: 14 groups × 100 dates × 6 slots × 10 attempts × 10 queries = **840,000 DB queries**

## Solution

Batch preload all pinned entities at the start, then validate in-memory using hash maps and sets.

### Implementation Approach

1. Create `PreAssignmentContext` class to hold preloaded data:
   - `Map<Long, Set<Long>> pinnedLessonsByTeacher` (teacherId → timeslotIds)
   - `Map<Long, Set<Long>> pinnedLessonsByRoom` (roomId → timeslotIds)
   - `Map<Long, Set<Long>> pinnedLessonsByGroup` (groupId → timeslotIds)
   - `Map<Long, Set<Long>> pinnedBlocksByTeacher`
   - `Map<Long, Set<Timeslot>> teacherUnavailability`
   - etc.

2. Add `PreAssignmentValidator.canPinInMemory(lesson, ts, room, context)` method

3. Update `ProfessionalizzantiPreAssigner.assignGroupStageDays()` to use in-memory validation

### Expected Outcome

- 10-50x speedup (minutes → seconds)
- Same assignments made (correctness preserved)

## Files Involved

- `service/ProfessionalizzantiPreAssigner.java` - main pre-assigner
- `service/PreAssignmentValidator.java` - validation layer
- New: `service/PreAssignmentContext.java` - preloaded context
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Preload all pinned Lessons and LabBlocks into in-memory maps before assignment loop
- [x] #2 Add canPinInMemory() method that validates against preloaded data (no DB queries)
- [x] #3 ProfessionalizzantiPreAssigner uses in-memory validation instead of per-call DB queries
- [x] #4 Runtime drops from minutes to seconds (measure with /api/schedule/pre-assign/professionalizzanti)
- [x] #5 All existing tests pass, same assignments produced
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Claude Analysis Additions

### 1. Same optimization needed for AcconciaturaPreAssigner and LabBlockPreAssigner - same N+1 query pattern

### 2. Context should be mutable - update in-memory maps when pinning so subsequent validations are accurate (replaces ad-hoc roomsUsedPerSlot/teachersUsedInCurrentRun tracking)

### 3. findAvailableRoom() also does 2 queries per call - should use preloaded context

### 4. Preload: List<Room> classrooms, Map<Long, Room> roomById for quick lookup
<!-- SECTION:NOTES:END -->
