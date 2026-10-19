package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static com.school.planner.TestDataBuilder.timeslot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * Pins the CONTEXT-target semantics of {@code ViolationScopedSteepestFinisher.
 * addContextTargets} (code-review F3 of the context extension): a future edit
 * to the provider's h28 gates or to the mirrored joiner equalities must break
 * THIS test, not silently regress the H28 middle-lesson cure to never-proposed
 * (the blind spot the extension fixes). The fixture is the arbitrated blind
 * puzzle cell, G13 2027-02-12 (see
 * claudedocs/spalmone-2026-27/task638-results/blind-and-moves/REPORT.md).
 */
class ViolationScopedSteepestFinisherContextTest {

    private static final LocalDate DAY = LocalDate.of(2027, 2, 12);

    private final StudentGroup g13 = group(1, "G13", 14);
    private final Teacher T10 = teacher(2, "T10");
    private final Teacher t15 = teacher(3, "T15");

    private final Course lav = course(10, "Lavorazioni meccaniche", T10, g13);
    private final Course lavM = course(11, "Lavorazioni meccaniche", t15, g13);
    private final Course ts = course(12, "Tecniche di saldatura", t15, g13);

    private Lesson at(long id, Course c, int hour) {
        Timeslot slot = timeslot(id * 100 + hour, DAY, hour, 0, hour + 1, 0);
        return lesson(id, c, slot, null);
    }

    /** The arbitrated cell: D-Lav@8, M-Lav@9, M-TS@10, D-Lav@11, M-TS@12. */
    private List<Lesson> cell() {
        return List.of(at(1, lav, 8), at(2, lavM, 9), at(3, ts, 10), at(4, lav, 11),
                at(5, ts, 12));
    }

    private static Map<Lesson, Integer> contextOf(List<Lesson> wound, List<Lesson> dayCell) {
        Map<Lesson, Integer> counts = new LinkedHashMap<>();
        Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay = new HashMap<>();
        byGroupDay.put(1L, Map.of(DAY, dayCell));
        ViolationScopedSteepestFinisher.addContextTargets(wound, byGroupDay, counts);
        return counts;
    }

    @Test
    void middleLessonOfReturnPairBecomesTarget() {
        // the H28 facts are the (a,b) endpoints: T10's two Lav lessons
        var counts = contextOf(List.of(cell().get(0), cell().get(3)), cell());
        assertEquals(1, counts.size(), "only the middle lesson is context");
        assertTrue(counts.containsKey(cell().get(1)),
                "the T15 Lav@9 (the A..B..A middle) must become a target");
    }

    @Test
    void differentSubjectBetweenLessonsAreNotContext() {
        var counts = contextOf(List.of(cell().get(0), cell().get(3)), cell());
        assertFalse(counts.containsKey(cell().get(2)),
                "Tecniche di saldatura@10 is a different subject: not the H28 'c'");
    }

    @Test
    void adjacentSameTeacherPairHasNoMiddle() {
        // a same-teacher adjacent pair (Lav@10, Lav@11): nothing strictly between
        List<Lesson> dayCell = List.of(at(1, lav, 8), at(2, lavM, 9), at(6, lav, 10),
                at(4, lav, 11), at(5, ts, 12));
        var counts = contextOf(List.of(dayCell.get(2), dayCell.get(3)), dayCell);
        assertTrue(counts.isEmpty(), "adjacent pair: h28Between admits no middle");
    }

    @Test
    void sameTeacherDifferentSubjectIsNotAReturnPair() {
        // T15 @9 and @12, same teacher but different subjects: not H28
        var counts = contextOf(List.of(cell().get(1), cell().get(4)), cell());
        assertTrue(counts.isEmpty(), "h28ReturnPair requires the same subject");
    }
}
