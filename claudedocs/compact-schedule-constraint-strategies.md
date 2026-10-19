# Compact/Contiguous Schedule Constraint Strategies for Timefold

## Research Report: Enforcing Gap-Free Student Group Schedules

**Date**: 2026-02-13
**Context**: School timetabling with Timefold Solver, Quarkus, HardMediumSoftScore
**Problem**: Student groups end up with gaps between lessons when they should have continuous blocks

---

## Current State Analysis

The existing `minimizeStudentGroupGaps` constraint (line 222-229 of `SchoolScheduleConstraintProvider.java`):

```java
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ONE_SOFT,
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Minimize student group gaps");
}
```

**Issues identified**:
1. Uses `ONE_SOFT` (weight 1) -- same weight as all other soft constraints, so gap avoidance competes equally with subject distribution, teacher preference, etc.
2. Uses `toList()` collector which forces sorting and iteration inside the penalty function -- not ideal for incremental performance.
3. Penalizes gap-minutes (duration-based), which is reasonable but may not provide enough gradient for the solver.
4. No complementary reward for compactness.

---

## Strategy 1: Reward-Based vs Penalty-Based Approaches

### Research Findings

Timefold/OptaPlanner explicitly supports both `penalize()` and `reward()` in constraint streams. The documentation states: "A positive soft constraint (or reward) should be fulfilled if possible."

The key insight from optimization theory: **penalties and rewards are mathematically equivalent** when the total potential score is bounded. However, they interact differently with the solver's search:

- **Penalty-based**: The solver sees "this solution has problems to fix." Works well when violations are sparse (few gaps relative to total schedule).
- **Reward-based**: The solver sees "this solution can be improved by adding consecutive lessons." Works well when starting from a dispersed state and trying to cluster.

**For compact schedules, a hybrid approach is most effective**: reward consecutive lesson pairs AND penalize gaps. This creates a stronger gradient because:
- Moving a lesson next to another triggers both a gap-penalty reduction AND a consecutiveness reward.
- The score improvement per move is doubled, making the solver converge faster.

### Implementation: Hybrid Reward + Penalty

```java
// REWARD: For each pair of consecutive lessons (no gap) for a student group on the same day
Constraint rewardConsecutiveStudentLessons(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(Lesson.class,
                    Joiners.equal(Lesson::getStudentGroup),
                    Joiners.equal(lesson -> lesson.timeslot.getDate()))
            .filter((a, b) -> {
                // Check if lessons are directly consecutive (end of one = start of other)
                return a.timeslot.endDateTime.equals(b.timeslot.startDateTime)
                    || b.timeslot.endDateTime.equals(a.timeslot.startDateTime);
            })
            .reward(HardMediumSoftScore.ofSoft(2))
            .asConstraint("Consecutive student group lessons");
}

// PENALTY: Keep the existing gap penalty but with higher weight (see Strategy 3)
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ofSoft(10),
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Minimize student group gaps");
}
```

**Why this works**: When the solver considers moving lesson X to a timeslot adjacent to lesson Y (same student group, same day), the score improvement is:
- Gap penalty removed: +10 * gapMinutes
- Consecutive reward gained: +2
- Net: strong incentive to cluster

The reward gives the solver a positive signal even when there were no gaps to begin with -- it differentiates between "no gaps but scattered across the day" vs "tightly packed block."

### Alternative: Pure Reward with Spread Penalty

The Timefold school timetabling quickstart uses a pure reward approach for teacher time efficiency:

```java
// From Timefold's official quickstart:
Constraint teacherTimeEfficiency(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .join(Lesson.class,
                    Joiners.equal(Lesson::getTeacher),
                    Joiners.equal(lesson -> lesson.timeslot.getDayOfWeek()))
            .filter((lesson1, lesson2) -> {
                Duration between = Duration.between(
                        lesson1.timeslot.getEndTime(),
                        lesson2.timeslot.getStartTime());
                return !between.isNegative() && between.compareTo(Duration.ofMinutes(30)) <= 0;
            })
            .reward(HardSoftScore.ONE_SOFT)
            .asConstraint("Teacher time efficiency");
}
```

You can adapt this pattern for student groups, but note: the pairwise join approach has O(n^2) behavior within each group-day. For student groups with many lessons per day, the `groupBy` + `toList()` approach followed by a linear scan may be more efficient.

---

## Strategy 2: Medium Constraint Level

### Research Findings

