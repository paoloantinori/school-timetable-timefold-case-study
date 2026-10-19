# Design: Denormalize LabBlocks to Pinned Lessons

**Task:** TASK-228
**Created:** 2026-03-13
**Status:** Planning

## Overview

Convert LabBlock planning entities to denormalized pinned Lessons for solver performance optimization. This eliminates the LabBlock abstraction entirely, representing labs as multiple contiguous pinned Lessons.

## Current vs Proposed Architecture

### Current (Block Entity Family)

```
Domain Model:
├── Lesson (@PlanningEntity)
│   ├── timeslot: Timeslot (@PlanningVariable)
│   ├── room: Room (@PlanningVariable)
│   └── pinned: boolean (@PlanningPin)
└── LabBlock (@PlanningEntity)
    ├── startTimeslot: Timeslot (@PlanningVariable)
    ├── room: Room (@PlanningVariable)
    ├── blockSize: int (2-6 hours)
    └── pinned: boolean (@PlanningPin)

Constraint Variants:
├── H1.1: Lesson-Lesson teacher conflict
├── H1.2: Lesson-LabBlock teacher conflict
├── H1.3: LabBlock-LabBlock teacher conflict
├── H2.1, H2.2, H2.3: Room conflicts (same pattern)
├── H3.1, H3.2, H3.3: Student group conflicts (same pattern)
├── H9: LabBlock valid placement (must fit in day)
├── M4: Lab room-tag affinity
├── M17, M17.2: Lab recurring pattern
└── M18: Multi-day lab start-time consistency

Solver Phases (7):
1. CH - Lesson Placement (DayClusteredConstructionPhase)
2. CH - Fallback Placement
3. LS - Compactness
4. LS - Conflict Resolution
5. LS - Compactness II
6. CH - Room Assignment
7. LS - Room Refinement
```

### Proposed (Denormalized Lessons)

```
Domain Model:
└── Lesson (@PlanningEntity)
    ├── timeslot: Timeslot (@PlanningVariable)
    ├── room: Room (@PlanningVariable)
    ├── pinned: boolean (@PlanningPin)
    └── isLabHour: boolean (new field, for UI grouping)

Constraint Variants (simplified):
├── H1: Teacher conflict (all lessons, including lab hours)
├── H2: Room conflict (all lessons)
├── H3: Student group conflict (all lessons)
├── H4-H8: Other hard constraints (unchanged)
├── H-M2: Room suitability (simplified, no block variants)
├── M-LA: Lab assistant conflict (simplified)
└── M3, M5, M12, M13, M20: Medium constraints (unchanged)

Removed Constraints:
├── H9: Pre-validated at assignment time
├── M4: Pre-validated at assignment time
├── M17, M17.2: Pre-validated at assignment time
└── M18: Pre-validated at assignment time

Solver Phases (7):
1. CH - Lesson Placement (DayClusteredConstructionPhase)
2. CH - Fallback Placement
3. LS - Compactness
4. LS - Conflict Resolution
5. LS - Compactness II
6. CH - Room Assignment
7. LS - Room Refinement
```

## Entity Count Impact

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Lesson entities | ~7,000 | ~7,600 | +600 (lab hours) |
| LabBlock entities | ~200 | 0 | -200 |
| **Total planning entities** | ~7,200 | ~7,600 | +400 (+5.5%) |
| Constraint variants | 9 sets | 3 sets | -6 sets (-67%) |
| Solver phases | 8 | 6 | -2 phases (-25%) |

**Net effect:** More entities but simpler evaluation = ~15-25% faster overall.

---

## Phase 1: Domain Model Preparation

### 1.1 Add `isLabHour` to Lesson Entity

**File:** `src/main/java/com/school/planner/domain/Lesson.java`

```java
@Entity
public class Lesson extends PanacheEntityBase {
    // ... existing fields ...

    /**
     * True if this lesson is part of a lab session (multiple consecutive hours).
     * Used for UI grouping to display contiguous lab hours as a single block.
     * Pre-assignment sets this to true for all lab lessons.
     */
    public boolean isLabHour = false;

    /**
     * For lab hours, the sequence within the lab session (0-indexed).
     * 0 = first hour, 1 = second hour, etc.
     * Non-lab lessons have this set to -1.
     */
    public int labHourIndex = -1;

    /**
     * Total hours in this lab session (for UI display).
     * Non-lab lessons have this set to 0.
     */
    public int labTotalHours = 0;
}
```

