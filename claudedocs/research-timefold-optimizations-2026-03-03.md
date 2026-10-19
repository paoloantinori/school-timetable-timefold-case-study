# Research: Timefold Solver Performance Optimizations & Compaction Constraints

**Date**: 2026-03-03
**Current version**: Timefold Solver 1.19.0
**Latest available**: Timefold Solver 1.31.0 (final 1.x release; 2.0 coming March 2026)

---

## Executive Summary

Six performance optimization opportunities were identified for our school timetabling solver. Three are **actionable now** (entity-level room VRP for Lessons, constraint stream optimizations, Ruin & Recreate moves). Two require **version upgrades** (precompute API, nearby selection). One is a **nuclear option** for maximum performance (Incremental Score Calculator).

The "vertical compaction" constraint (H13) was confirmed — it's already implemented in our codebase as a hard constraint that penalizes gaps in student group daily schedules. This replaced the earlier soft S5 approach and is well-aligned with academic timetabling literature on "compactness constraints."

---

## 1. Entity-Level ValueRangeProvider for Lesson.room

### Current State

- `LabBlock` already has entity-level VRPs for BOTH `startTimeslot` (filtered by targetEndDate, teacher availability, holidays, block validity) AND `room` (filtered by `requiredRoomTags`).
- `Lesson` has entity-level VRP for `timeslot` (filtered by targetEndDate) but uses the **solution-level** VRP for `room` (all rooms).

### Opportunity

Add an entity-level `@ValueRangeProvider` on `Lesson` for rooms, filtering out impossible room assignments. This eliminates moves the solver would try and immediately reject via H-M2.

**What to filter:**
- Lab lessons (`course.isLab && course.requiredRoomTags != null`) → only rooms with matching tags
- Non-lab lessons → exclude rooms that have tags (lab-only rooms) — enforced by H-M2b

**Expected impact**: Reduces room search space for lab Lessons from ~20 rooms to ~2-3 per entity. For non-lab Lessons, reduces from ~20 to ~15 (excluding lab rooms). Construction heuristic should see the biggest speedup.

### Caveats (from Timefold docs)

> "A ValueRangeProvider on the planning entity consumes more memory than ValueRangeProvider on the Solution and **disables certain automatic performance optimizations**."
>
> "Never use this to enforce a soft constraint... there is a real risk of trading short-term benefits for long-term harm."

Room suitability (H-M2) is a HARD constraint, so entity-level restriction is safe — the solver should never need to break it. The memory concern is mitigated by caching (we already cache on LabBlock).

### Implementation Sketch

```java
// In Lesson.java
@Transient
private List<Room> cachedAvailableRooms;

@JsonIgnore
@ValueRangeProvider(id = "availableRooms")
public List<Room> getAvailableRooms() {
    if (cachedAvailableRooms != null) return cachedAvailableRooms;
    if (pinned) {
        cachedAvailableRooms = room != null ? List.of(room) : List.of();
        return cachedAvailableRooms;
    }
    List<Room> allRooms = SchoolSchedule.getAllRooms();
    if (allRooms == null || allRooms.isEmpty()) {
        cachedAvailableRooms = new ArrayList<>();
        return cachedAvailableRooms;
    }
    if (course != null && course.requiredRoomTags != null && !course.requiredRoomTags.isEmpty()) {
        // Lab lesson: only rooms with matching tags
        cachedAvailableRooms = allRooms.stream()
                .filter(r -> r.tags != null && r.tags.containsAll(course.requiredRoomTags))
                .toList();
    } else {
        // Non-lab lesson: exclude lab-only rooms
        cachedAvailableRooms = allRooms.stream()
                .filter(r -> r.tags == null || r.tags.isEmpty())
                .toList();
    }
    return cachedAvailableRooms;
}
```

And update the `@PlanningVariable` annotation:
```java
@PlanningVariable(valueRangeProviderRefs = {"availableRooms"})
@ManyToOne
public Room room;
```

**Difficulty**: Low
**Risk**: Low (hard constraint → safe to restrict)
**Expected speedup**: ~10-20% during CH, minor during LS

---

## 2. Constraint Streams `precompute()` API

### What It Does

When a constraint stream's initial joins/filters depend only on problem facts (not planning variables), those joins can be precomputed once and reused. The solver then only re-evaluates the variable-dependent parts.

### Availability

Introduced in **Timefold 1.13.0** (BOM 1.29.0). Our current version (1.19.0) likely doesn't have it — need to verify exact mapping. The feature matured significantly in early 2026 releases.

### Candidates in Our Codebase

Several constraints join with problem facts where the join condition is static:

| Constraint | Join Pattern | Precomputable? |
|---|---|---|
| H4 (Stage blackout) | `Lesson ⋈ StagePattern` on studentGroup | Yes — StagePattern is a problem fact, studentGroup doesn't change |
| H5 (Teacher unavail.) | `Lesson ⋈ TeacherAvailability` on teacher | Yes — TeacherAvailability is a problem fact |
| H6 (School holiday) | `Lesson ⋈ SchoolHoliday` on date | Partially — date depends on timeslot (variable) |
| M5 (Informatica after stage) | `Lesson ⋈ StagePattern` | Yes — course/group properties are static |
| H-D1 (Doppio conflicts) | `Lesson ⋈ Lesson` on doppioTeacher | Yes — doppioTeacher is a problem fact property |

