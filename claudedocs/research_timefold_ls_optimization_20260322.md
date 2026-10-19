# Timefold Solver Local Search Optimization for School Timetabling

**Date:** 2026-03-22
**Scope:** Comprehensive research on LS techniques for timetabling, with actionable recommendations
**Sources:** Official Timefold docs (1.x + 2.0 beta), Timefold blog, Picnic engineering blog, academic papers, GitHub discussions

---

## Executive Summary

Local Search is the dominant algorithm class for real-world timetabling. International Timetabling Competition results consistently show LS-based approaches outperform exhaustive and population-based methods under real-world time constraints. This report covers move selectors, acceptance criteria, nearness, entity selection, move filters, shadow variables, phase configuration, and Timefold 1.x-specific features.

**Key takeaways for our project:**
1. Our current move set (Change + Swap + PillarChange + PillarSwap) is solid but we are missing `SwapMove` on the room variable during the timeslot phase.
2. Tabu Search with entity tabu size 5-7 is the right default; Late Acceptance is the strongest alternative.
3. Nearby selection (Enterprise-only) could significantly improve our timetabling by biasing toward same-day or adjacent-day timeslots.
4. Move filters are a double-edged sword: they save evaluation time but can create score traps if not carefully designed.
5. The 5-phase architecture is appropriate but Phase 3 and Phase 5 could potentially be merged into a single LS phase with cartesian product selectors.

---

## 1. Move Selector Best Practices for Timetabling

### 1.1 Available Move Types

| Move | Description | Timetabling Relevance | In Our Config? |
|------|-------------|----------------------|----------------|
| **ChangeMove** | Change one entity's planning variable | Core move: reassign lesson to different timeslot/room | Yes (timeslot + room) |
| **SwapMove** | Swap planning variables between two entities | Avoids intermediate constraint violations | Yes (timeslot only) |
| **PillarChangeMove** | Move all entities sharing the same value | Shift entire day's schedule | Yes (experimental) |
| **PillarSwapMove** | Swap all entities between two values | Exchange entire day schedules | Yes (experimental) |
| **SubChainSwapMove** | Swap a sub-sequence of chained entities | Vehicle routing, not applicable | No (list variable) |
| **RuinRecreateMove** | Remove N entities, re-insert via CH | Escape deep local optima | No |
| **2-Opt Move** | Reconnect edges in a tour | Vehicle routing only | No |
| **Custom Move** | User-defined move logic | Domain-specific operations | No |

### 1.2 Recommended Move Set for Timetabling

The default Timefold configuration for basic variables (non-list) includes:
- **ChangeMove** (timeslot variable)
- **ChangeMove** (room variable)
- **SwapMove** (both variables, same entity class)

**SwapMove is critically important for timetabling.** From the official docs:

> "Although a SwapMove on a single variable is essentially just two ChangeMoves, it's often the winning step in cases that the first of the two ChangeMoves would not win because it leaves the solution in a state with broken hard constraints. For example: swapping the room of two lectures doesn't bring the solution in an intermediate state where both lectures are in the same room which breaks a hard constraint."

**Gap analysis for our config:**
- Phase 3 (Compactness) has ChangeMove(timeslot) + SwapMove(timeslot) + Pillar moves -- GOOD
- Phase 5 (Room Refinement) has ChangeMove(room) only -- **MISSING SwapMove(room)**

Adding `SwapMoveSelector` for room in Phase 5 would allow the solver to swap rooms between two lessons in a single step, avoiding the intermediate state where both lessons are in the same room (H2 hard constraint violation).

### 1.3 Cartesian Product Selectors

A `<cartesianProductMoveSelector>` combines multiple independent moves into a single composite move. This is useful when two changes must happen simultaneously to improve the score (avoiding score traps).

Example: Move a lesson to a new timeslot AND change its room in a single step:
```xml
<cartesianProductMoveSelector>
    <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
        <valueSelector variableName="room"/>
    </changeMoveSelector>
</cartesianProductMoveSelector>
```

**Recommendation:** Consider adding a cartesian product move (timeslot + room) to Phase 3 or a combined phase. This directly addresses the score trap where moving a lesson's timeslot forces a room conflict that the solver cannot resolve in a single ChangeMove.

### 1.4 RuinRecreate Move (Coarse-Grained)

