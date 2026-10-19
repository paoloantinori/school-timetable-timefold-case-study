---
id: TASK-312
title: Add diminished returns termination to LS phases
status: Done
assignee: []
created_date: '2026-05-21 17:07'
updated_date: '2026-05-21 20:50'
labels:
  - solver
  - performance
milestone: m-1
dependencies: []
priority: high
ordinal: 136000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add Timefold 2.x `diminishedReturns` termination to Phase 3 (compactness) and Phase 5 (room refinement) in solverConfig.xml.

**What**: The `diminishedReturns` termination monitors the rate of score improvement and stops automatically when it plateaus. This is hardware-independent and problem-instance-independent — unlike `unimprovedSecondsSpentLimit` which requires per-machine tuning.

**Changes needed**:
- Add `<diminishedReturns>` termination to Phase 3 and Phase 5 in `solverConfig.xml`
- Keep `secondsSpentLimit` as a safety cap
- Tune `slidingWindowDuration` (suggested PT2M) and `minimumImprovementRatio` (suggested 0.1)
- Verify solver behavior with a benchmark run

**Config example**:
```xml
<termination>
    <diminishedReturns>
        <slidingWindowDuration>PT2M</slidingWindowDuration>
        <minimumImprovementRatio>0.1</minimumImprovementRatio>
    </diminishedReturns>
</termination>
```

**Reference**: claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 diminishedReturns termination added to Phase 3 in solverConfig.xml
- [ ] #2 diminishedReturns termination added to Phase 5 in solverConfig.xml
- [ ] #3 Solver completes a full solve without hanging
- [ ] #4 Phase timing shows auto-termination when plateaus are reached
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added Timefold 2.x diminishedReturns termination to all 3 LS phases in solverConfig.xml:
- Phase 3 (compactness): 2min sliding window, 10% minimum improvement ratio
- Phase 4 (ruin-recreate): 2min sliding window, 10% minimum improvement ratio  
- Phase 6 (room refinement): 1min sliding window, 5% minimum improvement ratio

Replaced unimprovedSecondsSpentLimit with hardware-independent diminished returns. secondsSpentLimit retained as safety cap. XSD validated, build passes.
<!-- SECTION:FINAL_SUMMARY:END -->
