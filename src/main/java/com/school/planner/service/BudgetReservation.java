package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.RotationTestRunner.BlockKey;
import com.school.planner.util.SeedCsv;

/**
 * TASK-516 round 7 (golden-guided reform of the round-6 flat rule, which FAILED
 * its gate: run6 treatment -70 vs control -42). Two measured defects, two fixes:
 *
 * <p><b>1. Numerator provenance.</b> Round 6 counted EVERY quota-bound course not
 * in the CURRENT phase's pin population, so the 110h "Lavorazioni meccaniche"
 * course of G08 (pinned by another phase of the same plan) inflated its pair to
 * a 5h/day reservation. Round 7 counts only the courses NO pre-assign phase pins:
 * the phases' populations are lab-tagged lessons of {@code isLab} courses, so the
 * numerator is the pair's quota-bound NON-lab courses (the RA of G04: isLab
 * false, never pinned).
 *
 * <p><b>2. Golden rhythm, not an invented formula.</b> The golden PRACTICES the
 * coexistence this lever wants: on the days it places the RA of T15 on
 * G04 the teacher's TOTAL load is 4h (12 days) or 6h (1 day) on a cap of 6,
 * i.e. the lab pins leave ~2h of headroom. The reservation is DERIVED from that
 * practice: cap minus the teacher's average total load on the golden days that
 * place the pair's non-enumerated quota-bound subject, floored at 1h. Provenance:
 * the {@code seed/lessons.csv} runtime-read family (censused TASK-452); without
 * the golden the pair falls back to the round-6 spread rule (ceil(demand/labDays),
 * min 1) and the banner says so.
 *
 * <p><b>Gated DEFAULT OFF</b> ({@code planner.pre-assign.budget-reservation.enabled}),
 * same opt-in form as the sibling prototypes. The greedy side pairs this with the
 * CLIP behaviour (never reject a whole block, see GreedyRotationAssigner): no
 * adoption without its own A/B gate (run7).
 */
final class BudgetReservation {

    private static final Logger LOG = Logger.getLogger(BudgetReservation.class);

    /** Opt-in flag: reserve H7 headroom for non-pinned quota-bound lab demand. */
    public static final String FLAG = "planner.pre-assign.budget-reservation.enabled";

    static final String GOLDEN_RESOURCE = "lessons.csv";
    static final int GOLDEN_COLUMNS = 7; // date,day,slot,closed,group,teacher,subject

    private BudgetReservation() {
    }

    public static boolean enabled() {
        // Same opt-in form as the other prototype flags: unset reads disabled.
        return Boolean.parseBoolean(System.getProperty(FLAG, "false"));
    }

    /**
     * Pure rule: hours of daily headroom to reserve. Zero when there is no demand
     * or no lab day to spread it over; ceil so a fractional hour (50h/53 days)
     * still reserves one full legal cell.
     */
    static int reservedHoursPerLabDay(int demandHours, int labDays) {
        if (demandHours <= 0 || labDays <= 0) {
            return 0;
        }
        return (demandHours + labDays - 1) / labDays;
    }

    /**
     * Pure rule (golden rhythm): the reservation is the headroom the golden
     * PRACTICES on the days it places the subject, i.e. cap minus the teacher's
     * average TOTAL load on those days. Floored at 1 (a zero reservation would be
     * no lever), capped at cap-1 (a full-cap reservation would starve the pins:
     * round-6's G08 failure mode).
     */
    static int goldenRhythmReservation(int teacherCap, int totalLoadOnGoldenDays) {
        if (teacherCap <= 1 || totalLoadOnGoldenDays <= 0) {
            return 0;
        }
        int headroom = Math.round((float) (teacherCap - totalLoadOnGoldenDays));
        return Math.max(1, Math.min(teacherCap - 1, headroom));
    }

