package com.school.planner.solver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * TASK-537: golden-derived budgets for the QUASI-hard families of the final
 * closing campaign. The audit of 20/08 hardcoded the numbers (H32 1h, H33 4
 * days, H11 43 days, H31 ~1 day/group); this class DERIVES them by counting
 * the golden's own practice, so no group name is ever hardcoded and the
 * budgets survive a seed change (the "derived table" the design doc asks for).
 *
 * <p>Protocol (CLAUDE.md "Trappole del golden"): every count reads
 * {@code seed/lessons.csv} EXCLUDING Saturdays, the Stage subject and the
 * Fasullo placeholder teacher. Quota facts come from the calendario CSVs
 * (aula+lab, the M20 axis). All thresholds are constants of the quasi
 * constraints, not of the seed.
 *
 * <p>Population replication, with two documented divergences from the audit:
 * <ul>
 * <li><b>H32</b>: replicates the real constraint population exactly
 * ({@link SchoolScheduleConstraintProvider#isQuotaFillExcluded} scope plus the
 * G09 1h tolerance, days where the golden actually placed hours and a
 * positive quota row exists). On the 2026/27 seed this yields <b>0</b> hours:
 * the only in-scope under-quota golden day (G09 2026-10-01, 5h vs 6) is
 * absorbed by the tolerance. The audit's 1h is the SAME day counted WITHOUT
 * the tolerance; the faithful replication is kept here because
 * {@code FinalClosingCampaign.findDonor} charges H32 with the tolerance. The
 * audit number needs an explicit decision (tolerance in the budget or not)
 * before wiring this into the campaign.</li>
 * <li><b>H33</b>: counts golden days under the floor
 * {@code min(4, mathFloor)} WITHOUT the golden existence cap of
 * {@code service.DayFloorDeriver}. The cap ({@code min(4, math, goldenMin)})
 * makes the count tautologically zero (the floor can never exceed the golden's
 * own minimum), while the audit's 4 days (G09 x2, G08 x2) come from the
 * uncapped floor. The cap is a constraint-derivation artifact (it protects the
 * H33 constraint from firing on the golden), not a budget fact.</li>
 * </ul>
 *
 * <p>{@code h31BudgetDaysPerGroup} (documented for the future move-set, not
 * consumed yet): golden days per group whose occupied slots are NOT one
 * contiguous run (the M31 single-contiguous-run family). {@code
 * h11DaysOverPracticedCap}: golden group-days above 6 hours, the days the
 * practiced cap of 8 makes legal (H11 axis).
 *
 * <p>Pure static counting, no Timefold coupling: the factory takes explicitly
 * parsed inputs ({@link GoldenLesson}/{@link QuotaDay}) so synthetic fixtures
 * drive the logic; {@link #fromClasspathSeed()} is the classpath-seed
 * convenience twin (see {@code GoldenSacrificeBudgetTest}).
 */
public record GoldenSacrificeBudget(int h32BudgetHours, int h33BudgetDays,
        Map<String, Integer> h31BudgetDaysPerGroup, int h11DaysOverPracticedCap) {

    /** Spec constant shared with DayFloorDeriver: the floor never exceeds 4h. */
    static final int FLOOR_CEILING_HOURS = 4;
    /** H11 axis: the practiced cap is 8, so the interesting days are those over 6. */
    static final int H11_LEGAL_CAP_DAYS_ABOVE = 6;

    /** One golden lesson row, raw (no protocol exclusion applied yet). */
    public record GoldenLesson(LocalDate date, int slotIndex, String groupName, String teacher, String subject) {
    }

    /** One quota fact: aula+lab expected hours for (group, date), the M20 axis. */
    public record QuotaDay(String groupName, LocalDate date, int aulaLabHours) {
    }

    /**
     * Derives the budgets. Pure function of the two lists; the protocol
     * exclusions (Saturday, Stage subject, Fasullo teacher) are applied here,
     * not by the caller, so the exclusion logic is itself under test.
     */
    public static GoldenSacrificeBudget derive(List<GoldenLesson> golden, List<QuotaDay> quotas) {
        // group -> date -> placed (non-Stage) hours, plus the occupied slot sets
        Map<String, Map<LocalDate, Integer>> dayHours = new HashMap<>();
        Map<String, Map<LocalDate, TreeSet<Integer>>> daySlots = new HashMap<>();
        Map<String, Integer> pool = new HashMap<>();
        for (GoldenLesson l : golden) {
            if (l.date() == null || !isCounted(l)) {
                continue;
            }
            dayHours.computeIfAbsent(l.groupName(), k -> new HashMap<>()).merge(l.date(), 1, Integer::sum);
            daySlots.computeIfAbsent(l.groupName(), k -> new HashMap<>())
                    .computeIfAbsent(l.date(), k -> new TreeSet<>()).add(l.slotIndex());
            pool.merge(l.groupName(), 1, Integer::sum);
        }
        Map<String, Map<LocalDate, Integer>> quota = new HashMap<>();
        Map<String, Integer> sessionDays = new HashMap<>();
        for (QuotaDay q : quotas) {
            if (q.aulaLabHours() > 0) {
                quota.computeIfAbsent(q.groupName(), k -> new HashMap<>()).put(q.date(), q.aulaLabHours());
                sessionDays.merge(q.groupName(), 1, Integer::sum);
            }
        }

        // H32: golden's own under-quota hours, real constraint population
        int h32 = 0;
        for (var groupDays : dayHours.entrySet()) {
            String group = groupDays.getKey();
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(group)) {
                continue;
            }
            int tolerance = SchoolScheduleConstraintProvider.quotaFillTolerance(group);
            for (var day : groupDays.getValue().entrySet()) {
                Integer q = quota.getOrDefault(group, Map.of()).get(day.getKey());
                if (q == null || q <= 0) {
                    continue;
                }
                int deficit = q - tolerance - day.getValue();
                if (deficit > 0) {
                    h32 += deficit;
                }
            }
        }

        // H33: golden days under the uncapped floor min(4, mathFloor)
        int h33 = 0;
        Map<String, Integer> floors = new HashMap<>();
        for (String group : dayHours.keySet()) {
            int days = sessionDays.getOrDefault(group, 0);
            int math = days > 0 ? pool.get(group) / days : 0;
            floors.put(group, Math.min(FLOOR_CEILING_HOURS, math));
        }
        for (var groupDays : dayHours.entrySet()) {
            int floor = floors.get(groupDays.getKey());
            for (int hours : groupDays.getValue().values()) {
                if (hours < floor) {
                    h33++;
                }
            }
        }

        // H31 (documented, not consumed): days whose slots are not one run
        Map<String, Integer> h31 = new HashMap<>();
        for (var groupDays : daySlots.entrySet()) {
            int count = 0;
            for (TreeSet<Integer> slots : groupDays.getValue().values()) {
                if (slots.size() > 1 && slots.last() - slots.first() + 1 != slots.size()) {
                    count++;
                }
            }
            h31.put(groupDays.getKey(), count);
        }

        // H11: golden days above 6h (legal under the practiced cap of 8)
        int h11 = 0;
        for (var groupDays : dayHours.entrySet()) {
            for (int hours : groupDays.getValue().values()) {
                if (hours > H11_LEGAL_CAP_DAYS_ABOVE) {
                    h11++;
                }
            }
        }
        return new GoldenSacrificeBudget(h32, h33, Map.copyOf(h31), h11);
    }

    private static boolean isCounted(GoldenLesson l) {
        return l.date().getDayOfWeek().getValue() < 6 // Saturday (and Sunday) out
                && !"Stage".equals(l.subject())
                && !"Fasullo".equals(l.teacher());
    }

    /**
     * Convenience twin: parses the classpath seed CSVs (lessons + both
     * calendario sheets, FC rows taking precedence on overlap) and derives.
     * Strict on IO errors: a budget deriver must not degrade silently.
     */
    public static GoldenSacrificeBudget fromClasspathSeed() {
        return derive(parseGoldenLessons(), parseQuotaDays());
    }

    private static List<GoldenLesson> parseGoldenLessons() {
        List<GoldenLesson> out = new ArrayList<>();
        readCsv("/seed/lessons.csv", line -> {
            String[] f = line.split(",", -1);
            if (f.length < 7) {
                return;
            }
            out.add(new GoldenLesson(LocalDate.parse(f[0]), slotIndexOf(f[2]), f[4], f[5], f[6]));
        });
        return out;
    }

    private static List<QuotaDay> parseQuotaDays() {
        Map<String, QuotaDay> byKey = new HashMap<>();
        // FC sheet first: fine-grained aula/lab rows win on overlap; keyed by
        // group+date (a group-only key would collapse the year to one row)
        readCsv("/seed/calendario_fc.csv", line -> {
            String[] f = line.split(",", -1);
            if (f.length < 5) {
                return;
            }
            byKey.put(f[0] + "@" + f[1], new QuotaDay(f[0], LocalDate.parse(f[1]),
                    Integer.parseInt(f[2]) + Integer.parseInt(f[3])));
        });
        readCsv("/seed/calendario_class_days.csv", line -> {
            String[] f = line.split(",", -1);
            if (f.length < 3) {
                return;
            }
            byKey.putIfAbsent(f[0] + "@" + f[1],
                    new QuotaDay(f[0], LocalDate.parse(f[1]), Integer.parseInt(f[2])));
        });
        return new ArrayList<>(byKey.values());
    }

    private static int slotIndexOf(String slotColumn) {
        String start = slotColumn.trim().split("\\s+")[0];
        LocalTime t = LocalTime.parse(start);
        for (int i = 0; i < TimeslotSequence.SLOT_START_TIMES.length; i++) {
            if (TimeslotSequence.SLOT_START_TIMES[i].equals(t)) {
                return i;
            }
        }
        throw new IllegalStateException("Unknown slot start time in golden CSV: " + slotColumn);
    }

    private static void readCsv(String resource, java.util.function.Consumer<String> rowConsumer) {
        try (var reader = new BufferedReader(new InputStreamReader(
                GoldenSacrificeBudget.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rowConsumer.accept(line);
                }
            }
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Golden seed not readable: " + resource, e);
        }
    }
}
