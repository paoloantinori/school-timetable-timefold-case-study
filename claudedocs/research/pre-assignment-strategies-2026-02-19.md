# Research: Pre-Assignment Strategies for Timefold Solver

**Date**: 2026-02-19
**Depth**: Exhaustive
**Status**: Complete

## Executive Summary

Your intuition is correct: the current pre-assignment approach has fundamental limitations. The good news is that Timefold supports multiple alternative strategies, and a **two-phase solver approach** is both reasonable and built-in. The key insight is that the current pre-assigners use a **partial constraint check** (only H1-H3) rather than the solver's full constraint evaluation.

### Key Findings

1. **Two independent solver runs ARE supported** - Timefold explicitly documents multi-stage planning with separate SolverManagers
2. **Warm start is built-in** - `solve(solution)` accepts partially/fully initialized solutions
3. **Custom phases exist** - `PhaseCommand` allows custom initialization before CH/LS
4. **Root cause identified** - Pre-assigners check only 3 of 16+ hard constraints

---

## Part 1: Analysis of Current Implementation

### What the Current Pre-Assigners Do

The current pre-assignment pipeline (`AcconciaturaPreAssigner`, `LabBlockPreAssigner`, `ProfessionalizzantiPreAssigner`) follows a **deterministic sequential approach**:

1. Query unassigned entities for each rotation/group
2. Compute valid dates (excluding holidays, stage blackouts)
3. For each date, try to assign entity
4. **Validate before pinning** using `PreAssignmentValidator`
5. If validation passes, pin the entity

### The Validation Gap (Root Cause)

The `PreAssignmentValidator` only checks:
- **H1**: Teacher conflict (same teacher, same timeslot)
- **H2**: Room conflict (same room, same timeslot)
- **H3**: Student group conflict (same group, same timeslot)

It does **NOT** check:
- **H4**: Stage blackout (fundamentals on stage days)
- **H5**: Teacher unavailability (vacations, preferred days)
- **H6**: School holidays (redundant with date filtering, but still)
- **H7**: Max teacher daily hours
- **H8**: Min student group daily lessons
- **H9**: Lab entity valid placement
- **H10**: Preparazione before stage
- **H11**: Max student group daily hours
- **H14**: Student group start date
- **H15**: Same-day same-subject consecutive
- **H16**: Consecutive same-subject same teacher-room
- **H-M2**: Room suitability (lab tags)

This means **pinned entities can violate unchecked constraints**, producing hard violations that the solver cannot fix (since pinned entities are immutable).

### Why It Seems "Easier" But Isn't

Your intuition about the early stage being simpler is valid for a **human planner** who can:
- See all constraints at once
- Backtrack when stuck
- Choose which constraint to relax

But the current pre-assigners are **greedy one-pass algorithms**:
- They don't backtrack
- They skip entities that fail validation
- They don't optimize, they just place

---

## Part 2: Alternative Strategies

### Strategy A: Two Independent Solver Runs

**Concept**: Run solver twice - first to place "must-have" entities, second to complete the schedule.

```
┌─────────────────┐     ┌─────────────────┐
│   Solver #1     │     │   Solver #2     │
│ Pre-assignment  │────▶│   Full solve    │
│ (subset only)   │     │ (pinned input)  │
└─────────────────┘     └─────────────────┘
```

**How it works**:
1. **First run**: Solver config with only the entities to pre-assign
   - Run for short time (just enough to find valid placements)
   - All constraints active (H1-H16)
   - Goal: Find ANY valid placement, not optimal

2. **Second run**: Take output from first, pin those entities
   - Pass the solution with pre-assigned entities
   - Run full optimization

**Timefold Support**:
```java
// Official documentation pattern
@Named("preAssignmentSolver")
SolverManager<SchoolSchedule, String> preAssignmentSolverManager;

@Named("mainSolver")
SolverManager<SchoolSchedule, String> mainSolverManager;

// Run 1: Pre-assignment
SchoolSchedule partialProblem = createPreAssignmentProblem();
SchoolSchedule preAssigned = preAssignmentSolverManager.solve(partialProblem);

// Pin the pre-assigned entities
pinEntities(preAssigned);

// Run 2: Full solve with pinned input
SchoolSchedule finalSolution = mainSolverManager.solve(preAssigned);
```

