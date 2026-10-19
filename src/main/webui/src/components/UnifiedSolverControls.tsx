import { useState, useEffect, useRef, useCallback, ChangeEvent } from 'react';
import type { ComponentProps } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import type { SchoolSchedule, Teacher, ConstraintAnalysis, PreAssignmentResult, FeasibilityReport, PhaseRecord, SolverTimeoutConfig } from '../types/domain';
import * as api from '../api/scheduleApi';
import AnalysisPanel from './AnalysisPanel';
import ProgressionPanel from './ProgressionPanel';
import { formatPreAssignResult } from '../utils/formatPreAssignResult';
import { getErrorMessage } from '../utils/apiError';
import { hasPlacedLessons } from '../utils/scheduleState';
import { parseScoreParts } from '../utils';
import { ActionButton } from './ui/ActionButton';
import { StatusBadge } from './ui/StatusBadge';
import { Icons } from './ui/Icons';
import { PreAssignErrorNotification } from './PreAssignErrorNotification';
import { useToast, ToastType } from '../contexts/ToastContext';

interface Props {
  schedule: SchoolSchedule | null;
  teachers: Teacher[];
  onRefresh: () => void;
  onSolvingRequested?: () => void;
  onPreAssignRequested?: () => void;
  onResetComplete?: () => void;
}

