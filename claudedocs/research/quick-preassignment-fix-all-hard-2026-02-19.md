# Research: Quick H15 Fix Options for Pre-Assignment

**Date**: 2026-02-19
**Depth**: Exhaustive
**Status**: Complete
**Context**: Looking for quick solutions while two-phase solver approach is developed on separate branch

---

## Executive Summary

**Recommendation: Add H15 (same-day consecutive) validation to PreAssignmentValidator**

This is the **quickest and most effective fix** because H15 accounts for **88% of post-pre-assignment hard violations** (-1473 out of -1668). Adding H15-aware slot selection during pre-assignment will prevent creating gaps that the solver cannot fix.

---

## Problem Analysis

### Current State After Pre-Assignment

| Constraint | Score | Matches | % of Total |
|------------|-------|---------|------------|
| **H15: Same-day same-subject consecutive** | **-1473** | 1473 | **88%** |
| H10: Preparazione before stage | -70 | 70 | 4% |
| H9: Block valid placement | -54 | 9 | 3% |
| H-D1.3: Doppio teacher conflict | -31 | 31 | 2% |
| H5.2: Teacher unavailability | -28 | 28 | 2% |
| H16: Consecutive same-subject teacher-room | -8 | 8 | <1% |
| H-M2.1: Room suitability | -4 | 4 | <1% |

### Root Cause: H15 Violation Pattern

**H15 Constraint Logic** (lines 394-414 of SchoolScheduleConstraintProvider):
```java
// If a student group has 2+ lessons of the same subject on the same day,
// those hours must be adjacent (no gaps allowed).
Constraint sameDaySameSubjectMustBeConsecutive(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> !lesson.isLab())
            .groupBy(
                    Lesson::getStudentGroup,
                    Lesson::getSubject,
                    lesson -> lesson.timeslot.getDate(),
                    toList())
            .filter((group, subject, date, lessons) -> lessons.size() >= 2)
            .penalize(HardMediumSoftScore.ONE_HARD,
                    (group, subject, date, lessons) -> calculateGapMinutes(lessons))
            .asConstraint("H15: Same-day same-subject must be consecutive");
}
```

**Example of H15 Violation Created by Pre-assignment**:
```
Day: 2026-03-10, Group: G01, Subject: Matematica

Pre-assigned: Matematica 08:00-09:00 (pinned)
Other pinned: Italiano 09:00-10:00 (pinned)
Pre-assigned: Matematica 11:00-12:00 (pinned)

Result: 60-minute gap between Matematica lessons → H15 violation (-1 hard)
Solver CANNOT fix because both Matematica lessons are pinned.
```

**Why Pre-Assigners Create H15 Violations**:
1. ProfessionalizzantiPreAssigner pins lessons to stage days
2. It only checks H1/H2/H3 conflicts via PreAssignmentValidator
3. It doesn't check if there are already same-subject lessons on that day
4. Result: Pins create gaps → H15 violations

---

## Quick Solution Options Evaluated

### ✅ Option 1: Add H15 Validation (RECOMMENDED)

**Approach**: Extend `PreAssignmentValidator` with H15-aware slot selection.

**Implementation**:
1. Add method `hasH15Violation(Lesson lesson, Timeslot proposedTimeslot)` to PreAssignmentValidator
2. Method checks if there are already pinned same-subject lessons on the same day
3. If yes, only allow slots that are adjacent (no gap)
4. Integrate into `canPin(Lesson, Timeslot, Room)`

