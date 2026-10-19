---
id: TASK-031
title: Review Value Range Provider Usage for Solver Optimization
status: Done
assignee: []
created_date: '2026-02-18 09:26'
updated_date: '2026-03-09 19:50'
labels:
  - solver
  - claude-planned
  - optimization
  - research
dependencies: []
priority: low
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
<!-- SECTION:DESCRIPTION:BEGIN -->
## Background

Value Range Providers (VRPs) are a Timefold Solver feature that defines the set of possible values for a `@PlanningVariable`. Proper use of VRPs can significantly improve solver performance by:

1. **Reducing search space** - Filtering out impossible values before the solver even tries them
2. **Avoiding unnecessary constraint evaluation** - Values outside the range never trigger constraint checks
3. **Enabling entity-specific optimization** - Each planning entity can have its own filtered value set

## Current Implementation Analysis

### What's Already Implemented (GOOD)

1. **Entity-based VRP for timeslots** (`Lesson.getAvailableTimeslots()`, `LabBlock.getAvailableStartTimeslots()`)
   - Filters timeslots based on student group's `targetEndDate`
   - Implements horizontal compactness by never offering late dates as options
   - Pinned entities return only their assigned value (optimization for fixed assignments)
   - Uses caching (`cachedAvailableTimeslots`) to avoid recomputing on every move

2. **Solution-level VRPs** (`SchoolSchedule.getTimeslots()`, `SchoolSchedule.getRooms()`)
   - Standard `@ValueRangeProvider` on the `@PlanningSolution` class
   - Provides the base value sets for all planning entities

3. **Entity filtering in solver config** (`UnpinnedLessonFilter`)
   - Filters pinned lessons from swap/pillar move selectors
   - Prevents wasted moves on fixed assignments

### What's NOT Implemented (Potential Optimizations)

#### 1. Room Value Filtering by Lab Requirements (MEDIUM PRIORITY)

**Current state**: Both `Lesson` and `LabBlock` use `@PlanningVariable` for `room` without a `valueRangeProviderRefs`, meaning they use the full `SchoolSchedule.getRooms()` list.

**Opportunity**: Lab courses (`Course.isLab == true`) with `Course.requiredRoomTags` could use an entity-based VRP that filters rooms to only those matching the required tags. This would:
- Eliminate invalid room assignments at the value source
- Reduce H-M2 constraint violations during search
- Make construction heuristic more efficient

**Trade-off**: According to Timefold docs, entity-based VRPs "consume more memory and disable certain automatic performance optimizations." With ~20 rooms, the memory impact is minimal, but the optimization benefit should be benchmarked.

**Code example**:
```java
@PlanningVariable(valueRangeProviderRefs = {"availableRooms"})
@ManyToOne
public Room room;

@ValueRangeProvider(id = "availableRooms")
public List<Room> getAvailableRooms() {
    if (course == null || course.requiredRoomTags.isEmpty()) {
        return SchoolSchedule.getAllRooms();
    }
    return SchoolSchedule.getAllRooms().stream()
        .filter(room -> room.tags.containsAll(course.requiredRoomTags))
        .toList();
}
```

#### 2. Nearby Timeslot Filtering (LOW PRIORITY)

**Current state**: Timeslot VRP filters by date but not by proximity to current assignment.

**Opportunity**: For local search phases, a `filterClass` on the valueSelector could prioritize nearby timeslots (same day/week) over distant ones. This is documented in `claudedocs/compact-schedule-constraint-strategies.md` but not implemented.

**Trade-off**: Adds complexity to solver config; benefit likely marginal given S5 (student gap minimization) already incentivizes compactness.

## Recommendations

| Optimization | Priority | Effort | Expected Impact |
|--------------|----------|--------|-----------------|
| Room VRP for lab courses | Medium | 2-3 hours | Moderate - reduces H-M2 violations during CH |
| Nearby timeslot filtering | Low | 1-2 hours | Low - S5 already handles compactness |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Research complete: All VRP opportunities documented (timeslot filtering, room filtering, nearby selection)
- [x] #2 Decision documented: Implement room VRP or explain why current approach is acceptable
- [ ] #3 If implemented: Room VRP uses caching to avoid recomputing filtered room lists
- [ ] #4 If implemented: Benchmark comparison shows improvement or no regression vs baseline
- [ ] #5 All tests pass: mvn test continues to pass after any changes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Started parallel research with sc:research skill

## Research Complete (2026-02-27)

**Decision: DO NOT implement additional entity-based VRPs.**

### Key Findings:

1. **LabBlock.room already has entity-based VRP** - filters by `requiredRoomTags`

2. **LabBlock.startTimeslot already has entity-based VRP** - filters by targetEndDate, teacher availability, holidays, block validity

3. **Lesson.timeslot already has entity-based VRP** - filters by targetEndDate

4. **Only gap is Lesson.room** - but implementation cost outweighs benefit:

- Memory overhead for ~7,000 entities

- Disables automatic performance optimizations (per Timefold docs)

- Reduces solver's ability to escape local optima

- H-M2.1 is already hard constraint - solver learns quickly

### Recommendation:

Keep current approach. If H-M2.1 evaluation becomes bottleneck, consider filtered selection in solver config instead.

**Full report**: `claudedocs/research-value-range-provider-2026-02-27.md`

## Research Complete - 2026-02-27

**Decision: DO NOT implement additional entity-based VRPs**

**Current VRP Status:**

- LabBlock.room: Already has entity-based VRP filtering by requiredRoomTags

- Lesson.room: Uses solution-level VRP (no filtering)

**Why NOT Implement:**

1. LabBlock (most constrained) already covered

2. Only ~20 rooms - constraint evaluation is fast

3. Entity-based VRPs disable automatic optimizations

4. H-M2 hard constraint quickly learned during CH

**Alternative**: Use filtered selection in solver config if H-M2.1 becomes bottleneck

**Report**: claudedocs/research-value-range-provider-2026-02-27.md
<!-- SECTION:NOTES:END -->
