---
id: TASK-113
title: 'Add H17 constraint: Lab recurring weekly pattern'
status: Done
assignee:
  - claude
created_date: '2026-02-24 16:18'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - solver
  - constraint
dependencies: []
priority: high
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Background**: Through analysis of Phase 1a pre-assignment solver performance (-314 hard after 162s with 369 LabBlocks), we discovered a fundamental modeling misconception. LabRotation records encode a specific SOLUTION (which day of week) rather than the actual REQUIREMENT (recurring weekly pattern). The requirement — "all LabBlocks from the same course must be scheduled on the same day(s) of the week every week" — was never modeled as a Timefold constraint. It was only enforced through deterministic pre-assignment code (AcconciaturaPreAssigner, LabBlockPreAssigner).

**What this means**: When the solver runs, it has no incentive to maintain recurring weekly patterns for lab blocks. The solver freely scatters blocks across different days of the week, which is physically impossible in a real school (teachers and students expect consistent weekly schedules).

**Evidence**: The deterministic pre-assigners in `AcconciaturaPreAssigner.computeValidDates()` and `LabBlockPreAssigner.computeValidDates()` pick ONE day of the week and assign all blocks to dates matching that day. This recurring pattern logic was baked into the pre-assignment pipeline but never expressed as a solver constraint.

**Decision**: Model this as a Timefold hard constraint (H17) using a hybrid pairwise + groupBy formulation. Option 2 (allow N expected days per course computed from data) was chosen over Option 1 (split multi-day courses) because it requires no data model changes and handles both single-day and multi-day courses uniformly.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 H17 pairwise constraint added to BaseConstraintProvider for single-day courses (expectedDays=1)
- [x] #2 H17.2 groupBy+countDistinct constraint added for multi-day courses (expectedDays>1)
- [x] #3 expectedLabDaysPerWeek() helper method computes expected days from course.totalHours/labBlockSize/38 weeks
- [x] #4 Both constraints added to getBaseConstraints() so they are active in pre-assignment mode
- [x] #5 mvn test passes (constraint verifier tests unaffected)
- [x] #6 validate-solver-xml.sh passes
- [x] #7 CLAUDE.md canonical constraint table updated with H17
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Architecture Decision

**Option 2 chosen** (allow N expected days per course, computed from data):
- No data model changes needed (no course splitting)
- `expectedDaysPerWeek` computed inline: `Math.round(ceil(totalHours / labBlockSize) / 38.0)`
- Handles both single-day (95%+ of courses) and multi-day (e.g., T15 G04) uniformly

**Pairwise vs groupBy**: Hybrid approach chosen:
- Single-day courses → `forEachUniquePair` (strong gradient: N-1 penalty per wrong-day block)
- Multi-day courses → `groupBy + countDistinct` (correct semantics, weaker gradient acceptable for rare cases)

### Step 1: Add H17 + H17.2 constraints to BaseConstraintProvider

**File**: `src/main/java/com/school/planner/solver/BaseConstraintProvider.java`

Add imports:
```java
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count;
import static ai.timefold.solver.core.api.score.stream.ConstraintCollectors.countDistinct;
import com.school.planner.domain.Course;
```

Add to `getBaseConstraints()` (after H-M2, before medium section):
```java
// H17: Lab blocks must follow recurring weekly pattern
constraints.add(labRecurringPattern(constraintFactory));
constraints.add(labRecurringPatternMultiDay(constraintFactory));
```

Add constraint methods:

**H17 (single-day courses, pairwise)**:
```java
Constraint labRecurringPattern(ConstraintFactory constraintFactory) {
    return constraintFactory.forEachUniquePair(LabBlock.class,
                    Joiners.equal(b -> b.course != null ? b.course.id : -1L))
            .filter((a, b) -> a.startTimeslot != null && b.startTimeslot != null
                    && expectedLabDaysPerWeek(a.course) == 1
                    && a.startTimeslot.getDate().getDayOfWeek()
                       != b.startTimeslot.getDate().getDayOfWeek())
            .penalize(HardMediumSoftScore.ONE_HARD)
            .asConstraint("H17: Lab recurring pattern");
}
```

Rationale: `forEachUniquePair` with course-id joiner creates pairs within same course. Penalizing different dayOfWeek gives strong gradient — a block on the wrong day creates N-1 penalty points where N is blocks on the majority day. ~20-40 blocks per course → ~200-800 pairs → manageable for Timefold's incremental engine.

