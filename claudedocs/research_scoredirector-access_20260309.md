# Research Report: ScoreDirector Access in Timefold/OptaPlanner Move Filters

**Date**: 2026-03-09
**Research Focus**: Accessing ScoreDirector in move filters and custom selectors to calculate constraint impact before accepting moves
**Use Case**: Gap-preserving filter for M13 (vertically compact) constraint

---

## Executive Summary

**Key Finding**: `SelectionFilter` interface **DOES provide** `ScoreDirector` access! The interface signature is:

```java
public interface SelectionFilter<Solution_, T> {
    boolean accept(ScoreDirector<Solution_> scoreDirector, T selection);
}
```

This contradicts the initial assumption that ScoreDirector was inaccessible. Multiple implementation patterns are available for calculating constraint impact before accepting moves.

---

## 1. SelectionFilter Interface - Primary Solution

### Interface Signature

**Source**: Timefold Solver 1.31.0 Documentation

```java
public interface SelectionFilter<Solution_, T> {
    boolean accept(ScoreDirector<Solution_> scoreDirector, T selection);
}
```

### Key Characteristics

- **ScoreDirector parameter**: Full access to score calculation APIs
- **Return value**: `true` = accept move, `false` = reject move
- **Performance**: Rejected moves are **not executed** and score is **not calculated**
- **Stateless**: Should be implemented as stateless for thread safety

### Example Implementation

**Source**: OptaPlanner Documentation

```java
public class DifferentCourseSwapMoveFilter implements SelectionFilter<CourseSchedule, SwapMove> {

    @Override
    public boolean accept(ScoreDirector<CourseSchedule> scoreDirector, SwapMove move) {
        Lecture leftLecture = (Lecture) move.getLeftEntity();
        Lecture rightLecture = (Lecture) move.getRightEntity();
        return !leftLecture.getCourse().equals(rightLecture.getCourse());
    }

}
```

### XML Configuration

```xml
<swapMoveSelector>
  <filterClass>org.optaplanner.examples.curriculumcourse.solver.move.DifferentCourseSwapMoveFilter</filterClass>
</swapMoveSelector>
```

---

## 2. Score Calculation Patterns Within Filters

### Pattern 1: Direct ScoreDirector Usage (Recommended)

The `ScoreDirector` parameter provides full access to:

- `calculateScore()` - Calculate full score (expensive)
- `getWorkingSolution()` - Access current solution state
- `beforeVariableChanged()` / `afterVariableChanged()` - For score delta calculation
- `getConstraintMatchTotals()` - Access individual constraint matches (if enabled)

### Pattern 2: InnerScoreDirector Cast (Advanced)

**Source**: Stack Overflow - "Timefold: 'Construction' of a solution"

```java
public class InitialConstructionPhase implements CustomPhaseCommand<Timetable> {
    @Override
    public void changeWorkingSolution(ScoreDirector<Timetable> scoreDirector) {
        InnerScoreDirector<Timetable, HardSoftScore> innerScoreDirector =
            (InnerScoreDirector<Timetable, HardSoftScore>) scoreDirector;

        scoreDirector.getWorkingSolution().getParticipations().stream()
            .filter(participation -> !participation.getEnrolled())
            .forEach(participation -> {
                HardSoftScore oldScore = innerScoreDirector.calculateScore();
                setParticipationEnrollment(scoreDirector, participation, true);
                HardSoftScore newScore = innerScoreDirector.calculateScore();

                if(oldScore.compareTo(newScore) > 0) {
                    setParticipationEnrollment(scoreDirector, participation, false);
                }
            });
    }
}
```

**Warning**: This uses internal APIs (`InnerScoreDirector`) which may change between versions. Not recommended for production use.

### Pattern 3: Score Analysis with Constraint Matches

**Source**: Timefold Documentation - Understanding the Score

```java
SolutionManager<VehicleRoutePlan, HardSoftLongScore> solutionManager =
    SolutionManager.create(solverFactory);
ScoreExplanation<VehicleRoutePlan, HardSoftLongScore> scoreExplanation =
    solutionManager.explain(vehicleRoutePlan);
Map<Object, Indictment<HardSoftLongScore>> indictmentMap =
    scoreExplanation.getIndictmentMap();

for (Visit visit : vehicleRoutePlan.getVisits()) {
    Indictment<HardSoftLongScore> indictment = indictmentMap.get(visit);
    if (indictment == null) {
        continue;
    }
    // The score impact of that planning entity
    HardSoftLongScore totalScore = indictment.getScore();

    for (ConstraintMatch<HardSoftLongScore> constraintMatch : indictment.getConstraintMatchSet()) {
        String constraintName = constraintMatch.getConstraintName();
        HardSoftLongScore score = constraintMatch.getScore();
        // ...
    }
}
```

