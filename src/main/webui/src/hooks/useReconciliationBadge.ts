import { useEffect } from 'react';
import { isReconciliationIssue, RECONCILIATION_CACHE_TTL_MS, useReconciliationReport } from './useReconciliationReport';

/**
 * Actionable badge count for the Reconciliation nav entry (TASK-353).
 * Scoped to this page's own nav link only - the global header badge
 * (any mismatch/violation across the whole app) belongs to the legality
 * watchdog (TASK-354), not this hook.
 *
 * Excludes pseudo (Stage/Esame/RA) rows: their deltas are expected by
 * design (see claudedocs/design/stage-esame-ra-modeling-2026-07-16.md),
 * so counting them here would keep the badge permanently non-zero even
 * when every real course reconciles. Uses the shared isReconciliationIssue
 * predicate (also used by ReconciliationPage.tsx's subtotal bucketing),
 * which mirrors ReconciliationService.isIssue on the backend.
 *
 * Polls at the cache TTL cadence instead of fetching once at mount: this is
 * a background indicator, so up to one TTL window of staleness is an
 * accepted tradeoff (see useReconciliationReport.ts docstring). The page
 * itself forces a fresh fetch on every visit instead of polling.
 */
export function useReconciliationBadge(): number {
  const { report, refresh } = useReconciliationReport();

  useEffect(() => {
    const interval = setInterval(refresh, RECONCILIATION_CACHE_TTL_MS);
    return () => clearInterval(interval);
  }, [refresh]);

  if (!report) return 0;
  return report.rows.filter(isReconciliationIssue).length;
}