export function UnifiedSolverControls({ schedule, teachers, onRefresh, onSolvingRequested, onPreAssignRequested, onResetComplete }: Props) {
  const { t } = useTranslation(['schedule', 'common']);
  const { addToast } = useToast();

  // Helper to reduce repetitive toast pattern
  const showToast = useCallback((key: string, type: ToastType, duration?: number, vars?: Record<string, unknown>) => {
    addToast(t(`toast.${key}`, vars), type, duration);
  }, [addToast, t]);

  const [showModal, setShowModal] = useState(false);
  const [selectedTeacherId, setSelectedTeacherId] = useState<number | null>(null);
  const [selectedDate, setSelectedDate] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [preAssignLoading, setPreAssignLoading] = useState<string | null>(null);
  const [preAssignRequested, setPreAssignRequested] = useState(false); // Optimistic UI state for pre-assign start time
  const [preAssignRequestTime, setPreAssignRequestTime] = useState<number | null>(null); // Local start time

  const [preAssignResult, setPreAssignResult] = useState<{ summary: string; details: string; fullReport: string } | null>(null);
  const [showDetails, setShowDetails] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [analysis, setAnalysis] = useState<ConstraintAnalysis | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [feasibility, setFeasibility] = useState<FeasibilityReport | null>(null);
  const [isFetchingFeasibility, setIsFetchingFeasibility] = useState(false);

  // TASK-660: the backend sends a stable code, localized via the
  // feasibilityMessage block in schedule.json (the authoritative code list);
  // an unknown code (payload from another build) passes through raw.
  const feasibilityLabel = () =>
    t(`feasibilityMessage.${feasibility?.message ?? ''}`, {
      defaultValue: feasibility?.message ?? '',
      count: Math.abs(feasibility?.hard ?? 0),
    });

  // Phase progression modal state
  const [showProgression, setShowProgression] = useState(false);
  const [phases, setPhases] = useState<PhaseRecord[]>([]);
  const [timeoutConfig, setTimeoutConfig] = useState<SolverTimeoutConfig | null>(null);

  // Track error context (phase, candidates) when pre-assignment fails
  const [lastErrorPhase, setLastErrorPhase] = useState<string | null>(null);
  const [lastCandidatesTried, setLastCandidatesTried] = useState(0);

  const copyFullReport = async () => {
    if (!preAssignResult) return;
    try {
      await navigator.clipboard.writeText(preAssignResult.fullReport);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (e) {
      console.error('Failed to copy:', e);
    }
  };

  // Solving state derived purely from backend
  const isSolving = schedule?.solverStatus === 'SOLVING_ACTIVE';
  // TASK-419: non-null while a staged solve cycle (construction -> promotion ->
  // timeslot solve -> demotion -> release -> room phases) is running. Every
  // stage runs on its own ad-hoc solver, so solverStatus stays NOT_SOLVING for
  // the entire cycle - stagedStage is the only signal that the grid shown is
  // an INTERMEDIATE checkpoint (stage pins, provisional rooms, inflated
  // H16b/H17), not a finished solve.
  const stagedStage = schedule?.stagedStage ?? null;
  // TASK-661: the delivery refinement runs AFTER the last staged stage ends (stagedStage
  // already null, solverStatus NOT_SOLVING for the whole staged protocol). A cycle is
  // "busy" for control purposes when any of the three signals is live.
  const isRefining = schedule?.refiningForDelivery ?? false;
  const isBusyCycle = isSolving || stagedStage != null || isRefining;
  // TASK-483: multi-start attempt X-of-Y (0/0 when off). When maxAttempts > 1 the banner adds a
  // "Tentativo X di Y" chip so the user sees the retry without knowing what a basin is.
  const stagedAttempt = schedule?.stagedAttempt ?? 0;
  const stagedMaxAttempts = schedule?.stagedMaxAttempts ?? 0;
  const stagedBudgetSeconds = schedule?.stagedBudgetSeconds ?? 0;
  const chCompletionTime = schedule?.chCompletionTime ?? null;
  const isInCH = isSolving && !chCompletionTime;
  const isInLS = isSolving && !!chCompletionTime;

  // Pre-assignment status detection
  const preAssignStatus = schedule?.preAssignmentStatus ?? null;
  const isServerPreAssigning = !!(preAssignStatus &&
    !['IDLE', 'COMPLETE', 'ERROR'].includes(preAssignStatus));
  // Show pre-assigning state optimistically as soon as button is clicked
  const isPreAssigning = isServerPreAssigning || preAssignRequested;

  // Pre-assign button disabled state logic
  const lessons = schedule?.lessons ?? [];
  const hasLessons = lessons.length > 0;


  // Compute disabled state and reason
  const getPreAssignDisabledReason = (): string | null => {
    if (isSolving) return t('preAssignDisabled.solving');
    if (!hasLessons) return t('preAssignDisabled.noLessons');
    // Check server status BEFORE optimistic state to prevent stale UI
    if (preAssignStatus === 'COMPLETE') return t('preAssignDisabled.completeUntilReset');
    if (isPreAssigning) return t('preAssignDisabled.inProgress');
    if (preAssignStatus === 'ERROR') return null;  // Allow retry after error
    return null;
  };

  const preAssignDisabledReason = getPreAssignDisabledReason();
  const canPreAssign = !preAssignDisabledReason && !preAssignLoading;

  // Reset should only be blocked by active solving/pre-assigning (not by recentlyCompleted).
  // TASK-661: also disabled mid-staged-cycle (stagedStage non-null) and during the
  // delivery refinement (isRefining): a reset under a running cycle would race the
  // stages / the refinement writing to the same schedule.
  const canReset = !isSolving && !isPreAssigning && !preAssignLoading && stagedStage == null && !isRefining;

  /**
   * Map phase number (1-5, TASK-420) to StatusBadge status type.
   * Uses phaseName from backend when available, falls back to CH/LS detection.
   */
  const getPhaseStatus = (phase: number, isInCH: boolean): 'idle' | 'ch' | 'ls' | 'phase1' | 'phase2' | 'phase3' | 'phase4' | 'phase5' => {
    if (phase >= 1 && phase <= 5) {
      return `phase${phase}` as 'phase1' | 'phase2' | 'phase3' | 'phase4' | 'phase5';
    }
    // Fallback to binary CH/LS detection for backward compatibility
    return isInCH ? 'ch' : 'ls';
  };

  /**
   * Get human-readable phase label with i18n support.
   */
  const getPhaseLabel = (
    phase: number,
    phaseName: string | null,
    isInCH: boolean,
    t: (key: string, options?: { defaultValue?: string }) => string
  ): string => {
    // Use phaseName from backend if available and meaningful
    if (phaseName && phaseName !== 'Not running') {
      // Try to translate using phase name key (e.g., "phases.Lab Block Placement")
      const translated = t(`phases.${phaseName}`, { defaultValue: phaseName });
      return translated;
    }

    // Fallback to binary CH/LS detection for backward compatibility
    return isInCH ? t('constructionHeuristic') : t('localSearch');
  };

  /**
   * Consolidated status+label for the top-right StatusBadge. Single source
   * of truth for the priority order (staged cycle > pre-assigning > solving
   * > idle), computed once instead of two parallel 4-deep ternary chains.
   */
  const getStatusBadgeProps = (): { status: ComponentProps<typeof StatusBadge>['status']; label: string } => {
    if (stagedStage) {
      return { status: 'staged', label: t('stagedCycle.badge', { stage: stagedStage }) };
    }
    if (isPreAssigning) {
      return {
        status: 'pre-assign',
        label: `Pre-assigning: ${preAssignStatus?.toLowerCase() || (preAssignRequested ? 'starting...' : '...')}`,
      };
    }
    if (isSolving) {
      return {
        status: getPhaseStatus(schedule?.currentPhase ?? 0, isInCH),
        label: getPhaseLabel(schedule?.currentPhase ?? 0, schedule?.phaseName ?? null, isInCH, t),
      };
    }
    return { status: 'idle', label: t('notSolving') };
  };

  // Determine which start time to use for elapsed display
  // For pre-assign, use local request time optimistically until server responds
  const preAssignStartTime = schedule?.preAssignmentStartTime ?? null;
  const effectivePreAssignStart = preAssignRequested && !preAssignStartTime && preAssignRequestTime
    ? new Date(preAssignRequestTime).toISOString()
    : preAssignStartTime;

  const serverStartTime = isPreAssigning
    ? effectivePreAssignStart ?? null
    : (isSolving ? schedule?.solveStartTime ?? null : null);

  const handlePreAssign = async (label: string, apiFn: () => Promise<PreAssignmentResult>) => {
    setPreAssignLoading(label);
    setPreAssignRequested(true);
    setPreAssignRequestTime(Date.now());
    onPreAssignRequested?.(); // Trigger parent polling
    setPreAssignResult(null);
    setShowDetails(false);
    setError(null);
    // Clear previous error context when starting new attempt
    setLastErrorPhase(null);
    setLastCandidatesTried(0);
    // Show immediate feedback that operation started
    addToast(t('toast.preAssignStarted'), 'info', 3000);
    try {
      const result = await apiFn();
      setPreAssignResult(formatPreAssignResult(result));
      // Don't show completion toast here - the operation is async and runs in background
      // The polling mechanism will update the UI with completion status
      onRefresh();
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Pre-assignment failed');
      setError(errorMsg);
      addToast(t('toast.preAssignError'), 'error', 6000);
      // Clear optimistic state on error
      setPreAssignRequested(false);
      setPreAssignRequestTime(null);
    } finally {
      setPreAssignLoading(null);
    }
  };

  const formatNumber = (n: number): string => {
    const abs = Math.abs(n);
    const sign = n < 0 ? '-' : '';
    // For small numbers (under 10k), show full number with locale formatting
    if (abs < 10_000) {
      return sign + abs.toLocaleString();
    }
    // For larger numbers, use abbreviations
    if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}M`;
    return `${sign}${(abs / 1_000).toFixed(1)}k`;
  };

  const formatScore = (raw: string): string => {
    return raw
      .replace(/-?\d+/g, match => formatNumber(parseInt(match, 10)))
      .replace(/\//g, ' / ');
  };

  const formatFullScore = (scoreStr: string): string => {
    // Shared dual-grammar parser (TASK-538): understands the bracket format too; the
    // legacy-only regex below used to degrade every live score to its raw string.
    const parts = parseScoreParts(scoreStr);
    if (!parts) return scoreStr;
    // TASK-572 point 5: quasi visible only when non-zero (flag OFF: identical to before)
    const quasiPart = parts.quasi !== 0 ? `${parts.quasi}q/` : '';
    return `${parts.hard}hard/${quasiPart}${parts.medium}medium/${parts.soft}soft`;
  };

  const scoreStr = schedule?.score ? formatScore(String(schedule.score)) : '--';

  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [chSeconds, setChSeconds] = useState(0);
  const [lsSeconds, setLsSeconds] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Score history for speed calculation
  interface ScoreSample {
    timestamp: number;
    hardScore: number;
  }
  const [scoreHistory, setScoreHistory] = useState<ScoreSample[]>([]);
  const scoreHistoryRef = useRef<ScoreSample[]>([]);

  // Parse score string to extract the pure hard score (level 0). Shared dual-grammar
  // parser (TASK-538): the legacy suffix regex could not match the bracket format
  // (']' precedes "hard"), so the TASK-483 validity badge read 0 for EVERY live score
  // and always showed the green "valid schedule" state regardless of real hard damage.
  // The quasi level (hard level 1) is not part of this predicate: validity here is the
  // pure-hard axis, consistent with the pick-best primary key.
  const parseHardScore = (scoreStr: string): number => {
    const parts = parseScoreParts(scoreStr);
    return parts ? parts.hard : 0;
  };

  // Track score samples during LS phase
  useEffect(() => {
    if (isInLS && schedule?.score) {
      const hardScore = parseHardScore(String(schedule.score));
      const now = Date.now();
      setScoreHistory(prev => {
        // Avoid duplicate samples within 1 second
        if (prev.length > 0 && prev[prev.length - 1].hardScore === hardScore &&
            now - prev[prev.length - 1].timestamp < 1000) {
          return prev;
        }
        const newHistory = [...prev, { timestamp: now, hardScore }];
        // Keep last 60 samples (~60 seconds at 1s polling)
        return newHistory.slice(-60);
      });
    }
    // Reset history when solver stops or CH starts
    if (!isSolving || isInCH) {
      setScoreHistory([]);
      scoreHistoryRef.current = [];
    }
  }, [schedule?.score, isInLS, isSolving, isInCH]);

  // Clear optimistic pre-assign state when server confirms COMPLETE or ERROR
  useEffect(() => {
    if (preAssignRequested && !isServerPreAssigning) {
      // Server has responded with non-active status (IDLE, COMPLETE, or ERROR)
      // Keep showing for 2 more seconds so user sees final elapsed time
      const timeout = setTimeout(() => {
        setPreAssignRequested(false);
        setPreAssignRequestTime(null);
      }, 2000);
      return () => clearTimeout(timeout);
    }
  }, [preAssignStatus, preAssignRequested, isServerPreAssigning]);

  // Track error context when pre-assignment fails
  // (capture phase/candidates before they're reset)
  useEffect(() => {
    if (schedule?.preAssignmentError && preAssignStatus === 'ERROR') {
      // Capture context from the schedule when error appears
      // Note: We rely on the schedule's currentPhase which may still be set
      setLastErrorPhase(schedule.preAssignmentStatus ?? null);
      // Candidates tried isn't in SchoolSchedule, so we use a placeholder
      // The actual value comes from the PreAssignmentStatusResponse
      setLastCandidatesTried(0);
    }
  }, [schedule?.preAssignmentError, preAssignStatus, schedule?.preAssignmentStatus]);

  // Track previous solver status to detect completion
  const prevSolverStatusRef = useRef<string | null>(null);

  // Initialize refs with current status on mount
  useEffect(() => {
    prevSolverStatusRef.current = schedule?.solverStatus ?? null;
    // Only run once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch feasibility when solver stops (even if score is null)
  useEffect(() => {
    if (!isSolving) {
      setIsFetchingFeasibility(true);
      api.getFeasibility()
        .then(setFeasibility)
        .catch(err => console.error('Failed to fetch feasibility:', err))
        .finally(() => setIsFetchingFeasibility(false));
    }
    // Clear feasibility when solving starts
    if (isSolving) {
      setFeasibility(null);
    }
  }, [isSolving, schedule?.score]);

  // Show toast when solving completes (status changes from SOLVING_ACTIVE to NOT_SOLVING)
  useEffect(() => {
    const currentStatus = schedule?.solverStatus ?? null;
    const prevStatus = prevSolverStatusRef.current;

    // Detect transition from SOLVING_ACTIVE to NOT_SOLVING
    if (prevStatus === 'SOLVING_ACTIVE' && currentStatus === 'NOT_SOLVING') {
      if (schedule?.score) {
        // Parse score for display
        const scoreStr = String(schedule.score);
        addToast(t('toast.solvingComplete', { score: scoreStr }), 'success', 5000);
      } else {
        addToast(t('toast.solvingCompleteNoScore'), 'info', 4000);
      }
    }

    // Update ref for next comparison
    prevSolverStatusRef.current = currentStatus;
  }, [schedule?.solverStatus, schedule?.score, addToast, t]);

  // TASK-667: staged-cycle completion. The staged protocol never crosses
  // SOLVING_ACTIVE -> NOT_SOLVING, so the SOLVING toast above never fires for
  // it; the backend publishes the AUTHORITATIVE terminal outcome instead
  // (stagedOutcome: COMPLETE | CANCELLED | FAILED, null while running),
  // replacing the TASK-661/668 client inference. The interrupted banner is
  // DERIVED from the outcome at render (survives reloads, clears on the next
  // cycle start); toasts fire only on transitions witnessed in this tab.
  const [cancelledDismissed, setCancelledDismissed] = useState(false);
  const [failedDismissed, setFailedDismissed] = useState(false);
  const cycleCancelled = schedule?.stagedOutcome === 'CANCELLED' && !cancelledDismissed;
  const cycleFailed = schedule?.stagedOutcome === 'FAILED' && !failedDismissed;
  const prevOutcomeRef = useRef<string | null>(null);
  const sawCycleRef = useRef(false);

  useEffect(() => {
    if (schedule?.stagedStage != null || schedule?.refiningForDelivery) sawCycleRef.current = true;
    const outcome = schedule?.stagedOutcome ?? null;
    const prev = prevOutcomeRef.current;
    prevOutcomeRef.current = outcome;
    if (prev !== outcome) {
      // a new outcome re-arms both banners
      setCancelledDismissed(false);
      setFailedDismissed(false);
    }
    if (outcome == null || prev === outcome) return;
    // Announce only ends witnessed live (or an outcome superseding another):
    // a page opened on an already-finished cycle must not re-toast per reload.
    if (prev == null && !sawCycleRef.current) return;
    // Outcome and score arrive in the SAME status payload, so the toast can
    // read the score directly - no deferral needed.
    if (outcome === 'FAILED') {
      addToast(t('stagedCycle.failed'), 'error', 6000);
    } else if (outcome === 'COMPLETE') {
      if (schedule?.score) addToast(t('toast.stagedComplete', { score: String(schedule.score) }), 'success', 5000);
      else addToast(t('toast.stagedCompleteNoScore'), 'info', 4000);
    }
    // CANCELLED: the derived banner carries it, no toast.
  }, [schedule?.stagedOutcome, schedule?.stagedStage, schedule?.refiningForDelivery, schedule?.score, addToast, t]);

  // Show toast on the first COMPLETE/ERROR observed after we initiated a
  // pre-assign. Gated purely on preAssignRequested (set when the user clicks
  // the button, cleared here) rather than on having sampled a RUNNING status
  // first: a zero-rotation no-op run can go IDLE -> COMPLETE between two
  // polls, never showing RUNNING at all, and a transition-detection ref
  // would silently miss that case.
  useEffect(() => {
    if (!preAssignRequested) return;
    const currentStatus = schedule?.preAssignmentStatus ?? null;

    if (currentStatus === 'COMPLETE') {
      // Fetch the pre-assignment status to get candidatesTried for the summary
      api.getPreAssignmentStatus().then(status => {
        const summary = `${status.candidatesTried} candidates tried`;
        addToast(t('toast.preAssignComplete', { summary, defaultValue: 'Pre-assignment complete' }), 'success', 4000);
      }).catch(() => {
        // Fallback if status fetch fails
        addToast(t('toast.preAssignComplete', { summary: 'Complete', defaultValue: 'Pre-assignment complete' }), 'success', 4000);
      });
      setPreAssignRequested(false);
      setPreAssignRequestTime(null);
    } else if (currentStatus === 'ERROR') {
      const error = schedule?.preAssignmentError;
      const errorMessage = error
        ? `${t('toast.preAssignError')}: ${error}`
        : t('toast.preAssignError');
      addToast(errorMessage, 'error', 6000);
      setPreAssignRequested(false);
      setPreAssignRequestTime(null);
    }
  }, [schedule?.preAssignmentStatus, schedule?.preAssignmentError, addToast, t, preAssignRequested]);

  // Poll phase progression during solving when modal is open
  useEffect(() => {
    if (showProgression && isSolving) {
      const interval = setInterval(async () => {
        try {
          const data = await api.getPhaseProgression();
          setPhases(data);
        } catch (err) {
          console.error('Failed to poll phase progression:', err);
        }
      }, 2000);
      return () => clearInterval(interval);
    }
  }, [showProgression, isSolving]);

  // Calculate solving speed (hard score improvement per minute)
  const calculateSpeed = (): { speed: number; isStalled: boolean } | null => {
    if (scoreHistory.length < 2) return null;

    const now = Date.now();
    // Use samples from last 60 seconds
    const recentSamples = scoreHistory.filter(s => now - s.timestamp < 60000);
    if (recentSamples.length < 2) return null;

    const oldest = recentSamples[0];
    const newest = recentSamples[recentSamples.length - 1];
    const elapsedMinutes = (newest.timestamp - oldest.timestamp) / 60000;

    if (elapsedMinutes < 0.1) return null; // Need at least 6 seconds of data

    const scoreDelta = newest.hardScore - oldest.hardScore;
    const speed = scoreDelta / elapsedMinutes; // Points per minute

    // Check for stall (no improvement in last 60 seconds)
    const isStalled = scoreDelta === 0 && elapsedMinutes >= 0.9;

    return { speed, isStalled };
  };

  const speedInfo = isInLS ? calculateSpeed() : null;

  const computeElapsed = useCallback(() => {
    const base = schedule?.cumulativeElapsedSeconds ?? 0;
    if (!serverStartTime) return base;
    return base + Math.max(0, Math.floor((Date.now() - new Date(serverStartTime).getTime()) / 1000));
  }, [serverStartTime, schedule?.cumulativeElapsedSeconds]);

  const computeChSeconds = useCallback(() => {
    const base = schedule?.cumulativeChSeconds ?? 0;
    if (!serverStartTime) return base;
    const startMs = new Date(serverStartTime).getTime();
    if (!chCompletionTime) return base + Math.floor((Date.now() - startMs) / 1000);
    return base + Math.floor((new Date(chCompletionTime).getTime() - startMs) / 1000);
  }, [serverStartTime, chCompletionTime, schedule?.cumulativeChSeconds]);

  const computeLsSeconds = useCallback(() => {
    const base = schedule?.cumulativeLsSeconds ?? 0;
    if (!chCompletionTime) return base;
    return base + Math.max(0, Math.floor((Date.now() - new Date(chCompletionTime).getTime()) / 1000));
  }, [chCompletionTime, schedule?.cumulativeLsSeconds]);

  // Timer should only be reset when timing state changes, not when schedule polls update.
  // The cumulative* values from the server are used as a base offset in the compute functions.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    if (serverStartTime) {
      const update = () => {
        setElapsedSeconds(computeElapsed());
        setChSeconds(computeChSeconds());
        setLsSeconds(computeLsSeconds());
      };
      // Don't call update() immediately here - let the interval tick naturally
      timerRef.current = setInterval(update, 1000);
      return () => { if (timerRef.current) clearInterval(timerRef.current); };
    } else {
      setElapsedSeconds(schedule?.cumulativeElapsedSeconds ?? 0);
      setChSeconds(schedule?.cumulativeChSeconds ?? 0);
      setLsSeconds(schedule?.cumulativeLsSeconds ?? 0);
    }
  }, [serverStartTime, chCompletionTime]);

  const formatElapsed = (secs: number): string => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`;
    if (m > 0) return `${m}m ${String(s).padStart(2, '0')}s`;
    return `${s}s`;
  };

  const handleSolve = async () => {
    // TASK-663: a solve on a delivered schedule reshuffles every unpinned hour and
    // runs ~30 minutes; guard a stray click on an existing delivery. First start
    // (nothing placed yet) stays friction-free.
    if (hasPlacedLessons(schedule) && !window.confirm(t('controls.confirmSolveExisting'))) return;
    setIsLoading(true);
    setError(null);
    try {
      await api.startSolving();
      addToast(t('toast.solvingStarted'), 'info', 3000);
      onSolvingRequested?.(); // Triggers immediate poll in parent
      onRefresh();
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Failed to start solver');
      setError(errorMsg);
      addToast(errorMsg, 'error', 6000);
    } finally {
      setIsLoading(false);
    }
  };

  const handleStop = async () => {
    setIsLoading(true);
    setError(null);
    try {
      await api.stopSolving();
      // TASK-667: no local stop mirrors anymore - the cycle's finally publishes
      // stagedOutcome=CANCELLED and the outcome effect above reacts to it.
      addToast(t('toast.solvingStopped'), 'warning', 3000);
      onRefresh();
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Failed to stop solver');
      setError(errorMsg);
      addToast(errorMsg, 'error', 6000);
    } finally {
      setIsLoading(false);
    }
  };

  const handleReschedule = async () => {
    if (selectedTeacherId && selectedDate) {
      setIsLoading(true);
      setError(null);
      addToast(t('toast.rescheduleStarted'), 'info', 2000);
      try {
        await api.reschedule(selectedTeacherId, selectedDate);
        setShowModal(false);
        addToast(t('toast.rescheduleComplete'), 'success', 3000);
        onRefresh();
      } catch (e) {
        const errorMsg = getErrorMessage(e, 'Failed to reschedule');
        setError(errorMsg);
        addToast(t('toast.rescheduleError'), 'error', 6000);
      } finally {
        setIsLoading(false);
      }
    }
  };

  const handleAnalyze = async () => {
    setIsAnalyzing(true);
    setError(null);
    addToast(t('toast.analyzeStarted'), 'info', 2000);
    try {
      const result = await api.analyzeSchedule();
      setAnalysis(result);
      addToast(t('toast.analyzeComplete', { count: result.constraints?.length ?? 0 }), 'success', 3000);
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Failed to analyze schedule');
      setError(errorMsg);
      addToast(t('toast.analyzeError'), 'error', 6000);
    } finally {
      setIsAnalyzing(false);
    }
  };

  // Phase progression modal state - combined into single object for atomic updates
  const [progressionState, setProgressionState] = useState({
    show: false,
    phases: [] as PhaseRecord[],
    timeoutConfig: null as SolverTimeoutConfig | null,
    freshSchedule: null as SchoolSchedule | null,
  });

  const handleShowProgression = async () => {
    try {
      // Fetch all data in parallel
      const [phasesData, configData, freshSchedule] = await Promise.all([
        api.getPhaseProgression(),
        api.getPhaseProgressionConfig(),
        api.getSchedule(), // Fresh schedule for latest pre-assignment status
      ]);

      // Atomic state update - all values update together
      setProgressionState({
        show: true,
        phases: phasesData,
        timeoutConfig: configData,
        freshSchedule,
      });
    } catch (e) {
      setError(getErrorMessage(e, 'Failed to fetch phase progression'));
    }
  };

  const handleExportProgression = async () => {
    addToast(t('toast.exportStarted'), 'info', 2000);
    try {
      const blob = await api.exportPhaseProgression();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `phase-progression-${new Date().toISOString()}.json`;
      a.click();
      URL.revokeObjectURL(url);
      addToast(t('toast.exportComplete'), 'success', 3000);
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Failed to export phase progression');
      setError(errorMsg);
      addToast(t('toast.exportError'), 'error', 6000);
    }
  };

  const handleExport = async () => {
    addToast(t('toast.exportStarted'), 'info', 2000);
    try {
      const blob = await api.exportSchedule();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `schedule-export-${new Date().toISOString()}.json`;
      a.click();
      URL.revokeObjectURL(url);
      addToast(t('toast.exportComplete'), 'success', 3000);
    } catch (e) {
      const errorMsg = getErrorMessage(e, 'Failed to export schedule');
      setError(errorMsg);
      addToast(t('toast.exportError'), 'error', 6000);
    }
  };

  const handleImport = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setError(null);
    try {
      // TASK-663: import REPLACES the entire current schedule (test-tooling
      // operation, not school data). Confirm always, before reading the file.
      // Cancelled: silent return through the finally, which restores the
      // input value (the single reset path).
      if (!window.confirm(t('controls.confirmImport'))) return;
      setIsLoading(true);
      addToast(t('toast.importStarted'), 'info', 2000);
      const text = await file.text();
      const scheduleData = JSON.parse(text);
      await api.importSchedule(scheduleData);
      addToast(t('toast.importComplete'), 'success', 3000);
      onRefresh();
    } catch (err) {
      const errorMsg = getErrorMessage(err, 'Failed to import schedule');
      setError(errorMsg);
      addToast(t('toast.importError'), 'error', 6000);
    } finally {
      setIsLoading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };
  const handleDismissPreAssignError = async () => {
    try {
      await api.resetPreAssignmentStatus();
      // Clear local error context state
      setLastErrorPhase(null);
      setLastCandidatesTried(0);
      onRefresh();
    } catch (e) {
      console.error('Failed to dismiss pre-assignment error:', e);
    }
  };

  return (
    <>
      <div className="flex flex-col bg-surface rounded-[10px] shadow-soft border border-border-light overflow-hidden">
        {/* Main control bar */}
        <div className="flex items-center gap-2 px-2 md:px-3 py-2.5 flex-wrap">
          {/* Pre-assign group (runs before solver) */}
          <div className="flex items-center gap-1.5">
            <ActionButton
              variant="teal"
              onClick={() => handlePreAssign('preAssign', api.preAssign)}
              disabled={!canPreAssign}
              loading={preAssignLoading === 'preAssign'}
              icon={Icons.assign}
              title={preAssignDisabledReason ?? undefined}
            >
              {t('preAssign')}
            </ActionButton>

            <ActionButton
              variant="danger"
              onClick={async () => {
                // TASK-388: the reset is destructive (unpin + unassign of every
                // lesson, no recovery): confirm before firing. The 2026-07-20
                // incident (866 pins vanished) was a stray click on exactly this
                // control; window.confirm matches the pinned-move pattern.
                if (!window.confirm(t('confirmReset'))) return;
                setPreAssignLoading('reset');
                try {
                  await api.resetSchedule();
                  // Clear all local UI state
                  setPreAssignResult(null);
                  setAnalysis(null);
                  setError(null);
                  // Clear elapsed time counters so stale values don't persist
                  setElapsedSeconds(0);
                  setChSeconds(0);
                  setLsSeconds(0);
                  // Notify parent to clear its state
                  onResetComplete?.();
                  onRefresh();
                  addToast(t('toast.resetComplete'), 'success', 3000);
                } catch (e) {
                  const errorMsg = getErrorMessage(e, 'Failed to reset schedule');
                  setError(errorMsg);
                  addToast(t('toast.resetError'), 'error', 6000);
                } finally {
                  setPreAssignLoading(null);
                }
              }}
              disabled={!canReset}
              loading={preAssignLoading === 'reset'}
              icon={Icons.refresh}
              title={t('controls.clearAssignments')}
            >
              {t('reset')}
            </ActionButton>
          </div>

          {/* Separator */}
          <div className="hidden md:block w-px h-6 bg-border mx-1" />

          {/* Solver actions group */}
          <div className="flex items-center gap-1.5 flex-wrap">
            {/* TASK-661: Stop shows whenever a cycle is busy (plain LS solve, staged
                stage, or delivery refinement) - the backend's stop endpoint handles
                the staged protocol too. Solve only when fully idle. */}
            {!isBusyCycle ? (
              <ActionButton
                variant="primary"
                onClick={handleSolve}
                disabled={isLoading || isPreAssigning}
                loading={isLoading}
                icon={Icons.play}
                title={isPreAssigning ? t('controls.waitForPreAssign') : undefined}
              >
                {isLoading ? t('starting') : t('solve')}
              </ActionButton>
            ) : (
              <>
                <ActionButton
                  variant="danger"
                  onClick={handleStop}
                  disabled={isLoading}
                  loading={isLoading}
                  icon={Icons.stop}
                >
                  {isLoading ? t('stopping') : t('stop')}
                </ActionButton>
              </>
            )}
            <ActionButton
              variant="secondary"
              onClick={() => setShowModal(true)}
              disabled={isLoading || isPreAssigning}
              icon={Icons.refresh}
              title={isPreAssigning ? t('controls.waitForPreAssign') : undefined}
            >
              {t('reactiveFix')}
            </ActionButton>
            <ActionButton
              variant="secondary"
              onClick={handleAnalyze}
              disabled={isAnalyzing || isPreAssigning || !schedule?.score}
              loading={isAnalyzing}
              icon={Icons.chart}
              title={isPreAssigning ? t('controls.waitForPreAssign') : undefined}
            >
              {isAnalyzing ? t('analyzing') : t('analyze')}
            </ActionButton>
            <ActionButton
              variant="secondary"
              onClick={handleShowProgression}
              disabled={isLoading || isPreAssigning}
              icon={Icons.chart}
              title={t('controls.viewProgression')}
            >
              {t('controls.progression')}
            </ActionButton>
          </div>

          {/* Separator */}
          <div className="hidden md:block w-px h-6 bg-border mx-1" />

          {/* Status display */}
          <div className="flex items-center gap-2 md:gap-3 flex-wrap md:ml-auto">
            <StatusBadge {...getStatusBadgeProps()} />
            {/* Feasibility indicator (TASK-661 alignment: hidden for the whole
                staged cycle, like the outcome banner, so a mid-cycle verdict
                chip can never contradict the "intermediate grid" banner) */}
            {!isBusyCycle && feasibility && (
              <div
                className={`px-2 py-0.5 rounded text-xs font-medium ${
                  feasibility.feasible
                    ? 'bg-feedback-success-bg text-feedback-success-text'
                    : 'bg-feedback-error-bg text-feedback-error-text'
                }`}
                title={feasibilityLabel()}
              >
                {feasibility.feasible ? '✓' : '⚠️'} {feasibilityLabel()}
              </div>
            )}
            <div className="flex items-baseline gap-1">
              <span className="text-xs text-muted">{t('score')}:</span>
              <span
                className="font-mono text-sm font-bold text-content tabular-nums"
                title={schedule?.score ? formatFullScore(String(schedule.score)) : undefined}
              >
                {scoreStr}
              </span>
            </div>
            {(isSolving || isPreAssigning || elapsedSeconds > 0 || (schedule?.preAssignmentDurationSeconds ?? 0) > 0) && (
              <div className="flex items-center gap-1">
                <span className="text-xs text-muted">{t('elapsed')}:</span>
                <span className="font-mono text-xs font-semibold text-elapsed tabular-nums">{formatElapsed(elapsedSeconds)}</span>
                {isSolving && (
                  <>
                    <span className="font-mono text-[10px] text-muted px-1 py-0.5 bg-surface-alt rounded">CH: {formatElapsed(chSeconds)}</span>
                    {isInLS && <span className="font-mono text-[10px] text-muted px-1 py-0.5 bg-surface-alt rounded">LS: {formatElapsed(lsSeconds)}</span>}
                    {/* Speed indicator during LS phase */}
                    {isInLS && (
                      <span className={`font-mono text-[10px] px-1 py-0.5 rounded ${
                        !speedInfo ? 'text-muted bg-surface-alt' :
                        speedInfo.isStalled ? 'text-feedback-warning-text bg-feedback-warning-bg' :
                        speedInfo.speed > 0 ? 'text-feedback-success-text bg-feedback-success-bg' :
                        speedInfo.speed < 0 ? 'text-feedback-error-text bg-feedback-error-bg' :
                        'text-muted bg-surface-alt'
                      }`}>
                        {!speedInfo ? 'speed...' :
                         speedInfo.isStalled ? 'stalled' :
                         `${speedInfo.speed >= 0 ? '+' : ''}${formatNumber(Math.round(speedInfo.speed))}/min`}
                      </span>
                    )}
                    {isInCH && (
                      <span className="font-mono text-[10px] text-muted px-1 py-0.5 bg-surface-alt rounded">CH phase...</span>
                    )}
                  </>
                )}
                {(schedule?.preAssignmentDurationSeconds ?? 0) > 0 && (
                  <span className="font-mono text-[10px] text-muted px-1 py-0.5 bg-surface-alt rounded">
                    PA: {formatElapsed(schedule!.preAssignmentDurationSeconds)}
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Separator */}
          <div className="hidden md:block w-px h-6 bg-border mx-1" />

          {/* TASK-663: Export/Import moved out of the everyday solver bar into a
              labelled development lane (common:devSection), styled like the SVILUPPO
              nav section: smaller secondary buttons under a dim uppercase label.
              Import replaces the ENTIRE schedule, so it never belongs next to Solve. */}
          <div className="flex flex-col items-start gap-1">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-muted select-none">
              {t('common:devSection')}
            </span>
            <div className="flex items-center gap-1.5">
              <ActionButton
                variant="secondary"
                className="text-xs"
                onClick={handleExport}
                disabled={isLoading || !schedule}
                title={t('controls.exportJson')}
              >
                📤 {t('controls.export')}
              </ActionButton>
              <ActionButton
                variant="secondary"
                className="text-xs"
                onClick={() => fileInputRef.current?.click()}
                disabled={isLoading}
                title={t('controls.importJson')}
              >
                📥 {t('controls.import')}
              </ActionButton>
              <input
                type="file"
                ref={fileInputRef}
                accept=".json"
                style={{ display: 'none' }}
                onChange={handleImport}
              />
            </div>
          </div>
        </div>

        {/* Staged cycle in-progress banner (TASK-419) - the grid shown right now is an
            intermediate checkpoint (stage pins, provisional rooms), not the final solve. */}
        {stagedStage && (
          <div
            className="flex items-center gap-2 px-4 py-2 border-t border-feedback-warning-border bg-feedback-warning-bg"
            role="status"
            aria-live="polite"
          >
            <span className="text-base" aria-hidden="true">⚠️</span>
            <span className="flex-1 text-xs font-semibold text-feedback-warning-text">
              {t('stagedCycle.banner', { stage: stagedStage })}
            </span>
            {stagedMaxAttempts > 1 && (
              <span className="text-xs font-bold text-feedback-warning-text whitespace-nowrap">
                {t('stagedCycle.attempt', { attempt: stagedAttempt, max: stagedMaxAttempts })}
                {stagedBudgetSeconds > 0 && (
                  <span className="font-normal opacity-80">
                    {' · '}{t('stagedCycle.budgetHint', { min: Math.round(stagedBudgetSeconds / 60) })}
                  </span>
                )}
              </span>
            )}
          </div>
        )}

        {/* TASK-661: delivery-refinement banner. Fires AFTER the staged banner (stagedStage
            already null): the ~10min final refinement window where solverStatus is
            NOT_SOLVING, stagedStage is null, and only refiningForDelivery says a cycle is
            still running. The grid shown is not final yet. */}
        {isRefining && stagedStage == null && (
          <div
            className="flex items-center gap-2 px-4 py-2 border-t border-feedback-warning-border bg-feedback-warning-bg"
            role="status"
            aria-live="polite"
          >
            <span
              className="inline-block w-2.5 h-2.5 rounded-full bg-feedback-warning-text animate-pulse"
              aria-hidden="true"
            />
            <span className="flex-1 text-xs font-semibold text-feedback-warning-text">
              {t('stagedCycle.refining')}
            </span>
          </div>
        )}

        {/* TASK-661 (Fedora 24/09): post-cancellation banner. Say plainly that the grid
            on screen is an intermediate, possibly empty timetable, until the next cycle. */}
        {cycleCancelled && !isBusyCycle && (
          <div
            className="flex items-center gap-2 px-4 py-2 border-t border-feedback-warning-border bg-feedback-warning-bg"
            role="status"
            aria-live="polite"
          >
            <span className="text-base" aria-hidden="true">⚠️</span>
            <span className="flex-1 text-xs font-semibold text-feedback-warning-text">
              {t('stagedCycle.cancelled')}
            </span>
            <button
              onClick={() => setCancelledDismissed(true)}
              className="bg-transparent border-none cursor-pointer text-feedback-warning-text text-sm font-bold px-1"
              aria-label={t('stagedCycle.cancelledDismiss')}
            >
              ×
            </button>
          </div>
        )}
        {cycleFailed && !isBusyCycle && (
          // TASK-667 (review F6): a FAILED cycle leaves the same "intermediate
          // grid" situation as a cancellation - it gets the same persistent
          // banner, not only a one-shot toast a fast crash can outrun.
          <div
            className="flex items-center gap-2 px-4 py-2 border-t border-feedback-error-border bg-feedback-error-bg"
            role="status"
            aria-live="polite"
          >
            <span className="text-base" aria-hidden="true">⛔</span>
            <span className="flex-1 text-xs font-semibold text-feedback-error-text">
              {t('stagedCycle.failed')}
            </span>
            <button
              onClick={() => setFailedDismissed(true)}
              className="bg-transparent border-none cursor-pointer text-feedback-error-text text-sm font-bold px-1"
              aria-label={t('stagedCycle.cancelledDismiss')}
            >
              ×
            </button>
          </div>
        )}

        {/* TASK-483 outcome badge (AC3): when a solve has finished, state plainly whether the
            schedule is valid (0 hard) or still has residual conflicts. 0-hard is binary, so a
            schedule with residual hard is flagged "not almost ready". Hidden while a cycle
            (solve, staged stage, delivery refinement) is in progress (TASK-661). */}
        {!isBusyCycle && schedule?.score && (() => {
          const hard = parseHardScore(String(schedule.score));
          const valid = hard === 0;
          return (
            <div
              className={`flex items-center gap-2 px-4 py-2 border-t ${valid ? 'border-feedback-success-border bg-feedback-success-bg' : 'border-feedback-warning-border bg-feedback-warning-bg'}`}
              role="status"
            >
              <span className="text-base" aria-hidden="true">{valid ? '✓' : '⚠️'}</span>
              <span className={`flex-1 text-xs font-semibold ${valid ? 'text-feedback-success-text' : 'text-feedback-warning-text'}`}>
                {valid
                  ? t('stagedCycle.validSchedule')
                  : t('stagedCycle.residualConflicts', { count: Math.abs(hard) })}
              </span>
            </div>
          );
        })()}

        {/* Pre-assignment error notification */}
        {schedule?.preAssignmentError && (
          <PreAssignErrorNotification
            error={schedule.preAssignmentError}
            currentPhase={lastErrorPhase}
            candidatesTried={lastCandidatesTried}
            failureAnalysis={schedule.preAssignmentFailureAnalysis}
            onDismiss={handleDismissPreAssignError}
          />
        )}

        {/* Result toast */}
        {preAssignResult && (
          <div className="px-4 py-2 border-t flex flex-col gap-1" style={{ backgroundColor: 'var(--toast-success-bg)', borderColor: 'var(--toast-success-border)' }}>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold" style={{ color: 'var(--toast-success-text)' }}>✓</span>
              <span className="flex-1 text-xs text-content">{preAssignResult.summary}</span>
              <button
                className="px-2 py-1 border-none rounded bg-transparent text-xs font-medium cursor-pointer transition-colors duration-100"
                style={{ color: 'var(--toast-success-text)' }}
                onClick={copyFullReport}
              >
                {copySuccess ? t('common:copied') : t('common:copy')}
              </button>
              {preAssignResult.details && (
                <button
                  className="px-2 py-1 border-none rounded bg-transparent text-xs font-medium cursor-pointer transition-colors duration-100"
                  style={{ color: 'var(--toast-success-text)' }}
                  onClick={() => setShowDetails(!showDetails)}
                >
                  {showDetails ? t('common:hide') : t('common:details')}
                </button>
              )}
              <button
                className="px-2 py-1 border-none rounded bg-transparent text-xs font-medium cursor-pointer transition-colors duration-100"
                style={{ color: 'var(--toast-success-text)' }}
                onClick={() => { setPreAssignResult(null); setShowDetails(false); }}
              >
                {t('common:hide')}
              </button>
            </div>
            {showDetails && preAssignResult.details && (
              <div className="mt-1 px-2.5 py-1.5 bg-surface rounded text-xs font-mono text-muted whitespace-pre-wrap max-h-[120px] overflow-y-auto">{preAssignResult.details}</div>
            )}
          </div>
        )}

        {/* Error display */}
        {error && (
          <div className="flex items-center gap-2 px-4 py-1.5 border-t" style={{ backgroundColor: 'var(--toast-error-bg)', borderColor: 'var(--toast-error-border)' }}>
            <span className="text-sm" style={{ color: 'var(--toast-error-text)' }}>⚠</span>
            <span className="flex-1 text-xs" style={{ color: 'var(--toast-error-text)' }}>{error}</span>
            <button className="bg-none border-none text-base cursor-pointer leading-none" style={{ color: 'var(--toast-error-text)' }} onClick={() => setError(null)}>×</button>
          </div>
        )}
      </div>

      {/* Reschedule modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-dropdown" onClick={() => setShowModal(false)}>
          <div className="bg-surface p-6 rounded-xl min-w-[340px] max-w-[400px] shadow-elevated" onClick={e => e.stopPropagation()}>
            <h3 className="m-0 mb-2 text-lg font-bold text-content">{t('rescheduleTitle')}</h3>
            <p className="m-0 mb-4 text-xs text-muted leading-relaxed">{t('rescheduleDesc')}</p>
            <div className="mb-3">
              <label className="block mb-1 text-xs font-semibold text-content">{t('selectTeacher')}</label>
              <select
                className="w-full px-2.5 py-2 border border-input-border rounded-md bg-surface-alt text-content text-sm"
                value={selectedTeacherId ?? ''}
                onChange={e => setSelectedTeacherId(Number(e.target.value))}
              >
                <option value="">--</option>
                {teachers.map(t => (
                  <option key={t.id} value={t.id}>{t.name}</option>
                ))}
              </select>
            </div>
            <div className="mb-3">
              <label className="block mb-1 text-xs font-semibold text-content">{t('selectDate')}</label>
              <input
                type="date"
                className="w-full px-2.5 py-2 border border-input-border rounded-md bg-surface-alt text-content text-sm"
                value={selectedDate}
                onChange={e => setSelectedDate(e.target.value)}
              />
            </div>
            <div className="flex gap-2 justify-end mt-4">
              <ActionButton variant="outline" onClick={() => setShowModal(false)}>
                {t('common:cancel')}
              </ActionButton>
              <ActionButton
                variant="secondary"
                onClick={handleReschedule}
                disabled={!selectedTeacherId || !selectedDate}
              >
                {t('common:confirm')}
              </ActionButton>
            </div>
          </div>
        </div>
      )}

      {analysis && (
        <AnalysisPanel
          analysis={analysis}
          onClose={() => setAnalysis(null)}
        />
      )}

      {progressionState.show && (
        <ProgressionPanel
          phases={progressionState.phases}
          isSolving={isSolving}
          onClose={() => setProgressionState(prev => ({ ...prev, show: false }))}
          onExport={handleExportProgression}
          timeoutConfig={progressionState.timeoutConfig}
          serverStartTime={serverStartTime}
          preAssignmentStatus={progressionState.freshSchedule?.preAssignmentStatus ?? schedule?.preAssignmentStatus ?? null}
          preAssignmentDuration={progressionState.freshSchedule?.preAssignmentDurationSeconds ?? schedule?.preAssignmentDurationSeconds ?? 0}
        />
      )}
    </>
  );
}
// FORCE REBUILD Fri  6 Mar 17:11:48 CET 2026
