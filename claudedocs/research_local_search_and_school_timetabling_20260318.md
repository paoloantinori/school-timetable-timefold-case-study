# Research Report: Local Search Algorithms & School Timetabling Best Practices

**Date:** 2026-03-18
**Phase:** Two-part exhaustive research
**Focus:** Timefold local search optimization + school timetabling with pre-assigned days and consecutive lessons

---

## Part 1: Timefold Local Search Algorithms - Benchmark Configurations

### Algorithm Overview & Recommendations

| Algorithm | Score | Recommendation | Notes |
|-----------|-------|----------------|-------|
| **Tabu Search** | ⭐⭐⭐⭐⭐ | **RECOMMENDED** | Best overall, entity tabu preferred |
| **Simulated Annealing** | ⭐⭐⭐⭐⭐ | **RECOMMENDED** | Fast stepping, good for exploration |
| **Late Acceptance** | ⭐⭐⭐⭐⭐ | **RECOMMENDED** | Low acceptedCountLimit, stable |
| **Great Deluge** | ⭐⭐⭐⭐ | Good | Deterministic alternative to SA |
| **Step Counting Hill Climbing** | ⭐⭐⭐⭐ | Good | Similar to Late Acceptance |
| **Diversified Late Acceptance** | ⭐⭐⭐⭐ | Good | Preview feature, similar to LA |
| **Variable Neighborhood Descent** | ⭐⭐⭐ | Niche | Only for erratic landscapes, poor scaling |
| **Hill Climbing** | ⭐ | **DISCOURAGED** | Gets stuck in local optima |

### Key Configuration Parameters

#### `acceptedCountLimit`
- **Critical for real-world problems** - start with value that takes <2 seconds per step
- High values (1000+) for Tabu Search
- Low values (1-4) for Simulated Annealing, Late Acceptance, Great Deluge

#### `pickEarlyType`
- `NEVER` (default): Evaluate all accepted moves
- `FIRST_BEST_SCORE_IMPROVING`: Pick first move that improves best score
- `FIRST_LAST_STEP_SCORE_IMPROVING`: Pick first move that improves last step score

#### Acceptor Types
- `TABU_SEARCH`: Maintains tabu list to avoid local optima
- `SIMULATED_ANNEALING`: Probabilistic acceptance based on temperature
- `LATE_ACCEPTANCE`: Accepts moves better than score N steps ago
- `GREAT_DELUGE`: Deterministic water level approach
- `STEP_COUNTING_HILL_CLIMBING`: Threshold-based acceptance

#### Tabu Types
- **Entity tabu** (recommended): Makes recently moved entities tabu
- **Value tabu**: Makes recently assigned values tabu
- **Move tabu**: Makes recent steps tabu

---

### Benchmark Configuration Collection

#### 1. Tabu Search (Entity Tabu) - **BASELINE RECOMMENDED**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
        <!-- Add other move selectors as needed -->
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>7</entityTabuSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1000</acceptedCountLimit>
    </forager>
</localSearch>
```

**Variants to test:**
- Tabu sizes: 5, 7, 10, 15, 0.02 (ratio)
- With value tabu: `<propertyTabuSize>7</propertyTabuSize>`
- Combined tabu: `<compositeTabuSize>7</compositeTabuSize>` with entity and property

---

#### 2. Tabu Search with Strategic Oscillation - **FOR CONSTRAINT BALANCING**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>7</entityTabuSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1000</acceptedCountLimit>
        <finalistPodiumType>STRATEGIC_OSCILLATION_BY_LEVEL</finalistPodiumType>
    </forager>
</localSearch>
```

**Variants:**
- `STRATEGIC_OSCILLATION` (default)
- `STRATEGIC_OSCILLATION_BY_LEVEL`
- `STRATEGIC_OSCILLATION_BY_LEVEL_ON_BEST_SCORE`

---

#### 3. Simulated Annealing - **FAST EXPLORATION**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <simulatedAnnealingStartingTemperature>2hard/100soft</simulatedAnnealingStartingTemperature>
    </acceptor>
    <forager>
        <acceptedCountLimit>4</acceptedCountLimit>
    </forager>
