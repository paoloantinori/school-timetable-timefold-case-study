package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.util.SeedCsv;

/**
 * TASK-435: pure, unit-testable planner for the <b>constrained-teacher pre-assignment pin</b>.
 *
 * <p>A "constrained teacher" is one whose weekly availability is squeezed to 1-2 weekdays
 * (derivable from H5 {@code VACATION} rows). The campaign's systemic node is exactly such a
 * teacher: T01 ({@code Servizio tecnico e stilistico}, 231h across 1/2/G10 + G12,
 * Thursday-only by H5). The solver provably cannot place him (24 H0 of TASK-432-per-group,
 * 40 H0 of G06 order-fragility, BoxRepair 0/41), so the human planner HAND-PLACES him early
 * and treats him as pinned. This class encodes that hand-placement: it derives, from the
 * authoritative golden reference, the per-(group, Thursday, hours) allocation to pin BEFORE the
 * solver runs, so the solver only moves "what is left out" (human-planner-heuristics.md line 68).
 *
 * <p><b>Why the golden is the reference, not the sheet:</b> the school sheet
 * ({@code calendario_class_days.csv}) is teacher-agnostic, it records a group's TOTAL expected
 * hours per date, not which teacher fills them. Verified (2026-07-28): the sheet's Thursday hours
 * for 1/2/G10 + G12 sum to 216/210/205/141h, not T01's 231h. Fedora's binding refinement
 * ("follow the per-date sheet profiles; if a profile does not specify WHICH Thursdays, derive from
 * the golden") resolves to: the golden ({@code seed/lessons.csv}) is the authoritative reference
 * for <i>which Thursdays</i> are the teacher's, and the sheet only cross-checks the hour count.
 * Verified invariant: on every one of the teacher's 35 golden Thursdays, his hours equal the
 * sheet's group-hours that day (the monographic property, when he teaches he fills the whole day).
 *
 * <p>This class is deliberately PURE (no JPA, no Timefold, keyed by group NAME not entity) so its
 * logic is unit-testable in isolation, mirroring {@link com.school.planner.solver.WeeklyTemplatePlanner}.
 * The actual DB writes ({@code lesson.pinned = true}, persistence) live in
 * {@link HybridPhase1aService}, which consumes a {@link Plan} and pins via
 * {@code PreAssignmentValidator.canPin} + {@code pinLesson}.
 *
 * <p>Guard-rails (Fedora binding refinement #2): H7 (teacher cap, e.g. 8h for T01) and H5
 * (available weekdays) are respected <i>by construction of the plan</i>, any profile entry that
 * would violate them is clamped/skipped with a recorded warning rather than pinned. The plan is
 * deterministic (same input &rarr; identical targets); service-level idempotency comes from pinning
 * only currently-unpinned lessons, so a re-run is a no-op.
 */
public final class ConstrainedTeacherAllocator {

    private static final Logger LOG = Logger.getLogger(ConstrainedTeacherAllocator.class);

    /** The classpath golden (school's hand-made timetable), mirrored from {@code claudedocs/.../data/}. */
    static final String GOLDEN_RESOURCE = "lessons.csv";
    static final int GOLDEN_COLUMNS = 7;

    private ConstrainedTeacherAllocator() {
        // Pure utility - no instances
    }

    /** A single pin target: pin {@code hours} lessons of group {@code groupName} to {@code date}. */
    public record PinTarget(String groupName, LocalDate date, int hours) {
        /** Stable key for dedup / idempotency accounting. */
        public String key() {
            return groupName + "|" + date;
        }
    }

    /**
     * Result of planning a constrained teacher's pin allocation.
     *
     * @param targets            validated pin targets (H5/H7-respecting, disjoint, budget-capped)
     * @param warnings           human-readable diagnostics (H5/H7 clamp, disjointness, residual)
     * @param pinnedHoursByGroup hours this plan newly pins per group (target hours summed)
     * @param residualHoursByGroup hours of each group's course budget the plan leaves to the solver
     */
    public record Plan(
            List<PinTarget> targets,
            List<String> warnings,
            Map<String, Integer> pinnedHoursByGroup,
            Map<String, Integer> residualHoursByGroup) {
    }

