# Research: Phase 1a Stuck at -3hard - Root Cause Analysis

**Date**: 2026-02-24
**Query**: Why Phase 1a solver gets stuck at -3hard instead of consistently achieving 0hard
**Depth**: Exhaustive
**Context**: Phase 1a (LabBlocks only) sometimes achieves 0hard/-100medium but often gets stuck at -3hard

---

## Executive Summary

**Root Cause**: The solver configuration has a **conflicting hybrid acceptor setup** that doesn't properly exploit Late Acceptance's strength.

**Key Issue**: `acceptedCountLimit=4` combined with Late Acceptance violates Timefold best practices. Late Acceptance should use `acceptedCountLimit=1`, while Tabu Search should use `acceptedCountLimit=1000`. The hybrid is poorly tuned.

**Quick Fix**: Change `acceptedCountLimit` from 4 to 1.

**Recommended Changes**:
1. **Immediate**: `acceptedCountLimit` = 1 (not 4)
2. **Tuning**: `lateAcceptanceSize` = 600-800 (more exploration)
3. **Safety**: `unimprovedSecondsSpentLimit` = 600 (longer plateau tolerance)
4. **Optional**: Add Simulated Annealing hybrid for better local optima escape

---

## Current Configuration Analysis

### Phase 1a Solver Config (`preAssignmentPhase1a-labblocks.xml`)

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>4</acceptedCountLimit>
</forager>
```

### Move Selector Weights
| Move Type | Weight | Purpose |
|-----------|--------|---------|
| Room change | 4.0 | H-M2 room suitability |
| Swap moves | 3.0 | Conflict resolution |
| Timeslot change | 2.0 | General placement |
| Cartesian (ts+room) | 2.0 | Atomic changes |

### Termination
- `secondsSpentLimit`: 600s (10 min)
- `unimprovedSecondsSpentLimit`: 300s (5 min)

---

## Root Cause: acceptedCountLimit Conflict

### Timefold Documentation (Official)

> **Late Acceptance should use a low `acceptedCountLimit`.**
> "A Tabu Search acceptor should be combined with a high `acceptedCountLimit`, such as `1000`."

The current `acceptedCountLimit=4` is:
- **Too high for Late Acceptance** (should be 1)
- **Too low for Tabu Search** (should be ~1000)

This creates a **greedy first-hit behavior** that accepts moves before evaluating alternatives, causing the solver to:
1. Accept a move that improves from -5hard to -4hard
2. Not explore the alternative path that would reach -3hard → -2hard → -1hard → 0hard
3. Get trapped in a local optimum

### Why -3hard Specifically?

The -3hard plateau likely represents a **score trap** where:
- Any single-entity move creates a new violation while fixing another
- Swap moves can resolve this, but need the right conditions
- The greedy acceptor doesn't give swap moves enough chances

---

## Detailed Recommendations

### 1. Fix acceptedCountLimit (CRITICAL)

**Current**: 4
**Recommended**: 1

```xml
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

**Rationale**: Per Timefold docs, Late Acceptance is designed to work with `acceptedCountLimit=1`. This forces the solver to evaluate more moves and accept worsening moves based on the late acceptance window, not greedy selection.

**Expected Impact**: High - This alone should significantly improve consistency.

---

### 2. Increase lateAcceptanceSize (RECOMMENDED)

**Current**: 400
**Recommended**: 600-800

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>600</lateAcceptanceSize>
</acceptor>
```

**Rationale**: A larger window gives the solver more "runway" to explore worsening moves before the late score catches up. With ~232 LabBlocks and complex constraints, 400 steps might not be enough exploration depth.

**Expected Impact**: Medium - Helps escape deeper local optima.

---

### 3. Increase unimprovedSecondsSpentLimit (SAFETY NET)

**Current**: 300s (5 min)
**Recommended**: 600s (10 min)

```xml
<termination>
  <unimprovedSecondsSpentLimit>600</unimprovedSecondsSpentLimit>
</termination>
```

**Rationale**: If the solver is on a plateau, 5 minutes might not be enough to find the escape path. 10 minutes gives more time without risking infinite loops (the hard cap of 600s still applies).

**Expected Impact**: Low - Safety net, not a fix.

---

### 4. Add Simulated Annealing Hybrid (OPTIONAL BUT POWERFUL)

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <simulatedAnnealingStartingTemperature>2hard/0medium/0soft</simulatedAnnealingStartingTemperature>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

**Rationale**: Simulated Annealing's temperature-based acceptance gives a more gradual transition from exploration to exploitation. Combined with Late Acceptance, it provides multiple escape mechanisms.

**Starting Temperature**: `2hard` means the solver will initially accept moves that worsen by up to 2 hard points, then gradually become more selective.

**Expected Impact**: Medium-High - Different escape mechanism.

---

### 5. Add Pillar Moves (OPTIONAL)

For multi-entity conflict resolution:

```xml
<pillarSwapMoveSelector>
  <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
  <pillarSelector>
    <entitySelector>
      <entityClass>com.school.planner.domain.LabBlock</entityClass>
    </entitySelector>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>3</maximumSubPillarSize>
  </pillarSelector>
