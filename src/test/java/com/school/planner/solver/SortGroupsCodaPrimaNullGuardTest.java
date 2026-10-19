package com.school.planner.solver;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK-666: a group with NO DailyActivityQuota rows (the imported profile that
 * lost them, see ImportService's TASK-666 inserts) must not blow up the
 * coda-prima ordering. Before the guard, sortGroupsCodaPrima stored a null
 * value for such a group and the comparator's getOrDefault returned the
 * PRESENT null instead of the LocalDate.MIN default, NPE-ing the whole
 * construction phase.
 */
class SortGroupsCodaPrimaNullGuardTest {

    @AfterEach
    void clearQuotaStatics() {
        SchoolSchedule.setDailyActivityQuotasStatic(List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<StudentGroup, List<Lesson>> invoke(SchoolSchedule schedule, Map<StudentGroup, List<Lesson>> grouped) throws Exception {
        DayClusteredConstructionPhase<SchoolSchedule> phase = new DayClusteredConstructionPhase<>();
        Method m = DayClusteredConstructionPhase.class.getDeclaredMethod("sortGroupsCodaPrima", SchoolSchedule.class, Map.class);
        m.setAccessible(true);
        return (Map<StudentGroup, List<Lesson>>) m.invoke(phase, schedule, grouped);
    }

    private static SchoolSchedule scheduleWithTimeslots(int n) {
        SchoolSchedule schedule = new SchoolSchedule();
        List<Timeslot> timeslots = new ArrayList<>();
        LocalDateTime start = LocalDate.of(2026, 9, 14).atTime(8, 0);
        for (int i = 0; i < n; i++) {
            timeslots.add(new Timeslot(start.plusDays(i), start.plusDays(i).plusHours(1)));
        }
        schedule.setTimeslots(timeslots);
        return schedule;
    }

    private static StudentGroup group(long id, String name) {
        StudentGroup g = new StudentGroup();
        g.id = id;
        g.name = name;
        return g;
    }

    @Test
    void quotaLessGroupsDoNotNpeAndKeepInputOrder() throws Exception {
        // No DailyActivityQuota statics are set: every group is quota-less, the
        // exact shape of the imported profile that NPE'd on the lane.
        SchoolSchedule schedule = scheduleWithTimeslots(5);
        StudentGroup a = group(1L, "A");
        StudentGroup b = group(2L, "B");
        Map<StudentGroup, List<Lesson>> grouped = new LinkedHashMap<>();
        grouped.put(a, new ArrayList<>());
        grouped.put(b, new ArrayList<>());

        Map<StudentGroup, List<Lesson>> sorted = invoke(schedule, grouped);

        assertEquals(List.of(a, b), new ArrayList<>(sorted.keySet()),
                "no quota rows anywhere: the deterministic fallback is the input order");
    }

    @Test
    void keyIsTheLatestQuotaDateNotTheFirst() throws Exception {
        // TASK-669: the ordering key must be the group's LAST positive-quota date
        // (the coda), not the first. All dates INSIDE the 5-day grid: A has quotas
        // on 9/14 AND 9/16, B only on 9/15. Old break-at-first keys A=9/14 < B=9/15
        // so sorts B first; the fixed max keys A=9/16 so A goes first. Mutation-
        // checked: reverting the fix turns this assertion red.
        StudentGroup a = group(9L, "A");
        StudentGroup b = group(10L, "B");
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                new DailyActivityQuota(a, LocalDate.of(2026, 9, 14), 5, 0, 0),
                new DailyActivityQuota(a, LocalDate.of(2026, 9, 16), 5, 0, 0),
                new DailyActivityQuota(b, LocalDate.of(2026, 9, 15), 5, 0, 0)));

        Map<StudentGroup, List<Lesson>> grouped = new LinkedHashMap<>();
        grouped.put(a, new ArrayList<>());
        grouped.put(b, new ArrayList<>());

        Map<StudentGroup, List<Lesson>> sorted = invoke(scheduleWithTimeslots(5), grouped);

        assertEquals(List.of(a, b), new ArrayList<>(sorted.keySet()),
                "A's coda is 9/16, B's is 9/15: later coda goes FIRST");
    }

    @Test
    void keyIsOrderIndependentAgainstUnorderedTimeslots() throws Exception {
        // TASK-669: the old break-at-first scan keyed on whichever quota row the
        // (unordered) DB scan returned first; the max scan must yield the SAME
        // order from a reversed timeslot list. Two interleaved groups so a key
        // difference would actually reorder.
        StudentGroup a = group(11L, "A");
        StudentGroup b = group(12L, "B");
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                new DailyActivityQuota(a, LocalDate.of(2026, 9, 14), 5, 0, 0),
                new DailyActivityQuota(a, LocalDate.of(2026, 9, 17), 5, 0, 0),
                new DailyActivityQuota(b, LocalDate.of(2026, 9, 15), 5, 0, 0),
                new DailyActivityQuota(b, LocalDate.of(2026, 9, 16), 5, 0, 0)));
        Map<StudentGroup, List<Lesson>> grouped = new LinkedHashMap<>();
        grouped.put(a, new ArrayList<>());
        grouped.put(b, new ArrayList<>());

        Map<StudentGroup, List<Lesson>> forward = invoke(scheduleWithTimeslots(5), grouped);
        List<StudentGroup> forwardOrder = new ArrayList<>(forward.keySet());

        SchoolSchedule reversed = scheduleWithTimeslots(5);
        List<Timeslot> reversedSlots = new ArrayList<>(reversed.getTimeslots());
        java.util.Collections.reverse(reversedSlots);
        reversed.setTimeslots(reversedSlots);
        Map<StudentGroup, List<Lesson>> reversedRun = invoke(reversed, grouped);

        assertEquals(forwardOrder, new ArrayList<>(reversedRun.keySet()),
                "same group order from both list orders (A's coda 9/17 beats B's 9/16)");
    }

    @Test
    void quotaLessGroupSortsLastAmongQuotaCarriers() throws Exception {
        SchoolSchedule schedule = scheduleWithTimeslots(5);
        StudentGroup withQuota = group(2L, "B");
        StudentGroup quotaLess = group(1L, "A");
        SchoolSchedule.setDailyActivityQuotasStatic(List.of(
                new DailyActivityQuota(withQuota, LocalDate.of(2026, 9, 16), 5, 0, 0)));

        Map<StudentGroup, List<Lesson>> grouped = new LinkedHashMap<>();
        grouped.put(quotaLess, new ArrayList<>());
        grouped.put(withQuota, new ArrayList<>());

        Map<StudentGroup, List<Lesson>> sorted = invoke(schedule, grouped);

        assertEquals(List.of(withQuota, quotaLess), new ArrayList<>(sorted.keySet()),
                "a quota-less group falls back to LocalDate.MIN and sorts AFTER the quota carrier");
    }
}
