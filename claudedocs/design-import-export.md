# Design: Schedule Import/Export Feature

**Status:** Draft
**Created:** 2026-03-06
**Author:** Claude (AI-assisted design)

## 1. Problem Statement

### 1.1 Goal
Enable A/B testing of solver configurations by exporting a complete schedule state to JSON and importing it back to restore identical starting conditions.

### 1.2 Current Issues

| Issue | Description | Root Cause |
|-------|-------------|------------|
| Import fails | `"Row was already updated or deleted by another transaction for entity [Timeslot with id '1']"` | Hibernate persistence context conflict |
| Large payload | 7.1MB JSON with 7538 lessons, 1135 labblocks | Need efficient batch processing |
| FK dependencies | Entities have complex relationships | Must insert in correct order |

### 1.3 Root Cause Analysis

The current implementation uses:
1. `PanacheEntity.deleteAll()` - Batch delete via HQL
2. `em.merge()` - Attempt to reattach detached entities

**Problem (CORRECTED - see research 2026-03-06):** The error occurs because `merge()` on **detached** entities (deserialized from JSON with IDs) fails when those IDs were deleted by `deleteAll()`. Hibernate's `merge()`:
1. First tries to load the entity from DB to compare states
2. Detects the entity doesn't exist (was deleted)
3. Attempts to INSERT, but conflicts with version tracking or FK constraints

**Key insight:** The issue is NOT "stale references in persistence context after deleteAll()". It's `merge()` behavior with deleted detached entities. The fix `em.flush() + em.clear()` after delete IS correct, but then we must use native SQL INSERT (not merge) for the import.

## 2. Solution Design

### 2.1 Approach: Native SQL with Explicit IDs

**Key Insight:** Use native SQL INSERT statements with explicit IDs to bypass Hibernate's merge logic entirely.

```
┌─────────────────────────────────────────────────────────────────┐
│                     IMPORT PIPELINE                              │
├─────────────────────────────────────────────────────────────────┤
│  1. VALIDATE                                                     │
│     └── Check all FK references exist in import data             │
│                                                                  │
│  2. DELETE (Native SQL)                                          │
│     └── TRUNCATE all tables (faster than DELETE, resets seq)     │
│                                                                  │
│  3. INSERT (Native SQL with explicit IDs)                        │
│     └── Insert in dependency order (parents first)               │
│     └── Plain INSERT (no ON CONFLICT needed - table is empty)    │
│                                                                  │
│  4. RESET SEQUENCES                                              │
│     └── Set PostgreSQL sequences to max(id) + 1                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Entity Dependency Order

```
Level 0 (No dependencies):
├── Timeslot
├── Room
├── Teacher
└── StudentGroup

Level 1 (Depends on Level 0):
├── SchoolHoliday (no FK)
├── StagePattern → StudentGroup
├── TeacherAvailability → Teacher
├── LabAssistant → Teacher
└── LabRotation → Teacher, StudentGroup

Level 2 (Depends on Level 0 + 1):
└── Course → Teacher, StudentGroup

Level 3 (Depends on Level 0 + 2):
├── Lesson → Course, Room, Timeslot
└── LabBlock → Course, Room, Timeslot
```

### 2.3 API Specification

#### Export Endpoint

```
GET /api/schedule/export

Response 200:
{
  "timeslots": [...],
  "rooms": [...],
  "teachers": [...],
  "studentGroups": [...],
  "schoolHolidays": [...],
  "stagePatterns": [...],
  "teacherAvailabilities": [...],
  "labAssistants": [...],
  "labRotations": [...],
  "courses": [...],
  "lessons": [...],
  "labBlocks": [...],
  "score": "...",
  "solverStatus": "..."
}

Response 500:
{ "error": "Export failed: ..." }
```

#### Import Endpoint

```
POST /api/schedule/import
Content-Type: application/json

Request Body: Same format as export response

Response 200:
{ "message": "Schedule imported successfully",
  "stats": {
    "timeslots": 1137,
    "lessons": 7538,
    "labBlocks": 1135,
    ...
  }
}

Response 400:
{ "error": "Validation failed: Lesson 123 references non-existent timeslot 456" }

Response 500:
{ "error": "Import failed: ..." }
```

## 3. Implementation Design

### 3.1 ImportService Refactor

```java
@ApplicationScoped
public class ImportService {

    @Inject
    EntityManager em;

    @Inject
    DataSource dataSource;  // For direct JDBC access

