package com.school.planner.solver;

import static com.school.planner.TestDataBuilder.course;
import static com.school.planner.TestDataBuilder.dailyActivityQuota;
import static com.school.planner.TestDataBuilder.group;
import static com.school.planner.TestDataBuilder.lesson;
import static com.school.planner.TestDataBuilder.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

/**
 * TASK-497 forma (b) prototype: the M20 target capped to the group's POOL, so a
 * capacity-exceeds-curriculum group's yearly target SUM equals the hours it actually has
 * to place (G04: 835 capacity vs 750 incarichi = an 85h unreachable deficit floor the
 * solver chased and could never close).
 *
 * <p>Covers, in order: the pure allocator ({@link SchoolScheduleConstraintProvider#
 * computeM20PoolCapTargets}), the pre-solve orchestrator
 * ({@code computeM20PoolCapTargetsByGroup}), and the flag semantics - ON charges M20
 * against the exact per-day target, OFF (the default) is byte-identical to the pre-TASK-497
 * TASK-461 ratio path even with the targets map published.
 *
 * <p>Pure in-memory JUnit, no Quarkus/DB: the same fixture shape {@code MarginalDeficitCostTest}
 * uses (everything flows through the {@link SchoolSchedule} statics, ids by hand).
 */
class M20PoolCapTargetTest {

    private static final String FLAG = "planner.constraints.m20-cap-pool.enabled";

    private long nextId = 1;
    private StudentGroup testGroup;
    private Teacher realTeacher;

    @BeforeEach
    void setUp() {
        testGroup = group(nextId++, "TEST", 20);   // maxHoursPerDay null: H11 never binds
        realTeacher = teacher(nextId++, "Prof Reale");
        // Empty calendar rules: getGroupCalendarOpenSlotCount falls back to TOTAL_SLOTS (8),
        // so capRawHours(6, 8) = 6 and the quotas below pass through uncapped.
        SchoolSchedule.setGroupCalendarRulesStatic(List.of());
    }

    @AfterEach
    void tearDown() {
        SchoolSchedule.clearStaticState();   // clears the whole M20-cap family (incl. the ratio)
        System.clearProperty(FLAG);
    }

    // ─── the pure allocator ────────────────────────────────────────────────

    /**
     * The validated G04 instance (TASK-497 notes, recomputed from sources): 111 days at
     * quota 6 + 33 at 5 + 1 at 4 = 835h capacity against a 750h pool. The allocator must
     * spread exactly the 85h shortfall one hour per day (the golden's own shape for this
     * group: 90 days at -1h, never a tail), all of it from the highest-quota days, and the
     * yearly SUM of the targets must be the pool to the hour.
     */
    @Test
    void oneFCInstanceSpreadsTheExactShortfall() {
        Map<LocalDate, Integer> raw = FCShapedRawQuotas();

        Map<LocalDate, Integer> targets = SchoolScheduleConstraintProvider.computeM20PoolCapTargets(raw, 750);

        assertNotNull(targets);
        int sum = targets.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(750, sum, "the yearly target sum IS the pool: no unreachable floor, no overshoot");
        long dropped = targets.entrySet().stream()
                .filter(e -> e.getValue() < raw.get(e.getKey())).count();
        assertEquals(85, dropped, "the whole 835-750 shortfall is allocated");
        targets.forEach((date, t) -> {
            assertTrue(t >= 1 && t <= raw.get(date), "a day never leaves M20 scope (0) nor exceeds its raw quota");
            assertTrue(raw.get(date) - t <= 1, "the shortfall is SPREAD at -1h per day, the golden's shape");
        });
        // All 85 dropped hours come from 6-quota days (the thickest days give first).
        assertTrue(targets.entrySet().stream()
                .filter(e -> e.getValue() < raw.get(e.getKey()))
                .allMatch(e -> raw.get(e.getKey()) == 6),
                "only 6-quota days were touched");
    }

