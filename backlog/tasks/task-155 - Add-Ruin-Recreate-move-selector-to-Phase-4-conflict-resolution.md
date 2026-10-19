---
id: TASK-155
title: Add Ruin & Recreate move selector to Phase 4 (conflict resolution)
status: Done
assignee: []
created_date: '2026-03-03 20:47'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - performance
  - solver
milestone: Solver Performance
dependencies: []
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - 'src/main/resources/solverConfig.xml:137-216'
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
  - >-
    https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking
priority: medium
ordinal: 78000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `ruinRecreateMoveSelector` to Phase 4 (Conflict Resolution) of solverConfig.xml to help escape local optima that fine-grained change/swap moves cannot break through.

Ruin & Recreate "ruins" (unassigns) a subset of entities and "recreates" (reassigns) them via construction heuristic. This is particularly useful in our 5-phase solver where Phase 4 can get stuck cycling through swap moves without improvement.

**Configuration to add inside Phase 4's `<unionMoveSelector>`:**

```xml
<!-- Ruin & Recreate: coarse-grained move for escaping local optima -->
<ruinRecreateMoveSelector>
    <fixedProbabilityWeight>0.01</fixedProbabilityWeight>
    <entitySelector>
        <entityClass>com.school.planner.domain.Lesson</entityClass>
    </entitySelector>
    <minimumRuinedCount>5</minimumRuinedCount>
    <maximumRuinedCount>20</maximumRuinedCount>
</ruinRecreateMoveSelector>
```

**Considerations:**
- Each ruin-recreate move evaluates score multiple times internally (more expensive per-move than change/swap)
- Weight of 0.01 means ~1 ruin per 100 fine-grained moves — must benchmark to find optimal value
- May need a separate ruin-recreate for LabBlock entities
- Must validate solver XML after modification: `./scripts/validate-solver-xml.sh`

**This is XML-only — no Java code changes required.**
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ruinRecreateMoveSelector added to Phase 4 unionMoveSelector in solverConfig.xml
- [ ] #2 Solver XML validates against Timefold XSD
- [ ] #3 All 193 tests pass
- [ ] #4 Benchmark comparison: score improvement vs baseline at 5-min and 10-min marks
- [ ] #5 Pre-assignment still achieves 100% pinning (unaffected by LS config)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Duplicate of TASK-178. Ruin & Recreate already implemented in solverConfig.xml Phase 4 (lines 222-230). Feature was completed as part of TASK-178 work.
<!-- SECTION:FINAL_SUMMARY:END -->