**Code Addition** (~50 lines):
```java
/**
 * Check if assigning this lesson would create an H15 violation.
 * H15: Same-day same-subject lessons must be consecutive.
 *
 * @return true if this slot would create a gap with existing same-subject lessons
 */
public boolean hasH15Violation(Lesson lesson, Timeslot proposedTimeslot) {
    if (lesson == null || proposedTimeslot == null || lesson.isLab()) {
        return false;
    }

    StudentGroup group = lesson.getStudentGroup();
    String subject = lesson.getSubject();
    LocalDate date = proposedTimeslot.getDate();

    // Find all pinned lessons of same subject on same day for same group
    List<Lesson> sameSubjectLessons = Lesson.find(
            "course.studentGroup = ?1 and " +
            "course.subject = ?2 and " +
            "timeslot.date = ?3 and " +
            "pinned = true and " +
            "id != ?4",
            group, subject, date, lesson.id).list();

    if (sameSubjectLessons.isEmpty()) {
        // No existing same-subject lessons, no H15 concern
        return false;
    }

    // Check if proposed slot is adjacent to any existing same-subject lesson
    LocalTime proposedStart = proposedTimeslot.startTime;
    LocalTime proposedEnd = proposedTimeslot.endTime;

    for (Lesson existing : sameSubjectLessons) {
        // Check adjacency: proposed starts when existing ends, or vice versa
        if (proposedStart.equals(existing.timeslot.endTime) ||
            proposedEnd.equals(existing.timeslot.startTime)) {
            // Adjacent! This is good, no H15 violation
            return false;
        }
    }

    // Not adjacent to any existing same-subject lesson → would create gap
    LOG.debugf("H15 violation: %s for group %s on %s - slot %s not adjacent to existing %s lessons",
            subject, group.name, date, proposedStart, sameSubjectLessons.size());
    return true;
}
```

**Then modify `canPin(Lesson, Timeslot, Room)`**:
```java
public boolean canPin(Lesson lesson, Timeslot timeslot, Room room) {
    // ... existing H1/H2/H3 checks ...

    // NEW: H15 check
    if (hasH15Violation(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: would create H15 gap for %s on %s",
                lesson.id, lesson.getSubject(), timeslot.getDate());
        return false;
    }

    return true;
}
```

**Estimated Impact**: Eliminates ~1400+ of the -1668 hard violations (84%)

**Effort**: 1-2 hours

---

### ❌ Option 2: Solver Timeout Tweaks (NOT HELPFUL)

**Why it won't help**: Timeouts control solver duration, not constraint violations. The solver already has 8-hour limit in solverConfig.xml. The issue is that **pinned entities cannot be moved**, so more time won't help.

---

### ❌ Option 3: Database Indexes (MINOR IMPACT)

**Current State**: Domain entities use Panache/Hibernate but don't have explicit `@Index` annotations.

**Potential Indexes**:
```java
@Table(name = "Lesson", indexes = {
    @Index(name = "idx_lesson_timeslot", columnList = "timeslot_id"),
    @Index(name = "idx_lesson_course", columnList = "course_id"),
    @Index(name = "idx_lesson_pinned", columnList = "pinned")
})
```

**Why it won't help much**:
1. H15 violation is a **logic problem**, not a query performance problem
2. Pre-assignment already runs in seconds
3. Query optimization won't change the constraint violations

**Effort**: 2-4 hours to add and test indexes
**Impact**: Maybe 10-20% faster queries, but won't fix violations

---

### ❌ Option 4: Working Outside Transaction (NOT RELEVANT)

**Why it won't help**: Transactions ensure data consistency. The pre-assigners need transactions to:
1. Query existing pinned entities
2. Check conflicts
3. Update entity state

Running outside transaction wouldn't change the validation logic or results.

---

### ⚠️ Option 5: Verify Assignments One-by-One with Retry (MEDIUM EFFORT)

**Approach**: When H15 conflict detected, try alternative slots on same day.

**Implementation**:
```java
// In ProfessionalizzantiPreAssigner
for (Timeslot slot : availableSlots) {
    if (validator.canPin(lesson, slot, room)) {
        // Pin it
        break;
    }
    // If canPin returned false due to H15, try next slot
}
```

**Problem**: This is already what pre-assigners do. The issue is that **no adjacent slot may be available** (occupied by other pinned entities).

**Enhancement**: If no adjacent slot available on this day, try a different day.

**Effort**: 4-8 hours to modify all three pre-assigners
**Impact**: Similar to Option 1 but more complex

---

### ❌ Option 6: Post-Assignment Repair (MEDIUM EFFORT, RISKY)

**Approach**: After pre-assignment, find H15 violations and unpin one of the conflicting lessons.

**Implementation**:
```java
// After runAll()
List<H15Violation> violations = findH15Violations();
for (H15Violation v : violations) {
    // Unpin the lesson that creates the gap
    unpinLesson(v.lessonId);
}
```

**Problem**: Unpinning defeats the purpose of pre-assignment (human planner intent). The solver may place it differently than the human planner intended.

**Effort**: 3-6 hours
**Impact**: Reduces violations but loses human-planned placements

---

## Recommended Quick Fix Implementation

