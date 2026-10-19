package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.util.SeedCsv;

/**
 * TASK-364 follow-up (mandate 2026-08-16 pomeriggio): make the LabRotation lever
 * REACHABLE without new school data. On the 2026/27 seed {@code
 * DataGenerator.generateLabRotations()} is intentionally empty (rotations deferred),
 * so {@code LabRotation.count() == 0} short-circuits pre-assign Phase 1/2/3 and the
 * TASK-467 enumerator is structurally inert.
 *
 * <p><b>Derivation protocol (golden proposes, input disposes).</b> The WEEKDAY each
 * (teacher, group) acconciatura pair practices is read from the golden's MODAL weekday
 * (plus any weekday holding >= 20% of the pair's hours: the rotating pairs the rotation
 * model exists for, e.g. T18 G02 Tue+Fri); slot/block shape is the PER-WEEKDAY
 * modal run (a rotating pair can practice very different hours on its two weekdays).
 * Every proposed weekday is then VALIDATED against the school's own calendar sheets
 * (a lab day for that group: class_days modality L, FC lab_hours > 0): a weekday the
 * input does not confirm is NOT seeded (it becomes a workbook question, not a guess).
 * Measured on the real seed: the production population (Phase 1's own tag filter, so
 * the subject_overrides de-tags apply) is 13 pairs -> 15 rows, 2 of them rotating.
 *
 * <p><b>Provenance, declared.</b> The weekday/slot shape is golden-derived (the
 * {@code seed/lessons.csv} runtime-read family censused in TASK-452; same provenance as
 * the Q22 availability and the TASK-451/464 statics). With the golden GONE the armed
 * flag degrades to a logged no-op (Phase 1/2/3 stay skipped): the rows must then come
 * from declared input (workbook sheet, the round-1 mining 23_GiornoFisso family) -
 * that is the documented school dependency.
 *
 * <p><b>Gated DEFAULT OFF</b> ({@code planner.pre-assign.labrotation.derive-from-golden.enabled}):
 * arming it makes Phase 1/2/3 run again and the TASK-467 enumerator reachable, but NO
 * efficacy is claimed without its own A/B gate (mandate rule: nessuna adozione senza
 * gate). NOTE: arming also shifts expected-days/completion KPIs (the periods feed
 * ExpectedDaysCalculator) - declared side surface.
 */
@ApplicationScoped
public class LabRotationDeriver {

    private static final Logger LOG = Logger.getLogger(LabRotationDeriver.class);

    /** Opt-in flag: derive-and-seed golden-validated rotation rows when the table is empty. */
    public static final String FLAG = "planner.pre-assign.labrotation.derive-from-golden.enabled";

    /** A weekday must hold at least this share of a pair's hours to earn a row (rotating pairs). */
    static final double WEEKDAY_SHARE_FLOOR = 0.20;

    static final String GOLDEN_RESOURCE = "lessons.csv";
    static final int GOLDEN_COLUMNS = 7; // date,day,slot,closed,group,teacher,subject
    static final int CLASS_DAYS_COLUMNS = 4; // group,date,hours,modality
    static final int FC_CSV_COLUMNS = 5; // group,date,aula_hours,lab_hours,stage_hours

    /** The measured TASK-467 scope: Phase 1's roomTag family (acconciatura). */
    static final String ROOM_TAG = "acconciatura";

    public static boolean enabled() {
        // Same opt-in form as the other prototype flags: unset reads disabled.
        return Boolean.parseBoolean(System.getProperty(FLAG, "false"));
    }

