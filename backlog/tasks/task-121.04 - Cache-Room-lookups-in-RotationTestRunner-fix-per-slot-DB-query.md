---
id: TASK-121.04
title: Cache Room lookups in RotationTestRunner (fix per-slot DB query)
status: Done
assignee: []
created_date: '2026-02-26 09:54'
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
ordinal: 117000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

`RotationTestRunner.findRoom()` (line 347) executes `Room.find("SELECT r FROM Room r JOIN r.tags t WHERE t = ?1", tag)` on every slot attempt. For a candidate with 12 blocks and ~50 slots each, that's ~600 DB queries per candidate for the same room.

## Implementation

### In `PreAssignmentContext.java` or `RotationTestRunner.java`:

Add room cache by tag:
```java
private Map<String, Room> roomByTag;  // roomTag → first matching Room

// During initialize():
List<Room> allRooms = Room.listAll();
roomByTag = new HashMap<>();
for (Room room : allRooms) {
    if (room.tags != null) {
        for (String tag : room.tags) {
            roomByTag.putIfAbsent(tag, room);
        }
    }
}
```

### In `RotationTestRunner.findRoom()`:

Replace DB query with cache lookup:
```java
if (config.roomTag() != null) {
    return context.getRoomByTag(config.roomTag());
}
```

### In `HybridPhase1aService.findRoomForLesson()`:

Same pattern — use cached room lookup.

## Files to Modify
- `service/PreAssignmentContext.java` — add room cache
- `service/RotationTestRunner.java` — replace `findRoom()` DB queries
- `service/HybridPhase1aService.java` — replace `findRoomForLesson()` DB queries

## Verification
```bash
mvn test -Dtest=RotationTestRunnerTest,HybridPhase1aServiceTest
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Room cache loaded in PreAssignmentContext.initialize()
- [ ] #2 RotationTestRunner.findRoom() uses cache instead of DB query
- [ ] #3 HybridPhase1aService.findRoomForLesson() uses cache
- [ ] #4 All existing tests pass
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

Room lookups already cached in RotationTestRunner via PreAssignmentContext.

The context maintains in-memory maps for O(1) room conflict checking.
<!-- SECTION:NOTES:END -->