    /**
     * The DB side: per (teacher, group) pair in this phase's pin population, the
     * daily H7 headroom to reserve. Numerator = the pair's quota-bound NON-lab
     * courses (no phase pins them). Rhythm = golden-derived when the golden has
     * evidence for the (teacher, group, subject), else the round-6 spread rule.
     * Multiple non-enumerated subjects take the MAX (the strongest practiced
     * reduction), never the sum (which over-reserves).
     */
    static Map<BlockKey, Integer> computeForPhase(Map<BlockKey, List<LabSession>> sessionsByKey) {
        if (sessionsByKey.isEmpty()) {
            return Map.of();
        }
        Set<Long> populationPairs = sessionsByKey.keySet().stream()
                .map(BlockKey::teacherId).collect(java.util.stream.Collectors.toSet());
        // The phase's own subjects per pair: a pair present here with the SAME
        // subject is being pinned right now and is not "non-enumerated" either.
        Map<BlockKey, Set<String>> pinnedSubjectsByPair = new HashMap<>();
        for (var e : sessionsByKey.entrySet()) {
            Set<String> subjects = new HashSet<>();
            for (LabSession sess : e.getValue()) {
                if (sess.course != null && sess.course.subject != null) {
                    subjects.add(sess.course.subject);
                }
            }
            pinnedSubjectsByPair.put(e.getKey(), subjects);
        }
        // Golden evidence: teacher total load per day, and the days each
        // (teacher, group, subject) is placed (traps excluded, deriver protocol).
        GoldenLoad golden = loadGoldenLoad();
        Map<Long, String> groupNamesById = new HashMap<>();
        Map<Long, Teacher> teacherByPairId = new HashMap<>();
        Map<String, Integer> labDaysByGroupName = new HashMap<>();
        // pair -> the strongest reservation found so far, with its provenance
        Map<BlockKey, Integer> out = new HashMap<>();
        Map<BlockKey, String> provenance = new HashMap<>();
        for (Course c : Course.<Course>listAll()) {
            if (c.teacher == null || c.studentGroup == null || c.subject == null || c.isLab) {
                continue; // lab courses are some phase's pin population
            }
            String groupName = groupNamesById.computeIfAbsent(c.studentGroup.id, id -> {
                StudentGroup g = StudentGroup.findById(id);
                return g != null ? g.name : null;
            });
            if (groupName == null
                    || !DailyActivityQuota.isQuotaBoundLabSubject(groupName, c.subject)) {
                continue;
            }
            BlockKey pair = new BlockKey(c.teacher.id, c.studentGroup.id);
            if (!populationPairs.contains(c.teacher.id)
                    || pinnedSubjectsByPair.getOrDefault(pair, Set.of()).contains(c.subject)) {
                continue;
            }
            Teacher teacher = teacherByPairId.computeIfAbsent(c.teacher.id, id ->
                    Teacher.<Teacher>findById(id));
            if (teacher == null) {
                continue;
            }
            int cap = teacher.getEffectiveMaxDailyHours();
            int reserved;
            String prov;
            Integer goldenAvg = golden.averageTeacherLoadOnSubjectDays(
                    teacher.name, groupName, c.subject);
            if (goldenAvg != null) {
                reserved = goldenRhythmReservation(cap, goldenAvg);
                prov = "golden avg load " + goldenAvg + "h on its " + c.subject + " days";
            } else {
                int labDays = labDaysByGroupName.computeIfAbsent(groupName, name ->
                        (int) DailyActivityQuota.count(
                                "studentGroup.name = ?1 and labHours > 0", name));
                // Review finding 1a: the spread rule is CLAMPED to cap-1 like the
                // golden path (110h/5 lab days would reserve 22h on a cap of 6,
                // zero the pair's budget and drop every session: the round-6
                // starvation this class promises never to do).
                reserved = Math.max(1, Math.min(cap - 1,
                        reservedHoursPerLabDay(c.totalHours, labDays)));
                prov = "fallback spread " + c.totalHours + "h/" + labDays + " lab days (clamped)";
            }
            if (reserved > out.getOrDefault(pair, 0)) {
                out.put(pair, reserved);
                provenance.put(pair, prov);
            }
        }
        if (!out.isEmpty()) {
            for (var e : out.entrySet()) {
                LOG.infof("[BudgetReservation] TASK-516 round 7: pair %s reserved %dh/day (%s)",
                        e.getKey(), e.getValue(), provenance.get(e.getKey()));
            }
            LOG.infof("[BudgetReservation] TASK-516 round 7: %d pair(s) reserved;"
                    + " golden-derived where the golden has evidence,"
                    + " round-6 spread otherwise%s",
                    out.size(), golden.available ? "" : " (golden UNREADABLE: all fallback)");
        }
        return out;
    }