    /**
     * Derive and persist the rotation rows when armed and the table is empty (idempotent:
     * a second call with rows present is a no-op). Returns the number of rows persisted.
     */
    @Transactional
    public int deriveAndPersistIfEmpty() {
        if (!enabled()) {
            return 0; // the caller also checks, so the off path never opens a transaction
        }
        long existing = LabRotation.count();
        if (existing > 0) {
            LOG.infof("[LabRotationDeriver] %d LabRotation rows already present - no derivation", existing);
            return 0;
        }

        // The (teacher, group) pairs and their acconciatura subjects come from the DB:
        // the SAME population loadLabSessionsByRoomTag feeds Phase 1, so the
        // subject_overrides de-tags apply here too (G14 and G12 Servizio stilistico
        // are aula for Phase 1 and stay out of the derivation as well).
        Map<PairKey, Set<String>> subjectsByPair = new TreeMap<>();
        List<Course> courses = Course.find(
                "SELECT c FROM Course c WHERE ?1 MEMBER OF c.requiredRoomTags", ROOM_TAG).list();
        for (Course c : courses) {
            if (c.teacher == null || c.studentGroup == null || c.teacher.name == null
                    || c.studentGroup.name == null || !c.isLab) {
                continue;
            }
            subjectsByPair.computeIfAbsent(new PairKey(c.teacher.name, c.studentGroup.name),
                    k -> new TreeSet<>()).add(c.subject);
        }
        if (subjectsByPair.isEmpty()) {
            LOG.infof("[LabRotationDeriver] no %s-tagged lab pairs in the DB - nothing to derive", ROOM_TAG);
            return 0;
        }

        Map<PairKey, List<GoldenLesson>> pairLessons;
        try {
            pairLessons = loadGoldenPairLessons(subjectsByPair);
        } catch (RuntimeException e) {
            // No golden on the classpath (the declared end-state once the file retires):
            // a hard failure here would abort the whole pre-assign, so the armed flag
            // degrades to a clean no-op - Phase 1/2/3 stay skipped exactly as today.
            LOG.warnf("[LabRotationDeriver] golden not readable (%s) - no rows derived,"
                    + " Phase 1/2/3 stay skipped", e.getMessage());
            return 0;
        }
        List<DerivedRotation> derived = derive(pairLessons, loadLabDaysByGroup(),
                subjectsByPair.keySet());
        int persisted = 0;
        int skippedNames = 0;
        // TASK-502 provenance counters: how many golden shapes needed a CLIP to fit the
        // group's calendar window / the teacher's daily cap, and how many were discarded
        // because no sensible shape survived (round 2 pinned raw 8h shapes on narrow
        // windows and the solver inherited -21 H20 and -15 H7).
        int clippedWindow = 0;
        int clippedTeacherCap = 0;
        int discarded = 0;
        for (DerivedRotation r : derived) {
            Teacher teacher = Teacher.find("name", r.teacherName()).firstResult();
            StudentGroup group = StudentGroup.find("name", r.groupName()).firstResult();
            if (teacher == null || group == null) {
                skippedNames++;
                LOG.warnf("[LabRotationDeriver] cannot resolve teacher='%s' or group='%s' - row skipped",
                        r.teacherName(), r.groupName());
                continue;
            }
            // H20: clamp the shape INTO the group's calendar window for this weekday
            // (GroupCalendarRule, same source the constraint reads; periodStart as the
            // representative date for override resolution).
            int[] win = GroupCalendarService.effectiveRange(
                    GroupCalendarRule.<GroupCalendarRule>find("studentGroup", group).list(),
                    r.dayOfWeek(), r.yearStart());
            int slotStart = Math.max(r.slotStart(), win[0]);
            int windowSize = Math.max(0, win[1] - slotStart + 1);
            if (windowSize <= 0) {
                discarded++;
                LOG.infof("[LabRotationDeriver] discard %s/%s %s: window [%d,%d] cannot host slot %d",
                        r.teacherName(), r.groupName(), r.dayOfWeek(), win[0], win[1], r.slotStart());
                continue;
            }
            int blockSize = r.blockSize();
            if (blockSize > windowSize) {
                clippedWindow++;
                blockSize = windowSize;
            }
            // H7: the teacher's daily cap bounds the rotation's own block too
            int teacherCap = teacher.getEffectiveMaxDailyHours();
            if (blockSize > teacherCap) {
                clippedTeacherCap++;
                blockSize = teacherCap;
            }
            if (blockSize < 1) {
                discarded++;
                continue;
            }
            LabRotation row = new LabRotation(teacher, group, r.dayOfWeek(), slotStart,
                    blockSize, r.yearStart(), r.yearEnd(), ROOM_TAG);
            row.persist();
            persisted++;
        }
        LOG.infof("[LabRotationDeriver] derived %d golden-validated LabRotation rows for %d %s pair(s)"
                + " (skipped: %d unresolvable names; TASK-502 shape hygiene: %d clipped to calendar"
                + " window, %d clipped to teacher cap, %d discarded; weekday floor 20%%; every seeded"
                + " weekday is an input-calendar lab day). Provenance: golden modal shape, validated"
                + " vs calendario sheets (TASK-452 census family). Rows survive until cleared.",
                persisted, subjectsByPair.size(), ROOM_TAG, skippedNames,
                clippedWindow, clippedTeacherCap, discarded);
        return persisted;
    }

