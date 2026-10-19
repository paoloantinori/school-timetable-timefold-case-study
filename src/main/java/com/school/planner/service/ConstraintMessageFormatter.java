package com.school.planner.service;

// Note: LabBlock import removed - deprecated in TASK-228
// Note: ConstraintMatch import removed - Timefold 2.x migration

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.constraints.SparseDayJustification;
import com.school.planner.domain.constraints.StudentGroupDailyHoursJustification;
import com.school.planner.domain.constraints.TeacherDailyHoursJustification;

import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Generates human-readable messages for constraint violations.
 * <p>
 * Extracts entity details from ConstraintMatch justification lists
 * and formats them into user-friendly explanations.
 * Supports internationalization via resource bundles.
 */
public class ConstraintMessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Resource bundle for constraint messages
    private static final String BUNDLE_NAME = "messages";

    /**
     * Format a human-readable message for a constraint violation.
     *
     * @param constraintId Constraint ID (e.g., "H1.1: Teacher conflict (L-L)")
     * @param justifications List of justification entities from ConstraintMatch
     * @return Human-readable violation message, or null if constraint type not supported
     */
    public static String formatViolation(String constraintId, List<Object> justifications) {
        return formatViolation(constraintId, justifications, Locale.ENGLISH);
    }

    /**
     * Format a human-readable message for a constraint violation with locale support.
     *
     * @param constraintId Constraint ID (e.g., "H1.1: Teacher conflict (L-L)")
     * @param justifications List of justification entities from ConstraintMatch
     * @param locale Desired locale for the message (e.g., Locale.ITALIAN)
     * @return Human-readable violation message, or null if constraint type not supported
     */
    public static String formatViolation(String constraintId, List<Object> justifications, Locale locale) {
        if (justifications == null || justifications.isEmpty()) {
            return null;
        }

        String constraintType = extractConstraintType(constraintId);
        String key = "constraint." + constraintType;

        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            if (!bundle.containsKey(key)) {
                return null;
            }

            String pattern = bundle.getString(key);
            Object[] args = buildArguments(constraintType, justifications, locale);

            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            // Fallback: return null if translation fails
            return null;
        }
    }

    /**
     * Extract constraint type ID from full constraint ID.
     * "H1.1 - Teacher conflict (L-L)" → "H1.1"
     */
    private static String extractConstraintType(String fullId) {
        int sep = fullId.indexOf(" - ");
        return sep > 0 ? fullId.substring(0, sep).trim() : fullId;
    }

    /**
     * Build argument array for message formatting.
     * Each constraint type has its own argument structure.
     */
    private static Object[] buildArguments(String constraintType, List<Object> justifications, Locale locale) {
        // Note: LabBlock split constraints (H1.2, H1.3, H2.2, etc.) deprecated in TASK-228
        // All entities are now Lessons. Redirect to Lesson-only message formatters.
        return switch (constraintType) {
            // H1: Teacher Conflict
            case "H1.1", "H1.2", "H1.3" -> buildTeacherConflictLLArgs(justifications, locale);

            // H2: Room Conflict
            case "H2.1", "H2.2", "H2.3" -> buildRoomConflictLLArgs(justifications, locale);

            // H3: Student Group Conflict
            case "H3.1", "H3.2", "H3.3" -> buildStudentGroupConflictLLArgs(justifications, locale);

            // H4: Stage Blackout (LabBlock case H4.2 deprecated)
            case "H4.1", "H4.2" -> buildStageBlackoutLessonArgs(justifications, locale);

            // H5: Teacher Unavailability
            case "H5.1", "H5.2" -> buildTeacherUnavailabilityArgs(justifications, locale);

            // H6: School Holiday
            case "H6.1", "H6.2" -> buildSchoolHolidayArgs(justifications, locale);

            // H7: Max Teacher Daily Hours
            case "H7" -> buildMaxTeacherDailyHoursArgs(justifications, locale);

            // H11: Max Student Group Daily Hours
            case "H11" -> buildMaxStudentGroupDailyHoursArgs(justifications, locale);

            // H29: Assigned lesson must have a room (TASK-447, was M36 medium). Single lesson,
            // placed but room-null. Shares the generic {subject, group, timeslot} single-lesson
            // formatter with H4 (buildStageBlackoutLessonArgs) - identical extraction.
            case "H29" -> buildStageBlackoutLessonArgs(justifications, locale);

            // M15: Same-day same-subject consecutive (was H15, demoted to medium TASK-392)
            case "M15" -> buildSameDaySubjectConsecutiveArgs(justifications, locale);

            // S16 (teacher continuity, was H16a) / M37 (room continuity, was H16b hard,
            // demoted to medium TASK-446/447 notte).
            // The old case "H16" never matched the letter-suffixed ids and was dead
            // since the split (TASK-377 repointed it).
            case "S16", "M37" -> buildConsecutiveSameSubjectArgs(justifications, locale);

            // M4: Lab room-tag affinity
            case "M4" -> buildLabRoomTagAffinityArgs(justifications, locale);

            // M13: Vertically compact
            case "M13" -> buildVerticallyCompactArgs(justifications, locale);

            // H17: Vertically compact (promoted to hard, TASK-227)
            case "H17" -> buildVerticallyCompactH17Args(justifications, locale);

            // M18: Day should start from first calendar-open slot (was H18/5-hour-gated
            // hard, widened + demoted TASK-247/405 - see CLAUDE.md "H17 afternoon-contiguity
            // + M18 gate recalibration" and "TASK-405")
            case "M18" -> buildDayStartOffsetArgs(justifications, locale);

            // S1: Minimize teacher gaps
            case "S1" -> buildTeacherGapsArgs(justifications, locale);

            // S3: Teacher preference
            case "S3" -> buildTeacherPreferenceArgs(justifications, locale);

            // S5: Minimize student group gaps
            case "S5" -> buildStudentGroupGapsArgs(justifications, locale);

            // S7: Reward consecutive student lessons
            case "S7" -> buildConsecutiveStudentLessonsArgs(justifications, locale);

            // S8: Isolated student lesson
            case "S8" -> buildIsolatedStudentLessonArgs(justifications, locale);

            // S10: Discourage generic lessons in labs
            case "S10" -> buildGenericLessonsInLabsArgs(justifications, locale);

            // S11: Student group room affinity
            case "S11" -> buildStudentGroupRoomAffinityArgs(justifications, locale);

            // M20: Daily quota fill (task-230/385/424). The constraint's justification
            // is a SparseDayJustification (group, date, actualHours, requiredHours=quota),
            // never a Lesson: the old Lesson-casting builder could only throw, get
            // swallowed by the catch above, and leave the message null (TASK-440).
            case "M20" -> buildDailyQuotaFillArgs(justifications, locale);

            // M32: Template deviation (TASK-413)
            case "M32" -> buildTemplateDeviationArgs(justifications, locale);

            default -> new Object[0];
        };
    }

    // ─── H1: Teacher Conflict ─────────────────────────────────────────────

    private static Object[] buildTeacherConflictLLArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);
        Lesson l2 = (Lesson) justifications.get(1);

        Teacher teacher = l1.getTeacher();
        String timeslot = formatTimeslot(l1.timeslot, locale);

        return new Object[]{
            teacher != null ? teacher.name : "?",
            timeslot,
            l1.getSubject(),
            l1.getStudentGroup() != null ? l1.getStudentGroup().name : "?",
            l2.getSubject(),
            l2.getStudentGroup() != null ? l2.getStudentGroup().name : "?"
        };
    }

    // LabBlock-specific methods removed - TASK-228




    // ─── H2: Room Conflict ────────────────────────────────────────────────

    private static Object[] buildRoomConflictLLArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);
        Lesson l2 = (Lesson) justifications.get(1);

        String room = l1.room != null ? l1.room.name : "?";
        String timeslot = formatTimeslot(l1.timeslot, locale);

        return new Object[]{
            room,
            timeslot,
            l1.getSubject(),
            l1.getStudentGroup() != null ? l1.getStudentGroup().name : "?",
            l2.getSubject(),
            l2.getStudentGroup() != null ? l2.getStudentGroup().name : "?"
        };
    }





    // ─── H3: Student Group Conflict ────────────────────────────────────────

    private static Object[] buildStudentGroupConflictLLArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);
        Lesson l2 = (Lesson) justifications.get(1);

        String group = l1.getStudentGroup() != null ? l1.getStudentGroup().name : "?";
        String timeslot = formatTimeslot(l1.timeslot, locale);

        return new Object[]{
            group,
            timeslot,
            l1.getSubject(),
            l1.getTeacher() != null ? l1.getTeacher().name : "?",
            l2.getSubject(),
            l2.getTeacher() != null ? l2.getTeacher().name : "?"
        };
    }





    // ─── H4: Stage Blackout ────────────────────────────────────────────────

    private static Object[] buildStageBlackoutLessonArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);
        String subject = lesson.getSubject();

        return new Object[]{subject, group, timeslot};
    }




    // ─── H5: Teacher Unavailability ────────────────────────────────────────

    private static Object[] buildTeacherUnavailabilityArgs(List<Object> justifications, Locale locale) {
        // Note: LabBlock handling removed - TASK-228
        Lesson lesson = (Lesson) justifications.get(0);
        String teacher = lesson.getTeacher() != null ? lesson.getTeacher().name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);
        String subject = lesson.getSubject();
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";

        return new Object[]{teacher, timeslot, subject, group};
    }

    // ─── H6: School Holiday ────────────────────────────────────────────────

    private static Object[] buildSchoolHolidayArgs(List<Object> justifications, Locale locale) {
        // Note: LabBlock handling removed - TASK-228
        Lesson lesson = (Lesson) justifications.get(0);
        String date = formatDate(lesson.timeslot);
        String subject = lesson.getSubject();
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";

        return new Object[]{subject, group, date};
    }

    // ─── H7: Max Teacher Daily Hours ───────────────────────────────────────

    private static Object[] buildMaxTeacherDailyHoursArgs(List<Object> justifications, Locale locale) {
        // TASK-440: H7 justifies with TeacherDailyHoursJustification, never Lessons (the
        // old Lesson cast could only throw -> swallowed -> null message, same rot as M20).
        if (!(justifications.get(0) instanceof TeacherDailyHoursJustification js)) {
            return new Object[0];
        }
        return dailyArgs(js.teacherId(), js.date(), js.lessonCount(), js.maxHours());
    }

    // ─── H8: Min Student Group Daily Lessons ────────────────────────────────

    // ─── H11: Max Student Group Daily Hours ────────────────────────────────

    private static Object[] buildMaxStudentGroupDailyHoursArgs(List<Object> justifications, Locale locale) {
        // TASK-440: H11 justifies with StudentGroupDailyHoursJustification, never Lessons.
        if (!(justifications.get(0) instanceof StudentGroupDailyHoursJustification js)) {
            return new Object[0];
        }
        return dailyArgs(js.groupId(), js.date(), js.lessonCount(), js.maxHours());
    }

    // ─── M15: Same-day same-subject consecutive (was H15, TASK-392) ────────────────────────────

    private static Object[] buildSameDaySubjectConsecutiveArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);
        Lesson l2 = (Lesson) justifications.get(1);

        String subject = l1.getSubject();
        String group = l1.getStudentGroup() != null ? l1.getStudentGroup().name : "?";
        String date = formatDate(l1.timeslot);

        return new Object[]{
            subject, group, date,
            formatTime(l1.timeslot), formatTime(l2.timeslot)
        };
    }

    // ─── H16: Consecutive same-subject same teacher-room ───────────────────

    private static Object[] buildConsecutiveSameSubjectArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);

        String subject = l1.getSubject();
        String teacher = l1.getTeacher() != null ? l1.getTeacher().name : "?";
        String room = l1.room != null ? l1.room.name : "?";
        String date = formatDate(l1.timeslot);

        return new Object[]{subject, teacher, room, date};
    }

    // ─── M4: Lab room-tag affinity ─────────────────────────────────────────

    private static Object[] buildLabRoomTagAffinityArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String subject = lesson.getSubject();
        String room = lesson.room != null ? lesson.room.name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);

        return new Object[]{subject, room, timeslot};
    }

    // ─── M13: Vertically compact ───────────────────────────────────────────

    private static Object[] buildVerticallyCompactArgs(List<Object> justifications, Locale locale) {
        Lesson l1 = (Lesson) justifications.get(0);

        String subject = l1.getSubject();
        String group = l1.getStudentGroup() != null ? l1.getStudentGroup().name : "?";

        return new Object[]{subject, group};
    }

    // ─── H17: Vertically compact (promoted to hard, TASK-227) ───────────────

    private static Object[] buildVerticallyCompactH17Args(List<Object> justifications, Locale locale) {
        // First, try to get data from the justification if present
        for (Object j : justifications) {
            if (j instanceof com.school.planner.domain.constraints.VerticallyCompactJustification vcj) {
                // Resolve group name from ID - we need to look up the entity
                // For now, use the ID directly since we don't have entity access here
                String groupId = String.valueOf(vcj.groupId());
                String date = vcj.date().toString();
                int gapCount = vcj.gapCount();
                return new Object[]{groupId, date, gapCount};
            }
        }
        // Fallback: extract from lesson
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String date = formatDate(lesson.timeslot);
        return new Object[]{group, date, "?"};
    }

    // ─── M18: Day should start from first calendar-open slot (was H18, widened TASK-405) ──

    private static Object[] buildDayStartOffsetArgs(List<Object> justifications, Locale locale) {
        // First, try to get data from the justification if present
        for (Object j : justifications) {
            if (j instanceof com.school.planner.domain.constraints.DayStartOffsetJustification dsoj) {
                String groupId = String.valueOf(dsoj.groupId());
                String date = dsoj.date().toString();
                return new Object[]{groupId, date, dsoj.offsetSlots()};
            }
        }
        // Fallback: extract from lesson. No calendar context available here, so the
        // offset itself can't be derived - the manual groupBy dispatch
        // (ScheduleService.addM18Violations) computes and reports it too; since the
        // train review the constraint emits DayStartOffsetJustification via
        // justifyWith, so the branch above is the live path and this covers the case where
        // a future justifyWith() wiring surfaces a bare Lesson instead.
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String date = formatDate(lesson.timeslot);
        return new Object[]{group, date, "?"};
    }

    // ─── S1: Minimize teacher gaps ─────────────────────────────────────────

    private static Object[] buildTeacherGapsArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String teacher = lesson.getTeacher() != null ? lesson.getTeacher().name : "?";
        String date = formatDate(lesson.timeslot);

        return new Object[]{teacher, date};
    }

    // ─── S3: Teacher preference ────────────────────────────────────────────

    private static Object[] buildTeacherPreferenceArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String teacher = lesson.getTeacher() != null ? lesson.getTeacher().name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);
        String subject = lesson.getSubject();

        return new Object[]{teacher, subject, timeslot};
    }

    // ─── S5: Minimize student group gaps ───────────────────────────────────

    private static Object[] buildStudentGroupGapsArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String date = formatDate(lesson.timeslot);

        return new Object[]{group, date};
    }

    // ─── S7: Reward consecutive student lessons ───────────────────────────

    private static Object[] buildConsecutiveStudentLessonsArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String date = formatDate(lesson.timeslot);

        return new Object[]{group, date};
    }

    // ─── S8: Isolated student lesson ───────────────────────────────────────

    private static Object[] buildIsolatedStudentLessonArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String subject = lesson.getSubject();
        String date = formatDate(lesson.timeslot);

        return new Object[]{subject, group, date};
    }

    // ─── S10: Discourage generic lessons in labs ──────────────────────────

    private static Object[] buildGenericLessonsInLabsArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String subject = lesson.getSubject();
        String room = lesson.room != null ? lesson.room.name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);

        return new Object[]{subject, room, timeslot};
    }

    // ─── S11: Student group room affinity ─────────────────────────────────

    private static Object[] buildStudentGroupRoomAffinityArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String room = lesson.room != null ? lesson.room.name : "?";
        String subject = lesson.getSubject();

        return new Object[]{group, subject, room};
    }

    // ─── M20: Horizontally compact ────────────────────────────────────────

    private static Object[] buildDailyQuotaFillArgs(List<Object> justifications, Locale locale) {
        if (!(justifications.get(0) instanceof SparseDayJustification js)) {
            return new Object[0];
        }
        return dailyArgs(js.groupId(), js.date(), js.actualHours(), js.requiredHours());
    }

    /** Shared shape for the aggregate (id, date, count, cap) justifications: the id renders
     *  as "#N" (the records deliberately store no names - see Justification's javadoc). */
    private static Object[] dailyArgs(long id, java.time.LocalDate date, long count, long cap) {
        String dateStr = date != null ? date.toString() : "?";
        return new Object[]{"#" + id, dateStr, count, cap};
    }

    // ─── M32: Template deviation (TASK-413) ───────────────────────────────

    private static Object[] buildTemplateDeviationArgs(List<Object> justifications, Locale locale) {
        Lesson lesson = (Lesson) justifications.get(0);
        String subject = lesson.getSubject();
        String group = lesson.getStudentGroup() != null ? lesson.getStudentGroup().name : "?";
        String timeslot = formatTimeslot(lesson.timeslot, locale);

        return new Object[]{subject, group, timeslot};
    }

    // ─── Utility Methods ──────────────────────────────────────────────────

    private static String formatTimeslot(Timeslot timeslot, Locale locale) {
        if (timeslot == null) return "?";
        String day = timeslot.getDayOfWeek() != null
            ? timeslot.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale)
            : "?";
        String time = formatTime(timeslot);
        return String.format("%s %s", day, time);
    }

    private static String formatDate(Timeslot timeslot) {
        if (timeslot == null || timeslot.getDate() == null) return "?";
        return timeslot.getDate().toString();
    }

    private static String formatTime(Timeslot timeslot) {
        if (timeslot == null || timeslot.startDateTime == null) return "?";
        return timeslot.startDateTime.format(TIME_FORMATTER);
    }
}