**Limitation**: This requires `SolutionManager` which is typically used outside the solver loop, not within filters.

---

## 3. Calculating Constraint Impact Before Accepting Moves

### Option 1: Simulate Move and Calculate Score Delta

```java
public class GapPreservingMoveFilter implements SelectionFilter<SchoolSchedule, ChangeMove> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
        Lesson lesson = (Lesson) move.getEntity();
        Timeslot newTimeslot = (Timeslot) move.getToValue();

        // Store current state
        Timeslot originalTimeslot = lesson.getTimeslot();

        // Simulate move
        scoreDirector.beforeVariableChanged(lesson, "timeslot");
        lesson.setTimeslot(newTimeslot);
        scoreDirector.afterVariableChanged(lesson, "timeslot");

        // Calculate impact of specific constraint (M13)
        HardSoftScore newScore = scoreDirector.calculateScore();

        // Revert move
        scoreDirector.beforeVariableChanged(lesson, "timeslot");
        lesson.setTimeslot(originalTimeslot);
        scoreDirector.afterVariableChanged(lesson, "timeslot");

        // Accept/reject based on M13 impact
        return isM13Acceptable(newScore);
    }

    private boolean isM13Acceptable(HardSoftScore score) {
        // Implement M13 logic here
        return true;
    }
}
```

**Performance Warning**: This approach calls `calculateScore()` twice per filter evaluation. Very expensive!

### Option 2: Heuristic-Based Filtering (Recommended)

Instead of calculating full score, use heuristics based on planning entity properties:

```java
public class GapPreservingMoveFilter implements SelectionFilter<SchoolSchedule, ChangeMove> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
        Lesson lesson = (Lesson) move.getEntity();
        Timeslot newTimeslot = (Timeslot) move.getToValue();
        Timeslot currentTimeslot = lesson.getTimeslot();

        // Heuristic: Calculate gap impact without full score calculation
        int currentGap = calculateGapMinutes(lesson, currentTimeslot);
        int newGap = calculateGapMinutes(lesson, newTimeslot);

        // Accept if gap doesn't increase significantly
        return newGap <= currentGap + GAP_TOLERANCE_MINUTES;
    }

    private int calculateGapMinutes(Lesson lesson, Timeslot timeslot) {
        // Implement gap calculation logic
        // Check adjacent lessons for same student group
        return 0;
    }
}
```

### Option 3: Constraint-Specific Filtering

Leverage the fact that the filter has access to the full solution state:

```java
public class GapPreservingMoveFilter implements SelectionFilter<SchoolSchedule, ChangeMove> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
        SchoolSchedule solution = scoreDirector.getWorkingSolution();
        Lesson lesson = (Lesson) move.getEntity();
        Timeslot newTimeslot = (Timeslot) move.getToValue();

        // Check M13 constraint directly on solution state
        List<Lesson> sameGroupLessons = solution.getLessonList().stream()
            .filter(l -> l.getStudentGroup().equals(lesson.getStudentGroup()))
            .filter(l -> l.getTimeslot().getDate().equals(newTimeslot.getDate()))
            .collect(Collectors.toList());

        // Calculate vertical compactness score
        int compactnessScore = calculateVerticalCompactness(sameGroupLessons, newTimeslot);

        return compactnessScore >= ACCEPT_THRESHOLD;
    }
}
```

---

## 4. Custom Move Selectors and Iterators

### Custom Move Interface

**Source**: Timefold Documentation

```java
public interface Move<Solution_> {
    boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector);
    Move<Solution_> doMove(ScoreDirector<Solution_> scoreDirector);
    // ...
}
```

### Extending AbstractMove

```java
public class TimeslotChangeMove extends AbstractMove<Timetable> {

    private Lesson lesson;
    private Timeslot toTimeslot;

    public TimeslotChangeMove(Lesson lesson, Timeslot toTimeslot) {
        this.lesson = lesson;
        this.toTimeslot = toTimeslot;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Timetable> scoreDirector) {
        scoreDirector.beforeVariableChanged(lesson, "timeslot");
        lesson.setTimeslot(toTimeslot);
        scoreDirector.afterVariableChanged(lesson, "timeslot");
    }

    // isMoveDoable can implement custom filtering logic
    @Override
    public boolean isMoveDoable(ScoreDirector<Timetable> scoreDirector) {
        return !lesson.getTimeslot().equals(toTimeslot);
    }
}
```