    /**
     * Import a complete schedule using native SQL.
     * This bypasses Hibernate's merge logic to avoid persistence context conflicts.
     */
    @Transactional
    public ImportResult importSchedule(SchoolSchedule schedule) {
        // Phase 1: Validate all references
        validateReferences(schedule);

        // Phase 2: Truncate all tables (native SQL)
        truncateAllTables();

        // Phase 3: Insert in dependency order (native SQL with explicit IDs)
        ImportResult result = new ImportResult();
        insertEntities(schedule, result);

        // Phase 4: Reset sequences
        resetSequences();

        return result;
    }

    private void truncateAllTables() {
        // Disable FK checks, truncate, re-enable
        em.createNativeQuery("SET CONSTRAINTS ALL DEFERRED").executeUpdate();

        // Truncate in reverse dependency order
        String[] tables = {
            "lab_block", "lesson", "course",
            "lab_rotation", "lab_assistant", "teacher_availability",
            "stage_pattern", "school_holiday",
            "student_group", "teacher", "room", "timeslot"
        };

        for (String table : tables) {
            em.createNativeQuery("TRUNCATE TABLE " + table + " CASCADE").executeUpdate();
        }

        em.flush();
        em.clear();  // Clear persistence context
    }

    private void insertEntities(SchoolSchedule schedule, ImportResult result) {
        // Use JDBC batch for performance
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Level 0
            insertTimeslots(conn, schedule.getTimeslots());
            insertRooms(conn, schedule.getRooms());
            insertTeachers(conn, schedule.getTeachers());
            insertStudentGroups(conn, schedule.getStudentGroups());

            // Level 1
            insertSchoolHolidays(conn, schedule.getSchoolHolidays());
            insertStagePatterns(conn, schedule.getStagePatterns());
            insertTeacherAvailabilities(conn, schedule.getTeacherAvailabilities());
            insertLabAssistants(conn, schedule.getLabAssistants());
            insertLabRotations(conn, schedule.getLabRotations());

            // Level 2
            insertCourses(conn, schedule.getCourses());

            // Level 3
            insertLessons(conn, schedule.getLessons());
            insertLabBlocks(conn, schedule.getLabBlocks());

            conn.commit();
        }
    }
}
```

### 3.2 JDBC Batch Insert Pattern

```java
private void insertTimeslots(Connection conn, List<Timeslot> timeslots) throws SQLException {
    // Plain INSERT - no ON CONFLICT needed because TRUNCATE empties the table
    String sql = "INSERT INTO timeslot (id, date, slot_index, day_of_week, in_period) VALUES (?, ?, ?, ?, ?)";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        for (Timeslot t : timeslots) {
            ps.setLong(1, t.id);
            ps.setDate(2, java.sql.Date.valueOf(t.date));
            ps.setInt(3, t.slotIndex);
            ps.setInt(4, t.dayOfWeek);
            ps.setBoolean(5, t.inPeriod);
            ps.addBatch();
        }
        ps.executeBatch();
    }
}

// CRITICAL: After all inserts, reset sequences!
// Without this, subsequent JPA persist() will fail with duplicate key violations
private void resetSequence(String table, String sequence) {
    em.createNativeQuery(String.format(
        "SELECT setval('%s', COALESCE((SELECT MAX(id) FROM %s), 0) + 1, false)",
        sequence, table
    )).getSingleResult();
}
```

### 3.3 Sequence Reset

```java
private void resetSequences() {
    // Reset each sequence to max(id) + 1
    String[] sequences = {
        "timeslot_id_seq", "room_id_seq", "teacher_id_seq",
        "student_group_id_seq", "course_id_seq", "lesson_id_seq", "lab_block_id_seq"
    };

    String[] tables = {
        "timeslot", "room", "teacher", "student_group", "course", "lesson", "lab_block"
    };

    for (int i = 0; i < sequences.length; i++) {
        em.createNativeQuery(String.format(
            "SELECT setval('%s', COALESCE((SELECT MAX(id) FROM %s), 0) + 1, false)",
            sequences[i], tables[i]
        )).getSingleResult();
    }
}
```

### 3.4 ElementCollection Handling

Entities with `@ElementCollection` fields require separate INSERT statements into collection tables:

```java
// Room has @ElementCollection Set<String> tags
// This creates a separate table: room_tags (room_id, tags)

private void insertRoomTags(Connection conn, List<Room> rooms) throws SQLException {
    String sql = "INSERT INTO room_tags (room_id, tags) VALUES (?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        for (Room room : rooms) {
            if (room.tags != null) {
                for (String tag : room.tags) {
                    ps.setLong(1, room.id);
                    ps.setString(2, tag);
                    ps.addBatch();
                }
            }
        }
        ps.executeBatch();
    }
}