    /**
     * Build the pin plan for a constrained teacher from his golden reference profile.
     *
     * <p>Validates, per profile entry:
     * <ul>
     *   <li><b>H7</b>: clamps {@code hours} to {@code h7Cap} (T01 cap = 8), warning if clamped.</li>
     *   <li><b>Disjointness</b>: one group per date (the golden demonstrates this). A second group
     *       claiming an already-claimed date is skipped with a warning (keep first = golden-faithful).</li>
     *   <li><b>Budget</b>: accumulates hours per group; once a group's course budget is exhausted,
     *       further entries for it are skipped with a warning (over-allocation guard).</li>
     * </ul>
     * H5 (Thursday-only availability) is NOT re-checked here: the caller gates on
     * {@code CONSTRAINED_TEACHER_MAX_WEEKDAYS} (the golden's distinct weekdays), so every date in the
     * profile is already on an available weekday by construction. After planning,
     * {@code residualHoursByGroup} reports each group's unplanned budget, the "explicit exceptions"
     * the pattern-exhaustion principle leaves to the solver.
     *
     * @param goldenProfile       groupName &rarr; date &rarr; hours (the teacher's golden allocation)
     * @param courseBudgetByGroup groupName &rarr; total course hours (e.g. G02 &rarr; 60)
     * @param h7Cap               teacher max daily hours (T01 = 8)
     */
    public static Plan plan(
            Map<String, Map<LocalDate, Integer>> goldenProfile,
            Map<String, Integer> courseBudgetByGroup,
            int h7Cap) {

        List<PinTarget> targets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> pinnedHoursByGroup = new LinkedHashMap<>();
        Map<LocalDate, String> dateClaimant = new HashMap<>();

        if (goldenProfile == null || goldenProfile.isEmpty()) {
            return new Plan(targets, warnings, pinnedHoursByGroup, residualAll(courseBudgetByGroup, pinnedHoursByGroup));
        }

        // Deterministic iteration: groups alphabetical, dates ascending.
        List<String> groups = new ArrayList<>(goldenProfile.keySet());
        Collections.sort(groups);
        for (String group : groups) {
            Map<LocalDate, Integer> byDate = goldenProfile.get(group);
            if (byDate == null) {
                continue;
            }
            int budget = courseBudgetByGroup == null ? Integer.MAX_VALUE
                    : courseBudgetByGroup.getOrDefault(group, Integer.MAX_VALUE);
            int pinnedForGroup = 0;

            List<LocalDate> dates = new ArrayList<>(byDate.keySet());
            Collections.sort(dates);
            for (LocalDate date : dates) {
                int rawHours = byDate.get(date);

                // Disjointness: one group per date (golden property). Keep the first claimant.
                String prior = dateClaimant.get(date);
                if (prior != null && !prior.equals(group)) {
                    warnings.add(String.format(
                            "Disjointness: %s %s already claimed by %s; skipped (one group per date)",
                            group, date, prior));
                    continue;
                }

                // H7 daily cap.
                int hours = rawHours;
                if (hours > h7Cap) {
                    warnings.add(String.format("H7: %s %s wants %dh, capped to %d", group, date, rawHours, h7Cap));
                    hours = h7Cap;
                }

                // Budget cap: never pin beyond the course budget.
                if (pinnedForGroup + hours > budget) {
                    int remaining = Math.max(0, budget - pinnedForGroup);
                    if (remaining == 0) {
                        warnings.add(String.format(
                                "Budget: %s %s (%dh) would exceed course budget %dh; skipped",
                                group, date, hours, budget));
                        continue;
                    }
                    warnings.add(String.format(
                            "Budget: %s %s trimmed %dh->%dh to stay within course budget %dh",
                            group, date, hours, remaining, budget));
                    hours = remaining;
                }

                targets.add(new PinTarget(group, date, hours));
                dateClaimant.put(date, group);
                pinnedForGroup += hours;
            }
            pinnedHoursByGroup.put(group, pinnedForGroup);
        }

        Map<String, Integer> residual = residualAll(courseBudgetByGroup, pinnedHoursByGroup);
        // Surface non-zero residuals as the pattern-exhaustion "explicit exceptions".
        for (Map.Entry<String, Integer> e : residual.entrySet()) {
            if (e.getValue() > 0) {
                warnings.add(String.format(
                        "Residual: %dh of %s course budget left to the solver (explicit exception)",
                        e.getValue(), e.getKey()));
            }
        }

        return new Plan(targets, warnings, pinnedHoursByGroup, residual);
    }

    private static Map<String, Integer> residualAll(Map<String, Integer> budget, Map<String, Integer> pinned) {
        Map<String, Integer> residual = new LinkedHashMap<>();
        if (budget != null) {
            for (Map.Entry<String, Integer> e : budget.entrySet()) {
                int p = pinned.getOrDefault(e.getKey(), 0);
                residual.put(e.getKey(), Math.max(0, e.getValue() - p));
            }
        }
        return residual;
    }

    /**
     * Load a teacher's golden reference profile from the classpath golden ({@code seed/lessons.csv},
     * columns {@code date,day,slot,closed,group,teacher,subject}). Returns groupName &rarr; date
     * &rarr; row-count (= hour count, one row per school hour). Empty map if the teacher has no
     * golden rows (a future-year teacher with no recorded timetable).
     *
     * @param teacherName the teacher's display name as it appears in the golden (e.g. "T01")
     */
    public static Map<String, Map<LocalDate, Integer>> loadGoldenProfile(String teacherName) {
        return loadAllGoldenProfiles().getOrDefault(teacherName, Map.of());
    }

