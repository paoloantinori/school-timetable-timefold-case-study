package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-549 famiglia (b) TDD: the quota-preserving predicate accepts ONLY same-group
 * cross-date swaps (H32/H33 invariant by construction) and rejects everything else.
 * Tested via the package-private pure predicate; the SelectionFilter wrapper is a
 * two-getter delegation on SwapMove.
 */
class QuotaPreservingSwapFilterTest {

    private static Lesson lesson(String group, LocalDate date, long id) {
        return lesson(group, date, id, id);
    }

    private static Lesson lesson(String group, LocalDate date, long id, long groupId) {
        StudentGroup g = new StudentGroup(group, 20);
        g.id = groupId;
        Teacher t = new Teacher("T" + id);
        Lesson l = new Lesson();
        l.id = id;
        l.course = new Course("S", t, g, 10);
        if (date != null) {
            Timeslot ts = new Timeslot();
            ts.id = id;
            ts.startDateTime = LocalDateTime.of(date, java.time.LocalTime.of(8, 0));
            l.timeslot = ts;
        }
        return l;
    }

    @Test
    void acceptsSameGroupDifferentDates() {
        assertTrue(QuotaPreservingSwapFilter.quotaPreserving(
                lesson("G04", LocalDate.of(2026, 9, 14), 101, 7),
                lesson("G04", LocalDate.of(2026, 9, 15), 102, 7)),
                "same group, different dates: H32 invariant, accept");
    }

    @Test
    void rejectsSameDate() {
        assertFalse(QuotaPreservingSwapFilter.quotaPreserving(
                lesson("G04", LocalDate.of(2026, 9, 14), 201, 7),
                lesson("G04", LocalDate.of(2026, 9, 14), 202, 7)),
                "same-date swap changes nothing H32 cares about");
    }

    @Test
    void rejectsCrossGroup() {
        assertFalse(QuotaPreservingSwapFilter.quotaPreserving(
                lesson("G04", LocalDate.of(2026, 9, 14), 301),
                lesson("G08", LocalDate.of(2026, 9, 15), 302, 999)),
                "cross-group swap breaks BOTH groups' day counts");
    }

    @Test
    void rejectsNullTimeslotOrGroup() {
        assertFalse(QuotaPreservingSwapFilter.quotaPreserving(
                lesson("G04", null, 401, 7),
                lesson("G04", LocalDate.of(2026, 9, 15), 402, 7)),
                "unassigned lessons have no date to preserve");
    }
}
