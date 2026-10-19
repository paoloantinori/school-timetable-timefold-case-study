---
id: TASK-294
title: Add diminished returns termination to Phase 3
status: Done
assignee: []
created_date: '2026-03-22 05:39'
updated_date: '2026-05-21 20:56'
labels:
  - claude-planned
  - ls-optimization
  - solver
milestone: solver-optimization
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `<diminishedReturnsSpentLimit>` termination criterion to Phase 3 (Compactness) in `solverConfig.xml` to auto-stop when the solver plateaus.

## Why This Matters

Phase 3 currently has a fixed `unimprovedSecondsSpentLimit` of 1800s (30 minutes). This means the solver keeps running for 30 minutes even if it hasn't improved in 28 minutes — wasting time on a flatlined search.

`diminishedReturnsSpentLimit` is a newer Timefold termination criterion that detects when the improvement rate drops below a threshold. It's more sophisticated than `unimprovedSecondsSpentLimit` because:
- It measures the RATE of improvement, not just whether any improvement occurred
- A solver that improves by 1 hard every 5 minutes is still making progress (not truly "unimproved")
- A solver that improved by 50 hard in the first minute but only 1 hard in the last 29 minutes is plateauing — diminished returns catches this

Combined with the existing `unimprovedSecondsSpentLimit`, this gives a two-level termination: stop if EITHER (a) no improvement at all for 30 min, OR (b) improvement rate drops below threshold.

## Implementation

Add to Phase 3 `<termination>` in `solverConfig.xml`:

```xml
<termination>
  <secondsSpentLimit>3600</secondsSpentLimit>
  <unimprovedSecondsSpentLimit>1800</unimprovedSecondsSpentLimit>
  <diminishedReturnsSpentLimit>300</diminishedReturnsSpentLimit>
</termination>
```

The `diminishedReturnsSpentLimit` value needs tuning. Start with 300s (5 minutes) and benchmark.

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — New Timefold features, diminished returns termination
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 3 termination in solverConfig.xml includes diminishedReturnsSpentLimit
- [ ] #2 Value tuned via benchmark (start with 300s)
- [ ] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [ ] #4 Rebuild succeeds and solver terminates earlier than before when plateau is reached
- [ ] #5 Benchmark: compare total solve time and final score with/without diminished returns
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by TASK-312. Diminished returns termination added to all 3 LS phases (3, 4, 6) in solverConfig.xml.
<!-- SECTION:FINAL_SUMMARY:END -->
