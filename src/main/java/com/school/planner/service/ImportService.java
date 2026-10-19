package com.school.planner.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.ProfessionalizzantiDay;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;
import com.school.planner.domain.Timeslot;

/**
 * Service for importing a complete schedule state.
 *
 * Uses native SQL with JDBC batch inserts for maximum performance.
 * The import is transactional and validates all entity references before committing.
 *
 * Design decisions:
 * - TRUNCATE CASCADE clears all tables in one operation (including ElementCollection join tables)
 * - Plain INSERT (no ON CONFLICT) since tables are empty after truncate
 * - JDBC batch mode for 10-100x performance improvement over individual statements
 * - Sequence reset is MANDATORY after inserting with explicit IDs
 * - Column names match PostgreSQL naming: lowercase, no underscores (e.g., startdatetime)
 */
@ApplicationScoped
public class ImportService {

    private static final Logger LOG = Logger.getLogger(ImportService.class);

    @Inject
    EntityManager em;

    @Inject
    AgroalDataSource dataSource;

    /**
     * Import a complete schedule, replacing all existing data.
     *
     * Process:
     * 1. Validate all entity references (rooms, timeslots, teachers, courses, etc.)
     * 2. Truncate all tables with CASCADE (clears dependent tables automatically)
     * 3. Insert all imported entities using JDBC batch
     * 4. Reset sequences to max ID + 1 (MANDATORY for future inserts)
     *
     * @param schedule The schedule to import
     * @throws IllegalStateException if validation fails
     */
    @Transactional
    public void importSchedule(SchoolSchedule schedule) {
        try {
            LOG.info("Import called with schedule: " + (schedule != null ? "non-null" : "null"));
            if (schedule != null) {
                LOG.infof("Timeslots: %d, Rooms: %d, Teachers: %d, Lessons: %d",
                    schedule.getTimeslots() != null ? schedule.getTimeslots().size() : 0,
                    schedule.getRooms() != null ? schedule.getRooms().size() : 0,
                    schedule.getTeachers() != null ? schedule.getTeachers().size() : 0,
                    schedule.getLessons() != null ? schedule.getLessons().size() : 0);
            }
            LOG.info("Starting schedule import...");

            // Step 1: Validate all references within the imported schedule
            validateReferences(schedule);

            // Step 2: Truncate all tables (CASCADE handles ElementCollection tables)
            truncateAllTables();

            // Step 3: Insert all imported entities using JDBC batch
            insertAllWithJdbc(schedule);

            // Step 4: Reset sequences (MANDATORY after explicit ID inserts)
            resetSequences();

            LOG.info("Schedule import completed successfully");
        } catch (Exception e) {
            LOG.error("Import failed: " + e.getMessage(), e);
            throw new IllegalStateException("Import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate that all entity references in the imported schedule are valid.
     */
    private void validateReferences(SchoolSchedule schedule) {
        LOG.info("Validating imported schedule references...");

        List<Timeslot> timeslots = schedule.getTimeslots() != null ? schedule.getTimeslots() : List.of();
        List<Room> rooms = schedule.getRooms() != null ? schedule.getRooms() : List.of();
        List<Teacher> teachers = schedule.getTeachers() != null ? schedule.getTeachers() : List.of();
        List<StudentGroup> studentGroups = schedule.getStudentGroups() != null ? schedule.getStudentGroups() : List.of();
        List<Course> courses = schedule.getCourses() != null ? schedule.getCourses() : List.of();
        List<Lesson> lessons = schedule.getLessons() != null ? schedule.getLessons() : List.of();
        // Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.

        Set<Long> timeslotIds = toIdSet(timeslots);
        Set<Long> roomIds = toIdSet(rooms);
        Set<Long> teacherIds = toIdSet(teachers);
        Set<Long> studentGroupIds = toIdSet(studentGroups);
        Set<Long> courseIds = toIdSet(courses);

        List<String> errors = new ArrayList<>();

        // Validate Lessons
        for (Lesson lesson : lessons) {
            if (lesson.timeslot != null && !timeslotIds.contains(lesson.timeslot.id)) {
                errors.add(String.format("Lesson %d: timeslot %d not found", lesson.id, lesson.timeslot.id));
            }
            if (lesson.room != null && !roomIds.contains(lesson.room.id)) {
                errors.add(String.format("Lesson %d: room %d not found", lesson.id, lesson.room.id));
            }
            if (lesson.course != null && !courseIds.contains(lesson.course.id)) {
                errors.add(String.format("Lesson %d: course %d not found", lesson.id, lesson.course.id));
            }
        }

        // Note: LabBlock validation was removed in TASK-228 (labs are now pinned Lessons)

        // Validate Courses
        for (Course course : courses) {
            if (course.teacher != null && !teacherIds.contains(course.teacher.id)) {
                errors.add(String.format("Course %d: teacher %d not found", course.id, course.teacher.id));
            }
            if (course.studentGroup != null && !studentGroupIds.contains(course.studentGroup.id)) {
                errors.add(String.format("Course %d: studentGroup %d not found", course.id, course.studentGroup.id));
            }
        }

        // TASK-454 review: validate the FK of every carried reference table, so a
        // payload with an orphaned row fails validation with the row's id instead
        // of dying mid-batch on a generic FK violation (rules join the pre-existing
        // quota/prof-day gap; same one-line shape as the loops above).
        List<GroupCalendarRule> calendarRules = safeList(schedule.getGroupCalendarRules());
        for (GroupCalendarRule rule : calendarRules) {
            if (rule.studentGroup != null && !studentGroupIds.contains(rule.studentGroup.id)) {
                errors.add(String.format("GroupCalendarRule %d: studentGroup %d not found", rule.id, rule.studentGroup.id));
            }
        }
        for (DailyActivityQuota quota : safeList(schedule.getDailyActivityQuotas())) {
            if (quota.studentGroup != null && !studentGroupIds.contains(quota.studentGroup.id)) {
                errors.add(String.format("DailyActivityQuota %d: studentGroup %d not found", quota.id, quota.studentGroup.id));
            }
        }
        for (ProfessionalizzantiDay day : safeList(schedule.getProfessionalizzantiDays())) {
            if (day.studentGroup != null && !studentGroupIds.contains(day.studentGroup.id)) {
                errors.add(String.format("ProfessionalizzantiDay %d: studentGroup %d not found", day.id, day.studentGroup.id));
            }
        }

        if (!errors.isEmpty()) {
            String message = "Import validation failed:\n" + String.join("\n", errors);
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        LOG.info("All references validated successfully");
    }

    /**
     * Truncate all scheduling tables.
     *
     * Strategy: TRUNCATE with CASCADE handles all FK dependencies.
     * Order matters: delete LabRotations first (they reference teacher/studentgroup).
     *
     * LabRotations will be re-seeded by DataGenerator on next startup if needed.
     */
    private void truncateAllTables() {
        LOG.info("Step 1: Truncating labrotation (seed data - will be re-seeded)...");
        em.createNativeQuery("TRUNCATE TABLE labrotation RESTART IDENTITY CASCADE").executeUpdate();
        LOG.info("Step 1 complete");

        LOG.info("Step 2: Truncating all other tables...");
        em.createNativeQuery("""
            TRUNCATE TABLE lesson, course,
                          labassistant, teacheravailability, stagepattern, schoolholiday,
                          teacher, studentgroup, room, timeslot
            RESTART IDENTITY CASCADE
            """).executeUpdate();
        LOG.info("Step 2 complete");

        LOG.info("All tables truncated successfully");
    }

    /**
     * Insert all entities using JDBC batch for performance.
     * Handles ElementCollection tables separately after parent entities.
     *
     * Note: This runs inside a @Transactional method, so we don't manage transactions here.
     * The container (Narayana) handles commit/rollback automatically.
     */
    private void insertAllWithJdbc(SchoolSchedule schedule) {
        LOG.info("Inserting imported entities with JDBC batch...");

        try (Connection conn = dataSource.getConnection()) {
            // Don't change autoCommit - container manages the transaction
            // conn.setAutoCommit(false) would break container-managed transactions

            // Insert in dependency order (parents first)
            insertTimeslots(conn, schedule.getTimeslots());
            insertRooms(conn, schedule.getRooms());
            insertTeachers(conn, schedule.getTeachers());
            insertStudentGroups(conn, schedule.getStudentGroups());
            insertSchoolHolidays(conn, schedule.getSchoolHolidays());
            insertStagePatterns(conn, schedule.getStagePatterns());
            insertTeacherAvailabilities(conn, schedule.getTeacherAvailabilities());
            insertLabAssistants(conn, schedule.getLabAssistants());
            // TASK-666: these two ride in the export payload but were never
            // re-inserted, while truncateAllTables' TRUNCATE ... CASCADE wipes
            // them (both FK studentgroup): an imported profile silently lost
            // every DailyActivityQuota and ProfessionalizzantiDay row, and the
            // next solve NPE'd in construction (sortGroupsCodaPrima) or ran with
            // H32/H34/M29 blind.
            insertDailyActivityQuotas(conn, schedule.getDailyActivityQuotas());
            insertProfessionalizzantiDays(conn, schedule.getProfessionalizzantiDays());
            insertGroupCalendarRules(conn, schedule.getGroupCalendarRules()); // TASK-454: why this matters, see getFullScheduleForExport
            // Note: LabRotations are seed data, not exported/imported
            insertCourses(conn, schedule.getCourses());
            insertLessons(conn, schedule.getLessons());
            // Note: LabBlocks were deprecated in TASK-228. All labs are now pinned Lessons.

            // TASK-666 (review altitude): the payload-carried-vs-reinserted contract
            // was enforced by memory only - a payload list without a hand-written
            // insert is silently wiped by the truncate CASCADE above. Parity must
            // hold exactly for the CARRIED tables: they started at 0 after the
            // truncate, so any mismatch means rows were dropped - fail the import
            // (the container rolls back) instead of leaving them silently lost.
            // NOT covered here: reference tables the payload does not carry are
            // still wiped by the cascade (recurringavailabilityrule).
            verifyPayloadParity(conn, schedule);

            // Don't call conn.commit() - container manages the transaction
            LOG.info("All entities inserted successfully");
        } catch (SQLException e) {
            throw new RuntimeException("JDBC batch insert failed: " + e.getMessage(), e);
        }
    }

    // ==================== INSERT METHODS ====================

    /**
     * TASK-454: re-inserts the export-carried group calendar rules (why the
     * carriage matters: see getFullScheduleForExport).
     */
    private void insertGroupCalendarRules(Connection conn, List<GroupCalendarRule> rules) throws SQLException {
        if (rules == null || rules.isEmpty()) return;

        String sql = "INSERT INTO groupcalendarrule (id, studentgroup_id, dayofweek, minslot, maxslot, effectivefrom, effectiveto) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (GroupCalendarRule r : rules) {
                ps.setLong(1, r.id);
                if (r.studentGroup != null) {
                    ps.setLong(2, r.studentGroup.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (r.dayOfWeek != null) {
                    ps.setInt(3, r.dayOfWeek.ordinal()); // 0=MONDAY, 6=SUNDAY (Hibernate ordinal storage)
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.setInt(4, r.minSlot);
                ps.setInt(5, r.maxSlot);
                if (r.effectiveFrom != null) {
                    ps.setDate(6, java.sql.Date.valueOf(r.effectiveFrom));
                } else {
                    ps.setNull(6, java.sql.Types.DATE);
                }
                if (r.effectiveTo != null) {
                    ps.setDate(7, java.sql.Date.valueOf(r.effectiveTo));
                } else {
                    ps.setNull(7, java.sql.Types.DATE);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d GroupCalendarRule entities", rules.size());
        }
    }

    /**
     * TASK-666 (review altitude): asserts that every payload-carried collection
     * landed in the database. The truncate CASCADE wiped all these tables, so
     * {@code SELECT COUNT(*)} must equal the payload list size exactly; a
     * smaller count is the signature of this task's original bug class (payload
     * field with no hand-written insert). Throws to fail the import loudly.
     */
    private void verifyPayloadParity(Connection conn, SchoolSchedule schedule) throws SQLException {
        record Parity(String table, List<?> payload) {}
        List<Parity> carried = List.of(
                new Parity("timeslot", schedule.getTimeslots()),
                new Parity("room", schedule.getRooms()),
                new Parity("teacher", schedule.getTeachers()),
                new Parity("studentgroup", schedule.getStudentGroups()),
                new Parity("schoolholiday", schedule.getSchoolHolidays()),
                new Parity("stagepattern", schedule.getStagePatterns()),
                new Parity("teacheravailability", schedule.getTeacherAvailabilities()),
                new Parity("labassistant", schedule.getLabAssistants()),
                new Parity("dailyactivityquota", schedule.getDailyActivityQuotas()),
                new Parity("professionalizzantiday", schedule.getProfessionalizzantiDays()),
                new Parity("groupcalendarrule", schedule.getGroupCalendarRules()),
                new Parity("course", schedule.getCourses()),
                new Parity("lesson", schedule.getLessons()));
        // Review finding: an export produced by an older build (before a carried
        // field existed) deserializes with empty lists, and 0 == 0 parity says
        // nothing. The profile stays alive but runs the affected constraints
        // blind - say it loudly instead of degrading silently.
        boolean hasLessons = schedule.getLessons() != null && !schedule.getLessons().isEmpty();
        warnIfVacuous(hasLessons, schedule.getDailyActivityQuotas(),
                "DailyActivityQuota", "H32/H34/M20/M29 targets");
        warnIfVacuous(hasLessons, schedule.getGroupCalendarRules(),
                "GroupCalendarRule", "H20 / VRP calendar windows");
        for (Parity p : carried) {
            int expected = p.payload() == null ? 0 : p.payload().size();
            long actual;
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + p.table())) {
                rs.next();
                actual = rs.getLong(1);
            }
            if (actual != expected) {
                throw new IllegalStateException("Import parity failed for " + p.table()
                        + ": payload carried " + expected + " rows but the table has " + actual
                        + " (a payload field without an insert method gets silently cascade-truncated)");
            }
        }
        LOG.infof("Payload parity verified for %d carried tables", carried.size());
    }

    private void insertTimeslots(Connection conn, List<Timeslot> timeslots) throws SQLException {
        if (timeslots == null || timeslots.isEmpty()) return;

        // UPSERT: Insert new, update existing (preserves FK targets for LabRotations)
        String sql = """
            INSERT INTO timeslot (id, startdatetime, enddatetime)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                startdatetime = EXCLUDED.startdatetime,
                enddatetime = EXCLUDED.enddatetime
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Timeslot t : timeslots) {
                ps.setLong(1, t.id);
                ps.setTimestamp(2, java.sql.Timestamp.valueOf(t.startDateTime));
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(t.endDateTime));
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Upserted %d Timeslot entities", timeslots.size());
        }
    }

    private void insertRooms(Connection conn, List<Room> rooms) throws SQLException {
        if (rooms == null || rooms.isEmpty()) return;

        // UPSERT: Insert new, update existing (preserves FK targets)
        String sql = """
            INSERT INTO room (id, name, capacity)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                capacity = EXCLUDED.capacity
            """;
        // Note: room tags are handled separately via ElementCollection after parent insert
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Room r : rooms) {
                ps.setLong(1, r.id);
                ps.setString(2, r.name);
                ps.setInt(3, r.capacity);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Insert ElementCollection: room_tags (cleared before upsert, so just insert)
        insertRoomTags(conn, rooms);
        LOG.infof("Upserted %d Room entities", rooms.size());
    }

    private void insertRoomTags(Connection conn, List<Room> rooms) throws SQLException {
        String sql = "INSERT INTO room_tags (room_id, tags) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Room r : rooms) {
                if (r.tags != null) {
                    for (String tag : r.tags) {
                        ps.setLong(1, r.id);
                        ps.setString(2, tag);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void insertTeachers(Connection conn, List<Teacher> teachers) throws SQLException {
        if (teachers == null || teachers.isEmpty()) return;

        // UPSERT: Insert new, update existing (preserves FK targets for LabRotations)
        // pseudo is NOT NULL since the Stage pseudo-teacher landed: the upsert
        // must carry it or the insert violates the constraint on re-import
        // (caught live 19/08 restoring the m13-final-run2 solution for UI review).
        String sql = """
            INSERT INTO teacher (id, name, nickname, color, pattern, external, pseudo)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                nickname = EXCLUDED.nickname,
                color = EXCLUDED.color,
                pattern = EXCLUDED.pattern,
                external = EXCLUDED.external,
                pseudo = EXCLUDED.pseudo
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Teacher t : teachers) {
                ps.setLong(1, t.id);
                ps.setString(2, t.name);
                ps.setString(3, t.nickname);
                ps.setString(4, t.color);
                ps.setString(5, t.pattern);
                ps.setBoolean(6, t.external);
                ps.setBoolean(7, t.pseudo);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Upserted %d Teacher entities", teachers.size());
        }
    }

    private void insertStudentGroups(Connection conn, List<StudentGroup> groups) throws SQLException {
        if (groups == null || groups.isEmpty()) return;

        // UPSERT: Insert new, update existing (preserves FK targets for LabRotations)
        String sql = """
            INSERT INTO studentgroup (id, name, size, maxhoursperday, startdate)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                size = EXCLUDED.size,
                maxhoursperday = EXCLUDED.maxhoursperday,
                startdate = EXCLUDED.startdate
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (StudentGroup g : groups) {
                ps.setLong(1, g.id);
                ps.setString(2, g.name);
                ps.setInt(3, g.size);
                if (g.maxHoursPerDay != null) {
                    ps.setInt(4, g.maxHoursPerDay);
                } else {
                    ps.setNull(4, java.sql.Types.INTEGER);
                }
                if (g.startDate != null) {
                    ps.setDate(5, java.sql.Date.valueOf(g.startDate));
                } else {
                    ps.setNull(5, java.sql.Types.DATE);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Upserted %d StudentGroup entities", groups.size());
        }
    }

    private void insertSchoolHolidays(Connection conn, List<SchoolHoliday> holidays) throws SQLException {
        if (holidays == null || holidays.isEmpty()) return;

        String sql = "INSERT INTO schoolholiday (id, date, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SchoolHoliday h : holidays) {
                ps.setLong(1, h.id);
                if (h.date != null) {
                    ps.setDate(2, java.sql.Date.valueOf(h.date));
                } else {
                    ps.setNull(2, java.sql.Types.DATE);
                }
                ps.setString(3, h.description);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d SchoolHoliday entities", holidays.size());
        }
    }

    private void insertStagePatterns(Connection conn, List<StagePattern> patterns) throws SQLException {
        if (patterns == null || patterns.isEmpty()) return;

        // Insert main table
        String sql = "INSERT INTO stagepattern (id, studentgroup_id, startdate, enddate) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (StagePattern p : patterns) {
                ps.setLong(1, p.id);
                if (p.studentGroup != null) {
                    ps.setLong(2, p.studentGroup.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (p.startDate != null) {
                    ps.setDate(3, java.sql.Date.valueOf(p.startDate));
                } else {
                    ps.setNull(3, java.sql.Types.DATE);
                }
                if (p.endDate != null) {
                    ps.setDate(4, java.sql.Date.valueOf(p.endDate));
                } else {
                    ps.setNull(4, java.sql.Types.DATE);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Insert ElementCollection: stagepattern_dayofweekmask
        insertStagePatternDayMask(conn, patterns);
        LOG.infof("Inserted %d StagePattern entities", patterns.size());
    }

    private void insertStagePatternDayMask(Connection conn, List<StagePattern> patterns) throws SQLException {
        String sql = "INSERT INTO stagepattern_dayofweekmask (stagepattern_id, dayofweekmask) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (StagePattern p : patterns) {
                if (p.dayOfWeekMask != null) {
                    for (DayOfWeek dow : p.dayOfWeekMask) {
                        ps.setLong(1, p.id);
                        ps.setInt(2, dow.ordinal()); // 0=MONDAY, 6=SUNDAY (Hibernate ordinal storage)
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void insertTeacherAvailabilities(Connection conn, List<TeacherAvailability> availabilities) throws SQLException {
        if (availabilities == null || availabilities.isEmpty()) return;

        String sql = "INSERT INTO teacheravailability (id, teacher_id, startdatetime, enddatetime, type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (TeacherAvailability ta : availabilities) {
                ps.setLong(1, ta.id);
                if (ta.teacher != null) {
                    ps.setLong(2, ta.teacher.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (ta.startDateTime != null) {
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(ta.startDateTime));
                } else {
                    ps.setNull(3, java.sql.Types.TIMESTAMP);
                }
                if (ta.endDateTime != null) {
                    ps.setTimestamp(4, java.sql.Timestamp.valueOf(ta.endDateTime));
                } else {
                    ps.setNull(4, java.sql.Types.TIMESTAMP);
                }
                ps.setString(5, ta.type != null ? ta.type.name() : null);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d TeacherAvailability entities", availabilities.size());
        }
    }

    private void insertLabAssistants(Connection conn, List<LabAssistant> assistants) throws SQLException {
        if (assistants == null || assistants.isEmpty()) return;

        String sql = "INSERT INTO labassistant (id, teacher_id, roomtag) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (LabAssistant la : assistants) {
                ps.setLong(1, la.id);
                if (la.teacher != null) {
                    ps.setLong(2, la.teacher.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                ps.setString(3, la.roomTag);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d LabAssistant entities", assistants.size());
        }
    }

    /**
     * TASK-666: quota rows are payload-carried but were never re-inserted -
     * see the call site in {@link #insertAllWithJdbc} for the TRUNCATE CASCADE
     * story and the consequences.
     */
    private void insertDailyActivityQuotas(Connection conn, List<DailyActivityQuota> quotas) throws SQLException {
        if (quotas == null || quotas.isEmpty()) return;

        String sql = "INSERT INTO dailyactivityquota (id, studentgroup_id, date, aulahours, labhours, stagehours, omcquota) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (DailyActivityQuota q : quotas) {
                ps.setLong(1, q.id);
                if (q.studentGroup != null) {
                    ps.setLong(2, q.studentGroup.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (q.date != null) {
                    ps.setDate(3, java.sql.Date.valueOf(q.date));
                } else {
                    ps.setNull(3, java.sql.Types.DATE);
                }
                ps.setInt(4, q.aulaHours);
                ps.setInt(5, q.labHours);
                ps.setInt(6, q.stageHours);
                ps.setBoolean(7, q.FCQuota);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d DailyActivityQuota entities", quotas.size());
        }
    }

    /**
     * TASK-666: ProfessionalizzantiDay rows share the same fate as the quotas
     * (payload-carried, cascade-truncated, never re-inserted): without them M29
     * runs blind on an imported profile. dayOfWeek is stored by ordinal, the
     * same Hibernate convention insertStagePatternDayMask uses.
     */
    private void insertProfessionalizzantiDays(Connection conn, List<ProfessionalizzantiDay> days) throws SQLException {
        if (days == null || days.isEmpty()) return;

        String sql = "INSERT INTO professionalizzantiday (id, studentgroup_id, date, dayofweek, description) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ProfessionalizzantiDay d : days) {
                ps.setLong(1, d.id);
                if (d.studentGroup != null) {
                    ps.setLong(2, d.studentGroup.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (d.date != null) {
                    ps.setDate(3, java.sql.Date.valueOf(d.date));
                } else {
                    ps.setNull(3, java.sql.Types.DATE);
                }
                if (d.dayOfWeek != null) {
                    ps.setInt(4, d.dayOfWeek.ordinal()); // 0=MONDAY, 6=SUNDAY (Hibernate ordinal storage)
                } else {
                    ps.setNull(4, java.sql.Types.INTEGER);
                }
                ps.setString(5, d.description);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d ProfessionalizzantiDay entities", days.size());
        }
    }

    private void insertCourses(Connection conn, List<Course> courses) throws SQLException {
        if (courses == null || courses.isEmpty()) return;

        // Insert main table
        String sql = "INSERT INTO course (id, subject, teacher_id, studentgroup_id, totalhours, islab, labblocksize, professionalizzanti, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Course c : courses) {
                ps.setLong(1, c.id);
                ps.setString(2, c.subject);
                if (c.teacher != null) {
                    ps.setLong(3, c.teacher.id);
                } else {
                    ps.setNull(3, java.sql.Types.BIGINT);
                }
                if (c.studentGroup != null) {
                    ps.setLong(4, c.studentGroup.id);
                } else {
                    ps.setNull(4, java.sql.Types.BIGINT);
                }
                ps.setInt(5, c.totalHours);
                ps.setBoolean(6, c.isLab);
                ps.setInt(7, c.labBlockSize);
                ps.setBoolean(8, c.professionalizzanti);
                ps.setString(9, c.color);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Insert ElementCollection: course_requiredroomtags
        insertCourseRequiredTags(conn, courses);
        LOG.infof("Inserted %d Course entities", courses.size());
    }

    private void insertCourseRequiredTags(Connection conn, List<Course> courses) throws SQLException {
        String sql = "INSERT INTO course_requiredroomtags (course_id, requiredroomtags) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Course c : courses) {
                if (c.requiredRoomTags != null) {
                    for (String tag : c.requiredRoomTags) {
                        ps.setLong(1, c.id);
                        ps.setString(2, tag);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void insertLessons(Connection conn, List<Lesson> lessons) throws SQLException {
        if (lessons == null || lessons.isEmpty()) return;

        // TASK-419: promotedpin is NOT NULL and carries a DB-side default of false (see
        // Lesson#promotedPin's columnDefinition), so listing it here is intent, not
        // necessity. It is always written FALSE: an import restores an authoritative state,
        // and a "pin the staged solve made up for itself" is meaningless outside the cycle
        // that made it (the exported payload carries no such marker either).
        String sql = "INSERT INTO lesson (id, course_id, timeslot_id, room_id, pinned, promotedpin, islabhour, labhourindex, labtotalhours, sessionindex) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Lesson l : lessons) {
                ps.setLong(1, l.id);
                if (l.course != null) {
                    ps.setLong(2, l.course.id);
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                if (l.timeslot != null) {
                    ps.setLong(3, l.timeslot.id);
                } else {
                    ps.setNull(3, java.sql.Types.BIGINT);
                }
                if (l.room != null) {
                    ps.setLong(4, l.room.id);
                } else {
                    ps.setNull(4, java.sql.Types.BIGINT);
                }
                ps.setBoolean(5, l.pinned);
                ps.setBoolean(6, false);
                ps.setBoolean(7, l.isLabHour);
                ps.setInt(8, l.labHourIndex);
                ps.setInt(9, l.labTotalHours);
                ps.setInt(10, l.sessionIndex);
                ps.addBatch();
            }
            ps.executeBatch();
            LOG.infof("Inserted %d Lesson entities", lessons.size());
        }
    }

    /**
     * Reset all sequences to max(id) + 1.
     * MANDATORY after inserting with explicit IDs - otherwise next INSERT will fail with duplicate key.
     */
    private void resetSequences() {
        LOG.info("Resetting sequences...");

        // List of (table, sequence) pairs - sequence names match PostgreSQL naming
        // Note: LabBlock table was removed in TASK-228 (labs are now pinned Lessons)
        String[][] tables = {
            {"timeslot", "timeslot_id_seq"},
            {"room", "room_id_seq"},
            {"teacher", "teacher_id_seq"},
            {"studentgroup", "studentgroup_id_seq"},
            {"schoolholiday", "schoolholiday_id_seq"},
            {"stagepattern", "stagepattern_id_seq"},
            {"teacheravailability", "teacheravailability_id_seq"},
            {"labassistant", "labassistant_id_seq"},
            {"dailyactivityquota", "dailyactivityquota_id_seq"},
            {"professionalizzantiday", "professionalizzantiday_id_seq"},
            {"groupcalendarrule", "groupcalendarrule_id_seq"},
            {"course", "course_id_seq"},
            {"lesson", "lesson_id_seq"}
        };

        for (String[] pair : tables) {
            String table = pair[0];
            String sequence = pair[1];
            try {
                em.createNativeQuery(
                    "SELECT setval('" + sequence + "', COALESCE((SELECT MAX(id) FROM " + table + "), 0) + 1, false)"
                ).getSingleResult();
            } catch (Exception e) {
                LOG.warnf("Failed to reset sequence %s: %s", sequence, e.getMessage());
            }
        }

        LOG.info("Sequences reset completed");
    }

    /** Null-tolerant list view (imported payloads may carry null collections for fields added later). */
    private static <T> List<T> safeList(List<T> list) {
        return list != null ? list : List.of();
    }

    /** Vacuous-parity warn: lessons carried but this reference table empty = stale export, constraints blind. */
    private static void warnIfVacuous(boolean hasLessons, List<?> carried, String table, String consequence) {
        if (hasLessons && (carried == null || carried.isEmpty())) {
            LOG.warn("Import payload carries lessons but ZERO " + table + " rows (stale export?):"
                    + " the profile will run without " + consequence);
        }
    }

    /**
     * Helper method to extract IDs from a list of entities.
     */
    private <T> Set<Long> toIdSet(List<T> entities) {
        Set<Long> ids = new HashSet<>();
        if (entities == null) {
            return ids;
        }
        for (T entity : entities) {
            if (entity instanceof Lesson l) {
                ids.add(l.id);
            } else if (entity instanceof Timeslot t) {
                ids.add(t.id);
            } else if (entity instanceof Room r) {
                ids.add(r.id);
            } else if (entity instanceof Teacher t) {
                ids.add(t.id);
            } else if (entity instanceof StudentGroup g) {
                ids.add(g.id);
            } else if (entity instanceof Course c) {
                ids.add(c.id);
            } else if (entity instanceof LabAssistant a) {
                ids.add(a.id);
            } else if (entity instanceof StagePattern p) {
                ids.add(p.id);
            } else if (entity instanceof TeacherAvailability a) {
                ids.add(a.id);
            } else if (entity instanceof SchoolHoliday h) {
                ids.add(h.id);
            }
        }
        return ids;
    }
}