</pillarSwapMoveSelector>
```

**Rationale**: When multiple LabBlocks from the same course need to move together to resolve a conflict, pillar moves handle this atomically.

**Expected Impact**: Low-Medium - Specific use case.

---

### 6. Use Entity Tabu Ratio Instead of Fixed Size (OPTIONAL)

**Current**: `entityTabuSize=7`
**Alternative**: `entityTabuRatio=0.02` (2% of entities)

```xml
<acceptor>
  <entityTabuRatio>0.02</entityTabuRatio>
  <lateAcceptanceSize>600</lateAcceptanceSize>
</acceptor>
```

**Rationale**: With 232 entities, 2% = ~5 entities tabu. This scales automatically if entity count changes.

**Expected Impact**: Low - Better scaling, not a fix.

---

## Proposed Configuration

### Option A: Minimal Fix (Recommended First)

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>  <!-- Changed from 4 -->
</forager>
```

### Option B: Enhanced Exploration

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <lateAcceptanceSize>600</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

### Option C: SA + LA Hybrid

```xml
<acceptor>
  <entityTabuSize>7</entityTabuSize>
  <simulatedAnnealingStartingTemperature>2hard/0medium/0soft</simulatedAnnealingStartingTemperature>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

---

## Constraint Analysis: What Causes -3hard?

Based on the constraint structure in `BaseConstraintProvider.java`:

### Most Likely Violations

| Constraint | Likelihood | Reason |
|------------|------------|--------|
| **H-M2.2** (Room suitability) | High | Only 1 meccanica room for multiple courses |
| **H2.3** (Room conflict B-B) | Medium | Multi-hour blocks compete for same slots |
| **H1.3** (Teacher conflict B-B) | Medium | Same teacher, different groups |
| **H9** (Block valid placement) | Low | Blocks don't fit in available slots |

### Why Room Suitability is Critical

- **R15** room serves courses 153, 171, 155, 172, 194
- Total demand: 550 hours, capacity: 1137 slots
- **Should fit** but solver may not find optimal arrangement

### H17→M17 Was Correct

The downgrade from Hard to Medium for H17 (Lab Recurring Pattern) was the right call:
- Shared room constraints make weekly patterns mathematically infeasible
- M17 still guides the solver toward good patterns
- Hard constraints remain for actual feasibility (room suitability, conflicts)

---

## Verification Steps

After applying fixes:

1. **Run Phase 1a 3x** with same timeout
2. **Check final score** - should consistently be 0hard
3. **Check time to 0hard** - should be faster
4. **Analyze violations** if still not 0hard:
   ```bash
   ./scripts/api/api.sh analysis
   ```

---

## Benchmarking Plan

| Config | acceptedCountLimit | lateAcceptanceSize | SA Temp |
|--------|-------------------|-------------------|---------|
| Current | 4 | 400 | - |
| Fix A | 1 | 400 | - |
| Fix B | 1 | 600 | - |
| Fix C | 1 | 400 | 2hard |

Run each 3x with 10-minute timeout, measure:
- % runs that achieve 0hard
- Average time to 0hard
- Final medium score

---

## Sources

1. [Timefold Solver: Local Search](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search)
2. [Timefold Solver: Benchmarking and Tweaking](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking)
3. [Timefold GitHub Discussion #951: Escaping Local Optima](https://github.com/TimefoldAI/timefold-solver/discussions/951)
4. [Diversified Late Acceptance Search](https://patatconference.org/patat2008/proceedings/Bykov-HC2a.pdf)
5. Project research: `claudedocs/research_phase1_solver_tuning.md`

---

## Action Items

1. **Immediate**: Change `acceptedCountLimit` from 4 to 1 in `preAssignmentPhase1a-labblocks.xml`
2. **Test**: Run Phase 1a 3x to verify consistency
3. **If still stuck**: Increase `lateAcceptanceSize` to 600
4. **If still stuck**: Add Simulated Annealing hybrid
5. **Document**: Update this research with results