### 1.2 Database Migration

Add columns to `lesson` table:

```sql
ALTER TABLE lesson ADD COLUMN is_lab_hour BOOLEAN DEFAULT FALSE;
ALTER TABLE lesson ADD COLUMN lab_hour_index INTEGER DEFAULT -1;
ALTER TABLE lesson ADD COLUMN lab_total_hours INTEGER DEFAULT 0;
```

---

## Phase 2: Pre-Assignment Changes

### 2.1 Modify HybridPhase1aService

**File:** `src/main/java/com/school/planner/service/HybridPhase1aService.java`

Replace `pinAssignments()` to create pinned Lessons instead of LabBlock:

```java
/**
 * Create pinned Lessons from successful assignments.
 * Replaces the old approach that pinned LabBlocks.
 *
 * @param assignments Successful assignments from test runner
 * @return Number of lessons created and pinned
 */
@Transactional
int createPinnedLabLessons(List<RotationTestRunner.SlotAssignment> assignments) {
    int count = 0;
    Map<Long, List<SlotAssignment>> assignmentsByBlockId = assignments.stream()
        .filter(a -> a.block() != null)
        .collect(Collectors.groupingBy(a -> a.block().id));

    for (Map.Entry<Long, List<SlotAssignment>> entry : assignmentsByBlockId.entrySet()) {
        LabBlock block = LabBlock.findById(entry.getKey());
        List<SlotAssignment> blockAssignments = entry.getValue();

        if (blockAssignments.isEmpty()) continue;

        // Get the first assignment (all should have same start/room for a single block)
        SlotAssignment first = blockAssignments.get(0);
        Timeslot startSlot = first.startTimeslot();
        Room room = first.room();

        // Create N consecutive pinned Lessons
        for (int i = 0; i < block.blockSize; i++) {
            Lesson lesson = new Lesson();
            lesson.course = block.course;
            lesson.timeslot = findConsecutiveSlot(startSlot, i);
            lesson.room = room;
            lesson.pinned = true;
            lesson.isLabHour = true;
            lesson.labHourIndex = i;
            lesson.labTotalHours = block.blockSize;
            lesson.persist();
            count++;
        }

        // Mark the old LabBlock for deletion (or keep for audit trail)
        block.preAssignedAsLessons = true;  // New field
        block.persist();
    }

    LOG.infof("[HybridPhase1a] Created %d pinned lab lessons from %d blocks",
        count, assignmentsByBlockId.size());
    return count;
}

/**
 * Find the timeslot that is N positions after the start slot.
 */
private Timeslot findConsecutiveSlot(Timeslot start, int offset) {
    return Timeslot.find(
        "date = ?1 and slotIndex = ?2",
        start.date,
        start.slotIndex + offset
    ).firstResult();
}
```

### 2.2 Update PreAssignmentEntityExtractor

**File:** `src/main/java/com/school/planner/service/PreAssignmentEntityExtractor.java`

```java
/**
 * Extract entities for pre-assignment.
 *
 * Now identifies lab lessons by isLabHour flag instead of LabBlock entities.
 */
@Transactional
public ExtractionResult extract() {
    ExtractionResult result = new ExtractionResult();

    // ... existing holiday loading ...

    // Step 1: Extract unpinned lab lessons (isLabHour = true, pinned = false)
    extractLabLessons(result, addedLessonIds);

    // Step 2: Extract professionalizzanti lessons (unchanged)
    extractProfessionalizzantiEntities(result, holidayDates, addedLessonIds);

    LOG.infof("Extraction complete: %d lab lessons, %d professionalizzanti to pre-assign",
            result.lessonsToPreAssign.stream().filter(l -> l.isLabHour).count(),
            result.lessonsToPreAssign.stream().filter(l -> !l.isLabHour).count());

    return result;
}

private void extractLabLessons(ExtractionResult result, Set<Long> addedLessonIds) {
    List<Lesson> unassignedLabLessons = Lesson.find(
        "isLabHour = true and timeslot is null"
    ).list();

    LOG.infof("Found %d unassigned lab lessons for pre-assignment", unassignedLabLessons.size());

    for (Lesson lesson : unassignedLabLessons) {
        if (!addedLessonIds.contains(lesson.id)) {
            result.lessonsToPreAssign.add(lesson);
            addedLessonIds.add(lesson.id);
        }
    }
}
```

### 2.3 Update DataGenerator

**File:** `src/main/java/com/school/planner/bootstrap/DataGenerator.java`