### Custom Move Iterator

For more control over move generation:

```java
public class GapPreservingMoveIterator implements MoveIterator<SchoolSchedule> {

    private final MoveIterator<SchoolSchedule> delegate;
    private final ScoreDirector<SchoolSchedule> scoreDirector;

    public GapPreservingMoveIterator(MoveIterator<SchoolSchedule> delegate,
                                     ScoreDirector<SchoolSchedule> scoreDirector) {
        this.delegate = delegate;
        this.scoreDirector = scoreDirector;
    }

    @Override
    public Move<SchoolSchedule> next() {
        while (delegate.hasNext()) {
            Move<SchoolSchedule> move = delegate.next();
            if (isGapPreserving(move)) {
                return move;
            }
        }
        return null;
    }

    private boolean isGapPreserving(Move<SchoolSchedule> move) {
        // Implement gap-preserving logic
        return true;
    }
}
```

---

## 5. PhaseCommand for Advanced Scenarios

### PhaseCommand Interface

**Source**: Timefold Documentation - Custom Solver Phase

```java
public interface PhaseCommand<Solution_> {
    void changeWorkingSolution(ScoreDirector<Solution_> scoreDirector,
                               BooleanSupplier isPhaseTerminated);
}
```

### Configuration in XML

```xml
<customPhase>
  <customPhaseCommandClass>com.example.MyPhaseCommand</customPhaseCommandClass>
</customPhase>
```

### Use Case for Gap-Preserving

A `PhaseCommand` could pre-process the solution to identify "gap-preserving zones" and tag lessons with metadata that filters can use:

```java
public class GapPreservingPreProcessor implements PhaseCommand<SchoolSchedule> {

    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector,
                                     BooleanSupplier isPhaseTerminated) {
        SchoolSchedule solution = scoreDirector.getWorkingSolution();

        // Analyze current schedule
        Map<StudentGroup, List<Lesson>> lessonsByGroup = solution.getLessonList()
            .stream()
            .collect(Collectors.groupingBy(Lesson::getStudentGroup));

        // Tag lessons with gap-preserving metadata
        lessonsByGroup.forEach((group, lessons) -> {
            // Identify compact time blocks
            // Store metadata on entities for filters to use
        });
    }
}
```

---

## 6. Performance Considerations

### Filter Performance Impact

**From Timefold Documentation**:

> "If a move is unaccepted by the filter, it's not executed and the score isn't calculated."

**However**: The filter logic itself runs for EVERY generated move. Expensive filter logic can negate the benefits of filtering.

### Best Practices

1. **Keep filters simple**: Fast boolean checks, no full score calculations
2. **Use heuristics**: Pre-calculate metadata in `PhaseCommand` rather than in filter
3. **Filter at the right level**: Apply filters at the move selector level, not entity/value level
4. **Benchmark**: Measure solver performance with and without filters

### Performance Anti-Pattern

```java
// AVOID: This kills performance
@Override
public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
    // DON'T DO THIS: Full score calculation in filter
    HardSoftScore score = scoreDirector.calculateScore();
    return analyzeFullScore(score);
}
```

### Performance Best Practice

```java
// PREFER: Fast heuristic check
@Override
public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
    Lesson lesson = (Lesson) move.getEntity();
    Timeslot newTimeslot = (Timeslot) move.getToValue();

    // Fast check: Only accept moves within same day for same group
    return lesson.getTimeslot().getDate().equals(newTimeslot.getDate());
}
```

---

## 7. Constraint Match Tracking (Advanced)

### Enable Constraint Match Tracking

**XML Configuration**:

```xml
<solver>
  <scoreDirectorFactory>
    <constraintMatchPolicy>INFORMATIVE</constraintMatchPolicy>
  </scoreDirectorFactory>
  <!-- ... -->
</solver>
```

### Accessing Constraint Matches in Filter

```java
public class ConstraintAwareFilter implements SelectionFilter<SchoolSchedule, ChangeMove> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
        // Access constraint match totals (if enabled)
        if (scoreDirector instanceof ConstraintMatchAwareScoreDirector) {
            ConstraintMatchAwareScoreDirector<SchoolScore> constraintAware =
                (ConstraintMatchAwareScoreDirector<SchoolScore>) scoreDirector;

            Collection<ConstraintMatchTotal<SchoolScore>> constraintMatches =
                constraintAware.getConstraintMatchTotals();

            // Find M13 constraint impact
            for (ConstraintMatchTotal<SchoolScore> total : constraintMatches) {
                if (total.getConstraintName().equals("M13: Vertically compact")) {
                    SchoolScore m13Score = total.getScore();
                    return isM13Acceptable(m13Score);
                }
            }
        }

        // Fallback if constraint tracking not enabled
        return true;
    }
}
```

