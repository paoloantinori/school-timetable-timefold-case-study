# Research Report: Value Range Provider Usage for Solver Optimization

**Date**: 2026-02-27
**Task**: task-031
**Status**: Complete

## Executive Summary

**Decision: DO NOT implement additional entity-based VRPs at this time.**

The planner already has comprehensive Value Range Provider optimization:

1. **LabBlock.room**: Already uses entity-based VRP (`getAvailableRooms()`) that filters by `requiredRoomTags`
2. **LabBlock.startTimeslot**: Already uses entity-based VRP (`getAvailableStartTimeslots()`) with extensive filtering
3. **Lesson.timeslot**: Already uses entity-based VRP (`getAvailableTimeslots()`) filtering by `targetEndDate`

The only gap is `Lesson.room`, which uses the solution-level VRP. Implementing an entity-based room VRP for Lessons would provide **marginal benefit at non-trivial cost** due to Timefold's documented performance trade-offs.

---

## 1. Current Implementation Analysis

### 1.1 Solution-Level VRPs (SchoolSchedule.java)

| Field | Annotation | Scope |
|-------|------------|-------|
| `timeslots` | `@ValueRangeProvider` | All entities |
| `rooms` | `@ValueRangeProvider` | All entities (overridden by LabBlock) |

### 1.2 Entity-Based VRPs (Already Implemented)

#### LabBlock.startTimeslot (Lines 77-131, LabBlock.java)

Filters start timeslots by:
- Student group's `targetEndDate` (schedule horizon)
- Teacher unavailability (H5.2 - VACATION/SICK periods)
- School holidays (H6.2)
- Block validity (H9 - block must fit within day)

**Caching**: Uses `cachedAvailableStartTimeslots` to avoid recomputing.

#### LabBlock.room (Lines 163-202, LabBlock.java)

Filters rooms by:
- `course.requiredRoomTags` - only rooms with ALL required tags
- Pinned blocks return only assigned room

**Caching**: Uses `cachedAvailableRooms`.

#### Lesson.timeslot (Lines 64-127, Lesson.java)

Filters timeslots by:
- Student group's `targetEndDate` (horizontal compactness)
- Pinned lessons return only assigned timeslot

**Caching**: Uses `cachedAvailableTimeslots`.

### 1.3 Gap Analysis

| Planning Variable | Current VRP | Filtered? | Gap? |
|-------------------|-------------|-----------|------|
| Lesson.timeslot | Entity-based (`getAvailableTimeslots`) | Yes (targetEndDate) | No |
| Lesson.room | Solution-level (`SchoolSchedule.rooms`) | No | **Yes** |
| LabBlock.startTimeslot | Entity-based (`getAvailableStartTimeslots`) | Yes (multiple) | No |
| LabBlock.room | Entity-based (`getAvailableRooms`) | Yes (requiredRoomTags) | No |

---

## 2. Timefold Documentation Findings

### 2.1 Entity-Based VRP Trade-offs

From the [Timefold documentation](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems):

> **A `ValueRangeProvider` on the planning entity consumes more memory than `ValueRangeProvider` on the Solution and disables certain automatic performance optimizations.**

Key constraints:
1. **Memory overhead**: Each entity stores its own value range list
2. **Disabled optimizations**: Automatic performance optimizations are disabled
3. **Solution cloning overhead**: Increased overhead during solution cloning (significant for huge datasets)
4. **Chained variables**: Not compatible with chained planning variables

### 2.2 Recommended Alternative

Timefold recommends using a parameter-based VRP when possible:

```java
@ValueRangeProvider
public List<Room> getPossibleRoomListWithSolution(Timetable timetable) {
    return timetable.getRoomListFor(this);
}
```

This approach:
- Consumes less memory (no entity-level storage)
- Reduces solution cloning overhead
- Still provides per-entity filtering

**However**: This requires the planning entity to have access to the solution instance, which the current codebase doesn't support.

### 2.3 Built-in Hard Constraints vs VRP Filtering

From the [Performance tips documentation](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance):

> Instead of implementing a hard constraint, it can sometimes be built in... Use a ValueRangeProvider on the planning entity or filtered selection to define that Course A should only be assigned a Room different than X.

**Important caveat**:
> Usually this is not a good idea because there is a real risk of trading short-term benefits for long-term harm: Many optimization algorithms rely on the freedom to break hard constraints when changing planning entities, to get out of local optima.

---

## 3. Decision Analysis

### 3.1 Option A: Implement Entity-Based Room VRP for Lesson

**Implementation**:

