import { useTranslation } from 'react-i18next';
import type { ValidationResult } from '../types/domain';

interface Props {
  result: ValidationResult;
  onClose: () => void;
}

/**
 * Lightweight panel for the solution legality watchdog (TASK-354).
 * Styled to match AnalysisPanel's modal chrome, but scoped to a single
 * concern: per-constraint match counts from the latest re-validation run,
 * with newly-illegal constraints highlighted.
 */
export default function ValidationPanel({ result, onClose }: Props) {
  const { t } = useTranslation('validation');
  const newlyViolatedIds = new Set(result.newlyViolated.map(d => d.constraintId));

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div
      className="fixed inset-0 bg-overlay flex items-center justify-center z-modal"
      onClick={handleOverlayClick}
      role="presentation"
    >
      <div
        className="bg-surface rounded-xl w-[90%] max-w-[95vw] md:max-w-[600px] max-h-[80vh]
                   flex flex-col shadow-[0_8px_32px_var(--shadow)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="validation-title"
      >
        <header className="flex justify-between items-center px-4 md:px-6 py-3 md:py-4
                           border-b border-border-light">
          <h3 id="validation-title" className="m-0 text-xl font-semibold text-content">
            {t('panelTitle')}
          </h3>
          <button
            className="bg-none border-none text-2xl cursor-pointer text-muted p-1 leading-none"
            onClick={onClose}
            aria-label={t('common:close')}
          >
            &times;
          </button>
        </header>

        <div className="p-4 md:p-6 overflow-y-auto flex-1">
          <div className="text-xs text-muted mb-4 font-mono">
            {t('lastChecked')}: {new Date(result.timestamp).toLocaleString()}
            {' — '}{t('trigger')}: {result.trigger}
            {result.baseline && <> — <span className="italic">{t('baselineNote')}</span></>}
          </div>

          {result.perConstraint.length === 0 ? (
            <div className="text-center p-8 text-muted">{t('noViolations')}</div>
          ) : (
            <table className="min-w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th className="p-2 text-left bg-surface-alt border-b-2 border-border font-semibold text-muted">
                    {t('constraintId')}
                  </th>
                  <th className="p-2 text-left bg-surface-alt border-b-2 border-border font-semibold text-muted">
                    {t('matches')}
                  </th>
                  <th className="p-2 text-left bg-surface-alt border-b-2 border-border font-semibold text-muted">
                    {t('newlyViolated')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.perConstraint.map(c => {
                  const isNew = newlyViolatedIds.has(c.constraintId);
                  return (
                    <tr key={c.constraintId} className={isNew ? 'bg-feedback-error-bg' : ''}>
                      <td className="p-2 border-b border-border-faint font-mono font-semibold text-content">
                        {c.constraintId}
                      </td>
                      <td className="p-2 border-b border-border-faint text-content">
                        {c.matchCount.toLocaleString()}
                      </td>
                      <td className="p-2 border-b border-border-faint">
                        {isNew && (
                          <span className="inline-block px-2 py-0.5 rounded text-xs font-semibold uppercase
                                           bg-feedback-error-bg text-feedback-error-text">
                            {t('newlyViolated')}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