**Warning**: `ConstraintMatchAwareScoreDirector` may be internal API. Use with caution.

---

## 8. Recommended Approach for Gap-Preserving Filter

### Strategy: Heuristic-Based with Solution State Access

```java
public class GapPreservingMoveFilter implements SelectionFilter<SchoolSchedule, ChangeMove> {

    private static final int MAX_GAP_INCREASE = 30; // minutes

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, ChangeMove move) {
        Lesson lesson = (Lesson) move.getEntity();
        Timeslot newTimeslot = (Timeslot) move.getToValue();
        Timeslot currentTimeslot = lesson.getTimeslot();

        // Fast rejection: Same timeslot
        if (currentTimeslot.equals(newTimeslot)) {
            return false;
        }

        // Fast rejection: Different day (gap doesn't apply)
        if (!currentTimeslot.getDate().equals(newTimeslot.getDate())) {
            return true; // Different day, gap doesn't matter
        }

        // Calculate gap impact for same student group on same day
        int currentGapPenalty = calculateDailyGapPenalty(scoreDirector, lesson, currentTimeslot);
        int newGapPenalty = calculateDailyGapPenalty(scoreDirector, lesson, newTimeslot);

        // Accept if gap penalty doesn't increase significantly
        return (newGapPenalty - currentGapPenalty) <= MAX_GAP_INCREASE;
    }

    private int calculateDailyGapPenalty(ScoreDirector<SchoolSchedule> scoreDirector,
                                        Lesson lesson, Timeslot targetTimeslot) {
        SchoolSchedule solution = scoreDirector.getWorkingSolution();
        LocalDate date = targetTimeslot.getDate();
        StudentGroup group = lesson.getStudentGroup();

        // Get all lessons for this group on this day
        List<Lesson> dailyLessons = solution.getLessonList().stream()
            .filter(l -> l.getStudentGroup().equals(group))
            .filter(l -> l.getTimeslot() != null)
            .filter(l -> l.getTimeslot().getDate().equals(date))
            .sorted(Comparator.comparing(l -> l.getTimeslot().getStartDateTime()))
            .collect(Collectors.toList());

        // Calculate total gap minutes
        int totalGap = 0;
        for (int i = 0; i < dailyLessons.size() - 1; i++) {
            Lesson current = dailyLessons.get(i);
            Lesson next = dailyLessons.get(i + 1);
            int gapMinutes = calculateGapMinutes(current, next);
            totalGap += gapMinutes;
        }

        return totalGap;
    }

    private int calculateGapMinutes(Lesson first, Lesson second) {
        LocalTime firstEnd = first.getTimeslot().getEndDateTime().toLocalTime();
        LocalTime secondStart = second.getTimeslot().getStartDateTime().toLocalTime();
        return (int) ChronoUnit.MINUTES.between(firstEnd, secondStart);
    }
}
```

### Why This Approach Works

1. **No full score calculation**: Only calculates gap penalty for affected student group/day
2. **Leverages ScoreDirector**: Accesses working solution state via `getWorkingSolution()`
3. **Fast rejection**: Quick checks for common cases (same timeslot, different day)
4. **Targeted calculation**: Only recalculates gaps for affected group, not entire schedule
5. **No internal APIs**: Uses only public `SelectionFilter` and `ScoreDirector` APIs

---

## 9. Alternative: Custom Move with Built-in Filtering

For even more control, implement a custom move that incorporates gap-preserving logic:

```java
public class GapPreservingChangeMove extends AbstractMove<SchoolSchedule> {

    private final Lesson lesson;
    private final Timeslot toTimeslot;

    public GapPreservingChangeMove(Lesson lesson, Timeslot toTimeslot) {
        this.lesson = lesson;
        this.toTimeslot = toTimeslot;
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<SchoolSchedule> scoreDirector) {
        scoreDirector.beforeVariableChanged(lesson, "timeslot");
        lesson.setTimeslot(toTimeslot);
        scoreDirector.afterVariableChanged(lesson, "timeslot");
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<SchoolSchedule> scoreDirector) {
        // Incorporate gap-preserving logic here
        return !lesson.getTimeslot().equals(toTimeslot) &&
               isGapPreserving(scoreDirector, lesson, toTimeslot);
    }

    private boolean isGapPreserving(ScoreDirector<SchoolSchedule> scoreDirector,
                                   Lesson lesson, Timeslot newTimeslot) {
        // Same heuristic logic as filter approach
        // ...
        return true;
    }
}
```