### Step 1: Add H15 Validation Method

Add to `PreAssignmentValidator.java`:

```java
/**
 * Check if assigning this lesson would create an H15 violation.
 * H15: Same-day same-subject lessons must be consecutive (no gaps).
 *
 * @param lesson the lesson to potentially pin
 * @param proposedTimeslot the proposed timeslot
 * @return true if this slot would create a gap with existing same-subject lessons
 */
public boolean hasH15Violation(Lesson lesson, Timeslot proposedTimeslot) {
    if (lesson == null || proposedTimeslot == null) {
        return false;
    }

    // Lab lessons are exempt from H15 (handled by LabBlock constraints)
    if (lesson.isLab()) {
        return false;
    }

    StudentGroup group = lesson.getStudentGroup();
    String subject = lesson.getSubject();
    if (group == null || subject == null) {
        return false;
    }

    LocalDate date = proposedTimeslot.getDate();

    // Find all pinned lessons of same subject on same day for same group
    List<Lesson> sameSubjectLessons = Lesson.find(
            "course.studentGroup = ?1 and " +
            "course.subject = ?2 and " +
            "timeslot.date = ?3 and " +
            "pinned = true and " +
            "id != ?4",
            group, subject, date, lesson.id).list();

    if (sameSubjectLessons.isEmpty()) {
        // No existing same-subject lessons, no H15 concern
        return false;
    }

    // Check if proposed slot is adjacent to any existing same-subject lesson
    LocalTime proposedStart = proposedTimeslot.startTime;
    LocalTime proposedEnd = proposedTimeslot.endTime;

    for (Lesson existing : sameSubjectLessons) {
        // Check adjacency: proposed starts when existing ends, or vice versa
        if (proposedStart.equals(existing.timeslot.endTime) ||
            proposedEnd.equals(existing.timeslot.startTime)) {
            // Adjacent! This is good, no H15 violation
            return false;
        }
    }

    // Not adjacent to any existing same-subject lesson → would create gap
    LOG.debugf("H15 violation would occur: %s for group %s on %s - slot %s-%s not adjacent to %d existing lessons",
            subject, group.name, date, proposedStart, proposedEnd, sameSubjectLessons.size());
    return true;
}

/**
 * Find an H15-safe timeslot for this lesson on the given date.
 * Returns a slot that is adjacent to existing same-subject lessons, or null if none available.
 *
 * @param lesson the lesson to place
 * @param date the target date
 * @param availableSlots all available slots on that date (not conflict-checked)
 * @return an H15-safe slot, or null if none found
 */
public Timeslot findH15SafeSlot(Lesson lesson, LocalDate date, List<Timeslot> availableSlots) {
    if (lesson == null || lesson.isLab() || availableSlots == null || availableSlots.isEmpty()) {
        return null;
    }

    StudentGroup group = lesson.getStudentGroup();
    String subject = lesson.getSubject();
    if (group == null || subject == null) {
        return availableSlots.get(0); // No H15 concern, return first available
    }

    // Find existing same-subject lessons on this day
    List<Lesson> sameSubjectLessons = Lesson.find(
            "course.studentGroup = ?1 and " +
            "course.subject = ?2 and " +
            "timeslot.date = ?3 and " +
            "pinned = true and " +
            "id != ?4",
            group, subject, date, lesson.id).list();

    if (sameSubjectLessons.isEmpty()) {
        // No existing same-subject lessons, any slot is H15-safe
        return availableSlots.get(0);
    }

    // Find the time boundaries of existing lessons
    LocalTime earliestEnd = sameSubjectLessons.stream()
            .map(l -> l.timeslot.endTime)
            .min(LocalTime::compareTo)
            .orElse(null);
    LocalTime latestStart = sameSubjectLessons.stream()
            .map(l -> l.timeslot.startTime)
            .max(LocalTime::compareTo)
            .orElse(null);

    // Prefer slots adjacent to existing cluster
    for (Timeslot slot : availableSlots) {
        // Check if slot starts where earliest lesson ends (extend downward)
        if (earliestEnd != null && slot.startTime.equals(earliestEnd)) {
            return slot;
        }
        // Check if slot ends where latest lesson starts (extend upward)
        if (latestStart != null && slot.endTime.equals(latestStart)) {
            return slot;
        }
    }

    // No adjacent slot available
    return null;
}
```