</localSearch>
```

**Variants to test:**
- Starting temperatures: `1hard/50soft`, `2hard/100soft`, `5hard/200soft`
- acceptedCountLimit: 1 (classic), 4 (recommended), 10

---

#### 4. Simulated Annealing + Tabu Hybrid - **SALTED WITH TABU**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>5</entityTabuSize>
        <simulatedAnnealingStartingTemperature>2hard/100soft</simulatedAnnealingStartingTemperature>
    </acceptor>
    <forager>
        <acceptedCountLimit>4</acceptedCountLimit>
    </forager>
</localSearch>
```

**Use lower tabu size than pure Tabu Search**

---

#### 5. Late Acceptance - **STABLE CONVERGENCE**

```xml
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
</localSearch>
```

**Variants to test:**
- lateAcceptanceSize: 100, 200, 400, 800
- acceptedCountLimit: 1, 4

---

#### 6. Late Acceptance + Tabu Hybrid - **LATE ACCEPTANCE WITH TABU**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>5</entityTabuSize>
        <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
</localSearch>
```

**Use lower tabu size than pure Tabu Search**

---

#### 7. Great Deluge - **DETERMINISTIC ALTERNATIVE TO SA**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <greatDelugeWaterLevelIncrementRatio>0.00000005</greatDelugeWaterLevelIncrementRatio>
    </acceptor>
    <forager>
        <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
</localSearch>
```

**Variants:**
- Increment ratio: 0.00000001, 0.00000005, 0.0000001
- Fixed increment: `<greatDelugeWaterLevelIncrementScore>10</greatDelugeWaterLevelIncrementScore>`

---

#### 8. Step Counting Hill Climbing - **THRESHOLD-BASED**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <stepCountingHillClimbingSize>400</stepCountingHillClimbingSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
</localSearch>
```

**Similar to Late Acceptance but with threshold mechanism**

---

#### 9. Variable Neighborhood Descent - **FOR ERRACTIC LANDSCAPES**

```xml
<localSearch>
    <localSearchType>VARIABLE_NEIGHBORHOOD_DESCENT</localSearchType>
    <unionMoveSelector>
        <selectionOrder>ORIGINAL</selectionOrder>
        <changeMoveSelector/>
        <swapMoveSelector/>
        <pillartChangeMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <acceptorType>HILL_CLIMBING</acceptorType>
    </acceptor>
    <forager>
        <pickEarlyType>FIRST_LAST_STEP_SCORE_IMPROVING</pickEarlyType>
    </forager>
</localSearch>
```

**Note:** Doesn't scale well - only for very erratic score landscapes

---

#### 10. Diversified Late Acceptance - **PREVIEW FEATURE**

```xml
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <diversifiedLateAcceptanceSize>5</diversifiedLateAcceptanceSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
</localSearch>
```

**Requires preview feature enabled: `<solverConfig><previewFeatures>DIVERSIFIED_LATE_ACCEPTANCE</previewFeatures></solverConfig>`**

---

### Forager Configurations

#### Pick Early Type Variants

```xml
<!-- NEVER - default, evaluate all accepted moves -->
<forager>
    <pickEarlyType>NEVER</pickEarlyType>
</forager>

<!-- FIRST_BEST_SCORE_IMPROVING - pick first move that improves best score -->
<forager>
    <pickEarlyType>FIRST_BEST_SCORE_IMPROVING</pickEarlyType>
</forager>

<!-- FIRST_LAST_STEP_SCORE_IMPROVING - pick first move that improves last step -->
<forager>
    <pickEarlyType>FIRST_LAST_STEP_SCORE_IMPROVING</pickEarlyType>
</forager>
```

---

### Configuration Patterns to Avoid

**❌ Hill Climbing (DISCOURAGED)**
```xml
<!-- DO NOT USE - Gets stuck in local optima -->
<localSearch>
    <acceptor>
        <acceptorType>HILL_CLIMBING</acceptorType>
    </acceptor>