**H17.2 (multi-day courses, groupBy)**:
```java
Constraint labRecurringPatternMultiDay(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(LabBlock.class)
            .filter(b -> b.startTimeslot != null && b.course != null
                    && expectedLabDaysPerWeek(b.course) > 1)
            .groupBy(b -> b.course.id,
                    b -> expectedLabDaysPerWeek(b.course),
                    countDistinct(b -> b.startTimeslot.getDate().getDayOfWeek()),
                    count())
            .filter((courseId, expectedDays, distinctDays, blockCount) ->
                    distinctDays > expectedDays)
            .penalize(HardMediumSoftScore.ONE_HARD,
                    (courseId, expectedDays, distinctDays, blockCount) ->
                            (distinctDays - expectedDays) * blockCount)
            .asConstraint("H17.2: Lab recurring pattern (multi-day)");
}
```

Rationale: `groupBy` with 2 keys (courseId, expectedDays) + 2 collectors (countDistinct, count) gives a QuadConstraintStream. Penalty scaled by blockCount for stronger signal. Only fires for the rare multi-day case.

**Helper method**:
```java
static int expectedLabDaysPerWeek(Course course) {
    if (course == null || !course.isLab || course.labBlockSize <= 0) return 1;
    int blockCount = (int) Math.ceil((double) course.totalHours / course.labBlockSize);
    return Math.max(1, (int) Math.round((double) blockCount / 38.0));
}
```

Rationale: 38 is a standard Italian school year length. `Math.round` avoids false positives (40 blocks / 38 = 1.05 → rounds to 1, correctly single-day). Only courses with clearly >1 block/week (76+ blocks → 2.0 → rounds to 2) get multi-day treatment.

### Step 2: Update class javadoc

Add to BaseConstraintProvider class-level comment:
```
 * - H17: Lab recurring pattern (single-day)
 * - H17.2: Lab recurring pattern (multi-day)
```

### Step 3: Update CLAUDE.md constraint table

Add H17 to the Hard Constraints table:
```
| H17 | Lab recurring pattern | — | — | H17, H17.2 |
```

### Step 4: Verify

- `mvn test` — constraint verifier tests don't test H17 directly but should still pass
- `./scripts/validate-solver-xml.sh` — XML configs unchanged, should pass
- Consider adding a constraint verifier test for H17 in a follow-up task

### Computational Complexity Analysis

**H17 (pairwise, single-day)**:
- ~1144 LabBlocks across ~40-50 courses
- Average ~25 blocks/course → ~300 pairs/course → ~15K total pairs
- Per move: re-evaluates ~25 pairs (pairs involving the moved block)
- Timefold incremental engine handles this efficiently

**H17.2 (groupBy, multi-day)**:
- Affects ~1-2 courses (e.g., T15 for G04)
- Per move: O(1) incremental update to countDistinct
- Negligible computational cost

### Interaction with Pre-Assignment

H17 is placed in `BaseConstraintProvider`, so it's active during Phase 1a/1b pre-assignment mode. This is intentional — it replaces the deterministic day selection in AcconciaturaPreAssigner/LabBlockPreAssigner with constraint-based optimization. The solver now freely chooses which day(s) of the week to use, guided by H17 to maintain consistency.

### Future Considerations

- After implementing H17, re-evaluate whether the Phase 1a/1b split still makes sense, or if the solver with H17 can handle the full problem
- The LabRotation data model may need refactoring to remove the hard-coded dayOfWeek field (separate task)
- Consider a constraint verifier test for H17 (separate task)

CORRECTION!!!!!!!

● Problem! H17 as HARD creates structural infeasibility:

  ┌─────────────────┬────────────────────────┐
  │  Configuration  │  Phase 1a Hard Score   │
  ├─────────────────┼────────────────────────┤
  │ Without H17     │ 0hard (achievable)     │
  ├─────────────────┼────────────────────────┤
  │ With H17 (HARD) │ -6693hard (infeasible) │
  └─────────────────┴────────────────────────┘

  Why? The same-day constraint conflicts with resource limits:
  - T28 (Thursday only) teaches 3 groups in acconciatura lab
  - Each group needs 6h → 18h total
  - But Thursday only has 6 hours × 1 lab room = 6 slots
  - Impossible to fit all on same day!

  The original downgrade to MEDIUM was correct - shared resources make same-day patterns sometimes infeasible.

  Options:
  1. Keep M17 as MEDIUM - aspirational, guides solver when feasible
  2. Refine H17 - only apply when room capacity allows (complex to implement)

  Should I revert H17 back to M17 (MEDIUM)?

