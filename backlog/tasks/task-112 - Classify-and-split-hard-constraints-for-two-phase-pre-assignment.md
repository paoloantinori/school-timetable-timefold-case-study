---
id: TASK-112
title: Classify and split hard constraints for two-phase pre-assignment
status: Done
assignee: []
created_date: '2026-02-24 09:01'
updated_date: '2026-03-09 19:50'
labels:
  - constraints
  - refactoring
  - claude-planned
dependencies: []
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Background

The two-phase pre-assignment approach runs a solver on a **subset** of entities (those matching LabRotation records or professionalizzanti courses). For Phase 1 to work correctly, we need constraints that can evaluate correctly on a sparse schedule.

## Constraint Categories

### Sparse-Compatible (BaseConstraintProvider)

These constraints evaluate entity pairs or single entities in isolation. They work correctly on ANY subset of entities:

| Constraint IDs | Description | Pattern |
|----------------|-------------|---------|
| H1.1, H1.2, H1.3 | Teacher conflicts | `forEachUniquePair` / `join` with `overlapping` |
| H2.1, H2.2, H2.3 | Room conflicts | `forEachUniquePair` / `join` with `overlapping` |
| H3.1, H3.2, H3.3 | Student group conflicts | `forEachUniquePair` / `join` with `overlapping` |
| H4.1, H4.2 | Stage blackout (non-prof) | `join` with StagePattern, date filtering |
| H5.1, H5.2 | Teacher unavailability | `join` with TeacherAvailability, `overlapping` |
| H6.1, H6.2 | School holiday | `join` with SchoolHoliday |
| H9 | Block valid placement | `forEach` with `filter` (single entity check) |
| H14.1, H14.2 | Student group start date | `forEach` with `filter` (single entity check) |
| H-M2.1, H-M2.2 | Room suitability | `forEach` with `filter` (single entity check) |
| H-D1.1, H-D1.2, H-D1.3, H-D1.4 | Doppio teacher conflicts | `join` patterns |

**Key property**: Each match is independent - penalizing a conflict in the subset doesn't change the score of other conflicts.

### Sparse-Incompatible (SchoolScheduleConstraintProvider only)

These constraints require global aggregation and cannot work correctly on a subset:

| Constraint ID | Description | Why Incompatible |
|---------------|-------------|------------------|
| H7 | Max teacher daily hours | Uses `count()` aggregate across ALL lessons/blocks for a teacher-day |
| H11 | Max student group daily hours | Uses `count()` + `sum()` across ALL entities for a group-day |
| H13 | Vertically compact | Groups ALL lessons for a group-day to count gaps |
| H15 | Same-day same-subject consecutive | Groups ALL lessons for a group-subject-day |
| H16 | Consecutive same-subject same teacher-room | `forEachUniquePair` but semantic depends on ALL same-subject lessons |

**Key property**: The score depends on aggregating across ALL entities - a subset would give wrong counts.

## Implementation Strategy

### Option A: Inheritance (Recommended)

```java
// BaseConstraintProvider.java - for Phase 1 (sparse-compatible only)
public class BaseConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[] {
            // H1-H6, H9, H14, H-M2, H-D1 (sparse-compatible only)
        };
    }
}

// SchoolScheduleConstraintProvider.java - for Phase 2 (all constraints)
public class SchoolScheduleConstraintProvider extends BaseConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        Constraint[] base = super.defineConstraints(cf);
        Constraint[] phase2 = new Constraint[] {
            // H7, H11, H13, H15, H16 (sparse-incompatible)
        };
        return concat(base, phase2);
    }
}
```

**Pros**: Code reuse, no duplication, constraint IDs remain consistent
**Cons**: Inheritance coupling

### Option B: Composition

```java
// Separate providers, merged in solver config
// preAssignmentSolverConfig.xml uses <constraintProviderClass>BaseConstraintProvider
// solverConfig.xml uses <constraintProviderClass>SchoolScheduleConstraintProvider
```

**Pros**: Complete separation
**Cons**: Requires duplicating constraints in both providers OR complex composition

## Verification Strategy

1. **Unit test**: Create a sparse schedule (subset of entities), verify sparse-compatible constraints score correctly
2. **Integration test**: Run Phase 1 on pre-assignment entities, verify no false positives from sparse-incompatible constraints
3. **Benchmark**: Compare Phase 1+2 scores vs single-phase to ensure no regression

## Files to Modify

- `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` (new)
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java` (refactor to extend Base)
- `src/main/resources/preAssignmentSolverConfig.xml` (change constraintProviderClass)
- `src/test/java/.../SparseConstraintTest.java` (new)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 BaseConstraintProvider contains ONLY sparse-compatible constraints (H1-H6, H9, H14, H-M2, H-D1)
- [ ] #2 SchoolScheduleConstraintProvider extends BaseConstraintProvider and adds sparse-incompatible constraints (H7, H11, H13, H15, H16)
- [ ] #3 preAssignmentSolverConfig.xml uses BaseConstraintProvider
- [ ] #4 Unit test verifies sparse-compatible constraints score correctly on subset schedules
- [ ] #5 All existing tests pass (no constraint ID changes)
- [ ] #6 Constraint IDs remain unchanged (H1.1, H1.2, etc.) for backward compatibility
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Technical Approach

1. **Create BaseConstraintProvider** with sparse-compatible constraints:
   - Copy H1-H6, H9, H14, H-M2, H-D1 constraint methods
   - Keep helper methods (isAdjacent, calculateGapMinutes, etc.) in base class
   - Constraint IDs MUST remain unchanged

2. **Refactor SchoolScheduleConstraintProvider**:
   - Extend BaseConstraintProvider
   - Override `defineConstraints()` to concatenate base + phase2 constraints
   - Move H7, H11, H13, H15, H16 to this class

3. **Update preAssignmentSolverConfig.xml**:
   ```xml
   <constraintProviderClass>com.school.planner.solver.BaseConstraintProvider</constraintProviderClass>
   ```

4. **Add unit test**:
   - Create schedule with only 10% of entities
   - Verify H1-H6 constraints score correctly
   - Verify H7/H11/H13 constraints are NOT evaluated (or are no-op)

## Edge Cases

- H15 uses `groupBy(toList())` - verify it handles empty lists gracefully in sparse schedules
- H16 uses `forEachUniquePair` - verify it doesn't penalize when only one lesson exists
- LabBlock vs Lesson joins - ensure null-safety when one entity type is absent

## Performance Considerations

- Phase 1 with fewer constraints should be 2-3x faster
- Constraint stream pruning is automatic in Timefold
- No runtime cost for unused constraints

Archived: Superseded by PreAssignmentContext which provides direct sparse-compatible constraint checks (H1, H2, H3, H5, H15, H16). The CSP-based HybridPhase1aService uses PreAssignmentContext for conflict detection rather than a separate BaseConstraintProvider. The goal of 'sparse-compatible constraints for Phase 1' is achieved through this different implementation approach.
<!-- SECTION:NOTES:END -->
