package com.school.planner.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.BendableScore;

import com.school.planner.solver.ScoreCompat;
import ai.timefold.solver.core.api.score.stream.ConstraintRef;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchTotal;

import com.school.planner.domain.AvailabilityType;
import com.school.planner.domain.Course;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.rest.dto.LessonViolationAnalysis;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.GoldenImportReport;
import com.school.planner.service.GoldenScheduleImportService;
import com.school.planner.service.ScheduleService;
import com.school.planner.service.SubjectRegistry;
import com.school.planner.solver.SchoolScheduleConstraintProvider;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.ConstraintIds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * TASK-355: golden-solution regression suite — the GATE for the m-8/m-9
 * constraint-recalibration work. Seeds the FULL real 2026/27 domain graph,
 * runs the REAL {@link GoldenScheduleImportService} over the real classpath
 * {@code lessons.csv}, and scores the result via the REAL {@link
 * ScheduleService#analyzeScore()} (no solving) — automating the exact
 * methodology of {@code claudedocs/spalmone-2026-27/golden-audit.md}
 * (2026-07-17, TASK-347).
 *
 * <h2>Why this doesn't call {@code DataGenerator.onStartup()}</h2>
 * Three existing tests ({@link DataGeneratorSeedTest}, {@link
 * DataGeneratorCalendarTest}, {@code GoldenScheduleImportDryRunTest})
 * deliberately avoid running {@code DataGenerator.onStartup()} inside a
 * {@code @QuarkusTest}: {@link DataGeneratorSeedTest}'s javadoc records that
 * an earlier attempt used a {@code @TestProfile} to flip {@code
 * planner.datagenerator.enabled=true}, and the resulting Quarkus
 * app-restart-on-profile-switch leaked {@code GroupCalendarRule}/{@code
 * StudentGroup} rows across the shared H2 instance ({@code
 * jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1}) into ~40 unrelated test classes.
 * {@code onStartup()}'s own {@code generate*()} seeding methods are all
 * {@code private} (not even package-private) and its {@code enabled} field
 * is populated from test config, so neither can be exercised directly
 * without reflection into CDI internals — a fragile trade the project has
 * already rejected once.
 *
 * <p>Instead, this test mirrors only the seeding actually needed for the
 * constraints under test, reusing every already package-visible piece of
 * {@link DataGenerator} verbatim ({@link DataGenerator#loadSeedCsv},
 * {@link DataGenerator#aggregateCourseHours}, {@link
 * DataGenerator#computeSchoolDays}, {@code STANDARD_SLOTS}, {@code
 * CLOSURE_DATES_2026_27}, {@code GROUP_CALENDAR_PATTERNS}/{@code
 * GROUP_CALENDAR_WEEKDAYS}, {@link DataGenerator#parseCalendarCell}) and
 * hand-transcribing the handful of genuinely {@code private} pieces that
 * have no accessible equivalent ({@code isProfessionalizzante}/{@code
 * isLabSubject}/{@code getRoomTags}, {@code MAX_HOURS_PER_DAY}, {@code
 * NO_PROFESSIONALIZZANTI_DURING_STAGE}, the 3 hardcoded {@link StagePattern}
 * entries). Each transcription is marked "mirrors DataGenerator.X, kept in
 * lockstep by hand" — the same convention {@code scripts/analyze/shared.py}
 * already uses for its own cross-file mirrors of these exact constants.</p>
 *
 * <p>Deliberately NOT seeded (irrelevant to every assertion below):
 * {@code Room}, {@code LabAssistant}, {@code LabRotation}, {@code
 * ProfessionalizzantiDay}. The golden import never sets {@code
 * Lesson.room} (see {@link GoldenScheduleImportService}'s class javadoc),
 * so H2/H-M2/M3/M-LA are "structurally untestable" against this data
 * regardless of whether {@code Room} rows exist (golden-audit.md's own
 * conclusion); {@code LabRotation}/{@code ProfessionalizzantiDay} are
 * seeded with nothing in production either (see {@link
 * DataGenerator#generateLabRotations}/{@link
 * DataGenerator#generateProfessionalizzantiDays} javadoc).
 * {@code LessonGenerator}'s unassigned-lesson pool is also skipped: the
 * golden import deletes every existing Lesson before creating its own, so
 * any pre-seeded unassigned Lessons would just be discarded.</p>
 *
 * <h2>Measured runtime</h2>
 * <p>6-15s for this single test method across several measured runs (seed +
 * golden import + analyzeScore over the full 12,979-row CSV / 12,520 pinned
 * Lessons), 15-31s including this class's own JVM/Quarkus/Hibernate startup
 * when run in isolation — well under the ~90s fallback threshold, so no
 * sampled/subset fixture was needed (see the task brief's fallback clause).
 * When run inside the full suite the JVM/Quarkus startup is shared with
 * every other test, so the marginal cost is close to the 6-12s test-method
 * figure.</p>
 */
@QuarkusTest
class GoldenSolutionRegressionTest {

    @Inject
    EntityManager em;

    @Inject
    GoldenScheduleImportService importService;

    @Inject
    ScheduleService scheduleService;

    @Inject
    AvailabilityExpansionService availabilityExpansionService;

    @Inject
    DataGenerator dataGenerator;

    @BeforeEach
    @Transactional
    void cleanBefore() {
        clean();
    }

    @AfterEach
    @Transactional
    void cleanAfter() {
        // Mirrors ReconciliationResourceTest/GoldenScheduleImportServiceTest's
        // "clean both before and after" convention: this fixture is by far
        // the largest in the suite (full 2026/27 seed), so leaving anything
        // behind would be the worst possible offender for the documented
        // shared-H2 leak class.
        System.clearProperty("planner.constraints.empty-day.enabled"); // TASK-557 arm
        clean();
    }

    private void clean() {
        em.createQuery("DELETE FROM Lesson").executeUpdate();
        em.createQuery("DELETE FROM TeacherAvailability").executeUpdate();
        em.createQuery("DELETE FROM RecurringAvailabilityRule").executeUpdate();
        em.createQuery("DELETE FROM LabAssistant").executeUpdate();
        em.createQuery("DELETE FROM LabRotation").executeUpdate();
        em.createQuery("DELETE FROM DailyActivityQuota").executeUpdate();
        em.createQuery("DELETE FROM GroupCalendarRule").executeUpdate();
        em.createQuery("DELETE FROM ProfessionalizzantiDay").executeUpdate();
        em.createQuery("DELETE FROM StagePattern").executeUpdate();
        em.createQuery("DELETE FROM SchoolHoliday").executeUpdate();
        em.createQuery("DELETE FROM Course").executeUpdate();
        em.createQuery("DELETE FROM Teacher").executeUpdate();
        em.createQuery("DELETE FROM StudentGroup").executeUpdate();
        em.createQuery("DELETE FROM Timeslot").executeUpdate();
    }

    @Test
    void goldenSolutionMatchesCalibratedConstraintExpectations() {
        seedTimeslots();
        seedStudentGroups();
        seedTeachers();
        seedCourses();
        seedSchoolHolidays();
        seedStagePatterns();
        seedGroupCalendarRules();
        seedDailyActivityQuotas();
        seedAvailabilityRules();
        availabilityExpansionService.expandAll();

        GoldenImportReport report = importService.importGolden(true);
        assertImportReport(report);

        // TASK-419 precondition: every pin in this fixture is AUTHORITATIVE (the
        // golden import pins 100% of its lessons), so the promoted-pin registry must
        // be empty and every pinned exemption asserted below keeps its pre-TASK-419
        // meaning. A non-empty registry here would silently strip those exemptions
        // and turn this whole guard's calibrated numbers into a different measurement.
        assertTrue(SchoolSchedule.getPromotedLessonIdsStatic().isEmpty(),
                "the golden fixture must have no TASK-419 promoted pins - every pin here is authoritative");

        // TASK-557 (26/08): H34 is opt-in in development - arm it around this
        // snapshot (cleared in cleanAfter) so the guard measures the golden
        // under the empty-declared-day companion too.
        System.setProperty("planner.constraints.empty-day.enabled", "true");
        ScheduleService.ConstraintMatchSnapshot snapshot = scheduleService.analyzeScore();

        // ─── Zero-tolerance (any match is a real regression) ───────────────
        // Cure F1 (28/08) explicit pin: the G09 pool (666h, golden-practiced) is
        // UNCHANGED by dropping the 04/06 declaration - the golden itself places every
        // pool lesson (4h as in-grid surplus above the now-662 sheet), so no lesson is
        // boxed and none is parked as expected-unscheduled.
        assertZeroHard(snapshot, "H0");
        assertZeroHard(snapshot, "H1");
        assertZeroHard(snapshot, "H3");
        assertZeroHard(snapshot, "H6");
        assertZeroHard(snapshot, "H20");
        assertZeroHard(snapshot, "H11");
        // M20 (daily quota fill) is structurally exempt for 100%-pinned data
        // (SchoolScheduleConstraintProvider filters !lesson.pinned) - golden-audit.md's
        // own root-cause conclusion, not a coincidence of this fixture. Still
        // pinned-exempt after TASK-385 retargeted M20 to expected-hours (the
        // 100%-pinned golden import measures 0 regardless of the target
        // source) - explicit pin added with that retarget so golden-neutrality
        // is test-guarded, not just argued.
        assertScore(snapshot, "M20", ScoreCompat::mediumOf, v -> v == 0, "medium score 0 (pinned-exempt, vacuous on the 100%-pinned import)");
        assertMatchCount(snapshot, "M20", 0);
        // M28 (afternoon slots reserved for stage; was hard H21 until TASK-379's
        // golden-first demotion - the golden itself uses 575 afternoon lessons as
        // capacity relief) is exempt here for the same reason as M20 above:
        // golden import pins every Lesson, and the constraint filters
        // !lesson.pinned. A non-zero value here would mean an unpinned
        // (solver-placed) lesson was scored - a fixture regression.
        assertScore(snapshot, "M28", ScoreCompat::mediumOf, v -> v == 0, "medium score 0");
        // M15 (same-day same-subject should be consecutive; was H15 until TASK-392
        // demoted it hard->medium) is exempt here for a related but distinct reason
        // from M20/M28 above: it's a BREAK-level exemption (both the previous and next
        // lesson of a break must be pinned), not a per-entity !lesson.pinned filter -
        // see the constraint's own javadoc. Since golden import pins every Lesson, every
        // break in this fixture has both sides pinned, so the golden's own 98 organic
        // splits (golden-audit.md - the very evidence that H15-hard was stricter than
        // the school's practice, TASK-392) score 0 here. A non-zero value would mean an
        // unpinned (solver-placed) lesson is involved in a split - a genuine regression,
        // exactly the case the break-level (not per-entity) exemption is designed to
        // still catch. Asserted on the MEDIUM tier now (mirrors M28 above). See CLAUDE.md
        // "H15->M15 demotion (TASK-392)".
        assertScore(snapshot, "M15", ScoreCompat::mediumOf, v -> v == 0, "medium score 0");
        assertMatchCount(snapshot, "M15", 0);
        // S16 (consecutive same-subject same teacher; was H16a) - TASK-347's "0 matches
        // on golden" was VACUOUS: as H16a it sat on forEachUniquePair (forEach
        // semantics: fully initialized entities only) and this fixture assigns no
        // rooms, so the constraint never evaluated a single pair. TASK-377 unblinded
        // it, revealing 15 deliberate golden violations (RA/Recuperi hours handed
        // between teachers back-to-back, e.g. T16->T02 on G01
        // 2027-05-21; Scienze split T09->T25 on G12 2026-11-11) and demoted
        // it to soft: with H17 forcing contiguity, a hard tier is structurally
        // unsatisfiable for multi-teacher RA days. The 15 are pinned exactly - a drift
        // means the golden data or the constraint changed.
        assertScore(snapshot, "S16", ScoreCompat::softOf, v -> v == -15,
                "soft score -15 (15 golden teacher-handoff pairs, TASK-377)");
        assertMatchCount(snapshot, "S16", 15);
        // M37 (consecutive same-subject same-teacher should keep the room; was H16b hard,
        // demoted TASK-446/447 notte) is MEDIUM now. This room-less fixture assigns no
        // rooms, so M37 (filter room!=null on both) never evaluates a pair: 0 matches,
        // medium score 0. assertMatchCount 0 is the meaningful guard here (mediumScore 0 is
        // trivial for a medium constraint); a non-zero count would mean M37 changed its null
        // handling or the fixture grew rooms.
        assertScore(snapshot, "M37", ScoreCompat::mediumOf, v -> v == 0, "medium score 0 (room-less fixture)");
        assertMatchCount(snapshot, "M37", 0);
        // M-LF (non-lab lesson in a tagged room, TASK-456) is DEFAULT ON since ratification. The
        // golden has ZERO non-lab-in-tagged-room placements (the school never parks an ordinary
        // lesson in a workshop), so M-LF contributes 0 here: pinning 0 guards both that invariant
        // AND that the ratified default-ON activation does not silently drift the golden's score.
        assertMatchCount(snapshot, "M-LF", 0);
        // H28 (no teacher return within group/subject/day, TASK-448) - golden-first guard:
        // the golden's same-subject multi-teacher blocks are all single-handoff (A->B), 0
        // returns (verified by Fedora). 0 matches here locks the invariant durably.
        assertMatchCount(snapshot, "H28", 0);
        // H-COPRESENCE (guest co-presenza beyond golden depth, TASK-464, ratified DEFAULT ON
        // 2026-08-01). Golden-first guard: the cap's ceiling IS the golden's own
        // per-(tag,weekday,slot) group depth, and the golden's own placements cannot exceed
        // their own depth by construction (each group contributes one lesson per slot, H3),
        // so a faithful golden scores 0 over-depth. 0 matches locks the ratified invariant
        // durably and surfaces a subject->tag mapping drift that would under-count depth (a
        // spurious over-depth firing on the golden would fail this assertion, which the bare
        // hard==0 check cannot catch since H-COPRESENCE is absent at 0 matches).
        assertZeroHard(snapshot, "H-COPRESENCE");
        assertMatchCount(snapshot, "H-COPRESENCE", 0);
        // M-CPS (curriculum post-stage target 6h asymmetric, TASK-469, ratified DEFAULT ON
        // 2026-08-01). Golden-first guard: the golden's post-stage curriculum hours are
        // G10=6 (at target, 0), G08=5 (deficit 1 x full-weight 100 = 100), G11=29
        // (excess 23 x weak-weight 1 = 23), total 123 medium across 3 perimeter-group
        // matches. Pinning both the score and the match count locks the ratified asymmetric
        // penalty AND the INPUT-derived stage-end map, surfacing drift the bare hard==0
        // check cannot catch (M-CPS is medium, absent from the hard feasibility read).
        assertScore(snapshot, "M-CPS", ScoreCompat::mediumOf, v -> v == -123,
                "medium score -123 (G10 0 at target + G08 deficit 1x100 + G11 excess 23x1)");
        assertMatchCount(snapshot, "M-CPS", 3);
        // M29 (professionalizzanti at day edges; was hard H22 until TASK-379's
        // golden-first demotion - the golden's own Wed/Thu placements never followed
        // the docx's edge rule) is exempt here for the same
        // structural reason as M20/H21 above, but at a DAY-LEVEL (not per-entity): the
        // constraint's own javadoc exempts a (group, date) group only when EVERY
        // professionalizzanti lesson contributing to that day's shape is pinned. Golden
        // import pins every Lesson, so every G12 Wed/Thu professionalizzanti day in
        // this fixture is 100% pinned and exempt - even though a manual CSV spot-check
        // (TASK-370, non-lab professionalizzanti only - see H22's own lab-exclusion note)
        // shows the golden's OWN Wed/Thu professionalizzanti placements do NOT follow the
        // docx's edge-of-day rule (e.g. Wednesday slot set {0,4} on 9 dates, Thursday slot
        // set {2,3} on 11 dates - neither is a contiguous <=2-slot run touching an edge).
        // A non-zero value here would mean an
        // unpinned (solver-placed) lesson created an illegal edge-run shape - a genuine
        // regression, not a tolerated historical pattern.
        assertScore(snapshot, "M29", ScoreCompat::mediumOf, v -> v == 0, "medium score 0");
        assertMatchCount(snapshot, "M29", 0);
        // H17 (vertically compact, TASK-350 recalibration) is exempt here for the same
        // break-level-pinned-exemption reason as H15 above (verticallyCompact()'s own
        // javadoc). Before this fix, the golden fixture measured -3 hard / 2 matches -
        // exactly the two documented afternoon-contiguity exceptions from analysis.md
        // section 3 (both fully-pinned historical days: T01/G10/2026-12-17's
        // 13:00 hole, G08/2027-04-01's stage+RA 12-14 hole). Since golden import pins
        // every Lesson, both breaks have both sides pinned, so they score 0 here. A
        // non-zero value would mean an unpinned (solver-placed) lesson created a gap -
        // a genuine regression, not a tolerated historical exception.
        assertZeroHard(snapshot, "H17");
        assertMatchCount(snapshot, "H17", 0);
        // H23/H24 (daily activity quota safety net, TASK-381 "L1-bis") are
        // pinned-exempt per-entity (same M20/M28 shape) - the golden's own 8
        // (G04) / 8 (G08) / 4 (G13) "stray" bound-lab lessons on
        // zero-quota days are all pinned (historical import), so both
        // measure 0/0 here. A non-zero value would mean an unpinned
        // (solver-placed) lesson landed on a quota-closed day - a genuine
        // regression, not a tolerated historical stray.
        assertZeroHard(snapshot, "H23");
        assertMatchCount(snapshot, "H23", 0);
        assertZeroHard(snapshot, "H24");
        assertMatchCount(snapshot, "H24", 0);
        // H25 (Stage lesson outside stage days safety net, TASK-384) is
        // pinned-exempt per-entity, same shape as H23/H24 above: the golden
        // import pins every Stage lesson (GoldenScheduleImportService's own
        // find-or-reuse-then-pin path), so this measures 0/0 here regardless
        // of whether this fixture seeds a stage-day fact for every stage
        // group (it only seeds the 3 FC-sourced groups via
        // generateDailyActivityQuotas() - see seedDailyActivityQuotas()'s own
        // javadoc; the other 5 class-days stage groups have no
        // DailyActivityQuota row in THIS fixture at all, so Lesson#isStageConfined()
        // is a structural no-op for them here too). A non-zero value would
        // mean an unpinned (solver-placed) Stage lesson landed outside its
        // group's stage-day fact - a genuine regression.
        assertZeroHard(snapshot, "H25");
        assertMatchCount(snapshot, "H25", 0);
        // H26 (lesson on no-session day safety net, TASK-408) is pinned-exempt
        // per-entity, same shape as H23/H24/H25 above. Its empirical basis IS
        // this golden: for every sheet-covered group the golden places 0 hours
        // on every date with no DailyActivityQuota row (the 26/26 phantom-day
        // pairs of the TASK-408 discovery), so a correct golden never has an
        // unpinned lesson on a no-session day - and every golden lesson is
        // pinned here anyway, so this measures 0/0. A non-zero value would mean
        // a genuine sheet-vs-golden discrepancy (an unpinned lesson on a
        // no-session date), to investigate, not a spurious hit.
        assertZeroHard(snapshot, "H26");
        assertMatchCount(snapshot, "H26", 0);
        // M31 (lab day single contiguous run) is DAY-LEVEL pinned-exempt, same
        // shape as M29 - the 100%-pinned golden import makes every quota-bound
        // lab day's set fully pinned, so this is vacuous 0/0 here regardless of
        // the raw data's own contiguity (see the constraint's own javadoc for
        // the one known non-vacuous exception, G13 2026-11-03).
        assertScore(snapshot, "M31", ScoreCompat::mediumOf, v -> v == 0, "medium score 0");
        assertMatchCount(snapshot, "M31", 0);
        // M32 (template deviation, TASK-413) is per-entity pinned-exempt (like
        // M20/M28) AND only fires for courses present in the pre-solve weekly
        // template. The golden import is 100% pinned, so (a) no lesson survives
        // the !pinned filter and (b) WeeklyTemplatePlanner sees zero unpinned
        // lessons and publishes an EMPTY template map - either alone makes this
        // vacuous 0/0/0 here.
        assertHard(snapshot, "M32", 0, "M32 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M32", ScoreCompat::mediumOf, v -> v == 0, "medium score 0 (pinned-exempt + empty template on the 100%-pinned import)");
        assertMatchCount(snapshot, "M32", 0);
        // M33 (surplus beyond expected day hours, TASK-423) shares M20's exact
        // population, so it is structurally vacuous on this 100%-pinned fixture for
        // the same reason. Calibration against the school's own 70 above-expectation
        // days lives in the daily-fill KPI's "sopra" count, never in this guard.
        assertScore(snapshot, "M33", ScoreCompat::mediumOf, v -> v == 0, "medium score 0 (pinned-exempt, vacuous on the 100%-pinned import)");
        assertMatchCount(snapshot, "M33", 0);
        // M34 (weekly burst cap, TASK-425) is NOT pinned-exempt (measures ALL
        // real-teacher hours like S12, for golden-practice verification) but it
        // is a structural zero anyway: the reference IS the golden's own max weekly
        // hours per pair, and a schedule can never burst above a max derived from
        // its own data, so every (group, subject, week) cell measures hours <= its
        // own pair's cap by construction. A non-zero value here would mean the
        // imported golden bursts ABOVE the reference JSON's max (a real data or
        // publication bug, not a tolerated historical pattern).
        assertHard(snapshot, "M34", 0, "M34 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M34", ScoreCompat::mediumOf, v -> v == 0, "medium score 0 (self-referential: golden never bursts above its own max)");
        assertMatchCount(snapshot, "M34", 0);
        // M35 (weekly spread deficit, TASK-425) is NOT a self-referential zero like
        // M34: the reference's active_weeks is computed from lessons.csv (the raw
        // golden source), while this fixture imports via GoldenScheduleImportService
        // which DROPS 15 beyond-horizon rows (G04/Scienze + G08/RA dated
        // 2027-06-14/15, past SCHOOL_YEAR_END 2027-06-10 = ISO week 2027-W24).
        // G04/Scienze has W24 as its last active week in the reference
        // (active_weeks=29); the import drops all W24 rows, so the imported golden
        // has 28 active weeks -> deficit 1, 1 match. The G08/RA pair ALSO has W24
        // activity in the reference, but its KPI-dump subject ("RA") does not match
        // the database's Course.subject ("Recuperi ed Approfondimenti") through
        // SubjectNormalization, so that reference entry is never looked up and
        // produces no deficit. This is the SAME 15-row gap S12's own golden pin
        // already documents (5,623 vs the naive Python's 5,625), surfaced here as a
        // small calibrated M35 residual rather than a structural zero. If the
        // import's horizon ever extends to W24 (or "RA" is aliased), this moves to 0.
        assertHard(snapshot, "M35", 0, "M35 has no hard component (medium-only constraint)");
        // TASK-550 extension: G04/Scienze's last active week (2027-W24, the 06-15
        // recovery row) is now INSIDE the horizon - the historical 1-week M35 deficit
        // the golden itself practiced is gone
        assertScore(snapshot, "M35", ScoreCompat::mediumOf, v -> v == 0,
                "M35 zero (TASK-550 extension: the 2027-W24 recovery week is in-horizon now)");
        assertMatchCount(snapshot, "M35", 0); // TASK-550 extension: the deficit match is gone too

        // ─── Calibrated residuals (TASK-366/373; re-measured here, not
        // blindly copied from the doc — see each assertion's comment) ──────
        assertHard(snapshot, "H4", -12,
                "TASK-366: 12 genuine rientro exceptions (real non-prof lessons scheduled during a stage "
                        + "window) after nonProfLessonStagePairs() excludes pseudo-teacher (Stage) lessons; "
                        + "the pre-fix number was -706 (694 self-blackout artifacts + 12 genuine), see golden-audit.md");
        assertZeroHard(snapshot, "H7"); // TASK-348: T01's maxDailyHours=8 removes the sole real overage
        // MEASURED 2026-07-18 (TASK-365 recalibration, threshold 1h): this fixture
        // scored -252,300 medium / 867 matches pre-recalibration, -621 medium / 452
        // matches post. RE-MEASURED 2026-07-20 (TASK-381 "L3", threshold 1h -> 2h):
        // full rationale/exemption sourcing in SchoolScheduleConstraintProvider's M21
        // javadoc and CLAUDE.md's "M21 recalibration (task-365)" / "Shape
        // recalibration (L3, TASK-381)" sections - not repeated here. If this pin
        // breaks: the residual is dominated by ordinary non-lab subjects genuinely
        // repeated same-day 3h+ (a 2h day is now free); "Ra lab aperto" and
        // "Recuperi ed Approfondimenti" are known non-whitelisted look-like-lab
        // subjects deliberately still penalized (see CLAUDE.md) - don't silently
        // widen the whitelist to make a drift disappear.
        assertHard(snapshot, "M21", 0, "M21 has no hard component (medium-only constraint)");
        // TASK-550 extension (measured 25/08): the 26 recovery-day golden rows add
        // their >2h blocks too - -196/97 (was -187/95 pre-extension)
        assertScore(snapshot, "M21", ScoreCompat::mediumOf, v -> v == -214,
                "medium score -214 (TASK-550: extension days + G14 Confezione de-tag - 18 more"
                        + " non-lab >2h golden blocks; was -196 extension-only, -187 pre-extension)");
        assertMatchCount(snapshot, "M21", 104); // TASK-550 combined (measured: de-tag merges some blocks)

        // S17 (reward same-subject block pair, TASK-381 "L3") - NOT pinned-exempt
        // (unlike M20/H21/H15/H17): measures the golden's real block structure
        // directly, same reasoning S12/S13's own non-pinned-exempt precedent (S13's
        // "previousWeek" side, S12 entirely). MEASURED 2026-07-20.
        assertScore(snapshot, "S17", ScoreCompat::softOf, v -> v == 12695,
                "soft score 12695 (TASK-550 extension: the recovery-day pairs add +60; was 12635/2527 matches pre-extension)");
        assertMatchCount(snapshot, "S17", 2539); // TASK-550 extension (measured)

        // M30 (lab day quota fill, TASK-381 "L1-bis"): -7 medium,
        // counting ALL lessons INCLUDING pinned (school-practice verification,
        // same M24/M25 precedent - not pinned-exempt like M20/H21). TASK-434
        // (2026-07-28): G04's 30-date pure-stage window (aula/lab -> 0, stage=8)
        // removed the lab-quota mismatch the golden previously paid on those
        // dates (was -43/12, with G04 35 excess-hours/10 mismatch days); the
        // stage days now carry stage-quota, so the golden's pure-stage placement
        // there scores clean. 2026-09-01 golden-first calendar fix: the G08
        // 04-26 row moved from phantom stage to lab-quota 5, and the golden's
        // real 5 lab hours that day now match their quota (-10 -> -7: one less
        // mismatch the sheet typo was charging the golden).
        assertScore(snapshot, "M30", ScoreCompat::mediumOf, v -> v == -7,
                "medium score -7 after TASK-434 + the 2026-09-01 G08 calendar fix");
        // mech (laptop, verified by run): the match count is 4, not the hypothesized 3 -
        // the pre-cure 4 was correct all along; only the score moved (-10 -> -7).
        assertMatchCount(snapshot, "M30", 4);

        // ─── Recalibration targets (m-8 tasks 350/372): presence/sign
        // only, not exact values - these are the ones still being tuned. ────
        assertScore(snapshot, "M12", ScoreCompat::mediumOf, v -> v <= 0, "medium score <= 0 (penalty-only constraints; a positive value would mean a reward misfire)"); // TODO(TASK-350): pin exact value once M12 lands

        // MEASURED 2026-07-23 (TASK-405: M18 WIDENED to every group-day with an
        // ofMedium(50) x offset-slots gradient (retuned 250->50 by the task405 laptop A/B) - the old <=5-open-slots gate,
        // task-350, measured 0/0 because it excluded the wide-window groups).
        // The widened rule is a CALIBRATED residual, not zero-tolerance: the
        // golden itself has exactly 3 non-anchored days, all G08 (2026-12-04,
        // 2027-04-27, 2027-04-29, each offset 1 -> -750) - 2,210 of 2,213
        // group-days anchored (99.86%). Everything else (incl. the ramp Monday,
        // which anchors at its calendar's own open slot 1) starts at the first
        // open slot, consistent with H17's compactness finding. A change here
        // means the golden import or the calendar model drifted.
        // MEASURED 2026-07-24 (TASK-409: M20.2 empty-expected-day complement, found
        // by the golden-vs-run visual comparator - M20 is Lesson-sourced so an
        // evacuated day was invisible AND perversely CHEAPER than a 1h day). The
        // golden as IMPORTED leaves only 2 expected days empty in the whole year
        // (0.09% of 2,213 group-days; a raw-CSV estimate said ~17, but most of
        // those carry rows the import does materialize) - a CALIBRATED residual,
        // not zero-tolerance (no lesson exists on an empty day, so no pin
        // exemption can apply; these are the school's own real gaps).
        // RE-PINNED 2026-07-26 (M20 progressive weigher): -8000 -> -14500 at an
        // UNCHANGED match count of 2. M20.2 now prices an empty day on M20's own
        // progressive curve at full depth, so evacuating a day can never undercut
        // leaving hours on it. Measurement bonus: the flat -8000 was compatible
        // with quotas 4+4 OR 3+5 (indistinguishable at 1000/h); the progressive
        // price -14500 = m20DeficitCost(3) + m20DeficitCost(5) = 4,500 + 10,000
        // REVEALS the two real empty days are a quota-3 and a quota-5 day. The
        // match count is the regression signal here - only the price tag of the
        // golden's 2 genuine empty days moved, not which days they are.
        assertHard(snapshot, "M20.2", 0, "M20.2 has no hard component (medium-only constraint)");
        // RE-PINNED 2026-08-16 (TASK-496 G14 fill-exemption removal): -14500 -> -21500.
        // G14 was exempt from the fill family; now included with its real 120h part-time
        // quota (THU/FRI), it contributes one more empty-expected day. The -21500 decomposed
        // as: quota-3 (4500) + quota-5 (10000) + G14's empty day at m20DeficitCost(7) =
        // 7000, total 21500. The 2 non-G14 matches are UNCHANGED: the removal added the
        // G14 contribution, it did not change which non-G14 days are empty.
        // RE-PINNED 2026-08-27 (TASK-550-family G09 cure): -21500 -> -14000, matches
        // 3 -> 2. The sheet declared 670h against the 666h pool; the 4h phantom came off
        // the year-end rows the golden itself leaves empty (04/06 5->4, 07/06 dropped).
        // The golden's 07/06 cell no longer exists as a declared day; 04/06 remained at
        // quota 4 = m20DeficitCost(4) 7000 + G14 7000.
        // RE-PINNED 2026-08-28 (golden-first cure F1): -14000 -> -7000, matches 2 -> 1.
        // The golden leaves 04/06 ENTIRELY empty, so the whole quota-4 row went too
        // (assume the practiced pool, correct the sheet, notify referente a posteriori;
        // one-line revert in both seed CSV copies if the 4h were real). Only G14's
        // empty day remains.
        assertScore(snapshot, "M20.2", ScoreCompat::mediumOf, v -> v == -7000, "medium score -7000 (only G14's quota-7 empty day remains: the G09 04/06 cell is no longer declared after the 28/08 cure F1)");
        assertMatchCount(snapshot, "M20.2", 1);

        assertHard(snapshot, "M18", 0, "M18 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M18", ScoreCompat::mediumOf, v -> v == -150, "medium score -150 (3 golden G08 late-start days x offset 1 x 50, retuned from 250 by the task405 laptop A/B)");
        assertMatchCount(snapshot, "M18", 3);
        assertScore(snapshot, "S1", ScoreCompat::softOf, v -> v != 0, "non-zero soft score (presence-only check - the constraint must have fired at all)"); // TODO(TASK-349/372): pin exact value once S-tier is recalibrated
        assertScore(snapshot, "S7", ScoreCompat::softOf, v -> v != 0, "non-zero soft score (presence-only check - the constraint must have fired at all)");

        // TASK-368: M24/M25 are new weekly choice-day constraints measured directly
        // against the golden (not pinned-exempt like M20/H21 - the golden's real
        // historical placements are exactly what these two constraints score).
        // MEASURED 2026-07-18, cross-checked independently against lessons.csv via
        // scripts/analyze/derive-golden-availability-checks.py's isocalendar()-based
        // Python re-derivation (same 15/16 and 27/27 numbers via a completely
        // different grouping mechanism - see that script's TASK-368 section).
        assertHard(snapshot, "M24", 0, "M24 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M24", ScoreCompat::mediumOf, v -> v == -16,
                "medium score -16 (15 weeks where T11 used slot 0 on 2+ distinct days - the golden "
                        + "actually has 52 T11 slot-0 lessons, mostly recurring Tuesdays, which CONTRADICTS "
                        + "the TASK-368 task brief's claim of 'ZERO slot-0 days' for T11; not zero-tolerance "
                        + "since this is real historical data M24 was never meant to retroactively re-litigate, "
                        + "only to cap the SOLVER's own future placements going forward");
        assertMatchCount(snapshot, "M24", 15);
        assertHard(snapshot, "M25", 0, "M25 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M25", ScoreCompat::mediumOf, v -> v == -27,
                "medium score -27 (27 weeks where T05's golden placements left fewer than "
                        + "requiredFreeDaysPerWeek=2 Mon-Fri days free - confirms Teacher.requiredFreeDaysPerWeek's "
                        + "javadoc reasoning: with the docx's literal value of 1, this measures 0/0 every time "
                        + "(Friday's existing hard block alone always satisfies it), so 2 is the only value that "
                        + "makes the constraint non-vacuous for him)");
        assertMatchCount(snapshot, "M25", 27);

        // TASK-369: T03 ("Sicurezza before stage") and T20 ("subject
        // start-period preference") course-timing constraints, plus the S12
        // weekly-continuity reward. See each constraint's own javadoc in
        // BaseConstraintProvider/SchoolScheduleConstraintProvider for the full
        // derivation and CLAUDE.md's "Course-timing constraints (task-369)"
        // design-decision section for the golden measurements.
        assertZeroHard(snapshot, "H30");

        // TASK-542 iter-18 (23/08, mandato maintainer): the families promoted AFTER this guard's
        // last calibration, now MEASURED on the golden (GoldenNewConstraintsMeasureTest):
        // H31/H32/H33 are all 0 on the golden in the current scope (H32 excludes the 1/G08
        // pool-cap groups and G14), so the school's practice violates none of them and the
        // constraints are calibrated. H4 measures -12 (12 non-professionalizzanti inside stage
        // windows: the school practices it) - pinned at the measured value until classified.
        assertZeroHard(snapshot, "H31");
        assertZeroHard(snapshot, "H32");
        assertZeroHard(snapshot, "H33");
        // TASK-557 (26/08): the golden's own practice leaves EXACTLY 2 declared cells
        // empty, both G09 year-end (04/06 quota 5, 07/06 quota 3). STRICT per
        // maintainer's amendment: NO tolerance on those cells - they may be the human
        // planner's year-end surrender, not the school's preference; the solver's
        // target is 0 (better-than-golden). A tolerance is admissible only as a
        // proven impossibility, declared quasi-hard in the task and VERDICT.
        // RE-PINNED 2026-08-27 (TASK-550-family G09 cure): -2 -> -1 (04/06 5->4,
        // 07/06 dropped). RE-PINNED 2026-08-28 (golden-first cure F1): -1 -> 0. The
        // golden leaves 04/06 entirely empty, so that cell's declaration went too:
        // the golden now has NO empty declared day left. STRICT stance unchanged and
        // now free: the solver's 0 target coincides with the golden's own practice.
        // One-line revert (restore the G09 04/06 row in both seed CSV copies) if
        // referente says the 4h were real.
        assertHard(snapshot, "H34", 0, "nessuna cella dichiarata vuota nel golden: la G09 04/06 non e' piu' dichiarata dopo la cura golden-first 28/08");
        if (Boolean.getBoolean("iter18.h4drill")) {
            System.out.println("H4DRILL count=" + snapshot.constraintMap().entrySet().stream()
                    .filter(e -> ((ConstraintRef) e.getKey()).id().startsWith("H4"))
                    .mapToLong(e -> e.getValue().getConstraintMatchCount()).sum());
            snapshot.constraintMap().forEach((ref, total) -> {
                if (ref.id().startsWith("H4")) {
                    total.getConstraintMatchSet().forEach(m ->
                        System.out.println("H4DRILL " + m.getJustification()));
                }
            });
        }
        assertScore(snapshot, "H4", ScoreCompat::hardOf, v -> v == -12,
                "hard score -12 (the golden's own 12 non-prof lessons inside stage windows:"
                        + " practiced by the school, classification pending iter-18)");
        assertScore(snapshot, "H30", ScoreCompat::hardOf, v -> v == 0,
                "hard score 0 (T03 already keeps all G07 Sicurezza hours before its stage start by hand; "
                        + "promoted M26->H30 2026-08-15, golden never violates it)");
        assertMatchCount(snapshot, "H30", 0);
        assertHard(snapshot, "M27", 0, "M27 has no hard component (medium-only constraint)");
        assertScore(snapshot, "M27", ScoreCompat::mediumOf, v -> v == 0,
                "medium score 0 (T20's own golden placements satisfy both the early- and late-subject windows "
                        + "with margin - see the constraint's own javadoc for the derivation)");
        assertMatchCount(snapshot, "M27", 0);
        assertScore(snapshot, "S12", ScoreCompat::softOf, v -> v == 5625,
                "soft score 5623 (5,623 distinct (group, subject, ISO week) pairs with >=1 lesson; 2 fewer than "
                        + "the naive 5,625 Python cross-check in scripts/analyze/derive-golden-availability-checks.py, "
                        + "which counts every open lessons.csv row's date regardless of whether it landed on a real "
                        + "Timeslot - report.unmatchedNoTimeslot=15 rows (G04/Scienze and G08/RA, all dated "
                        + "2027-06-14/15, past the Timeslot horizon SCHOOL_YEAR_END=2027-06-10) never become pinned "
                        + "Lessons, and they were each the ONLY lesson in their (group, subject, ISO week) combo - "
                        + "collapsing exactly those 2 combos, confirmed by re-running the Python script with the same "
                        + "horizon exclusion added)");
        assertMatchCount(snapshot, "S12", 5625); // TASK-550 extension: the 2 collapsed combos are back

        // TASK-375: S13 weekly-stability soft constraint (design doc
        // claudedocs/design/weekly-template-redesign-2026-07-17.md). MEASURED
        // 2026-07-18: soft score 0 / 0 matches - structurally, not because the
        // golden's recurrence is actually zero (the design doc's own numbers say
        // 41.5-72% of golden hours DO recur week-to-week). The golden import pins
        // every Lesson, and weeklyStability()'s current (rewarded) side filters
        // !lesson.pinned - a 100%-pinned fixture has zero eligible earners no
        // matter how many previous-week cells would otherwise match. This is the
        // same structural exemption shape as M20/H21/H22 above, except S13 is a
        // reward (not a penalty), so "exempt" here reads as "0" rather than
        // "vacuously satisfied". Pinned lessons remain eligible as the
        // PREVIOUS-week ANCHOR side (no !pinned filter there) - that role just
        // never gets exercised in this fixture because there's no unpinned
        // current-side Lesson left to earn the reward. A non-zero value here
        // would mean an unpinned (solver-placed) Lesson matched a previous-week
        // cell in a 100%-pinned golden import, which cannot happen by
        // construction - a genuine regression in the pinned-exclusion filter.
        assertScore(snapshot, "S13", ScoreCompat::softOf, v -> v == 0,
                "soft score 0 (golden import is 100% pinned; S13 excludes pinned lessons from EARNING the "
                        + "reward, so there are zero eligible earners regardless of match rate - see this "
                        + "assertion's own comment for the full mechanism)");
        assertMatchCount(snapshot, "S13", 0);
    }

    /**
     * M-BF prototype golden check (wt/mbf-proto, opt-in PLANNER_TEST_MBF_GOLDEN=1):
     * the imported golden under the lab-teacher day contiguity medium. Prediction
     * (written before the first run): exactly 1 match (G05 2027-03-17, the single
     * interleaved lab day of the golden census), 1200 medium at the default 20/min,
     * hard tier untouched. Tolerance for isLab()-vs-census-family divergence is
     * REPORTED, not assumed: the run prints the match count.
     */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PLANNER_TEST_MBF_GOLDEN", matches = "1")
    void mbfGoldenCheck() {
        seedTimeslots();
        seedStudentGroups();
        seedTeachers();
        seedCourses();
        seedSchoolHolidays();
        seedStagePatterns();
        seedGroupCalendarRules();
        seedDailyActivityQuotas();
        seedAvailabilityRules();
        availabilityExpansionService.expandAll();

        GoldenImportReport report = importService.importGolden(true);
        assertImportReport(report);

        System.setProperty("planner.constraints.empty-day.enabled", "true");
        System.setProperty("planner.constraints.mbf.enabled", "true");
        ScheduleService.ConstraintMatchSnapshot snapshot = scheduleService.analyzeScore();
        snapshot.constraintMap().forEach((ref, total) -> {
            if (ref.id().contains("M-BF")) {
                System.out.printf("=== M-BF golden: matches=%d score=%s (predicted 1 match, -1200)%n",
                        total.getConstraintMatchCount(), total.getScore());
                total.getConstraintMatchSet().forEach(m ->
                        System.out.println("    M-BF golden match: " + m.getJustification()));
            }
        });
    }

    // ─── Import-report assertions (task brief's mechanical expectations) ───

    private void assertImportReport(GoldenImportReport report) {
        assertEquals(12979, report.rows, "lessons.csv row count (DataGeneratorSeedTest#lessonsCsvHas12979Rows)");
        // TASK-550 extension: golden rows on the 06-11/14/15 recovery days now import on-grid
        assertEquals(10542, report.imported, "10,501 real-teacher matches + 41 RA fallback rows (TASK-550 ext included; TASK-592 cure: they pin under the 9 real courses)");
        assertEquals(2009, report.importedStage, "Stage rows matched to a Timeslot (TASK-550 extension: +16 recovery-day rows)");
        assertEquals(311, report.getStageOnClosure(), "TASK-373 stage-on-closure total (24+183+16+8+72+8, see GoldenScheduleImportServiceTest)");
        assertEquals(0, report.getStageBeyondHorizon(), "TASK-550 extension: the 06-11/14 recovery rows now have timeslots");
        assertEquals(117, report.skippedClosed, "closed=True/blank-teacher rows, excluding stage-on-closure");
        assertEquals(0, report.skippedEsame, "Esame rows are never scheduled in the golden grid");
        assertEquals(0, report.getUnmatchedNoCourseRows(), "TASK-366: all previously-unmatched rows now import via RA micro-courses");
        assertEquals(6, report.esameCoursesCreated, "one Esame accounting Course per incarichi.csv Esame row");
        // TASK-592 cure (11/09): G08's RA pool is now distributed into real per-teacher
        // incarichi rows matching the golden's own split, so the import matches every
        // golden RA row to a real course and creates no accounting Course anymore.
        assertEquals(0, report.raCoursesCreated, "TASK-592 cure: the RA pool has real courses, no accounting Course needed");
    }

    // ─── Constraint-score assertions with drill-down failure messages ──────

    private void assertZeroHard(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId) {
        assertHard(snapshot, constraintId, 0, null);
    }

    private void assertHard(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId,
                            long expectedHard, String mechanismNote) {
        BendableScore score = scoreFor(snapshot, constraintId);
        if (ScoreCompat.hardOf(score) != expectedHard) {
            String suffix = mechanismNote != null ? " Expected mechanism: " + mechanismNote : "";
            fail(failureMessage(constraintId, "hard score " + expectedHard, score, snapshot) + suffix);
        }
    }

    private void assertMatchCount(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId, long expectedMatches) {
        long actual = matchCountFor(snapshot, constraintId);
        if (actual != expectedMatches) {
            fail(constraintId + ": expected " + expectedMatches + " constraint matches, got " + actual
                    + ". " + offendingTuplesMessage(constraintId));
        }
    }

    /**
     * Shared shape for every non-hard-exact assertion below (M20's zero medium,
     * the M21/M12 sign checks, the S1/S7 presence checks): pull one score
     * component via {@code field}, test it with {@code accepts}, and fail with
     * the same offending-tuples drill-down every other assertion in this file
     * uses (avoids each variant hand-rolling its own message and silently
     * losing that drill-down, as the soft-score check originally did).
     */
    private void assertScore(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId,
                             ToLongFunction<BendableScore> field, LongPredicate accepts, String expectedDescription) {
        BendableScore score = scoreFor(snapshot, constraintId);
        if (!accepts.test(field.applyAsLong(score))) {
            fail(failureMessage(constraintId, expectedDescription, score, snapshot));
        }
    }

    /**
     * Matches a constraintId exactly, OR as the dot-notation PARENT of a
     * split child (e.g. querying "M30" also picks up "M30.1"/"M30.2" - M30 is
     * the first constraint THIS suite asserts on that's actually split across
     * two {@code Constraint} methods; see {@code BaseConstraintProvider}'s
     * own M30 javadoc for why). Prefix-summing is deliberately scoped to the
     * {@link #DOT_SPLIT_PARENTS} allowlist rather than applied to every id:
     * a blanket numeric-prefix match would make the calibrated "H4" pin
     * silently absorb "H4.3" (a DISTINCT constraint that merely shares the
     * numeric prefix, today 0 only because ProfessionalizzantiDay has zero
     * 2026/27 rows) - a future H4.3 regression would then surface as an
     * inexplicable H4 failure, or worse be absorbed by it.
     */
    private static final Set<String> DOT_SPLIT_PARENTS = Set.of("M30");

    private Stream<Map.Entry<ConstraintRef, ConstraintMatchTotal<BendableScore>>> matchesFor(
            ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId) {
        return snapshot.constraintMap().entrySet().stream()
                .filter(e -> {
                    String canonical = ConstraintIds.canonicalId(e.getKey().id());
                    return constraintId.equals(canonical)
                            || (DOT_SPLIT_PARENTS.contains(constraintId)
                                    && canonical != null && canonical.startsWith(constraintId + "."));
                });
    }

    private BendableScore scoreFor(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId) {
        return matchesFor(snapshot, constraintId)
                .map(e -> (BendableScore) e.getValue().getScore())
                .reduce(ScoreCompat.ZERO, BendableScore::add);
    }

    private long matchCountFor(ScheduleService.ConstraintMatchSnapshot snapshot, String constraintId) {
        return matchesFor(snapshot, constraintId)
                .mapToLong(e -> e.getValue().getConstraintMatchCount())
                .sum();
    }

    private String failureMessage(String constraintId, String expectedDescription, BendableScore actual,
                                  ScheduleService.ConstraintMatchSnapshot snapshot) {
        return constraintId + ": expected " + expectedDescription + ", got " + actual + ". "
                + offendingTuplesMessage(constraintId);
    }

    /** Up to 10 offending (teacher, group, timeslot) tuples via the same drill-down the UI uses. */
    private String offendingTuplesMessage(String constraintId) {
        List<LessonViolationAnalysis> violations = scheduleService.analyzeLessonViolations(
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), constraintId, "en");
        if (violations.isEmpty()) {
            return "No per-lesson drill-down available for " + constraintId + " (check /api/schedule/analysis/lessons manually).";
        }
        String tuples = violations.stream()
                .limit(10)
                .map(v -> String.format("[teacher=%s group=%s date=%s]", v.teacher(), v.studentGroup(), v.timeslot()))
                .collect(Collectors.joining(", "));
        return "Offending entities (up to 10 of " + violations.size() + "): " + tuples;
    }

    // ─── Fixture seeding (mirrors DataGenerator's own private seeding logic
    // field-for-field; see class javadoc for why onStartup() itself can't be
    // called) ────────────────────────────────────────────────────────────

    /** Mirrors {@code DataGenerator.generateTimeslots()} (private). */
    @Transactional
    void seedTimeslots() {
        for (LocalDate date : DataGenerator.computeSchoolDays(
                TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_END, DataGenerator.CLOSURE_DATES_2026_27)) {
            for (LocalTime[] slot : DataGenerator.STANDARD_SLOTS) {
                new Timeslot(LocalDateTime.of(date, slot[0]), LocalDateTime.of(date, slot[1])).persist();
            }
        }
    }

    private static final Map<String, Integer> MAX_HOURS_PER_DAY = RealSeedFixture.MAX_HOURS_PER_DAY;

    // Mirrors DataGenerator.NO_PROFESSIONALIZZANTI_DURING_STAGE (private, DataGenerator.java:171-174).
    private static final Set<String> NO_PROFESSIONALIZZANTI_DURING_STAGE = Set.of("G08", "G09");

    /** Mirrors {@code DataGenerator.generateStudentGroups()} (private). */
    @Transactional
    void seedStudentGroups() {
        for (String[] row : DataGenerator.loadSeedCsv("groups.csv", 3)) {
            String name = row[0].trim();
            int enrolled = Integer.parseInt(row[2].trim());
            StudentGroup group = new StudentGroup(name, enrolled);
            group.maxHoursPerDay = MAX_HOURS_PER_DAY.get(name);
            group.startDate = TimeslotSequence.SCHOOL_YEAR_START;
            group.allowProfessionalizzantiDuringStage = !NO_PROFESSIONALIZZANTI_DURING_STAGE.contains(name);
            group.persist();
        }
    }

    /** Mirrors {@code DataGenerator.generateTeachers()} (private). */
    @Transactional
    void seedTeachers() {
        for (String[] row : DataGenerator.loadSeedCsv("teachers.csv", 6)) {
            Teacher teacher = new Teacher(row[0].trim());
            String maxDailyHours = row[3].trim();
            teacher.maxDailyHours = maxDailyHours.isEmpty() ? null : Integer.valueOf(maxDailyHours);
            // TASK-368: two more optional columns, same blank-means-null pattern.
            String maxEarlyStartDaysPerWeek = row[4].trim();
            teacher.maxEarlyStartDaysPerWeek = maxEarlyStartDaysPerWeek.isEmpty() ? null : Integer.valueOf(maxEarlyStartDaysPerWeek);
            String requiredFreeDaysPerWeek = row[5].trim();
            teacher.requiredFreeDaysPerWeek = requiredFreeDaysPerWeek.isEmpty() ? null : Integer.valueOf(requiredFreeDaysPerWeek);
            teacher.persist();
        }
    }

    // Subject taxonomy now comes from SubjectRegistry / seed/subjects.csv (TASK-437,
    // audit C1) - the single source of truth, no hand-kept mirror copy. These thin
    // delegators keep the seedCourses() call sites readable.
    private static final SubjectRegistry SUBJECTS = loadSubjects();

    private static SubjectRegistry loadSubjects() {
        SubjectRegistry registry = new SubjectRegistry();
        registry.load();
        return registry;
    }

    private static boolean isLabSubject(String subject, String group) {
        return SUBJECTS.flagsFor(subject, group).isLab();
    }

    private static List<String> getRoomTags(String subject, String group) {
        return SUBJECTS.flagsFor(subject, group).roomTags();
    }

    private static boolean isProfessionalizzante(String subject, String group) {
        return SUBJECTS.flagsFor(subject, group).professionalizzanti();
    }

    /** Mirrors {@code DataGenerator.generateCourses()} (private), reusing its accessible {@code aggregateCourseHours}. */
    @Transactional
    void seedCourses() {
        List<Teacher> teachers = Teacher.listAll();
        List<StudentGroup> groups = StudentGroup.listAll();
        List<String[]> rows = DataGenerator.loadSeedCsv("incarichi.csv", 4);
        DataGenerator.CourseAggregation aggregation = DataGenerator.aggregateCourseHours(rows);

        for (Map.Entry<DataGenerator.CourseKey, Integer> entry : aggregation.hoursByKey().entrySet()) {
            DataGenerator.CourseKey key = entry.getKey();
            Teacher teacher = findByName(teachers, key.teacherName());
            StudentGroup group = findGroupByName(groups, key.groupName());
            Course course = new Course(key.subject(), teacher, group, entry.getValue());
            course.isLab = isLabSubject(key.subject(), key.groupName());
            course.requiredRoomTags = getRoomTags(key.subject(), key.groupName());
            course.professionalizzanti = isProfessionalizzante(key.subject(), key.groupName());
            course.persist();
        }
    }

    /** Mirrors {@code DataGenerator.generateSchoolHolidays()} (private), using the accessible date set (descriptions aren't needed for H6). */
    @Transactional
    void seedSchoolHolidays() {
        for (LocalDate date : DataGenerator.CLOSURE_DATES_2026_27) {
            if (date.isBefore(TimeslotSequence.SCHOOL_YEAR_START) || date.isAfter(TimeslotSequence.SCHOOL_YEAR_END)) continue;
            new SchoolHoliday(date, "closure (TASK-355 fixture; see closures.csv for the real description)").persist();
        }
    }

    /** Mirrors {@code DataGenerator.generateStagePatterns()} (private, DataGenerator.java:597-626) - kept in lockstep by hand. */
    @Transactional
    void seedStagePatterns() {
        List<StudentGroup> groups = StudentGroup.listAll();
        addStagePattern(groups, "G07", LocalDate.of(2027, 3, 23), LocalDate.of(2027, 5, 28),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        addStagePattern(groups, "G11", LocalDate.of(2027, 1, 13), LocalDate.of(2027, 3, 19),
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        addStagePattern(groups, "G12", LocalDate.of(2026, 11, 20), LocalDate.of(2027, 4, 17),
                Set.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
    }

    private void addStagePattern(List<StudentGroup> groups, String groupName, LocalDate start, LocalDate end, Set<DayOfWeek> mask) {
        StudentGroup group = findGroupByName(groups, groupName);
        if (group != null) {
            new StagePattern(group, start, end, mask).persist();
        }
    }

    /** Mirrors {@code DataGenerator.generateGroupCalendarRules()} (private), reusing its accessible pattern table/parser verbatim. */
    @Transactional
    void seedGroupCalendarRules() {
        List<StudentGroup> groups = StudentGroup.listAll();
        List<StudentGroup> rampEligibleGroups = new ArrayList<>();

        for (String[] row : DataGenerator.GROUP_CALENDAR_PATTERNS) {
            String groupName = row[0];
            StudentGroup group = findGroupByName(groups, groupName);
            if (group == null) continue;
            for (int i = 0; i < DataGenerator.GROUP_CALENDAR_WEEKDAYS.length; i++) {
                int[] slots = DataGenerator.parseCalendarCell(row[i + 1]);
                new GroupCalendarRule(group, DataGenerator.GROUP_CALENDAR_WEEKDAYS[i], slots[0], slots[1]).persist();
            }
            if (row[1] != null) {
                rampEligibleGroups.add(group);
            }
        }
        for (StudentGroup group : rampEligibleGroups) {
            new GroupCalendarRule(group, DayOfWeek.MONDAY, 1, 4, TimeslotSequence.SCHOOL_YEAR_START, TimeslotSequence.SCHOOL_YEAR_START).persist();
        }
    }

    /**
     * Unlike the sibling {@code seedX} mirrors (which reproduce PRIVATE
     * DataGenerator seeders to avoid running {@code onStartup()}), this one
     * calls the real package-private generator directly - the horizon-filter
     * and skip-zero logic carrying the 513-row invariant stays in ONE place,
     * so the golden guard can't silently drift from production seeding.
     */
    @Transactional
    void seedDailyActivityQuotas() {
        dataGenerator.generateDailyActivityQuotas();
        // TASK-409: also the class_days expected-hours facts (TASK-385, the other
        // 11 groups) - this fixture predated generateExpectedDailyHours() and
        // never seeded them, which made M20.2 (empty expected day) structurally
        // blind here. Production seeds both; the pinned-exempt consumers
        // (M20/M12/H25) stay vacuous on the 100%-pinned golden either way.
        dataGenerator.generateExpectedDailyHours();
    }

    /** Mirrors {@code DataGenerator.generateAvailabilityRules()} (private). */
    @Transactional
    void seedAvailabilityRules() {
        List<Teacher> teachers = Teacher.listAll();
        for (String[] row : DataGenerator.loadSeedCsv("availability_rules.csv", 8)) {
            Teacher teacher = findByNameOrNull(teachers, row[0].trim());
            if (teacher == null) continue;
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(row[1].trim());
            int minSlot = Integer.parseInt(row[2].trim());
            int maxSlot = Integer.parseInt(row[3].trim());
            AvailabilityType type = AvailabilityType.valueOf(row[4].trim());
            LocalDate effectiveFrom = row[5].isBlank() ? null : LocalDate.parse(row[5].trim());
            LocalDate effectiveTo = row[6].isBlank() ? null : LocalDate.parse(row[6].trim());
            String note = row[7].trim();

            String dateError = RecurringAvailabilityRule.validateDateCoherence(dayOfWeek, effectiveFrom, effectiveTo);
            if (dateError != null) {
                throw new IllegalStateException("fixture availability_rules.csv row for '" + row[0].trim() + "': " + dateError);
            }
            RecurringAvailabilityRule rule = new RecurringAvailabilityRule(teacher, dayOfWeek, minSlot, maxSlot, type,
                    effectiveFrom, effectiveTo, note);
            for (RecurringAvailabilityRule existing : RecurringAvailabilityRule.listByTeacher(teacher)) {
                if (RecurringAvailabilityRule.overlaps(rule, existing)) {
                    throw new IllegalStateException("fixture availability_rules.csv: rule for '" + row[0].trim()
                            + "' overlaps an already-seeded rule (" + existing + ")");
                }
            }
            rule.persist();
        }
    }

    private static Teacher findByName(List<Teacher> teachers, String name) {
        Teacher t = findByNameOrNull(teachers, name);
        if (t == null) {
            throw new IllegalStateException("fixture: unknown teacher '" + name + "'");
        }
        return t;
    }

    private static Teacher findByNameOrNull(List<Teacher> teachers, String name) {
        return teachers.stream().filter(t -> t.name.equals(name)).findFirst().orElse(null);
    }

    private static StudentGroup findGroupByName(List<StudentGroup> groups, String name) {
        return groups.stream().filter(g -> g.name.equals(name)).findFirst().orElseThrow(() -> new IllegalStateException("unknown group in seed CSVs: " + name));
    }
}
