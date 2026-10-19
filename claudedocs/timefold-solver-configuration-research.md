# Timefold Solver Configuration for School Timetable Scheduling

## Research Report: Optimizing for Student Group Compactness (No Gaps)

**Date**: 2026-02-13
**Scope**: Timefold Solver 1.x (successor to OptaPlanner)
**Goal**: Produce better solutions where student groups have full, contiguous days

---

## Table of Contents

1. [Construction Heuristic Options](#1-construction-heuristic-options)
2. [Local Search Algorithms](#2-local-search-algorithms)
3. [Move Selectors](#3-move-selectors)
4. [Multi-Phase Strategies](#4-multi-phase-strategies)
5. [Termination Configuration](#5-termination-configuration)
6. [Score Level Weighting](#6-score-level-weighting)
7. [Constraint Weight Configuration](#7-constraint-weight-configuration)
8. [Analysis of Current Configuration](#8-analysis-of-current-configuration)
9. [Recommended Configurations](#9-recommended-configurations)

---

## 1. Construction Heuristic Options

### Available Types

| Type | Description | Best For |
|------|-------------|----------|
| `FIRST_FIT` | Assigns first uninitialized entity to the best value | Quick baseline |
| `FIRST_FIT_DECREASING` | Sorts entities by difficulty, places hardest first | **Recommended starting point** |
| `WEAKEST_FIT` | Places entity in the least-used value | Load balancing |
| `WEAKEST_FIT_DECREASING` | Difficulty sort + weakest fit | Balanced distribution |
| `STRONGEST_FIT` | Places entity in the most-used value | Clustering/compactness |
| `STRONGEST_FIT_DECREASING` | Difficulty sort + strongest fit | **Best for compactness** |
| `ALLOCATE_FROM_POOL` | Most versatile - all entity-value combos in a pool | Complex problems |
| `CHEAPEST_INSERTION` | Inserts entity-value pair that increases score least | TSP-style problems |

### Timetabling Recommendations

**FIRST_FIT_DECREASING** (current choice) is the documented recommendation for step 2 of the optimization procedure. However, for a compactness goal (student groups with no gaps), **STRONGEST_FIT_DECREASING** deserves testing because it will try to cluster lessons into already-occupied timeslots rather than spreading them out.

**ALLOCATE_FROM_POOL** is the most versatile but slower. It considers all entity-value combinations globally, which can produce a better initial solution at the cost of construction time. For large problems, it may not scale well.

### Current Config vs. Alternatives

The current configuration uses sequential variable assignment (one `changeMoveSelector` per variable), which is the scaling-friendly approach:

```xml
<!-- CURRENT: Sequential assignment - scales well -->
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <changeMoveSelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</constructionHeuristic>
```

The alternative is Cartesian product (assigns both variables together), which produces better quality but scales poorly:

```xml
<!-- ALTERNATIVE: Cartesian product - better quality, worse scaling -->
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <!-- No individual changeMoveSelectors = cartesian product of all variables -->
</constructionHeuristic>
```

### Recommended Construction Heuristic for Compactness

```xml
<constructionHeuristic>
  <constructionHeuristicType>STRONGEST_FIT_DECREASING</constructionHeuristicType>
  <changeMoveSelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</constructionHeuristic>
```

STRONGEST_FIT_DECREASING will place the hardest lessons first (due to `LessonDifficultyComparator`) and prefer timeslots that are already heavily used by the same student group, naturally creating more compact schedules from the start. The local search then refines from a better starting point.

---

## 2. Local Search Algorithms

### Algorithm Comparison for Timetabling

| Algorithm | Diversification | Intensification | Speed | Reproducibility | Timetabling Fit |
|-----------|----------------|-----------------|-------|-----------------|-----------------|
| **Late Acceptance** | 4/5 | 4/5 | Fast stepping | Yes | Excellent |
| **Tabu Search** | 4/5 | 5/5 | Slow stepping | Yes | Excellent |
| **Simulated Annealing** | 5/5 | 3/5 | Fast stepping | No* | Good |
| **Great Deluge** | 4/5 | 4/5 | Fast stepping | Yes | Good |
| **Step Counting HC** | 4/5 | 4/5 | Fast stepping | Yes | **Best for timetabling** |

*Simulated Annealing depends heavily on random numbers and time gradients, making it non-reproducible with time-based termination.

### Late Acceptance (Currently Used)

The default and most commonly recommended algorithm. Accepts a move if it doesn't decrease the score, or if it leads to a score at least as good as the best score from `lateAcceptanceSize` steps ago.

```xml
<localSearch>
  <acceptor>
    <lateAcceptanceSize>400</lateAcceptanceSize>
  </acceptor>
  <forager>
    <acceptedCountLimit>1</acceptedCountLimit>
  </forager>
</localSearch>
```

**Key parameter**: `lateAcceptanceSize` controls how "adventurous" the search is. Higher values allow more worsening moves (better for escaping local optima). The docs recommend `acceptedCountLimit` of 1 for pure Late Acceptance.

### Tabu Search

Prevents recently visited solutions from being revisited. The entity tabu variant is recommended for timetabling (makes recently moved lessons tabu).

```xml
<localSearch>
  <acceptor>
    <entityTabuSize>7</entityTabuSize>
  </acceptor>
  <forager>
    <acceptedCountLimit>1000</acceptedCountLimit>
  </forager>
</localSearch>
```

**Critical**: Tabu Search requires a **high** `acceptedCountLimit` (1000+) because it evaluates many moves per step. This is the opposite of Late Acceptance which works with low values (1-4).

Alternative: use `entityTabuRatio` instead of a fixed size to auto-scale:

```xml
<acceptor>
  <entityTabuRatio>0.02</entityTabuRatio>  <!-- 2% of entities -->
</acceptor>
```

### Late Acceptance + Tabu (Combined - Currently Used)

The Timefold docs explicitly recommend this "Late Acceptance salted with a bit of Tabu" combination:

```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

**Important**: When combining, use a **lower** tabu size than pure Tabu Search, and keep `acceptedCountLimit` low (aligned with Late Acceptance).

### Step Counting Hill Climbing (SCHC)

Academically proven to be one of the best algorithms specifically for timetabling (Bykov & Petrovic, 2016). It keeps a threshold score for a fixed number of steps, accepting moves that don't worsen below that threshold.

```xml
<localSearch>
  <acceptor>
    <stepCountingHillClimbingSize>400</stepCountingHillClimbingSize>
  </acceptor>
  <forager>
    <acceptedCountLimit>1</acceptedCountLimit>
  </forager>
</localSearch>
```

### Great Deluge

Uses a "water level" that gradually rises, accepting worse solutions early and becoming more restrictive over time:

```xml
<localSearch>
  <acceptor>
    <greatDelugeWaterLevelIncrementRatio>0.00000005</greatDelugeWaterLevelIncrementRatio>
  </acceptor>
  <forager>
    <acceptedCountLimit>1</acceptedCountLimit>
  </forager>
</localSearch>
```

### Simulated Annealing + Tabu (Combined)

```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <simulatedAnnealingStartingTemperature>2hard/100soft</simulatedAnnealingStartingTemperature>
</acceptor>
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

**Warning**: Simulated Annealing is time-gradient dependent, so different run times produce fundamentally different behavior. This hurts reproducibility.

---

## 3. Move Selectors

### Move Types for Timetabling

| Move Type | Description | Benefit for Timetabling |
|-----------|-------------|-------------------------|
| `changeMoveSelector` | Changes one entity's variable | **Essential** - basic building block |
| `swapMoveSelector` | Swaps two entities' variables | **Essential** - efficient timeslot exchanges |
| `pillarChangeMoveSelector` | Moves a group of entities sharing a value | **Critical for compactness** - moves all of a student group's lessons together |
| `pillarSwapMoveSelector` | Swaps two groups sharing values | **Critical** - swaps entire blocks between groups |

### Why Pillar Moves Matter for Student Group Compactness

A pillar is a set of planning entities that share the same planning value(s). For timetabling, a pillar might be "all lessons for StudentGroup-3A on Monday at 08:00-09:00". When you use `pillarChangeMoveSelector`, the solver can move **all** of StudentGroup-3A's Monday morning lessons to Tuesday morning in a single move. Without pillar moves, the solver would have to move them one at a time, and the intermediate states (half-moved) would have terrible scores, creating a local optimum trap.

### Current Configuration (Good)

```xml
<unionMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
  <swapMoveSelector/>
  <pillarChangeMoveSelector>
    <pillarSelector>
      <minimumSubPillarSize>2</minimumSubPillarSize>
      <maximumSubPillarSize>5</maximumSubPillarSize>
    </pillarSelector>
    <valueSelector variableName="timeslot"/>
  </pillarChangeMoveSelector>
  <pillarSwapMoveSelector>
    <pillarSelector>
      <minimumSubPillarSize>2</minimumSubPillarSize>
      <maximumSubPillarSize>5</maximumSubPillarSize>
    </pillarSelector>
  </pillarSwapMoveSelector>
</unionMoveSelector>
```

### Recommended Improvements

#### 1. Add Variable-Specific Swap Moves

The current `swapMoveSelector` swaps ALL variables (both timeslot and room). Adding variable-specific swaps can be more efficient:

```xml
<swapMoveSelector>
  <variableNameInclude>timeslot</variableNameInclude>
</swapMoveSelector>
<swapMoveSelector>
  <variableNameInclude>room</variableNameInclude>
</swapMoveSelector>
```

This lets the solver swap only timeslots (keeping rooms) or only rooms (keeping timeslots), which is often more productive than swapping everything.

#### 2. Add Sequential Sub-Pillars

For student group compactness, sequential sub-pillars ensure that only contiguous blocks are moved together. This requires the `Lesson` entity to implement `Comparable` (or provide a `Comparator` class):

```xml
<pillarChangeMoveSelector>
  <pillarSelector>
    <subPillarType>SEQUENCE</subPillarType>
    <subPillarSequenceComparatorClass>
      com.school.planner.domain.LessonTimeslotComparator
    </subPillarSequenceComparatorClass>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>6</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="timeslot"/>
</pillarChangeMoveSelector>
```

Where `LessonTimeslotComparator` sorts by `timeslot.startDateTime` to define what "sequential" means.

#### 3. Weight Pillar Moves Higher

Use `fixedProbabilityWeight` to make pillar moves more frequent, since they're the key to achieving compactness:

```xml
<unionMoveSelector>
  <changeMoveSelector>
    <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
  <swapMoveSelector>
    <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
  </swapMoveSelector>
  <pillarChangeMoveSelector>
    <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
    <!-- ... -->
  </pillarChangeMoveSelector>
  <pillarSwapMoveSelector>
    <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
    <!-- ... -->
  </pillarSwapMoveSelector>
</unionMoveSelector>
```

---

## 4. Multi-Phase Strategies

### The Timefold Multi-Phase Recommendation

The official docs recommend this multi-algorithm sequence:

1. **First Fit Decreasing** (construction heuristic)
2. **Late Acceptance** (relatively long time - broad exploration)
3. **Tabu Search** (relatively short time - intensive refinement)

This works because Late Acceptance is good at broad exploration (finding promising regions of the search space), while Tabu Search excels at intensive local refinement (polishing a good solution).

### Multi-Phase XML Configuration

```xml
<solver>
  <!-- Phase 1: Construction -->
  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <!-- Phase 2: Late Acceptance - broad exploration -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phase 3: Tabu Search - intensive refinement -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
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
</solver>
```

### Three-Phase Variant with SCHC for Timetabling

Given the academic evidence for Step Counting Hill Climbing in timetabling:

```xml
<solver>
  <!-- Phase 1: Construction -->
  <constructionHeuristic>
    <constructionHeuristicType>STRONGEST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <!-- Phase 2: SCHC - proven for timetabling -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <!-- ... move selectors ... -->
    </unionMoveSelector>
    <acceptor>
      <stepCountingHillClimbingSize>400</stepCountingHillClimbingSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phase 3: Late Acceptance + Tabu for final polish -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>180</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <!-- ... move selectors ... -->
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>
</solver>
```

---

## 5. Termination Configuration

### Available Strategies

| Strategy | XML Element | Behavior |
|----------|-------------|----------|
| Time limit | `<minutesSpentLimit>5</minutesSpentLimit>` | Hard stop after N minutes total |
| Unimproved time | `<unimprovedSecondsSpentLimit>300</unimprovedSecondsSpentLimit>` | Stop if no improvement for N seconds |
| Best score | `<bestScoreLimit>0hard/0medium/-100soft</bestScoreLimit>` | Stop when score reaches target |
| Step count | `<stepCountLimit>1000</stepCountLimit>` | Stop after N steps |
| Unimproved + threshold | `<unimprovedSecondsSpentLimit>30</unimprovedSecondsSpentLimit>` + `<unimprovedScoreDifferenceThreshold>0hard/0medium/100soft</unimprovedScoreDifferenceThreshold>` | Stop if improvement is below threshold |
| Diminished returns | `quarkus.timefold.solver.termination.diminished-returns.*` | Stop when improvements slow down (Quarkus/Spring property only) |

### Combining Terminations

Termination conditions within the same `<termination>` block are OR-combined (any one triggers). You can nest them for AND logic. For multi-phase, each phase has its own termination, plus there is a solver-level termination.

### Recommendations for Timetabling

**Per-phase termination** is better than a single global termination because different phases have different convergence characteristics:

```xml
<solver>
  <!-- Global hard limit (safety net) -->
  <termination>
    <minutesSpentLimit>10</minutesSpentLimit>
  </termination>

  <!-- Construction: auto-terminates when all entities assigned -->
  <constructionHeuristic>
    <!-- No termination needed - auto-terminates -->
  </constructionHeuristic>

  <!-- Phase 2: Late Acceptance - broad exploration -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <!-- ... -->
  </localSearch>

  <!-- Phase 3: Tabu Search - refinement -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
    </termination>
    <!-- ... -->
  </localSearch>
</solver>
```

**Key insight**: `unimprovedSecondsSpentLimit` only counts time during local search, not construction heuristics. The timer resets to zero when a new local search phase starts.

### Diminished Returns (Newer Feature)

Available through Quarkus/Spring properties:

```properties
quarkus.timefold.solver.termination.diminished-returns.sliding-window-duration=30s
quarkus.timefold.solver.termination.diminished-returns.minimum-improvement-ratio=0.25
```

This terminates when the score improvement in the last 30 seconds is less than 25% of the improvement seen in the first 30 seconds. This is more adaptive than a fixed `unimprovedSecondsSpentLimit`.

---

## 6. Score Level Weighting

### Available Score Types

| Type | Levels | When to Use |
|------|--------|-------------|
| `HardSoftScore` | 2 (hard, soft) | Simple problems with feasibility + optimization |
| `HardMediumSoftScore` | 3 (hard, medium, soft) | **Current choice** - good for separating "must-have" from "nice-to-have" |
| `HardSoftLongScore` | 2 (long values) | When int overflow is possible |
| `BendableScore` | N configurable | When you need 4+ priority levels |

### Current Score Usage

The project already uses `HardMediumSoftScore` with this distribution:

- **Hard**: Teacher conflict, room conflict, student group conflict, stage blackout, teacher unavailability, school holiday, max daily hours
- **Medium**: Unassigned room, room suitability
- **Soft**: Teacher gaps, student group gaps, lab contiguity, subject distribution, teacher preference, no consecutive same-subject

### The Compactness Problem

Currently, `minimizeStudentGroupGaps` uses `HardMediumSoftScore.ONE_SOFT` (weight = 1 soft), the same as all other soft constraints. This means a 60-minute gap in a student group's schedule is penalized the same as 60 "units" of any other soft constraint violation. To prioritize compactness:

#### Option A: Increase Soft Weight (Simple)

Multiply the student group gap penalty by a higher weight:

```java
// Before: weight 1 per gap-minute
.penalize(HardMediumSoftScore.ONE_SOFT,
    (group, date, lessons) -> calculateGapMinutes(lessons))

// After: weight 10 per gap-minute — 10x more important than other soft constraints
.penalize(HardMediumSoftScore.ofSoft(10),
    (group, date, lessons) -> calculateGapMinutes(lessons))
```

#### Option B: Promote to Medium (Stronger)

Move student group gaps to the medium level, making them always take priority over other soft constraints regardless of weight:

```java
// Student group gaps as MEDIUM constraint
.penalize(HardMediumSoftScore.ONE_MEDIUM,
    (group, date, lessons) -> calculateGapMinutes(lessons))
```

This is a strong choice if you truly want "no gaps for students" to dominate all other soft preferences. However, it means even a 1-minute student gap is worse than any amount of teacher gap, subject distribution issues, etc.

#### Option C: Use BendableScore (Most Flexible)

If you need fine-grained priority levels, use `BendableScore` with more soft levels:

```java
@PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 3)
private BendableScore score;
```

This gives you: hard (1 level) + 3 soft levels for tiered priority. For example:
- Soft level 0: Student group compactness
- Soft level 1: Lab block contiguity
- Soft level 2: Teacher gaps, subject distribution, preferences

#### Recommended Approach

**Option A with aggressive weighting** is the most practical:

```java
// Student group gaps: weight 10 (10x other soft constraints)
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ofSoft(10),
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Minimize student group gaps");
}

// Lab contiguity: weight 5 (5x base soft constraints)
Constraint labBlockContiguity(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.isLab() && lesson.getStudentGroup() != null)
            .groupBy(...)
            .penalize(HardMediumSoftScore.ofSoft(5),
                    (key, date, lessons) -> { ... })
            .asConstraint("Lab block contiguity");
}

// Other soft constraints: weight 1 (baseline)
// minimizeTeacherGaps, subjectDistribution, teacherPreference, noConsecutiveSameSubject
```

---

## 7. Constraint Weight Configuration

### Modern Approach: ConstraintWeightOverrides (Recommended)

As of Timefold 1.13+, `@ConstraintConfiguration` is deprecated. The new approach uses `ConstraintWeightOverrides`:

#### Step 1: Define default weights in ConstraintProvider

```java
public class SchoolScheduleConstraintProvider implements ConstraintProvider {

    Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
                .groupBy(Lesson::getStudentGroup,
                        lesson -> lesson.timeslot.getDate(),
                        toList())
                .penalize(HardMediumSoftScore.ofSoft(10),  // Default weight
                        (group, date, lessons) -> calculateGapMinutes(lessons))
                .asConstraint("Minimize student group gaps");
    }

    Constraint minimizeTeacherGaps(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Lesson.class)
                .groupBy(Lesson::getTeacher,
                        lesson -> lesson.timeslot.getDate(),
                        toList())
                .penalize(HardMediumSoftScore.ONE_SOFT,  // Default weight
                        (teacher, date, lessons) -> calculateGapMinutes(lessons))
                .asConstraint("Minimize teacher gaps");
    }
    // ... other constraints with default weights ...
}
```

#### Step 2: Add ConstraintWeightOverrides to PlanningSolution

```java
@PlanningSolution
public class SchoolSchedule {

    private ConstraintWeightOverrides<HardMediumSoftScore> constraintWeightOverrides;

    // ... existing fields ...

    public ConstraintWeightOverrides<HardMediumSoftScore> getConstraintWeightOverrides() {
        return constraintWeightOverrides;
    }

    public void setConstraintWeightOverrides(
            ConstraintWeightOverrides<HardMediumSoftScore> constraintWeightOverrides) {
        this.constraintWeightOverrides = constraintWeightOverrides;
    }
}
```

#### Step 3: Override weights at runtime

```java
// When building the problem instance, override specific weights:
SchoolSchedule schedule = new SchoolSchedule(timeslots, rooms, ...);

schedule.setConstraintWeightOverrides(
    ConstraintWeightOverrides.of(Map.of(
        "Minimize student group gaps", HardMediumSoftScore.ofSoft(20),
        "Minimize teacher gaps", HardMediumSoftScore.ofSoft(1),
        "Subject distribution", HardMediumSoftScore.ofSoft(2),
        "Lab block contiguity", HardMediumSoftScore.ofSoft(8),
        "Teacher preference", HardMediumSoftScore.ofSoft(1),
        "No consecutive same-subject", HardMediumSoftScore.ofSoft(1)
    ))
);
```

**Key advantage**: Constraints with zero weight are automatically disabled and impose no performance penalty. This lets you effectively turn constraints on/off from the UI.

### Legacy Approach: @ConstraintConfiguration (Deprecated but Still Works)

```java
@ConstraintConfiguration(constraintPackage = "com.school.planner.solver")
public class ScheduleConstraintConfiguration {

    @ConstraintWeight("Minimize student group gaps")
    private HardMediumSoftScore studentGroupGaps = HardMediumSoftScore.ofSoft(10);

    @ConstraintWeight("Minimize teacher gaps")
    private HardMediumSoftScore teacherGaps = HardMediumSoftScore.ONE_SOFT;

    @ConstraintWeight("Lab block contiguity")
    private HardMediumSoftScore labBlock = HardMediumSoftScore.ofSoft(5);

    @ConstraintWeight("Subject distribution")
    private HardMediumSoftScore subjectDist = HardMediumSoftScore.ONE_SOFT;

    @ConstraintWeight("Teacher preference")
    private HardMediumSoftScore teacherPref = HardMediumSoftScore.ONE_SOFT;

    @ConstraintWeight("No consecutive same-subject")
    private HardMediumSoftScore noConsecutive = HardMediumSoftScore.ONE_SOFT;

    // Getters, setters, no-arg constructor...
}
```

Then in ConstraintProvider, use `penalizeConfigurable()`:

```java
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalizeConfigurable(
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Minimize student group gaps");
}
```

And add to the PlanningSolution:

```java
@PlanningSolution
public class SchoolSchedule {
    @ConstraintConfigurationProvider
    private ScheduleConstraintConfiguration constraintConfiguration;
    // ...
}
```

---

## 8. Analysis of Current Configuration

### Current `solverConfig.xml`

```xml
<solver xmlns="https://timefold.ai/xsd/solver">
  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>300</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>4</acceptedCountLimit>
    </forager>
  </localSearch>
</solver>
```

### Issues Identified

1. **`acceptedCountLimit` of 4 is a compromise value**: For pure Late Acceptance, 1 is recommended. For pure Tabu Search, 1000+ is recommended. The value 4 is neither optimal for either algorithm. Since the combined acceptor is "Late Acceptance salted with Tabu," 1 is the better choice.

2. **Single local search phase**: No multi-phase strategy. The solver cannot benefit from broad exploration followed by intensive refinement.

3. **Student group gap weight too low**: `HardMediumSoftScore.ONE_SOFT` gives student group gaps the same priority as every other soft constraint. A 60-minute gap in a student's day costs the same as one subject distribution violation.

4. **No variable-specific swap moves**: The `swapMoveSelector` swaps ALL variables. Adding variable-specific swaps (timeslot-only, room-only) increases the neighborhood diversity.

5. **No probability weighting**: All move selectors have equal selection probability. Pillar moves should be weighted higher for compactness.

6. **Construction heuristic could try STRONGEST_FIT_DECREASING**: For compactness goals, strongest fit naturally clusters lessons together.

---

## 9. Recommended Configurations

### Configuration A: Conservative Improvement (Low Risk)

Minimal changes to current config with immediate benefit:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://timefold.ai/xsd/solver">
  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>300</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <!-- Changed from 4 to 1 for Late Acceptance + Tabu combo -->
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>
</solver>
```

**Plus** in `SchoolScheduleConstraintProvider.java`, change:

```java
// Minimize student group gaps: increase weight from 1 to 10
.penalize(HardMediumSoftScore.ofSoft(10),
    (group, date, lessons) -> calculateGapMinutes(lessons))

// Lab block contiguity: increase weight from 1 to 5
.penalize(HardMediumSoftScore.ofSoft(5),
    (key, date, lessons) -> { ... })
```

---

### Configuration B: Multi-Phase with Weighted Moves (Recommended)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://timefold.ai/xsd/solver">

  <!-- Phase 1: Construction - STRONGEST_FIT for compactness -->
  <constructionHeuristic>
    <constructionHeuristicType>STRONGEST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <!-- Phase 2: Late Acceptance + Tabu - broad exploration (longer) -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>180</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector>
        <fixedProbabilityWeight>2.0</fixedProbabilityWeight>
      </swapMoveSelector>
      <pillarChangeMoveSelector>
        <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <fixedProbabilityWeight>3.0</fixedProbabilityWeight>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phase 3: Tabu Search - intensive refinement (shorter) -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
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
</solver>
```

---

### Configuration C: Aggressive Timetabling-Optimized (For Benchmarking)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://timefold.ai/xsd/solver">

  <!-- Multi-threaded move evaluation -->
  <moveThreadCount>AUTO</moveThreadCount>

  <!-- Phase 1: Construction -->
  <constructionHeuristic>
    <constructionHeuristicType>STRONGEST_FIT_DECREASING</constructionHeuristicType>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </constructionHeuristic>

  <!-- Phase 2: SCHC - best academic results for timetabling -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <variableNameInclude>timeslot</variableNameInclude>
      </swapMoveSelector>
      <swapMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
        <variableNameInclude>room</variableNameInclude>
      </swapMoveSelector>
      <swapMoveSelector>
        <fixedProbabilityWeight>1.0</fixedProbabilityWeight>
      </swapMoveSelector>
      <pillarChangeMoveSelector>
        <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>6</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <fixedProbabilityWeight>4.0</fixedProbabilityWeight>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>6</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <stepCountingHillClimbingSize>400</stepCountingHillClimbingSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phase 3: Late Acceptance - broad escape from local optima -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
      <pillarChangeMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
        <valueSelector variableName="timeslot"/>
      </pillarChangeMoveSelector>
      <pillarSwapMoveSelector>
        <pillarSelector>
          <minimumSubPillarSize>2</minimumSubPillarSize>
          <maximumSubPillarSize>5</maximumSubPillarSize>
        </pillarSelector>
      </pillarSwapMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>400</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phase 4: Tabu Search - final intensive polish -->
  <localSearch>
    <termination>
      <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
      <changeMoveSelector>
        <valueSelector variableName="room"/>
      </changeMoveSelector>
      <swapMoveSelector/>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>7</entityTabuSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1000</acceptedCountLimit>
    </forager>
  </localSearch>
</solver>
```

---

### Constraint Weight Recommendations

For all configurations, update `SchoolScheduleConstraintProvider.java`:

```java
// Student group gaps: HIGHEST soft priority for compactness
.penalize(HardMediumSoftScore.ofSoft(10),
    (group, date, lessons) -> calculateGapMinutes(lessons))
.asConstraint("Minimize student group gaps");

// Lab block contiguity: HIGH priority (labs must be contiguous)
.penalize(HardMediumSoftScore.ofSoft(5),
    (key, date, lessons) -> { ... })
.asConstraint("Lab block contiguity");

// Teacher gaps: MEDIUM priority
.penalize(HardMediumSoftScore.ofSoft(2),
    (teacher, date, lessons) -> calculateGapMinutes(lessons))
.asConstraint("Minimize teacher gaps");

// Subject distribution: MEDIUM priority
.penalize(HardMediumSoftScore.ofSoft(2))
.asConstraint("Subject distribution");

// No consecutive same-subject: LOW priority
.penalize(HardMediumSoftScore.ONE_SOFT)
.asConstraint("No consecutive same-subject");

// Teacher preference: LOW priority
.penalize(HardMediumSoftScore.ONE_SOFT)
.asConstraint("Teacher preference");
```

---

## Appendix: How to Benchmark These Configurations

Use Timefold's built-in benchmarker to compare configurations:

### Add Dependency

```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-benchmark</artifactId>
</dependency>
```

### Benchmark Configuration

Create `src/main/resources/solverBenchmarkConfig.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plannerBenchmark xmlns="https://timefold.ai/xsd/benchmark">
  <benchmarkDirectory>local/benchmarkReport</benchmarkDirectory>

  <inheritedSolverBenchmark>
    <solver>
      <solutionClass>com.school.planner.domain.SchoolSchedule</solutionClass>
      <entityClass>com.school.planner.domain.Lesson</entityClass>
      <scoreDirectorFactory>
        <constraintProviderClass>
          com.school.planner.solver.SchoolScheduleConstraintProvider
        </constraintProviderClass>
      </scoreDirectorFactory>
      <termination>
        <minutesSpentLimit>5</minutesSpentLimit>
      </termination>
    </solver>
  </inheritedSolverBenchmark>

  <solverBenchmark>
    <name>Current Config (LA+Tabu)</name>
    <solver>
      <constructionHeuristic>
        <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
      </constructionHeuristic>
      <localSearch>
        <acceptor>
          <entityTabuSize>5</entityTabuSize>
          <lateAcceptanceSize>400</lateAcceptanceSize>
        </acceptor>
        <forager>
          <acceptedCountLimit>4</acceptedCountLimit>
        </forager>
      </localSearch>
    </solver>
  </solverBenchmark>

  <solverBenchmark>
    <name>Config A (Fixed acceptedCount)</name>
    <solver>
      <constructionHeuristic>
        <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
      </constructionHeuristic>
      <localSearch>
        <acceptor>
          <entityTabuSize>5</entityTabuSize>
          <lateAcceptanceSize>400</lateAcceptanceSize>
        </acceptor>
        <forager>
          <acceptedCountLimit>1</acceptedCountLimit>
        </forager>
      </localSearch>
    </solver>
  </solverBenchmark>

  <solverBenchmark>
    <name>Config B (Multi-phase LA then TS)</name>
    <solver>
      <!-- ... Configuration B from above ... -->
    </solver>
  </solverBenchmark>

  <solverBenchmark>
    <name>Config C (SCHC + LA + TS)</name>
    <solver>
      <!-- ... Configuration C from above ... -->
    </solver>
  </solverBenchmark>
</plannerBenchmark>
```

### Run the Benchmark

```java
PlannerBenchmarkFactory benchmarkFactory =
    PlannerBenchmarkFactory.createFromXmlResource("solverBenchmarkConfig.xml");
PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark();
benchmark.benchmarkAndShowReportInBrowser();
```

The benchmarker generates an HTML report with:
- Best score over time graphs
- Score comparison statistics
- Move evaluation speed
- Time to best score

---

## Sources

- [Timefold Solver Official Documentation](https://docs.timefold.ai/timefold-solver/latest/)
  - [Construction Heuristics](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics)
  - [Local Search](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search)
  - [Move Selector Reference](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference)
  - [Optimization Algorithms Overview](https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview)
  - [Adjusting Constraints at Runtime](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/constraint-configuration)
  - [Score Types Overview](https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/overview)
  - [Benchmarking and Tweaking](https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking)
  - [Upgrade Guide (ConstraintWeightOverrides)](https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version)
- [Timefold Default Algorithms (Stack Overflow)](https://stackoverflow.com/questions/78221315/)
- [Timefold ConstraintConfiguration Examples (Stack Overflow)](https://stackoverflow.com/questions/78089782/)
- Bykov, Y. & Petrovic, S. (2016). "A step counting hill climbing algorithm applied to university examination timetabling." Journal of Scheduling, 19(4), 479-492.
- Burke, E.K. & Bykov, Y. (2012). "The Late Acceptance Hill-Climbing Heuristic." Technical Report.
