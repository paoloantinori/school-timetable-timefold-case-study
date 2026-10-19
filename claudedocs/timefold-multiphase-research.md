# Timefold Solver: Multi-Phase Optimization, Variable Pinning, and Construction Heuristic Patterns

## Exhaustive Research Report

**Date**: 2026-02-14
**Sources**: Timefold official documentation (v1.30-1.31), GitHub issues, Stack Overflow, OptaPlanner legacy documentation, Timefold blog posts, community discussions.

---

## Table of Contents

1. [Multi-Phase Local Search](#1-multi-phase-local-search)
2. [Construction Heuristics Targeting Specific Variables](#2-construction-heuristics-targeting-specific-variables)
3. [Cartesian Product vs Sequential Variable Assignment](#3-cartesian-product-vs-sequential-variable-assignment)
4. [Variable Pinning at the Phase Level](#4-variable-pinning-at-the-phase-level)
5. [Custom PhaseCommand for Pin/Unpin Between Phases](#5-custom-phasecommand-for-pinunpin-between-phases)
6. [ValueRangeProvider Filtering and Dependencies](#6-valuerangeprovider-filtering-and-dependencies)
7. [School Timetabling Quickstart: Room + Timeslot Approach](#7-school-timetabling-quickstart-room--timeslot-approach)
8. [Recommendations for This Project](#8-recommendations-for-this-project)

---

## 1. Multi-Phase Local Search

### Can different phases optimize different planning variables?

**Yes, but with important caveats.** Timefold Solver supports running multiple solver phases in sequence. Each phase operates on the same solution, and each phase can have its own `MoveSelector` configuration.

**Key documentation excerpt** (Optimization Algorithms Overview):
```xml
<solver>
  ...
  <constructionHeuristic/>
  <localSearch>
    <termination>
      <secondsSpentLimit>90</secondsSpentLimit>
    </termination>
    ...
  </localSearch>
  <localSearch>
    <termination>
      <secondsSpentLimit>60</secondsSpentLimit>
    </termination>
    ...
  </localSearch>
</solver>
```

> "The solver phases are run in the order defined by solver configuration. When the first Phase terminates, the second Phase starts, and so on."

**Restricting moves to specific variables per phase**: You can configure each `<localSearch>` phase with move selectors that only target certain variables. For example:

```xml
<!-- Phase 1: Only optimize timeslot -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <changeMoveSelector>
    <valueSelector variableName="timeslot"/>
  </changeMoveSelector>
</localSearch>

<!-- Phase 2: Only optimize room -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</localSearch>
```

**However, there is no built-in "freeze" mechanism.** If Phase 2 only has `changeMoveSelector` for `room`, it will only generate moves that change rooms -- but the solver does NOT guarantee that a later phase won't undo work from an earlier phase if both phases touch the same variables. Each phase inherits the best solution from the prior phase and can freely modify any variable that its move selectors expose.

### What does "freeze" mean in practice?

The solver does not have a concept of "locking" a variable's value between phases. If a phase's move selectors only target one variable (e.g., `room`), then the other variable (e.g., `timeslot`) is effectively frozen for that phase because no moves can change it. This is the **move selector restriction technique** -- the simplest and most reliable way to focus a phase on specific variables.

### Practical limitation

If Phase 1 only optimizes `timeslot` and Phase 2 only optimizes `room`, the solver cannot make joint moves that change both simultaneously in either phase. This means it cannot escape local optima that require changing both variables together. This is why the typical recommendation is to have at least one phase that can change both variables (using `unionMoveSelector` or `swapMoveSelector`).

---

## 2. Construction Heuristics Targeting Specific Variables

### Can construction heuristics target specific variables only?

**Yes.** Construction heuristics can be configured to assign variables sequentially (one at a time) or via Cartesian product (all at once).

**Sequential variable assignment** (from official docs):
```xml
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <changeMoveSelector>
    <valueSelector variableName="period"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</constructionHeuristic>
```

In this sequential configuration, each variable is assigned in separate steps. The order matters: assign the more constrained variable first (e.g., `period`/`timeslot` before `room`).

> "The order of the variables is important, especially in the sequential technique. In the sequential example above, it's better to select the period first and the room second (instead of the other way around), because there are more hard constraints that do not involve the room, such as no teacher should teach two lectures at the same time."

### Multiple entity classes

For multiple entity classes, run separate construction heuristic phases:
```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector>
      <entityClass>...DogEntity</entityClass>
    </entitySelector>
  </queuedEntityPlacer>
</constructionHeuristic>
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector>
      <entityClass>...CatEntity</entityClass>
    </entitySelector>
  </queuedEntityPlacer>
</constructionHeuristic>
```

### Filtered entity selection in construction heuristics

Entity selectors can be filtered to only process certain entities:
```xml
<constructionHeuristic>
  <queuedEntityPlacer>
    <entitySelector>
      <filterClass>com.example.LongLectureSelectionFilter</filterClass>
    </entitySelector>
  </queuedEntityPlacer>
</constructionHeuristic>
```

---

## 3. Cartesian Product vs Sequential Variable Assignment

### The Two Approaches (Construction Heuristics)

**Cartesian product (default):** All variables of the selected entity are assigned together. Evaluates every combination.

```xml
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <cartesianProductMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="period"/>
    </changeMoveSelector>
  </cartesianProductMoveSelector>
</constructionHeuristic>
```

- With 200 rooms and 40 periods: 8,000 moves per entity
- With 3 variables of 1,000 values each: 1,000,000,000 moves per entity (infeasible)
- Better solution quality, but scales poorly

**Sequential:** One variable is assigned at a time. Scales much better.

```xml
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <changeMoveSelector>
    <valueSelector variableName="period"/>
  </changeMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</constructionHeuristic>
```

- With 200 rooms and 40 periods: 240 moves per entity (40 + 200)
- With 3 variables of 1,000 values each: 3,000 moves per entity
- 300,000x faster than Cartesian for the 3-variable case

**Hybrid approach** (3+ variables):
```xml
<constructionHeuristic>
  <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  <cartesianProductMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="variableA"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="variableB"/>
    </changeMoveSelector>
  </cartesianProductMoveSelector>
  <changeMoveSelector>
    <valueSelector variableName="variableC"/>
  </changeMoveSelector>
</constructionHeuristic>
```

This combines cartesian for two tightly-coupled variables and sequential for the third.

### In Local Search (cartesianProductMoveSelector)

During local search, the `cartesianProductMoveSelector` creates composite moves:

```xml
<localSearch>
  <cartesianProductMoveSelector>
    <changeMoveSelector>
      <entitySelector id="cartesianProductEntitySelector"/>
      <valueSelector variableName="timeslot"/>
    </changeMoveSelector>
    <changeMoveSelector>
      <entitySelector mimicSelectorRef="cartesianProductEntitySelector"/>
      <valueSelector variableName="room"/>
    </changeMoveSelector>
  </cartesianProductMoveSelector>
</localSearch>
```

This uses **mimic selection** to ensure both changes target the same entity. Each composite move changes both timeslot and room of a single entity simultaneously.

---

## 4. Variable Pinning at the Phase Level

### Current state: No per-variable pinning (yet)

**This is a known functionality gap.** Timefold's `@PlanningPin` annotation pins the entire entity (all its planning variables), not individual variables.

```java
@PlanningEntity
public class Lesson {
    @PlanningPin
    public boolean pinned = false;

    @PlanningVariable
    public Timeslot timeslot;

    @PlanningVariable(allowsUnassigned = true)
    public Room room;
}
```

When `pinned = true`, neither `timeslot` nor `room` can be changed by the solver.

### Feature Request: Per-Variable Pinning (GitHub Issue #1321)

**Status**: Open feature request, filed January 2025, labeled "Feature" and "process/needs triage".

**Proposed strawman API**:
```java
@PlanningEntity
class Lesson {
    @PlanningPin(variable = {"room"})
    private boolean roomLocked;

    @PlanningVariable
    private Timeslot timeslot;

    @PlanningVariable
    private Room room;
}
```

**Timefold team response** (triceo, Collaborator):
> "This is indeed a functionality gap - at the moment, I cannot promise if or when we'll get to closing it."

**The challenge** (from the original OptaPlanner issue PLANNER-124):
- Entities would need to be selected, but only in move selectors that actually change one of the pinned planning variables
- Move selectors that change multiple variables must not change pinned planning variables
- This is "not simple" to implement

### Official Workaround: ValueRangeProvider on Entity

The recommended workaround from Christopher Chianelli (Timefold contributor, July 2025):

```java
@PlanningEntity
class Lesson {
    private boolean roomLocked;
    private Timetable timetable;

    @PlanningVariable
    private Timeslot timeslot;

    @PlanningVariable
    private Room room;

    @ValueRangeProvider
    public List<Room> getPossibleRoomList() {
        if (roomLocked) {
            return List.of(room);  // Only the current value
        }
        return timetable.getRooms();
    }
}
```

**Limitation noted by community**: When entities have pre-assigned timeslots that are pinned, the construction heuristic cannot properly initialize the room variable because the entity-level pin blocks all variables. Using the ValueRangeProvider workaround, the construction phase assigns rooms with a restricted range, which can lead to suboptimal room assignments in later phases.

### Alternative: Restrict Moves Per Phase

The most practical approach today is to use per-phase move selector configuration:

```xml
<!-- Phase 1: Optimize both variables freely -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <unionMoveSelector>
    <changeMoveSelector/>
    <swapMoveSelector/>
  </unionMoveSelector>
</localSearch>

<!-- Phase 2: Only optimize room (timeslot effectively frozen) -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>30</unimprovedSecondsSpentLimit>
  </termination>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
</localSearch>
```

In Phase 2, because only `room` change moves are generated, `timeslot` values are de facto frozen. This is NOT true pinning (no entity is excluded from selection), but it achieves the same practical effect for local search phases.

---

## 5. Custom PhaseCommand for Pin/Unpin Between Phases

### PhaseCommand Interface (formerly CustomPhaseCommand)

Timefold provides `PhaseCommand` (renamed from OptaPlanner's `CustomPhaseCommand`) for running custom logic between standard phases:

```java
public interface PhaseCommand<Solution_> {
    void changeWorkingSolution(ScoreDirector<Solution_> scoreDirector,
                                BooleanSupplier isPhaseTerminated);
}
```

**Configuration:**
```xml
<solver>
  <constructionHeuristic/>
  <customPhase>
    <customPhaseCommandClass>com.example.MyPinningPhaseCommand</customPhaseCommandClass>
  </customPhase>
  <localSearch>
    ...
  </localSearch>
</solver>
```

### Can it be used to pin/unpin variables between phases?

**Yes, with the ValueRangeProvider workaround.** You can use a `PhaseCommand` to set flags on entities that control the ValueRangeProvider return values:

```java
public class FreezeTimeslotPhaseCommand implements PhaseCommand<SchoolSchedule> {
    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector,
                                       BooleanSupplier isPhaseTerminated) {
        SchoolSchedule schedule = scoreDirector.getWorkingSolution();
        for (Lesson lesson : schedule.getLessons()) {
            scoreDirector.beforeVariableChanged(lesson, "timeslotLocked");
            lesson.setTimeslotLocked(true);
            scoreDirector.afterVariableChanged(lesson, "timeslotLocked");
        }
    }
}
```

**Critical caveat**: If the changes made in a `PhaseCommand` do not result in a better score, the best solution will not be changed. Setting a boolean flag that does not affect score calculation will have no effect on the working solution seen by the next phase.

**This means**: The flag-based approach only works if `timeslotLocked` is NOT a planning variable and NOT a shadow variable -- it must be a problem fact property that the `ValueRangeProvider` reads. But problem facts should NOT be changed during solving (this corrupts the solver). The documentation explicitly warns:

> "Do not change any of the problem facts in a PhaseCommand, it will corrupt the Solver because any previous score or solution was for a different problem."

### The fundamental problem

`PhaseCommand` cannot safely modify problem fact state to control pinning. And since `@PlanningPin` is entity-level (not variable-level), you cannot toggle per-variable pinning between phases.

### PhaseLifecycleListener

`PhaseLifecycleListener` is an **internal API** (package `org.optaplanner.core.impl.phase.event` / `ai.timefold.solver.core.impl.phase.event`) that provides callbacks:
- `phaseStarted()`
- `phaseEnded()`
- `stepStarted()`
- `stepEnded()`

This is NOT part of the public API and is primarily used for monitoring, not for modifying the solution. Using it to toggle pinning flags would suffer the same problems as `PhaseCommand`.

### Practical recommendation

Instead of trying to pin/unpin variables between phases, **use per-phase move selector configuration** to control which variables each phase can modify. This is the supported, documented approach.

---

## 6. ValueRangeProvider Filtering and Dependencies

### Can value ranges depend on other variables?

**Yes, with important limitations.** `@ValueRangeProvider` can be placed on the planning entity class, giving each entity its own value range:

```java
@PlanningVariable
public Room getRoom() {
    return room;
}

@ValueRangeProvider
public List<Room> getPossibleRoomList() {
    return getCourse().getTeacher().getDepartment().getRoomList();
}
```

From the documentation:
> "Each planning entity has its own value range (a set of possible planning values) for the planning variable. For example, if a teacher can never teach in a room that does not belong to their department, lectures of that teacher can limit their room value range to the rooms of their department."

### Can a ValueRangeProvider depend on another planning variable?

**This is problematic.** The documentation warns:

> "A planning entity should not use other planning entities to determine its value range."
> "None of the current planning variable state in any of the planning entities should be used to compare planning values. During construction heuristics, those variables are likely to be null."

However, an entity-level `@ValueRangeProvider` CAN read the entity's own non-planning properties (problem facts) to filter the range. For example, filtering rooms based on the course's teacher (a problem fact), not based on the currently-assigned timeslot (a planning variable).

### Dynamic value ranges based on planning variable state

A Stack Overflow answer (2018) confirms that placing `@ValueRangeProvider` on a planning entity works for static filtering based on problem facts:

> "You can create a getter in planning entity class to get the value range based on current planning entity instance, filter the scope to reduce the possible values of a planning variable, to reduce the search space."

But another user reports problems with dynamic filtering:
> "I tried to set up a ValueRangeProvider on the ShiftAssignment (the PlanningEntity), which returns a countable value range based on the available times for the currently assigned Employee, but it seems that this is static to the first Employee that was assigned."

**Conclusion**: Entity-level `@ValueRangeProvider` is evaluated per entity but is based on the entity's problem fact state, not on the current state of planning variables. It is NOT re-evaluated every time a planning variable changes. For truly dynamic dependencies, use constraints (score calculation) instead.

### Filtered Selection (Move-Level Filtering)

A more flexible alternative is `SelectionFilter`, applied at the move level:

```java
public class LessonChangeMoveFilter implements SelectionFilter<Timetable, ChangeMove<Timetable>> {
    @Override
    public boolean accept(ScoreDirector<Timetable> scoreDirector, ChangeMove<Timetable> move) {
        Lesson lesson = (Lesson) move.getEntity();
        // Custom logic to reject certain moves
        return someCondition(lesson, move.getToPlanningValue());
    }
}
```

```xml
<changeMoveSelector>
  <filterClass>com.example.LessonChangeMoveFilter</filterClass>
  <valueSelector variableName="timeslot"/>
</changeMoveSelector>
```

> "`SelectionFilter` implementations are expected to be stateless. The solver may choose to reuse them in different contexts."

This can be used in both construction heuristics and local search phases.

---

## 7. School Timetabling Quickstart: Room + Timeslot Approach

### Official Timefold approach: Two separate planning variables

The official Timefold school timetabling quickstart uses **two separate `@PlanningVariable` fields** on a single `Lesson` entity -- NOT a combined/cartesian approach:

```java
@PlanningEntity
public class Lesson {
    @PlanningId
    private String id;
    private String subject;
    private String teacher;
    private String studentGroup;

    @PlanningVariable
    private Timeslot timeslot;

    @PlanningVariable
    private Room room;
}
```

**Solution class:**
```java
@PlanningSolution
public class Timetable {
    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Timeslot> timeslots;

    @ValueRangeProvider
    @ProblemFactCollectionProperty
    private List<Room> rooms;

    @PlanningEntityCollectionProperty
    private List<Lesson> lessons;

    @PlanningScore
    private HardSoftScore score;
}
```

### Why two separate variables?

1. **Simplicity**: The domain naturally separates "when" (timeslot) from "where" (room)
2. **Default move generation**: Timefold auto-generates `ChangeMove` and `SwapMove` for each variable separately, plus combination moves
3. **Construction heuristic flexibility**: Can be assigned together (Cartesian) or separately (sequential)
4. **Constraint clarity**: Room constraints and time constraints are naturally independent

### No combined/cartesian value approach

Timefold does NOT use a combined `TimeSlotRoom` value object. There is no example in the quickstarts or documentation that combines timeslot and room into a single planning variable.

The documentation explicitly advises against unnecessary combined variables:
> "Remove any surplus @PlanningVariable annotations so that they become problem properties. Doing this significantly decreases the search space size and significantly increases solving efficiency."

The advice is to have FEWER planning variables, not to combine them into one.

### Default solver behavior with multiple variables

When no explicit move selector is configured, the default `<localSearch>` unfolds into:
- `ChangeMoveSelector` for `timeslot`
- `ChangeMoveSelector` for `room`
- `SwapMoveSelector` (swaps both variables between two entities)

This means the default behavior already handles both variables in a single phase. The `SwapMoveSelector` is particularly important because it changes both variables atomically for two entities, helping the solver escape local optima that require joint changes.

### The "correlated variables" problem

When two planning variables are semantically dependent (changing one without the other breaks constraints), the generic moves may not be efficient. From a Timefold contributor (Stack Overflow, April 2024):

> "The generic moves Timefold Solver offers are not aware of the fact that these two variables should be changed only together. Correct assignment is then unlikely. In this situation, I would recommend implementing a custom move that contains the logic for correctly assigning both variables."

For school timetabling, `timeslot` and `room` are largely independent (a lesson's valid rooms don't depend on its timeslot), so the separate variable approach works well.

---

## 8. Recommendations for This Project

### Current Configuration Analysis

This project already uses the recommended pattern:
- **Sequential construction heuristic**: `timeslot` first, `room` second (correct ordering)
- **Two local search phases**: Phase 1 (Late Acceptance + Tabu, broad), Phase 2 (Pure Tabu, refinement)
- **Separate `changeMoveSelector` per variable**: Proper `variableName` targeting
- **`@PlanningPin`**: Entity-level pinning already in place
- **Nullable room**: `@PlanningVariable(allowsUnassigned = true)` on room

### If you want to optimize timeslot and room in separate phases

**Approach A: Move selector restriction (recommended, no code changes)**

```xml
<!-- Phase 1: Timeslot-focused with swap support -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>120</unimprovedSecondsSpentLimit>
  </termination>
  <unionMoveSelector>
    <changeMoveSelector>
      <valueSelector variableName="timeslot"/>
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
  ...
</localSearch>

<!-- Phase 2: Room refinement only -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <changeMoveSelector>
    <valueSelector variableName="room"/>
  </changeMoveSelector>
  ...
</localSearch>

<!-- Phase 3: Joint optimization (escape cross-variable optima) -->
<localSearch>
  <termination>
    <unimprovedSecondsSpentLimit>60</unimprovedSecondsSpentLimit>
  </termination>
  <unionMoveSelector>
    <changeMoveSelector/>
    <swapMoveSelector/>
  </unionMoveSelector>
  ...
</localSearch>
```

**Approach B: Per-variable "pinning" via ValueRangeProvider (code changes needed)**

If you need some lessons to have their timeslot frozen while the room is still optimized:

```java
@PlanningEntity
public class Lesson {
    // Remove @PlanningPin if using per-variable control
    public boolean timeslotLocked = false;

    @PlanningVariable
    public Timeslot timeslot;

    @PlanningVariable(allowsUnassigned = true)
    public Room room;

    // Entity-level value range provider for timeslot
    @ValueRangeProvider
    public List<Timeslot> getPossibleTimeslots() {
        if (timeslotLocked && timeslot != null) {
            return List.of(timeslot);
        }
        // Return the full range from the solution
        return schoolSchedule.getTimeslots();
    }
}
```

**Important**: This requires the entity to have a reference to the solution (or its timeslot list). Set this reference before submitting to the solver.

### Approach C: Multiple Solvers (advanced, for true multi-stage planning)

Timefold supports multiple `SolverManager` instances in the same application:

```properties
# Quarkus
quarkus.timefold.solver."timeslotSolver".solver-config-xml=timeslotConfig.xml
quarkus.timefold.solver."roomSolver".solver-config-xml=roomConfig.xml
```

Each solver has its own domain model and planning entity. Stage 1 assigns timeslots, Stage 2 takes the result and assigns rooms. This is true multi-stage planning as described in the documentation:

> "Multi-stage planning leads to suboptimal results. Nevertheless, it might be beneficial in order to simplify the maintenance."

This is generally NOT recommended for school timetabling because timeslot and room constraints are interdependent.

---

## Key Findings Summary

| Question | Answer | Confidence |
|----------|--------|------------|
| Multi-phase local search with different variables? | Yes, via move selector configuration per phase | High |
| "Freeze" a variable in a later phase? | Only by excluding it from move selectors (no true freeze) | High |
| Construction heuristic targeting specific variables? | Yes: sequential or cartesian configuration | High |
| Cartesian product / combined variable pattern? | Documented for CH; NOT recommended as a single combined variable | High |
| Per-variable pinning at phase level? | NOT SUPPORTED (open feature request #1321) | High |
| Custom PhaseCommand for pin/unpin? | Technically possible via ValueRangeProvider hack, but fragile | Medium |
| PhaseLifecycleListener for pinning? | Internal API, NOT recommended for this purpose | High |
| ValueRangeProvider dependent on other variables? | Works for problem facts; NOT for planning variable state | High |
| School timetabling: Room + Timeslot approach? | Two separate @PlanningVariable fields (official pattern) | High |

---

## Sources

### Official Documentation
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/construction-heuristics
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/overview
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search
- https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference
- https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems
- https://docs.timefold.ai/timefold-solver/latest/responding-to-change/responding-to-change
- https://docs.timefold.ai/timefold-solver/latest/design-patterns/design-patterns
- https://docs.timefold.ai/timefold-solver/latest/enterprise-edition/enterprise-edition
- https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/configuration

### GitHub
- https://github.com/TimefoldAI/timefold-solver/issues/1321 (Per-variable pinning feature request)
- https://github.com/TimefoldAI/timefold-quickstarts (Official quickstarts)

### Blog Posts
- https://timefold.ai/blog/continuous-planning-optimization-with-pinning
- https://timefold.ai/blog/multiple-solver-managers
- https://blog.kie.org/2013/12/construction-heuristics-with-multiple-variables-or-multiple-planning-variables.html

### Stack Overflow
- https://stackoverflow.com/questions/78344701/planning-entity-with-multiple-planning-variables
- https://stackoverflow.com/questions/78073693/timefold-ai-partial-pinning
- https://stackoverflow.com/questions/49741991/value-range-provider-dependent-on-planning-variable
- https://stackoverflow.com/questions/76803188/timefold-construction-of-a-solution
- https://stackoverflow.com/questions/78516124/timefold-construction-heuristic-on-overconstrained-problem

### Legacy OptaPlanner
- https://docs.optaplanner.org/latest/optaplanner-docs/html_single/index.html
- https://issues.redhat.com/browse/PLANNER-124 (original per-variable pinning request)