`HardMediumSoftScore` is already in use in this project. The score levels have strict lexicographic ordering:

> "Higher medium values take precedence over soft values irrespective of the soft value." -- Timefold docs

This means: a solution with `0hard/-1medium/-0soft` is WORSE than `0hard/0medium/-999999soft`. The medium level creates an absolute priority barrier.

### When to Use Medium for Gap Avoidance

**Use medium if**: gaps in student schedules are considered a scheduling defect that should almost never happen, comparable in importance to room assignment.

**Do not use medium if**: gaps are merely a preference that should be balanced against other preferences (teacher happiness, subject distribution, etc.).

### Implementation

```java
// Promote to MEDIUM -- gaps become more important than ALL soft constraints combined
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Minimize student group gaps");
}
```

### Recommended Approach: Partial Medium

A more nuanced approach: put a **count-based gap penalty** at medium level and a **duration-based refinement** at soft level:

```java
// MEDIUM: Penalize the existence of any gap (count-based)
Constraint studentGroupGapCount(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ONE_MEDIUM,
                    (group, date, lessons) -> countGaps(lessons))
            .asConstraint("Student group gap count");
}

// SOFT: Minimize gap duration (refinement once gaps are reduced)
Constraint studentGroupGapDuration(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .penalize(HardMediumSoftScore.ofSoft(5),
                    (group, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("Student group gap duration");
}
```

This way the solver first eliminates gaps (medium priority), then minimizes their duration (soft), while still being able to trade off gap duration against other soft preferences.

---

## Strategy 3: Scaled Penalty Weights

### Research Findings

From Timefold documentation on constraint weights: "Not all score constraints are equally important. If breaking one constraint is equally bad as breaking another constraint x times, then those two constraints have a different weight."

The conference scheduling example shows weights ranging from `ofSoft(1)` to `ofSoft(100)`:
```java
@ConstraintWeight("Content conflict")
private HardMediumSoftScore contentConflict = HardMediumSoftScore.ofSoft(100);

@ConstraintWeight("Theme track conflict")
private HardMediumSoftScore themeTrackConflict = HardMediumSoftScore.ofSoft(10);
```

### Score Trap Warning

The Timefold docs explicitly warn about "score traps": "A trapped score constraint uses the same weight for different constraint matches, when it could just as easily use a different weight. It effectively lumps its constraint matches together, which creates a flatlined score function."

Your current implementation already avoids this by scaling with gap-minutes. But the base weight of `ONE_SOFT` makes it compete equally with other soft constraints.

### Recommended Weight Configuration

Use `@ConstraintConfiguration` to make weights tunable at runtime:

```java
@ConstraintConfiguration
public class SchoolConstraintConfiguration {

    // Student group compactness -- HIGH priority among soft constraints
    @ConstraintWeight("Minimize student group gaps")
    private HardMediumSoftScore studentGroupGaps = HardMediumSoftScore.ofSoft(50);

    // Teacher gaps -- moderate priority
    @ConstraintWeight("Minimize teacher gaps")
    private HardMediumSoftScore teacherGaps = HardMediumSoftScore.ofSoft(10);

    // Subject distribution -- moderate priority
    @ConstraintWeight("Subject distribution")
    private HardMediumSoftScore subjectDistribution = HardMediumSoftScore.ofSoft(5);

    // Teacher preference -- low priority
    @ConstraintWeight("Teacher preference")
    private HardMediumSoftScore teacherPreference = HardMediumSoftScore.ofSoft(1);

    // No consecutive same subject -- moderate priority
    @ConstraintWeight("No consecutive same-subject")
    private HardMediumSoftScore noConsecutiveSameSubject = HardMediumSoftScore.ofSoft(3);

    // Lab block contiguity -- high priority
    @ConstraintWeight("Lab block contiguity")
    private HardMediumSoftScore labBlockContiguity = HardMediumSoftScore.ofSoft(30);

    // Consecutive student lessons reward
    @ConstraintWeight("Consecutive student group lessons")
    private HardMediumSoftScore consecutiveReward = HardMediumSoftScore.ofSoft(2);

    // ... getters/setters
}
```

Then in the constraint provider, use `penalizeConfigurable()`:

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

### Weight Tuning Guidelines

