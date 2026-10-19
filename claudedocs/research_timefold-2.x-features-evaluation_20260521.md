# Timefold Solver 2.x Feature Evaluation for Planner

**Date**: 2026-05-21
**Context**: Project upgraded from Timefold 1.31.0 → 2.0.0 (TASK-305/304/306/307)
**Purpose**: Identify 2.x features that would meaningfully improve our school timetable solver

## Executive Summary

Six features in Timefold 2.x are directly applicable to our project. Three are high-value and ready to implement now; one is blocked by Enterprise licensing; two are Preview features worth monitoring.

| Feature | Value | Effort | Status | Priority |
|---------|-------|--------|--------|----------|
| **Precomputed Joins** | HIGH | Medium | Stable API | Implement now |
| **toConsecutiveSequences** | HIGH | Low | Stable API | Implement now |
| **Diminished Returns Termination** | HIGH | Trivial | Stable API | Implement now |
| **Constraint Profiling** | HIGH | Trivial | Enterprise only | Blocked |
| **Neighborhoods API** | MEDIUM | High | Preview | Monitor |
| **Joiners.overlapping()** | LOW | Medium | Stable API | Low priority |

---

## 1. Precomputed Joins (HIGH VALUE — Implement Now)

### What It Does
Caches static parts of Constraint Streams that only depend on problem facts, not planning variables. The `PrecomputeFactory` builds a stream once and reuses it across moves.

### Our Impact
Our H1 (teacher conflict), H2 (room conflict), and H3 (student group conflict) constraints perform **O(n²) joins** on 8,000+ lessons, checking equality on teacher/room/group. Most of the join results are static — only the pairs where a planning variable changed need re-evaluation.

### Example from Docs
```java
TriConstraintStream<Shift, Shift, Integer> overlappingShifts(PrecomputeFactory precomputeFactory) {
    return precomputeFactory.forEachUnfilteredUniquePair(Shift.class,
            Joiners.overlapping(Shift::getStart, Shift::getEnd))
        .expand((shift1, shift2) -> shift1.getOverlap(shift2));
}

Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
    return constraintFactory.precompute(this::overlappingShifts)
        .filter((shift1, shift2, overlap) ->
            shift1.getEmployee() != null &&
            shift1.getEmployee() == shift2.getEmployee())
        .penalize(HardSoftScore.ONE_HARD, (shift1, shift2, overlap) -> overlap)
        .asConstraint("No overlapping shifts");
}
```

### Application to Our Constraints
- **H1 (Teacher conflict)**: Precompute all lesson pairs that share a timeslot, filter by teacher at evaluation time
- **H2 (Room conflict)**: Same pattern, filter by room
- **H3 (Student group conflict)**: Same pattern, filter by group
- **H5 (Teacher unavailability)**: Precompute teacher-vacation pairs

### Effort Estimate
2-4 hours per constraint. Start with H1 as a proof-of-concept, benchmark before/after.

### Backlog Task
→ TASK-308: Evaluate precomputed joins for H1/H2/H3 constraint performance

---

## 2. toConsecutiveSequences Collector (HIGH VALUE — Implement Now)

### What It Does
Built-in collector that groups items into consecutive sequences based on a comparable index. Automatically detects gaps between sequences.

### Our Impact
Three of our constraints do manual consecutive sequence detection:

| Constraint | Current Approach | toConsecutiveSequences Replacement |
|------------|-----------------|-----------------------------------|
| **H17 (Vertically compact)** | Custom gap detection per student group per day | Group by (group, date), sequence by slotIndex, penalize gaps |
| **H15 (Same-day same-subject consecutive)** | Manual pair joins checking adjacency | Group by (group, date, subject), sequence by slotIndex |
| **S7 (Reward consecutive student lessons)** | Manual consecutive pair detection | Group by (group, date), sequence by slotIndex, reward sequences |

### Example from Docs
```java
Constraint multipleConsecutiveHomeMatches(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Match.class)
            .groupBy(Match::getHomeTeam,
                    ConstraintCollectors.toConsecutiveSequences(Match::getRoundId))
            .flattenLast(SequenceChain::getConsecutiveSequences)
            .filter((team, matches) -> matches.getCount() >= MAX_CONSECUTIVE_MATCHES)
            .penalize(HardSoftScore.ONE_HARD, (team, matches) -> matches.getCount())
            .asConstraint("4 or more consecutive home matches");
}
```

### Effort Estimate
1-2 hours per constraint. The API is clean and well-documented.

### Backlog Task
→ TASK-309: Evaluate toConsecutiveSequences for H17/H15 constraints

---

## 3. Diminished Returns Termination (HIGH VALUE — Implement Now)

### What It Does
Automatically terminates a local search phase when the rate of score improvement drops below a configurable threshold. This is **hardware-independent** and **problem-instance-independent** — unlike `unimprovedSecondsSpentLimit` which behaves differently on fast vs slow machines.