    /**
     * Single-pass load of EVERY teacher's golden profile from the classpath golden
     * ({@code seed/lessons.csv}). Returns teacherName &rarr; groupName &rarr; date &rarr; hour-count.
     *
     * <p>Use this (not {@link #loadGoldenProfile}) from the pre-assignment phase: it parses the
     * ~13k-row golden ONCE and partitions by teacher, instead of re-reading the whole CSV once per
     * teacher (the {@code loadGoldenProfile} convenience re-parses each call, fine for a test,
     * wasteful on the solve-setup path).
     */
    public static Map<String, Map<String, Map<LocalDate, Integer>>> loadAllGoldenProfiles() {
        // Columns: 0=date 1=day 2=slot 3=closed 4=group 5=teacher 6=subject
        List<String[]> rows = SeedCsv.load(GOLDEN_RESOURCE, GOLDEN_COLUMNS);
        Map<String, Map<String, Map<LocalDate, Integer>>> byTeacher = new LinkedHashMap<>();
        for (String[] row : rows) {
            String teacher = row[5];
            LocalDate date = LocalDate.parse(row[0]);
            String group = row[4];
            byTeacher.computeIfAbsent(teacher, t -> new LinkedHashMap<>())
                    .computeIfAbsent(group, g -> new LinkedHashMap<>())
                    .merge(date, 1, Integer::sum);
        }
        return byTeacher;
    }

    /**
     * TASK-451: the golden's stable slot-0 anchor map, {@code (groupName, weekday) -> modal
     * teacher NAME at the 08:00 slot} (the teacher the school itself opens the day with, e.g.
     * G11 Friday -> T08 26/26). Pure single-pass read of the classpath golden
     * ({@code seed/lessons.csv}); unlike {@link #loadAllGoldenProfiles} this counts ONLY open
     * rows ({@code closed != True}) whose slot starts {@code "08:00"}, then takes the modal
     * teacher per (group, weekday) (ties broken first-seen, stable via LinkedHashMap).
     *
     * <p>Used by the construction builder's coordinated first-hour block
     * ({@link com.school.planner.solver.WeeklyTemplateBuilder}) as the cross-group matching
     * preference: replicate the golden's anchor where it is stable, leave the rest to
     * construction (the school does not anchor 70/70 cells either - see spike1-feasibility).
     *
     * @return groupName -> weekday -> modal slot-0 teacher name (never null/blank); a group
     *         absent from the map has no stable golden slot-0 anchor on that weekday
     */
    public static Map<String, Map<DayOfWeek, String>> loadGoldenSlot0Anchors() {
        // Columns: 0=date 1=day 2=slot 3=closed 4=group 5=teacher 6=subject
        // Golden-free degradation (rehearsal 18/08): a missing golden must degrade
        // to an EMPTY anchor map with a loud warning (first-hour block loses the
        // teacher preference, quality not feasibility), never kill /api/schedule.
        List<String[]> rows;
        try {
            rows = SeedCsv.load(GOLDEN_RESOURCE, GOLDEN_COLUMNS);
        } catch (RuntimeException e) {
            // TASK m-13 night (mandate 18/19-08): the FROZEN anchors are the second
            // (and last) congelamento the control path needs without the golden.
            // Generated once from the real golden by GoldenSlot0FreezeTest, identity-gated.
            Map<String, Map<DayOfWeek, String>> frozen = loadFrozenSlot0Anchors();
            if (!frozen.isEmpty()) {
                LOG.warnf("[Slot0Anchors] golden not readable (%s) - using the FROZEN"
                        + " anchor map (%d group(s), seed/golden-slot0-anchors.csv)",
                        e.getMessage(), frozen.size());
                return frozen;
            }
            LOG.warnf("[Slot0Anchors] golden not readable (%s) and no frozen map -"
                    + " empty anchor map, first-hour block falls back to"
                    + " semanticCourseKey ordering", e.getMessage());
            return Map.of();
        }
        Map<String, EnumMap<DayOfWeek, Map<String, Integer>>> tally = new LinkedHashMap<>();
        for (String[] row : rows) {
            if ("True".equalsIgnoreCase(row[3])) {
                continue; // closed rows are not real scheduled hours
            }
            String slot = row[2];
            if (slot == null || !slot.startsWith("08:00")) {
                continue; // slot-0 opening only
            }
            String teacher = row[5];
            if (teacher == null || teacher.isBlank()) {
                continue;
            }
            LocalDate date = LocalDate.parse(row[0]);
            tally.computeIfAbsent(row[4], g -> new EnumMap<>(DayOfWeek.class))
                    .computeIfAbsent(date.getDayOfWeek(), w -> new LinkedHashMap<>())
                    .merge(teacher, 1, Integer::sum);
        }
        Map<String, Map<DayOfWeek, String>> modal = new LinkedHashMap<>();
        for (Map.Entry<String, EnumMap<DayOfWeek, Map<String, Integer>>> ge : tally.entrySet()) {
            EnumMap<DayOfWeek, String> perWd = new EnumMap<>(DayOfWeek.class);
            for (Map.Entry<DayOfWeek, Map<String, Integer>> we : ge.getValue().entrySet()) {
                String best = null;
                int bestN = -1;
                for (Map.Entry<String, Integer> te : we.getValue().entrySet()) {
                    if (te.getValue() > bestN) {
                        bestN = te.getValue();
                        best = te.getKey();
                    }
                }
                perWd.put(we.getKey(), best);
            }
            modal.put(ge.getKey(), perWd);
        }
        return modal;
    }

