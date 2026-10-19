# Research: Slot-Aware Horizontal Compactness Feasibility

**Date**: 2026-03-07
**Topic**: Can Timefold's constraint streaming API support a "smarter" M12 constraint that only penalizes lessons when earlier days have available slots?

## Executive Summary

**Conclusion**: The slot-aware approach is **theoretically possible but impractical** due to:
1. **O(n × m) complexity** (~12M tuple pairs for 8,000 lessons × 1,500 timeslots)
2. **Circular dependency**: "Availability" is itself constraint-dependent
3. **No incremental benefit**: Cross-day lookups defeat Timefold's incremental scoring

The current **grace period approach (30 days)** is the recommended solution.

---

## 1. The Problem Statement

### Current Implementation (M12: Horizontally Compact)

```java
// Lines 460-481 in SchoolScheduleConstraintProvider.java
Constraint horizontallyCompact(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> !lesson.pinned)
            .penalize(HardMediumSoftScore.of(0, 100, 0),
                    lesson -> {
                        long daysFromStart = ChronoUnit.DAYS.between(
                                effectiveStart, lesson.timeslot.getDate());
                        // Only penalize if beyond grace period
                        return daysFromStart > HORIZONTAL_COMPACT_GRACE_DAYS
                                ? (int)(daysFromStart - HORIZONTAL_COMPACT_GRACE_DAYS)
                                : 0;
                    })
            .asConstraint("M12: Horizontally compact");
}
```

**Limitation**: Penalizes all lessons beyond day 30, even if earlier days are full.

### Desired "Smarter" Behavior

Only penalize a lesson on day X if:
1. An earlier day Y exists with available slots
2. The student group could theoretically attend on day Y

---

## 2. Complexity Analysis

### Problem Scale

| Entity | Count | Notes |
|--------|-------|-------|
| Lessons | ~8,000 | After course generation |
| Timeslots | ~1,500 | Sep-Jun, 6 slots/day, Mon-Fri |
| Student Groups | 14 | FB, FA, MODA, FC, etc. |
| Days | ~200 | School year days |

### Cross-Product Complexity

A slot-aware constraint would need to join:
- **Lessons × Timeslots** = 8,000 × 1,500 = **12,000,000 tuple pairs**
- Then filter by date comparison (earlier than lesson date)
- Then check "availability" (itself expensive)

**Timefold's warning** (from docs):
> "To process a thousand lessons, the constraint first creates a cross-product of one million pairs, only to throw away pretty much all of them before penalizing. Reducing the size of the cross-product by half will therefore double the move evaluation speed."

### Incremental Score Calculation Impact

Timefold's constraint streams use **incremental (delta-based) scoring**:
- When a lesson moves from slot A to slot B, only affected constraints re-evaluate
- Cross-day lookups would require re-checking all earlier days for every move
- This defeats the incremental benefit, degrading to O(n) per move instead of O(1)

---

## 3. Why "Availability" is Constraint-Dependent

A slot is "available" only if it doesn't violate hard constraints:

| Constraint | What it blocks |
|------------|----------------|
| H5 | Teacher unavailability (vacations, sick days) |
| H6 | School holidays |
| H4 | Stage blackout days (for non-prof groups) |
| H1-H3 | Resource conflicts (teacher, room, group) |

**Circular dependency**: To know if a slot is "available," you'd need to check hard constraints — but those constraints are being evaluated simultaneously!

### The Pre-Assignment Precedent

The codebase already handles this in pre-assignment (`HybridPhase1aService.java`):
- Pre-assignment runs **before** the solver
- It checks for "0 hard violations" before pinning
- This is a **CSP (Constraint Satisfaction Problem)** approach, not a soft constraint

A slot-aware M12 would essentially need to run a mini-CSP check for every lesson — prohibitively expensive.

---

## 4. Alternative Approaches Analyzed

### Approach A: Precompute Available Slots per Day

**Idea**: Before solving, compute a map:
```
Map<StudentGroupId, Map<LocalDate, Integer>> availableSlotsPerDay;
```

**Problems**:
1. Precomputed data becomes stale as solver moves lessons (conflicts change)
2. Would need to recompute after every move — defeats incremental scoring
3. Storage: 14 groups × 200 days × varying slot counts = significant memory