    /**
     * TASK-518 plan sizing (AC#1/#2): BEFORE the greedy runs, the pair's planned
     * pin hours must fit the net budget (cap - reservation) x labDays together
     * with the non-enumerated demand the reservation protects. When they do not,
     * TRAILING whole sessions are handed back to the solver (the pre-chain
     * "overflow" semantics, never a day-zeroing and never an orphan clip tail:
     * the solver sees the same aggregate headroom the reservation created).
     * Pure map-in/map-out so the rule is unit-testable without a DB.
     *
     * @return the plan to pin (same map when the budget already holds)
     */
    static Map<BlockKey, List<LabSession>> applyPlanSizing(
            Map<BlockKey, List<LabSession>> sessionsByKey,
            Map<BlockKey, Integer> reservations,
            Map<BlockKey, Integer> labDaysByPair,
            Map<BlockKey, Integer> capByPair) {
        Map<BlockKey, List<LabSession>> out = new HashMap<>(sessionsByKey);
        for (var e : reservations.entrySet()) {
            BlockKey pair = e.getKey();
            int reservation = e.getValue();
            List<LabSession> sessions = out.get(pair);
            if (sessions == null || sessions.isEmpty()) {
                continue;
            }
            Integer labDays = labDaysByPair.get(pair);
            Integer cap = capByPair.get(pair);
            if (labDays == null || cap == null || labDays <= 0) {
                continue;
            }
            int budget = Math.max(0, (cap - reservation) * labDays);
            int planned = sessions.stream().mapToInt(sess -> sess.lessons.size()).sum();
            if (planned <= budget) {
                continue;
            }
            List<LabSession> kept = new ArrayList<>(sessions);
            int keptHours = planned;
            // Review finding 1b: NEVER trim to zero. With a budget of 0 (or
            // sessions larger than the whole budget) the old loop dropped every
            // session: keep one regardless, the overflow accounting is the log.
            while (kept.size() > 1 && keptHours > budget) {
                keptHours -= kept.remove(kept.size() - 1).lessons.size();
            }
            if (keptHours > budget) {
                LOG.warnf("[BudgetReservation] TASK-518 plan sizing: pair %s still %dh over"
                        + " the %dh net budget after keeping ONE session (oversized blocks)",
                        pair, keptHours, budget);
            }
            LOG.infof("[BudgetReservation] TASK-518 plan sizing: pair %s planned %dh"
                    + " > net budget %dh ((cap %d - %dh reserved) x %d lab days)"
                    + " - %d trailing session(s) returned to the solver",
                    pair, planned, budget, cap, reservation, labDays,
                    sessions.size() - kept.size());
            out.put(pair, kept);
        }
        return out;
    }