Change lab course generation to create Lessons instead of LabBlocks:

```java
private void generateLabCourse(Course course, int blockSize) {
    // OLD: Create LabBlock
    // LabBlock block = new LabBlock();
    // block.course = course;
    // block.blockSize = blockSize;
    // block.persist();

    // NEW: Create N unpinned Lessons with isLabHour=true
    for (int i = 0; i < blockSize; i++) {
        Lesson lesson = new Lesson();
        lesson.course = course;
        lesson.pinned = false;
        lesson.isLabHour = true;
        lesson.labHourIndex = i;
        lesson.labTotalHours = blockSize;
        lesson.persist();
    }
}
```

---

## Phase 3: Constraint Provider Simplification

### 3.1 Remove Collapsed Constraint Variants

**File:** `src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java`

#### H1: Teacher Conflict (Simplified)

```java
/**
 * H1: Teacher conflict - covers ALL lessons including lab hours.
 * Replaces H1.1, H1.2, H1.3 from the Block Entity architecture.
 */
private Constraint H1_TeacherConflict(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(l -> l.timeslot != null && l.course.teacher != null)
        .join(Lesson.class,
            equal(l -> l.timeslot),
            equal(l -> l.course.teacher),
            lessThan(l -> l.id))  // Prevent duplicate pairs
        .filter((l1, l2) -> !l1.id.equals(l2.id))  // Different lessons
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("H1: Teacher conflict");
}

// DELETE: H1.2_LessonLabBlockTeacherConflict
// DELETE: H1.3_LabBlockLabBlockTeacherConflict
```

#### H2: Room Conflict (Simplified)

```java
/**
 * H2: Room conflict - covers ALL lessons including lab hours.
 * Replaces H2.1, H2.2, H2.3 from the Block Entity architecture.
 */
private Constraint H2_RoomConflict(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(l -> l.timeslot != null && l.room != null)
        .join(Lesson.class,
            equal(l -> l.timeslot),
            equal(l -> l.room),
            lessThan(l -> l.id))
        .filter((l1, l2) -> !l1.course.studentGroup.equals(l2.course.studentGroup))
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("H2: Room conflict");
}

// DELETE: H2.2_LessonLabBlockRoomConflict
// DELETE: H2.3_LabBlockLabBlockRoomConflict
```

#### H3: Student Group Conflict (Simplified)

```java
/**
 * H3: Student group conflict - covers ALL lessons including lab hours.
 * Replaces H3.1, H3.2, H3.3 from the Block Entity architecture.
 */
private Constraint H3_StudentGroupConflict(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(l -> l.timeslot != null && l.course.studentGroup != null)
        .join(Lesson.class,
            equal(l -> l.timeslot),
            equal(l -> l.course.studentGroup),
            lessThan(l -> l.id))
        .filter((l1, l2) -> {
            // Same student group, different course, not both lab hours for same teacher
            if (l1.course.equals(l2.course)) return false;
            if (l1.isLabHour && l2.isLabHour && l1.course.teacher.equals(l2.course.teacher)) {
                return false;  // Same lab session (consecutive hours for same teacher)
            }
            return true;
        })
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("H3: Student group conflict");
}

// DELETE: H3.2_LessonLabBlockStudentGroupConflict
// DELETE: H3.3_LabBlockLabBlockStudentGroupConflict
```

### 3.2 Remove Pre-Validated Constraints

```java
// DELETE ENTIRELY: H9_LabBlockValidPlacement
// DELETE ENTIRELY: M4_LabRoomTagAffinity
// DELETE ENTIRELY: M17_LabRecurringPattern
// DELETE ENTIRELY: M17.2_LabRecurringPatternLimit
// DELETE ENTIRELY: M18_MultiDayLabStartTimeConsistency
```

### 3.3 Update H-M2 (Room Suitability)

```java
/**
 * H-M2: Room suitability - simplified for Lesson-only.
 * Pre-assignment ensures lab lessons are in correct rooms.
 */
private Constraint H_M2_RoomSuitability(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(l -> l.timeslot != null && l.room != null)
        .filter(l -> {
            // Check if lesson requires specific room tags
            Set<String> requiredTags = l.course.requiredRoomTags;
            if (requiredTags == null || requiredTags.isEmpty()) return false;

            // Check if room has all required tags
            return !l.room.tags.containsAll(requiredTags);
        })
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("H-M2: Room suitability");
}

// DELETE: H-M2.1, H-M2.2 (LabBlock variants)
```