</localSearch>
```

**❌ Low acceptedCountLimit with SHUFFLED selectionOrder**
```xml
<!-- Inefficient - shuffling generates random numbers for all elements -->
<unionMoveSelector>
    <selectionOrder>SHUFFLED</selectionOrder>
    <changeMoveSelector/>
</unionMoveSelector>
<forager>
    <acceptedCountLimit>10</acceptedCountLimit> <!-- Low count with shuffle = waste -->
</forager>
```

---

### Combined/Hybrid Approaches

#### Multi-Phase Configuration Example

```xml
<!-- Phase 1: Tabu Search for global exploration -->
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>10</entityTabuSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1000</acceptedCountLimit>
    </forager>
</localSearch>

<!-- Phase 2: Late Acceptance for refinement -->
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
        <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <lateAcceptanceSize>200</lateAcceptanceSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>4</acceptedCountLimit>
    </forager>
</localSearch>

<!-- Phase 3: Simulated Annealing for final polish -->
<localSearch>
    <unionMoveSelector>
        <changeMoveSelector/>
    </unionMoveSelector>
    <acceptor>
        <simulatedAnnealingStartingTemperature>1hard/50soft</simulatedAnnealingStartingTemperature>
    </acceptor>
    <forager>
        <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
</localSearch>
```

---

### Parameter Tuning Guidelines

1. **Start with acceptedCountLimit** that produces steps in <2 seconds
2. **For Tabu Search**: Begin with entity tabu, size 7-10
3. **For Simulated Annealing**: Set temperature to max single-move score delta
4. **For Late Acceptance**: Start with size ~400 for medium problems
5. **Use the Benchmarker** to fine-tune values systematically
6. **Combine acceptors** (e.g., Tabu + SA) for salted behavior
7. **Strategic Oscillation** helps with constraint-level trade-offs

---

## Part 2: School Timetabling with Pre-Assigned Days & Consecutive Lessons

### Problem Characteristics

**This project's specific constraints:**
- **Pre-assigned days** (hard constraint): Lessons/rooms must be on specific days
- **Fully consecutive lessons**: Lessons must occupy adjacent timeslots within a day
- These are **hard constraints** - must be satisfied, not soft preferences

### Research Findings: What Works Best

#### 1. Constraint Programming Approaches (For Expressiveness)

**IBM CP Optimizer (Interval/Sequence Modeling)**
- ⭐⭐⭐⭐⭐ Best native expressiveness
- Interval variables with duration directly model consecutive slots
- Sequence variables handle resource non-overlap
- Blocked times/fixed days via availability constraints
- **Downside**: Commercial licensing required

**OR-Tools CP-SAT (Boolean/Reified Encoding)**
- ⭐⭐⭐⭐ Most practical open-source option
- Boolean x[group,subject,teacher,day,hour] variables
- Consecutive lessons via OnlyEnforceIf reified constraints
- More verbose but well-supported with community examples
- **Recommended for**: Rapid prototyping, production use

**Modeling Consecutive Lessons in CP-SAT:**
```python
# Create Boolean variables
x[group, subject, teacher, day, hour] = model.NewBoolVar(...)

# Enforce adjacency (consecutive hours)
for h1, h2 in adjacent_hour_pairs:
    not_consecutive = model.NewBoolVar(...)
    model.Add(h2 != h1 + 1).OnlyEnforceIf(not_consecutive)
    model.AddBoolAnd([hour_vars[h1], hour_vars[h2]]).OnlyEnforceIf(not_consecutive)
    model.Add(not_consecutive == 0)  # Force consecutiveness
