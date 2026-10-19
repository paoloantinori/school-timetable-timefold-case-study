package com.school.planner.solver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.phase.PhaseCommand;
import ai.timefold.solver.core.api.solver.phase.PhaseCommandContext;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.service.PhaseProgressionService;
import io.quarkus.logging.Log;

import jakarta.enterprise.inject.spi.CDI;

/**
 * Event-based phase transition marker using Timefold's PhaseCommand API.
 *
 * This class serves as a "marker phase" that:
 * 1. Records the completion of the previous phase (when this phase starts)
 * 2. Immediately terminates (returns true from isPhaseTerminated)
 *
 * By inserting these markers between real solver phases in solverConfig.xml,
 * we get precise event-based phase detection instead of time-based heuristics.
 *
 * Usage in solverConfig.xml:
 * <pre>
 *   <constructionHeuristic>...</constructionHeuristic>  <!-- Phase 1 -->
 *   <customPhase>
 *     <customPhaseCommandClass>com.school.planner.solver.PhaseTransitionMarker</customPhaseCommandClass>
 *     <phaseNumber>2</phaseNumber>
 *     <phaseName>2. CH - Lesson Placement</phaseName>
 *   </customPhase>
 *   <constructionHeuristic>...</constructionHeuristic>  <!-- Phase 2 -->
 *   <customPhase>
 *     <customPhaseCommandClass>com.school.planner.solver.PhaseTransitionMarker</customPhaseCommandClass>
 *     <phaseNumber>3</phaseNumber>
 *     <phaseName>3. LS - Compactness</phaseName>
 *   </customPhase>
 *   ...
 * </pre>
 *
 * The phaseNumber represents the phase that is ABOUT TO START (the next phase),
 * not the phase that just ended. This aligns with PhaseProgressionService.onPhaseTransition()
 * semantics which record the start of a new phase.
 */
public class PhaseTransitionMarker<Solution_> implements PhaseCommand<Solution_> {

    /**
     * The phase number that will start after this marker completes.
     * Configured via XML property injection.
     */
    private int phaseNumber;

    /**
     * Human-readable phase name.
     * Configured via XML property injection.
     */
    private String phaseName;

    /**
     * Required no-arg constructor for Timefold's PhaseCommand instantiation.
     * Properties (phaseNumber, phaseName) are set via reflection by Timefold.
     */
    public PhaseTransitionMarker() {
    }

    /**
     * Set the phase number (called by Timefold via XML configuration).
     */
    public void setPhaseNumber(int phaseNumber) {
        this.phaseNumber = phaseNumber;
    }