    // Pure derivation core (unit-testable without a DB)

    /** One derived rotation row, names only (id resolution is the DB side's job). */
    record DerivedRotation(String teacherName, String groupName, DayOfWeek dayOfWeek,
            int slotStart, int blockSize, LocalDate yearStart, LocalDate yearEnd) {
    }

    /** (teacherName, groupName) identity for the derivation core. */
    record PairKey(String teacherName, String groupName) implements Comparable<PairKey> {
        @Override
        public int compareTo(PairKey o) {
            int c = teacherName.compareTo(o.teacherName);
            return c != 0 ? c : groupName.compareTo(o.groupName);
        }
    }

    /** One golden lesson relevant to a pair: weekday + slot index + date. */
    record GoldenLesson(DayOfWeek weekday, int slot, LocalDate date) {
    }

    /**
     * The pure rule: per pair, one row per weekday holding >= {@link #WEEKDAY_SHARE_FLOOR}
     * of the pair's hours AND confirmed as a lab day by the input calendar; slot/block
     * from that WEEKDAY's modal run (first slot and length); period = the pair's own
     * golden date span (period SPLITS are the school-dependent part and stay unsplit:
     * two rows for a rotating pair are the documented MULTI_DAY shape).
     */
    static List<DerivedRotation> derive(Map<PairKey, List<GoldenLesson>> pairLessons,
            Map<String, Set<DayOfWeek>> labDaysByGroup, Set<PairKey> pairs) {
        List<DerivedRotation> out = new ArrayList<>();
        for (PairKey pair : pairs) {
            List<GoldenLesson> lessons = pairLessons.get(pair);
            if (lessons == null || lessons.isEmpty()) {
                continue;
            }
            Set<DayOfWeek> labDays = labDaysByGroup.getOrDefault(pair.groupName(), Set.of());
            EnumMap<DayOfWeek, Integer> byWeekday = new EnumMap<>(DayOfWeek.class);
            LocalDate yearStart = null;
            LocalDate yearEnd = null;
            for (GoldenLesson l : lessons) {
                byWeekday.merge(l.weekday(), 1, Integer::sum);
                if (yearStart == null || l.date().isBefore(yearStart)) {
                    yearStart = l.date();
                }
                if (yearEnd == null || l.date().isAfter(yearEnd)) {
                    yearEnd = l.date();
                }
            }
            int total = lessons.size();
            // PER-WEEKDAY run shapes: a rotating pair can practice very different hours
            // on its two weekdays (measured: T18 G06 Fri 5h from slot 0 but Wed
            // single hours from slot 5); a pair-global modal would seed the majority
            // weekday's shape onto the minority weekday too (review finding).
            Map<DayOfWeek, Map<Integer, Integer>> firstSlotByWeekday = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, Map<Integer, Integer>> lengthByWeekday = new EnumMap<>(DayOfWeek.class);
            tallyRunShapes(lessons, firstSlotByWeekday, lengthByWeekday);
            for (Map.Entry<DayOfWeek, Integer> e : byWeekday.entrySet()) {
                if (e.getValue() < WEEKDAY_SHARE_FLOOR * total) {
                    continue;
                }
                if (!labDays.contains(e.getKey())) {
                    continue; // golden proposes, input disposes: not a lab day, not seeded
                }
                out.add(new DerivedRotation(pair.teacherName(), pair.groupName(), e.getKey(),
                        modal(firstSlotByWeekday.get(e.getKey())),
                        modal(lengthByWeekday.get(e.getKey())), yearStart, yearEnd));
            }
        }
        return out;
    }

