import { useEffect, useState, useCallback, useRef, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { format, parseISO } from 'date-fns';
import type { SchoolSchedule, Teacher, Lesson, ConstraintAnalysis, ValidationViolationDelta } from '../types/domain';
import * as api from '../api/scheduleApi';
import type { ScheduleStatusLight } from '../api/scheduleApi';
import { getErrorMessage } from '../utils/apiError';
import { hasPlacedLessons } from '../utils/scheduleState';
import { UnifiedSolverControls } from './UnifiedSolverControls';
import { ScheduleFilterBar } from './ScheduleFilterBar';
import ScheduleSidebar from './ScheduleSidebar';
import LessonTooltip, { getConstraintDescription } from './LessonTooltip';
import { useHiddenGroups } from '../hooks/useHiddenGroups';
import { useScheduleFilter } from '../hooks/useScheduleFilter';
import { useScheduleNavigation, weekMonday } from '../hooks/useScheduleNavigation';
import { useScheduleData, getColor } from '../hooks/useScheduleData';
import { useLessonViolations } from '../hooks/useLessonViolations';
import { useToast } from '../contexts/ToastContext';
import { notifyValidationRefresh } from '../utils/validationRefreshBus';
import {
  TimelineHeader,
  WeekViewGrid,
  TimelineView,
  type GroupByMode,
} from './timeline';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Viewport height offset for timeline/week view (header + nav + controls) */
const VIEWPORT_HEADER_OFFSET = 180;

// ---------------------------------------------------------------------------
// Helper: Compute total assigned hours for a lesson's course
// ---------------------------------------------------------------------------

/**
 * Compute total assigned hours for a lesson's course in its student group.
 * Counts all lessons with the same subject and student group.
 */
function computeTotalHours(schedule: SchoolSchedule, lesson: Lesson): number {
  const subject = lesson.course?.subject;
  const groupId = lesson.course?.studentGroup?.id;

  if (!subject || !groupId) return 0;

  // Count regular lessons
  const regularCount = schedule.lessons.filter(
    l => l.course?.subject === subject &&
         l.course?.studentGroup?.id === groupId &&
         l.timeslot // Only count assigned lessons
  ).length;

  return regularCount;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

// One snapshot shape + builder for the light status (the poll tick, the mount
// prime and the poll ref all share it): the next additive field costs one edit.
// Nullables because the ref starts empty before the first response.
type StatusSnapshot = {
  solverStatus: string | null;
  preAssignmentStatus: string | null;
  score: string | null;
  stagedStage: string | null;
  refiningForDelivery: boolean;
  stagedOutcome: string | null;
  scoreRestorePending: boolean;
};
const snapshotOf = (light: ScheduleStatusLight): StatusSnapshot => ({
  solverStatus: light.solverStatus,
  preAssignmentStatus: light.preAssignmentStatus,
  score: light.score,
  stagedStage: light.stagedStage,
  refiningForDelivery: light.refiningForDelivery,
  stagedOutcome: light.stagedOutcome ?? null,
  scoreRestorePending: light.scoreRestorePending ?? false,
});

export default function ScheduleTimeline() {
  const { t } = useTranslation('schedule');

  // ---- State ----
  const [schedule, setSchedule] = useState<SchoolSchedule | null>(null);
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [selectedLesson, setSelectedLesson] = useState<Lesson | null>(null);
  const [groupBy, setGroupBy] = useState<GroupByMode>('studentGroup');
  const [preAssignRequested, setPreAssignRequested] = useState(false);
  const [solveTrigger, setSolveTrigger] = useState(0); // Timestamp to trigger immediate poll
  const [sidebarAnalysis, setSidebarAnalysis] = useState<ConstraintAnalysis | null>(null);
  // TASK-664: first-load UX. initialLoading stays true until the first
  // fetchSchedule concludes (success or failure) so the page shows a grid
  // skeleton instead of silence; loadError surfaces a failed load with a
  // Retry action (the console.error stays).
  const [initialLoading, setInitialLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // ---- Sidebar collapse state ----
  // Always collapse on mobile (< 768px); only respect localStorage on desktop
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;
    if (isMobile) return true;
    const saved = localStorage.getItem('sidebar-collapsed');
    return saved === 'true';
  });
  const toggleSidebarCollapse = useCallback(() => {
    const newState = !sidebarCollapsed;
    setSidebarCollapsed(newState);
    localStorage.setItem('sidebar-collapsed', String(newState));
  }, [sidebarCollapsed]);

  // ---- Timeline height state (responsive to window resize) ----
  // Match Week view's max-h-[calc(100vh-VIEWPORT_HEADER_OFFSET)] for consistent viewport fill
  const [timelineHeight, setTimelineHeight] = useState(() =>
    typeof window !== 'undefined' ? window.innerHeight - VIEWPORT_HEADER_OFFSET : 600
  );

  useEffect(() => {
    const handleResize = () => {
      setTimelineHeight(prev => {
        const newHeight = window.innerHeight - VIEWPORT_HEADER_OFFSET;
        return prev !== newHeight ? newHeight : prev;
      });
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // ---- Hooks ----
  const { hiddenIds, hideGroup, showAll, isHidden } = useHiddenGroups();
  const {
    filterState,
    hasActiveFilters,
    toggleTeacher,
    toggleSubject,
    toggleGroup,
    removeTeacher,
    removeSubject,
    removeGroup,
    clearAll,
    activeFilterCount,
  } = useScheduleFilter();

  // ---- Data hook ----
  const {
    yearRange,
    allDays,
    holidayMap,
    stageDaysByGroup,
    idealEndDatesByGroup,
    columns,
    subColumnLabels,
    timePeriods,
    getLesson,
    slotUtilizationByName,
    isSlotOpenForGroup,
    slotSchemaError,
    groupCalendarError,
  } = useScheduleData(schedule, teachers, groupBy, filterState, hasActiveFilters, isHidden, schedule?.solverStatus);

  // Non-blocking data-load warning (N6): the grid still renders with fallback/fully-open
  // defaults on either fetch failure, but the user should know the data may be stale.
  const dataLoadError = slotSchemaError ?? groupCalendarError;

  // ---- Color getter ----
  const subjectColorMap = useRef(new Map<string, string>());
  const colorGetter = useCallback((subject: string, customColor?: string | null): string => {
    return getColor(subjectColorMap, subject, customColor);
  }, []);

  // ---- Navigation hook ----
  const navigation = useScheduleNavigation(yearRange, allDays);
  const {
    currentWeekStart,
    setCurrentWeekStart,
    viewMode,
    setViewMode,
    atStart,
    atEnd,
    weekLabel,
    days,
    goPrev,
    goNext,
    goToday,
    localizedDate,
    localizedDayName,
    jumpToDate,
  } = navigation;

  // ---- TASK-659: print target from the filter selection alone (altitude review:
  // the printable subject is a property of the selection, not of the view
  // grouping; one teacher selected prints that teacher whatever the grouping). ----
  const printHref = useMemo(() => {
    const week = format(currentWeekStart, 'yyyy-MM-dd');
    if (filterState.teachers.size === 1) {
      return `/print/docente/${[...filterState.teachers][0]}?week=${week}`;
    }
    if (filterState.groups.size === 1) {
      return `/print/classe/${[...filterState.groups][0]}?week=${week}`;
    }
    return null;
  }, [filterState.teachers, filterState.groups, currentWeekStart]);

  // ---- Compute date range for violation fetching ----
  // Only fetch violations for the visible week to reduce payload size
  const violationDateRange = useMemo(() => {
    if (viewMode === 'week' && days.length > 0) {
      return {
        start: format(days[0], 'yyyy-MM-dd'),
        end: format(days[days.length - 1], 'yyyy-MM-dd'),
      };
    }
    // For timeline/infinite view, fetch all (could optimize later with larger window)
    return undefined;
  }, [viewMode, days]);

  // ---- Violation highlighting hook ----
  const {
    violationMap,
    getViolations,
    violationCount,
    firstViolation,
    refetch: refetchViolations,
    clear: clearViolations,
  } = useLessonViolations({
    solverStatus: schedule?.solverStatus,
    // TASK-525 finding UI: during a staged cycle the DB is a partial persist
    // (TASK-509), so violation contours would describe a non-delivered state.
    // Disable the fetch until the cycle is truly over (stagedStage null).
    enabled: schedule?.stagedStage == null,
    dateRange: violationDateRange,
  });

  // ---- Reset callback to clear all UI state ----
  const handleResetComplete = useCallback(() => {
    setSidebarAnalysis(null);  // Clear sidebar analysis
    clearViolations();         // Clear violation highlighting
  }, [clearViolations]);

  // ---- Auto-scroll to first violation when solver stops ----
  useEffect(() => {
    // Only auto-scroll when solver just stopped (has violations now, didn't before)
    if (!firstViolation || !firstViolation.timeslot) return;

    // Parse date from timeslot string (ISO 8601 format: "2025-12-03T08:00")
    const violationDate = new Date(firstViolation.timeslot);

    // Check if violation is outside current view
    const violationWeekStart = weekMonday(violationDate);
    const weeksDiff = Math.abs((violationWeekStart.getTime() - currentWeekStart.getTime()) / (7 * 24 * 60 * 60 * 1000));

    // Only jump if violation is more than 1 week away or in a different week
    if (weeksDiff >= 1) {
      jumpToDate(violationDate);
    }
  }, [firstViolation, currentWeekStart, jumpToDate]);

  // ---- Year range label ----
  const yearRangeLabel = yearRange
    ? `${localizedDate(yearRange.start)} \u2013 ${localizedDate(yearRange.end)}`
    : null;

  // ---- Data fetching ----

  // Pre-assign start fires two independent GET /schedule calls close together
  // (the immediate-fetch effect below, and UnifiedSolverControls's onRefresh
  // after the POST resolves). With ~10k lessons the response can take long
  // enough, and vary enough under solver load, that they resolve out of
  // order - a fetch dispatched first can land after a later one and stomp a
  // fresh COMPLETE snapshot with a stale one. Only the latest dispatched
  // call's response is ever applied.
  const fetchSeqRef = useRef(0);
  // Last light-status snapshot (TASK-380) - drives change-detection for the heavy fetch.
  const statusPollRef = useRef<StatusSnapshot>({
    solverStatus: null, preAssignmentStatus: null, score: null, stagedStage: null, refiningForDelivery: false, stagedOutcome: null, scoreRestorePending: false,
  });
  // Ticks spent polling ONLY for the async score restore (TASK-658); gives the
  // poll a deadline instead of letting it run forever on a restore that never lands.
  const awaitingScoreRestoreTicksRef = useRef(0);
  // TASK-661: the delivery-refinement window opens AFTER stagedStage goes null
  // (the wrapper publishes the stage end, then arms the refinement corner): a poll
  // that dies on the null transition never sees refining=true. Keep polling for a
  // grace window after every staged end, long enough for the corner to open.
  const stagedEndGraceTicksRef = useRef(0);
  const fetchSchedule = useCallback(async () => {
    const seq = ++fetchSeqRef.current;
    try {
      const data = await api.getSchedule();
      if (seq === fetchSeqRef.current) {
        // TASK-661/667: refiningForDelivery, stagedOutcome and scoreRestorePending
        // are published ONLY by the light status payload (GET /api/schedule/status);
        // the heavy GET /api/schedule omits them, so a plain setSchedule(data) wipes
        // flags the light poll (or the mount prime) just set: the outcome banner
        // and the restore poll would drop until the next tick. Preserve the
        // previously observed values; when the heavy fetch WINS the mount race
        // (schedule still null), fold in whatever the mount prime already saw.
        const foldCycleFlags = (s: SchoolSchedule): SchoolSchedule => ({
          ...s,
          refiningForDelivery: s.refiningForDelivery ?? statusPollRef.current.refiningForDelivery,
          stagedOutcome: s.stagedOutcome ?? statusPollRef.current.stagedOutcome,
          scoreRestorePending: s.scoreRestorePending ?? statusPollRef.current.scoreRestorePending,
        });
        setSchedule(prev => prev
          ? { ...data, refiningForDelivery: prev.refiningForDelivery ?? false, stagedOutcome: prev.stagedOutcome ?? null, scoreRestorePending: prev.scoreRestorePending ?? false }
          : foldCycleFlags(data));
        setLoadError(null);
      }
    } catch (e) {
      if (seq === fetchSeqRef.current) {
        console.error('Failed to fetch schedule:', getErrorMessage(e));
        setLoadError(getErrorMessage(e));
      }
    } finally {
      setInitialLoading(false);
    }
  }, []);

  // TASK-664: retry button on the load-error banner clears the message and
  // re-runs the fetch.
  const handleRetryLoad = useCallback(() => {
    setLoadError(null);
    fetchSchedule();
  }, [fetchSchedule]);

  const fetchAnalysis = useCallback(async () => {
    try {
      const data = await api.analyzeSchedule();
      setSidebarAnalysis(data);
    } catch (e) {
      console.error('Failed to fetch analysis:', e);
    }
  }, []);

  // ---- Manual placement (TASK-357): drag-drop + LessonTooltip's Move panel ----
  const { addToast } = useToast();
  const [draggingLesson, setDraggingLesson] = useState<Lesson | null>(null);

  const handleLessonDragStart = useCallback((lesson: Lesson) => setDraggingLesson(lesson), []);
  const handleLessonDragEnd = useCallback(() => setDraggingLesson(null), []);

  // Latest-value ref (not a dependency): handleManualMove only needs `schedule` to read
  // one lesson's current `pinned` flag, a cheap one-time check - not a reason to give the
  // callback a new identity on every poll tick, which would otherwise cascade into
  // TimelineView's context memo re-running for every visible virtualized row.
  const scheduleRef = useRef(schedule);
  useEffect(() => { scheduleRef.current = schedule; }, [schedule]);

  /**
   * Shared by drag-drop and the Move panel: assigns the lesson to its new
   * timeslot/room, optionally pins it (school desiderata: a hand-placed hour
   * becomes "intoccabile" by default for both entry points), then re-fetches
   * the schedule and kicks off the watchdog's instant re-validation. Throws
   * on assign/pin failure so both callers can react (LessonTooltip keeps its
   * panel open; both callers show an error toast). fetchSchedule and
   * runValidation are independent (both only depend on the already-applied
   * assign/pin, not on each other) and run concurrently rather than
   * back-to-back; fetchSchedule never itself rejects (see its own
   * try/catch), so a runValidation failure is soft-failed (logged only)
   * without losing the schedule refresh.
   */
  const handleManualMove = useCallback(async (
    lessonId: number, timeslotId: number, roomId: number | null, pinAfterMove = true
  ) => {
    await api.assignLesson(lessonId, timeslotId, roomId);
    if (pinAfterMove) {
      const current = scheduleRef.current?.lessons.find(l => l.id === lessonId);
      if (current && !current.pinned) {
        await api.togglePin(lessonId);
      }
    }
    refetchViolations();
    try {
      const [, result] = await Promise.all([fetchSchedule(), api.runValidation()]);
      notifyValidationRefresh();
      const newHard = result.newlyViolated.filter((d: ValidationViolationDelta) => d.hard < 0);
      if (!result.baseline && newHard.length > 0) {
        const details = newHard
          .map(d => `${d.constraintId} ${getConstraintDescription(d.constraintId)} (+${d.delta})`)
          .join(', ');
        addToast(t('validation:moveIntroducedViolations', { details }), 'warning', 8000);
      }
    } catch (e) {
      console.error('Post-move validation run failed:', getErrorMessage(e));
    }
  }, [fetchSchedule, refetchViolations, addToast, t]);

  const handleLessonDrop = useCallback(async (lesson: Lesson, timeslotId: number) => {
    setDraggingLesson(null);
    if (lesson.pinned && !window.confirm(t('validation:confirmMovePinned'))) return;
    try {
      await handleManualMove(lesson.id, timeslotId, lesson.room?.id ?? null);
    } catch (e) {
      addToast(t('toast.moveError', { message: getErrorMessage(e) }), 'error', 6000);
    }
  }, [handleManualMove, t, addToast]);

  const handleTooltipMove = useCallback(async (timeslotId: number, roomId: number | null, pinAfterMove: boolean) => {
    if (!selectedLesson) throw new Error('No lesson selected');
    try {
      await handleManualMove(selectedLesson.id, timeslotId, roomId, pinAfterMove);
    } catch (e) {
      addToast(t('toast.moveError', { message: getErrorMessage(e) }), 'error', 6000);
      throw e; // re-throw: LessonTooltip's handleMoveClick must NOT close the panel on failure
    }
  }, [selectedLesson, handleManualMove, addToast, t]);

  useEffect(() => {
    fetchSchedule();
    api.getTeachers().then(setTeachers).catch(e => {
      console.error('Failed to fetch teachers:', getErrorMessage(e));
    });
    // TASK-661: prime the cycle flags the heavy payload does not carry. A page
    // opened mid-cycle (or during the delivery refinement) would otherwise start
    // with stagedStage/refiningForDelivery unknown, no poll running, and the
    // staged banner / Stop button missing until some other event fired.
    api.getScheduleStatus().then(light => {
      statusPollRef.current = snapshotOf(light);
      setSchedule(prev => prev ? {
        ...prev,
        stagedStage: light.stagedStage,
        refiningForDelivery: light.refiningForDelivery ?? false,
        stagedOutcome: light.stagedOutcome ?? null,
        scoreRestorePending: light.scoreRestorePending ?? false,
      } : prev);
    }).catch(() => { /* the heavy fetch carries the rest; cycle flags re-prime on the next poll */ });
  }, [fetchSchedule]);

  // ---- Polling while solving or pre-assigning ----

  useEffect(() => {
    // Poll during solver execution OR pre-assignment
    const isSolving = schedule?.solverStatus === 'SOLVING_ACTIVE';
    const isPreAssigning = schedule?.preAssignmentStatus &&
      !['IDLE', 'COMPLETE', 'ERROR'].includes(schedule.preAssignmentStatus);
    // TASK-419: a staged solve cycle runs every stage on its own ad-hoc solver,
    // so solverStatus reports NOT_SOLVING for the whole cycle - stagedStage is
    // the only signal that a cycle is still in progress between stages, so it
    // must keep the poll alive on its own, independent of isSolving.
    const isStagedCycle = schedule?.stagedStage != null;
    // TASK-661: the final delivery refinement runs AFTER stagedStage goes null, so the
    // poll must stay alive on the refining flag alone to observe its false transition
    // (which triggers the heavy re-fetch of the saved delivery below).
    const isRefining = schedule?.refiningForDelivery ?? false;
    // TASK-658: from idle (no solve/pre-assign/staged) nothing polls, yet the
    // backend's async score restore fills latestScore ~10-20s after the first
    // read of a placed delivery (restart or import). Keep the light poll alive
    // until that null->score transition arrives; the change-detection below
    // then re-fetches the schedule and the grid populates without any click.
    // TASK-667: the server now DECLARES the restore in flight
    // (scoreRestorePending, false even when the recompute dies); the client
    // inference below stays only as IGNITION on the very first load, before
    // any status response has arrived (flag undefined).
    const restorePending = schedule?.scoreRestorePending;
    const awaitingScoreRestore = restorePending === true
      || (restorePending === undefined && schedule?.score == null && hasPlacedLessons(schedule));
    const stagedEndGrace = stagedEndGraceTicksRef.current > 0;
    const shouldPoll = isSolving || isPreAssigning || isStagedCycle || isRefining || stagedEndGrace || preAssignRequested || solveTrigger > 0 || awaitingScoreRestore;
    // True when awaitingScoreRestore is among the active signals WITHOUT the strong
    // ones (solve/pre-assign/stage/refining): in that mode the poll runs under its
    // own deadline (see the tick counter below) instead of waiting forever on a
    // restore that never lands.
    const awaitingScoreRestoreOnly = awaitingScoreRestore
      && !isSolving && !isPreAssigning && !isStagedCycle && !isRefining
      && !preAssignRequested && solveTrigger <= 0;
    // TASK-668 (finding 5): the staged-end grace counts down EVEN WHEN the restore
    // wait is active (the post-Stop case: score null + cycle just ended is exactly
    // the case the grace was born for). The two deadlines coexist: the grace (90s)
    // only closes the poll when it is the last signal standing - if the restore
    // deadline is still counting, the poll survives under that one.
    const stagedEndGraceOnly = stagedEndGrace
      && !isSolving && !isPreAssigning && !isStagedCycle && !isRefining
      && !preAssignRequested && solveTrigger <= 0;
    // Reset the deadline counter whenever a strong signal drives the poll.
    if (!awaitingScoreRestoreOnly) awaitingScoreRestoreTicksRef.current = 0;
    if (!stagedEndGraceOnly) stagedEndGraceTicksRef.current = 0;

    if (!shouldPoll) return;

    // TASK-380: poll the LIGHT status endpoint (<100ms, no lessons/DB) and
    // merge it into the schedule state so buttons/toasts react within a poll
    // cycle; the heavy 16MB GET /api/schedule (~9s on the full dataset) runs
    // only when the status actually changes. The old design polled the heavy
    // endpoint at 500ms/2s: requests piled up, responses were discarded as
    // stale, and fast transitions (the zero-rotation pre-assign no-op) were
    // never observed — the root cause of the TASK-367 toast failures.
    const pollInterval = 1000;

    // Immediate fetch when starting an operation
    const needsImmediateFetch = preAssignRequested || solveTrigger > 0;
    if (needsImmediateFetch) {
      fetchSchedule(); // Fetch immediately, then start polling
      // Clear the trigger after immediate fetch
      if (solveTrigger > 0) setSolveTrigger(0);
    }

    const pollStatus = async () => {
      try {
        const light = await api.getScheduleStatus();
        // Previous refining value, read BEFORE the ref update: the refining
        // false->true edge must NOT trigger the heavy fetch below (the
        // refinement window just opened, nothing is saved to fetch yet); the
        // true->false edge (delivery saved) still does.
        const prevRefining = statusPollRef.current.refiningForDelivery;
        // Arm the grace window on the staged END transition (the refinement corner
        // opens after this null): see stagedEndGraceTicksRef above.
        if (light.stagedStage == null && statusPollRef.current.stagedStage != null) {
          stagedEndGraceTicksRef.current = 90;
        }
        if (light.stagedStage != null) {
          stagedEndGraceTicksRef.current = 0;
        }
        const prevSnap = statusPollRef.current;
        const nextSnap = snapshotOf(light);
        const coreChanged =
          nextSnap.solverStatus !== prevSnap.solverStatus ||
          nextSnap.preAssignmentStatus !== prevSnap.preAssignmentStatus ||
          nextSnap.score !== prevSnap.score ||
          nextSnap.stagedStage !== prevSnap.stagedStage ||
          nextSnap.refiningForDelivery !== prevSnap.refiningForDelivery ||
          nextSnap.stagedOutcome !== prevSnap.stagedOutcome;
        const restoreFlagChanged = nextSnap.scoreRestorePending !== prevSnap.scoreRestorePending;
        const changed = coreChanged || restoreFlagChanged;
        statusPollRef.current = nextSnap;
        // Merge into schedule state only on a real change: a no-op tick must
        // not invalidate the schedule object identity (every merge rebuilds
        // the lookup maps over the full lesson set downstream).
        if (changed) {
          setSchedule(prev => prev ? {
            ...prev,
            solverStatus: light.solverStatus as SchoolSchedule['solverStatus'],
            score: light.score ?? prev.score,
            preAssignmentStatus: light.preAssignmentStatus as SchoolSchedule['preAssignmentStatus'],
            preAssignmentError: light.preAssignmentError,
            lastSolverError: light.lastSolverError,
            currentPhase: light.currentPhase,
            stagedStage: light.stagedStage,
            refiningForDelivery: light.refiningForDelivery ?? false,
            stagedOutcome: light.stagedOutcome ?? null,
            scoreRestorePending: light.scoreRestorePending ?? false,
          } : prev);
          // The restore flag alone never changes the grid (its edges must not
          // pay the ~9s heavy fetch): the restore's own completion arrives with
          // a SCORE change, which is coreChanged and fetch-worthy.
          const onlyRestoreFlagChanged = restoreFlagChanged && !coreChanged;
          if (!(light.refiningForDelivery && !prevRefining) && !onlyRestoreFlagChanged) fetchSchedule();
        }
        // Deadline for the restore-only poll: 600 ticks = 10 minutes at the 1s
        // interval (TASK-661). The restore is documented at ~10-20s, but under
        // solver load it can slip well past that; give up only after 10 minutes,
        // when the "not yet scored" state is honest rather than impatient.
        if (awaitingScoreRestoreOnly) {
          awaitingScoreRestoreTicksRef.current += 1;
          if (awaitingScoreRestoreTicksRef.current >= 600) {
            clearInterval(interval);
            return;
          }
        }
        if (stagedEndGraceOnly) {
          stagedEndGraceTicksRef.current -= 1;
          // TASK-668 (finding 5): expiring grace closes the poll only when the
          // restore wait is NOT still counting under its own longer deadline.
          if (stagedEndGraceTicksRef.current <= 0 && !awaitingScoreRestoreOnly) {
            clearInterval(interval);
            return;
          }
        }
      } catch (e) {
        console.error('Light status poll failed:', getErrorMessage(e));
      }
    };
    const interval = setInterval(pollStatus, pollInterval);

    let timeout: ReturnType<typeof setTimeout> | undefined;

    // Clear preAssignRequested after 10s if pre-assign doesn't start
    if (preAssignRequested && !isPreAssigning) {
      timeout = setTimeout(() => setPreAssignRequested(false), 10000);
    }
    // Clear preAssignRequested once pre-assign is active
    if (preAssignRequested && isPreAssigning) {
      setPreAssignRequested(false);
    }

    return () => {
      clearInterval(interval);
      if (timeout) clearTimeout(timeout);
    };
  }, [schedule?.solverStatus, schedule?.preAssignmentStatus, schedule?.stagedStage, schedule?.refiningForDelivery, schedule?.score, preAssignRequested, solveTrigger, fetchSchedule]);

  // ---- Fetch analysis when solver stops ----

  useEffect(() => {
    if (schedule && schedule.solverStatus === 'NOT_SOLVING') {
      fetchAnalysis();
    }
  }, [schedule?.solverStatus, fetchAnalysis]);

  // ---- Cell click handler ----

  const handleCellClick = useCallback((lesson: Lesson) => {
    setSelectedLesson(lesson);
  }, []);

  // ---- Stable sidebar callbacks (prevents ScheduleSidebar re-render on view switch) ----

  const handleSolve = useCallback(() => api.startSolving(), []);
  const handleStop = useCallback(() => api.stopSolving(), []);
  const handleReset = useCallback(() => {
    // TASK-388: same destructive-reset confirmation as the controls bar (this is the
    // sidebar's Reset, the second live control): the 2026-07-20 incident was a stray
    // click on exactly this class of control. Namespace-prefixed key so it resolves
    // regardless of this component's translation namespaces.
    if (!window.confirm(t('schedule:confirmReset'))) return Promise.resolve();
    return api.resetSchedule().then(() => fetchSchedule());
  }, [fetchSchedule]);
  const handlePreAssign = useCallback(() => api.preAssign().then(() => fetchSchedule()), [fetchSchedule]);
  const handleAnalyze = useCallback(() => fetchAnalysis(), [fetchAnalysis]);

  // ---- Render ----

  return (
    <div className="flex flex-col gap-4">
      <UnifiedSolverControls
        schedule={schedule}
        teachers={teachers}
        onRefresh={fetchSchedule}
        onSolvingRequested={() => setSolveTrigger(Date.now())}
        onPreAssignRequested={() => setPreAssignRequested(true)}
        onResetComplete={handleResetComplete}
      />

      <ScheduleFilterBar
        teachers={teachers}
        studentGroups={schedule?.studentGroups || []}
        lessons={schedule?.lessons || []}
        filterState={filterState}
        onToggleTeacher={toggleTeacher}
        onToggleSubject={toggleSubject}
        onToggleGroup={toggleGroup}
        onRemoveTeacher={removeTeacher}
        onRemoveSubject={removeSubject}
        onRemoveGroup={removeGroup}
        onClearAll={clearAll}
        hasActiveFilters={hasActiveFilters}
        activeFilterCount={activeFilterCount}
      />

      {dataLoadError && (
        <div className="p-2 bg-feedback-error-bg rounded text-sm text-feedback-error-text">
          {t('dataLoadWarning', { message: dataLoadError })}
        </div>
      )}

      {/* TASK-664: visible load error with retry (fetchSchedule logs to console too) */}
      {loadError && (
        <div
          role="alert"
          className="flex items-center justify-between gap-4 p-3 border border-feedback-error-border bg-feedback-error-bg rounded text-sm text-feedback-error-text"
        >
          <span>{t('loadFailed', { message: loadError })}</span>
          <button
            type="button"
            onClick={handleRetryLoad}
            className="px-3 py-1 border border-feedback-error-border rounded text-sm text-feedback-error-text hover:bg-feedback-error-border/20 cursor-pointer flex-shrink-0"
          >
            {t('retryLoad')}
          </button>
        </div>
      )}

      {/* Toolbar: week nav + group-by toggle */}
      <TimelineHeader
        viewMode={viewMode}
        setViewMode={setViewMode}
        groupBy={groupBy}
        setGroupBy={setGroupBy}
        currentWeekStart={currentWeekStart}
        weekLabel={weekLabel}
        atStart={atStart}
        atEnd={atEnd}
        goPrev={goPrev}
        goNext={goNext}
        goToday={goToday}
        yearRangeLabel={yearRangeLabel}
        hiddenCount={groupBy === 'studentGroup' ? hiddenIds.size : 0}
        onShowAll={showAll}
        allDaysCount={allDays.length}
        yearRange={yearRange}
        onJumpToDate={jumpToDate}
        printHref={printHref}
        printHint={t('printHint')}
      />

      {/* Mobile sidebar toggle (only when collapsed on mobile) */}
      {sidebarCollapsed && schedule && (
        <button
          className="md:hidden flex items-center gap-2 px-3 py-2 bg-surface border border-border rounded-lg text-sm text-muted hover:text-content cursor-pointer"
          onClick={toggleSidebarCollapse}
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          {t('sidebar.stats')}
        </button>
      )}

      {/* TASK-664: first-load skeleton instead of a blank page while the
          initial (potentially large) schedule fetch is in flight */}
      {initialLoading && (
        <div className="flex-1 flex flex-col gap-2 animate-pulse py-4" aria-hidden="true">
          <div className="h-10 rounded bg-surface border border-border" />
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-12 rounded bg-surface border border-border" />
          ))}
          <div className="text-sm text-muted self-center">{t('loadingSchedule')}</div>
        </div>
      )}

      {/* Main body: grid + sidebar, stack on mobile */}
      {!initialLoading && (
      <div className="flex flex-col md:flex-row gap-4">
        {/* Week view */}
        {viewMode === 'week' && schedule && (
          <WeekViewGrid
            schedule={schedule}
            timePeriods={timePeriods}
            days={days}
            columns={columns}
            holidayMap={holidayMap}
            stageDaysByGroup={stageDaysByGroup}
            idealEndDatesByGroup={idealEndDatesByGroup}
            completionDatesByGroup={new Map()}
            groupBy={groupBy}
            subColumnLabels={subColumnLabels}
            getLesson={getLesson}
            getColor={colorGetter}
            localizedDayName={localizedDayName}
            onCellClick={handleCellClick}
            violationMap={violationMap}
            slotUtilizationByName={slotUtilizationByName}
            isSlotOpenForGroup={isSlotOpenForGroup}
            draggingLesson={draggingLesson}
            onLessonDragStart={handleLessonDragStart}
            onLessonDragEnd={handleLessonDragEnd}
            onLessonDrop={handleLessonDrop}
          />
        )}

        {/* Infinite timeline view */}
        {viewMode === 'infinite' && schedule && (
          <TimelineView
            allDays={allDays}
            holidayMap={holidayMap}
            timePeriods={timePeriods}
            columns={columns}
            groupBy={groupBy}
            stageDaysByGroup={stageDaysByGroup}
            idealEndDatesByGroup={idealEndDatesByGroup}
            completionDatesByGroup={new Map()}
            schedule={schedule}
            getLesson={getLesson}
            getColor={colorGetter}
            localizedDayName={localizedDayName}
            onCellClick={handleCellClick}
            height={timelineHeight}
            violationMap={violationMap}
            slotUtilizationByName={slotUtilizationByName}
            isSlotOpenForGroup={isSlotOpenForGroup}
            draggingLesson={draggingLesson}
            onLessonDragStart={handleLessonDragStart}
            onLessonDragEnd={handleLessonDragEnd}
            onLessonDrop={handleLessonDrop}
          />
        )}

        {schedule && (
          <div className={`${sidebarCollapsed ? 'hidden md:block md:w-12' : 'w-full md:w-sidebar'} flex-shrink-0 transition-all duration-200 order-first md:order-last`}>
            <ScheduleSidebar
              schedule={schedule}
              onSolve={handleSolve}
              onStop={handleStop}
              onReset={handleReset}
              onPreAssign={handlePreAssign}
              onAnalyze={handleAnalyze}
              onRefresh={fetchSchedule}
              analysis={sidebarAnalysis}
              collapsed={sidebarCollapsed}
              onToggleCollapse={toggleSidebarCollapse}
              onLessonDragStart={handleLessonDragStart}
              onLessonDragEnd={handleLessonDragEnd}
              dragDropEnabled={groupBy === 'studentGroup'}
            />
          </div>
        )}
      </div>
      )}

      {/* Lesson detail modal */}
      {selectedLesson && schedule && (
        <LessonTooltip
          lesson={selectedLesson}
          onClose={() => setSelectedLesson(null)}
          onUpdate={fetchSchedule}
          subjectColor={colorGetter(selectedLesson.course?.subject || '?')}
          totalHours={computeTotalHours(schedule, selectedLesson)}
          violations={getViolations(selectedLesson.id)}
          timeslots={schedule.timeslots}
          timePeriods={timePeriods}
          rooms={schedule.rooms}
          isSlotOpenForGroup={isSlotOpenForGroup}
          onMove={handleTooltipMove}
          yearRange={yearRange}
          onNavigateTeacher={(teacherId) => {
            setGroupBy('teacher');
            setSelectedLesson(null);
          }}
          onNavigateGroup={(groupId) => {
            setGroupBy('studentGroup');
            showAll(); // Ensure all groups are visible
            setSelectedLesson(null);
          }}
          onNavigateRoom={(roomId) => {
            setGroupBy('room');
            setSelectedLesson(null);
          }}
          onNavigateDate={(date) => {
            setViewMode('week');
            setCurrentWeekStart(weekMonday(date));
            setSelectedLesson(null);
          }}
        />
      )}
    </div>
  );
}