```

---

#### 2. Metaheuristic Approaches (For Solution Quality)

**ALNS/LNS with Matheuristic Repair** ⭐⭐⭐⭐⭐
- **Best for large instances**: Within ~1% of optimal on PECTP benchmarks
- Adaptive Large Neighborhood Search with MIP repair subproblems
- Outperformed commercial MIP (Gurobi) on large instances in studies
- **Key**: Neighborhood operators must preserve adjacency blocks

**GOAL-Style Hyper-Heuristic** ⭐⭐⭐⭐⭐
- Multi-stage: Simulated Annealing + LNS + Iterated Local Search
- Multiple neighborhood operators for duration/sequence constraints
- Effective for high-school timetabling
- **Key**: Specialized moves for block contiguity

**Adaptive Tabu Search** ⭐⭐⭐⭐
- Excellent results on university timetabling benchmarks
- Multi-neighborhood moves with adaptive constraint relaxation
- **Key**: Tabu mechanisms to avoid cycling on blocked days

**PSO + Local Search Hybrid** ⭐⭐⭐⭐
- Lowest mean soft-constraint violations on XHSTT instances
- Extremely fast: 0.18s to ~22 minutes on small instances
- **Key**: Custom operators to preserve adjacency

---

#### 3. Hybrid Approaches (For Production)

**Recommended Architecture:**
```
CP-SAT / Greedy CH
       ↓
    Feasible Initial Solution
       ↓
ALNS/LNS with Block-Preserving Operators
       ↓
MIP/CP Repair for Critical Subproblems
```

**Why this works:**
1. **Construction Heuristic** ensures feasibility (hard constraints satisfied)
2. **Large Neighborhood Search** explores while preserving consecutiveness
3. **Exact Repair** handles complex constraint interactions

---

### Key Implementation Insights

#### Neighborhood Design for Consecutive Lessons

**✅ DO: Block-preserving operators**
```java
// Move entire consecutive block together
Move moveBlock(Lesson block, Timeslot newStart) {
    // All lessons in block move together
    // Maintains consecutiveness
}

// Swap two entire blocks
Move swapBlocks(Lesson block1, Lesson block2) {
    // Swap timeslots, preserve internal structure
}

// Insert block into gap
Move insertBlock(Lesson block, Timeslot gapStart) {
    // Requires checking gap size >= block size
}
```

**❌ DON'T: Single-lesson moves that break blocks**
```java
// BAD: Breaks consecutive constraint
Move moveSingleLesson(Lesson lesson, Timeslot newSlot) {
    // May create gaps in consecutive sequences
    // Requires repair mechanism
}
```

#### Handling Pre-Assigned Days

**Approach 1: Filter moves at generation**
```java
// Only generate moves that respect pre-assigned days
if (lesson.isPreAssigned() && newSlot.getDay() != lesson.getAssignedDay()) {
    return null; // Invalid move
}
```

**Approach 2: Use move filters**
```xml
<changeMoveSelector>
    <filterClass>PreAssignedDayFilter</filterClass>
</changeMoveSelector>
```

**Approach 3: Pinned entities**
```java
// Mark pre-assigned lessons as pinned
lesson.setPinned(true);
// Timefold won't move pinned entities
```

---

### Empirical Results from Literature

| Algorithm | Benchmark | Performance | Notes |
|-----------|-----------|-------------|-------|
| **ALNS + MIP repair** | PECTP | Within 1% optimal | Large instances |
| **PSO-HC hybrid** | XHSTT | Lowest mean violations | Fast convergence |
| **GOAL hyper-heuristic** | ITC-2007 | Top results | Multi-stage |
| **Adaptive Tabu** | University datasets | Excellent | Scalable |
| **CP-SAT** | Small/Medium | Feasibility-first | Good for prototyping |

**Representative numbers:**
- PSO-based solver: 0.18s to 21.88 minutes (25 students, 18 staff)
- ALNS: Near-optimal on PECTP, beating Gurobi on large instances
- HPSOT: Best mean soft-constraint violations on 7 XHSTT instances

---

### Recommendations by Problem Size

#### Small Problems (≤ 500 lessons)
- **Use**: OR-Tools CP-SAT or PSO+local search
- **Why**: Fast development, rapid solve times
- **Configuration**: Simple SA or Late Acceptance

#### Medium Problems (500-2000 lessons)
- **Use**: GOAL-style hyper-heuristic or Tabu Search
- **Why**: Balanced quality/effort
- **Configuration**: Tabu + SA hybrid, strategic oscillation

#### Large Problems (2000+ lessons)
- **Use**: ALNS/LNS with matheuristic repair
- **Why**: Scales best, highest quality
- **Configuration**: Multi-phase with specialized operators

---

### Configuration Recommendations for This Project

Given the **pre-assigned days** and **fully consecutive lessons** constraints:

#### 1. Use Entity-Focused Tabu Search
```xml
<localSearch>
    <unionMoveSelector>
        <!-- Block-aware move selectors -->
        <cartesianProductMoveSelector>
            <changeMoveSelector/>
            <pillarSelector/>
        </cartesianProductMoveSelector>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>7</entityTabuSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1000</acceptedCountLimit>
        <finalistPodiumType>STRATEGIC_OSCILLATION_BY_LEVEL</finalistPodiumType>
    </forager>
