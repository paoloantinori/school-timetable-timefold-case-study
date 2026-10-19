package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * TASK-536 checkpoint 1: golden-oracle unit test for the H33 per-group hard
 * day floor. The floor is {@code min(4, mathFloor, goldenExistenceMin)}: the
 * math floor (5h/12 groups, 4h G04, 3h G14 on the 2026/27 seed) is the
 * DOCUMENTED aspiration (referente question), the enforced floor is the two-level
 * spec (4h for 12 groups, 2h for G08/G09, 3h G14). This test replicates
 * the derivation independently from the seed CSVs (oracle, no production loader
 * involved) and asserts the golden can never violate its own floor.
 */
class DayFloorHardConstraintTest {

    @Test
    void flagIsOptOutStandardForm() {
        assertNull(System.getProperty("planner.constraints.day-floor.enabled"));
        assertTrue(SchoolScheduleConstraintProvider.dayFloorHardEnabled());
        System.setProperty("planner.constraints.day-floor.enabled", "false");
        assertFalse(SchoolScheduleConstraintProvider.dayFloorHardEnabled());
        System.clearProperty("planner.constraints.day-floor.enabled");
    }

    /**
     * Oracle table (spec numbers): the derivation must land on 4h for the 12
     * full groups, 2h for G08 and G09 (golden existence cap), 3h G14.
     * Computed inline from the seed sheets, independently of DayFloorDeriver.
     */
    @Test
    void derivedFloorTableMatchesSpec() {
        // pool non-stage per group from incarichi (skip Stage rows AND the
        // 'Totale ore corso' totalization rows: teacher column, subject 'None')
        java.util.Map<String, double[]> pool = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<String>> sessionDays = new java.util.HashMap<>();
        readCsv("/seed/incarichi.csv", f -> {
            String teacher = f[0].trim(), subject = f[1].trim();
            if ("stage".equalsIgnoreCase(teacher) || "stage".equalsIgnoreCase(subject)) {
                return;
            }
            if (teacher.toLowerCase().startsWith("totale") || "none".equalsIgnoreCase(subject)) {
                return;
            }
            pool.computeIfAbsent(f[2].trim(), k -> new double[2])[0] += Double.parseDouble(f[3]);
        });
        // session days in grid: quota-sheet rows with aula+lab > 0 (weekday
        // only, DISTINCT dates: the MODA sheet carries per-shift rows)
        readCsv("/seed/calendario_fc.csv", f -> {
            if (Integer.parseInt(f[2].trim()) + Integer.parseInt(f[3].trim()) > 0 && weekday(f[1])) {
                sessionDays.computeIfAbsent(f[0].trim(), k -> new java.util.HashSet<>()).add(f[1]);
            }
        });
        readCsv("/seed/calendario_class_days.csv", f -> {
            if (!"S".equals(f[3].trim()) && Integer.parseInt(f[2].trim()) > 0 && weekday(f[1])) {
                sessionDays.computeIfAbsent(f[0].trim(), k -> new java.util.HashSet<>()).add(f[1]);
            }
        });
        readCsv("/seed/calendario_moda.csv", f -> {
            if (Integer.parseInt(f[3].trim()) > 0 && weekday(f[0])) {
                sessionDays.computeIfAbsent(f[1].trim(), k -> new java.util.HashSet<>()).add(f[0]);
            }
        });
        sessionDays.forEach((g, days) -> pool.computeIfAbsent(g, k -> new double[2])[1] = days.size());
        // golden existence min per group (protocol exclusions)
        java.util.Map<String, java.util.Map<String, Integer>> goldenDays = new java.util.HashMap<>();
        readCsv("/seed/lessons.csv", f -> {
            if ("True".equalsIgnoreCase(f[3]) || "Sabato".equals(f[1])) {
                return;
            }
            if ("Stage".equals(f[5]) || "Fasullo".equals(f[5])) {
                return;
            }
            goldenDays.computeIfAbsent(f[4], k -> new java.util.HashMap<>())
                    .merge(f[0], 1, Integer::sum);
        });
        java.util.Map<String, Integer> goldenMin = new java.util.HashMap<>();
        goldenDays.forEach((g, days) -> goldenMin.put(g, days.values().stream()
                .mapToInt(Integer::intValue).min().orElse(0)));

        for (var e : pool.entrySet()) {
            String group = e.getKey();
            double[] p = e.getValue();
            int mathFloor = p[1] > 0 ? (int) Math.floor(p[0] / p[1]) : 0;
            int floor = Math.min(4, Math.min(mathFloor, goldenMin.getOrDefault(group, 4)));
            int expected = expectedSpecFloor(group);
            assertEquals(expected, floor,
                    group + ": derived floor " + floor + " (math " + mathFloor
                            + ", golden min " + goldenMin.get(group) + ") vs spec " + expected);
        }
        assertEquals(14, pool.size(), "all 14 groups covered by the derivation");
    }

    /** Golden-oracle: the golden's own per-group minimum never sits below the floor. */
    @Test
    void goldenNeverViolatesItsOwnFloor() {
        java.util.Map<String, java.util.Map<String, Integer>> goldenDays = new java.util.HashMap<>();
        readCsv("/seed/lessons.csv", f -> {
            if ("True".equalsIgnoreCase(f[3]) || "Sabato".equals(f[1])) {
                return;
            }
            if ("Stage".equals(f[5]) || "Fasullo".equals(f[5])) {
                return;
            }
            goldenDays.computeIfAbsent(f[4], k -> new java.util.HashMap<>())
                    .merge(f[0], 1, Integer::sum);
        });
        for (var e : goldenDays.entrySet()) {
            int min = e.getValue().values().stream().mapToInt(Integer::intValue).min().orElse(0);
            assertTrue(min >= expectedSpecFloor(e.getKey()),
                    e.getKey() + ": golden min " + min + " below its own floor");
        }
    }

    /** The spec table (4h/2h/3h), used as the oracle's expected values. */
    private static int expectedSpecFloor(String group) {
        return switch (group) {
            case "G08", "G09" -> 2;
            case "G14" -> 3;
            default -> 4;
        };
    }

    private static boolean weekday(String isoDate) {
        return java.time.LocalDate.parse(isoDate).getDayOfWeek().getValue() <= 5;
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(String[] f);
    }

    private static void readCsv(String resource, RowConsumer consumer) {
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                DayFloorHardConstraintTest.class.getResourceAsStream(resource),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length >= 4) {
                    consumer.accept(f);
                }
            }
        } catch (Exception e) {
            fail(resource + " unreadable: " + e.getMessage());
        }
    }
}
