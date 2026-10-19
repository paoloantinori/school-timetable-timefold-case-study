import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { FailureAnalysis } from '../types/domain';

interface Props {
  /** The error message from schedule.preAssignmentError */
  error: string;
  /** The phase that was running when error occurred */
  currentPhase: string | null;
  /** Number of candidates tried before failure */
  candidatesTried: number;
  /** Detailed failure analysis from backend (may be null for older API versions) */
  failureAnalysis: FailureAnalysis | null;
  /** Callback to dismiss the error notification */
  onDismiss: () => void;
}

/**
 * Expandable error notification for pre-assignment failures.
 * Displays structured failure analysis with teacher/group names,
 * conflict patterns, and actionable suggestions.
 */
export function PreAssignErrorNotification({
  error,
  currentPhase,
  candidatesTried,
  failureAnalysis,
  onDismiss,
}: Props) {
  const { t } = useTranslation(['schedule', 'common']);
  const [showDetails, setShowDetails] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  const copyErrorReport = async () => {
    const report = formatErrorReport(error, currentPhase, candidatesTried, failureAnalysis, t);
    try {
      await navigator.clipboard.writeText(report);
      setCopySuccess(true);
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (e) {
      console.error('Failed to copy error report:', e);
    }
  };

  // Extract a short summary from the error message
  const errorSummary = getErrorSummary(error);

  // Use failure analysis if available, otherwise fall back to basic display
  const hasRichAnalysis = failureAnalysis !== null;

  return (
    <div
      className="px-4 py-2 bg-feedback-error-bg border-t border-feedback-error-border flex flex-col gap-2"
      role="alert"
    >
      {/* Summary row */}
      <div className="flex items-center gap-2">
        <span className="text-feedback-error-text text-sm font-bold">❌</span>
        <span className="flex-1 text-xs text-feedback-error-text font-medium">
          {errorSummary}
        </span>
        <button
          className="px-2 py-1 border-none rounded bg-transparent text-feedback-error-text text-xs font-medium cursor-pointer transition-colors duration-100 hover:opacity-80"
          onClick={copyErrorReport}
          aria-label={t('preAssignError.copyAriaLabel')}
        >
          {copySuccess ? t('common:copied') : t('common:copy')}
        </button>
        <button
          className="px-2 py-1 border-none rounded bg-transparent text-feedback-error-text text-xs font-medium cursor-pointer transition-colors duration-100 hover:opacity-80"
          onClick={() => setShowDetails(!showDetails)}
          aria-expanded={showDetails}
          aria-label={showDetails ? t('preAssignError.hideAriaLabel') : t('preAssignError.showAriaLabel')}
        >
          {showDetails ? t('common:hide') : t('common:details')}
        </button>
        <button
          className="bg-none border-none text-feedback-error-text text-base cursor-pointer leading-none p-1 hover:opacity-80 rounded transition-colors"
          onClick={onDismiss}
          aria-label={t('preAssignError.dismissAriaLabel')}
        >
          ×
        </button>
      </div>

      {/* Rich analysis display */}
      {hasRichAnalysis && (
        <div className="flex flex-col gap-1.5 text-xs">
          {/* Teachers and Groups involved */}
          <div className="flex flex-wrap gap-x-4 gap-y-0.5">
            {failureAnalysis.conflictingTeacherNames.length > 0 && (
              <div className="flex items-center gap-1">
                <span className="text-feedback-error-text font-semibold">{t('preAssignError.teachers')}:</span>
                <span className="text-feedback-error-text">{failureAnalysis.conflictingTeacherNames.join(', ')}</span>
              </div>
            )}
            {failureAnalysis.conflictingGroupNames.length > 0 && (
              <div className="flex items-center gap-1">
                <span className="text-feedback-error-text font-semibold">{t('preAssignError.groups')}:</span>
                <span className="text-feedback-error-text">{failureAnalysis.conflictingGroupNames.join(', ')}</span>
              </div>
            )}
          </div>

          {/* Suggestion (highlighted) */}
          {failureAnalysis.suggestion && (
            <div className="flex items-start gap-1.5 px-2 py-1.5 bg-feedback-warning-bg rounded border border-feedback-warning-border">
              <span className="text-feedback-warning-text">💡</span>
              <span className="text-feedback-warning-text">{failureAnalysis.suggestion}</span>
            </div>
          )}
        </div>
      )}

      {/* Expandable details */}
      {showDetails && (
        <div className="mt-1 px-2.5 py-2 bg-surface rounded text-xs font-mono text-muted max-h-[180px] overflow-y-auto">
          {hasRichAnalysis ? (
            <div className="flex flex-col gap-2">
              {/* Phase and Reason */}
              <div>
                <span className="text-feedback-error-text font-bold">{t('preAssignError.phase')}:</span>{' '}
                <span className="text-content">{failureAnalysis.phase}</span>
              </div>
              <div>
                <span className="text-feedback-error-text font-bold">{t('preAssignError.reason')}:</span>{' '}
                <span className="text-content">{failureAnalysis.reason}</span>
              </div>
              {failureAnalysis.periodRange && (
                <div>
                  <span className="text-feedback-error-text font-bold">{t('preAssignError.period')}:</span>{' '}
                  <span className="text-content">{failureAnalysis.periodRange}</span>
                </div>
              )}

              {/* Conflict Patterns */}
              {failureAnalysis.conflictPatterns.length > 0 && (
                <div className="mt-1 border-t border-border pt-2">
                  <div className="text-feedback-error-text font-bold mb-1">{t('preAssignError.conflictPatterns')}:</div>
                  {failureAnalysis.conflictPatterns.map((pattern, idx) => (
                    <div key={idx} className="ml-2 mb-2">
                      <div className="text-content font-semibold">
                        {pattern.type} ({t('preAssignError.occurrences', { count: pattern.occurrenceCount })})
                      </div>
                      <div className="text-muted ml-2">{pattern.description}</div>
                      {pattern.examples.length > 0 && (
                        <ul className="ml-4 mt-0.5 text-muted">
                          {pattern.examples.slice(0, 3).map((ex, i) => (
                            <li key={i} className="list-disc">• {ex}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            // Fallback for older API without failureAnalysis
            <pre className="whitespace-pre-wrap">{formatDetails(error, currentPhase, candidatesTried, t)}</pre>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Extract a short summary from the error message.
 * Shows first line or first 100 chars if multi-line.
 */
function getErrorSummary(error: string): string {
  const firstLine = error.split('\n')[0];
  if (firstLine.length > 100) {
    return firstLine.substring(0, 100) + '...';
  }
  return firstLine;
}

/**
 * Format detailed error information for the expandable section (fallback mode).
 */
function formatDetails(
  error: string,
  currentPhase: string | null,
  candidatesTried: number,
  t: (key: string, opts?: Record<string, unknown>) => string
): string {
  const lines: string[] = [];

  if (currentPhase) {
    lines.push(`${t('preAssignError.phase')}: ${currentPhase}`);
  }
  lines.push(t('preAssignError.candidatesTried', { count: candidatesTried }));
  lines.push('');
  lines.push(t('preAssignError.error'));
  lines.push(error);

  return lines.join('\n');
}

/**
 * Format error report as markdown for clipboard.
 */
function formatErrorReport(
  error: string,
  currentPhase: string | null,
  candidatesTried: number,
  failureAnalysis: FailureAnalysis | null,
  t: (key: string, opts?: Record<string, unknown>) => string
): string {
  const lines: string[] = [`# ${t('preAssignError.preAssignmentError')}`];

  lines.push('');
  if (currentPhase) {
    lines.push(`**${t('preAssignError.phase')}:** ${currentPhase}`);
  }
  lines.push(`**${t('preAssignError.candidatesTried', { count: candidatesTried })}**`);

  if (failureAnalysis) {
    lines.push('');
    lines.push(`## ${t('preAssignError.failureAnalysis')}`);
    lines.push('');
    lines.push(`**${t('preAssignError.phase')}:** ${failureAnalysis.phase}`);
    lines.push(`**${t('preAssignError.reason')}:** ${failureAnalysis.reason}`);

    if (failureAnalysis.periodRange) {
      lines.push(`**${t('preAssignError.period')}:** ${failureAnalysis.periodRange}`);
    }

    if (failureAnalysis.conflictingTeacherNames.length > 0) {
      lines.push(`**${t('preAssignError.teachers')}:** ${failureAnalysis.conflictingTeacherNames.join(', ')}`);
    }

    if (failureAnalysis.conflictingGroupNames.length > 0) {
      lines.push(`**${t('preAssignError.groups')}:** ${failureAnalysis.conflictingGroupNames.join(', ')}`);
    }

    if (failureAnalysis.suggestion) {
      lines.push('');
      lines.push(`**${t('preAssignError.suggestion')}:** ${failureAnalysis.suggestion}`);
    }

    if (failureAnalysis.conflictPatterns.length > 0) {
      lines.push('');
      lines.push(`## ${t('preAssignError.conflictPatterns')}`);
      lines.push('');
      failureAnalysis.conflictPatterns.forEach((pattern, idx) => {
        lines.push(`### ${idx + 1}. ${pattern.type} (${t('preAssignError.occurrences', { count: pattern.occurrenceCount })})`);
        lines.push('');
        lines.push(pattern.description);
        if (pattern.examples.length > 0) {
          lines.push('');
          lines.push(t('preAssignError.examples'));
          pattern.examples.forEach(ex => {
            lines.push(`- ${ex}`);
          });
        }
        lines.push('');
      });
    }
  }

  lines.push('');
  lines.push(`## ${t('preAssignError.rawErrorMessage')}`);
  lines.push('```');
  lines.push(error);
  lines.push('```');

  return lines.join('\n');
}