    /**
     * TASK-518 DB side of the plan sizing: resolves the pair's lab-day count and
     * teacher cap, then delegates to {@link #applyPlanSizing}.
     */
    static Map<BlockKey, List<LabSession>> applyPlanSizingTo(
            Map<BlockKey, List<LabSession>> sessionsByKey,
            Map<BlockKey, Integer> reservations) {
        if (reservations.isEmpty() || sessionsByKey.isEmpty()) {
            return sessionsByKey;
        }
        Map<BlockKey, Integer> labDaysByPair = new HashMap<>();
        Map<BlockKey, Integer> capByPair = new HashMap<>();
        int skipped = 0;
        String skipWhy = "";
        for (BlockKey pair : reservations.keySet()) {
            StudentGroup g = StudentGroup.findById(pair.studentGroupId());
            Teacher t = Teacher.findById(pair.teacherId());
            if (g == null || t == null) {
                skipped++;
                skipWhy = "unresolved group/teacher";
                continue;
            }
            int labDays = (int) DailyActivityQuota.count(
                    "studentGroup.name = ?1 and labHours > 0", g.name);
            if (labDays <= 0) {
                skipped++;
                skipWhy = "zero lab days for " + g.name;
                continue;
            }
            labDaysByPair.put(pair, labDays);
            capByPair.put(pair, t.getEffectiveMaxDailyHours());
        }
        if (skipped > 0) {
            // Review finding 2: a silent skip here would leave the pair unsized
            // with no trace; the fail-non-silente rule wants the count and why.
            LOG.warnf("[BudgetReservation] TASK-518 plan sizing: %d reserved pair(s)"
                    + " SKIPPED (%s) - their plan is NOT trimmed", skipped, skipWhy);
        }
        return applyPlanSizing(sessionsByKey, reservations, labDaysByPair, capByPair);
    }

    // ─── Golden load extraction (deriver-protocol traps excluded) ───────────

    /** Teacher total load per day + the days each (teacher, group, subject) sits on. */
    private static final class GoldenLoad {
        final boolean available;
        private final Map<String, Map<LocalDate, Integer>> teacherLoadByDay = new HashMap<>();
        private final Map<String, Set<LocalDate>> subjectDays = new HashMap<>();

        GoldenLoad(boolean available) {
            this.available = available;
        }

        /** Average TOTAL load of the teacher on the golden days placing the subject, or null. */
        Integer averageTeacherLoadOnSubjectDays(String teacher, String group, String subject) {
            Set<LocalDate> days = subjectDays.get(teacher + "|" + group + "|" + subject);
            if (days == null || days.isEmpty()) {
                return null;
            }
            Map<LocalDate, Integer> load = teacherLoadByDay.getOrDefault(teacher, Map.of());
            long total = 0;
            for (LocalDate d : days) {
                total += load.getOrDefault(d, 0);
            }
            return (int) Math.round((double) total / days.size());
        }
    }

    private static GoldenLoad loadGoldenLoad() {
        GoldenLoad out = new GoldenLoad(true);
        List<String[]> goldenRows;
        try {
            goldenRows = SeedCsv.load(GOLDEN_RESOURCE, GOLDEN_COLUMNS);
        } catch (RuntimeException e) {
            // Golden-free degradation (rehearsal 18/08): the DECLARED fallback
            // (round-6 spread rule) must actually fire when the file is absent,
            // never abort the pre-assign. The banner below says so loudly.
            LOG.warnf("[BudgetReservation] golden not readable (%s) - all reservations"
                    + " fall back to the round-6 spread rule (clamped to cap-1)", e.getMessage());
            return new GoldenLoad(false);
        }
        for (String[] row : goldenRows) {
            if ("True".equalsIgnoreCase(row[3])) {
                continue; // closed rows are not real scheduled hours
            }
            String teacher = row[5];
            String group = row[4];
            String subject = row[6];
            if (teacher == null || group == null || subject == null
                    || teacher.equals("Stage") || teacher.equals("Fasullo")
                    || subject.equals("Stage") || subject.equals("Fasullo")) {
                continue;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(row[0]);
            } catch (RuntimeException e) {
                continue;
            }
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                continue; // golden Saturday hours are not placeable in our grid
            }
            out.teacherLoadByDay.computeIfAbsent(teacher, t -> new TreeMap<>())
                    .merge(date, 1, Integer::sum);
            out.subjectDays.computeIfAbsent(teacher + "|" + group + "|" + subject,
                    k -> new HashSet<>()).add(date);
        }
        if (out.teacherLoadByDay.isEmpty()) {
            LOG.warnf("[BudgetReservation] golden not readable - all reservations"
                    + " fall back to the round-6 spread rule (provenance: declared)");
            return new GoldenLoad(false);
        }
        return out;
    }
}