### Step 2: Integrate into canPin()

```java
public boolean canPin(Lesson lesson, Timeslot timeslot, Room room) {
    // ... existing H1/H2/H3 checks ...

    // H15 check
    if (hasH15Violation(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: would create H15 gap for %s on %s",
                lesson.id, lesson.getSubject(), timeslot.getDate());
        return false;
    }

    return true;
}
```

### Step 3: Use H15-Safe Slot Selection in ProfessionalizzantiPreAssigner

Modify the slot selection logic to prefer H15-safe slots:

```java
// Get available slots for this date
List<Timeslot> availableSlots = findAvailableSlots(date, lesson);

// Try H15-safe slot first
Timeslot h15SafeSlot = validator.findH15SafeSlot(lesson, date, availableSlots);
if (h15SafeSlot != null && validator.canPin(lesson, h15SafeSlot, room)) {
    // Pin using H15-safe slot
    assignLesson(lesson, h15SafeSlot, room);
    continue;
}

// Fallback: try any slot (will fail if H15 conflict exists)
for (Timeslot slot : availableSlots) {
    if (validator.canPin(lesson, slot, room)) {
        assignLesson(lesson, slot, room);
        break;
    }
}
```

---

## Expected Impact

| Metric | Before Fix | After Fix | Improvement |
|--------|-----------|-----------|-------------|
| H15 violations | -1473 | ~0-100 | ~95% |
| Total hard violations | -1668 | ~-200 | ~88% |
| Pre-assignment time | ~5s | ~8s | +3s (H15 queries) |
| Solver time to fix remaining | Variable | Much faster | Fewer pinned violations |

---

## Remaining Hard Violations (After H15 Fix)

The remaining ~200 hard violations will be from:
- **H10**: Preparazione before stage (-70) - requires stage-aware logic
- **H9**: Block valid placement (-54) - block size vs available slots
- **H-D1**: Doppio teacher conflict (-31) - co-teaching overlaps
- **H5**: Teacher unavailability (-28) - vacation conflicts
- **H16**: Consecutive same-subject teacher-room (-8) - consistency
- **H-M2**: Room suitability (-4) - lab tag requirements

These require more targeted fixes or can be handled by the solver if not pinned.

---

## Alternative: If Quick Fix Doesn't Work

If H15 validation prevents too many pre-assignments (no valid slots), consider:

1. **Relax to same-day-only**: Only pre-assign at most ONE lesson per subject per day, let solver group them
2. **Selective pinning**: Only pin when H15-safe slot exists, leave others for solver
3. **Proceed with two-phase solver**: The more robust solution already being explored

---

## Comprehensive Quick Fix: All Hard Violations

To eliminate **ALL** hard violations, extend `PreAssignmentValidator` with checks for each constraint:

### H10: Preparazione before stage (-70 violations)

**Problem**: Preparazione all'alternanza lessons scheduled during/after stage period.

**Fix**: Add validation in PreAssignmentValidator:
```java
public boolean hasH10Violation(Lesson lesson, Timeslot proposedTimeslot) {
    if (lesson == null || lesson.getSubject() == null ||
        !lesson.getSubject().startsWith("Preparazione all'alternanza")) {
        return false; // Not a Preparazione lesson
    }

    StudentGroup group = lesson.getStudentGroup();
    if (group == null) return false;

    // Check if this student group has a stage pattern
    List<StagePattern> patterns = StagePattern.find("studentGroup", group).list();
    for (StagePattern sp : patterns) {
        if (!proposedTimeslot.getDate().isBefore(sp.startDate)) {
            // Lesson is on/after stage start date → H10 violation
            LOG.debugf("H10 violation: Preparazione for %s on %s (stage starts %s)",
                    group.name, proposedTimeslot.getDate(), sp.startDate);
            return true;
        }
    }
    return false;
}
```

### H9: Block valid placement (-54 violations, 9 blocks)

**Problem**: LabBlock doesn't fully fit (occupiedTimeslots.size < blockSize).

**Root Cause**: LabBlock extends past the end of available timeslots on a day.

