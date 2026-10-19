package com.school.planner.bootstrap;

import java.util.List;

import com.school.planner.domain.Course;
import com.school.planner.domain.LabRotation;
import com.school.planner.service.ExpectedDaysCalculator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Initializes the {@link Course#expectedLabDaysPerWeek} field for all lab courses.
 *
 * <p>This service computes and persists the expected number of distinct days per week
 * for each lab course, based on its total hours, block size, and optionally its
 * {@link LabRotation} period duration.</p>
 *
 * <p>The initialization is <b>idempotent</b> — it only updates courses where the
 * computed value differs from the current value, making it safe to run on every
 * application startup.</p>
 *
 * <p><b>When to run:</b> After courses are created/updated, before solver starts.</p>
 *
 * @see Course#expectedLabDaysPerWeek
 * @see ExpectedDaysCalculator
 * @see LabRotation
 */
@ApplicationScoped
public class ExpectedDaysInitializer {

    /**
     * Initializes expectedLabDaysPerWeek for all lab courses in the database.
     *
     * <p>This method queries all courses where {@code isLab = true} and computes
     * the expected days per week for each. For courses with a LabRotation,
     * it uses the rotation period duration; for courses without, it uses the
     * default 38-week school year.</p>
     *
     * <p>The computation is idempotent — it only persists when the computed
     * value differs from the current value.</p>
     *
     * @return the number of courses updated
     */
    @Transactional
    public int initializeExpectedLabDays() {
        int updated = 0;
        int processed = 0;

        Log.info("Initializing expectedLabDaysPerWeek for all lab courses...");

        // Process all lab courses
        List<Course> labCourses = Course.find("isLab = true").list();
        for (Course course : labCourses) {
            processed++;

            // Find a LabRotation for this course's student group
            // (if multiple exist, any will do for period calculation)
            LabRotation rotation = LabRotation.find(
                    "studentGroup = ?1", course.studentGroup).firstResult();

            // Compute and update if changed
            if (ExpectedDaysCalculator.updateCourseExpectedDays(course, rotation)) {
                updated++;
            }
        }

        Log.infof("ExpectedLabDaysPerWeek initialization complete: %d processed, %d updated",
                processed, updated);

        return updated;
    }
}