    /**
     * TASK-464: the golden's co-presenza depth per {@code (tag, weekday, slotStart)} cell,
     * i.e. the number of DISTINCT groups the school itself co-locates on that weekly cell.
     * Pure single-pass read of the classpath golden ({@code seed/lessons.csv}); like
     * {@link #loadGoldenSlot0Anchors} it counts ONLY open rows ({@code closed != True}), then
     * tallies distinct groups per cell. A subject is mapped to its lab tag via the supplied
     * {@code subjectToTag} (derived from the Course entities, the authoritative subject-to-tag
     * source), so the tag string is identical to what the runtime H2/H-COPRESENCE constraints
     * read off {@code course.requiredRoomTags}. Subjects absent from the map (theory, or any
     * name drift) are skipped, so only lab hours the runtime also sees ever count toward depth.
     *
     * <p>Published pre-solve by {@code ScheduleService.refreshSolverStatics} into
     * {@link SchoolSchedule#setGoldenCoPresenceDepthStatic}; a cell's depth ({@code >= 2}) is
     * the practice the H2 pairwise exemption unlocks and the ceiling the H-COPRESENCE cap
     * enforces. On the 2026/27 seed the only tag with depth {@code >= 2} is acconciatura, on
     * the Mon AM and Thu cells the spike isolated.
     *
     * @param subjectToTag golden subject name -> single lab tag (built from Course.requiredRoomTags)
     * @return cell key ({@link SchoolSchedule#coPresenceCell}) -> depth (distinct co-present groups);
     *         never null, empty when no co-presenza is practiced
     */
    /**
     * TASK-464 helper: the authoritative {@code subject -> single-lab-tag} map, built once
     * from the loaded {@link Course} entities (subject + requiredRoomTags). Only single-tag
     * lab courses map (the seed's only shape), so the tag string is identical to what the
     * runtime H2 exemption and H-COPRESENCE cap read off {@code requiredRoomTags}. Shared by
     * {@code ScheduleService.refreshSolverStatics} (production) and the RoomPhaseSnapshotTest
     * harness so the two cannot drift (single source of truth for the loader's input). Pure:
     * iterates Course objects but returns name-keyed maps, no JPA/Timefold coupling.
     *
     * @param courses the loaded Course entities (ScheduleService derives them from lessons;
     *                the harness passes {@code snap.problem.getCourses()}); null-safe
     */
    public static Map<String, String> buildSubjectToSingleTagMap(Iterable<Course> courses) {
        Map<String, String> subjectToTag = new HashMap<>();
        if (courses != null) {
            for (Course c : courses) {
                if (c != null && c.subject != null
                        && c.requiredRoomTags != null && c.requiredRoomTags.size() == 1) {
                    subjectToTag.putIfAbsent(c.subject, c.requiredRoomTags.get(0));
                }
            }
        }
        return subjectToTag;
    }