**Fix**: In `LabBlockPreAssigner` and `AcconciaturaPreAssigner`, verify block fits:
```java
public boolean hasH9Violation(LabBlock block, Timeslot startTimeslot) {
    // Check if all slots from start to start+blockSize exist
    List<Timeslot> occupied = block.getOccupiedTimeslots();
    if (occupied == null || occupied.size() < block.blockSize) {
        LOG.debugf("H9 violation: Block %d (size=%d) only has %d slots available",
                block.id, block.blockSize, occupied != null ? occupied.size() : 0);
        return true;
    }
    return false;
}
```

### H-D1.3: Doppio teacher conflict (-31 violations)

**Problem**: LabBlock with `doppioTeacher` has a lesson by that teacher at the same time.

**Fix**: Extend PreAssignmentValidator:
```java
public boolean hasDoppioConflict(LabBlock block, Timeslot startTimeslot, Room room) {
    if (block.course == null || block.course.doppioTeacher == null) {
        return false; // No doppio teacher
    }

    Teacher doppioTeacher = block.course.doppioTeacher;

    // Check if doppio teacher has any pinned lesson at any slot this block occupies
    List<Timeslot> occupied = block.getOccupiedTimeslots();
    for (Timeslot ts : occupied) {
        long count = Lesson.count(
            "course.teacher = ?1 and pinned = true and timeslot = ?2",
            doppioTeacher, ts);
        if (count > 0) {
            LOG.debugf("H-D1 violation: Doppio teacher %s has lesson at %s",
                    doppioTeacher.name, ts);
            return true;
        }
    }
    return false;
}

// Also for Lesson pinning:
public boolean hasDoppioConflict(Lesson lesson, Timeslot timeslot) {
    if (lesson.course == null || lesson.course.doppioTeacher == null) {
        return false;
    }

    Teacher doppioTeacher = lesson.course.doppioTeacher;

    // Check if doppio teacher has any pinned LabBlock occupying this slot
    List<LabBlock> blocks = LabBlock.find(
            "course.doppioTeacher = ?1 and pinned = true and startTimeslot is not null",
            doppioTeacher).list();
    for (LabBlock block : blocks) {
        if (block.occupiesTimeslot(timeslot)) {
            LOG.debugf("H-D1 violation: Lesson %d doppio teacher %s conflicts with LabBlock %d",
                    lesson.id, doppioTeacher.name, block.id);
            return true;
        }
    }
    return false;
}
```

### H5.2: Teacher unavailability (-28 violations)

**Problem**: LabBlock scheduled during teacher's vacation/unavailability period.

**Fix**: Already have `hasTeacherConflict()` for H1 - extend to check availability:
```java
public boolean hasH5Violation(LabBlock block, Timeslot startTimeslot) {
    Teacher teacher = block.getTeacher();
    if (teacher == null) return false;

    List<Timeslot> occupied = block.getOccupiedTimeslots();
    for (Timeslot ts : occupied) {
        // Check for VACATION or SICK availability at this time
        long count = TeacherAvailability.count(
            "teacher = ?1 and type in ('VACATION', 'SICK') " +
            "and startDateTime <= ?2 and endDateTime >= ?3",
            teacher, ts.endDateTime, ts.startDateTime);
        if (count > 0) {
            LOG.debugf("H5 violation: Teacher %s unavailable at %s", teacher.name, ts);
            return true;
        }
    }
    return false;
}

public boolean hasH5Violation(Lesson lesson, Timeslot timeslot) {
    Teacher teacher = lesson.getTeacher();
    if (teacher == null) return false;

    long count = TeacherAvailability.count(
        "teacher = ?1 and type in ('VACATION', 'SICK') " +
        "and startDateTime <= ?2 and endDateTime >= ?3",
        teacher, timeslot.endDateTime, timeslot.startDateTime);
    if (count > 0) {
        LOG.debugf("H5 violation: Teacher %s unavailable at %s", teacher.name, timeslot);
        return true;
    }
    return false;
}
```

### H16: Consecutive same-subject teacher-room (-8 violations)

**Problem**: Two consecutive same-subject lessons have different teacher or room.

**Note**: This is harder to validate during pre-assignment because we don't control what the solver will place. However, we CAN ensure that when we pin a lesson adjacent to an existing same-subject lesson, they share teacher and room.

