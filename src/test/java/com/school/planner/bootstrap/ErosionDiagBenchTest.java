package com.school.planner.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.ScheduleService;
import com.school.planner.solver.checkpoint.StagedCheckpointCursor;

/**
 * TASK-631 EROSION DIAGNOSIS (20/09 sera): which phase shatters G13 2027-06-10's
 * CLASSPATH:: same trap as the LS-variance bench - the profile points the service
 * at the guarded byte-identical copy of the MAIN solver config (LsVarConfigGuardTest
 * keeps the two from drifting).
 * monographic block (contiguous T15 Cad 8-11 at POST_CONSTRUCTION, verified by
 * the probe)? Resume from the integrato-1-era deterministic construction with the
 * TASK-326 phase-transition dumps ON (a dedicated dir), STOP at B-ROOM (the useful
 * window is B-TIMESLOT; the dumps for its phases are already on disk by then), then
 * print every phase dump's G13 June rows (06-07..06-10) so the diff reads at a
 * glance. Gated by {@code -Derosion.run=true}.
 */
@QuarkusTest
@TestProfile(ErosionDiagBenchTest.ErosionProfile.class)
class ErosionDiagBenchTest {

    /** The live phase structure + live env mode (the smoke solverConfig.xml on the
     * test classpath has neither, and the staged split refuses it loudly). */
    public static class ErosionProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.timefold.solver.solver-config-xml", "lsvar-live-solver-config.xml",
                    "quarkus.timefold.solver.environment-mode", "NON_REPRODUCIBLE");
        }
    }

    private static final String DUMP_DIR = "target/phase-transitions-631";

    @Inject ScheduleService scheduleService;
    @Inject DataGenerator dataGenerator;
    @Inject EntityManager em;
    @Inject UserTransaction userTransaction;
    @Inject com.school.planner.service.LessonGenerator lessonGenerator;
    @Inject com.school.planner.service.AvailabilityExpansionService availabilityExpansionService;

    @BeforeEach
    @AfterEach
    @jakarta.transaction.Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
        System.clearProperty("planner.phase-transition-dump.enabled");
        System.clearProperty("planner.phase-transition-dump.dir");
    }

    @Test
    @Timeout(value = 14400)
    void diagnosesG13BlockErosion() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("erosion.run"), "-Derosion.run=true");
        seedLiveOrderFixture();
        System.setProperty("planner.phase-transition-dump.enabled", "true");
        System.setProperty("planner.phase-transition-dump.dir", DUMP_DIR);
        Path dir = Path.of(DUMP_DIR);
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                for (var f : s.filter(p -> p.getFileName().toString().endsWith(".txt")).toList()) {
                    Files.delete(f);
                }
            }
        }
        userTransaction.begin();
        StagedCheckpointCursor cursor;
        try {
            cursor = scheduleService.applyStagedCheckpoint(
                    Path.of(System.getProperty("erosion.ck",
                            "staged-checkpoints-v132/attempt1/POST_CONSTRUCTION.json")));
        } finally {
            userTransaction.commit();
        }
        scheduleService.startResumeCycle(cursor);
        Instant start = Instant.now();
        while (true) {
            Thread.sleep(5000);
            String stage = scheduleService.getStagedStage();
            if ("B-ROOM".equals(stage)) {
                System.out.printf("[EROSION] B-ROOM reached at +%ds: stopping (dumps on disk)%n",
                        Duration.between(start, Instant.now()).getSeconds());
                scheduleService.stopSolving();
                break;
            }
            if (!scheduleService.isBusy() && stage == null) {
                System.out.println("[EROSION] cycle ended on its own before B-ROOM");
                break;
            }
            if (Duration.between(start, Instant.now()).getSeconds() > 3 * 3600) {
                throw new IllegalStateException("erosion bench exceeded 3h wall");
            }
        }
        // let the stop settle, then print G13 June rows from every phase dump in order
        Thread.sleep(10000);
        try (var s = Files.list(Path.of(DUMP_DIR))) {
            List<Path> files = s.filter(p -> p.getFileName().toString().endsWith(".txt")
                            && !p.getFileName().toString().contains("boxed")
                            && !p.getFileName().toString().contains("ids"))
                    .sorted().toList();
            for (Path f : files) {
                System.out.printf("[EROSION] === %s ===%n", f.getFileName());
                for (String line : Files.readAllLines(f)) {
                    for (String d : new String[] { "2027-06-07", "2027-06-08", "2027-06-09", "2027-06-10" }) {
                        if (line.startsWith(" G13|" + d + "|") || line.startsWith("G13|" + d + "|")) {
                            System.out.printf("[EROSION]   %s%n", line.trim());
                        }
                    }
                }
            }
        }
    }

    @jakarta.transaction.Transactional
    void seedLiveOrderFixture() {
        Map<String, StudentGroup> groups =
                RealSeedFixture.seedTimeslotsGroupsAndCalendarRules(null, RealSeedFixture.MAX_HOURS_PER_DAY);
        Map<String, Teacher> teachers =
                RealSeedFixture.seedTeachersAndAvailabilityRules(availabilityExpansionService, true);
        RealSeedFixture.seedRooms();
        com.school.planner.service.SubjectRegistry registry = new com.school.planner.service.SubjectRegistry();
        registry.load();
        SnapshotFixture.seedCoursesFromIncarichi(teachers, groups, lessonGenerator, registry);
        dataGenerator.generateStageCourses();
        dataGenerator.generateDailyActivityQuotas();
        dataGenerator.generateExpectedDailyHours();
        org.junit.jupiter.api.Assertions.assertTrue(Room.count() > 0, "rooms seeded");
    }
}