**Verdict**: ❌ Not feasible for incremental scoring

### Approach B: Custom Constraint Collector with Lookup

**Idea**: Implement `UniConstraintCollector` that tracks slot usage per day:

```java
public class SlotAvailabilityCollector implements UniConstraintCollector<Lesson, ..., Map<LocalDate, Integer>> {
    // accumulate(): increment count for lesson's date
    // finisher(): return map of date → usage count
}
```

**Problems**:
1. Collector runs **after** grouping, not during individual lesson evaluation
2. Would need to group by student group first, then compare dates within group
3. Still requires cross-day comparison in penalty function

**Verdict**: ❌ Doesn't solve the core cross-day lookup problem

### Approach C: Two-Phase Approach (Pre-solver Analysis)

**Idea**: Before solving, analyze which days are "full" and store as problem facts:

```java
@ProblemFactCollectionProperty
public Set<LocalDate> getFullDaysForGroup(StudentGroup group) { ... }
```

**Problems**:
1. "Full" is constraint-dependent (conflicts, holidays, etc.)
2. Becomes stale as solver progresses
3. Requires re-running analysis after major moves

**Verdict**: ⚠️ Possible but fragile — doesn't adapt to solver state

### Approach D: Current Grace Period (Recommended)

**Idea**: Accept that the constraint is an approximation:

```java
// 30-day grace period allows flexibility for hard constraints
private static final int HORIZONTAL_COMPACT_GRACE_DAYS = 30;
```

**Benefits**:
1. **O(n) complexity** — single pass over lessons
2. **Incremental-friendly** — penalty depends only on lesson's own date
3. **Predictable** — linear penalty, easy to reason about
4. **Tunable** — adjust grace period or weight as needed

**Trade-off**: May penalize lessons that *couldn't* be scheduled earlier, but:
- The solver will naturally push lessons earlier when possible
- Hard constraints (holidays, vacations) take precedence
- Medium weight (100) allows other constraints to compete

**Verdict**: ✅ Recommended — best balance of simplicity and effectiveness

---

## 5. Why the Comment Says "Too Complex"

From the source code (lines 449-451):

```java
* ALTERNATIVES CONSIDERED:
* - Check if earlier days are full: Too complex for constraint streaming API
* - Medium constraint with high weight: Less predictable enforcement
```

**"Too complex" means**:

1. **API limitations**: Constraint streams are optimized for local comparisons (same slot, same day). Cross-day comparisons require joins or lookups that scale poorly.

2. **Performance**: A slot-aware constraint would degrade from O(n) to O(n × m) or worse, making the solver significantly slower.

3. **Incremental scoring**: Timefold's incremental score calculation (its main performance feature) doesn't help with cross-day lookups.

4. **Circular dependencies**: "Availability" depends on hard constraints being satisfied, creating a chicken-and-egg problem.

---

## 6. Recommendation

**Keep the current grace period approach.** It's a well-designed approximation that:

1. Allows flexibility for hard constraints (holidays, vacations, stage patterns)
2. Maintains O(n) complexity for constraint evaluation
3. Works with Timefold's incremental scoring
4. Can be tuned (grace days, weight) if needed

### If Tuning is Needed

| Symptom | Adjustment |
|---------|------------|
| Lessons too scattered | Increase weight (100 → 200) |
| Too much conflict with hard constraints | Increase grace period (30 → 45 days) |
| Solver too slow | Decrease weight (100 → 50) |

### When to Revisit

Consider a slot-aware approach only if:
1. **Grace period fails**: Lessons cluster late even when earlier days are empty
2. **Problem size shrinks**: < 1,000 lessons makes O(n × m) acceptable
3. **Timefold adds new API**: Future versions may support cross-day lookups more efficiently

---

## 7. References

- **Timefold Docs**: [Constraint Streams Performance](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance)
- **Source Code**: `SchoolScheduleConstraintProvider.java` lines 438-481
- **Related Constraints**: M13 (Vertically Compact) uses similar approach with gaps
- **Pre-Assignment**: `HybridPhase1aService.java` — CSP approach for hard constraint feasibility
