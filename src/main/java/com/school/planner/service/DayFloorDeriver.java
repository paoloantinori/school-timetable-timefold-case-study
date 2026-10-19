package com.school.planner.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.StudentGroup;

/**
 * TASK-536: per-group hard day-floor derivation, two levels (spec maintainer 20/08,
 * corrected same day: the floor is MATH on the inputs, not a golden-min lookup).
 *
 * <p><b>Level 1 (aspiration, DOCUMENTED, not a constraint):</b> the mathematical
 * even-spread floor {@code floor(poolNonStage / sessionDaysInGrid)}, where
 * {@code poolNonStage} is the group's total non-Stage hours from the Course
 * entities (the authoritative incarichi import: Stage pseudo-teacher courses are
 * out, and the CSV's {@code Totale ore corso} totalization rows never become
 * Course entities - that trap double-counts pools when parsing the raw sheet)
 * and {@code sessionDaysInGrid} is the count of the group's DailyActivityQuota
 * rows with {@code aula+lab > 0} (pure-Stage days are out: their hours are the
 * stage bucket). On the 2026/27 seed this gives 5h/day for 12 groups, 4h for
 * G04, 3h for G14. The golden does NOT reach 5 (its min is 4 for 12 groups):
 * 5 is a hint of real difficulty, an ASPIRATION to ask referente about, never a
 * constraint.
 *
 * <p><b>Level 2 (hard floor, what H33 enforces):</b> {@code min(4, mathFloor,
 * goldenExistenceMin)} - the 4h ceiling is the spec constant ("proven
 * existence": the golden shows 4h days are achievable for 12 groups), and the
 * golden's own per-group minimum acts only as an EXISTENCE CAP so the floor can
 * never fire on the golden itself (G08 and G09 drop to 2h, G14 to 3h).
 * The cap is a golden-runtime read (censito TASK-452 style, same family as the
 * slot0-anchors and co-presenza depth reads); when the golden is unreadable the
 * cap degrades to "no cap" with a loud warning, never blocking.
 *
 * <p>Pure static math, no Timefold coupling: unit-testable against the real
 * seed (see {@code DayFloorHardConstraintTest}).
 */
public final class DayFloorDeriver {

    private static final Logger LOG = Logger.getLogger(DayFloorDeriver.class);

    /** Spec constant: the hard floor never exceeds 4h (proven existence). */
    static final int HARD_FLOOR_CEILING = 4;

    private DayFloorDeriver() {
    }

    /**
     * Derives the per-group hard floor map (group name -> floor hours). Pure
     * function of the inputs plus the golden existence cap.
     */
    public static Map<String, Integer> deriveHardFloors(List<StudentGroup> groups,
                                                        List<Course> courses,
                                                        List<DailyActivityQuota> quotas) {
        Map<String, Double> pool = new HashMap<>();
        for (Course c : courses) {
            if (c.studentGroup == null) {
                continue;
            }
            var t = c.teacher;
            if (t != null && t.pseudo) {
                continue; // Stage pseudo-courses: the stageHours bucket, not the pool
            }
            pool.merge(c.studentGroup.name, (double) c.totalHours, Double::sum);
        }
        return deriveHardFloorsFromPool(groups, pool, quotas);
    }

    /**
     * Same derivation with the pool supplied directly as group name -&gt; non-Stage
     * hours. For harnesses whose Course entities carry no real totalHours
     * (snapshot fixtures build them per-row with totalHours=1): there the pool
     * is the group's non-pseudo LESSON count (one lesson = one hour), which is
     * the same quantity.
     */
    public static Map<String, Integer> deriveHardFloorsFromPool(List<StudentGroup> groups,
                                                                Map<String, Double> pool,
                                                                List<DailyActivityQuota> quotas) {
        Map<String, Integer> mathFloor = deriveMathFloorFromPool(groups, pool, quotas);
        Map<String, Integer> existenceCap = goldenExistenceMin();
        Map<String, Integer> out = new HashMap<>();
        for (StudentGroup g : groups) {
            int math = mathFloor.getOrDefault(g.name, 0);
            Integer cap = existenceCap.get(g.name);
            int floor = Math.min(HARD_FLOOR_CEILING, math);
            if (cap != null) {
                floor = Math.min(floor, cap);
            }
            out.put(g.name, floor);
            LOG.infof("[DayFloor] %s: math(aspiration) %dh, golden existence cap %s, HARD floor %dh",
                    g.name, math, cap == null ? "n/a" : cap + "h", floor);
        }
        return out;
    }