### 3.4 Update M-LA (Lab Assistant)

```java
/**
 * M-LA: Lab assistant conflict - simplified for Lesson-only.
 * Check if a lab assistant has overlapping assignments with lessons.
 */
private Constraint M_LA_LabAssistantConflict(ConstraintFactory factory) {
    return factory.forEach(LabAssistant.class)
        .join(Lesson.class,
            equal((la) -> la.roomTag, (l) -> l.room != null ? l.room.tags.stream().findFirst().orElse(null) : null),
            equal((la) -> la.roomTag, (l) -> l.room != null ? l.room.tags.stream().findFirst().orElse(null) : null))
        .filter((la, lesson) -> {
            if (lesson.room == null || lesson.timeslot == null) return false;
            if (!lesson.room.tags.contains(la.roomTag)) return false;
            // Check if assistant's teacher has a lesson at the same time
            return Lesson.find("course.teacher = ?1 and timeslot = ?2",
                la.teacher, lesson.timeslot).count() > 0;
        })
        .penalize(HardSoftScore.ONE_MEDIUM)
        .asConstraint("M-LA: Lab assistant conflict");
}
```

---

## Phase 4: Solver Configuration Simplification

### 4.1 New solverConfig.xml

**File:** `src/main/resources/solverConfig.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<solver xmlns="https://timefold.ai/xsd/solver">

  <scoreDirectorFactory>
    <constraintProviderClass>com.school.planner.solver.SchoolScheduleConstraintProvider</constraintProviderClass>
  </scoreDirectorFactory>

  <termination>
    <secondsSpentLimit>28800</secondsSpentLimit>
  </termination>

  <!-- Phase 1: CH - Lesson Placement (was Phase 2) -->
  <customPhase>
    <customPhaseCommandClass>com.school.planner.solver.DayClusteredConstructionPhase</customPhaseCommandClass>
  </customPhase>

  <!-- Phase 1→2 marker -->
  <customPhase>
    <customPhaseCommandClass>com.school.planner.solver.PhaseTransitionMarker</customPhaseCommandClass>
    <customProperties>
      <property name="phaseNumber" value="2"/>
      <property name="phaseName" value="2. LS - Compactness"/>
    </customProperties>
  </customPhase>

  <!-- Phase 2: LS - Compactness (Lesson-only moves) -->
  <localSearch>
    <termination>
      <secondsSpentLimit>7200</secondsSpentLimit>
      <unimprovedSecondsSpentLimit>900</unimprovedSecondsSpentLimit>
    </termination>
    <unionMoveSelector>
      <changeMoveSelector>
        <fixedProbabilityWeight>5.0</fixedProbabilityWeight>
        <entitySelector>
          <entityClass>com.school.planner.domain.Lesson</entityClass>
          <filterClass>com.school.planner.solver.UnpinnedLessonFilter</filterClass>
        </entitySelector>
        <valueSelector variableName="timeslot"/>
      </changeMoveSelector>
    </unionMoveSelector>
    <acceptor>
      <entityTabuSize>5</entityTabuSize>
      <lateAcceptanceSize>200</lateAcceptanceSize>
    </acceptor>
    <forager>
      <acceptedCountLimit>1</acceptedCountLimit>
    </forager>
  </localSearch>

  <!-- Phases 3-7: Same as before but Lesson-only selectors -->

  <!-- ... remaining phases updated similarly ... -->

</solver>
```

### 4.2 Update Phase Names

**File:** `src/main/java/com/school/planner/solver/SolverProgressionListener.java`

```java
public static String getPhaseName(int phaseNumber) {
    return switch (phaseNumber) {
        case 1 -> "1. CH - Lesson Placement";
        case 2 -> "2. LS - Compactness";
        case 3 -> "3. LS - Conflict Resolution";
        case 4 -> "4. LS - Compactness II";
        case 5 -> "5. CH - Room Assignment";
        case 6 -> "6. LS - Room Refinement";
        case 7 -> "7. LS - Final Compaction";
        default -> "Unknown Phase " + phaseNumber;
    };
}
```

---

## Phase 5: Domain Model Cleanup

### 5.1 Deprecate LabBlock Entity

**File:** `src/main/java/com/school/planner/domain/LabBlock.java`