RuinRecreate removes a subset of entities from their assignments and re-inserts them using a construction heuristic. It is **not enabled by default** and is a coarse-grained move that helps escape deep local optima.

**Critical warning from docs:**
> "If nearby selection is enabled, the RuinRecreateMove is likely to underperform."

**Recommended configuration** uses probabilistic selection to control frequency:
```xml
<ruinRecreateMoveSelector>
    <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
    <!-- Other moves at 100.0 weight -->
</ruinRecreateMoveSelector>
```

This runs RuinRecreate once for every 100 fine-grained moves.

**Timetabling relevance:** Could be useful for "unsticking" a group of lessons that are trapped in a bad day/room combination. However, the overhead is significant. Benchmark before adopting.

### 1.5 Move Selector API Deprecation

Timefold is moving from the Move Selector API to the **Neighborhoods API**:
> "The Neighborhoods API is a simple and intuitive way to implement custom moves. It is currently a preview feature and is expected to be the main way to implement custom moves in the near future."

The Move Selector API will continue to be supported throughout Timefold Solver 2.0 but will only receive critical bug fixes. For now, the Move Selector API remains the primary way to configure moves.

---

## 2. Acceptance Criteria Comparison

### 2.1 Algorithm Comparison Matrix

| Algorithm | Scalability | Optimal? | Easy? | Tunable? | Requires CH? | Best For |
|-----------|-------------|----------|-------|----------|-------------|----------|
| Hill Climbing | 5/5 | 2/5 | 4/5 | 3/5 | Yes | Simple problems |
| **Tabu Search** | **5/5** | **4/5** | 3/5 | **5/5** | Yes | **General purpose (recommended)** |
| **Simulated Annealing** | **5/5** | **4/5** | 2/5 | **5/5** | Yes | **Exploration-heavy problems** |
| **Late Acceptance** | **5/5** | **4/5** | 4/5 | 4/5 | Yes | **Stable, low-parameter** |
| Great Deluge | 5/5 | 4/5 | 3/5 | 5/5 | Yes | Deterministic SA alternative |
| Step Counting HC | 5/5 | 4/5 | 3/5 | 5/5 | Yes | Similar to Late Acceptance |
| VND | 3/5 | 3/5 | 2/5 | 5/5 | Yes | Erratic landscapes only |

### 2.2 Detailed Recommendations

#### Tabu Search (Our Current Choice)

**Why it works well for timetabling:**
- Entity tabu prevents cycling back to recently moved lessons
- High `acceptedCountLimit` (1000) ensures thorough exploration per step
- Deterministic tabu list makes debugging easier

**Recommended configuration:**
```xml
<acceptor>
    <entityTabuSize>7</entityTabuSize>  <!-- or 0.02 ratio -->
</acceptor>
<forager>
    <acceptedCountLimit>1000</acceptedCountLimit>
</forager>
```

**Tabu types:**
- **Entity tabu** (recommended): Makes recently moved lessons tabu. Best default.
- **Value tabu**: Makes recently assigned timeslots/rooms tabu. Useful when certain values are over-concentrated.
- **Move tabu**: Prevents repeating exact same step. Works with custom moves.
- **Combined tabu**: `<entityTabuSize>7</entityTabuSize>` + `<valueTabuSize>3</valueTabuSize>`

**Tabu size tuning:**
- Too small: cycles back to recent solutions
- Too large: restricts too many moves, slows progress
- Ratio-based (0.02 = 2% of entities) adapts to problem size automatically

#### Late Acceptance (Strong Alternative)

Late Acceptance accepts moves that are at least as good as the score from N steps ago. It is:
- Less sensitive to parameter tuning than SA
- Deterministic (no random component in acceptance)
- Good at escaping plateaus

**Configuration:**
```xml
<acceptor>
    <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
    <acceptedCountLimit>1</acceptedCountLimit>  <!-- Low for LA -->
</forager>
```

**Key difference from Tabu:** LA uses low `acceptedCountLimit` (1-4), Tabu uses high (1000+).

#### Simulated Annealing

SA uses a temperature that decreases over time, probabilistically accepting worsening moves:
```xml
<acceptor>
    <simulatedAnnealingStartingTemperature>0hard/200medium/0soft</simulatedAnnealingStartingTemperature>
</acceptor>
<forager>
    <acceptedCountLimit>1</acceptedCountLimit>  <!-- Low for SA -->
</forager>
```

