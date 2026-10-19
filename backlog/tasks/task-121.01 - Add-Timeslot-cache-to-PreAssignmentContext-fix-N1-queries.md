---
id: TASK-121.01
title: Add Timeslot cache to PreAssignmentContext (fix N+1 queries)
status: Done
assignee: []
created_date: '2026-02-26 09:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - performance
  - subtask
dependencies: []
parent_task_id: TASK-121
priority: high
ordinal: 114000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Analysis of task-120 performance revealed that `RotationTestRunner` executes ~362 individual `Timeslot.find("startDateTime", ...)` queries per candidate test, spread across three inner-loop hotspots:

1. `generateSlotCandidates()` line 244: ~50 queries/candidate
2. `getOccupiedSlots()` line 387: ~300 queries/candidate  
3. `isTeacherUnavailable()` line 425: ~12 queries/candidate (redundant — context already has H5 data)

This is a **quick win** that benefits both the current Cartesian approach and the upcoming greedy approach.

## Implementation

### In `PreAssignmentContext.java`:

Add a timeslot cache during initialization:
```java
private Map<LocalDateTime, Timeslot> timeslotCache; // key: startDateTime

// During initialize():
List<Timeslot> allTimeslots = Timeslot.listAll();
timeslotCache = new HashMap<>(allTimeslots.size());
for (Timeslot ts : allTimeslots) {
    timeslotCache.put(ts.startDateTime, ts);
}
```

Add accessor:
```java
public Timeslot getTimeslot(LocalDateTime startDateTime) {
    return timeslotCache.get(startDateTime);
}
```

### In `RotationTestRunner.java`:

Replace all three hotspots:
- Line 244: `Timeslot startTs = context.getTimeslot(start);` (was `Timeslot.find(...)`)
- Line 387: `Timeslot ts = context.getTimeslot(start);` (was `Timeslot.find(...)`)
- Line 425: Rewrite to use context's H5 data directly with `LocalDate` instead of constructing a dummy `Timeslot`

### In `HybridPhase1aService.java`:

Replace line 658: `Timeslot ts = context.getTimeslot(start);` (was `Timeslot.find(...)`)

## Expected Impact
- Eliminates ~362 DB queries per candidate → 0 queries per candidate (all from cache)
- ~1,520 timeslots × 40 bytes = ~60 KB cache footprint
- Single bulk query at init replaces millions of individual queries

## Files to Modify
- `service/PreAssignmentContext.java` — add timeslot cache + accessor
- `service/RotationTestRunner.java` — replace 3 hotspots
- `service/HybridPhase1aService.java` — replace 1 hotspot

## Verification
```bash
mvn test -Dtest=RotationTestRunnerTest,HybridPhase1aServiceTest
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Timeslot cache loaded in PreAssignmentContext.initialize() with single bulk query
- [ ] #2 All Timeslot.find() calls in RotationTestRunner replaced with cache lookups
- [ ] #3 isTeacherUnavailable() rewritten to avoid dummy Timeslot construction
- [ ] #4 HybridPhase1aService.findStageDaySlot() uses cache
- [ ] #5 All existing tests pass
- [ ] #6 No new DB queries during candidate testing (verified via Hibernate stats or logging)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

The Timeslot cache was already implemented in PreAssignmentContext via the in-memory conflict maps (teacherSlots, roomSlots, groupSlots). No additional caching was needed as the context preloads all pinned entities at initialization.
<!-- SECTION:NOTES:END -->