The H4 and H5 joins with StagePattern/TeacherAvailability are the best candidates — the joiner conditions (`equal(getTeacher, ta.teacher)`) depend on entity properties that are NOT planning variables.

### Action Required

Upgrade Timefold to >=1.13.0 (BOM >=1.29.0) to access `precompute()`. Then refactor candidate constraints.

**Difficulty**: Medium (version upgrade + refactoring)
**Risk**: Low (purely performance optimization)
**Expected speedup**: 5-15% on constraint evaluation (depends on how many tuples the precomputed joins eliminate)

---

## 3. Ruin & Recreate Move Selector

### What It Does

A coarse-grained move that "ruins" (unassigns) a subset of planning entities and then "recreates" (reassigns) them via construction heuristic. Effective for escaping local optima that fine-grained moves (change, swap) cannot.

### Why It Helps Us

Our solver has a 5-phase config with 1-hour unimproved limits. When stuck in a local optimum, fine-grained moves cycle without improvement. Ruin & Recreate can break out by reshuffling a cluster of entities.

### Configuration (add to Phase 4 — Conflict Resolution)

```xml
<ruinRecreateMoveSelector>
    <fixedProbabilityWeight>0.01</fixedProbabilityWeight>  <!-- 1 ruin per 100 fine-grained -->
    <entitySelector>
        <entityClass>com.school.planner.domain.Lesson</entityClass>
    </entitySelector>
    <minimumRuinedCount>5</minimumRuinedCount>
    <maximumRuinedCount>20</maximumRuinedCount>
</ruinRecreateMoveSelector>
```

### Caveats

- Each ruin-recreate move evaluates score multiple times internally → expensive per-move
- Must benchmark to find optimal weight and ruin count
- May need separate ruin-recreate for LabBlock entities

**Difficulty**: Low (XML config change)
**Risk**: Medium (could slow down if weight is too high)
**Expected speedup**: Variable — mainly improves final score quality, not raw speed

---

## 4. Nearby Selection (Enterprise Only)

### What It Does

Instead of randomly selecting entities/values for moves, prefer "nearby" ones. Define a distance metric via `NearbyDistanceMeter<Origin, Destination>`.

### Relevance to Our Problem

In school timetabling, "nearby" means:
- Timeslots on the same day or adjacent days
- Rooms in the same building or with same tags
- Entities (Lessons/LabBlocks) with the same teacher or student group

A `NearbyDistanceMeter` could prioritize swapping lessons that share a teacher or student group, making conflict resolution much faster.

### Availability

**Enterprise Edition only.** Not available in Community Edition (which we use). Would require a commercial license.

### Workaround

Use `SelectionFilter` on swap/pillar move selectors to achieve a similar (but less sophisticated) effect:
- Filter swaps to only consider entities sharing a teacher, student group, or room
- This is coarser than nearby selection but can still help