**Pros:** Very good exploration, escapes deep local optima.
**Cons:** Temperature parameter is hard to tune; needs to know approximate score range.

#### Hybrid: Tabu + SA

From official docs:
> "Simulated Annealing can be combined with a tabu acceptor at the same time. That gives Simulated Annealing salted with a bit of Tabu. Use a lower tabu size than in a pure Tabu Search configuration."

```xml
<acceptor>
    <simulatedAnnealingStartingTemperature>0hard/200medium/0soft</simulatedAnnealingStartingTemperature>
    <entityTabuSize>3</entityTabuSize>  <!-- Lower than pure Tabu -->
</acceptor>
```

### 2.3 Multi-Phase Acceptor Strategy (Recommended by Timefold)

The official docs recommend:
> "1. First Fit Decreasing
> 2. Late Acceptance (relatively long time)
> 3. Tabu Search (relatively short time)"

This multi-algorithm approach gives LA time to stabilize, then Tabu fine-tunes. Our current single-algorithm approach (Tabu throughout) could benefit from trying LA for the compactness phase and Tabu for the room refinement phase.

---

## 3. Nearness Metadata (Enterprise Feature)

**IMPORTANT: Nearby selection is a commercial feature of Timefold Solver Enterprise Edition and is NOT available in the Community Edition.**

### 3.1 What It Does

Nearby selection biases the solver toward selecting planning values that are "nearby" to the current value. For timetabling, this means:
- When moving a lesson from Monday slot 2, prefer Monday slot 3 over Thursday slot 1
- This reduces the "jumps" in the search path, leading to better local improvements

From the docs:
> "In some use cases (such as TSP and VRP, but also in other cases), changing entities to nearby values or swapping nearby entities leads to better results faster."

### 3.2 How It Would Help Timetabling

A `NearbyDistanceMeter` for timeslots could be:
```java
public class TimeslotNearbyDistanceMeter implements NearbyDistanceMeter<Timeslot, Timeslot> {
    @Override
    public double getNearbyDistance(Timeslot origin, Timeslot destination) {
        // Distance in days + fraction for same-day slot distance
        long daysBetween = ChronoUnit.DAYS.between(origin.getDate(), destination.getDate());
        if (daysBetween == 0) {
            // Same day: distance = slot difference / total slots
            return Math.abs(origin.getSlotIndex() - destination.getSlotIndex()) * 0.1;
        }
        return daysBetween;
    }
}
```

This would make the solver prefer:
1. Same-day adjacent slots (distance 0.1)
2. Same-day non-adjacent slots (distance 0.2-0.5)
3. Next-day slots (distance 1.0)
4. Far-away slots (distance 5+)

### 3.3 Community Edition Alternatives

Since nearby selection is enterprise-only, we can approximate its effect:

1. **Move filters**: Filter out moves to timeslots that are too far away (e.g., different week). Risky due to score traps.

2. **Custom shadow variable**: Track "day proximity score" and penalize lessons far from their group's other lessons. Already partially achieved by H17 (vertical compactness) and S7 (reward consecutive).

3. **Difficulty comparator for timeslots**: Sort timeslots by proximity to the lesson's current assignment, so ChangeMove evaluates nearby slots first. This is a poor approximation.

4. **Pillar moves**: Our pillarChange/pillarSwap moves partially achieve the same goal by moving entire day-blocks together.

---

## 4. Entity Selectors for Local Search

### 4.1 Selection Order

For Local Search, entity selection order matters less than for Construction Heuristics because LS evaluates many moves per step. However, sorting can still help:

| Order | Description | LS Relevance |
|-------|-------------|--------------|
| `ORIGINAL` | Default order | Good for reproducibility |
| `RANDOM` | Random order each step | Default for LS, prevents order bias |
| `SHUFFLED` | Random once per step | Good, but slower with low acceptedCountLimit |
| `SORTED` | By comparator | Useful for CH, less so for LS |
| `PROBABILISTIC` | Weight-based random | Useful for focusing on hard-to-schedule entities |

**From the docs:**
> "With a low `acceptedCountLimit` (so a fast stepping algorithm), it is recommended to avoid using `selectionOrder` SHUFFLED because the shuffling generates a random number for every element in the selector, taking up a lot of time, but only a few elements are actually selected."