    public static Map<String, Integer> loadGoldenCoPresenceDepth(Map<String, String> subjectToTag) {
        // Columns: 0=date 1=day 2=slot 3=closed 4=group 5=teacher 6=subject
        // Golden-free degradation (rehearsal 18/08): a missing golden must degrade
        // to an EMPTY depth map with a loud warning, never kill /api/schedule.
        // DOCUMENTED CONSEQUENCE (input-rehearsal-2026-08-16): the H2 ospite
        // exemption stops unlocking and acconciatura room-null returns (hard tier).
        List<String[]> rows;
        try {
            rows = SeedCsv.load(GOLDEN_RESOURCE, GOLDEN_COLUMNS);
        } catch (RuntimeException e) {
            // TASK-523 closure (mandate 18/08 notte): the FROZEN map is the single
            // congelamento the control path needs to survive the golden. Generated
            // ONCE from the real golden by GoldenCoPresenceFreezeTest (single
            // source: the same loader that reads the CSV below), identity-gated.
            Map<String, Integer> frozen = loadFrozenCoPresenceDepth();
            if (!frozen.isEmpty()) {
                LOG.warnf("[CoPresenceDepth] golden not readable (%s) - using the"
                        + " FROZEN depth map (%d cells, seed/golden-copresence-depth.csv)",
                        e.getMessage(), frozen.size());
                return frozen;
            }
            LOG.warnf("[CoPresenceDepth] golden not readable (%s) and no frozen map -"
                    + " empty depth map, the H2 ospite exemption is INERT (known hard"
                    + " regression, workbook question: practised depth per cell)",
                    e.getMessage());
            return Map.of();
        }
        Map<String, Set<String>> groupsByCell = new LinkedHashMap<>();
        for (String[] row : rows) {
            if ("True".equalsIgnoreCase(row[3])) {
                continue; // closed rows are not real scheduled hours
            }
            String subject = row[6];
            String tag = subject == null ? null : subjectToTag.get(subject);
            if (tag == null) {
                continue; // not a single-tag lab course the runtime also sees
            }
            String slot = row[2];
            if (slot == null || slot.isBlank()) {
                continue;
            }
            // slot column is "HH:MM HH:MM" (start end); take the start token.
            LocalTime slotStart;
            try {
                slotStart = LocalTime.parse(slot.split(" ")[0]);
            } catch (java.time.format.DateTimeParseException e) {
                continue; // malformed slot row: skip rather than abort the whole load
            }
            String group = row[4];
            if (group == null || group.isBlank()) {
                continue;
            }
            DayOfWeek weekday = LocalDate.parse(row[0]).getDayOfWeek();
            String cell = SchoolSchedule.coPresenceCell(tag, weekday, slotStart);
            groupsByCell.computeIfAbsent(cell, c -> new HashSet<>()).add(group);
        }
        Map<String, Integer> depthByCell = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : groupsByCell.entrySet()) {
            depthByCell.put(e.getKey(), e.getValue().size());
        }
        return depthByCell;
    }

    /** The frozen slot-0 anchor map (m-13 night): lines "group,WEEKDAY,teacher". */
    static final String FROZEN_SLOT0_RESOURCE = "/seed/golden-slot0-anchors.csv";

    /** Read the frozen anchor map; empty (the caller warns) when absent. */
    public static Map<String, Map<DayOfWeek, String>> loadFrozenSlot0Anchors() {
        Map<String, Map<DayOfWeek, String>> out = new LinkedHashMap<>();
        try (var in = ConstrainedTeacherAllocator.class
                .getResourceAsStream(FROZEN_SLOT0_RESOURCE);
                var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length != 3) {
                    continue;
                }
                try {
                    out.computeIfAbsent(parts[0], g -> new EnumMap<>(DayOfWeek.class))
                            .put(DayOfWeek.valueOf(parts[1]), parts[2]);
                } catch (IllegalArgumentException e) {
                    // malformed weekday: skip, never abort the load
                }
            }
        } catch (Exception e) {
            return Map.of();
        }
        return out;
    }

    /** The frozen co-presence depth map (TASK-523): lines "cell,depth" with the
     *  {@link SchoolSchedule#coPresenceCell} key format (tag|WEEKDAY|HH:MM). */
    static final String FROZEN_COPRESENCE_RESOURCE = "/seed/golden-copresence-depth.csv";

    /** Read the frozen depth map; empty (and silent: the caller warns) when absent. */
    public static Map<String, Integer> loadFrozenCoPresenceDepth() {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (var in = ConstrainedTeacherAllocator.class
                .getResourceAsStream(FROZEN_COPRESENCE_RESOURCE);
                var reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.lastIndexOf(',');
                if (comma <= 0) {
                    continue;
                }
                try {
                    out.put(line.substring(0, comma),
                            Integer.parseInt(line.substring(comma + 1).trim()));
                } catch (NumberFormatException e) {
                    // malformed line: skip, never abort the load
                }
            }
        } catch (Exception e) {
            return Map.of();
        }
        return out;
    }

    // ----- TASK-452: input-derived profile (the census ESITO 1 reconstruction) -----

    /**
     * TASK-452: the Mon-Fri school grid minus the weekdays where the teacher has a full-day
     * VACATION (the only kind that removes a whole weekday; a partial VACATION leaves the
     * uncovered slots teachable). Example: T01 has full-day VACATION Mon/Tue/Wed/Fri, so
     * his available weekday set is {Thursday}. Pure over the passed set (the caller builds it
     * from {@link com.school.planner.domain.RecurringAvailabilityRule#isFullDay} VACATION rules).
     */
    public static Set<DayOfWeek> availableWeekdays(Set<DayOfWeek> fullDayVacationWeekdays) {
        EnumSet<DayOfWeek> available = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        if (fullDayVacationWeekdays != null) {
            available.removeAll(fullDayVacationWeekdays);
        }
        return available;
    }

    /**
     * TASK-452: build a constrained-teacher profile from INPUTS only (incarichi
     * budgets + H5 available weekdays + school calendar), never reading the golden.
     * Same shape as {@link #loadAllGoldenProfiles}'s per-teacher value
     * (groupName -> date -> hours), so it is a drop-in alternative source behind
     * {@code planner.pre-assign.constrained-teacher.source=input}.
     *
     * <p>The per-date assignment is a deterministic INTERLEAVED greedy fill, stage-aware
     * and disjoint (one group per date, mirroring the golden's monographic property),
     * pinning {@code min(h7Cap, remaining, calendar-open slots)} hours per date. The
     * pre-cure builder walked a shared cursor through the dates in alphabetical group
     * order, which front-loaded the first groups into the early weeks and left the
     * last group (G10) only the tail Thursdays, where its own Mar-Apr stage window
     * deleted the cells it was assigned: its hours landed on off-grid stage days the
     * pin loop can never fill (the run3 hard-0 root cause). Instead each date now goes
     * to the FEASIBLE group with the LARGEST remaining budget (most-starved first), so
     * every group takes Thursdays spread across the whole year and no group is pushed
     * into a window its own stage deletes; the golden shows the same year-long interlace
     * (G10's 9 Thursdays run Dec 3 -> Jun 3, deliberately avoiding its Mar-Apr stage).
     * Ties on remaining are broken alphabetically ({@code groups} is alpha-sorted and the
     * comparison is strict, so the first alphabetically wins), keeping the result
     * deterministic. Feasible = still owes budget AND the date is not the group's stage
     * day AND the group's calendar is open that date. This reproduces the census
     * reconstruction ({@code /tmp/measure_task452_deducibility.py}, doc
     * {@code task452-deducibility-census-2026-08-01.md} #1): the per-group TOTALS match
     * the golden 100% (both equal the incarichi budget), but the specific dates DIFFER
     * (the school's Thursday->group map is a non-load-bearing choice). The equivalence
     * test crystallizes exactly that.
     *
     * <p><b>TASK-452 hard-0 cure: stage-aware skip (2026-08-01).</b> A date the group is on
     * stage ({@code DailyActivityQuota.stageHours > 0}, INPUT-derived, the same source M-CPS
     * reads, never the golden) makes the group infeasible for that date: it is off-grid on
     * internship, so an ordinary constrained-teacher lesson has no cell there (H25 stage
     * confinement / H26 no-session for the non-stage quota). Skipping those dates for the
     * group is the cure for the run3 hard-0; the golden proves a feasible disjoint distribution
     * exists once the stage window is respected. The signal is {@code stageHours}, NOT
     * {@code aulaHours}: for a LAB teacher like T01, {@code aulaHours == 0} conflates stage
     * days with LAB-modality days (G10 has 9 Thursdays with {@code aulaHours=0, labHours>0,
     * stageHours=0}), and capping by {@code aulaHours} would wrongly skip exactly the lab days he
     * must teach. {@code stageHours} isolates the off-grid signal. (A binary stage skip also
     * declines the partial capacity of a split stage/aula day; on T01's groups every stage
     * Thursday is full-stage {@code aulaHours=0}, so the skip is exact, not conservative.)
     *
     * <p><b>TASK-452 cura: calendar-open-slot cap (2026-08-01).</b> Each date's hours are
     * further capped to the number of slots the group's {@link GroupCalendarRule} leaves OPEN
     * that day, so a pin never lands on an H20 closed slot. The pin loop downstream places a
     * contiguous block from slot 0 upward, so any hour beyond the open window would spill onto a
     * closed slot (run-1 defect: G06 Thursday "0-5" = 6 open slots, but the profile front-loaded
     * 8h at the H7 cap, landing on the closed 14:30/15:30 slots). This count-cap is exact because
     * every constrained-teacher weekday window on the 2026/27 seed starts at slot 0 (1/G06
     * Thursday [0,5], G10/G12 [0,7]); a future window with minSlot &gt; 0 would still be
     * hour-count-correct here, but its in-window PLACEMENT depends on the pin loop's slot source
     * reading the real calendar (the deferred openSlotsForGroupDay static-map fix), since this
     * builder controls only the hour count, not the slot. The open counts are passed in explicitly
     * from the REAL GroupCalendarRule rows: SchoolSchedule's static calendar map is EMPTY at
     * pre-assign time ({@code setGroupCalendarRulesStatic} runs only in
     * {@code ScheduleService.refreshSolverStatics} at solve-start), so reading it here would always
     * see "fully open" and the cap would be a no-op. A day the group's calendar closes entirely
     * (openSlots &le; 0) is skipped for every group (the budget flows to the next open date). An
     * entry absent from {@code openSlotsByGroupDate} means "no rule = fully open", i.e. the cap
     * reduces to {@code min(h7Cap, remaining)}.
     *
     * @param budgetByGroup      groupName -> total owed hours (from Course entities)
     * @param availableWeekdays  H5-derived weekdays the teacher may teach (see
     *                           {@link #availableWeekdays}); a teacher squeezed to
     *                           {@code <= MAX_WEEKDAYS} is "constrained"
     * @param schoolDates        every school date in session (Timeslot dates)
     * @param h7Cap              teacher max daily hours (per-date hour clamp)
     * @param openSlotsByGroupDate groupName -> date -> count of calendar-open slots for that group
     *                           on that date, indexed by the caller from the REAL GroupCalendarRule
     *                           rows via {@link GroupCalendarService#effectiveRange} +
     *                           {@link GroupCalendarService#openSlotCountFromRange}; null/absent
     *                           entry = fully open (no cap beyond h7Cap)
     * @param stageDatesByGroup  groupName -> set of dates the group is on stage that day
     *                           (INPUT-derived from {@code DailyActivityQuota.stageHours > 0},
     *                           the SAME source M-CPS reads, NEVER the golden): a constrained
     *                           teacher's ordinary lesson cannot share a stage day (the group is
     *                           off-grid on internship), so the profile must skip it. Null/absent
     *                           entry = the group has no stage days (no skip). TASK-452 hard-0 cure.
     * @param quotaByGroupDate   groupName -> date -> DECLARED aula+lab quota (INPUT-derived from
     *                           the same {@code DailyActivityQuota} rows H32 reads): the density-
     *                           axis cap - the plan never pins beyond a day's own quota, because
     *                           an over-quota pin is immovable and starves the group's other days
     *                           (the 11 blocked H32 cells of h33b-run1). Null/absent entry = no
     *                           cap (the pre-density behavior).
     * @return groupName -> date -> hours (empty if no budget or no valid dates);
     *         a group whose budget cannot fully fit the available dates is
     *         returned partially filled (the residual is the solver's to place)
     */
    public static Map<String, Map<LocalDate, Integer>> buildInputDerivedProfile(
            Map<String, Integer> budgetByGroup,
            Set<DayOfWeek> availableWeekdays,
            List<LocalDate> schoolDates,
            int h7Cap,
            Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate,
            Map<String, Set<LocalDate>> stageDatesByGroup,
            Map<String, Map<LocalDate, Integer>> quotaByGroupDate) {
        Map<String, Map<LocalDate, Integer>> profile = new LinkedHashMap<>();
        if (budgetByGroup == null || budgetByGroup.isEmpty()
                || schoolDates == null || availableWeekdays == null) {
            return profile;
        }
        // Valid school dates on an available weekday, ascending (schoolDates is a HashMap keyset, unordered).
        List<LocalDate> valid = schoolDates.stream()
                .filter(d -> availableWeekdays.contains(d.getDayOfWeek()))
                .sorted()
                .toList();
        if (valid.isEmpty()) {
            return profile;
        }
        List<String> groups = new ArrayList<>(budgetByGroup.keySet());
        Collections.sort(groups); // deterministic alpha, same as plan()
        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (String g : groups) {
            int budget = budgetByGroup.getOrDefault(g, 0);
            if (budget > 0) {
                remaining.put(g, budget);
            }
        }
        // Interleaved, stage-aware, disjoint assignment (see method javadoc): for each valid date
        // hand it to the feasible group with the LARGEST remaining budget (most-starved first), so
        // no group is front-loaded into a window its own stage deletes. Feasible = owes budget AND
        // not the group's stage day AND calendar open. Ties: alpha (groups is sorted, strict >
        // keeps the first). "Largest remaining" is a proxy that COINCIDES with the more principled
        // density metric (remaining / non-stage capacity) on this seed, where G10 is both the
        // largest budget and the tightest window; switch to density if a future seed decouples them.
        for (LocalDate d : valid) {
            String best = null;
            int bestRemaining = -1;
            int bestOpenSlots = 0;
            for (String g : groups) {
                int rem = remaining.getOrDefault(g, 0);
                if (rem <= 0) {
                    continue;
                }
                if (isStageDate(stageDatesByGroup, g, d)) {
                    continue; // group on internship that date: off-grid, skip
                }
                int openSlots = openSlotsFor(openSlotsByGroupDate, g, d);
                if (openSlots <= 0) {
                    continue; // group's calendar closed this date: skip (the date stays free for another group,
                              // unlike the old shared-cursor greedy which consumed it for everyone)
                }
                // DENSITY-AXIS cap (maintainer 20/08 sera, anti-pattern piano-sopra-quota): the
                // plan must never pin beyond the day's DECLARED aula+lab quota. A pin over
                // quota is immovable (authoritative) and makes the QuotaFillRepair inert:
                // exactly the 11 blocked H32 cells of h33b-run1 (G10 monographic Thursdays
                // pinned 8h against quota 4 - the interleaved greedy capped only by
                // h7/openSlots). Same INPUT source H32 reads (DailyActivityQuota), never the
                // golden; the school's own monographic signal (quota 8) is IN the sheets.
                int quota = quotaFor(quotaByGroupDate, g, d);
                if (quota <= 0) {
                    continue; // no quota fact / closed / pure-stage day: nothing to pin toward
                }
                if (rem > bestRemaining) {
                    bestRemaining = rem;
                    bestOpenSlots = openSlots;
                    best = g;
                }
            }
            if (best == null) {
                continue;
            }
            // max(1,..) is a hang guard: openSlots>=1 here and remaining>=1, but a teacher
            // configured with maxDailyHours=0 yields h7Cap=0, which without it loops forever.
            int h = Math.max(1, Math.min(h7Cap,
                    Math.min(Math.min(bestRemaining, bestOpenSlots), quotaFor(quotaByGroupDate, best, d))));
            profile.computeIfAbsent(best, k -> new LinkedHashMap<>()).put(d, h);
            remaining.merge(best, -h, Integer::sum);
        }
        return profile;
    }

    /**
     * True when {@code date} is one of {@code group}'s stage days, per the caller's INPUT-derived
     * index ({@code DailyActivityQuota.stageHours > 0}, the same source M-CPS reads). Absent map,
     * unknown group, or an empty set means the group has no stage days (never skip). See
     * {@link #buildInputDerivedProfile} for why a stage day makes the group infeasible for the date.
     */
    private static boolean isStageDate(
            Map<String, Set<LocalDate>> stageDatesByGroup, String group, LocalDate date) {
        if (stageDatesByGroup == null) {
            return false;
        }
        Set<LocalDate> dates = stageDatesByGroup.get(group);
        return dates != null && dates.contains(date);
    }

    /**
     * The (group, date)'s DECLARED aula+lab quota from the caller's INPUT-derived index (the
     * same {@code DailyActivityQuota} rows H32 reads), or {@link #NO_CALENDAR_CAP} when absent
     * (null map / unknown group / uncovered date: no cap, the pre-density behavior). The
     * density-axis cap of {@link #buildInputDerivedProfile} - see the call site's comment.
     */
    private static int quotaFor(
            Map<String, Map<LocalDate, Integer>> quotaByGroupDate, String group, LocalDate date) {
        if (quotaByGroupDate == null) {
            return NO_CALENDAR_CAP;
        }
        Map<LocalDate, Integer> byDate = quotaByGroupDate.get(group);
        if (byDate == null) {
            // group not covered by the quota sheets at all: pre-cure behavior (uncapped)
            return NO_CALENDAR_CAP;
        }
        // TASK-542 iter-5 (22/08): the group IS sheet-covered, so a date ABSENT from the
        // index is a no-session / zero-quota day, not "fully open". Treating it as
        // NO_CALENDAR_CAP let the plan pin 32 T01/G12 hours onto four dates with no
        // quota row at all: H26 is pinned-exempt so nothing fired, and the immovable pins
        // starved the group's real days (H32 cells + the M20.2 empty days of iter-2/4).
        return byDate.getOrDefault(date, 0);
    }

    /** Sentinel for "no calendar rule / fully open": the cap then reduces to min(h7Cap, remaining),
     *  the pre-cure behaviour, for any (group, date) absent from the caller's index. */
    private static final int NO_CALENDAR_CAP = Integer.MAX_VALUE;

    /**
     * The calendar-open slot count the caller indexed for this (group, date), or
     * {@link #NO_CALENDAR_CAP} when absent (null map, unknown group, or an un-indexed date). See
     * {@link #buildInputDerivedProfile} for why the caller must pass the REAL counts (the static
     * map is empty at pre-assign) and why an absent entry is treated as fully open.
     */
    private static int openSlotsFor(
            Map<String, Map<LocalDate, Integer>> openSlotsByGroupDate, String group, LocalDate date) {
        if (openSlotsByGroupDate == null) {
            return NO_CALENDAR_CAP;
        }
        Map<LocalDate, Integer> byDate = openSlotsByGroupDate.get(group);
        if (byDate == null) {
            return NO_CALENDAR_CAP;
        }
        return byDate.getOrDefault(date, NO_CALENDAR_CAP);
    }
}