1. **Start with a 10:1 ratio**: Student group gaps at 50, other soft constraints at 1-5.
2. **Run benchmarks**: Use Timefold benchmarker to compare solutions with different weight ratios.
3. **Check score breakdown**: Use `SolutionManager.analyze()` to see which constraints dominate.
4. **Avoid extreme ratios (>1000:1)**: This effectively creates a hidden score level and may cause the solver to ignore other soft constraints entirely -- at that point, use medium level instead.
5. **The penalty function already scales with minutes**: A 60-minute gap at weight 50 = -3000soft, while a 30-minute gap = -1500soft. This provides good gradient.

---

## Strategy 4: Alternative Constraint Formulations

### 4A: Count-Based Gap Penalty

Instead of penalizing total gap-minutes, penalize the NUMBER of gaps:

```java
private static int countGaps(List<Lesson> lessons) {
    if (lessons.size() < 2) return 0;

    List<Lesson> sorted = lessons.stream()
            .sorted(Comparator.comparing(l -> l.timeslot.startDateTime))
            .toList();

    int gaps = 0;
    for (int i = 1; i < sorted.size(); i++) {
        LocalDateTime prevEnd = sorted.get(i - 1).timeslot.endDateTime;
        LocalDateTime currStart = sorted.get(i).timeslot.startDateTime;
        int gapMinutes = (int) Duration.between(prevEnd, currStart).toMinutes();
        if (gapMinutes > 0) {
            if (isStructuralBreakWithinGap(prevEnd, currStart)) {
                gapMinutes -= STRUCTURAL_BREAK_MINUTES;
            }
            if (gapMinutes > 0) {
                gaps++;
            }
        }
    }
    return gaps;
}
```

**Trade-off**: Count-based treats a 30-minute gap the same as a 120-minute gap. This avoids score traps for very large gaps but loses gradient for distinguishing "slightly spread" vs "very spread."

**Best practice**: Use BOTH -- count at medium level, duration at soft level (see Strategy 2).

### 4B: Spread Penalty (Compactness Measure)

Penalize the "spread" of lessons: the time span from first to last lesson minus the actual lesson time. This is equivalent to "total gap time" but computed differently:

```java
private static int calculateSpread(List<Lesson> lessons) {
    if (lessons.size() < 2) return 0;

    LocalDateTime earliest = lessons.stream()
            .map(l -> l.timeslot.startDateTime)
            .min(Comparator.naturalOrder()).orElseThrow();
    LocalDateTime latest = lessons.stream()
            .map(l -> l.timeslot.endDateTime)
            .max(Comparator.naturalOrder()).orElseThrow();

    int totalSpan = (int) Duration.between(earliest, latest).toMinutes();
    int totalLessonTime = lessons.stream()
            .mapToInt(l -> (int) Duration.between(
                    l.timeslot.startDateTime, l.timeslot.endDateTime).toMinutes())
            .sum();

    // Subtract structural break if it falls within the span
    if (earliest.toLocalTime().isBefore(STRUCTURAL_BREAK_END)
            && latest.toLocalTime().isAfter(STRUCTURAL_BREAK_START)) {
        totalSpan -= STRUCTURAL_BREAK_MINUTES;
    }

    return Math.max(0, totalSpan - totalLessonTime);
}
```

**Advantage**: Captures the overall compactness in a single number. A group with lessons at 8:00, 9:00, 14:30 has a spread of (6.5h - 3h) = 3.5h of "wasted" time, while one at 8:00, 9:00, 10:00 has spread = 0.

### 4C: ITC 2007 Curriculum Compactness (Academic Standard)

The International Timetabling Competition 2007 Track 3 defines "curriculum compactness" as:

> "Lectures belonging to the same curriculum should be adjacent to each other (so in consecutive periods)."

The penalty is: for each lecture, if it has no adjacent lecture (in the same curriculum) in the immediately preceding or following period, it gets a penalty.

```java
// ITC-style: Penalize isolated lessons (lessons with no neighbor from same group)
Constraint isolatedStudentLessons(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.getStudentGroup() != null)
            .ifNotExists(Lesson.class,
                    Joiners.equal(Lesson::getStudentGroup),
                    Joiners.equal(lesson -> lesson.timeslot.getDate()),
                    Joiners.filtering((a, b) ->
                            a.timeslot.endDateTime.equals(b.timeslot.startDateTime)
                         || b.timeslot.endDateTime.equals(a.timeslot.startDateTime)))
            .penalize(HardMediumSoftScore.ofSoft(20))
            .asConstraint("Isolated student lesson");
}
```