---

## 10. Configuration Recommendations

### For M13 Gap-Preserving Filter

```xml
<!-- In solverConfig.xml -->
<localSearch>
  <changeMoveSelector>
    <valueSelector variableName="timeslot">
      <!-- Apply filter at value selector level -->
      <filterClass>com.school.planner.solver.move.GapPreservingMoveFilter</filterClass>
    </valueSelector>
  </changeMoveSelector>

  <!-- Also apply to other move types -->
  <swapMoveSelector>
    <filterClass>com.school.planner.solver.move.GapPreservingSwapMoveFilter</filterClass>
  </swapMoveSelector>
</localSearch>
```

### Important: Apply to All Move Types

**From Timefold Documentation**:

> "Any built-in hard constraint must probably be filtered on **every move type of every solver phase**."

For M13 (medium constraint), the same principle applies:
- Filter `changeMove` in Local Search
- Filter `swapMove` in Local Search
- Filter `changeMove` in Construction Heuristics (if applicable)
- Filter custom moves (if any)

---

## 11. Testing and Validation

### Unit Test for Filter

```java
@QuarkusTest
public class GapPreservingMoveFilterTest {

    @Test
    public void testAcceptsGapPreservingMove() {
        // Setup test solution
        SchoolSchedule solution = createTestSolution();
        ScoreDirector<SchoolSchedule> scoreDirector =
            ScoreDirectorFactory.create(constraintProvider).buildScoreDirector(solution);

        // Create move that preserves gaps
        Lesson lesson = solution.getLessonList().get(0);
        Timeslot newTimeslot = findGapPreservingTimeslot(lesson);
        ChangeMove move = new ChangeMove(lesson, "timeslot", newTimeslot);

        // Test filter
        GapPreservingMoveFilter filter = new GapPreservingMoveFilter();
        boolean accepted = filter.accept(scoreDirector, move);

        assertTrue(accepted, "Should accept gap-preserving move");
    }

    @Test
    public void testRejectsGapCreatingMove() {
        // Similar test for gap-creating move
        // ...
    }
}
```

### Benchmark Impact

```bash
# Run solver with and without filter
# Compare score calculation speed and final score quality

./scripts/benchmark.sh --with-filter
./scripts/benchmark.sh --without-filter
```

---

## 12. Key Takeaways

1. **SelectionFilter DOES provide ScoreDirector access**: The interface signature includes `ScoreDirector<Solution_> scoreDirector` parameter
2. **Multiple implementation patterns**:
   - Heuristic-based filtering (recommended)
   - Simulate move + calculate score delta (expensive)
   - Custom move with built-in filtering
   - PhaseCommand preprocessing
3. **Performance is critical**: Filters run on EVERY generated move, so keep logic simple
4. **Apply to all move types**: Don't forget swap moves, custom moves, etc.
5. **Avoid internal APIs**: `InnerScoreDirector` cast works but may break between versions
6. **Constraint match tracking**: Can be enabled for detailed analysis, but has overhead

---

## 13. Next Steps for Gap-Preserving Implementation

1. **Implement heuristic-based filter**: Use recommended approach from Section 8
2. **Add to all move selectors**: Change, swap, and custom moves
3. **Benchmark performance**: Measure impact on score calculation speed
4. **Validate quality**: Ensure M13 constraint score improves vs. baseline
5. **Consider custom move**: If filter proves insufficient, implement custom move class

---

## 14. References and Sources

### Official Documentation
- Timefold Solver Optimization Algorithms: https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview
- Score Calculation: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation
- Understanding the Score: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/understanding-the-score
- Performance Tips: https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance

### Stack Overflow
- "Timefold: 'Construction' of a solution when initial solution is already feasible" - InnerScoreDirector example
- "How to get the selected move when the best solution has been updated"
- "Constraining optaplanner's choices based on the result of different constraints"

### OptaPlanner Heritage
- OptaPlanner 8.4.1.Final Documentation (Move and neighborhood selection)
- Constraint match tracking and score explanation

### GitHub Discussions
- Timefold Solver Discussion #869: Dynamic constraint weight calculation
- Move selector configuration examples

---

**Research Completed**: 2026-03-09
**Researcher**: Claude (with exhaustive documentation search)
**Status**: Ready for implementation