**Fix**: Extend H15 check to also verify consistency:
```java
public boolean hasH16Violation(Lesson lesson, Timeslot proposedTimeslot, Room proposedRoom) {
    StudentGroup group = lesson.getStudentGroup();
    String subject = lesson.getSubject();
    Teacher teacher = lesson.getTeacher();
    if (group == null || subject == null || teacher == null) return false;

    LocalDate date = proposedTimeslot.getDate();

    // Find pinned same-subject lessons on same day
    List<Lesson> adjacentLessons = Lesson.find(
            "course.studentGroup = ?1 and course.subject = ?2 and " +
            "timeslot.date = ?3 and pinned = true and id != ?4",
            group, subject, date, lesson.id).list();

    for (Lesson existing : adjacentLessons) {
        // Check if they would be consecutive
        boolean wouldBeConsecutive =
            proposedTimeslot.startTime.equals(existing.timeslot.endTime) ||
            proposedTimeslot.endTime.equals(existing.timeslot.startTime);

        if (wouldBeConsecutive) {
            // H16: Must have same teacher and room
            if (!teacher.id.equals(existing.getTeacher().id)) {
                LOG.debugf("H16 violation: Different teachers for consecutive %s lessons", subject);
                return true;
            }
            if (proposedRoom != null && existing.room != null &&
                !proposedRoom.id.equals(existing.room.id)) {
                LOG.debugf("H16 violation: Different rooms for consecutive %s lessons", subject);
                return true;
            }
        }
    }
    return false;
}
```

### H-M2.1: Room suitability (-4 violations)

**Problem**: Lesson requiring lab tags is in a room without those tags.

**Fix**: Already partially handled in pre-assigners (room selection). Add explicit validation:
```java
public boolean hasRoomSuitabilityViolation(Lesson lesson, Room room) {
    if (lesson.course == null || lesson.course.requiredRoomTags == null ||
        lesson.course.requiredRoomTags.isEmpty()) {
        return false; // No room requirements
    }
    if (room == null || room.tags == null) {
        return true; // Required tags but no room/tags
    }
    if (!room.tags.containsAll(lesson.course.requiredRoomTags)) {
        LOG.debugf("H-M2 violation: Room %s missing required tags %s for lesson %d",
                room.name, lesson.course.requiredRoomTags, lesson.id);
        return true;
    }
    return false;
}

public boolean hasRoomSuitabilityViolation(LabBlock block, Room room) {
    if (block.course == null || block.course.requiredRoomTags == null ||
        block.course.requiredRoomTags.isEmpty()) {
        return false;
    }
    if (room == null || room.tags == null) {
        return true;
    }
    if (!room.tags.containsAll(block.course.requiredRoomTags)) {
        LOG.debugf("H-M2 violation: Room %s missing required tags %s for block %d",
                room.name, block.course.requiredRoomTags, block.id);
        return true;
    }
    return false;
}
```

---

## Unified PreAssignmentValidator Enhancement

Here's the updated `canPin()` method that checks ALL constraints:

