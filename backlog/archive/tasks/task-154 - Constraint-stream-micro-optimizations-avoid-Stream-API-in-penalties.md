---
id: TASK-154
title: 'Constraint stream micro-optimizations: avoid Stream API in penalties'
status: To Do
assignee: []
created_date: '2026-03-03 20:46'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - performance
  - solver
milestone: Solver Performance
dependencies: []
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
  - 'src/main/java/com/school/planner/domain/LabBlock.java:271-290'
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
  - >-
    https://blog.dotsandlines.ai/how-we-made-our-clients-timefold-solver-over-17x-faster-074524485b39
priority: medium
ordinal: 64000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Replace Java Stream API usage in constraint penalty functions with simple array/loop iteration for better performance. Timefold docs explicitly recommend: "Avoid Stream APIs and explicit iteration in constraints."

**Targets in BaseConstraintProvider.java:**

1. **B-B penalty functions** (H1.3, H2.3, H3.3) — all use:
```java
.penalize(..., (a, b) -> (int) a.getOccupiedTimeslots().stream()
        .filter(b::occupiesTimeslot).count())
```
Replace with for-loop:
```java
.penalize(..., (a, b) -> {
    int count = 0;
    for (Timeslot ts : a.getOccupiedTimeslots()) {
        if (b.occupiesTimeslot(ts)) count++;
    }
    return count;
})
```

2. **H4.2 Stage blackout (B)** — uses stream in both filter and penalty functions to check occupied timeslots against stage patterns.

3. **`occupiesTimeslot()` method** in LabBlock — uses `occupiedTimeslots.stream().anyMatch()`. Replace with indexed loop.

4. **H2.1 redundant filter** — `.filter((a, b) -> a.room != null)` after `Joiners.equal(l -> l.room)` could be a pre-filter on the forEach instead.

These are small changes individually but compound across 30+ constraints evaluated millions of times.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All Stream API usage in penalty functions replaced with for-loops
- [ ] #2 LabBlock.occupiesTimeslot() uses indexed loop instead of stream
- [ ] #3 H4.2 stage blackout filter/penalty uses loops
- [ ] #4 All 193 tests pass with identical scores
- [ ] #5 Benchmark shows measurable move evaluation speed improvement
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): l'entita' LabBlock e i pattern B-B bersaglio del task sono stati rimossi interamente (TASK-228/267).
<!-- SECTION:NOTES:END -->
