package com.school.planner.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.school.planner.service.GoldenScheduleImportService;
import com.school.planner.service.GroupCalendarService;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.SubjectNormalization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@PlanningEntity(comparatorFactoryClass = LessonDifficultyWeightFactory.class)
public class Lesson extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    public Course course;

    @Column(nullable = false)
    public boolean pinned = false;

    /**
     * TASK-419 (staged solve): true only while this lesson's {@code pinned = true}
     * was set by the STAGED solve itself (stage 1 freezes every unpinned lesson
     * already sitting on its own M32 template cell), never by the school. It is the
     * PERSISTENT twin of {@link SchoolSchedule#isPromoted} (the in-memory registry the
     * hot scoring path reads): the registry dies with the JVM, this column survives it,
     * so a crash mid-cycle leaves the promoted pins identifiable and the closing
     * "smarcatura" stays a single crash-safe statement -
     * {@code UPDATE lesson SET pinned = false, promotedPin = false WHERE promotedPin = true}
     * (ScheduleService#releasePromotedPins, also run at startup).
     *
     * <p><b>Invariant: never {@code pinned = true} without the marker for a promoted
     * lesson.</b> An AUTHORITATIVE pin (golden import, calendario/MODA, stage, lab
     * rotation, hand pin) is {@code pinned = true, promotedPin = false} and is NEVER
     * written by the staged cycle - the cycle asserts their count is unchanged
     * end-to-end. A naked {@code pinned = true} with no way to tell the two classes
     * apart is exactly the accounting hole the TASK-383 vanished-pins incident came
     * out of.
     *
     * <p>{@code columnDefinition} carries the DB-side {@code default false} (same
     * precedent as {@code DailyActivityQuota.FCQuota}): production profiles run
     * {@code schema-management.strategy=update}, and adding a NOT NULL column with no
     * default to a table that already holds ~13k lesson rows fails the ALTER outright.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean promotedPin = false;

    /**
     * True if this lesson is part of a lab session (multiple consecutive hours).
     * Used for UI grouping to display contiguous lab hours as a single block.
     * Pre-assignment sets this to true for all lab lessons.
     */
    @Column(nullable = false)
    public boolean isLabHour = false;

    /**
     * For lab hours, the sequence within the lab session (0-indexed).
     * 0 = first hour, 1 = second hour, etc.
     * Non-lab lessons have this set to -1.
     */
    @Column(nullable = false)
    public int labHourIndex = -1;

    /**
     * Total hours in this lab session (for UI grouping).
     * Non-lab lessons have this set to 0.
     */
    @Column(nullable = false)
    public int labTotalHours = 0;

    /**
     * Identifies which block session this lesson belongs to (0-indexed).
     * Set by LessonGenerator for block courses (labBlockSize > 1).
     * Lessons in the same session share the same sessionIndex.
     * Used by M23 constraint to keep block pairs on the same day.
     * Regular lessons have this set to -1.
     */
    @Column(nullable = false)
    public int sessionIndex = -1;

    @PlanningPin
    public boolean isPinned() {
        return pinned;
    }

    // TASK-398: allowsUnassigned = true enables "the box" - a lesson may sit with a
    // null timeslot instead of being FORCED into a hard conflict. This is the
    // mechanical enabler for box-the-residual construction (spike RESULTS): the
    // construction now boxes the unplaceable residual (H0 -2 each, TASK-447 weight) instead of
    // force-placing it into H3/H1 conflicts (the old -44,100 scatter). H0
    // (unassignedLesson, forEachIncludingUnassigned) is the pressure that keeps the
    // box small; the local search places the box as capacity opens. Golden guard is
    // unaffected (the 100%-pinned import gives every lesson a timeslot). EXPERIMENTAL
    // pending the TASK-398 laptop A/B on the FINAL solve (revert if it doesn't reach
    // hard <= baseline -1/0 with box=0).
    @PlanningVariable(valueRangeProviderRefs = {"availableTimeslots"},
                      comparatorFactoryClass = TimeslotStrengthComparatorFactory.class,
                      allowsUnassigned = true)
    @ManyToOne(fetch = FetchType.EAGER)
    public Timeslot timeslot;

    @PlanningVariable(valueRangeProviderRefs = {"availableRooms"}, allowsUnassigned = true)
    @ManyToOne(fetch = FetchType.EAGER)
    public Room room;

    /**
     * TASK-508 (flag-gated callers only): prime this entity's Hibernate dirty-field
     * tracker for BOTH planning variables, single-threaded, BEFORE any solver thread
     * can write them. The build-time bytecode enhancement routes every intra-entity
     * field write through {@code $$_hibernate_trackChange}, whose SimpleFieldTracker
     * mutates a plain String[] with no synchronization: two threads whose first write
     * of the same field lands on the same (cloner-shared) tracker can interleave the
     * array growth and leave a null element, which the next {@code contains()} reads
     * as "existing is null" NPE (run3 treatment, attempt 5, 21:30:23). After priming,
     * the tracker already contains the field names: {@code contains()} short-circuits,
     * {@code add()} never runs again, and the array is effectively immutable for the
     * rest of the solve, eliminating the race under EITHER sharing model (tracker
     * shared by reference across clones, or fresh per clone and single-owner).
     * Self-assignments: no domain semantics change.
     */
    public void primeDirtyTracking() {
        this.timeslot = this.timeslot;
        this.room = this.room;
    }

    /**
     * Cache for filtered timeslots to avoid recomputing on every move. Populated
     * on first read and NEVER invalidated: the clone-time clearing once documented
     * here does not exist (no assignment anywhere but the populate-on-miss), and
     * when the statics are republished (windows, quotas, pins) this cache goes
     * stale. Cold passes that must see the live statics read
     * {@link #getAvailableTimeslotsFresh()} instead (stale-cache crash of the
     * 2026-09-02 cycle).
     */
    @Transient
    private volatile List<Timeslot> cachedAvailableTimeslots;

    /**
     * Cache for filtered rooms to avoid recomputing on every move.
     */
    @Transient
    private volatile List<Room> cachedAvailableRooms;

    /**
     * ValueRangeProvider that filters timeslots for this lesson.
     * Applies filters to eliminate structurally impossible placements:
     * 1. H6.1 — school holidays (lessons can never be on holidays)
     * 2. H5.1 — teacher unavailability (VACATION/SICK periods)
     * 3. H4.1 VRP — stage blackout dates for non-professionalizzanti courses
     * 4. H3 VRP — timeslots with pinned lessons for the same student group
     * 5. H20 VRP (TASK-342) — timeslots closed by the group's calendar
     * 6. L1-bis VRP (TASK-381) — quota-group lessons restricted to their
     *    sheet-derived daily activity budget (DailyActivityQuota)
     * 7. Stage confinement VRP (TASK-384) — unpinned pseudo "Stage" lessons
     *    for a stage-eligible group restricted to that group's stage-day
     *    facts (DailyActivityQuota.stageHours &gt; 0)
     * 8. No-session VRP (TASK-408) — for a sheet-covered group, a school date
     *    with no DailyActivityQuota row at all means the group is not in
     *    session that day (SchoolSchedule.isNoSessionDate); real-teacher
     *    lessons are kept off those dates
     *
     * The old H21 afternoon-slot VRP exclusion (TASK-372) was removed by
     * TASK-379: the golden itself uses 575 afternoon lessons as capacity
     * relief, so afternoons are a soft last resort (M28), not off-limits.
     *
     * For pinned lessons, returns only the assigned timeslot (or empty if null).
     */
    @JsonIgnore
    @ValueRangeProvider(id = "availableTimeslots")
    public List<Timeslot> getAvailableTimeslots() {
        if (cachedAvailableTimeslots != null) {
            return cachedAvailableTimeslots;
        }

        // Not cached: during assertScoreFromScratch, Timefold clones the solution and
        // the variable reference may change. Always return current value to pass validation.
        if (pinned) {
            return timeslot != null ? List.of(timeslot) : List.of();
        }

        cachedAvailableTimeslots = computeAvailableTimeslots();
        return cachedAvailableTimeslots;
    }

    /**
     * H29's exact population: a PLACED (timeslot set), unpinned, non-pseudo-teacher
     * lesson with no room. Shared by the constraint's own filter and the delivery
     * concordance recount (TASK-526 seconda meta') so the two can never drift: an
     * exemption added here reaches both, and the trust metric keeps measuring the
     * same family the score charges.
     */
    public boolean isH29Roomless() {
        return timeslot != null && room == null && !pinned
                && (getTeacher() == null || !getTeacher().pseudo);
    }

    /**
     * TASK-578: single-slot legality, the O(1) companion of
     * {@link #getAvailableTimeslotsFresh()} for guards that only need "may THIS
     * lesson sit at THIS slot" (the contains-shaped call sites). Semantically the
     * membership test of the FRESH range, at ~1 filter-pass instead of the ~220us
     * full-range rebuild (measured): the efficiency review of the Fresh migration
     * priced the unhoisted contains sites at 1-4 extra minutes per staged attempt.
     * Same filter set as {@link #computeAvailableTimeslots()}, plus the same
     * current-slot grandfather (a range always re-adds the lesson's own slot) and
     * the pinned shortcut ({@code [own slot]}). Cold deterministic passes only,
     * like Fresh: the solver hot path keeps the cache.
     */
    public boolean isSlotLegalFresh(Timeslot ts) {
        if (ts == null) return false;
        if (pinned) return ts.equals(timeslot);
        if (ts.equals(timeslot)) return true;   // the grandfather: own slot is always in range
        List<Timeslot> allTimeslots = SchoolSchedule.getAllTimeslots();
        if (allTimeslots == null || allTimeslots.isEmpty() || !allTimeslots.contains(ts)) return false;
        StudentGroup group = getStudentGroup();
        Long teacherId = getTeacher() != null ? getTeacher().id : null;
        // H6.1 holidays
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        if (holidays != null && holidays.contains(ts.getDate())) return false;
        // H5.1 teacher unavailability
        if (isTeacherUnavailable(ts, teacherId, SchoolSchedule.getTeacherAvailabilitiesStatic())) return false;
        boolean pseudoTeacherLesson = getTeacher() != null && getTeacher().pseudo;
        // H4.1 stage blackout (non-professionalizzanti, non-pseudo)
        if (course != null && !course.professionalizzanti && !pseudoTeacherLesson && group != null) {
            Map<Long, Set<LocalDate>> stageBlackout = SchoolSchedule.getStageBlackoutDatesStatic();
            Set<LocalDate> dates = stageBlackout == null ? null : stageBlackout.get(group.id);
            if (dates != null && dates.contains(ts.getDate())) return false;
        }
        // H3 VRP pinned cells of the same group
        if (group != null) {
            Map<Long, Set<Timeslot>> pinnedMap = SchoolSchedule.getPinnedLessonTimeslotsByGroupStatic();
            if (pinnedMap != null) {
                Set<Timeslot> occupied = pinnedMap.get(group.id);
                if (occupied != null && !occupied.isEmpty() && occupied.contains(ts)) return false;
            }
        }
        // H20 group calendar window
        List<GroupCalendarRule> groupCalendarRules = group != null
                ? SchoolSchedule.getGroupCalendarRulesForGroup(group.id) : List.of();
        if (!GroupCalendarService.isOpen(groupCalendarRules, ts)) return false;
        // L1-bis quota-group daily budget
        Map<LocalDate, DailyActivityQuota> dailyQuotasByDate = group != null
                ? SchoolSchedule.getDailyActivityQuotasForGroup(group.id) : Map.of();
        if (!dailyQuotasByDate.isEmpty() && !pseudoTeacherLesson) {
            boolean quotaBoundLabSubject = DailyActivityQuota.isQuotaBoundLabSubject(group.name, getSubject());
            if (!DailyActivityQuota.permits(dailyQuotasByDate.get(ts.getDate()), quotaBoundLabSubject)) return false;
        }
        // stage confinement
        if (group != null && isStageConfined()
                && !DailyActivityQuota.isStageDate(SchoolSchedule.getExpectedHoursForGroup(group.id).get(ts.getDate()))) {
            return false;
        }
        // no-session (sheet-covered group, no quota row that date)
        if (!pseudoTeacherLesson && group != null) {
            Map<LocalDate, DailyActivityQuota> expectedHoursByDate = SchoolSchedule.getExpectedHoursForGroup(group.id);
            if (!expectedHoursByDate.isEmpty() && !expectedHoursByDate.containsKey(ts.getDate())) return false;
        }
        return true;
    }

    /**
     * The range recomputed from the CURRENT SchoolSchedule statics, bypassing the
     * cache. For COLD deterministic passes that must see the live statics: the
     * cached range is never invalidated (volatile field, no resetter), so a pass
     * that runs after the statics were republished (windows, quotas, pins) and
     * trusts the cache can place a lesson in a cell the fresh range forbids; the
     * next solver start recomputes cache-free on its clone and fails validation
     * (cycle 2026-09-02, SurplusHarvest stale-cache crashes). Never call in hot
     * paths: the cache exists for the solver's move evaluation.
     */
    @JsonIgnore
    public List<Timeslot> getAvailableTimeslotsFresh() {
        if (pinned) {
            return timeslot != null ? List.of(timeslot) : List.of();
        }
        return computeAvailableTimeslots();
    }

    private List<Timeslot> computeAvailableTimeslots() {
        List<Timeslot> allTimeslots = SchoolSchedule.getAllTimeslots();
        if (allTimeslots == null || allTimeslots.isEmpty()) {
            return new ArrayList<>();
        }

        // Get filtering data from static references
        StudentGroup group = getStudentGroup();
        Long teacherId = getTeacher() != null ? getTeacher().id : null;
        Map<Long, List<TeacherAvailability>> teacherAvail = SchoolSchedule.getTeacherAvailabilitiesStatic();
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        Map<Long, Set<LocalDate>> stageBlackout = SchoolSchedule.getStageBlackoutDatesStatic();
        List<GroupCalendarRule> groupCalendarRules = group != null
                ? SchoolSchedule.getGroupCalendarRulesForGroup(group.id) : List.of();
        Map<LocalDate, DailyActivityQuota> dailyQuotasByDate = group != null
                ? SchoolSchedule.getDailyActivityQuotasForGroup(group.id) : Map.of();
        boolean quotaGroup = !dailyQuotasByDate.isEmpty();
        boolean pseudoTeacherLesson = getTeacher() != null && getTeacher().pseudo;
        boolean quotaBoundLabSubject = quotaGroup && DailyActivityQuota.isQuotaBoundLabSubject(group.name, getSubject());
        boolean stageConfinedLesson = isStageConfined();
        // Hoisted once for BOTH the stage confinement filter (TASK-384) and the
        // no-session filter (TASK-408); same static map SchoolSchedule.isNoSessionDate
        // reads, so membership can be checked inline in the hot loop.
        Map<LocalDate, DailyActivityQuota> expectedHoursByDate = group != null
                ? SchoolSchedule.getExpectedHoursForGroup(group.id) : Map.of();
        boolean sheetCoveredGroup = !expectedHoursByDate.isEmpty();

        // CRITICAL: Preserve currently assigned timeslot to avoid validation errors
        // If the lesson has a timeslot assigned that would be filtered out, we still
        // need to include it in the value range for Timefold validation to pass.
        // This can happen when data changes (teacher availability, target dates, etc.)
        // after a lesson was assigned.
        Timeslot currentTimeslot = this.timeslot;

        List<Timeslot> range = allTimeslots.stream()
                // The idealEndDate VRP horizon was REMOVED (TASK-378 run-8 forensics):
                // it hard-cut each group's capacity below its demand (G14: 115
                // lessons confined to ~32 legal slots -> 83 forced same-slot stacks;
                // G06 capped at Apr 21 -> 128; the saturated groups at May 27 ->
                // ~35 each - the ENTIRE persistent H3 residual). The golden schedules
                // through June 10; a hard horizon is stricter than school practice
                // (golden-first directive). M12 keeps the early-finish pressure at
                // medium tier, where it belongs.
                // H6.1 - Filter out school holidays
                .filter(ts -> holidays == null || !holidays.contains(ts.getDate()))
                // H5.1 - Filter out timeslots where teacher is unavailable
                .filter(ts -> !isTeacherUnavailable(ts, teacherId, teacherAvail))
                // H4.1 VRP - Filter out stage blackout dates for non-professionalizzanti courses.
                // Pseudo-teacher lessons (Stage/Esame) are exempt, mirroring the H4
                // constraint's own pseudo exclusion (nonProfLessonStagePairs): a Stage
                // lesson trivially belongs INSIDE its own group's stage window - without
                // this exemption an unpinned Stage stub (seeded since TASK-381 "stage on
                // the grid") could never be placed in its own stage period at all.
                .filter(ts -> {
                    if (course == null || course.professionalizzanti || pseudoTeacherLesson) return true;
                    if (group == null || stageBlackout == null) return true;
                    Set<LocalDate> dates = stageBlackout.get(group.id);
                    return dates == null || !dates.contains(ts.getDate());
                })
                // H3 VRP - Filter out timeslots that have pinned lessons for the same student group
                // Uses pre-computed map for O(1) lookup instead of iterating all lessons
                .filter(ts -> {
                    if (group == null) return true;
                    Map<Long, Set<Timeslot>> pinnedMap = SchoolSchedule.getPinnedLessonTimeslotsByGroupStatic();
                    if (pinnedMap == null) return true;
                    Set<Timeslot> occupiedSlots = pinnedMap.get(group.id);
                    // If this group has any pinned lessons, exclude those timeslots
                    if (occupiedSlots != null && !occupiedSlots.isEmpty()) {
                        return !occupiedSlots.contains(ts);
                    }
                    return true; // No pinned lessons for this group
                })
                // H20 VRP (TASK-342) - Filter out timeslots closed by the group's calendar
                // (e.g. first-year groups have no afternoons, G14 only runs Thu+Fri).
                // Rules hoisted once above (like teacherAvail/holidays/stageBlackout)
                // instead of re-resolving per candidate timeslot.
                .filter(ts -> GroupCalendarService.isOpen(groupCalendarRules, ts))
                // L1-bis VRP (TASK-381) - restrict quota-group (G04/G08/G13)
                // lessons to the sheet-derived daily activity budget
                // (calendario_fc.csv / DailyActivityQuota, now an authoritative
                // search-space restriction, not just a report-only cross-check).
                // The lab-vs-aula threshold decision lives in ONE method,
                // DailyActivityQuota.permits (called positively here, negated by
                // H23/H24) - see its javadoc. Pseudo-teacher lessons
                // (Stage/Esame) are exempt - stage placement is driven separately
                // by CalendarioImportService's quota-based stage pinning, not this
                // generic subject filter. Non-quota groups (every group except the
                // three sheet-covered ones) are unrestricted (quotaGroup false).
                .filter(ts -> !quotaGroup || pseudoTeacherLesson
                        || DailyActivityQuota.permits(dailyQuotasByDate.get(ts.getDate()), quotaBoundLabSubject))
                // Stage confinement VRP (TASK-384) - an unpinned pseudo "Stage"
                // lesson for a stage-eligible group (see isStageConfined()) may
                // only land on a date that group's stage-day fact actually marks
                // as a stage day (stageHours > 0). No-op for every other lesson
                // (real-teacher lessons, non-Stage pseudo lessons like Esame, and
                // groups with no stage-day fact at all).
                .filter(ts -> !stageConfinedLesson
                        || DailyActivityQuota.isStageDate(expectedHoursByDate.get(ts.getDate())))
                // No-session VRP (TASK-408) - for a sheet-covered group (has
                // expected-hours facts, all 14 groups in 2026/27), a school date
                // with NO DailyActivityQuota row means the group is not in session
                // that day (golden places 0 hours on every such date - see
                // SchoolSchedule.isNoSessionDate). Exclude those dates so M20 can't
                // reward filling a day the golden leaves empty. Pseudo-teacher
                // lessons (Stage/Esame) are exempt, mirroring the H4.1/L1-bis
                // filters above: Stage placement is driven separately by stage-day
                // facts, and a no-session date has no fact so it can never be a
                // stage date anyway. Membership checked inline against the hoisted
                // map (single source of truth with isNoSessionDate).
                .filter(ts -> pseudoTeacherLesson || !sheetCoveredGroup
                        || expectedHoursByDate.containsKey(ts.getDate()))
                // TASK-232: Sort chronologically for weaker-values-first principle
                .sorted(Comparator.comparing(Timeslot::getDate)
                        .thenComparing(ts -> TimeslotSequence.slotIndexOf(ts)))
                .toList();

        // CRITICAL: If current timeslot was filtered out, add it back to the value range
        // This ensures Timefold validation passes and allows analysis of stale assignments
        if (currentTimeslot != null && !range.contains(currentTimeslot)) {
            range = new ArrayList<>(range);
            range.add(currentTimeslot);
            // Re-sort to maintain chronological order
            range.sort(Comparator.comparing(Timeslot::getDate)
                    .thenComparing(ts -> TimeslotSequence.slotIndexOf(ts)));
        }

        return range;
    }

    /**
     * Check if a teacher is unavailable (VACATION/SICK) during a timeslot.
     * Public static (TASK-381 "L2" simplify pass): doesn't read any Lesson
     * instance state, and {@code DayClusteredConstructionPhase} needs the
     * exact same overlap check for its own majority-pattern scan - widened
     * instead of re-declaring a second copy in a different package.
     */
    public static boolean isTeacherUnavailable(Timeslot ts, Long teacherId,
                                         Map<Long, List<TeacherAvailability>> teacherAvail) {
        if (teacherId == null || teacherAvail == null) return false;
        List<TeacherAvailability> availList = teacherAvail.get(teacherId);
        if (availList == null) return false;

        for (TeacherAvailability ta : availList) {
            // Only VACATION and SICK block scheduling; PREFERENCE is informational
            if (ta.type == AvailabilityType.VACATION || ta.type == AvailabilityType.SICK) {
                // Check if timeslot overlaps with unavailability period
                if (ts.startDateTime.isBefore(ta.endDateTime) && ts.endDateTime.isAfter(ta.startDateTime)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ValueRangeProvider that filters rooms based on the course's required room tags.
     * - Lab lessons: only rooms with ALL required tags (same logic as LabBlock)
     * - Non-lab lessons: exclude lab-only rooms (rooms with tags)
     * - Pinned lessons: only the assigned room (prevents solver from moving them)
     */
    @JsonIgnore
    @ValueRangeProvider(id = "availableRooms")
    public List<Room> getAvailableRooms() {
        if (cachedAvailableRooms != null) {
            return cachedAvailableRooms;
        }

        // Not cached: same reason as timeslot VRP above.
        // Pinned lessons are grandfathered as-is (a pin is a human-fixed room the solver must not
        // move), INCLUDING a room with capacity <= 0: nulling a pinned lesson's room would freeze
        // it room-null for the whole solve (pinned entities are immovable), an H29 hard violation
        // with no recovery. The capacity filter below therefore applies to UNpinned lessons only.
        if (pinned) {
            return room != null ? List.of(room) : List.of();
        }

        List<Room> allRooms = SchoolSchedule.getAllRooms();
        if (allRooms == null || allRooms.isEmpty()) {
            cachedAvailableRooms = new ArrayList<>();
            return cachedAvailableRooms;
        }

        // TASK-456: a room with capacity <= 0 is not assignable (it has no seats) and disappears
        // from the value domain. GENERAL rule on capacity (Room.isAssignable), never a room name:
        // today the only such room is R18 (cap 0; R17 was cap-0 by error and is
        // already 22), so the rule and the school's request coincide 100%. Excluding it here (one
        // filter shared by both branches) means the solver never places a NEW lesson there; M3 (room
        // capacity) stays as the softer nudge and still scores any grandfathered pinned lesson in
        // such a room.
        List<Room> assignable = allRooms.stream().filter(Room::isAssignable).toList();

        if (course != null && course.requiredRoomTags != null && !course.requiredRoomTags.isEmpty()) {
            // Lab lesson: only rooms with ALL required tags
            cachedAvailableRooms = assignable.stream()
                    .filter(r -> r.tags != null && r.tags.containsAll(course.requiredRoomTags))
                    // TASK-232: Sort by capacity ascending for weaker-values-first (try smaller rooms first)
                    .sorted(Comparator.comparing(r -> r.capacity))
                    .toList();
        } else {
            // Non-lab lesson: by default exclude lab-only rooms (rooms with tags).
            // TASK-456 lab-fallback (domanda 21, "i laboratori possono ospitare anche lezioni
            // normali, dando priorita' alle attivita' di lab vere"): with the flag ON (the ratified
            // default) a non-lab ALSO sees tagged rooms as candidates, so the solver can shelter an
            // ordinary lesson in a free lab when the aulas are full (10 ordinary aulas vs 11-13
            // simultaneous classes off-lab) and, together with the cap-0 exclusion above, absorb the
            // overflow that used to land in R18. The flag is read LIVE here (not
            // class-init) so an in-harness System.setProperty before solve takes effect at this
            // cache-build; set -Dplanner.room.lab-fallback.enabled=false to opt out. Genuine lab
            // sessions keep priority via H-M2 (hard) and the M-LF medium discouragement, so a lab is
            // a deliberate last-resort shelter, not a convenience.
            boolean labFallback = SchoolSchedule.isLabFallbackEnabled();
            cachedAvailableRooms = assignable.stream()
                    .filter(r -> labFallback || !r.isTagged())
                    // TASK-232: Sort by capacity ascending for weaker-values-first (try smaller rooms first)
                    .sorted(Comparator.comparing(r -> r.capacity))
                    .toList();
        }
        return cachedAvailableRooms;
    }

    public Lesson() {
    }

    public Lesson(Course course) {
        this.course = course;
    }

    public Teacher getTeacher() {
        return course != null ? course.teacher : null;
    }

    public StudentGroup getStudentGroup() {
        return course != null ? course.studentGroup : null;
    }

    public String getSubject() {
        return course != null ? course.subject : null;
    }

    public boolean isLab() {
        return course != null && course.isLab;
    }

    /**
     * TASK-384: true if this is a pseudo-teacher "Stage" lesson for a group
     * that has at least one persisted stage-day fact ({@link
     * DailyActivityQuota#stageHours} &gt; 0 - {@link
     * SchoolSchedule#groupHasStageFacts}). Single source of truth for "does
     * this lesson's placement need to stay inside its group's stage days",
     * shared by this class's own VRP confinement filter above and its H-tier
     * safety net (H25 in {@code BaseConstraintProvider}) - same
     * anti-drift rationale as {@link DailyActivityQuota#isStageDate}. Subject
     * match goes through {@link SubjectNormalization#normalize} (same
     * precedent as M21/H30's own subject checks) so seed-data casing/whitespace
     * variance can't silently exempt a Stage lesson from confinement.
     */
    public boolean isStageConfined() {
        Teacher teacher = getTeacher();
        StudentGroup group = getStudentGroup();
        String subject = getSubject();
        return teacher != null && teacher.pseudo
                && subject != null && GoldenScheduleImportService.STAGE_NAME.equals(SubjectNormalization.normalize(subject))
                && group != null && SchoolSchedule.groupHasStageFacts(group.id);
    }

    public int getLabBlockSize() {
        return course != null ? course.labBlockSize : 0;
    }

    /**
     * Load all lessons with timeslot relationship eagerly fetched.
     * This ensures timeslot objects are properly initialized for constraint analysis.
     * Without JOIN FETCH, accessing lesson.timeslot.getDate() in constraints
     * may fail because the lazy proxy isn't initialized.
     */
    public static List<Lesson> listAllWithTimeslot() {
        return getEntityManager().createQuery(
                "SELECT l FROM Lesson l " +
                "LEFT JOIN FETCH l.timeslot " +
                "LEFT JOIN FETCH l.course c " +
                "LEFT JOIN FETCH c.studentGroup " +
                "LEFT JOIN FETCH l.room " +
                "LEFT JOIN FETCH c.teacher",
                Lesson.class)
                .getResultList();
    }

    @Override
    public String toString() {
        return (course != null ? course.subject : "?") + "(" + id + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lesson other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? getClass().hashCode() : id.hashCode();
    }
}