**Pros**:
- Uses solver's full constraint evaluation (all H1-H16)
- Can find valid placements humans might miss
- No custom validation code to maintain

**Cons**:
- Two solver runs = more time
- Need to extract subset of entities for first run
- Solver may not finish with 0 hard score either

**Verdict**: ✅ **REASONABLE AND SUPPORTED**

---

### Strategy B: Custom PhaseCommand

**Concept**: Replace pre-assigners with a custom solver phase that uses the solver's own score calculation.

```xml
<solver>
  <customPhase>
    <customPhaseCommandClass>com.school.planner.solver.PreAssignmentPhase</customPhaseCommandClass>
  </customPhase>
  <constructionHeuristic>...</constructionHeuristic>
  <localSearch>...</localSearch>
</solver>
```

```java
public class PreAssignmentPhase implements PhaseCommand<SchoolSchedule> {
    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector,
                                       BooleanSupplier isPhaseTerminated) {
        // Use scoreDirector to make moves
        // Score calculation is the SAME as main solver
        // Can check score before committing
    }
}
```

**Pros**:
- Single solver run
- Uses full constraint set
- Can check score impact of each move

**Cons**:
- More complex to implement
- Need to work with ScoreDirector API
- Still need to implement placement logic

**Verdict**: ⚠️ **POSSIBLE BUT MORE WORK**

---

### Strategy C: Enhanced Sequential Greedy with Backtracking

**Concept**: Keep the current approach but add:
1. **Full constraint checking** using the constraint provider
2. **Backtracking** when stuck
3. **Entity ordering** by difficulty

```java
public class EnhancedPreAssigner {
    // Use the actual constraint provider for validation
    @Inject ConstraintProvider constraintProvider;

    // Try placement, check score impact
    boolean tryPlacement(Entity e, Timeslot ts, Room r) {
        // Temporarily assign
        e.setTimeslot(ts);
        e.setRoom(r);

        // Check full score impact
        Score score = scoreDirector.calculateScore();

        // If hard score degraded, undo
        if (score.hardScore() < 0) {
            e.setTimeslot(null);
            e.setRoom(null);
            return false;
        }
        return true;
    }
}
```

**Pros**:
- Human-like sequential approach
- Uses full constraint set
- Can implement backtracking

**Cons**:
- Need to integrate with score calculation
- Still need to implement backtracking logic
- May not find solutions if greedy choices lead to dead ends

**Verdict**: ⚠️ **POSSIBLE BUT REINVENTS THE WHEEL**

---

### Strategy D: Solver-Based Pre-Assignment with Custom Move Selector

**Concept**: Use the solver's construction heuristic but customize move selection to prioritize "must-have" entities.

```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector>
      <entityClass>com.school.planner.domain.LabBlock</entityClass>
      <sorterManner>DECREASING_DIFFICULTY</sorterManner>
    </entitySelector>
    <changeMoveSelector>
      <valueSelector variableName="startTimeslot"/>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </queuedEntityPlacer>
</constructionHeuristic>
```

Run the solver with:
- Only the pre-assignment entities in the problem
- Strong preference for 0 hard score

**Pros**:
- Uses all solver machinery
- Full constraint checking
- Optimal placement within search space

**Cons**:
- Still need to run solver
- May not guarantee 0 hard score

**Verdict**: ✅ **GOOD APPROACH**

---

### Strategy E: Relax and Repair

**Concept**: If 0 hard score is impossible, accept it and let the solver repair.

1. Pre-assign with best-effort (current approach)
2. **Don't pin** - let solver reposition if needed
3. Add **soft constraint** to reward keeping pre-assigned positions

```java
// New soft constraint: "Respect pre-assignment preference"
Constraint respectPreAssignment(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(l -> l.preferredTimeslot != null)
        .reward(SoftScore.ONE_SOFT,
                l -> l.timeslot.equals(l.preferredTimeslot) ? 1 : 0)
        .asConstraint("Respect pre-assignment");
}
```

**Pros**:
- Simple to implement
- Solver has full freedom
- Pre-assignment becomes a "hint" not a "hard requirement"

**Cons**:
- Solver may completely ignore pre-assignment
- Loses the search space reduction benefit

**Verdict**: ⚠️ **LOSES THE POINT OF PRE-ASSIGNMENT**

---

## Part 3: Recommendations

