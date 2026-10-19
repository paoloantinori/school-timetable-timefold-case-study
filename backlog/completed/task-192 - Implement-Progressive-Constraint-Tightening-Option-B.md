---
id: TASK-192
title: Implement Progressive Constraint Tightening (Option B)
status: Done
assignee: []
created_date: '2026-03-07 07:13'
updated_date: '2026-07-14 05:31'
labels:
  - solver
  - constraints
  - compaction
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement a phased approach where compaction constraints start relaxed and then progressively tighten for better solver performance.

**Phases:**
1. **Phases 1-5**: Keep M12/M13 as medium (weight 100-500)
2. **Phase 6 (new)**: Add Simulated Annealing focused on compaction
3. **Phase 7 (new)**: Increase weights or promote to hard for final polish

**Rationale:**
Research shows that starting with relaxed constraints and tightening progressively can work better than full hard from the start. This matches the user's experience that "promoting to HARD from the beginning made progress too slow."

**Implementation:**
```xml
<!-- Phase 6: Simulated Annealing for compaction -->
<localSearch>
  <unionMoveSelector>
    <changeMoveSelector/>
    <swapMoveSelector/>
  </unionMoveSelector>
  <acceptor>
    <simulatedAnnealingStartingTemperature>0hard/500medium</simulatedAnnealingStartingTemperature>
  </acceptor>
  <forager>
    <acceptedCountLimit>1</acceptedCountLimit>
  </forager>
  <termination>
    <unimprovedSecondsSpentLimit>1800</unimprovedSecondsSpentLimit>
  </termination>
</localSearch>

<!-- Phase 7: Final polish with increased weights -->
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
    <unimprovedSecondsSpentLimit>600</unimprovedSecondsSpentLimit>
  </termination>
</localSearch>
```

**Acceptance Criteria:**
- [ ] Two new phases added (Phase 6 and Phase 7)
- [ ] Phase 6 uses Simulated Annealing for compaction
- [ ] Phase 7 uses Late Acceptance for final polish
- [ ] Benchmark shows better compaction scores than current approach
- [ ] 0 hard still achievable
- [ ] Update claudedocs/solver-configurations.md

**References:**
- claudedocs/research-timefold-compaction-constraint-tuning-2026-03-07.md
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED by TASK-331 (and TASK-325). The progressive-relaxation-then-tighten idea is re-scoped by TASK-331 (two-stage solve: relaxed compaction then full hard) under the standardized protocol. The SA/LA acceptor angles from this task's Phase 6/7 design are covered by TASK-325 (acceptor sweep).
<!-- SECTION:FINAL_SUMMARY:END -->
