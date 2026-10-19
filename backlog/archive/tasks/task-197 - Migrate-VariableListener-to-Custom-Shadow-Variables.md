---
id: TASK-197
title: Migrate VariableListener to Custom Shadow Variables
status: To Do
assignee: []
created_date: '2026-03-08 08:38'
updated_date: '2026-07-25 05:55'
labels:
  - technical-debt
  - timefold-migration
  - architectural-change
  - claude-planned
dependencies: []
documentation:
  - 'https://timefold.ai/blog/cascading-update-shadow-var'
  - >-
    https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-to-latest-version
  - 'https://github.com/TimefoldAI/timefold-solver/discussions/2036'
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Migrate OccupiedTimeslotsListener from deprecated VariableListener API to Timefold's new Custom Shadow Variables mechanism (@CascadingUpdateShadowVariable).

## Context

The VariableListener API is deprecated for removal in Timefold 2.0. The replacement is Custom Shadow Variables using declarative annotations. This is an architectural change requiring refactoring of how shadow variables are updated.

## Affected Files

- src/main/java/com/school/planner/solver/OccupiedTimeslotsListener.java

## Migration Patterns

### Pattern A: CascadingUpdateShadowVariable (Simple Chain Updates)

If the listener updates related entities in a chain:

```java
// BEFORE (VariableListener)
public class OccupiedTimeslotsListener implements VariableListener<SchoolSchedule, Lesson> {
    @Override
    public void afterVariableChanged(ScoreDirector<SchoolSchedule> scoreDirector, Lesson lesson) {
        updateOccupiedTimeslots(lesson);
    }
}

// AFTER (@CascadingUpdateShadowVariable)
@PlanningEntity
public class Lesson {
    @CascadingUpdateShadowVariable
    public void updateCascadingFields() {
        // Called automatically when timeslot, room, or teacher changes
        this.occupiedTimeslot = calculateOccupiedTimeslot();
    }
}
```

### Pattern B: Custom Shadow Variable (Complex Logic)

For cross-entity coordination:

```java
// AFTER
@PlanningEntity
public class SchoolSchedule {
    @CustomShadowVariable(
        variableListenerClass = OccupiedTimeslotsShadowVariableListener.class,
        sourceVariableName = "timeslot"
    )
    private Map<Timeslot, List<Lesson>> occupiedTimeslots;
}
```

## Implementation Steps

1. **Analyze current implementation** - Understand what shadow variables are maintained
2. **Choose pattern** - Simple cascade vs complex custom shadow variable
3. **Implement new approach** - Add annotations, update entity classes
4. **Integration testing** - Run solver with same datasets, verify constraint scores match
5. **Remove old listener** - Once migration is verified
6. **Update documentation** if shadow variable logic is business-critical

## Risk Assessment

**Risk Level**: MEDIUM-HIGH - Architectural change requiring careful testing. May expose hidden dependencies on listener behavior.

## Mitigation

1. Keep old listener code in a separate branch for rollback
2. Run A/B tests comparing old vs new implementations
3. Profile performance before/after migration
4. Incremental migration (one shadow variable at a time if possible)

## Time Estimate: 4-8 hours

## Notes

- **Cannot be automated** with OpenRewrite recipes
- **Manual refactoring required**
- Consider scheduling for a focused migration sprint
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 OccupiedTimeslotsListener.java removed or replaced with shadow variable approach
- [ ] #2 All shadow variable updates work correctly
- [ ] #3 Solver produces same constraint scores as before
- [ ] #4 Performance is equal or better than before
- [ ] #5 Tests verify occupied timeslots tracking is accurate
- [ ] #6 Build completes without VariableListener deprecation warnings
- [ ] #7 Documentation updated if shadow variable logic is non-trivial
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ARCHIVIATA 2026-07-25 (giro di pulizia, verifica meccanica): nessun VariableListener nel codebase (grep vuoto); OccupiedTimeslotsListener rimosso.
<!-- SECTION:NOTES:END -->
