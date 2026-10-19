package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.school.planner.service.LabRotationDeriver.DerivedRotation;
import com.school.planner.service.LabRotationDeriver.GoldenLesson;
import com.school.planner.service.LabRotationDeriver.PairKey;

/**
 * TASK-364 follow-up: the pure derivation rule plus the real-seed census.
 * <ul>
 * <li>Synthetic: a clean pair yields ONE row on the modal weekday; a rotating
 *     pair (second weekday >= 20% of the hours) yields TWO rows; a weekday the
 *     input calendar does not confirm as a lab day is NOT seeded; run shape
 *     (slot/block) is the modal run.</li>
 * <li>Real seed (classpath golden + calendar sheets): the PRODUCTION population
 *     (13 pairs after the subject_overrides de-tags) yields 15 rows, every weekday
 *     an input lab day, the two rotating pairs exactly as measured (T18 G02,
 *     T18 G06) with their per-weekday practiced shapes.</li>
 * <li>Flag: DEFAULT OFF (opt-in seeding).</li>
 * </ul>
 */
class LabRotationDeriverRealSeedCensusTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 14);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 17);

    /**
     * The PRODUCTION population: the (teacher, group) pairs whose courses carry the
     * acconciatura roomTag AND are lab, with the subjects the tag actually survives on
     * (subject_overrides de-tags G14 and G12 Servizio stilistico, so those pairs'
     * aula lessons stay out, exactly like loadLabSessionsByRoomTag feeds Phase 1).
     * The test hardcodes the mechanically verified census (13 pairs -> 15 rows).
     */
    static Map<PairKey, Set<String>> productionPairs() {
        Map<PairKey, Set<String>> m = new HashMap<>();
        m.put(new PairKey("T01", "G02"), Set.of("Servizio tecnico e stilistico"));
        m.put(new PairKey("T01", "G06"), Set.of("Servizio tecnico e stilistico"));
        m.put(new PairKey("T01", "G10"), Set.of("Servizio tecnico e stilistico"));
        m.put(new PairKey("T01", "G12"), Set.of("Servizio tecnico e stilistico"));
        m.put(new PairKey("T18", "G02"), Set.of("Lavaggio"));
        m.put(new PairKey("T18", "G06"), Set.of("Lavaggio"));
        m.put(new PairKey("T18", "G10"), Set.of("Lavaggio"));
        m.put(new PairKey("T19", "G02"), Set.of("Barberia"));
        m.put(new PairKey("T19", "G06"), Set.of("Barberia"));
        m.put(new PairKey("T19", "G12"), Set.of("Barberia"));
        m.put(new PairKey("T26", "G02"), Set.of("Servizio stilistico"));
        m.put(new PairKey("T26", "G06"), Set.of("Servizio stilistico"));
        m.put(new PairKey("T26", "G10"), Set.of("Servizio stilistico"));
        return m;
    }

    @Test
    void cleanPairYieldsOneRowOnTheModalWeekday() {
        PairKey pair = new PairKey("T19", "G02");
        List<GoldenLesson> lessons = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            lessons.add(new GoldenLesson(DayOfWeek.TUESDAY, 0, D1.plusWeeks(i)));
            lessons.add(new GoldenLesson(DayOfWeek.TUESDAY, 1, D1.plusWeeks(i)));
        }
        var rows = LabRotationDeriver.derive(Map.of(pair, lessons),
                Map.of("G02", EnumSet.of(DayOfWeek.TUESDAY)), Set.of(pair));
        assertEquals(1, rows.size());
        DerivedRotation r = rows.get(0);
        assertEquals(DayOfWeek.TUESDAY, r.dayOfWeek());
        assertEquals(0, r.slotStart(), "modal run starts at the first slot");
        assertEquals(2, r.blockSize(), "modal run is the 2h block");
        assertEquals(D1, r.yearStart());
        assertEquals(D1.plusWeeks(5), r.yearEnd());
    }

    @Test
    void rotatingPairYieldsTwoRows() {
        PairKey pair = new PairKey("T18", "G02");
        List<GoldenLesson> lessons = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lessons.add(new GoldenLesson(DayOfWeek.TUESDAY, 2, D1.plusWeeks(i)));
            lessons.add(new GoldenLesson(DayOfWeek.TUESDAY, 3, D1.plusWeeks(i)));
        }
        for (int i = 0; i < 7; i++) {
            lessons.add(new GoldenLesson(DayOfWeek.FRIDAY, 2, D3.plusWeeks(i)));
        }
        var rows = LabRotationDeriver.derive(Map.of(pair, lessons),
                Map.of("G02", EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)), Set.of(pair));
        assertEquals(2, rows.size(), "the rotating pair gets both weekdays (MULTI_DAY shape)");
        assertTrue(rows.stream().anyMatch(r -> r.dayOfWeek() == DayOfWeek.TUESDAY));
        assertTrue(rows.stream().anyMatch(r -> r.dayOfWeek() == DayOfWeek.FRIDAY));
    }

    @Test
    void weekdayNotConfirmedByInputCalendarIsNotSeeded() {
        PairKey pair = new PairKey("X", "G02");
        List<GoldenLesson> lessons = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            lessons.add(new GoldenLesson(DayOfWeek.THURSDAY, 0, D3.plusWeeks(i)));
        }
        var rows = LabRotationDeriver.derive(Map.of(pair, lessons),
                Map.of("G02", EnumSet.of(DayOfWeek.TUESDAY)), Set.of(pair));
        assertTrue(rows.isEmpty(),
                "golden proposes, input disposes: Thursday is not a declared lab day");
    }

    @Test
    void minorWeekdayUnderTheFloorIsDropped() {
        PairKey pair = new PairKey("X", "G02");
        List<GoldenLesson> lessons = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lessons.add(new GoldenLesson(DayOfWeek.TUESDAY, 0, D1.plusWeeks(i)));
        }
        lessons.add(new GoldenLesson(DayOfWeek.FRIDAY, 0, D1.plusDays(1)));
        var rows = LabRotationDeriver.derive(Map.of(pair, lessons),
                Map.of("G02", EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)), Set.of(pair));
        assertEquals(1, rows.size(), "1/11 = 9% is below the 20% floor");
        assertEquals(DayOfWeek.TUESDAY, rows.get(0).dayOfWeek());
    }

    @Test
    void realSeedProducesTheProductionCensus() {
        Map<PairKey, Set<String>> subjects = productionPairs();
        var pairLessons = LabRotationDeriver.loadGoldenPairLessons(subjects);
        assertEquals(13, pairLessons.size(), "all 13 production pairs have golden lessons");

        var labDays = LabRotationDeriver.loadLabDaysByGroup();
        var rows = LabRotationDeriver.derive(pairLessons, labDays, subjects.keySet());
        assertEquals(15, rows.size(),
                "11 single-weekday pairs + 2 rotating pairs x 2 weekdays = 15 rows");

        for (DerivedRotation r : rows) {
            assertTrue(labDays.getOrDefault(r.groupName(), Set.of()).contains(r.dayOfWeek()),
                    r.groupName() + " " + r.dayOfWeek() + " must be a calendar lab day");
        }
        var rotating = rows.stream()
                .map(r -> r.teacherName() + "/" + r.groupName())
                .distinct()
                .filter(name -> rows.stream()
                        .filter(r -> (r.teacherName() + "/" + r.groupName()).equals(name)).count() == 2)
                .sorted()
                .toList();
        assertEquals(List.of("T18/G02", "T18/G06"), rotating);
        // PER-WEEKDAY modal shapes, measured on the golden: full-morning blocks for the
        // mono-day pairs, and the rotating pair's two DIFFERENT practiced shapes
        // (Fri 5h from slot 0 vs Wed single hours from slot 5) - the review finding
        // that a pair-global modal would get wrong.
        assertTrue(rows.stream().anyMatch(r -> r.teacherName().equals("T01")
                && r.groupName().equals("G02") && r.dayOfWeek() == DayOfWeek.THURSDAY
                && r.blockSize() == 6 && r.slotStart() == 0), "T01 G02: 6h from slot 0");
        assertTrue(rows.stream().anyMatch(r -> r.teacherName().equals("T18")
                && r.groupName().equals("G10") && r.dayOfWeek() == DayOfWeek.WEDNESDAY
                && r.blockSize() == 3 && r.slotStart() == 5), "T18 G10: 3h from slot 5");
        assertTrue(rows.stream().anyMatch(r -> r.teacherName().equals("T18")
                && r.groupName().equals("G06") && r.dayOfWeek() == DayOfWeek.FRIDAY
                && r.blockSize() == 5 && r.slotStart() == 0), "T18 G06 Fri: 5h from 0");
        assertTrue(rows.stream().anyMatch(r -> r.teacherName().equals("T18")
                && r.groupName().equals("G06") && r.dayOfWeek() == DayOfWeek.WEDNESDAY
                && r.blockSize() == 1 && r.slotStart() == 5), "T18 G06 Wed: 1h from 5");
        assertTrue(rows.stream().anyMatch(r -> r.teacherName().equals("T26")
                && r.groupName().equals("G10") && r.dayOfWeek() == DayOfWeek.WEDNESDAY
                && r.blockSize() == 4 && r.slotStart() == 1), "T26 G10: 4h from slot 1");
        // the golden raw census ALSO contains G14 and G12 mixed-weekday pairs, but
        // their Servizio stilistico is de-tagged for Phase 1: production never seeds them
        assertFalse(rows.stream().anyMatch(r -> r.groupName().equals("G14")),
                "G14 Servizio stilistico is de-tagged: not Phase 1's population");
    }

    @Test
    void flagDefaultsToOff() {
        System.clearProperty(LabRotationDeriver.FLAG);
        assertFalse(LabRotationDeriver.enabled(),
                "the seeding is opt-in: unset property reads disabled");
    }
}