```java
public boolean canPin(Lesson lesson, Timeslot timeslot, Room room) {
    if (lesson == null || timeslot == null || room == null) {
        return false;
    }

    // H1: Teacher conflict
    if (hasTeacherConflict(lesson.getTeacher(), timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H1 teacher conflict", lesson.id);
        return false;
    }

    // H2: Room conflict
    if (hasRoomConflict(room, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H2 room conflict", lesson.id);
        return false;
    }

    // H3: Student group conflict
    if (hasStudentGroupConflict(lesson.getStudentGroup(), timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H3 student group conflict", lesson.id);
        return false;
    }

    // H5: Teacher unavailability
    if (hasH5Violation(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H5 teacher unavailable", lesson.id);
        return false;
    }

    // H10: Preparazione before stage
    if (hasH10Violation(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H10 Preparazione after stage start", lesson.id);
        return false;
    }

    // H-M2: Room suitability
    if (hasRoomSuitabilityViolation(lesson, room)) {
        LOG.warnf("Cannot pin Lesson %d: H-M2 wrong room type", lesson.id);
        return false;
    }

    // H15: Same-day same-subject consecutive
    if (hasH15Violation(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H15 would create gap", lesson.id);
        return false;
    }

    // H16: Consecutive same-subject teacher-room consistency
    if (hasH16Violation(lesson, timeslot, room)) {
        LOG.warnf("Cannot pin Lesson %d: H16 inconsistent teacher/room", lesson.id);
        return false;
    }

    // H-D1: Doppio teacher conflict
    if (hasDoppioConflict(lesson, timeslot)) {
        LOG.warnf("Cannot pin Lesson %d: H-D1 doppio conflict", lesson.id);
        return false;
    }

    return true;
}

public boolean canPin(LabBlock block, Timeslot startTimeslot, Room room) {
    if (block == null || startTimeslot == null || room == null) {
        return false;
    }

    // Check each occupied timeslot for basic conflicts
    List<Timeslot> occupied = block.getOccupiedTimeslots();
    if (occupied == null || occupied.isEmpty()) {
        // Fall back to checking start slot only
        occupied = List.of(startTimeslot);
    }

    for (Timeslot ts : occupied) {
        if (hasTeacherConflict(block.getTeacher(), ts)) {
            LOG.warnf("Cannot pin LabBlock %d: H1 teacher conflict at %s", block.id, ts);
            return false;
        }
        if (hasRoomConflict(room, ts)) {
            LOG.warnf("Cannot pin LabBlock %d: H2 room conflict at %s", block.id, ts);
            return false;
        }
        if (hasStudentGroupConflict(block.getStudentGroup(), ts)) {
            LOG.warnf("Cannot pin LabBlock %d: H3 student group conflict at %s", block.id, ts);
            return false;
        }
    }

    // H5: Teacher unavailability
    if (hasH5Violation(block, startTimeslot)) {
        LOG.warnf("Cannot pin LabBlock %d: H5 teacher unavailable", block.id);
        return false;
    }

    // H9: Block valid placement
    if (hasH9Violation(block, startTimeslot)) {
        LOG.warnf("Cannot pin LabBlock %d: H9 block doesn't fit", block.id);
        return false;
    }

    // H-M2: Room suitability
    if (hasRoomSuitabilityViolation(block, room)) {
        LOG.warnf("Cannot pin LabBlock %d: H-M2 wrong room type", block.id);
        return false;
    }

    // H-D1: Doppio teacher conflict
    if (hasDoppioConflict(block, startTimeslot, room)) {
        LOG.warnf("Cannot pin LabBlock %d: H-D1 doppio conflict", block.id);
        return false;
    }

    return true;
}
```

---

## Estimated Effort and Impact

| Constraint | Current Violations | Fix Effort | Expected After Fix |
|------------|-------------------|------------|-------------------|
| H15 | -1473 (88%) | 1-2 hours | ~0 |
| H10 | -70 (4%) | 30 min | 0 |
| H9 | -54 (9 blocks) | 30 min | 0 |
| H-D1.3 | -31 (2%) | 30 min | 0 |
| H5.2 | -28 (2%) | 30 min | 0 |
| H16 | -8 (<1%) | 30 min | 0 |
| H-M2.1 | -4 (<1%) | Already done | 0 |
| **TOTAL** | **-1668** | **~4-5 hours** | **~0** |

---

## Files to Modify

1. **`src/main/java/com/school/planner/service/PreAssignmentValidator.java`**
   - Add H5, H9, H10, H15, H16, H-M2, H-D1 validation methods
   - Update `canPin()` methods to call all checks

2. **`src/main/java/com/school/planner/service/ProfessionalizzantiPreAssigner.java`**
   - Already uses `validator.canPin()` - no changes needed if validator is updated

3. **`src/main/java/com/school/planner/service/LabBlockPreAssigner.java`**
   - Already uses `validator.canPin()` - no changes needed if validator is updated

4. **`src/main/java/com/school/planner/service/AcconciaturaPreAssigner.java`**
   - Already uses `validator.canPin()` - no changes needed if validator is updated

---

## What This Won't Fix

Even with full validation, some violations may persist because:

1. **No valid slot exists**: The constraint is fundamentally infeasible for some entities
2. **Pre-assignment scope**: Some entities simply can't be pre-assigned without conflicts
3. **Cross-step conflicts**: A later pre-assignment step conflicts with an earlier one

**Fallback**: Skip entities that can't be conflict-free pre-assigned. Let solver handle them.

---

## Conclusion

**Comprehensive quick fix**: Extend `PreAssignmentValidator` with ALL hard constraint checks (~200 lines, 4-5 hours).

This is a **purely additive change** - no existing logic is modified, only extended. The validation methods are independent and can be tested individually.

**Expected result**: Pre-assignment produces a conflict-free pinned subset, solver starts from a clean state with minimal remaining violations (only those that are fundamentally infeasible).
