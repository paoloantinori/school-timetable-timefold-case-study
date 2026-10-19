package com.school.planner.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;

/**
 * Extracts entities that should be pre-assigned.
 *
 * Pre-assignment entities are:
 * 1. Lab lesson blocks (isLabHour=true lessons for courses with requiredRoomTags)
 * 2. Professionalizzanti lessons for groups with StagePatterns
 *
 * Note: LabRotation records guide which lab sessions should be pre-assigned.
 * M17 (medium constraint) encourages consistent day-of-week patterns.
 *
 * NOTE: LabBlocks were deprecated in TASK-228. Lab rotations are now represented
 * as pinned Lessons with isLabHour=true.
 */
@ApplicationScoped
public class PreAssignmentEntityExtractor {

    private static final Logger LOG = Logger.getLogger(PreAssignmentEntityExtractor.class);

    @Inject
    PreAssignmentContext context;

    /**
     * Result of entity extraction.
     */
    public static class ExtractionResult {
        public List<Lesson> lessonsToPreAssign = new ArrayList<>();
        public List<Lesson> skippedLessons = new ArrayList<>();

        public int totalExtracted() {
            return lessonsToPreAssign.size();
        }

        public int totalSkipped() {
            return skippedLessons.size();
        }
    }

    /**
     * Extract entities for pre-assignment.
     *
     * Unassigned lab lesson blocks (isLabHour=true) are extracted for
     * pre-assignment. M17 guides day-of-week patterns, solver has full
     * flexibility to avoid structural infeasibility.
     * Professionalizzanti lessons for stage days are also included.
     *
     * @return extraction result with entities to pre-assign and skipped entities
     */
    @Transactional
    public ExtractionResult extract() {
        ExtractionResult result = new ExtractionResult();

        // Track already-added entity IDs to avoid duplicates
        Set<Long> addedLessonIds = new HashSet<>();

        // Initialize context for fast lookups
        context.initialize();
        List<SchoolHoliday> holidays = SchoolHoliday.listAll();
        Set<LocalDate> holidayDates = new HashSet<>();
        for (SchoolHoliday h : holidays) {
            holidayDates.add(h.date);
        }

        // Step 1: Extract ALL unassigned lab lesson blocks
        extractLabLessonBlocks(result, addedLessonIds);

        // Step 2: Extract professionalizzanti lessons for stage days
        extractProfessionalizzantiEntities(result, holidayDates, addedLessonIds);

        LOG.infof("Extraction complete: %d Lessons to pre-assign, %d skipped",
                result.lessonsToPreAssign.size(),
                result.totalSkipped());

        return result;
    }

    /**
     * Extract ALL unassigned lab lesson blocks for pre-assignment.
     *
     * Lab lesson blocks are Lessons where isLabHour=true (from courses with
     * requiredRoomTags). M17 (medium constraint) guides day-of-week patterns.
     *
     * NOTE: LabBlocks were deprecated in TASK-228. This now extracts Lessons
     * with isLabHour=true instead.
     */
    private void extractLabLessonBlocks(ExtractionResult result, Set<Long> addedLessonIds) {
        // Get all unassigned lab lesson blocks
        List<Lesson> unassignedLabLessons = Lesson.find(
                "timeslot is null and course.isLabHour = true").list();
        LOG.infof("Found %d unassigned lab lesson blocks for pre-assignment", unassignedLabLessons.size());

        for (Lesson lesson : unassignedLabLessons) {
            if (!addedLessonIds.contains(lesson.id)) {
                result.lessonsToPreAssign.add(lesson);
                addedLessonIds.add(lesson.id);
            }
        }
    }

    /**
     * Extract professionalizzanti lessons for groups with StagePatterns.
     */
    private void extractProfessionalizzantiEntities(ExtractionResult result, Set<LocalDate> holidayDates,
                                                    Set<Long> addedLessonIds) {
        List<StagePattern> stagePatterns = StagePattern.listAll();
        LOG.infof("Processing %d StagePattern records for professionalizzanti", stagePatterns.size());

        for (StagePattern pattern : stagePatterns) {
            StudentGroup group = pattern.studentGroup;
            if (group == null) continue;

            // Find professionalizzanti courses for this group with unassigned lessons
            List<Course> profCourses = Course.find(
                    "studentGroup = ?1 and professionalizzanti = true",
                    group).list();

            for (Course course : profCourses) {
                List<Lesson> unassignedLessons = Lesson.find(
                        "course = ?1 and timeslot is null",
                        course).list();

                for (Lesson lesson : unassignedLessons) {
                    if (!addedLessonIds.contains(lesson.id)) {
                        result.lessonsToPreAssign.add(lesson);
                        addedLessonIds.add(lesson.id);
                    }
                }
            }
        }
    }

    /**
     * Get IDs of lessons that should be pre-assigned.
     * Convenience method for filtering without holding entity references.
     */
    @Transactional
    public Set<Long> getPreAssignmentLessonIds() {
        ExtractionResult result = extract();
        return result.lessonsToPreAssign.stream()
                .map(l -> l.id)
                .collect(java.util.stream.Collectors.toSet());
    }


    /**
     * Identify which entities are NOT part of pre-assignment (will be left to main solver).
     */
    @Transactional
    public ExtractionResult identifyNonPreAssignmentEntities() {
        ExtractionResult result = new ExtractionResult();

        // All unassigned Lessons not in pre-assignment set
        List<Lesson> allUnassignedLessons = Lesson.find("timeslot is null").list();
        ExtractionResult preAssignmentSet = extract();

        Set<Long> preAssignmentLessonIds = new HashSet<>();
        for (Lesson l : preAssignmentSet.lessonsToPreAssign) {
            preAssignmentLessonIds.add(l.id);
        }

        for (Lesson lesson : allUnassignedLessons) {
            if (!preAssignmentLessonIds.contains(lesson.id)) {
                result.skippedLessons.add(lesson);
            }
        }

        // Note: LabBlocks were deprecated in TASK-228
        // All labs are now represented as pinned Lessons with isLabHour=true

        return result;
    }
}