**Advantage**: Incremental-friendly. Each lesson is evaluated independently (no `groupBy` + `toList()`). When a single lesson moves, only that lesson and its immediate neighbors need re-evaluation.

**Disadvantage**: Does not penalize the SIZE of gaps. A 30-minute gap and a 2-hour gap both count as one isolation.

### 4D: Cluster Density Approach

Penalize based on how "dense" the lesson cluster is:

```java
// Density = lessons / (maxSlotIndex - minSlotIndex + 1)
// Perfect density = 1.0 (all slots filled), penalize 1 - density
private static int calculateDensityPenalty(List<Lesson> lessons) {
    if (lessons.size() < 2) return 0;

    // Get distinct timeslot indices sorted
    List<Integer> slotIndices = lessons.stream()
            .map(l -> l.timeslot.slotIndex) // assuming timeslots have an ordinal index
            .distinct()
            .sorted()
            .toList();

    int span = slotIndices.get(slotIndices.size() - 1) - slotIndices.get(0) + 1;
    int filled = slotIndices.size();
    int emptySlots = span - filled;

    return emptySlots * 30; // 30 penalty points per empty slot in the span
}
```

### Recommended Combination

Use a multi-pronged approach:

```java
// In defineConstraints():
return new Constraint[] {
    // ... hard constraints ...

    // Medium: eliminate gaps (count-based, high priority)
    studentGroupGapCount(constraintFactory),

    // Soft: reward consecutive lessons (positive gradient)
    rewardConsecutiveStudentLessons(constraintFactory),

    // Soft: penalize isolated lessons (ITC-style, incremental-friendly)
    isolatedStudentLessons(constraintFactory),

    // Soft: minimize gap duration (refinement)
    studentGroupGapDuration(constraintFactory),

    // ... other soft constraints ...
};
```

---

## Strategy 5: Construction Heuristic Configuration

### Research Findings

The construction heuristic assigns initial values to planning variables. A good initial assignment can dramatically reduce the work needed in local search. The current config uses `FIRST_FIT_DECREASING`, which sorts entities by difficulty and assigns each to its best value.

### Entity Difficulty for Compact Schedules

The `LessonDifficultyComparator` should prioritize student groups with MORE lessons per week (harder to schedule compactly). If two lessons belong to the same student group, they should be scheduled early (so subsequent lessons can be placed adjacent).

```java
public class LessonDifficultyComparator implements Comparator<Lesson> {
    @Override
    public int compare(Lesson a, Lesson b) {
        // 1. Groups with more total lessons are harder -- schedule first
        int aGroupSize = countLessonsForGroup(a);
        int bGroupSize = countLessonsForGroup(b);
        int sizeComparison = Integer.compare(bGroupSize, aGroupSize); // descending
        if (sizeComparison != 0) return sizeComparison;

        // 2. Within same group, labs first (they need contiguous blocks)
        int labComparison = Boolean.compare(b.isLab(), a.isLab());
        if (labComparison != 0) return labComparison;

        // 3. Same subject lessons together (helps with contiguity)
        int subjectComparison = nullSafeCompare(a.getSubject(), b.getSubject());
        if (subjectComparison != 0) return subjectComparison;

        return Long.compare(a.id, b.id);
    }
}
```

### ALLOCATE_FROM_POOL Configuration

`ALLOCATE_FROM_POOL` is more versatile than `FIRST_FIT_DECREASING`. It puts all entity-value combinations in a pool and picks the best one each step:

```xml
<constructionHeuristic>
  <constructionHeuristicType>ALLOCATE_FROM_POOL</constructionHeuristicType>
</constructionHeuristic>
```

However, `ALLOCATE_FROM_POOL` is significantly slower because it evaluates ALL entity-value pairs at each step. For large timetabling problems (hundreds of lessons), this can be prohibitively expensive.

### Recommended: FIRST_FIT_DECREASING with Value Strength

A more practical approach: keep `FIRST_FIT_DECREASING` but add a value (timeslot) strength comparator that prefers timeslots adjacent to already-assigned lessons for the same student group:

```xml
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <!-- The entity sorter already uses LessonDifficultyComparator -->
  <!-- The value sorter can use INCREASING_STRENGTH_IF_AVAILABLE -->
</constructionHeuristic>
```

The "strength" of a timeslot for a given lesson depends on the current partial solution (which timeslots are already assigned to the same student group). This is context-dependent and cannot be fully captured by a static comparator.