```java
// In Lesson.java
@JsonIgnore
@ValueRangeProvider(id = "availableRooms")
public List<Room> getAvailableRooms() {
    if (cachedAvailableRooms != null) {
        return cachedAvailableRooms;
    }

    if (pinned) {
        cachedAvailableRooms = room != null ? List.of(room) : List.of();
        return cachedAvailableRooms;
    }

    List<Room> allRooms = SchoolSchedule.getAllRooms();
    if (allRooms == null || allRooms.isEmpty()) {
        cachedAvailableRooms = new ArrayList<>();
        return cachedAvailableRooms;
    }

    // Filter by required room tags for lab courses
    if (course != null && course.requiredRoomTags != null && !course.requiredRoomTags.isEmpty()) {
        cachedAvailableRooms = allRooms.stream()
                .filter(r -> r.tags != null && r.tags.containsAll(course.requiredRoomTags))
                .toList();
    } else {
        cachedAvailableRooms = allRooms;
    }

    return cachedAvailableRooms;
}
```

**Update @PlanningVariable annotation**:
```java
@PlanningVariable(valueRangeProviderRefs = {"availableRooms"})
@ManyToOne
public Room room;
```

**Pros**:
- Reduces H-M2.1 violations during construction heuristic
- Filters out invalid rooms before constraint evaluation
- Consistent with LabBlock implementation

**Cons**:
- **Memory overhead**: ~7,000 Lesson entities each storing a room list
- **Disabled optimizations**: Automatic performance optimizations disabled
- **Local optima risk**: Solver cannot temporarily violate H-M2 to escape local optima
- **Marginal benefit**: H-M2 is already a HARD constraint; solver learns to avoid violations quickly

### 3.2 Option B: Keep Current Approach (Recommended)

**Rationale**:

1. **LabBlock already covered**: Lab blocks (the most constrained entities) already have entity-based room filtering

2. **Room count is small**: Only ~20 rooms total, 6 of which are lab rooms
   - Lab room breakdown: 2 informatica, 1 meccanica, 1 termoidraulica, 1 acconciatura, g01
   - Non-lab lessons can use all 20 rooms; filtering adds minimal value

3. **Constraint is hard**: H-M2.1 is a HARD constraint - solver quickly learns to avoid violations

4. **Local search needs flexibility**: Allowing temporary H-M2 violations helps solver escape local optima

5. **Memory/performance trade-off**: 7,000 Lesson entities with cached room lists = significant memory overhead

**Pros**:
- No memory overhead
- No disabled optimizations
- Solver maintains flexibility for local search
- Simpler codebase

**Cons**:
- Construction heuristic may try invalid room assignments (but H-M2 catches them)

---

## 4. Recommendation

**DO NOT implement entity-based room VRP for Lesson.**

### Justification

| Factor | Analysis |
|--------|----------|
| **Current state** | LabBlock (most constrained) already has room VRP |
| **Room count** | ~20 total, small enough for efficient constraint evaluation |
| **Constraint type** | H-M2 is hard constraint; solver learns quickly |
| **Memory cost** | ~7,000 entities * cached list = non-trivial overhead |
| **Performance risk** | Disables automatic optimizations per Timefold docs |
| **Local search impact** | Reduces solver's ability to escape local optima |

### Alternative: Consider Filtered Selection (Future)

If H-M2.1 evaluation becomes a bottleneck during benchmarking, consider **filtered selection** in solver config instead:

```xml
<changeMoveSelector>
    <valueSelector id="roomSelector" variableName="room">
        <filterClass>com.school.planner.solver.RoomSuitabilityFilter</filterClass>
    </valueSelector>
</changeMoveSelector>
```

This provides filtering during move selection without the memory overhead of entity-based VRPs.

---

## 5. Benchmark Validation (If Reconsidering)

If room VRP is reconsidered in the future, validate with:

```bash
# Branch 1: Baseline (current)
./scripts/benchmark.sh feature/cartesian-block-entity 5

# Branch 2: With Lesson room VRP
# Create experiment branch, implement VRP, then:
./scripts/benchmark.sh experiment/lesson-room-vrp 5
```

**Success criteria**:
- Construction heuristic time reduced by >10%
- No regression in final hard score
- Memory usage increase <20%

---

## 6. Task Completion

Acceptance criteria status:

| # | Criterion | Status |
|---|-----------|--------|
| 1 | Research complete: All VRP opportunities documented | Done |
| 2 | Decision documented: Implement room VRP or explain why current approach is acceptable | Done (NOT implementing) |
| 3 | If implemented: Room VRP uses caching | N/A |
| 4 | If implemented: Benchmark comparison | N/A |
| 5 | All tests pass | N/A (no code changes) |

---

## 7. References

- [Timefold Modeling Planning Problems](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems) - VRP documentation
- [Timefold Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance) - Built-in hard constraints discussion
- Task file: `backlog/tasks/task-031 - Review-Value-Range-Provider-Usage-for-Solver-Optimization.md`