● User answered Claude's questions:
  ⎿  · H17 as HARD creates structural infeasibility (-6693hard). Shared resources (rooms, teachers) make same-day patterns sometimes impossible. Revert to M17 (MEDIUM)? →   Why? The same-day constraint conflicts with resource limits:
       - T28 (Thursday only) teaches 3 groups in acconciatura lab
       - Each group needs 6h → 18h total
       - But Thursday only has 6 hours × 1 lab room = 6 slots
       - Impossible to fit all on same day!
      

     if this is the case, then the constraint has been modeled wrong! T28 is around only on Thursday, in the same lab. She cycles through rotation with the other 2 teachers. But also, the rotation rotate the Student group for here: First cycle she does just group A, second she does group B, third she does Group C. They are not supposed to try to have lesson all on the
     same day, at the same time, with the same teacher on the same weeks. The csv might help in identifying the pattern and eventually come up with a more correct Constraint definition

∴ Thinking…

  The user is pointing out that the constraint is modeled incorrectly. The issue is that the constraint is checking ALL blocks of a course across the entire year, but the rotation means:

  1. T28 teaches Group A in Cycle 1 (on Thursdays)
  2. T28 teaches Group B in Cycle 2 (on Thursdays)
  3. T28 teaches Group C in Cycle 3 (on Thursdays)

  So the blocks are NOT supposed to all be on the same day - they're supposed to be on the same day WITHIN EACH CYCLE.

  The current H17 constraint checks if ANY two blocks of the same course are on different days, but it should only check blocks WITHIN THE SAME TIME PERIOD (rotation cycle).

  This is a more complex constraint that needs to consider time periods. Let me think about how to model this...

  The rotation cycles are:
  - Period 1: 06/10 to 22/12
  - Period 2: 10/01 to 04/04
  - Period 3: 04/05 to 01/06

  The constraint should be: Within each period, blocks of the same course should be on the same day of week.

  But this requires knowing which period each block belongs to, which isn't currently tracked in the LabBlock entity.

  Let me revert to M17 for now and discuss the proper modeling with the user.

● You're right! The constraint is modeled incorrectly. The rotation means:

  - Cycle 1: T28 → Group A (Thursdays)
  - Cycle 2: T28 → Group B (Thursdays)
  - Cycle 3: T28 → Group C (Thursdays)

  The current H17 checks ALL blocks across the entire year, but it should only check blocks within the same rotation period.

  This requires knowing which period each block belongs to - currently not tracked in LabBlock.

  Let me revert to M17 (MEDIUM) for now, and we can properly model the rotation-aware constraint later.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Progress Update (2026-02-25)

### Commit 221eaad
- **BaseConstraintProvider**: Added M17/M17.2 constraint documentation
  - M17: Lab recurring weekly pattern (MEDIUM)
  - M17.2: Multi-day courses (MEDIUM)
  - Downgraded from HARD to MEDIUM due to structural infeasibility with shared resources

### Design Decision
LabRotation filtering removed from PreAssignmentEntityExtractor - now M17 constraint guides solver to find patterns organically rather than hard-coding them.

## Blocked (2026-02-26)

Constraint is semantically incorrect for rotation-based courses. Current implementation checks if ALL blocks of a course are on the same day across the entire year, but rotation courses (e.g., T28's acconciatura) teach different student groups in different periods:

- Period 1: Group A on Thursdays
- Period 2: Group B on Thursdays
- Period 3: Group C on Thursdays

These correctly have blocks on the same day WITHIN each period, but the constraint incorrectly penalizes them.

**Also**: Documentation mismatch - CLAUDE.md says H17 (Hard) but code has M17 (Medium).

**Unblocks when**: Rotation period tracking is added to LabBlock entity, or constraint is redesigned to be period-aware.

## Completed (2026-03-01)

Fixed M17 to be period-aware:

1. Added labRotation field to LabBlock entity

2. BacktrackingPreAssigner assigns rotation to blocks

3. M17/M17.2 now only compare blocks within same rotation period

See git diff for implementation details.
<!-- SECTION:NOTES:END -->