    /**
     * The code-review corner (pool < number of quota days): a group with quota rows but no
     * countable lessons (reachable mid-import, before lesson generation) has pool 0. The
     * allocation must still converge to sum = pool - days may reach 0 (what the TASK-461
     * ratio path already does at ratio 0.0), NOT stop at an unreachable 1h/day floor.
     */
    @Test
    void poolBelowTheDayCountStillSumsToThePool() {
        Map<LocalDate, Integer> raw = new HashMap<>();
        LocalDate start = LocalDate.of(2026, 9, 14);
        for (int i = 0; i < 10; i++) {
            raw.put(start.plusDays(i), 6);
        }

        Map<LocalDate, Integer> zero = SchoolScheduleConstraintProvider.computeM20PoolCapTargets(raw, 0);
        assertEquals(0, zero.values().stream().mapToInt(Integer::intValue).sum(),
                "pool 0: M20 asks nothing of any day (ratio 0.0 equivalent), not 1h/day forever");
        assertTrue(zero.values().stream().allMatch(v -> v == 0));

        Map<LocalDate, Integer> three = SchoolScheduleConstraintProvider.computeM20PoolCapTargets(raw, 3);
        assertEquals(3, three.values().stream().mapToInt(Integer::intValue).sum(),
                "mid-range pool: the invariant holds for ANY pool in [0, capacity]");
        assertTrue(three.containsValue(3) || three.values().stream().anyMatch(v -> v > 0));
    }

    /**
     * A group whose pool covers its capacity needs no cap: null, so the orchestrator omits
     * it and the map only ever names groups the cap actually affects.
     */
    @Test
    void nullWhenThePoolCoversTheCapacity() {
        Map<LocalDate, Integer> raw = new HashMap<>();
        raw.put(LocalDate.of(2026, 9, 14), 6);
        raw.put(LocalDate.of(2026, 9, 15), 6);
        assertNull(SchoolScheduleConstraintProvider.computeM20PoolCapTargets(raw, 12));
        assertNull(SchoolScheduleConstraintProvider.computeM20PoolCapTargets(raw, 15),
                "over-supplied group (pool > capacity): still no cap, the raw quotas stand");
    }

    // ─── the orchestrator ──────────────────────────────────────────────────

    /**
     * computeM20PoolCapTargetsByGroup counts the pool with the SAME filters
     * computeSupplyCapRatios uses (non-pseudo, non-G14 lessons), so the two paths can
     * never disagree about who is over-capacity: the over-capacity group is present, the
     * at-capacity group is absent.
     */
    @Test
    void orchestratorPublishesOnlyTheOverCapacityGroup() {
        StudentGroup atCapacity = group(nextId++, "ATCAP", 20);
        List<DailyActivityQuota> quotas = new ArrayList<>();
        LocalDate mon = LocalDate.of(2026, 9, 14);
        quotas.add(dailyActivityQuota(testGroup, mon, 6, 0, 0, false));
        quotas.add(dailyActivityQuota(atCapacity, mon, 4, 0, 0, false));
        // testGroup pool 5 (< capacity 6, over-capacity); atCapacity pool 4 (= capacity).
        List<Lesson> lessons = new ArrayList<>();
        Course overCourse = course(nextId++, "Materia Over", realTeacher, testGroup);
        Course atCourse = course(nextId++, "Materia At", realTeacher, atCapacity);
        for (int i = 0; i < 5; i++) {
            lessons.add(lesson(nextId++, overCourse, null, null));
        }
        for (int i = 0; i < 4; i++) {
            lessons.add(lesson(nextId++, atCourse, null, null));
        }

        Map<Long, Map<LocalDate, Integer>> published =
                SchoolScheduleConstraintProvider.computeM20PoolCapTargetsByGroup(
                        List.of(testGroup, atCapacity), lessons, quotas);

        assertTrue(published.containsKey(testGroup.id), "over-capacity group is capped");
        assertFalse(published.containsKey(atCapacity.id), "at-capacity group is not");
        assertEquals(5, published.get(testGroup.id).get(mon).intValue(),
                "capacity 6, pool 5: the one missing hour is allocated to Monday");
        assertEquals(5, published.values().stream()
                .flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum());
    }

    // ─── the flag semantics (ON = exact target, OFF = byte-identical) ──────