### Our Impact
We currently use `unimprovedSecondsSpentLimit` for LS phases, which requires manual tuning per machine. Diminished returns would:
- Eliminate the need for time-based tuning
- Stop Phase 3 (compactness) automatically when it plateaus
- Stop Phase 5 (room refinement) when room swaps stop helping
- Complement (not replace) our absolute `secondsSpentLimit` safety cap

### Configuration (Trivial — XML only)
```xml
<localSearch>
    <termination>
        <diminishedReturns>
            <slidingWindowDuration>PT2M</slidingWindowDuration>
            <minimumImprovementRatio>0.1</minimumImprovementRatio>
        </diminishedReturns>
    </termination>
</localSearch>
```

Also available via Quarkus properties:
```properties
timefold.solver.termination.diminished-returns.enabled=true
timefold.solver.termination.diminished-returns.sliding-window-duration=PT2M
timefold.solver.termination.diminished-returns.minimum-improvement-ratio=0.1
```

### Effort Estimate
30 minutes — XML config change only. Add to Phase 3 (compactness) and Phase 5 (room refinement).

### Backlog Task
→ TASK-294: Add diminished returns termination to Phase 3 (already exists, now unblocked by 2.x upgrade)

---

## 4. Constraint Stream Profiling (HIGH VALUE — Enterprise Only)

### What It Does
Measures time spent in each constraint stream node, showing which constraints and which operations (JOIN, FILTER, SCORING) are the bottlenecks.

### Example Output
```
| 2: Teacher time efficiency     |   6.19 s | 23.15% |
| 6: Room conflict               |   4.17 s | 15.61% |
| 4: Student group conflict      |   3.76 s | 14.07% |
```

With lifecycle breakdown:
```
| 2: Teacher time efficiency   |  INSERT  |  UPDATE  | RETRACT |
| JOIN                         |  13.12%  |  75.64%  |  11.23% |
| FILTER                       |  23.69%  |  50.45%  |  25.86% |
| SCORING                      |  13.10%  |  16.12%  |  70.78% |
```

### Our Impact
With 30+ constraints, we have no visibility into which ones consume the most evaluation time. Profiling would immediately identify the top 3-5 constraints to optimize with precomputed joins.

### Configuration (Trivial)
```xml
<scoreDirectorFactory>
    <constraintProviderClass>...ConstraintProvider</constraintProviderClass>
    <constraintStreamProfilingEnabled>true</constraintStreamProfilingEnabled>
</scoreDirectorFactory>
```

### Status
**BLOCKED** — Requires Timefold Enterprise Edition. The community edition doesn't include profiling.

### Workaround
Manual benchmarking: comment out individual constraints and measure move evaluation speed impact.

---

## 5. Neighborhoods API (MEDIUM VALUE — Preview, Monitor)

### What It Does
Declarative way to define custom moves, intended to replace the complex MoveSelector XML configuration and custom MoveSelector implementations.

### Our Impact
We have two custom move filters (`CompositeMoveFilter`, `GapPreservingMoveFilter`) and a custom construction phase (`DayClusteredConstructionPhase`). The Neighborhoods API could simplify these, but:

- It's a **Preview feature** (requires `<enablePreviewFeature>NEIGHBORHOODS</enablePreviewFeature>`)
- It "may not yet support all advanced functionalities"
- Our custom phases are complex (day-clustering logic, gap preservation) — unlikely the basic providers cover this

### Recommendation
**Monitor**. When it graduates from Preview, re-evaluate for replacing CompositeMoveFilter and GapPreservingMoveFilter. Don't touch DayClusteredConstructionPhase — it's too specialized.

### Backlog Task
→ TASK-310: Evaluate Neighborhoods API to replace custom move selectors

---

## 6. Joiners.overlapping() (LOW VALUE — Not Applicable)

### What It Does
Built-in joiner for temporal overlap detection between intervals (start/end times).

### Why Low Value for Us
Our lessons are aligned to discrete timeslots (hourly grid), not continuous time intervals. Overlap is determined by same-timeslot comparison, which `Joiners.equal()` handles efficiently. The `overlapping()` joiner is designed for continuous time ranges (shifts, meetings) where partial overlap matters.

We could theoretically use it for stage blackout periods (H4), but our current `Joiners.equal()` on date is simpler and equally correct.

---

## Recommended Implementation Order

```
1. Diminished Returns Termination   (30 min, config only)
2. toConsecutiveSequences           (2-4 hours, H17 first)
3. Precomputed Joins                (4-8 hours, H1 first, benchmark)
4. Constraint Profiling             (blocked, needs Enterprise license)
5. Neighborhoods API                (wait for stable release)
```

## Sources
- https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation (precompute, consecutive)
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/neighborhoods (neighborhoods)
- https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition (profiling)
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview (diminished returns)
- https://timefold.ai/blog/timefold-solver-2.0-release (release blog)