    /**
     * Level 1: {@code floor(poolNonStage / sessionDaysInGrid)} per group. Pool
     * from Course entities (Stage pseudo-teacher courses excluded - the CSV's
     * {@code Totale ore corso} rows never reach this list); session days from
     * the DailyActivityQuota facts with aula+lab &gt; 0.
     */
    static Map<String, Integer> deriveMathFloor(List<StudentGroup> groups,
                                                List<Course> courses,
                                                List<DailyActivityQuota> quotas) {
        Map<String, Double> pool = new HashMap<>();
        for (Course c : courses) {
            if (c.studentGroup == null) {
                continue;
            }
            var t = c.teacher;
            if (t != null && t.pseudo) {
                continue; // Stage pseudo-courses: the stageHours bucket, not the pool
            }
            pool.merge(c.studentGroup.name, (double) c.totalHours, Double::sum);
        }
        return deriveMathFloorFromPool(groups, pool, quotas);
    }

    /** Pool-supplied twin of {@link #deriveMathFloor}. */
    static Map<String, Integer> deriveMathFloorFromPool(List<StudentGroup> groups,
                                                        Map<String, Double> pool,
                                                        List<DailyActivityQuota> quotas) {
        Map<String, double[]> acc = new HashMap<>(); // [hours, days]
        for (StudentGroup g : groups) {
            acc.put(g.name, new double[] { pool.getOrDefault(g.name, 0.0), 0 });
        }
        for (DailyActivityQuota q : quotas) {
            if (q.studentGroup == null || q.date == null) {
                continue;
            }
            if (q.aulaHours + q.labHours <= 0) {
                continue; // pure-stage / closed day: not a session day for the pool's spread
            }
            double[] p = acc.get(q.studentGroup.name);
            if (p != null) {
                p[1] += 1;
            }
        }
        Map<String, Integer> out = new HashMap<>();
        for (var e : acc.entrySet()) {
            double days = e.getValue()[1];
            out.put(e.getKey(), days > 0 ? (int) Math.floor(e.getValue()[0] / days) : 0);
        }
        return out;
    }

    /**
     * The golden's own per-group minimum day-hours (protocol exclusions:
     * Saturdays, Stage subject, Fasullo teacher - CLAUDE.md "Trappole del
     * golden"), used ONLY as the existence cap on the hard floor. Empty map on
     * an unreadable golden (the cap degrades off, loudly).
     */
    static Map<String, Integer> goldenExistenceMin() {
        Map<String, Map<LocalDate, Integer>> byDay = new HashMap<>();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                DayFloorDeriver.class.getResourceAsStream("/seed/lessons.csv"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",", -1);
                if (f.length < 7 || "True".equalsIgnoreCase(f[3])) {
                    continue;
                }
                if ("Sabato".equals(f[1])) {
                    continue;
                }
                if ("Stage".equals(f[5]) || "Fasullo".equals(f[5])) {
                    continue;
                }
                byDay.computeIfAbsent(f[4], k -> new HashMap<>())
                        .merge(LocalDate.parse(f[0]), 1, Integer::sum);
            }
        } catch (Exception e) {
            LOG.warnf("[DayFloor] golden not readable (%s) - existence cap OFF, hard floor"
                    + " is min(4, math) only: the golden-oracle property is NOT guaranteed", e.getMessage());
            return Map.of();
        }
        Map<String, Integer> out = new HashMap<>();
        for (var e : byDay.entrySet()) {
            out.put(e.getKey(), e.getValue().values().stream().mapToInt(Integer::intValue).min().orElse(0));
        }
        return out;
    }
}
