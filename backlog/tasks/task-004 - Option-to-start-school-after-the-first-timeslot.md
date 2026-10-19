---
id: task-004
title: Option to start school after the first timeslot
status: Done
assignee: []
created_date: '2026-02-17 16:27'
updated_date: '2026-02-28 05:37'
labels:
  - claude-planned
  - enhancement
  - solver
  - constraint
dependencies: []
priority: low
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem Statement

Currently, all student groups must have lessons scheduled from the first timeslot (08:00) due to the implicit hard constraint that the school day starts at 08:00. This can make some scheduling problems unsolvable when:
- Teacher availability conflicts block the first slot
- Room constraints make first-slot placement impossible
- Pre-assignment patterns (like acconciatura rotations) occupy other slots but leave first slot empty

The human planners indicate that starting at the second slot (09:00) could be acceptable in constrained situations.

## Proposed Solution

Add an optional `minStartSlotIndex` field to `StudentGroup` that allows relaxing the start time constraint on a per-group basis. This field would:
- Default to `0` (first slot, 08:00) — current behavior
- Allow values like `1` (second slot, 09:00) for groups that can start later
- Be enforced via a new hard constraint (or modification to existing constraints)

## Implementation Details

### Slot Structure (reference)
- Monday-Thursday: 6 slots (08:00-14:00)
- Friday: 5 slots (08:00-13:00)
- Slot indices: 0=08:00, 1=09:00, 2=10:00, 3=11:00, 4=12:00, 5=13:00

### Constraint Design Options

**Option A: New hard constraint H17 (Min start slot)**
```java
// H17: Lessons cannot be scheduled before StudentGroup.minStartSlotIndex
Constraint studentGroupMinStartSlot(ConstraintFactory cf) {
    return cf.forEach(Lesson.class)
        .filter(l -> l.getStudentGroup().minStartSlotIndex != null
            && l.getStudentGroup().minStartSlotIndex > 0)
        .filter(l -> getSlotIndex(l.timeslot) < l.getStudentGroup().minStartSlotIndex)
        .penalize(HardMediumSoftScore.ONE_HARD)
        .asConstraint("H17: Student group min start slot");
}
```

**Option B: Solver-level configuration**
Modify `ValueRangeProvider` to exclude early timeslots from the planning value range for groups with `minStartSlotIndex > 0`. This is more efficient but requires custom value range logic.

### Use Cases
1. **G14 groups**: Reduced schedule groups may benefit from starting later
2. **Solver escape hatch**: Allow solver to find solutions when first-slot constraints are too tight
3. **Special programs**: Groups with alternative schedules

### Interaction with Existing Constraints
- H8 (min 4 lessons/day): Unaffected — counts total daily lessons regardless of start time
- H11 (max hours/day): Unaffected — counts total daily hours
- H15 (same-day consecutive same-subject): Unaffected — only checks adjacency

### Files to Modify
- `src/main/java/com/school/planner/domain/StudentGroup.java`
- `src/main/java/com/school/planner/domain/Timeslot.java`
- `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java`
- `src/main/webui/src/types/domain.ts`
- `src/main/webui/src/components/admin/StudentGroupsAdmin.tsx`
- `CLAUDE.md` (constraint table)
- `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Given a StudentGroup with minStartSlotIndex=1, when the solver assigns a Lesson to slot 0 (08:00), then constraint H17 should penalize with 1 HARD
- [ ] #2 Given a StudentGroup with minStartSlotIndex=0 or null (default), when the solver assigns lessons, then any slot from 0-5 is allowed (current behavior preserved)
- [ ] #3 Given a G14 group configured with minStartSlotIndex=1, when viewing the schedule timeline, then no G14 lessons appear before 09:00
- [ ] #4 Given the StudentGroupsAdmin UI, when editing a student group, then an optional 'Min Start Slot' dropdown (0-5) is available with default '0 - 08:00'

- [ ] #5 Given a full solve cycle with no minStartSlotIndex set on any group, then the solution score is identical to the current baseline (no regression)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Phase 1: Domain Model
1. Add `minStartSlotIndex` field to `StudentGroup.java`
2. Add helper method to `Timeslot.java` to compute slot index

### Phase 2: Constraint Implementation
1. Add `studentGroupMinStartSlot()` constraint in SchoolScheduleConstraintProvider
2. Register in `defineConstraints()`
3. Add constraint ID to CLAUDE.md

### Phase 3: Frontend
1. Add `minStartSlotIndex` to TypeScript domain types
2. Add dropdown in StudentGroupsAdmin.tsx

### Phase 4: Testing
1. Add constraint verifier test for H17
2. Test with G14 group
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | Domain field + constraint + UI |
| Risk | 2 | Optional feature, no breaking changes |
| Testing | 3 | Constraint test + UI test |
| Dependencies | 1 | None |
| **Total** | **3** | 1-2 days |

---

## Priority: **LOW** (Niche Use Case)

G14 groups only, no current demand. Optional enhancement.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
