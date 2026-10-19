# Pillar Moves Experiment (2026-03-17)

## Overview

Experimental branch to test whether **pillar moves** can preserve consecutive student lesson patterns (S7) while resolving teacher conflicts (H1), avoiding the "conflict tension" where fixing H1 violations breaks S7 patterns.

## Branch

- **Name**: `feature/pillar-moves-experiment`
- **Based on**: `noroom` (single-entity architecture, LabBlocks deprecated)

## Problem Statement

### H1 vs S7 Tension

| Constraint | Purpose | Current Risk |
|------------|---------|--------------|
| **H1** (Hard) | Prevents teacher conflicts | Forces breaking patterns |
| **S7** (Soft) | Rewards consecutive student lessons | Patterns get fragmented |

**Current behavior**: Change moves shift lessons individually → break consecutive patterns → fix conflicts → lose S7 score.

**Hypothesis**: Pillar moves shift ALL lessons at a timeslot together → preserve consecutive patterns → resolve H1 without breaking S7.

## Changes Made

### 1. Solver Configuration (`solverConfig.xml`)

**Phase 3 (Compactness LS)** - Added pillar move selectors:

```xml
<unionMoveSelector>
  <!-- Existing: Individual change moves (5.0 weight) -->
  <changeMoveSelector>
    <fixedProbabilityWeight>5.0</fixedProbabilityWeight>
    <entitySelector>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
    </entitySelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>

  <!-- NEW: Pillar change (2.0 weight) -->
  <pillarChangeMoveSelector>
    <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
    <pillarSelector>
      <entitySelector>
        <entityClass>com.school.planner.domain.Lesson</entityClass>
      </entitySelector>
    </pillarSelector>
    <valueSelector variableName="timeslot"/>
  </pillarChangeMoveSelector>

  <!-- NEW: Pillar swap (1.0 weight) -->
  <pillarSwapMoveSelector>
    <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
    <pillarSelector>
      <entitySelector>
        <entityClass>com.school.planner.domain.Lesson</entityClass>
      </entitySelector>
    </pillarSelector>
    <variableNameIncludes>
      <variableNameInclude>timeslot</variableNameInclude>
    </variableNameIncludes>
  </pillarSwapMoveSelector>
</unionMoveSelector>
```

### 2. Benchmark Script

Created `scripts/benchmark/benchmark-pillar-moves.sh` for A/B testing.

## Architecture Compatibility

| Aspect | Status | Notes |
|--------|--------|-------|
| Single Entity Type | ✅ | Only `Lesson` entities |
| Planning Variables | ✅ | `(Timeslot, Room)` structure |
| Pillar Move Scope | ✅ | Timeslot-only (Phase 3) |
| Room Assignment | ✅ | Phases 4-5 handle rooms separately |

**Why pillar moves NOW work:**
- LabBlocks deprecated (no heterogeneous planning values)
- Single entity type = uniform pillar structure
- Timeslot pillar = all lessons at a specific datetime

## Expected Behavior

### Pillar Change Move
```
Before: Teacher A has 3 lessons at Mon 8:00, 9:00, 10:00
        Teacher B has 2 lessons at Mon 11:00, 12:00
After:  ALL 5 lessons shift together to Mon 13:00, 14:00, 15:00
Result: Consecutive patterns preserved within the pillar
```

### Pillar Swap Move
```
Before: Mon 8:00 has [Lesson1, Lesson2, Lesson3]
        Mon 13:00 has [Lesson4, Lesson5, Lesson6]
After:  Mon 8:00 has [Lesson4, Lesson5, Lesson6]
        Mon 13:00 has [Lesson1, Lesson2, Lesson3]
Result: Entire schedules exchanged, patterns intact
```

## Validation Checklist

- [x] XML validates against Timefold XSD
- [x] App builds successfully
- [x] App starts without errors
- [ ] Run benchmark to collect metrics
- [ ] Compare H1/S7 trade-off vs baseline
- [ ] Measure solve time impact

## Benchmark Plan

1. **Baseline** (noroom branch without pillar moves)
   - Run 2x 60-second solves
   - Record H1 violations, S7 score, total score

2. **Pillar Moves** (this branch)
   - Run 2x 60-second solves
   - Record H1 violations, S7 score, total score

3. **Analysis**
   - Compare S7 score at equivalent H1 levels
   - Measure solve time overhead (pillar moves are expensive)
   - Assess if pillar moves justify the cost

## Risks & Caveats

1. **Performance**: Pillar moves are O(n²) vs O(n) for change moves
2. **Room constraints**: Pillar moves on timeslot don't affect rooms (Phase 4-5 handle this)
3. **Solver complexity**: More move types = longer acceptance decisions

## References

- Timefold Documentation: [Pillar-based move selectors](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference)
- Original suggestion: Gemini (AI recommendation)
- Branch family: Single-entity (noroom baseline)

## Next Steps

1. Run benchmark: `./scripts/benchmark/benchmark-pillar-moves.sh`
2. Analyze results vs baseline
3. If positive: Merge to noroom or create long-running benchmark
4. If negative: Document findings and abandon approach