### 4.2 Difficulty Comparator

We already use `LessonDifficultyComparator` and `LabBlockDifficultyComparator` for CH sorting. For LS, these are less critical but can be used with `selectionOrder SORTED` if desired.

**Important caveat from docs:**
> "None of the current planning variable states should be used to compare planning entity difficulty. During Construction Heuristics, those variables are likely to be null anyway."

### 4.3 Probabilistic Entity Selection

Weight entities by how "problematic" they are:
```java
public class LessonProblemWeightFactory implements SelectionProbabilityWeightFactory<Lesson> {
    @Override
    public double createProbabilityWeight(ScoreDirector<Score> scoreDirector, Lesson lesson) {
        // Higher weight = more likely to be selected for moves
        double weight = 1.0;
        if (lesson.isPinned()) return 0.1;  // Rarely move pinned lessons
        if (lesson.getCourse().getStudentGroup().getMaxHoursPerDay() <= 3) weight *= 2.0;
        return weight;
    }
}
```

**For our project:** Could weight lessons with H17 (vertical compactness) violations higher, focusing the solver on fixing compaction issues.

---

## 5. Custom Move Filters

### 5.1 When Filters Are Useful

From the Picnic blog:
> "TimeFold's features, such as move filtering and benchmarking, are used to improve our algorithms and get better results in a shorter timespan."

Move filters skip evaluating moves that are obviously pointless, saving score calculation time:
```java
public class SameCourseSwapFilter implements SelectionFilter<ScoreDirector, SwapMove> {
    @Override
    public boolean accept(ScoreDirector scoreDirector, SwapMove move) {
        Lesson left = (Lesson) move.getLeftEntity();
        Lesson right = (Lesson) move.getRightEntity();
        // Don't swap lessons of the same course (same teacher + same group)
        return !left.getCourse().equals(right.getCourse());
    }
}
```

### 5.2 When Filters Are Harmful

**Critical warning from Picnic:**
> "It should be noted that move filtering can also reduce the performance of the algorithm. Each move must first pass this filter, which requires computation time. Thus, if the amount of moves that can be filtered is small or if the filter calculation is complex, it might be better to simply evaluate the HardSoft score of every move."

**Key risks:**
1. **Filter overhead**: Complex filter logic can be slower than just evaluating the move
2. **Score traps**: If the filter removes a move that the solver needs to escape a local optimum, the solver gets stuck
3. **Incomplete filtering**: A filter on ChangeMove must also be applied to SwapMove, PillarChangeMove, etc.

### 5.3 Filter vs Score Trap

From Timefold docs on score traps:
> "Avoiding score traps does not mean that your score function should be smart enough to avoid local optima. Leave it to the optimization algorithms to deal with the local optima. Avoiding score traps means to avoid, for each score constraint individually, a flatlined score function."

**Example score trap in timetabling:**
If you penalize "days with fewer than 4 lessons" with a flat weight of -1000 per day, then moving one lesson from a 5-lesson day to a 3-lesson day doesn't improve the score (both days still violate the constraint). The solver has no incentive to make the move.