**Better approach**: Rely on the construction heuristic's natural behavior. `FIRST_FIT_DECREASING` already evaluates each possible timeslot for the current entity and picks the one with the best score. If the gap penalty constraint is weighted high enough (medium level or high soft weight), the construction heuristic will naturally prefer compact placements because they have better scores.

**Key insight**: The construction heuristic respects constraint weights. Increasing the gap penalty weight (Strategy 2 or 3) automatically improves the initial assignment quality.

---

## Strategy 6: Pillar Moves for Student Group Contiguity

### Research Findings

A pillar is a set of entities that share the same planning value. A PillarChangeMove moves the entire pillar to a new value. A sub-pillar is a subset of a pillar.

The existing solver config has pillar moves but **does not use `subPillarType=SEQUENCE`**, which is critical for timetabling:

From Timefold docs:
> "A sequential sub-pillar is a continuous subset of its sorted base pillar. For example, if an employee has shifts on Monday (M), Tuesday (T), and Wednesday (W), they are a pillar and only the following are its sequential sub-pillars: [M], [T], [W], [M, T], [T, W], [M, T, W]. But [M, W] is not a sub-pillar in this case, as there is a gap on Tuesday."

### Problem with Current Config

The current pillar selectors group by shared planning variable values (same timeslot). But for compactness, you want to move **all lessons of a student group on a given day** together. These lessons have DIFFERENT timeslot values (different hours), so they are NOT a pillar by default.

### Custom Move Selector Approach

For student group day-block moves, you need a **custom move** because the standard pillar selectors operate on entities sharing the same value:

```java
/**
 * Custom move: shift all lessons of a student group on a given day
 * by a fixed timeslot offset (e.g., move the entire block one hour earlier).
 */
public class StudentGroupBlockShiftMove extends AbstractMove<SchoolSchedule> {

    private final List<Lesson> blockLessons;
    private final int timeslotOffset; // +1 = one slot later, -1 = one slot earlier

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<SchoolSchedule> scoreDirector) {
        for (Lesson lesson : blockLessons) {
            Timeslot newTimeslot = findTimeslotByOffset(lesson.timeslot, timeslotOffset);
            if (newTimeslot != null) {
                scoreDirector.beforeVariableChanged(lesson, "timeslot");
                lesson.timeslot = newTimeslot;
                scoreDirector.afterVariableChanged(lesson, "timeslot");
            }
        }
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<SchoolSchedule> scoreDirector) {
        // All lessons in the block must have a valid target timeslot
        return blockLessons.stream().allMatch(l ->
                findTimeslotByOffset(l.timeslot, timeslotOffset) != null);
    }

    // ... undo move, equals, hashCode, etc.
}
```

### Better Alternative: Use Existing Pillar Moves with SEQUENCE

While standard pillars group by same value, you can improve the existing pillar configuration to work better for timetabling. Add `subPillarType=SEQUENCE` with a comparator that orders lessons by their timeslot:

```xml
<pillarChangeMoveSelector>
  <pillarSelector>
    <subPillarType>SEQUENCE</subPillarType>
    <subPillarSequenceComparatorClass>
      com.school.planner.solver.LessonTimeslotComparator
    </subPillarSequenceComparatorClass>
    <minimumSubPillarSize>2</minimumSubPillarSize>
    <maximumSubPillarSize>6</maximumSubPillarSize>
  </pillarSelector>
  <valueSelector variableName="timeslot"/>
</pillarChangeMoveSelector>
```

```java
public class LessonTimeslotComparator implements Comparator<Lesson> {
    @Override
    public int compare(Lesson a, Lesson b) {
        if (a.timeslot == null || b.timeslot == null) {
            return Comparator.nullsLast(Comparator.<Timeslot, LocalDateTime>comparing(
                    t -> t.startDateTime)).compare(a.timeslot, b.timeslot);
        }
        return a.timeslot.startDateTime.compareTo(b.timeslot.startDateTime);
    }
}
```

**Note**: Standard pillar moves still require entities in the pillar to share the same value (timeslot). For moving a student group's day-block, this means pillar moves only help when multiple lessons happen to be in the same timeslot (which is a conflict, not a feature).

**Practical conclusion**: For moving contiguous blocks together, a custom `MoveIteratorFactory` is the most effective approach. It generates moves that shift or swap entire day-blocks for a student group. This is an advanced technique but directly addresses the contiguity problem.

---

## Strategy 7: Nearby Selection

### Research Findings

