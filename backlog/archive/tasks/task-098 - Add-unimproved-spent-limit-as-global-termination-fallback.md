---
id: task-098
title: Add unimproved-spent-limit as global termination fallback
status: To Do
assignee: []
created_date: '2026-02-23 17:26'
updated_date: '2026-07-27 04:48'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
dependencies: []
priority: low
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The planner uses per-phase `unimprovedSecondsSpentLimit` (3600s for Phase 3-4, 600s for Phase 5) and a global `secondsSpentLimit` of 28800s (8 hours). However, there's no global `unimprovedSecondsSpentLimit` — meaning if the solver plateaus across all phases, it will run for the full 8 hours before stopping.

**Current termination config**:
```xml
<solver>
  <termination>
    <secondsSpentLimit>28800</secondsSpentLimit>  <!-- 8h hard cap -->
  </termination>
  <!-- Per-phase: 3600s, 3600s, 600s unimproved limits -->
</solver>
```

**Proposed addition**:
```xml
<solver>
  <termination>
    <secondsSpentLimit>28800</secondsSpentLimit>
    <unimprovedSecondsSpentLimit>7200</unimprovedSecondsSpentLimit>  <!-- 2h no improvement = stop -->
  </termination>
</solver>
```

**Rationale**: If the solver hasn't improved the score in 2 hours across any phase, continuing to run is unlikely to produce meaningful gains. This saves compute resources when the solver has converged.

**Note**: The per-phase limits already handle most plateau scenarios. The global `unimprovedSecondsSpentLimit` is a safety net for edge cases where phases transition but still don't improve the overall score.

**Risk**: Low — this only adds a termination condition. The solver can still run for up to 8 hours if it's making progress. The 2-hour value should be validated: check benchmark logs to see how long plateaus typically last.

**Propagation**: `solverConfig.xml` is architecture-specific — add to all 3 branches.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Global unimprovedSecondsSpentLimit added to solverConfig.xml
- [ ] #2 Value chosen based on benchmark log analysis of typical plateau durations
- [ ] #3 No change to solver behavior during normal operation (only triggers on extended plateaus)
- [ ] #4 Change propagated to all 3 active branches
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SUPERATO/IMPLEMENTATO (2026-07-27): unimprovedSecondsSpentLimit e' stato aggiunto alle fasi LS da TASK-381 L3 (600s su fase 2; la vecchia fase 3 e' stata poi rimossa da TASK-420). Il fallback globale non serve piu' come task separato. Archiviato.
<!-- SECTION:NOTES:END -->
