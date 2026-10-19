---
id: TASK-191
title: 'Hybrid approach: Increase M12 weight + Add compaction phase'
status: Done
assignee: []
created_date: '2026-03-07 07:06'
updated_date: '2026-03-09 19:50'
labels:
  - solver
  - constraints
  - compaction
  - claude-planned
dependencies: []
priority: high
ordinal: 91000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Combine both approaches for maximum effectiveness: increase M12 constraint weight AND add a dedicated compaction phase.

**Rationale:**
- Option 2 (weight increase) alone: Stronger signal during all phases, but may not be enough focused optimization
- Option 3 (compaction phase) alone: Focused optimization, but weaker signal during earlier phases
- Hybrid: Best of both - strong signal throughout + focused late-stage optimization

**Implementation Plan:**

**Part 1: Increase M12 Weight**
```java
// In SchoolScheduleConstraintProvider.java
.penalize(HardMediumSoftScore.of(0, 100, 0),  // Was ONE_MEDIUM
    lesson -> (int) DAYS.between(graceEnd, lesson.timeslot.getDate()))
```

**Part 2: Add Compaction Phase**
```xml
<!-- Add after existing LS phases -->
<localSearch>
  <unionMoveSelector>
    <changeMoveSelector/>
    <swapMoveSelector/>
  </unionMoveSelector>
  <acceptor>
    <lateAcceptanceSize>400</lateAcceptanceSize>
  </acceptor>
  <forager>
    <acceptedCountLimit>1</acceptedCountLimit>
  </forager>
  <termination>
    <unimprovedSecondsSpentLimit>1800</unimprovedSecondsSpentLimit>
  </termination>
</localSearch>
```

**Acceptance Criteria:**
- [ ] M12 weight increased to 100 (or 500)
- [ ] Compaction phase added with Late Acceptance or Simulated Annealing
- [ ] Benchmark comparison shows M12+M13 penalty reduced by at least 60%
- [ ] Total solve time not increased by more than 30%
- [ ] 0 hard still achievable
- [ ] Update claudedocs/solver-configurations.md

**Benchmark Plan:**
1. Run on same dataset (cartesian-block-entity)
2. Compare: time to 0 hard, final M12/M13 scores, total time
3. Target: M12 reduction from -554k to < -200k

**References:**
- claudedocs/research-timefold-compaction-constraint-tuning-2026-03-07.md
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented hybrid approach for M12/M13 compaction optimization:

**Changes:**
1. M12 weight increased from 1 to 100 (line 466 in ConstraintProvider)
2. Added Phase 6 (Final Compaction) to solverConfig.xml:
   - 30min unimproved termination
   - Timeslot-only moves (changes + swaps for Lesson/LabBlock)
   - Simulated Annealing with 1000medium starting temperature
   - Focus on horizontal/vertical compactness after room optimization

**Rationale:**
- M12 accounts for 76% of medium penalty (-554k out of -727k)
- Weight of 100 is proportionate to impact while still 5x weaker than M13's 500
- SA allows escaping deep local optima during compaction
- Timeslot-only moves focus search budget on temporal positioning

**Commit:** a14dec0
<!-- SECTION:FINAL_SUMMARY:END -->
