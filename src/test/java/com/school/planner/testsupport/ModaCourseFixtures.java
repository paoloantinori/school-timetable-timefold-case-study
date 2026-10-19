package com.school.planner.testsupport;

import com.school.planner.domain.Course;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

/**
 * Shared JPA-persisted fixture for the real MODA lab-course set
 * (T17/T16/T07 with the real incarichi.csv hours - TASK-379
 * made those totals load-bearing via the "Orario wins" per-course budget
 * cap, so a drifted copy silently caps real CSV rows). Extracted from two
 * identical copies in {@code CalendarioImportServiceTest} (service package)
 * and {@code CalendarioImportRealSeedStageTest} (bootstrap package) - same
 * cross-package rationale as {@link LessonFixtures}.
 */
public final class ModaCourseFixtures {

    private ModaCourseFixtures() {}

    /**
     * Persists the three MODA lab teachers and their nine real courses
     * (Modello (Moulage) 128/150/80, Confezione 169/166/105 - both lab-tagged
     * per DataGenerator.getRoomTags - and non-lab Tecnologia e creatività 24)
     * for the given 1/2/G09 groups, in that order.
     */
    public static void persistRealModaLabCourses(StudentGroup moda1, StudentGroup moda2, StudentGroup moda3) {
        Teacher t17 = new Teacher("T17");
        t17.persist();
        Teacher t16 = new Teacher("T16");
        t16.persist();
        Teacher t07 = new Teacher("T07");
        t07.persist();
        int[] moulageHours = {128, 150, 80};
        int[] confezioneHours = {169, 166, 105};
        StudentGroup[] modaGroups = {moda1, moda2, moda3};
        for (int i = 0; i < modaGroups.length; i++) {
            Course moulage = new Course("Modello (Moulage)", t17, modaGroups[i], moulageHours[i]);
            moulage.isLab = true;
            moulage.persist();
            Course confezione = new Course("Confezione", t16, modaGroups[i], confezioneHours[i]);
            confezione.isLab = true;
            confezione.persist();
            Course tecnologia = new Course("Tecnologia e creatività", t07, modaGroups[i], 24);
            tecnologia.isLab = false; // matches DataGenerator.getRoomTags: not classified as a lab subject
            tecnologia.persist();
        }
    }
}