### Recommended Approach: Two-Phase Solver with Entity Subset

**Implementation Plan**:

1. **Create a pre-assignment solver config** (`preAssignmentSolverConfig.xml`):
   - Include only pre-assignment entities
   - Run with short termination (seconds, not minutes)
   - Goal: Find valid placements

2. **Create main solver config** (current):
   - Accept pinned entities from pre-assignment
   - Run full optimization

3. **Modify pre-assignment workflow**:
```java
public SchoolSchedule preAssignAndSolve(SchoolSchedule problem) {
    // Step 1: Extract pre-assignment entities
    SchoolSchedule preAssignmentProblem = extractPreAssignmentEntities(problem);

    // Step 2: Run solver to find valid placements
    Solver<SchoolSchedule> preSolver = preSolverFactory.buildSolver();
    SchoolSchedule preAssigned = preSolver.solve(preAssignmentProblem);

    // Step 3: Check if we got valid placements
    if (preAssigned.getScore().hardScore() < 0) {
        LOG.warn("Pre-assignment could not find valid placements");
        // Option A: Fall back to no pre-assignment
        // Option B: Pin only the valid ones
    }

    // Step 4: Pin successfully assigned entities
    pinAssignedEntities(preAssigned);

    // Step 5: Merge back with full problem
    SchoolSchedule fullProblem = mergePreAssigned(preAssigned, problem);

    // Step 6: Run main solver with pinned input
    Solver<SchoolSchedule> mainSolver = mainSolverFactory.buildSolver();
    return mainSolver.solve(fullProblem);
}
```

### Why This Is Better

| Aspect | Current Approach | Two-Phase Solver |
|--------|-----------------|------------------|
| Constraint checking | Partial (H1-H3 only) | Full (all constraints) |
| Validation code | Custom, must maintain | Solver handles it |
| Backtracking | None | Solver handles it |
| Optimality | First-fit only | Solver optimizes |
| Complexity | Medium | Low (reuses solver) |

### Fallback: If Two-Phase Is Too Slow

If the two-phase approach takes too long, consider:

1. **Reduce pre-assignment scope**: Only pre-assign the most critical entities
2. **Use custom CH phase**: Write a PhaseCommand that does greedy placement but uses `scoreDirector` for validation
3. **Accept partial pre-assignment**: Only pin entities that achieved 0 hard violations

---

## Part 4: Why Pre-Assignment May Still Fail

Even with full constraint checking, pre-assignment may fail because:

1. **No valid placement exists**: The lab rotation patterns may be fundamentally infeasible
2. **Cascade effects**: Placing one entity may block all options for another
3. **Over-constrained**: Too many requirements, not enough slots

### Diagnosis Steps

Before implementing any solution, run this analysis:

```bash
# Check if pre-assignment entities can even theoretically fit
# Count required slots vs available slots per room/teacher/group
```

---

## Part 5: Human Sequential Approach vs Solver

The user's observation about humans doing "pick one, find valid position, move to next" is exactly what **Construction Heuristics** do:

- **First Fit**: Pick entity, assign to first valid slot
- **First Fit Decreasing**: Order by difficulty, then First Fit
- **Cheapest Insertion**: Pick entity, assign to slot with best score impact

The difference is:
- Humans can **backtrack** ("this doesn't work, try another")
- Humans can **relax constraints** ("this teacher will have to double-book")
- Humans have **intuition** about which placements are likely to cause problems

The solver's construction heuristic is more systematic but less flexible. The **Two-Phase approach** gives the solver the same flexibility humans have: find the best valid placement, not just the first one.

---

## Conclusion

1. **Your two-solver idea is valid and supported** - Timefold explicitly documents this pattern
2. **The current pre-assigners have a validation gap** - they only check 3 of 16+ constraints
3. **The simplest fix**: Use the solver for pre-assignment instead of custom code
4. **If that fails**: Accept partial pre-assignment and let the solver handle the rest

The key insight: **Don't fight the solver, use it**. The solver already knows how to find valid placements. Let it do the work.

---

## References

- Timefold docs: Multi-stage planning with separate SolverManagers
- Timefold docs: Custom PhaseCommand API
- Timefold docs: Warm start with partially initialized solutions
- Timefold docs: Overconstrained planning with nullable variables
- Stack Overflow: Timefold CH on overconstrained problems