**Fix:** Use graduated weights:
- 3 lessons on a day: -1000
- 2 lessons on a day: -3000
- 1 lesson on a day: -5000
- 0 lessons on a day: 0 (no violation -- the group simply doesn't attend that day)

This is exactly what our M20 (sparse day penalty) constraint does.

### 5.4 Recommendation for Our Project

Our `GapPreservingMoveFilter` and `UnpinnedLessonFilter` are reasonable but should be benchmarked with and without them. Given that we removed the unpinned filter from Phase 3 in our latest config, that is the right direction. The more moves the solver can evaluate, the better it can escape local optima -- especially with Tabu Search which needs diverse moves to be effective.

---

## 6. Shadow Variables and Incremental Score Calculation

### 6.1 How Incremental Score Works

Timefold's Constraint Streams engine automatically performs incremental score calculation. When a single lesson moves from one timeslot to another, only the constraints involving that lesson, its teacher, its student group, and its room are re-evaluated. This is a massive performance gain.

> "When a single queen A moves from row 1 to 2, it will not bother to check if queen B and C can attack each other, since neither of them changed."

### 6.2 Shadow Variables for Timetabling

Shadow variables are derived from genuine planning variables. Timefold provides several built-in types:

| Type | Annotation | Use Case |
|------|-----------|----------|
| Inverse relation | `@InverseRelationShadowVariable` | Track which lessons are in a room |
| Anchor | `@AnchorShadowVariable` | Chained/tour planning |
| Previous/Next | `@PreviousElementShadowVariable` | List variable ordering |
| Custom | `@CustomShadowVariable` (1.x) | Any derived computation |
| Declarative | `@ShadowVariable` with supplier (1.31+) | Simplified custom shadows |

### 6.3 Declarative Shadow Variables (New in Timefold 1.31)

**This is the biggest LS-relevant feature in recent Timefold versions.** From Geoffrey De Smet's LinkedIn post listing the top 5 open-source features of 2025:

> "1) Declarative Shadow Variables"

Declarative shadow variables replace the old variable listener mechanism (deprecated for removal in 2.0):

**Old way (variable listeners):**
```java
@PlanningEntity
public class Lesson {
    @CustomShadowVariable(variableListenerClass = ..., ...)
    private String derivedField;
}
```

**New way (declarative, 1.31+):**
```java
@PlanningEntity
public class Lesson {
    @ShadowVariable(sourceVariableName = "timeslot",
                    variableListenerClass = LessonTimeslotListener.class)
    private LocalDate lessonDate;

    // Or with supplier (even simpler):
    @ShadowVariable(sourceVariableName = "timeslot")
    private LocalDate lessonDate;  // auto-computed via supplier method
}
```

**Migration required:** Variable listeners are deprecated and will be removed in Timefold 2.0. We should check if our project uses variable listeners.

### 6.4 LS-Specific Shadow Variable Opportunities for Timetabling

Potential shadow variables that could speed up constraint evaluation:

1. **Lesson date** (derived from timeslot): Avoids calling `timeslot.getDate()` repeatedly in constraints. Our H17 (vertical compactness) groups by date.

2. **Day slot index** (derived from timeslot): The `slotIndex` (0-5) for the lesson's day. Used by H18 (5-hour groups start from slot 0).

3. **Teacher daily load** (derived from all lessons): Track how many hours a teacher has on each day. Used by H7 (max teacher daily hours).

4. **Student group daily load** (derived from all lessons): Track how many hours a group has on each day. Used by H11 (max student group daily hours) and H17 (vertical compactness).

**Caveat:** Shadow variables on collection-level aggregations (like daily load) require careful implementation. The supplier method must be fast and correct. For most cases, computing these inline in Constraint Streams is faster because the engine can share intermediate results across constraints.

### 6.5 Performance Guidelines

From the docs:
> "Each custom Shadow Variable on each planning entity constitutes one node of a reference graph. The Solver analyzes this graph to decide what is the most optimal strategy for updating these variables. In general, the less variables and the less dependencies between them, the faster the processing."

**Guidelines:**
- Keep all shadow variables on the same entity class
- Use `@InverseRelationShadowVariable` for simple back-references (e.g., room -> lessons)
- Avoid shadow variables that depend on other shadow variables (chain depth increases overhead)
- Shadow variable suppliers are called very frequently -- they must be fast (use HashMap lookups, not iteration)

---

## 7. Phase Configuration Strategy

### 7.1 Our Current 5-Phase Architecture

| Phase | Type | Goal | Duration |
|-------|------|------|----------|
| 1 | Custom CH | Day-clustered placement | Fast (seconds) |
| 2 | CH (FIRST_FIT) | Fallback placement | Fast |
| 3 | LS (Tabu, timeslot) | Compactness optimization | 30min unimproved |
| 4 | CH (FIRST_FIT) | Room assignment | Fast |
| 5 | LS (Tabu, room) | Room refinement | 2min unimproved |

### 7.2 When Multi-Phase Helps

1. **Separate variable domains**: When optimizing timeslots and rooms independently (timeslots first, then rooms), phases prevent the solver from wasting time on room conflicts while still resolving timeslot conflicts.

2. **Different move sets per phase**: Our Phase 3 uses pillar moves (timeslot-only) while Phase 5 uses room-only moves. These are orthogonal concerns.

3. **Termination control**: Different phases can have different time limits. Room refinement converges faster than compactness.

### 7.3 When Multi-Phase Hurts

1. **Lost exploration time**: Each phase starts from scratch. Time spent in Phase 3 that could have been used in Phase 5 is wasted if Phase 3's solution doesn't need room refinement.

2. **Phase boundary traps**: A good timeslot solution from Phase 3 might be unachievable with the room assignments from Phase 4. The solver in Phase 5 cannot go back and change timeslots.

3. **Termination overhead**: Time spent on phase transitions (custom phase commands) is time not spent optimizing.

### 7.4 Alternative: Single LS Phase with Cartesian Product

Instead of separate timeslot + room phases, use a single LS phase with all moves:
```xml
<localSearch>
    <termination>
        <unimprovedSecondsSpentLimit>1800</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
        <changeMoveSelector>
            <valueSelector variableName="timeslot"/>
        </changeMoveSelector>
        <changeMoveSelector>
            <valueSelector variableName="room"/>
        </changeMoveSelector>
        <swapMoveSelector/>  <!-- Swaps both timeslot AND room -->
        <cartesianProductMoveSelector>
            <changeMoveSelector>
                <valueSelector variableName="timeslot"/>
            </changeMoveSelector>
            <changeMoveSelector>
                <valueSelector variableName="room"/>
            </changeMoveSelector>
        </cartesianProductMoveSelector>
        <pillarChangeMoveSelector>
            <valueSelector variableName="timeslot"/>
        </pillarChangeMoveSelector>
    </unionMoveSelector>
    <acceptor>
        <entityTabuSize>7</entityTabuSize>
    </acceptor>
    <forager>
        <acceptedCountLimit>1000</acceptedCountLimit>
    </forager>
</localSearch>
```

**Trade-off:** More moves per step = slower step time but more exploration. The cartesian product move specifically addresses the score trap where a lesson needs both a timeslot AND room change to improve.

### 7.5 VND (Variable Neighborhood Descent) -- Not Recommended for Our Scale

From the docs:
> "Variable Neighborhood Descent doesn't scale well, but it is useful in some use cases with a very erratic score landscape."

VND tries multiple move selectors sequentially, picking the first improving move. With our ~8000 entities, VND would be too slow. Our problem has a relatively smooth score landscape (few deep local optima), making Tabu Search more appropriate.

---

## 8. Timefold 1.x Features Relevant to LS

### 8.1 New Features in Recent Versions (1.20+)

From Geoffrey De Smet's LinkedIn post and release notes:

| Feature | Version | LS Relevance |
|---------|---------|--------------|
| **Declarative Shadow Variables** | 1.31 | Replace variable listeners, simpler model |
| **Diminished Returns Termination** | 1.31+ | Auto-terminate when improvement rate drops |
| **Precompute Constraint Streams** | 1.31+ | Faster initial score calculation |
| **List Variable Improvements** | Ongoing | Better chained entity handling |
| **Neighborhoods API** | Preview | Future replacement for Move Selectors |
| **Diversified Late Acceptance** | Preview | Alternative to standard LA |
| **Solution Diff API** | Preview | Better debugging of solver steps |

### 8.2 Diminished Returns Termination

This is particularly valuable for long-running solves:
```xml
<termination>
    <diminishedReturnsTimeGradientThreshold>0.001</diminishedReturnsTimeGradientThreshold>
</termination>
```

Automatically terminates when the score improvement rate drops below a threshold. This prevents wasting time on a flatlined solver.

**Recommendation:** Add this to Phase 3 alongside the existing `unimprovedSecondsSpentLimit`.

### 8.3 Variable Listeners Deprecation

Variable listeners are deprecated for removal in Timefold 2.0. If our project uses `@CustomShadowVariable` with `variableListenerClass`, we need to migrate to the declarative approach:

**Before (deprecated):**
```java
@CustomShadowVariable(
    variableListenerClass = SomeListener.class,
    sources = {@PlanningVariableReference(entityClass = Lesson.class, variableName = "timeslot")})
private LocalDate lessonDate;
```

**After (1.31+):**
```java
@ShadowVariable(sourceVariableName = "timeslot",
                variableListenerClass = LessonDateUpdater.class)
private LocalDate lessonDate;
```

### 8.4 Timefold 1.31 = Final Feature Release of 1.x

From the upgrade guide:
> "Timefold Solver 1.31.0 marks the final feature release of the 1.x line. All new feature development will focus on Timefold Solver 2.0, and the 1.x line moves into maintenance mode."

Our project uses Timefold 1.31.0 (patched). This means we are on the latest 1.x release and should plan for the 2.0 migration eventually (which removes chained variables and variable listeners).

---

## 9. Actionable Recommendations for Our Project

### Priority 1: Quick Wins (Low Risk)

1. **Add SwapMoveSelector for room in Phase 5**
   ```xml
   <swapMoveSelector>
       <entitySelector>
           <entityClass>com.school.planner.domain.Lesson</entityClass>
       </entitySelector>
       <secondaryEntitySelector>
           <entityClass>com.school.planner.domain.Lesson</entityClass>
       </secondaryEntitySelector>
       <variableNameIncludes>
           <variableNameInclude>room</variableNameInclude>
       </variableNameIncludes>
   </swapMoveSelector>
   ```

2. **Benchmark Tabu size 7 vs 5** -- our research doc mentions 7 as the recommended starting point, but our current config uses 5. Verify which is actually better.

3. **Add diminished returns termination to Phase 3:**
   ```xml
   <termination>
       <secondsSpentLimit>3600</secondsSpentLimit>
       <unimprovedSecondsSpentLimit>1800</unimprovedSecondsSpentLimit>
       <diminishedReturnsTimeGradientThreshold>0.0001</diminishedReturnsTimeGradientThreshold>
   </termination>
   ```

### Priority 2: Medium Effort (Benchmark Required)

4. **Experiment with Late Acceptance for Phase 3**
   - LA is the default in Timefold and works well for timetabling
   - Try: `<lateAcceptanceSize>400</lateAcceptanceSize>` + `<acceptedCountLimit>1</acceptedCountLimit>`
   - Compare against our current Tabu config

5. **Try multi-algorithm approach: LA (long) + Tabu (short)**
   - Phase 3a: Late Acceptance for 20 minutes
   - Phase 3b: Tabu Search for 10 minutes
   - This is the recommended pattern from Timefold docs

6. **Add SwapMoveSelector for both timeslot AND room in Phase 3**
   - Currently Phase 3 only has timeslot swap
   - Adding room swap to Phase 3 allows resolving room conflicts during compactness optimization

### Priority 3: Higher Effort (Significant Changes)

7. **Evaluate single LS phase vs current 5-phase architecture**
   - Try merging Phase 3 and Phase 5 into a single LS phase with cartesian product moves
   - Benchmark: 5-phase (current) vs 2-phase (CH + combined LS)

8. **Add cartesian product move (timeslot + room change)**
   - Addresses score trap where both variables must change simultaneously

9. **Investigate RuinRecreate with probabilistic selection (1/100)**
   - Only if the solver frequently gets stuck in deep local optima
   - Benchmark with and without

10. **Plan Timefold 2.0 migration**
    - Migrate variable listeners to declarative shadow variables
    - Evaluate Neighborhoods API for custom moves
    - Test on 2.0 beta

### Priority 4: Enterprise Features (If Budget Allows)

11. **Nearby selection for timeslots** -- Enterprise only
    - Implement `TimeslotNearbyDistanceMeter`
    - Would significantly improve timeslot move quality
    - Free community alternative: implement as a custom move filter (with score trap risk)

---

## Sources

- Timefold Solver Local Search docs: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search
- Timefold Move Selector Reference: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
- Timefold Performance Tips: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
- Timefold Design Patterns: https://docs.timefold.ai/timefold-solver/1.x/design-patterns/design-patterns
- Timefold Modeling: https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems
- Timefold Upgrade Guide (1.x): https://docs.timefold.ai/timefold-solver/1.x/upgrading-timefold-solver/upgrade-to-latest-version
- Timefold Nearby Selection (Enterprise): https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition
- Timefold Benchmarking: https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking
- Picnic "Hill-climbing towards good plannings with TimeFold": https://jobs.picnic.app/en/blogs/hill-climbing-towards-good-plannings-with-timefold
- Geoffrey De Smet LinkedIn (2025 features): https://www.linkedin.com/posts/ge0ffrey_top-5-open-source-new-features-in-2025-in-activity-7414774132640509952-3lEh
- GitHub Discussion #951 (score traps): https://github.com/TimefoldAI/timefold-solver/discussions/951
- Late Acceptance paper (Burke & Bykov, 2012): https://patatconference.org/patat2008/proceedings/Bykov-HC2a.pdf