    /** Tally the pair's run shapes PER WEEKDAY: a run = maximal consecutive slots of
     *  the pair in one day, credited to that day's weekday. */
    private static void tallyRunShapes(List<GoldenLesson> lessons,
            Map<DayOfWeek, Map<Integer, Integer>> firstSlotByWeekday,
            Map<DayOfWeek, Map<Integer, Integer>> lengthByWeekday) {
        Map<LocalDate, List<GoldenLesson>> byDate = new TreeMap<>();
        for (GoldenLesson l : lessons) {
            byDate.computeIfAbsent(l.date(), d -> new ArrayList<>()).add(l);
        }
        for (List<GoldenLesson> day : byDate.values()) {
            day.sort(java.util.Comparator.comparingInt(GoldenLesson::slot));
            int runStart = 0;
            for (int i = 1; i <= day.size(); i++) {
                if (i == day.size() || day.get(i).slot() != day.get(i - 1).slot() + 1) {
                    DayOfWeek wd = day.get(runStart).weekday();
                    firstSlotByWeekday.computeIfAbsent(wd, w -> new TreeMap<>())
                            .merge(day.get(runStart).slot(), 1, Integer::sum);
                    lengthByWeekday.computeIfAbsent(wd, w -> new TreeMap<>())
                            .merge(i - runStart, 1, Integer::sum);
                    runStart = i;
                }
            }
        }
    }

    private static int modal(Map<Integer, Integer> tally) {
        if (tally == null || tally.isEmpty()) {
            return 0;
        }
        int best = 0;
        int bestN = -1;
        for (Map.Entry<Integer, Integer> e : new TreeMap<>(tally).entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    // Classpath loads (same family as ConstrainedTeacherAllocator's golden loaders)

    /** Golden lessons per pair, restricted to the pair's acconciatura subjects, traps excluded. */
    static Map<PairKey, List<GoldenLesson>> loadGoldenPairLessons(Map<PairKey, Set<String>> subjectsByPair) {
        Map<PairKey, List<GoldenLesson>> out = new LinkedHashMap<>();
        for (String[] row : SeedCsv.load(GOLDEN_RESOURCE, GOLDEN_COLUMNS)) {
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
            Set<String> subjects = subjectsByPair.get(new PairKey(teacher, group));
            if (subjects == null || !subjects.contains(subject)) {
                continue;
            }
            LocalDate date;
            int slotIndex;
            try {
                date = LocalDate.parse(row[0]);
                // Slot cell format "HH:MM HH:MM": the start token indexes the grid
                // (slot 0 = 08:00), same convention as the sibling golden loaders.
                slotIndex = LocalTime.parse(row[2].split(" ")[0]).getHour() - 8;
            } catch (RuntimeException e) {
                continue; // malformed row: skip, never throw into the pre-assign run
            }
            if (slotIndex < 0 || date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                continue; // golden Saturday hours are not placeable in our grid (KPI protocol)
            }
            out.computeIfAbsent(new PairKey(teacher, group), k -> new ArrayList<>())
                    .add(new GoldenLesson(date.getDayOfWeek(), slotIndex, date));
        }
        return out;
    }

    /** The groups' lab weekdays from the school's calendar sheets (input authority). */
    static Map<String, Set<DayOfWeek>> loadLabDaysByGroup() {
        Map<String, Set<DayOfWeek>> out = new HashMap<>();
        for (String[] row : SeedCsv.load("calendario_class_days.csv", CLASS_DAYS_COLUMNS)) {
            if ("L".equals(row[3])) {
                out.computeIfAbsent(row[0], g -> EnumSet.noneOf(DayOfWeek.class))
                        .add(LocalDate.parse(row[1]).getDayOfWeek());
            }
        }
        for (String[] row : SeedCsv.load("calendario_fc.csv", FC_CSV_COLUMNS)) {
            if (parseIntOrZero(row[3]) > 0) { // lab_hours column
                out.computeIfAbsent(row[0], g -> EnumSet.noneOf(DayOfWeek.class))
                        .add(LocalDate.parse(row[1]).getDayOfWeek());
            }
        }
        return out;
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