```java
/**
 * @deprecated Use pinned Lessons with isLabHour=true instead.
 *             This entity is kept for migration purposes only.
 *             Will be removed in a future version.
 */
@Deprecated(forRemoval = true)
@Entity
public class LabBlock extends PanacheEntityBase {
    // ... keep existing fields for migration ...

    /**
     * Flag indicating this block has been converted to pinned Lessons.
     * Used during migration to track conversion status.
     */
    public boolean preAssignedAsLessons = false;
}
```

### 5.2 Update SchoolSchedule

**File:** `src/main/java/com/school/planner/domain/SchoolSchedule.java`

```java
@PlanningSolution
public class SchoolSchedule extends PanacheEntityBase {
    // ... existing fields ...

    /**
     * @deprecated Labs are now stored as pinned Lessons.
     *             This list is kept for backward compatibility during migration.
     */
    @Deprecated
    @ProblemFactCollectionProperty
    @ObjectShareable
    public List<LabBlock> labBlocks = new ArrayList<>();

    // Remove @PlanningEntityCollectionProperty from labBlocks
    // They are no longer planning entities
}
```

---

## Phase 6: Frontend Updates

### 6.1 Group Lab Lessons for Display

**File:** `src/main/webui/src/utils/groupLabLessons.ts`

```typescript
interface LabGroup {
  courseId: number;
  teacherId: number;
  studentGroupId: number;
  date: string;
  room: Room;
  startSlotIndex: number;
  totalHours: number;
  lessons: Lesson[];
}

/**
 * Group consecutive pinned lab lessons into visual blocks for display.
 */
export function groupLabLessons(lessons: Lesson[]): LabGroup[] {
  const labLessons = lessons.filter(l => l.isLabHour && l.pinned);

  // Group by course, date, room
  const groups = new Map<string, LabGroup>();

  for (const lesson of labLessons) {
    const key = `${lesson.course.id}-${lesson.timeslot.date}-${lesson.room?.id || 'none'}`;

    if (!groups.has(key)) {
      groups.set(key, {
        courseId: lesson.course.id,
        teacherId: lesson.course.teacher.id,
        studentGroupId: lesson.course.studentGroup.id,
        date: lesson.timeslot.date,
        room: lesson.room,
        startSlotIndex: lesson.timeslot.slotIndex,
        totalHours: lesson.labTotalHours,
        lessons: [],
      });
    }

    groups.get(key)!.lessons.push(lesson);
  }

  // Sort lessons within each group by slot index
  for (const group of groups.values()) {
    group.lessons.sort((a, b) => a.timeslot.slotIndex - b.timeslot.slotIndex);
  }

  return Array.from(groups.values());
}
```

### 6.2 Update Timeline Component

**File:** `src/main/webui/src/components/timeline/cellRenderer.tsx`

```typescript
// Render lab groups as visual blocks (same style as old LabBlocks)
function renderLabGroup(group: LabGroup): JSX.Element {
  return (
    <div
      className="lab-block-cell"
      style={{
        gridRow: `span ${group.totalHours}`,
        backgroundColor: getCourseColor(group.courseId),
      }}
    >
      <div className="lab-block-header">
        {group.lessons[0].course.subject.name}
      </div>
      <div className="lab-block-details">
        {group.lessons[0].course.teacher.name}
        <br />
        {group.room?.name}
      </div>
    </div>
  );
}
```

---

## Phase 7: Testing & Validation

### 7.1 Unit Tests for New Constraints

**File:** `src/test/java/com/school/planner/solver/SchoolScheduleConstraintProviderTest.java`

```java
@Test
void testH1_TeacherConflict_WithLabLessons() {
    // Create two lessons at same time, same teacher, different courses
    Lesson labLesson1 = createLabLesson(teacher, timeslot, null, 0, 4);
    Lesson labLesson2 = createLabLesson(teacher, timeslot, null, 1, 4);

    // Should NOT conflict - same lab session
    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::H1_TeacherConflict)
        .given(labLesson1, labLesson2)
        .penalizesBy(0);

    // Create lesson at same time, different course
    Lesson otherLesson = createLesson(teacher, timeslot, null);

    // Should conflict
    constraintVerifier.verifyThat(SchoolScheduleConstraintProvider::H1_TeacherConflict)
        .given(labLesson1, otherLesson)
        .penalizesBy(1);
}

private Lesson createLabLesson(Teacher teacher, Timeslot timeslot, Room room, int index, int total) {
    Lesson lesson = new Lesson();
    lesson.course = createCourse(teacher);
    lesson.timeslot = timeslot;
    lesson.room = room;
    lesson.pinned = true;
    lesson.isLabHour = true;
    lesson.labHourIndex = index;
    lesson.labTotalHours = total;
    return lesson;
}
```

