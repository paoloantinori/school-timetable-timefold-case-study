# Research: Construction Heuristic Compaction Theory

**Date:** 2026-03-12
**Topic:** Why CH piles lessons into one timeslot instead of spreading across slots
**Context:** Timefold Solver school timetabling with vertical/horizontal compaction constraints

## Problem Statement

**Observation:** With compaction-only constraints (H1, H2, H3, H7, H11, M13, M20), CH places all lessons into the 08:00 timeslot, leaving slots 09:00-13:00 empty.

**User Hypothesis:** "To have full days without gaps, lessons need to be assigned to free slots, not busy slots."

## Research Findings

### 1. How CH Chooses Values (Timeslots)

**Current Configuration:**
```xml
<valueSelector variableName="timeslot">
  <cacheType>PHASE</cacheType>
  <selectionOrder>SORTED</selectionOrder>
  <sorterManner>ASCENDING</sorterManner>
</valueSelector>
```

**CH Behavior with FIRST_FIT_DECREASING:**
1. Sorts entities by `DECREASING_DIFFICULTY`
2. For each entity, evaluates values in **sorted order** (ASCENDING = 08:00 first)
3. Picks the **first value** that doesn't violate hard constraints

**Why Everything Piles into 08:00:**
- 08:00 is first in sorted order
- Initial assignment to 08:00 has same score as any other slot
- CH picks 08:00 (first valid)
- Subsequent assignments don't "see" slot overcrowding as a penalty
- Result: 148 lessons → 08:00, 0 lessons → other slots

### 2. Planning Value Strength (Key Limitation)

Timefold supports **planning value strength** to influence CH value selection:

```java
@PlanningVariable(strengthComparatorClass = TimeslotStrengthComparator.class)
public Timeslot getTimeslot() { ... }
```

**Critical Constraint from Documentation:**
> "None of the current planning variable state in any of the planning entities should be used to compare planning values."

**What This Means:**
- You CANNOT use "how many lessons are already in this slot" to calculate strength
- Strength must be **static** or based on **immutable properties**
- Example: Earlier timeslots could be "stronger" because they're more desirable

**Why This Prevents "Free Slot" Preference:**
- Knowing if a slot is "free" or "busy" requires checking current assignments
- Current assignments = planning variable state
- This violates the strength calculation rule

### 3. CH Types: First Fit vs Strongest Fit vs Weakest Fit

| CH Type | Entity Ordering | Value Ordering | Behavior |
|---------|----------------|----------------|----------|
| **FIRST_FIT** | Any | Any | Picks first valid value |
| **FIRST_FIT_DECREASING** | Hardest first | Sorted | Picks first valid (hardest entities) |
| **STRONGEST_FIT** | Any | Strongest first | Uses strong values first (assumes lower soft cost) |
| **STRONGEST_FIT_DECREASING** | Hardest first | Strongest first | Hardest entities → strongest values |
| **WEAKEST_FIT** | Any | Weakest first | Uses weak values first (saves strong for later) |
| **WEAKEST_FIT_DECREASING** | Hardest first | Weakest first | Hardest entities → weakest values |

**From Documentation:**
> "Do not presume that STRONGEST_FIT_DECREASING has better results than FIRST_FIT_DECREASING or WEAKEST_FIT_DECREASING. That's often not the case."

### 4. The Pick Early Type Factor

**Default pickEarlyType:**
- `NEVER` when InitializingScoreTrend is not ONLY_DOWN
- Evaluates ALL values before picking

**With NO Explicit Configuration:**
- CH evaluates all timeslots
- If all have equal score → picks first in sorted order (08:00)

### 5. Why Compaction Constraints Don't Help During CH

**Current Compaction Constraints:**
- **M13 (Vertical):** Penalizes days with gaps → rewards using FEWER days
- **M20 (Horizontal):** Penalizes gaps within a day → rewards clustering lessons

**The Issue:**
These constraints evaluate **after** assignment, not **during**. CH doesn't say "which slot minimizes M13/M20 penalty?" — it just says "is this assignment valid?" and picks the first valid one.

**Result:** CH picks 08:00, then LS phase tries to fix it. But LS has limited ability to move 148 lessons out of 08:00.

## Solution Options

### Option A: Slot Capacity Constraint (Recommended)

Add a constraint that directly penalizes having too many lessons in the same timeslot:

```java
private Constraint timeslotCapacity(ConstraintFactory factory) {
    return factory.forEach(Timeslot.class)
        .join(Lesson.class,
            Joiners.equal((timeslot, lesson) -> lesson.getTimeslot().equals(timeslot)))
        .groupBy((timeslot, lesson) -> timeslot)
        .filter((timeslot, lessons) -> lessons.size() > MAX_LESSONS_PER_SLOT)
        .penalize("Timeslot capacity exceeded",
            HardSoftScore.ofSoft((lessons.size() - MAX_LESSONS_PER_SLOT) * 1000));
}
```

**Pros:** Directly addresses the problem, works with CH and LS
**Cons:** Requires tuning MAX_LESSONS_PER_SLOT

### Option B: Weakest Fit Decreasing CH

Change CH configuration to use WEAKEST_FIT_DECREASING with static strength:

1. Define timeslot strength (e.g., later = weaker):
```java
public class TimeslotStrengthComparator implements Comparator<Timeslot> {
    public int compare(Timeslot a, Timeslot b) {
        // Later timeslots are "weaker" (less desirable)
        return b.getStartDateTime().compareTo(a.getStartDateTime());
    }
}
```

2. Configure WEAKEST_FIT_DECREASING:
```xml
<constructionHeuristic>
  <constructionHeuristicType>WEAKEST_FIT_DECREASING</constructionHeuristicType>
  ...
</constructionHeuristic>
```

**Pros:** Uses built-in CH algorithm
**Cons:** Static strength doesn't consider current occupancy; may not work as intended

### Option C: Custom CH with Pick Best

Use a custom CH that evaluates score impact for each potential assignment:

```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    ...
    <changeMoveSelector>
      <valueSelector>
        <cacheType>STEP</cacheType>  <!-- Recalculate score impact each step -->
        ...
      </valueSelector>
    </changeMoveSelector>
  </queuedEntityPlacer>
</constructionHeuristic>
```

**Pros:** Most accurate, considers actual score impact
**Cons:** Performance impact, complex configuration

## Conclusion

**User's Intuition Was Correct:** You DO want to prefer "free slots" over "busy slots" for even distribution.

**BUT:** Timefold's planning value strength mechanism cannot implement this directly because:
1. Strength cannot use current planning variable state
2. Knowing if a slot is "free" requires checking current state
3. This is a fundamental architectural limitation

**Recommended Solution:** Add a **slot capacity constraint** (Option A) that penalizes overcrowded timeslots. This works within Timefold's architecture because:
- It's a normal constraint (not strength calculation)
- CH evaluates score impact during assignment
- LS can optimize it later
- Tunable via MAX_LESSONS_PER_SLOT

## References

- Timefold Construction Heuristics: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics
- Planning Value Strength: https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems
- Pick Early Type: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics
