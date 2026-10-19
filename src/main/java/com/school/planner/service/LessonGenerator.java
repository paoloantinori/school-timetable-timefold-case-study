package com.school.planner.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;

import org.jboss.logging.Logger;

/**
 * Generates Lesson entities from Course definitions.
 *
 * Three generation modes based on course properties:
 * 1. Lab courses (isLab=true, labBlockSize>1): consecutive Lessons with isLabHour=true,
 *    picked up by pre-assignment for pinning in tagged rooms.
 * 2. Block courses (isLab=false, labBlockSize>1): consecutive Lessons with isLabHour=false,
 *    for subjects taught in regular classrooms but in multi-hour blocks (e.g., Disegno edile).
 * 3. Regular courses (labBlockSize<=1): individual Lessons.
 */
@ApplicationScoped
public class LessonGenerator {

    private static final Logger LOG = Logger.getLogger(LessonGenerator.class);

    @Transactional
    public int generateForCourse(Long courseId) {
        Course course = Course.findById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("Course not found: " + courseId);
        }
        if (course.totalHours <= 0) {
            return 0;
        }

        // Delete existing lessons for this course before regenerating
        Lesson.delete("course.id", courseId);

        if (course.labBlockSize > 1) {
            return generateBlockLessons(course);
        } else {
            return generateRegularLessons(course);
        }
    }

    private int generateRegularLessons(Course course) {
        for (int i = 0; i < course.totalHours; i++) {
            Lesson lesson = new Lesson(course);
            // Latent asymmetry fixed (TASK-364 chain smoke, 2026-08-16 sera): the block
            // path (labBlockSize > 1) has always set isLabHour = course.isLab, the
            // regular path never did - and on the 2026/27 seed every lab course has
            // labBlockSize = 0, so lesson.isLabHour was false for ALL 2.836 lab hours
            // and the pre-assign Phase 1/2 session queries (isLabHour = true) saw an
            // empty population no matter the rotations. A lab course's hours are lab
            // hours regardless of block shape. Read ONLY by the pre-assign machinery
            // (no constraint touches it), so the solver score and the flags-OFF
            // production path (phases still rotation-skipped) are unchanged.
            lesson.isLabHour = course.isLab;
            lesson.persist();
        }
        return course.totalHours;
    }

    /**
     * Generate block lessons for courses with labBlockSize > 1.
     *
     * Creates N consecutive Lessons per session, grouped by labHourIndex/labTotalHours.
     * Lab courses (isLab=true) get isLabHour=true for pre-assignment pinning.
     * Non-lab block courses (isLab=false) get isLabHour=false but still benefit from
     * contiguous block structure (H1 teacher conflict exemption within same session).
     *
     * Example: 80-hour Disegno edile with blockSize=2 creates 40 sessions of 2 lessons each.
     * Example: 60-hour Servizio tecnico with blockSize=6 creates 10 sessions of 6 lessons each.
     */
    private int generateBlockLessons(Course course) {
        int blockSize = course.labBlockSize;
        int fullBlocks = course.totalHours / blockSize;
        int remainder = course.totalHours % blockSize;
        int totalLessons = 0;
        boolean isLab = course.isLab;

        // Create N consecutive Lessons for each session
        for (int session = 0; session < fullBlocks; session++) {
            for (int hour = 0; hour < blockSize; hour++) {
                Lesson lesson = new Lesson(course);
                lesson.isLabHour = isLab;
                lesson.labHourIndex = hour;
                lesson.labTotalHours = blockSize;
                lesson.sessionIndex = session;
                lesson.persist();
                totalLessons++;
            }
        }

        // Remainder hours: create as a lab session with isLabHour=true so the
        // pre-assigner can pin them deterministically. Previously these were
        // created with isLabHour=false (invisible to pre-assigner), leaving
        // orphan lessons for the solver — breaking deterministic placement.
        if (remainder > 0) {
            for (int hour = 0; hour < remainder; hour++) {
                Lesson lesson = new Lesson(course);
                lesson.isLabHour = isLab;
                lesson.labHourIndex = hour;
                lesson.labTotalHours = remainder;
                lesson.sessionIndex = fullBlocks;
                lesson.persist();
                totalLessons++;
            }
        }

        int totalSessions = fullBlocks + (remainder > 0 ? 1 : 0);
        LOG.infof("Generated %d %s lessons for course %s (blockSize=%d, sessions=%d, remainder=%d)",
                totalLessons, isLab ? "lab" : "block", course.subject, blockSize, totalSessions, remainder);

        return totalLessons;
    }
}
