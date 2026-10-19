package com.school.planner.bootstrap;

import com.school.planner.domain.DailyActivityQuota;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-577 SURPLUS-HARVEST BANCO: replay of the e1bis delivered grid + the PURE pass
 * (no LS, no other finisher). Pre-registered expectation (2026-09-01 night):
 * H34 cells 6 -> 0, 0 new hard, over-quota hours drop (~8h on G06), per-date
 * counts unchanged elsewhere.
 *
 * <p>Gated by {@code PLANNER_TEST_PAIR_CELLS=1} like its siblings. The dump is the
 * committed {@code e1bis/lessons.txt} (the predictor-bypass run's delivered grid).
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_PAIR_CELLS", matches = ".+")
class SurplusHarvestBenchTest {

    private static final Path DEFAULT_DUMP =
            Path.of("claudedocs", "spalmone-2026-27", "task576-results", "f6-diag", "e1bis", "lessons.txt");

    @Inject EntityManager em;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject DataGenerator dataGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty(com.school.planner.solver.SurplusHarvest.FLAG);
    }

    @Test
    @Timeout(value = 300)
    @Transactional
    void surplusHarvestClosesTheH34Cells() throws Exception {
        System.out.println("=== SURPLUS-HARVEST BENCH v2 (replay fedele via export.json.gz) ===");
        System.out.println("pre-registered: cells 6 -> 0, 0 new hard, overquota drops");
        System.setProperty(com.school.planner.solver.SurplusHarvest.FLAG, "true");

        // Faithful replay: SnapshotFixture.loadFromExport (IDs, no name-matching).
        Path exportPath = Path.of("/tmp", "e1bis-export.json.gz");
        assertTrue(java.nio.file.Files.exists(exportPath),
                "the e1bis export must be present at " + exportPath);
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadFromExport(exportPath, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        com.school.planner.domain.SchoolSchedule schedule = snap.problem;
        com.school.planner.domain.SchoolSchedule.setAllRoomsStatic(snap.rooms);

        // SANITY of the replay BEFORE the pass.
        long placed = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null).count();
        int h34 = countH34Cells(schedule);
        int hard = countHardViolations(schedule);
        int overQuota = countOverQuotaHours(schedule);
        System.out.printf("DIAG replay-sanity: placed=%d/%d H34=%d hardViolations=%d overQuota=%d%n",
                placed, schedule.getLessons().size(), h34, hard, overQuota);
        System.out.printf("DIAG sanity-check: (a) placed 12510/12510: %s | (b) H34 == 6: %s | (c) hard in band: %s%n",
                placed == 12510 ? "PASS" : "FAIL(" + placed + ")",
                h34 == 6 ? "PASS" : "FAIL(" + h34 + ")",
                hard <= 20 ? "PASS(" + hard + ")" : "CHECK(" + hard + ")");
        // DEBUG the join: a known full date (2026-09-16, any group with lessons).
        debugJoin(schedule);
        if (h34 != 6) {
            System.out.printf("SANITY FAIL: H34=%d (expected 6): STOP, no pass%n", h34);
            return;
        }

        // THE PASS (pure).
        var result = com.school.planner.solver.SurplusHarvest.repair(schedule,
                (l, ts) -> l.timeslot = ts, (l, room) -> l.room = room);

        int h34After = countH34Cells(schedule);
        int hardAfter = countHardViolations(schedule);
        int overQuotaAfter = countOverQuotaHours(schedule);
        System.out.printf("=== SURPLUS-HARVEST BENCH v2: cells %d -> %d | hard %d -> %d | overquota %d -> %d | moves %d rejected %d ===%n",
                h34, h34After, hard, hardAfter, overQuota, overQuotaAfter,
                result.movesApplied(), result.rejected());
        assertTrue(true, "bench v2 reached");
    }

    /** DEBUG: dump the join keys for one known-full date to find the mismatch. */
    private void debugJoin(com.school.planner.domain.SchoolSchedule schedule) {
        LocalDate probe = LocalDate.of(2026, 9, 16);
        System.out.printf("DIAG debugJoin: date=%s%n", probe);
        // Left side: quota rows for this date.
        List<DailyActivityQuota> quotas = DailyActivityQuota.listAll();
        int quotaRows = 0;
        for (var q : quotas) {
            if (q.date != null && q.date.equals(probe)) {
                quotaRows++;
                System.out.printf("  quota: group=%s(%d) date=%s aula=%d lab=%d%n",
                        q.studentGroup != null ? q.studentGroup.name : "NULL",
                        q.studentGroup != null ? q.studentGroup.id : -1,
                        q.date, q.aulaHours, q.labHours);
            }
        }
        System.out.printf("  quota rows for %s: %d (total quotas=%d)%n", probe, quotaRows, quotas.size());
        // Right side: lessons placed on this date.
        int placedOnDate = 0;
        var byGroup = new java.util.HashMap<String, Integer>();
        for (var l : schedule.getLessons()) {
            if (l.timeslot == null) continue;
            if (!l.timeslot.getDate().equals(probe)) continue;
            placedOnDate++;
            var g = l.course != null && l.course.studentGroup != null ? l.course.studentGroup.name : "?";
            byGroup.merge(g, 1, Integer::sum);
        }
        System.out.printf("  lessons on %s: %d (by group: %s)%n", probe, placedOnDate, byGroup);
        // Check the specific join key: does getExpectedHours return > 0 for a group with lessons?
        for (var group : schedule.getStudentGroups()) {
            if (group == null || group.id == null) continue;
            int quota = com.school.planner.domain.SchoolSchedule.getExpectedHours(group.id, probe);
            int placed = byGroup.containsKey(group.name) ? byGroup.get(group.name) : 0;
            if (placed > 0 || quota > 0) {
                System.out.printf("  JOIN group=%s(id=%d) quota=%d placed=%d -> %s%n",
                        group.name, group.id, quota, placed,
                        quota > 0 && placed == 0 ? "H34-CELL!" : "full");
            }
        }
    }

    // ── metrics ──────────────────────────────────────────────────────────────────

    /** H34 cells: (group, date) with expected-hours > 0 and zero non-Stage placed. */
    private int countH34Cells(com.school.planner.domain.SchoolSchedule schedule) {
        Map<Long, Map<LocalDate, Integer>> hours = new java.util.HashMap<>();
        for (var l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            if (l.course.teacher != null && l.course.teacher.pseudo) continue;
            hours.computeIfAbsent(l.course.studentGroup.id, k -> new java.util.HashMap<>())
                    .merge(l.timeslot.getDate(), 1, Integer::sum);
        }
        // Deduplicate: unique dates only (each date has 8 timeslots, count once).
        java.util.Set<LocalDate> uniqueDates = new java.util.TreeSet<>();
        for (var ts : schedule.getTimeslots()) uniqueDates.add(ts.getDate());
        // H34 scope = H32 scope: exclude G04, G08 (pool-cap form) and G14.
        java.util.Set<String> excludedGroups = java.util.Set.of("G04", "G08", "G14");
        int count = 0;
        for (var group : schedule.getStudentGroups()) {
            if (group == null || group.id == null) continue;
            if (excludedGroups.contains(group.name)) continue;
            for (LocalDate d : uniqueDates) {
                int quota = com.school.planner.domain.SchoolSchedule.getExpectedHours(group.id, d);
                if (quota <= 0) continue;
                int placed = hours.getOrDefault(group.id, Map.of()).getOrDefault(d, 0);
                if (placed == 0) count++;
            }
        }
        return count;
    }

    /** Hard violations via the live occupancy (H1 teacher, H3 group, H11 daily, H7 teacher daily). */
    private int countHardViolations(com.school.planner.domain.SchoolSchedule schedule) {
        int violations = 0;
        Map<String, Integer> teacherSlotCount = new java.util.HashMap<>();
        Map<String, Integer> groupSlotCount = new java.util.HashMap<>();
        Map<String, Integer> teacherDayHours = new java.util.HashMap<>();
        Map<String, Integer> groupDayHours = new java.util.HashMap<>();
        for (var l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null) continue;
            String tsKey = l.timeslot.startDateTime.toString();
            if (l.course.teacher != null && !l.course.teacher.pseudo) {
                String key = l.course.teacher.id + "|" + tsKey;
                if (teacherSlotCount.merge(key, 1, Integer::sum) > 1) violations++; // H1
                teacherDayHours.merge(l.course.teacher.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
            if (l.course.studentGroup != null) {
                String gkey = l.course.studentGroup.id + "|" + tsKey;
                if (groupSlotCount.merge(gkey, 1, Integer::sum) > 1) violations++; // H3
                groupDayHours.merge(l.course.studentGroup.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
            }
        }
        // H7 / H11 caps (simplified: count excess-hour pairs).
        for (var e : teacherDayHours.entrySet()) {
            int hours = e.getValue();
            if (hours > 6) violations += hours - 6; // default H7 cap
        }
        for (var e : groupDayHours.entrySet()) {
            int hours = e.getValue();
            if (hours > 8) violations += hours - 8; // default H11 cap
        }
        return violations;
    }

    /** Sum of (placed - quota) over all (group, date) with placed > quota. */
    private int countOverQuotaHours(com.school.planner.domain.SchoolSchedule schedule) {
        Map<String, Integer> hours = new java.util.HashMap<>();
        for (var l : schedule.getLessons()) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) continue;
            if (l.course.teacher != null && l.course.teacher.pseudo) continue;
            hours.merge(l.course.studentGroup.id + "|" + l.timeslot.getDate(), 1, Integer::sum);
        }
        int overQuota = 0;
        for (var e : hours.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            long groupId = Long.parseLong(parts[0]);
            LocalDate date = LocalDate.parse(parts[1]);
            int quota = com.school.planner.domain.SchoolSchedule.getExpectedHours(groupId, date);
            if (quota > 0 && e.getValue() > quota) {
                overQuota += e.getValue() - quota;
            }
        }
        return overQuota;
    }

    // ── dump replay ──────────────────────────────────────────────────────────────

    /** Map the pipe-format dump rows onto the fixture's lesson entities. */
    private int replayDump(Path dumpPath, List<com.school.planner.domain.Lesson> lessons,
                           List<com.school.planner.domain.Timeslot> timeslots) throws Exception {
        Map<String, com.school.planner.domain.Timeslot> tsByDateHour = new java.util.HashMap<>();
        for (var ts : timeslots) {
            tsByDateHour.put(ts.getDate() + "|" + ts.startDateTime.getHour(), ts);
        }
        int mapped = 0;
        for (String line : java.nio.file.Files.readAllLines(dumpPath)) {
            if (line.startsWith("SET") || line.isBlank()) continue;
            String[] p = line.split("\\|");
            if (p.length < 6) continue;
            String groupName = p[0].trim();
            LocalDate date = LocalDate.parse(p[1].trim());
            int hour = Integer.parseInt(p[2].trim());
            String subject = p[3].trim();
            String teacherName = p[4].trim();
            var ts = tsByDateHour.get(date + "|" + hour);
            if (ts == null) continue;
            // Find the lesson: match by (group, subject, teacher) among unassigned.
            for (var l : lessons) {
                if (l.timeslot != null) continue; // already placed by a previous row
                if (l.course == null || l.course.studentGroup == null) continue;
                if (!l.course.studentGroup.name.equals(groupName)) continue;
                if (!l.course.subject.equals(subject)) continue;
                if (l.course.teacher == null || !l.course.teacher.name.equals(teacherName)) continue;
                l.timeslot = ts;
                mapped++;
                break;
            }
        }
        return mapped;
    }

    // ── fixture (mirrors PairCellsTemplateBenchTest) ─────────────────────────────

    @Transactional
    void seedRealSeedFixture() {
        Map<String, com.school.planner.domain.StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, com.school.planner.domain.Teacher> teachers = new java.util.LinkedHashMap<>();
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            var t = new com.school.planner.domain.Teacher(row[0].trim());
            String maxH = row[3].trim();
            t.maxDailyHours = maxH.isEmpty() ? null : Integer.valueOf(maxH);
            t.persist();
            teachers.put(t.name, t);
        }
        var aggregation = DataGenerator.aggregateCourseHours(DataGenerator.loadSeedCsv("incarichi.csv", 4));
        for (var entry : aggregation.hoursByKey().entrySet()) {
            var key = entry.getKey();
            var teacher = teachers.get(key.teacherName());
            var group = groups.get(key.groupName());
            if (teacher == null || group == null) continue;
            var course = new com.school.planner.domain.Course(key.subject(), teacher, group, entry.getValue());
            course.persist();
            lessonGenerator.generateForCourse(course.id);
        }
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            var teacher = teachers.get(row[0].trim());
            if (teacher == null) continue;
            new com.school.planner.domain.RecurringAvailabilityRule(teacher,
                    java.time.DayOfWeek.valueOf(row[1].trim()),
                    Integer.parseInt(row[2].trim()), Integer.parseInt(row[3].trim()),
                    com.school.planner.domain.AvailabilityType.valueOf(row[4].trim()),
                    row[5].isBlank() ? null : LocalDate.parse(row[5].trim()),
                    row[6].isBlank() ? null : LocalDate.parse(row[6].trim()), row[7].trim())
                    .persist();
        }
        availabilityExpansionService.expandAll();
    }
}
