import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '../api/scheduleApi';
import type { ValidationResult } from '../types/domain';
import ValidationPanel from './ValidationPanel';
import { subscribeValidationRefresh } from '../utils/validationRefreshBus';

const POLL_INTERVAL_MS = 15000;

/**
 * Global header badge for the solution legality watchdog (TASK-354).
 * Polls GET /api/schedule/validation/latest; hidden when the schedule has no
 * hard violations, amber when it does, red (with the newly-illegal count)
 * when the last debounced re-validation found NEW hard violations.
 */
export default function ValidationBadge() {
  const { t } = useTranslation('validation');
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);

  const poll = useCallback(async () => {
    try {
      const data = await api.getValidationLatest();
      setResult(data);
    } catch {
      // Transient network/backend errors: keep showing the last known state.
    }
  }, []);

  useEffect(() => {
    poll();
    const id = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [poll]);

  // TASK-357: force-refresh right after a manual lesson move, instead of
  // waiting up to POLL_INTERVAL_MS for the next scheduled tick.
  useEffect(() => subscribeValidationRefresh(poll), [poll]);

  if (!result || result.hard >= 0) {
    return null;
  }

  const newHardDeltas = result.newlyViolated.filter(d => d.hard < 0);
  const newHardCount = newHardDeltas.reduce((sum, d) => sum + d.delta, 0);
  // A baseline result's newlyViolated is always empty (nothing to diff against yet),
  // so this guard is redundant in practice - kept explicit so the red state can never
  // depend on baseline-run data even if that invariant ever changes upstream.
  const isRed = !result.baseline && newHardDeltas.length > 0;

  return (
    <>
      <button
        onClick={() => setPanelOpen(true)}
        className={`px-2.5 py-1 rounded-full text-xs font-semibold uppercase tracking-wide
                    cursor-pointer border transition-colors duration-150 ${
          isRed
            ? 'bg-feedback-error-bg text-feedback-error-text border-feedback-error-border'
            : 'bg-feedback-warning-bg text-feedback-warning-text border-feedback-warning-border'
        }`}
        aria-label={t('badgeLabel')}
        title={isRed
          ? (t('badgeTooltipRed', { count: newHardCount }) as string)
          : (t('badgeTooltipAmber', { count: Math.abs(result.hard) }) as string)}
      >
        {isRed ? t('newViolations', { count: newHardCount }) : t('hardViolations', { count: Math.abs(result.hard) })}
      </button>
      {panelOpen && <ValidationPanel result={result} onClose={() => setPanelOpen(false)} />}
    </>
  );
}
