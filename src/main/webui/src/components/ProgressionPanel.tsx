import { useMemo, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import type { PhaseRecord, SolverTimeoutConfig } from '../types/domain';
import { parseScoreParts } from '../utils';

interface Props {
  phases: PhaseRecord[];
  isSolving: boolean;
  onClose: () => void;
  onExport: () => void;
  timeoutConfig?: SolverTimeoutConfig | null;
  serverStartTime?: string | null;  // ISO timestamp of when solving started
  preAssignmentStatus?: string | null;  // Pre-assignment status for better messaging
  preAssignmentDuration?: number;  // Pre-assignment duration in seconds
}

/**
 * Modal panel displaying solver phase progression history.
 * Shows timeline, durations, and score changes for each phase.
 */
export default function ProgressionPanel({ phases, isSolving, onClose, onExport, timeoutConfig, serverStartTime, preAssignmentStatus, preAssignmentDuration }: Props) {
  const { t } = useTranslation(['schedule', 'common']);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Focus management and keyboard handling
  useEffect(() => {
    closeButtonRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  // Format score for display
  const formatScore = (scoreStr: string): string => {
    // Both score grammars parse through the shared util (TASK-538): bracket format
    // "[h/q]hard/[m/s]soft" first, legacy flat format as fallback.
    const parts = parseScoreParts(scoreStr);
    if (!parts) return scoreStr;

    const formatPart = (num: number): string => {
      const abs = Math.abs(num);
      const sign = num < 0 ? '-' : (num > 0 ? '+' : '');
      if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}M`;
      if (abs >= 1_000) return `${sign}${(abs / 1_000).toFixed(1)}k`;
      return `${sign}${abs}`;
    };

    return `${formatPart(parts.hard)}h / ${formatPart(parts.medium)}m / ${formatPart(parts.soft)}s`;
  };

  // Format duration for display
  const formatDuration = (millis: number): string => {
    const seconds = Math.floor(millis / 1000);
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    if (minutes > 0) return `${minutes}m ${secs}s`;
    return `${secs}s`;
  };

  // Calculate score delta between start and end
  const scoreDelta = (phase: PhaseRecord): string => {
    if (!phase.endScore) return '—';

    const parseScore = (s: string) => {
      const parts = parseScoreParts(s);
      if (!parts) return { h: 0, m: 0, s: 0 };
      return { h: parts.hard, m: parts.medium, s: parts.soft };
    };

    const start = parseScore(phase.startScore);
    const end = parseScore(phase.endScore);
    const dh = end.h - start.h;
    const dm = end.m - start.m;
    const ds = end.s - start.s;

    const formatDelta = (n: number): string => {
      if (n === 0) return '0';
      const abs = Math.abs(n);
      const sign = n > 0 ? '+' : '';
      if (abs >= 1000) return `${sign}${(abs / 1000).toFixed(1)}k`;
      return `${sign}${abs}`;
    };

    return `${formatDelta(dh)}h / ${formatDelta(dm)}m / ${formatDelta(ds)}s`;
  };

  // Calculate total time - use serverStartTime when available for accuracy,
  // otherwise fall back to summing phase durations
  const totalTime = useMemo(() => {
    // If we have serverStartTime and solver is running or was running, use it
    if (serverStartTime) {
      const start = new Date(serverStartTime).getTime();
      const now = Date.now();
      return Math.max(0, now - start);
    }
    // Fallback: sum of phase durations (less accurate)
    return phases.reduce((sum, p) => sum + p.timeSpentMillis, 0);
  }, [phases, serverStartTime]);

  // Get score color class
  const getScoreColor = (scoreStr: string): string => {
    if (scoreStr.includes('-')) return 'text-feedback-error-text';
    if (scoreStr.includes('+')) return 'text-feedback-success-text';
    return 'text-muted';
  };

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // Sort phases by number
  const sortedPhases = useMemo(() => {
    return [...phases].sort((a, b) => a.phaseNumber - b.phaseNumber);
  }, [phases]);

  return (
    <div
      className="fixed inset-0 bg-overlay flex items-center justify-center z-modal"
      onClick={handleOverlayClick}
      role="presentation"
    >
      <div
        className="bg-surface rounded-xl w-[90%] max-w-[95vw] md:max-w-panel lg:max-w-[1000px]
                 max-h-[85vh] flex flex-col shadow-[0_8px_32px_var(--shadow)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="progression-title"
      >
        {/* Header */}
        <header className="flex justify-between items-center px-4 md:px-6 py-3 md:py-4
                           border-b border-border-light">
          <div className="flex items-center gap-3">
            <h3 id="progression-title" className="m-0 text-xl font-semibold text-content">
              {t('progression.title')}
            </h3>
            {isSolving && (
              <span className="px-2 py-0.5 rounded text-xs font-medium bg-feedback-info-bg text-feedback-info-text">
                {t('progression.live')}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              className="px-3 py-1.5 rounded-md bg-surface-alt border border-border
                         text-content text-sm font-medium
                         hover:bg-border-light transition-colors duration-100"
              onClick={onExport}
              title={t('progression.exportTitle')}
            >
              📥 {t('progression.exportJson')}
            </button>
            <button
              ref={closeButtonRef}
              className="bg-none border-none text-2xl cursor-pointer text-muted
                         p-1 leading-none hover:text-content transition-colors"
              onClick={onClose}
              aria-label={t('common:close')}
            >
              &times;
            </button>
          </div>
        </header>

        {/* Content */}
        <div className="p-4 md:p-6 overflow-y-auto flex-1">
          {/* Summary */}
          <div className="flex gap-4 mb-6 flex-wrap">
            <div className="px-4 py-2 rounded-lg bg-surface-alt border border-border-light">
              <div className="text-xs text-muted uppercase tracking-wide">{t('progression.totalTime')}</div>
              <div className="text-lg font-bold text-content">{formatDuration(totalTime)}</div>
            </div>
            <div className="px-4 py-2 rounded-lg bg-surface-alt border border-border-light">
              <div className="text-xs text-muted uppercase tracking-wide">{t('progression.phases')}</div>
              <div className="text-lg font-bold text-content">{sortedPhases.length}</div>
            </div>
            {timeoutConfig && (
              <div className="px-4 py-2 rounded-lg bg-surface-alt border border-border-light">
                <div className="text-xs text-muted uppercase tracking-wide">{t('progression.overallLimit')}</div>
                <div className="text-lg font-bold text-content">{formatDuration(timeoutConfig.overallSecondsSpentLimit * 1000)}</div>
              </div>
            )}
            {isSolving && (
              <div className="px-4 py-2 rounded-lg bg-feedback-info-bg border border-feedback-info-border text-feedback-info-text">
                <div className="text-xs uppercase tracking-wide">{t('progression.status')}</div>
                <div className="text-lg font-bold">{t('progression.solving')}</div>
              </div>
            )}
          </div>

          {/* Phase cards or table */}
          {sortedPhases.length > 0 ? (
            <div className="space-y-3">
              {sortedPhases.map((phase, idx) => (
                <div
                  key={`${phase.phaseNumber}-${idx}`}
                  className={`p-4 rounded-lg border ${
                    !phase.isComplete
                      ? 'bg-feedback-info-bg border-feedback-info-border'
                      : 'bg-surface-alt border-border-light'
                  }`}
                >
                  <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-3 md:gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className={`text-xs font-bold px-2 py-0.5 rounded ${
                          !phase.isComplete && isSolving
                            ? 'bg-feedback-info-bg text-feedback-info-text'
                            : 'bg-border-light text-muted'
                        }`}>
                          {t('progression.phase')} {phase.phaseNumber}
                        </span>
                        <h4 className="m-0 text-base font-semibold text-content">
                          {phase.phaseName}
                        </h4>
                      </div>

                      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm mt-2">
                        <div>
                          <span className="text-muted">{t('progression.duration')}: </span>
                          <span className="font-mono font-semibold text-content">
                            {formatDuration(phase.timeSpentMillis)}
                          </span>
                        </div>
                        <div>
                          <span className="text-muted">{t('progression.start')}: </span>
                          <span className="font-mono text-muted">
                            {new Date(phase.startTime).toLocaleTimeString()}
                          </span>
                        </div>
                        {phase.endTime && (
                          <div>
                            <span className="text-muted">{t('progression.end')}: </span>
                            <span className="font-mono text-muted">
                              {new Date(phase.endTime).toLocaleTimeString()}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Timeout display */}
                      {(phase.secondsSpentLimit || phase.unimprovedSecondsSpentLimit) && (
                        <div className="flex flex-wrap gap-3 text-xs mt-2 pt-2 border-t border-border-light">
                          {phase.secondsSpentLimit && (
                            <div>
                              <span className="text-muted">{t('progression.limit')}: </span>
                              <span className="font-mono text-muted">
                                {formatDuration(phase.secondsSpentLimit * 1000)}
                              </span>
                            </div>
                          )}
                          {phase.unimprovedSecondsSpentLimit && (
                            <div>
                              <span className="text-muted">{t('progression.unimproved')}: </span>
                              <span className="font-mono text-muted">
                                {formatDuration(phase.unimprovedSecondsSpentLimit * 1000)}
                              </span>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="flex flex-row md:flex-col gap-3 md:gap-2 md:text-right md:min-w-[200px] flex-wrap border-t md:border-t-0 border-border-light pt-2 md:pt-0">
                      <div>
                        <div className="text-xs text-muted mb-0.5">{t('progression.startScore')}</div>
                        <div className={`font-mono text-sm ${getScoreColor(phase.startScore)}`}>
                          {formatScore(phase.startScore)}
                        </div>
                      </div>
                      {phase.endScore && (
                        <>
                          <div>
                            <div className="text-xs text-muted mb-0.5">{t('progression.endScore')}</div>
                            <div className={`font-mono text-sm ${getScoreColor(phase.endScore)}`}>
                              {formatScore(phase.endScore)}
                            </div>
                          </div>
                          <div>
                            <div className="text-xs text-muted mb-0.5">{t('progression.change')}</div>
                            <div className={`font-mono text-xs ${getScoreColor(scoreDelta(phase))}`}>
                              {scoreDelta(phase)}
                            </div>
                          </div>
                        </>
                      )}
                      {!phase.isComplete && (
                        <div className="text-xs text-feedback-info-text italic">
                          {t('progression.inProgress')}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center p-8 text-muted">
              {preAssignmentStatus === 'COMPLETE' ? (
                <div>
                  <p className="mb-2">{t('progression.preAssignComplete')}</p>
                  <p>{t('progression.startSolverToTrack')}</p>
                </div>
              ) : preAssignmentStatus && preAssignmentStatus !== 'IDLE' ? (
                <div>
                  <p className="mb-2">{t('progression.preAssignInProgress')}</p>
                  <p>{t('progression.progressionAfterSolver')}</p>
                </div>
              ) : (
                <p>{t('progression.noPhaseData')}</p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