**Nearby selection is an Enterprise Edition feature of Timefold.** It is not available in the Community Edition (open source).

> "Nearby Selection: Get better solutions quicker, especially with spatial problems." -- Timefold Enterprise Edition description

The concept: when the solver considers moving a lesson to a different timeslot, nearby selection biases the selection toward timeslots that are "close" to the lesson's current position or to timeslots already used by the same student group.

### If Using Enterprise Edition

Implement a `NearbyDistanceMeter` for timeslots:

```java
public class TimeslotNearbyDistanceMeter
        implements NearbyDistanceMeter<Lesson, Timeslot> {

    @Override
    public double getNearbyDistance(Lesson origin, Timeslot destination) {
        if (origin.timeslot == null) {
            return Double.MAX_VALUE;
        }
        // Distance = time difference in minutes between origin timeslot and destination
        long minutesDiff = Math.abs(Duration.between(
                origin.timeslot.startDateTime,
                destination.startDateTime).toMinutes());

        // Bonus: reduce distance if destination is on the same day
        // and close to other lessons of the same student group
        if (origin.timeslot.getDate().equals(destination.getDate())) {
            return minutesDiff; // same day = closer
        }
        return minutesDiff + 1440; // different day = add 24 hours penalty
    }
}
```

Configuration:
```xml
<localSearch>
  <unionMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="timeslot">
        <nearbySelection>
          <originMimicSelectorRef>entitySelector</originMimicSelectorRef>
          <nearbyDistanceMeterClass>
            com.school.planner.solver.TimeslotNearbyDistanceMeter
          </nearbyDistanceMeterClass>
          <distributionSizeMaximum>20</distributionSizeMaximum>
        </nearbySelection>
      </valueSelector>
    </changeMoveSelector>
  </unionMoveSelector>
</localSearch>
```

Or using the simple configuration property:
```properties
quarkus.timefold.solver.nearby-distance-meter-class=com.school.planner.solver.TimeslotNearbyDistanceMeter
```

### Community Edition Alternative

Without nearby selection, you can achieve a similar effect with **filtered selection** and **probabilistic selection**:

```xml
<changeMoveSelector>
  <filterClass>com.school.planner.solver.NearbyTimeslotMoveFilter</filterClass>
  <valueSelector variableName="timeslot">
    <cacheType>PHASE</cacheType>
    <selectionOrder>RANDOM</selectionOrder>
  </valueSelector>
</changeMoveSelector>
```

```java
public class NearbyTimeslotMoveFilter implements SelectionFilter<SchoolSchedule, ChangeMove<SchoolSchedule>> {
    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove<SchoolSchedule> move) {
        Lesson lesson = (Lesson) move.getEntity();
        Timeslot newTimeslot = (Timeslot) move.getToPlanningValue();

        if (lesson.timeslot == null || newTimeslot == null) return true;

        // Accept moves to timeslots within 3 hours of current position
        // or on a completely different day (for exploration)
        long minutesDiff = Math.abs(Duration.between(
                lesson.timeslot.startDateTime,
                newTimeslot.startDateTime).toMinutes());

        boolean sameDay = lesson.timeslot.getDate().equals(newTimeslot.getDate());

        // Always accept different-day moves (exploration)
        if (!sameDay) return true;

        // On same day, prefer nearby timeslots (within 3 hours)
        return minutesDiff <= 180;
    }
}
```

**Warning**: Filtered selection reduces the search space. This can improve convergence for compactness but may cause the solver to miss globally better solutions. Use in a `unionMoveSelector` alongside unfiltered selectors.

---

## Strategy 8: Shadow Variables for Gap Tracking

### Research Findings

Shadow variables are planning variables whose value is derived from genuine planning variables. They are updated automatically when genuine variables change.

Using a shadow variable to track gaps could make constraint evaluation O(1) instead of O(n log n) (sorting lessons per group-day). However, there are important caveats.

### Why Shadow Variables Are Challenging Here

The "gap count per student-group-day" depends on ALL lessons for that group on that day. When ANY lesson moves, the gap count for BOTH the old and new day changes. This is a many-to-many dependency that does not map cleanly to the shadow variable model (which is designed for one-to-one or chain-based relationships).

From Timefold docs:
> "Deprecation notice: Using VariableListeners effectively required intimate knowledge of some internal solver concepts. We recommend all users of VariableListeners to migrate to custom shadow variables."

### Alternative: Incremental Score Calculation with Connected Ranges