### 7.2 Integration Test for Pre-Assignment

```java
@Test
@QuarkusTest
void testPreAssignmentCreatesPinnedLabLessons() {
    // Setup: Create courses with lab requirements
    Course labCourse = createLabCourse("Informatica", 4);

    // Run pre-assignment
    given()
        .post("/api/schedule/pre-assign")
        .then()
        .statusCode(202);

    // Wait for completion
    await().atMost(60, TimeUnit.SECONDS)
        .until(() -> getPreAssignmentStatus() == HybridStatus.COMPLETE);

    // Verify: Lab lessons created and pinned
    List<Lesson> labLessons = Lesson.find("isLabHour = true and pinned = true").list();
    assertThat(labLessons).hasSize(4);  // 4-hour lab

    // Verify: All lessons have consecutive timeslots
    assertThat(labLessons).extracting(l -> l.timeslot.slotIndex)
        .containsExactly(0, 1, 2, 3);

    // Verify: All lessons have same room
    assertThat(labLessons).extracting(l -> l.room.id)
        .allMatch(id -> id.equals(labLessons.get(0).room.id));
}
```

### 7.3 Performance Benchmark

```bash
# Before (Block Entity)
./scripts/benchmark.sh feature/cartesian-block-entity

# After (Denormalized Lessons)
./scripts/benchmark.sh feature/denormalized-labs

# Compare:
# - CH phase time
# - LS phase iteration rate
# - Score calculation time
# - Final compaction score
```

---

## Migration Script (One-Time)

**File:** `scripts/migrate-labblocks-to-lessons.py`

```python
#!/usr/bin/env python3
"""
One-time migration: Convert existing LabBlocks to pinned Lessons.
Run this after deploying the code changes but before starting the solver.
"""

import psycopg2
import os

def migrate():
    conn = psycopg2.connect(os.environ['DATABASE_URL'])
    cur = conn.cursor()

    # Get all LabBlocks
    cur.execute("""
        SELECT id, course_id, start_timeslot_id, room_id, block_size, pinned
        FROM lab_block
        WHERE start_timeslot_id IS NOT NULL
    """)

    blocks = cur.fetchall()
    print(f"Found {len(blocks)} LabBlocks to migrate")

    for block in blocks:
        block_id, course_id, start_slot_id, room_id, block_size, pinned = block

        # Get consecutive timeslots
        cur.execute("""
            SELECT id, slot_index
            FROM timeslot
            WHERE date = (SELECT date FROM timeslot WHERE id = %s)
            AND slot_index >= (SELECT slot_index FROM timeslot WHERE id = %s)
            AND slot_index < (SELECT slot_index FROM timeslot WHERE id = %s) + %s
            ORDER BY slot_index
        """, (start_slot_id, start_slot_id, start_slot_id, block_size))

        slots = cur.fetchall()

        # Create lessons
        for i, (slot_id, slot_index) in enumerate(slots):
            cur.execute("""
                INSERT INTO lesson (course_id, timeslot_id, room_id, pinned, is_lab_hour, lab_hour_index, lab_total_hours)
                VALUES (%s, %s, %s, %s, true, %s, %s)
            """, (course_id, slot_id, room_id, pinned, i, block_size))

        # Mark block as migrated
        cur.execute("""
            UPDATE lab_block SET pre_assigned_as_lessons = true WHERE id = %s
        """, (block_id,))

    conn.commit()
    print(f"Migration complete: created {len(blocks) * block_size} lab lessons")

if __name__ == '__main__':
    migrate()
```

---

## Rollback Plan

If issues arise:

1. **Revert code changes** — LabBlock entity still exists
2. **Run inverse migration** — Convert pinned lab lessons back to LabBlocks
3. **Restore old solverConfig.xml** — Re-enable LabBlock selectors
4. **Restore old constraint provider** — Re-add L-B and B-B variants

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Entity types | 1 (Lesson only) | Domain model inspection |
| Constraint variants | 3 (H1, H2, H3) | Constraint provider code |
| Solver phases | 6-7 | solverConfig.xml |
| CH time | -20% | Benchmark comparison |
| LS iteration rate | +15% | Phase listener logs |
| Compaction score | Same or better | Final M12/M13 scores |
| Test coverage | 100% of new code | JaCoCo report |