    /**
     * Set the phase name (called by Timefold via XML configuration).
     */
    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
    }

    /**
     * Called by Timefold when this custom phase starts.
     * Records the phase transition and immediately signals termination.
     *
     * @param context The phase command context for accessing the solution
     */
    @Override
    public void changeWorkingSolution(PhaseCommandContext<Solution_> context) {
        Log.infof("PhaseTransitionMarker: Transitioning to phase %d (%s)",
                  phaseNumber, phaseName);

        // Get current score from the solution
        BendableScore currentScore = null;
        try {
            @SuppressWarnings("unchecked")
            var solution = (com.school.planner.domain.SchoolSchedule) context.getWorkingSolution();
            currentScore = solution.getScore();
        } catch (Exception e) {
            Log.warnf("PhaseTransitionMarker: Could not extract score: %s", e.getMessage());
            currentScore = ScoreCompat.ZERO;
        }

        // Record phase transition in PhaseProgressionService
        try {
            PhaseProgressionService progressionService = CDI.current().select(PhaseProgressionService.class).get();
            progressionService.onPhaseTransition(
                phaseNumber,
                phaseName,
                currentScore,
                Instant.now()
            );

            // Also update PhaseTrackingListener for UI display
            PhaseTrackingListener.setPhase(phaseNumber, phaseName);
        } catch (Exception e) {
            Log.errorf(e, "PhaseTransitionMarker: Failed to record phase transition for phase %d", phaseNumber);
        }

        // Immediately terminate this marker phase
        // The isPhaseTerminated check signals to Timefold that this phase is complete
        context.isPhaseTerminated();

        // TASK-415: optional lightweight per-transition dump, OFF by default. Writes the
        // working solution (the END state of the phase that just completed) in the same
        // group|date|hour|subject|teacher|pinned pipe format capture-run.sh / the KPI scripts
        // use, so resemblance-kpi.py can measure recurrence erosion at every phase boundary
        // (not just construction-vs-final). Cost is ~12k placed rows per transition, written
        // once per boundary — negligible, and zero when the flag is off (the default).
        if (transitionDumpEnabled()) {
            try {
                @SuppressWarnings("unchecked")
                SchoolSchedule solution = (SchoolSchedule) context.getWorkingSolution();
                writeTransitionDump(solution);
                writeBoxedTransitionDump(solution);
                writeTransitionDumpWithIds(solution);
            } catch (Exception e) {
                // Never let a measurement dump break the solve.
                Log.warnf("PhaseTransitionMarker: transition dump failed for phase %d: %s",
                        phaseNumber, e.getMessage());
            }
        }
    }

    /** True only when {@code planner.phase-transition-dump.enabled=true} is set (system
     *  property), default OFF. A measurement opt-in; never affects a production solve. */
    private static boolean transitionDumpEnabled() {
        return Boolean.getBoolean("planner.phase-transition-dump.enabled");
    }

    /** Dir for the transition dumps (default {@code target/phase-transitions}); created if missing. */
    private static Path transitionDumpDir() {
        String dir = System.getProperty("planner.phase-transition-dump.dir", "target/phase-transitions");
        return Path.of(dir);
    }

    /** Write the placed lessons of {@code solution} as a {@code phase-<n>-<slug>.txt} pipe dump
     *  into {@link #transitionDumpDir()}, sorted by (group, date, hour) for determinism.
     *  Unassigned (boxed) lessons have no timeslot/room and are omitted, matching capture-run.sh's
     *  SQL dump (which JOINs timeslot). The "hour" column is the wall-clock start hour (8-15),
     *  NOT the 0-7 slotIndex — same convention as the committed lessons.txt artifacts. */
    private void writeTransitionDump(SchoolSchedule solution) {
        writeLessons(solution, "", PLACED_FILTER, PLACED_SORT, PhaseTransitionMarker::placedRow, "placed");
    }

    /** TASK-419: sibling of {@link #writeTransitionDump}, written alongside it under the same
     *  flag: the BOXED (unassigned) lessons at this phase boundary, in {@code
     *  group|teacher|subject} format (the same format {@code SnapshotFixture#parseBoxedRows}
     *  already consumes for {@code boxed.txt}). TASK-403's E3 checkpoint found the earlier
     *  placed-only dump made the evidence inconclusive: the pipe-format snapshot fed into
     *  {@code Task403EarlyRepairCheckpointTest} carried zero box, so the BoxRepair phase being
     *  measured had nothing to repair, precisely the mechanism the real phase-4 repair spends
     *  most of its hard-score improvement on. Boxed lessons are, by construction, those with a
     *  null timeslot; {@code isLabHour}/room are irrelevant to the box concept, so only
     *  group/teacher/subject are written. Pinned lessons are never boxed (pinned implies
     *  placed in this codebase), but the filter still excludes them defensively, matching
     *  every other box-counting site (e.g. {@code Task403EarlyRepairCheckpointTest}'s own
     *  {@code !l.pinned && l.timeslot == null}). Absent or empty file means zero box at this
     *  boundary, the same "optional file" contract {@code boxed.txt} already has for
     *  {@link com.school.planner.bootstrap.SnapshotFixture}. */
    private void writeBoxedTransitionDump(SchoolSchedule solution) {
        writeLessons(solution, "-boxed", BOXED_FILTER, BOXED_SORT, PhaseTransitionMarker::boxedRow, "boxed");
    }

    /** Filename slug shared by {@link #writeTransitionDump} and {@link #writeBoxedTransitionDump}
     *  so the two files always pair up ({@code phase-N-slug.txt} / {@code phase-N-slug-boxed.txt}). */
    private String transitionDumpSlug() {
        return phaseName == null ? String.valueOf(phaseNumber)
                : phaseName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /** Sibling of {@link #writeTransitionDump} written under the same flag, but carrying the
     *  lesson {@code id} so two phase dumps from the SAME solve can be matched lesson-by-lesson
     *  (not by slot/position). This is the identity hook the recurrence-erosion tool needs to
     *  separate pure LS erosion from the composition shift that confounds aggregate
     *  resemblance-% comparisons (SPIKE-7 caveat: ~3090 lessons get pinned between construction
     *  and final, so the unpinned population is not the same set at the two cuts).
     *  <p>Format: {@code id|group|date|hour|subject|teacher|pinned|promotedPin} (same placed-only
     *  filter and (group, date, hour) sort as the 6-field dump, so the two pair up row-for-row;
     *  {@code promotedPin} distinguishes template-promoted pins from authoritative ones for the
     *  mobile-set filter). Lesson identity does NOT survive across separate solves (the DB id is
     *  reassigned), so this is only meaningful for two dumps from one flagged solve - which is
     *  exactly what this marker produces at every phase boundary. */
    private void writeTransitionDumpWithIds(SchoolSchedule solution) {
        writeLessons(solution, "-id", PLACED_FILTER, PLACED_SORT, PhaseTransitionMarker::idRow, "id-tagged");
    }

    // The one dump writer the three variants above delegate to.

    /** Population selectors for {@link #writeLessons}: placed (timeslot set, real slot) vs boxed
     *  (null timeslot, not pinned). The placed and id-tagged dumps share {@link #PLACED_FILTER}
     *  so their rows pair up 1:1 by construction; the boxed dump is a different population. */
    private static final Predicate<Lesson> PLACED_FILTER =
            l -> l.timeslot != null && l.getStudentGroup() != null;
    private static final Predicate<Lesson> BOXED_FILTER =
            l -> l.timeslot == null && !l.pinned && l.getStudentGroup() != null;

    /** Sort orders: placed/id by (group, date, hour); boxed by (group, teacher, subject). */
    private static final Comparator<Lesson> PLACED_SORT = Comparator
            .comparing((Lesson l) -> l.getStudentGroup().name)
            .thenComparing(l -> l.timeslot.getDate())
            .thenComparingInt(l -> l.timeslot.startDateTime.getHour());
    private static final Comparator<Lesson> BOXED_SORT = Comparator
            .comparing((Lesson l) -> l.getStudentGroup().name)
            .thenComparing(l -> l.getTeacher() != null ? l.getTeacher().name : "")
            .thenComparing(Lesson::getSubject);

    /** Select {@code population} from the solution, sort it, and write one {@code SET} line plus
     *  one {@code rowFn}-formatted line per lesson to {@code phase-<n>-<slug><suffix>.txt}. The
     *  three dump variants above differ only in (suffix, population, sort, rowFn); this is the
     *  single create-dirs/write/log/catch skeleton they share, so a filter or sort change can no
     *  longer drift between siblings. Never throws: a dump failure only logs, matching the
     *  measurement-only contract of the surrounding flag. */
    private void writeLessons(SchoolSchedule solution, String suffix, Predicate<Lesson> population,
                              Comparator<Lesson> sort, Function<Lesson, String> rowFn, String label) {
        List<Lesson> selected = new ArrayList<>();
        for (Lesson lesson : solution.getLessons()) {
            if (population.test(lesson)) selected.add(lesson);
        }
        selected.sort(sort);
        Path file = transitionDumpPath(suffix);
        try {
            Files.createDirectories(transitionDumpDir());
            List<String> lines = new ArrayList<>(selected.size() + 1);
            lines.add("SET");
            for (Lesson l : selected) lines.add(rowFn.apply(l));
            Files.write(file, lines);
            Log.infof("PhaseTransitionMarker: wrote %s transition dump for phase %d (%s): %d lessons -> %s",
                    label, phaseNumber, phaseName, selected.size(), file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + label + " transition dump to " + file, e);
        }
    }

    private static String placedRow(Lesson l) {
        return groupDateHourPrefix(l) + l.getSubject() + "|" + teacher(l) + "|" + l.pinned;
    }

    private static String idRow(Lesson l) {
        return l.id + "|" + groupDateHourPrefix(l) + l.getSubject() + "|" + teacher(l)
                + "|" + l.pinned + "|" + l.promotedPin;
    }

    private static String boxedRow(Lesson l) {
        return l.getStudentGroup().name + "|" + teacher(l) + "|" + l.getSubject();
    }

    /** Shared {@code group|date|hour} prefix of the placed and id row formats. */
    private static String groupDateHourPrefix(Lesson l) {
        return l.getStudentGroup().name + "|" + l.timeslot.getDate() + "|"
                + l.timeslot.startDateTime.getHour() + "|";
    }

    private static String teacher(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().name : "";
    }

    private Path transitionDumpPath(String suffix) {
        return transitionDumpDir().resolve(
                "phase-" + phaseNumber + "-" + transitionDumpSlug() + suffix + ".txt");
    }

    /**
     * Getter for phaseNumber (useful for debugging/logging).
     */
    public int getPhaseNumber() {
        return phaseNumber;
    }

    /**
     * Getter for phaseName (useful for debugging/logging).
     */
    public String getPhaseName() {
        return phaseName;
    }
}
