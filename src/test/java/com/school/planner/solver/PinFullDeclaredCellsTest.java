package com.school.planner.solver;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-590: the pin-full-cells mechanism. The construction fills declared
 * cells to quota; the LS then drains them one lesson at a time with moves
 * that are individually hard-neutral (H34 fires only when the cell is
 * COMPLETELY empty, too late). The pin (promotedPin=true) protects the cell:
 * the LS's entity selector skips pinned entities.
 */
class PinFullDeclaredCellsTest {

    @AfterEach
    void clean() {
        System.clearProperty("planner.construction.pin-full-cells.enabled");
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    @Test
    void flagOff_noPin() {
        System.clearProperty("planner.construction.pin-full-cells.enabled");
        var phase = new DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule schedule = fixture(2, 2);  // full cell (2/2)
        phase.pinFullDeclaredCells(schedule);
        long pinned = schedule.getLessons().stream().filter(l -> l.promotedPin || l.pinned).count();
        assertEquals(0, pinned, "flag off: nothing should be pinned");
    }

    @Test
    void flagOn_fullCellPinned() {
        System.setProperty("planner.construction.pin-full-cells.enabled", "true");
        var phase = new DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule schedule = fixture(2, 2);  // exactly at quota: PIN
        phase.pinFullDeclaredCells(schedule);
        long pinned = schedule.getLessons().stream()
                .filter(l -> l.pinned && l.promotedPin && l.timeslot != null
                        && l.timeslot.getDate().equals(LocalDate.of(2026, 9, 16)))
                .count();
        assertEquals(2, pinned, "the full cell's 2 lessons should carry BOTH flags "
                + "(promotedPin alone restricts nothing: the VRP shortcut and the LS "
                + "entity filter key on `pinned`)");
        assertTrue(schedule.getLessons().stream().noneMatch(l -> l.promotedPin && !l.pinned),
                "no lesson may end in the invalid promotedPin-without-pinned state");
    }

    @Test
    void flagOn_underQuotaCellNotPinned() {
        System.setProperty("planner.construction.pin-full-cells.enabled", "true");
        var phase = new DayClusteredConstructionPhase<SchoolSchedule>();
        SchoolSchedule schedule = fixture(1, 3);  // 1 placed, quota 3: NOT full, NOT pinned
        phase.pinFullDeclaredCells(schedule);
        long pinned = schedule.getLessons().stream().filter(l -> l.promotedPin || l.pinned).count();
        assertEquals(0, pinned, "under-quota cell should NOT be pinned (the LS needs to fill it)");
    }

    /**
     * Fixture: one group, one date, quota N, M lessons placed.
     * quota=2 placed=2 -> full cell (pin eligible).
     * quota=3 placed=1 -> under-quota (not pin eligible).
     */
    private SchoolSchedule fixture(int placedCount, int quota) {
        Teacher t = new Teacher("PinTeacher");
        t.id = 1L;
        StudentGroup g = new StudentGroup("PinGroup", 6);
        g.id = 1L;
        g.startDate = LocalDate.of(2026, 1, 1);
        Course c = new Course("PinSubject", t, g, 100);
        c.id = 1L;
        LocalDate date = LocalDate.of(2026, 9, 16);
        List<Timeslot> timeslots = List.of(
                ts(date, 8), ts(date, 9), ts(date, 10), ts(date, 11),
                ts(date, 12), ts(date, 13), ts(date, 14), ts(date, 15));
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                new com.school.planner.domain.DailyActivityQuota(g, date, quota, 0, 0)));
        List<Lesson> lessons = new java.util.ArrayList<>();
        for (int i = 0; i < placedCount; i++) {
            Lesson l = new Lesson();
            l.id = (long) (i + 1);
            l.course = c;
            l.timeslot = timeslots.get(i);
            lessons.add(l);
        }
        return new SchoolSchedule(timeslots, List.of(), List.of(), List.of(),
                List.of(), List.of(g), List.of(t), List.of(c), lessons, List.of());
    }

    private Timeslot ts(LocalDate d, int h) {
        var t = new Timeslot(
                java.time.LocalDateTime.of(d, java.time.LocalTime.of(h, 0)),
                java.time.LocalDateTime.of(d, java.time.LocalTime.of(h + 1, 0)));
        t.id = (long) (h - 7);
        return t;
    }
}