// Similarly for Course.requiredTags -> course_required_tags table
// StagePattern.dayMask -> stage_pattern_day_mask table
```

**Affected entities:**
- `Room.tags` → `room_tags` table
- `Course.requiredTags` → `course_required_tags` table
- `StagePattern.dayMask` → `stage_pattern_day_mask` table

## 4. Error Handling

### 4.1 Validation Errors

```java
private void validateReferences(SchoolSchedule schedule) {
    List<String> errors = new ArrayList<>();

    // Build ID sets for quick lookup
    Set<Long> timeslotIds = toIdSet(schedule.getTimeslots());
    Set<Long> roomIds = toIdSet(schedule.getRooms());
    Set<Long> courseIds = toIdSet(schedule.getCourses());

    // Validate Lesson references
    for (Lesson lesson : schedule.getLessons()) {
        if (lesson.timeslot != null && !timeslotIds.contains(lesson.timeslot.id)) {
            errors.add(String.format("Lesson %d: timeslot %d not found",
                lesson.id, lesson.timeslot.id));
        }
        // ... more validations
    }

    if (!errors.isEmpty()) {
        throw new ValidationException("Import validation failed", errors);
    }
}
```

### 4.2 Import Errors

| Error Type | Response Code | Message Format |
|------------|---------------|----------------|
| Validation | 400 | `"Validation failed: <details>"` |
| FK Violation | 500 | `"Import failed: FK constraint violation"` |
| DB Error | 500 | `"Import failed: <exception message>"` |

## 5. Performance Considerations

### 5.1 Batch Size Tuning

```java
// In application.properties
quarkus.datasource.jdbc.statement-batch-size=500
```

### 5.2 JDBC Batch vs JPA

| Approach | 7000 entities | Memory | Complexity |
|----------|---------------|--------|------------|
| JPA merge | ~30s (fails) | High | Low |
| JDBC batch | ~2-3s | Low | Medium |
| COPY (PostgreSQL) | ~0.5s | Low | High |

**Recommendation:** JDBC batch is the sweet spot - fast enough and maintainable.

### 5.3 Transaction Management

- Single transaction for entire import (atomicity)
- If any insert fails, entire import rolls back
- No partial state left in database

## 6. Testing Strategy

### 6.1 Unit Tests

```java
@QuarkusTest
class ImportServiceTest {

    @Test
    void testValidateReferences_validData() {
        // Should not throw
    }

    @Test
    void testValidateReferences_missingTimeslot() {
        // Should throw ValidationException
    }

    @Test
    void testTruncateAllTables() {
        // Verify all tables are empty after truncate
    }
}
```

### 6.2 Integration Tests

```java
@QuarkusTest
class ImportExportIT {

    @Test
    void testExportImportRoundtrip() {
        // 1. Export current schedule
        SchoolSchedule exported = given()
            .get("/api/schedule/export")
            .as(SchoolSchedule.class);

        // 2. Import it back
        given()
            .contentType(ContentType.JSON)
            .body(exported)
            .post("/api/schedule/import")
            .then()
            .statusCode(200);

        // 3. Export again and compare
        SchoolSchedule reexported = given()
            .get("/api/schedule/export")
            .as(SchoolSchedule.class);

        assertThat(reexported.getLessons()).hasSize(exported.getLessons().size());
        assertThat(reexported.getLabBlocks()).hasSize(exported.getLabBlocks().size());
    }
}
```

## 7. Migration Path

### Phase 1: Fix Current Implementation
1. Add `em.flush()` and `em.clear()` after truncate
2. Switch to native SQL inserts with explicit IDs
3. Test roundtrip

### Phase 2: Optimize
1. Implement JDBC batch inserts
2. Add sequence reset
3. Add performance metrics

### Phase 3: Polish
1. Add import statistics in response
2. Add progress indicator for large imports
3. Add cancel import endpoint

## 8. Files to Modify

| File | Changes |
|------|---------|
| `ImportService.java` | Complete refactor to use JDBC batch |
| `ScheduleResource.java` | Add import stats to response |
| `ScheduleService.java` | Update importSchedule() call |
| `application.properties` | Add batch size config |
| `ImportExportIT.java` | New integration test |

## 9. Acceptance Criteria

- [ ] Export produces valid JSON with all entities
- [ ] Import succeeds with 7000+ entities
- [ ] Roundtrip preserves all data (export → import → export = same)
- [ ] Import validates FK references before database operations
- [ ] Import is atomic (all or nothing)
- [ ] Import completes in < 10 seconds for 7000 entities
- [ ] Error messages are clear and actionable
