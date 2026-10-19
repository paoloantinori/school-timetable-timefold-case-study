package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * TASK-532: golden-oracle test for the H32 day-fill quota constraint (spec
 * 19/08). Static analysis (Fedora): the golden converges to the declared
 * aula+lab quota EXACTLY for 10/11 groups; the only real exception is
 * G09 01/10 (quota 6 vs golden 5, 1h) which the constraint tolerates.
 * The golden import is all-pinned, so H32's counting (which does NOT exempt
 * pins: a pinned day is still filled hours) must still see every golden day
 * at quota. This test replicates the analysis on the golden CSV directly.
 */
class QuotaFillHardConstraintTest {

    @Test
    void flagIsOptOutStandardForm() {
        assertNull(System.getProperty("planner.constraints.quota-fill.enabled"));
        assertTrue(SchoolScheduleConstraintProvider.quotaFillHardEnabled());
        System.setProperty("planner.constraints.quota-fill.enabled", "false");
        assertFalse(SchoolScheduleConstraintProvider.quotaFillHardEnabled());
        System.clearProperty("planner.constraints.quota-fill.enabled");
    }

    @Test
    void scopeExcludesFCPairsAndG14() {
        assertTrue(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G04"));
        assertTrue(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G08"));
        assertTrue(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G14"));
        assertFalse(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G12"));
        assertFalse(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G09"));
        assertFalse(SchoolScheduleConstraintProvider.isQuotaFillExcluded("G03"));
    }

    /**
     * Golden-oracle: over every (group, date with quota and a timeslot-worthy
     * row), non-Stage golden hours >= quota - tolerance, with the ONLY
     * tolerance case being G09 at -1h. Excluded groups not counted.
     */
    @Test
    void goldenMeetsDeclaredQuotaEverywhereExcept3ModaOneHour() {
        // Inline parse of the golden CSV and the quota sheets (test-local, no
        // production loader involved: this is the ORACLE, independent by design)
        java.util.Map<String, Integer> under = new java.util.HashMap<>();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/seed/lessons.csv"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length < 7 || "True".equalsIgnoreCase(f[3])) continue;
                if ("Sabato".equals(f[1])) continue;
                if ("Stage".equals(f[5]) || "Fasullo".equals(f[5])) continue;
                under.merge(f[4] + "|" + f[0], 1, Integer::sum);
            }
        } catch (Exception e) {
            fail("golden unreadable: " + e.getMessage());
        }
        java.util.Map<String, java.util.Map<String, int[]>> quotas = new java.util.HashMap<>();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/seed/calendario_fc.csv"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",", -1);
                quotas.computeIfAbsent(f[0], k -> new java.util.HashMap<>())
                        .put(f[1], new int[] { Integer.parseInt(f[2].trim()), Integer.parseInt(f[3].trim()) });
            }
        } catch (Exception e) {
            fail("fc sheet unreadable: " + e.getMessage());
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/seed/calendario_class_days.csv"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (!"S".equals(f[3].trim())) {
                    quotas.computeIfAbsent(f[0], k -> new java.util.HashMap<>())
                            .putIfAbsent(f[1], new int[] { Integer.parseInt(f[2].trim()), 0 });
                }
            }
        } catch (Exception e) {
            fail("class_days sheet unreadable: " + e.getMessage());
        }
        int violations = 0;
        java.util.List<String> samples = new java.util.ArrayList<>();
        for (var e : quotas.entrySet()) {
            String group = e.getKey();
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(group)) {
                continue;
            }
            int tolerance = "G09".equals(group) ? 1 : 0;
            for (var d : e.getValue().entrySet()) {
                int quota = d.getValue()[0] + d.getValue()[1];
                if (quota <= 0) {
                    continue;
                }
                // SPEC: only dates that HAVE a Timeslot. The 14-24/06/2027 quota
                // rows are past the year end (10/06): phantom, out of scope. In
                // the constraint this is structural (groupBy only sees dates
                // with lessons); the oracle replicates it with the horizon cut.
                if (d.getKey().compareTo("2027-06-10") > 0) {
                    continue;
                }
                // The constraint's groupBy only sees dates WITH lessons: a
                // totally-empty expected day is M20.2's complement (medium),
                // H32 catches the PARTIAL underfill (e.g. 3h on a 5h day). The
                // oracle replicates that scope: only dates the golden itself
                // touches (G09 early-June empty days are the golden's own
                // year-end tail, not a partial fill).
                if (!under.containsKey(group + "|" + d.getKey())) {
                    continue;
                }
                int hours = under.getOrDefault(group + "|" + d.getKey(), 0);
                if (hours < quota - tolerance) {
                    violations++;
                    if (samples.size() < 5) {
                        samples.add(group + " " + d.getKey() + " " + hours + "/" + quota);
                    }
                }
            }
        }
        assertEquals(0, violations, "golden under declared quota: " + samples);
    }
}