</localSearch>
```

#### 2. Add Block-Preserving Move Filters
```java
// Custom filter to prevent breaking consecutive blocks
public class ConsecutiveBlockFilter implements SelectionFilter<Lesson, Timeslot> {
    @Override
    public boolean accept(SolutionDirector<Lesson, Timeslot> director, Lesson lesson) {
        // Don't move lessons that are part of a consecutive block
        // unless moving the entire block
        return !lesson.isPartOfConsecutiveBlock() || lesson.isBlockStart();
    }
}
```

#### 3. Strategic Oscillation for Constraint Trade-offs
- Helps balance hard constraint levels (teacher conflicts vs room conflicts vs consecutiveness)
- `STRATEGIC_OSCILLATION_BY_LEVEL` prefers improving softer constraints when no improving move exists

#### 4. Consider Late Acceptance for Stability
- Late Acceptance's memory mechanism helps with highly constrained problems
- Less sensitive to parameter tuning than SA

---

### Summary of Actionable Recommendations

**For Benchmarking (Phase 1):**
1. Start with **Tabu Search (entity tabu, size 7-10)** as baseline
2. Test **Strategic Oscillation** variants for constraint balancing
3. Try **Simulated Annealing** (low acceptedCountLimit) for exploration
4. Test **Late Acceptance** for stable convergence
5. **Avoid**: Pure Hill Climbing (gets stuck)

**For School Timetabling (Phase 2):**
1. **Model**: Use block-aware move selectors that preserve consecutive lessons
2. **Filter**: Apply pre-assigned day filters at move generation
3. **Algorithm**: Tabu Search with Strategic Oscillation or GOAL-style hyper-heuristic
4. **Hybrid**: Consider multi-phase with CH → Tabu → SA
5. **Neighborhood**: Design operators that move entire blocks, not individual lessons

---

## References

### Timefold Documentation
1. Local Search - https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search
2. Optimization Algorithms Overview - https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview
3. Move Selector Reference - https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
4. Move and Neighborhood Selection - https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview

### School Timetabling Research
5. CP-SAT Primer - https://github.com/d-krupke/cpsat-primer
6. OR-Tools CP Solver - https://developers.google.com/optimization/cp
7. IBM CP Optimizer Scheduling - https://ibm.com/docs/en/icos/22.1.1?topic=concepts-introduction-scheduling-in-cp-optimizer
8. ALNS for Timetabling - https://khoury.northeastern.edu/home/rhoshino/papers/quest7.pdf
9. GOAL Solver - https://ahmedkheiri.github.io/publications/EJOR-HH.pdf
10. PSO-HC Hybrid - https://www.graham-kendall.com/papers/tgsks2021.pdf
11. Open Timetables - https://github.com/rocristoi/openTimetables
12. Hybrid-Mistral - https://github.com/siala/Hybrid-Mistral

---

**Report prepared by:** Claude Code Research Agent
**Confidence:** High (based on official documentation + peer-reviewed research)
**Next steps:** Run benchmark comparisons to identify best configuration for this specific problem