**The ConnectedRangeChain API is the recommended approach for gap detection in Timefold.** It was specifically designed for this pattern.

From a Timefold GitHub discussion (#1567 -- exactly your problem):

```java
// Using ConnectedRangeChain for student group gap detection
Constraint studentGroupTimeEfficiency(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    ConstraintCollectors.toConnectedTemporalRanges(
                            lesson -> lesson.timeslot.startDateTime,
                            lesson -> lesson.timeslot.endDateTime))
            .flattenLast(ConnectedRangeChain::getGaps)
            .penalize(HardMediumSoftScore.ofSoft(10),
                    (group, gap) -> {
                        int gapMinutes = (int) gap.getLength().toMinutes();
                        // Subtract structural break if applicable
                        // (would need additional logic here)
                        return Math.max(0, gapMinutes);
                    })
            .asConstraint("Student group time efficiency");
}
```

**Advantages of ConnectedRangeChain**:
1. Timefold manages incrementality internally -- no manual sorting or list operations.
2. The `getGaps()` method directly provides gap objects with start/end/length.
3. `getConnectedRanges()` gives you the contiguous blocks of lessons.
4. Can also detect overlaps (useful for conflict constraints).

**Handling the structural break**: The ConnectedRangeChain sees the 14:00-14:30 break as a gap. You need to filter it:

```java
.flattenLast(ConnectedRangeChain::getGaps)
.filter((group, gap) -> {
    // Ignore gaps that are exactly the structural break
    LocalTime gapStart = gap.getStart() /* ... */;
    // Or more robustly: ignore gaps <= 30 minutes that span 14:00
    return !isStructuralBreak(gap);
})
```

### ConnectedRange for Compactness Reward

You can also use the connected ranges to REWARD compact blocks:

```java
Constraint compactStudentBlocks(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getStudentGroup,
                    ConstraintCollectors.toConnectedTemporalRanges(
                            lesson -> lesson.timeslot.startDateTime,
                            lesson -> lesson.timeslot.endDateTime))
            .flattenLast(ConnectedRangeChain::getConnectedRanges)
            .filter((group, range) -> range.getContainedRangeCount() >= 2)
            .reward(HardMediumSoftScore.ofSoft(5),
                    (group, range) ->
                        // Reward proportional to block size (squared for super-linear incentive)
                        range.getContainedRangeCount() * range.getContainedRangeCount())
            .asConstraint("Compact student blocks");
}
```

### Shadow Variable Approach (If Still Desired)

If you want O(1) constraint evaluation, you can maintain a shadow variable that tracks a per-lesson "gap contribution":

```java
@PlanningEntity
public class Lesson extends PanacheEntityBase {
    // ... existing fields ...

    @ShadowVariable(variableListenerClass = GapCountListener.class,
                    sourceVariableName = "timeslot")
    public int gapContribution = 0; // 0 = no gap, >0 = gap minutes after this lesson
}
```

```java
public class GapCountListener implements VariableListener<SchoolSchedule, Lesson> {

    @Override
    public void afterVariableChanged(ScoreDirector<SchoolSchedule> scoreDirector, Lesson lesson) {
        // Recalculate gap contribution for this lesson and its neighbors
        // in the same student group and day
        StudentGroup group = lesson.getStudentGroup();
        if (group == null || lesson.timeslot == null) return;

        LocalDate date = lesson.timeslot.getDate();

        // Find all lessons for this group on this day
        List<Lesson> sameDayLessons = scoreDirector.getWorkingSolution()
                .getLessonList().stream()
                .filter(l -> l.getStudentGroup() != null
                        && l.getStudentGroup().equals(group)
                        && l.timeslot != null
                        && l.timeslot.getDate().equals(date))
                .sorted(Comparator.comparing(l -> l.timeslot.startDateTime))
                .toList();

        // Update gap contribution for each lesson
        for (int i = 0; i < sameDayLessons.size(); i++) {
            Lesson current = sameDayLessons.get(i);
            int gap = 0;
            if (i < sameDayLessons.size() - 1) {
                Lesson next = sameDayLessons.get(i + 1);
                gap = (int) Duration.between(
                        current.timeslot.endDateTime,
                        next.timeslot.startDateTime).toMinutes();
                if (gap > 0 && isStructuralBreakWithinGap(
                        current.timeslot.endDateTime, next.timeslot.startDateTime)) {
                    gap -= 30;
                }
                gap = Math.max(0, gap);
            }
            if (current.gapContribution != gap) {
                scoreDirector.beforeVariableChanged(current, "gapContribution");
                current.gapContribution = gap;
                scoreDirector.afterVariableChanged(current, "gapContribution");
            }
        }
    }

    // ... other lifecycle methods (beforeVariableChanged, etc.)
}
```

Then the constraint becomes trivial:

```java
Constraint minimizeStudentGroupGaps(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> lesson.gapContribution > 0)
            .penalize(HardMediumSoftScore.ofSoft(50),
                    lesson -> lesson.gapContribution)
            .asConstraint("Minimize student group gaps");
}
```

**Warning**: The shadow variable listener above iterates over all lessons for the group-day on every change, which is O(n) per variable change. This is only beneficial if n (lessons per group per day) is small (3-6), which is typical for school timetabling. For large n, the `ConnectedRangeChain` approach is better.

---

## Recommended Implementation Plan

Based on the research, here is the prioritized implementation order:

### Phase 1: Quick Wins (Constraint Changes Only)

1. **Increase weight to `ofSoft(50)`** for student group gaps (currently `ONE_SOFT`).
2. **Add consecutive lesson reward** at `ofSoft(2)` per pair.
3. **Add isolated lesson penalty** (ITC-style) at `ofSoft(20)` per isolated lesson.

Expected impact: Significant improvement. The 50x weight increase alone should make the solver prioritize compactness heavily.

### Phase 2: Structural Improvements

4. **Replace `toList()` approach with `ConnectedRangeChain`** for both teacher and student group gap constraints. This is more idiomatic Timefold and benefits from internal incrementality optimizations.
5. **Split gap constraint into medium (count) and soft (duration)** levels.
6. **Add `@ConstraintConfiguration`** to make weights tunable.

Expected impact: Better solver performance (faster score calculation) and the medium-level gap count provides a strong priority signal.

### Phase 3: Solver Configuration

7. **Add `subPillarType=SEQUENCE`** to pillar move selectors with a timeslot-ordering comparator.
8. **Consider custom move selector** for student group day-block shifts.
9. **If Enterprise Edition**: Enable nearby selection with `TimeslotNearbyDistanceMeter`.

Expected impact: Better local search exploration. The solver will try more "intelligent" moves that preserve or improve contiguity.

### Phase 4: Advanced (If Needed)

10. **Shadow variable for gap contribution** -- only if ConnectedRangeChain performance is insufficient.
11. **Custom construction heuristic** with entity difficulty that prioritizes large student groups.

---

## Benchmarking Recommendations

Use Timefold Benchmarker to compare strategies:

```xml
<plannerBenchmark>
  <benchmarkDirectory>local/benchmarkReport</benchmarkDirectory>

  <solverBenchmark>
    <name>baseline</name>
    <!-- current config -->
  </solverBenchmark>

  <solverBenchmark>
    <name>high-weight</name>
    <!-- weight=50 for student gaps -->
  </solverBenchmark>

  <solverBenchmark>
    <name>medium-level</name>
    <!-- gap count at medium -->
  </solverBenchmark>

  <solverBenchmark>
    <name>hybrid-reward-penalty</name>
    <!-- reward + penalty combo -->
  </solverBenchmark>
</plannerBenchmark>
```

Metrics to compare:
- Total student group gap minutes in final solution
- Number of student-group-days with gaps
- Score calculation speed (moves/second)
- Time to reach gap-free solution (if possible)
- Impact on other constraint satisfaction

---

## Sources

- Timefold Solver 1.30.0 Documentation: Score Calculation, Constraint Configuration, Move Selector Reference, Design Patterns, Enterprise Edition
- Timefold GitHub Discussion #1567: "How to avoid gaps between lessons for the same student group?" (Apr 2025)
- Timefold Blog: "Writing fast constraints with OptaPlanner: the secret recipe" (Lukáš Petrovický)
- Timefold Blog: "Simplify the Shadow Variable Listener Implementation" (Frederico Goncalves)
- OptaPlanner User Guide: Score Trap, Curriculum Compactness (ITC 2007 Track 3)
- ITC 2007 Track 3: Curriculum Based Course Timetabling problem definition
- OptaPlanner/Timefold Configuration: Construction Heuristics, Pillar Move Selectors, Nearby Selection
- Blog: "How we made our client's Timefold solver 17x faster" (Dots and Lines AI)