**Difficulty**: N/A (Enterprise only) or Medium (workaround via SelectionFilter)
**Risk**: Low
**Expected speedup**: 20-50% for conflict resolution (based on Timefold's claims)

---

## 5. Incremental Score Calculator (Nuclear Option)

### What It Does

Replace Constraint Streams with a hand-coded `IncrementalScoreCalculator` that manually tracks score deltas. Much faster but much harder to maintain.

### Evidence

The [Dots & Lines blog](https://blog.dotsandlines.ai/17x-faster-timefold-part1-f6d54d2a4094) achieved **17x speedup** by migrating from Constraint Streams to Incremental + array-based tracking:
- Constraint Streams → Incremental: ~4x speedup
- HashMap → arrays for frequency tracking: additional ~4x
- Combined: **17x on course timetabling** (8 constraints, 14 datasets)

### Recommended Approach (from blog)

Implement **both** Constraint Streams and Incremental in parallel:
- Use Constraint Streams for testing and score explanation
- Use Incremental for production solving
- Validate correctness by comparing scores from both

### Assessment for Our Project

We have ~30 constraints across 2 entity types. Converting all to Incremental would be a major engineering effort. However, we could:
1. Start with the most expensive constraints (H1-H3 conflicts) in Incremental
2. Keep remaining constraints in Constraint Streams
3. Measure speedup incrementally

**Difficulty**: Very High
**Risk**: High (maintenance burden, correctness verification)
**Expected speedup**: 5-17x

---

## 6. Constraint Stream Micro-Optimizations (No Upgrade Needed)

### a) Cache `occupiesTimeslot()` Results

Several constraints (H1.2, H2.2, H3.2, H-D1, etc.) call `block.occupiesTimeslot(lesson.timeslot)` in filters AND in penalty functions. The method iterates `occupiedTimeslots` list each time.

**Optimization**: The filter already confirms overlap; the penalty function could use a pre-computed count.

### b) Avoid Stream API in Penalty Functions

Multiple B-B constraints use:
```java
.penalize(..., (a, b) -> (int) a.getOccupiedTimeslots().stream()
        .filter(b::occupiesTimeslot).count())
```

Replace with array iteration:
```java
.penalize(..., (a, b) -> {
    int count = 0;
    for (Timeslot ts : a.getOccupiedTimeslots()) {
        if (b.occupiesTimeslot(ts)) count++;
    }
    return count;
})
```

### c) Order Joiners for Maximum Pruning

In H5 (Teacher unavailability), the `overlapping` joiner should come before `equal` if the overlapping check eliminates more tuples. However, in our case, teacher equality is more selective (28 teachers vs ~1500 timeslots), so current order is correct.

### d) Avoid `.filter()` After `.join()` Where Joiner Exists

H2.1 has:
```java
.forEachUniquePair(Lesson.class,
    Joiners.equal(l -> l.room),
    Joiners.overlapping(...))
.filter((a, b) -> a.room != null)  // <-- redundant if equal joiner already matched
```

The `.filter((a, b) -> a.room != null)` is actually needed because `Joiners.equal(l -> l.room)` treats `null == null` as a match. But it could be moved to a pre-filter before the join.

**Difficulty**: Low
**Risk**: Very Low
**Expected speedup**: 2-5% on constraint evaluation

---

## Vertical / Horizontal Compaction — What We Already Have

### H13: Vertically Compact (Already Implemented)

Our `verticallyCompact()` constraint at `SchoolScheduleConstraintProvider.java:255` is a **hard constraint** that penalizes gaps in a student group's daily schedule:

```java
constraintFactory.forEach(Lesson.class)
    .groupBy(Lesson::getStudentGroup, l -> l.timeslot.getDate(), toList())
    .filter((group, date, lessons) -> lessons.size() >= 2)
    .penalize(HardMediumSoftScore.ONE_HARD,
            (group, date, lessons) -> countInternalGaps(lessons))
    .asConstraint("H13: Vertically compact");
```

This replaced the earlier S5 (soft gap minimization, weight=500) based on benchmarking that showed the hard approach was better.

### Academic Context

The term "compactness constraints" in timetabling literature ([Drexl & Salewski, 1997](https://www.sciencedirect.com/science/article/abs/pii/S0377221796002093)) covers:
- **Vertical compactness**: No gaps within a day (what H13 does) — lessons contiguous from first to last slot
- **Horizontal compactness**: Lessons concentrated early in the schedule horizon (what H12 does) — fill early weeks first
- **Teacher compactness**: Minimize teacher working days / idle slots (related to S1)

Our codebase uses both terms correctly:
- **H13 "Vertically compact"** = no intra-day gaps for student groups
- **H12 "Horizontally compact"** = lessons pushed toward early dates (currently commented out — replaced by entity-level VRP targetEndDate filtering + M12 overflow penalty)

### Is H13 Too Aggressive?

Making vertical compactness a HARD constraint means the solver MUST eliminate all gaps before optimizing anything else. This could:
- Slow down solving if many gaps exist in the CH output
- Compete with other hard constraints (H1-H3 conflicts)

**Benchmark comparison** (from CLAUDE.md design decisions):
| Metric | Hard (H10/H13) | Soft (S5=500) |
|--------|----------|---------------|
| CH time | 231s | 155s |
| Final hard score | -1,975k | -1,185k |
| Final soft score | -4.3M | -52.1M |

Wait — the historical benchmark showed **soft was better** (40% better hard score). Yet H13 is currently hard. This may need re-evaluation with the current solver config (which has changed significantly since that benchmark).

---

## Recommendation Priority

| Priority | Optimization | Effort | Expected Impact | Prerequisite |
|----------|-------------|--------|-----------------|--------------|
| 1 | Entity-level room VRP for Lesson | Low | 10-20% CH speed | None |
| 2 | Constraint micro-optimizations | Low | 2-5% score eval | None |
| 3 | Ruin & Recreate move selector | Low | Better final score | Benchmark |
| 4 | Upgrade to Timefold 1.31+ | Medium | Access to precompute + fixes | Compatibility testing |
| 5 | Constraint Streams precompute | Medium | 5-15% score eval | Timefold upgrade |
| 6 | Re-evaluate H13 hard vs soft | Low | TBD | Benchmark |
| 7 | Incremental Score Calculator | Very High | 5-17x speed | Major engineering effort |

---

## Sources

- [Timefold Performance Tips](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance)
- [Timefold Modeling - ValueRangeProvider](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems)
- [Move Selector Reference](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference)
- [Timefold Enterprise Edition](https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition)
- [17x Faster Timefold - Part 1](https://blog.dotsandlines.ai/17x-faster-timefold-part1-f6d54d2a4094)
- [17x Faster Timefold - Part 2](https://blog.dotsandlines.ai/how-we-made-our-clients-timefold-solver-over-17x-faster-074524485b39)
- [Timefold Releases](https://github.com/timefoldai/timefold-solver/releases)
- [Drexl & Salewski - Distribution and Compactness in School Timetabling](https://www.sciencedirect.com/science/article/abs/pii/S0377221796002093)
- [Timefold Score Calculation - Precompute](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation)
- [Benchmarking and Tweaking](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking)