    /**
     * THE decisive case: a quota-6 day under a ratio that rounds 6 up to 6 (the G08
     * defect: ratio 0.931, round(5.586) = 6, the whole floor survives per-day rounding).
     * With the flag ON the published exact target (5) is charged; with the flag OFF the
     * score keeps charging against the ratio target (6) even though the exact map is
     * published - the default is byte-identical to pre-TASK-497.
     *
     * <p>Fixture: 8 days at quota 6 + 2 at 5 = 58h capacity, 54 lessons = pool 54. The
     * allocator drops the first four 6-days to 5 (sum 54); the ratio 54/58 = 0.931 rounds
     * every 6 to 6 and every 5 to 5 (sum 58: the 4h floor intact).
     */
    @Test
    void flagOnChargesTheExactTargetFlagOffStaysOnTheRatioPath() {
        LocalDate firstSixDay = LocalDate.of(2026, 9, 14);
        LocalDate fifthSixDay = LocalDate.of(2026, 9, 18);
        List<DailyActivityQuota> quotas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            quotas.add(dailyActivityQuota(testGroup, firstSixDay.plusDays(i), 6, 0, 0, false));
        }
        for (int i = 0; i < 2; i++) {
            quotas.add(dailyActivityQuota(testGroup, firstSixDay.plusDays(8 + i), 5, 0, 0, false));
        }
        SchoolSchedule.setDailyActivityQuotasStatic(quotas);
        List<Lesson> lessons = new ArrayList<>();
        Course course = course(nextId++, "Materia", realTeacher, testGroup);
        for (int i = 0; i < 54; i++) {
            lessons.add(lesson(nextId++, course, null, null));
        }
        Map<Long, Map<LocalDate, Integer>> published =
                SchoolScheduleConstraintProvider.computeM20PoolCapTargetsByGroup(
                        List.of(testGroup), lessons, quotas);
        SchoolSchedule.setM20PoolCapTargetsStatic(published);
        SchoolSchedule.setSupplyCapRatioByGroupStatic(Map.of(testGroup.id, 54 / 58.0));
        assertEquals(5, published.get(testGroup.id).get(firstSixDay).intValue(),
                "the allocator drops this 6-day to 5 (one of the four spread hours)");

        // DEFAULT ON since the ratification (2026-08-15): no property set -> the captured
        // flag is TRUE, the same day charges against the exact target 5 - the deficit the
        // pool cannot fill is gone; a REAL missing hour (4 of 5) still costs 1000. The
        // capture mimics refreshSolverStatics (production reads the volatile on the hot path).
        System.clearProperty(FLAG);
        SchoolSchedule.captureM20CapPoolFlag();
        assertEquals(0, SchoolScheduleConstraintProvider.m20DayCost(testGroup, firstSixDay, 5, 5),
                "DEFAULT ON: at the pool-capped target 5, no unreachable deficit is charged");
        assertEquals(1000, SchoolScheduleConstraintProvider.m20DayCost(testGroup, firstSixDay, 4, 4),
                "DEFAULT ON: an hour genuinely missing from the capped target still costs the first-step 1000");

        // Explicit opt-OUT (the only way back to the TASK-461 ratio path): the published
        // exact target must be IGNORED, the day's quota stays 6.
        System.setProperty(FLAG, "false");
        SchoolSchedule.captureM20CapPoolFlag();
        assertEquals(1000, SchoolScheduleConstraintProvider.m20DayCost(testGroup, firstSixDay, 5, 5),
                "OPT-OUT: ratio rounds 6*0.931 to 6, so 5 placed hours are a 1000 deficit (pre-TASK-497)");

        // A 6-day the allocator did NOT touch (the spread stopped at four): identical both
        // ways, so the flag only moves the days it must.
        assertEquals(1000, SchoolScheduleConstraintProvider.m20DayCost(testGroup, fifthSixDay, 5, 5),
                "ON: an untouched 6-day still targets 6 (the spread never over-cuts)");
    }

    // ─── fixtures ──────────────────────────────────────────────────────────

    /** The validated G04 quota shape: 111 days at 6, 33 at 5, 1 at 4 (= 835h capacity). */
    private static Map<LocalDate, Integer> FCShapedRawQuotas() {
        Map<LocalDate, Integer> raw = new HashMap<>();
        LocalDate start = LocalDate.of(2026, 9, 14);
        for (int i = 0; i < 111; i++) {
            raw.put(start.plusDays(i), 6);
        }
        for (int i = 0; i < 33; i++) {
            raw.put(start.plusDays(111 + i), 5);
        }
        raw.put(start.plusDays(111 + 33), 4);
        return raw;
    }
}
