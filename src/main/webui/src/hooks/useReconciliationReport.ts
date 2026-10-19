import { useCallback, useEffect, useState } from 'react';
import { getReconciliation } from '../api/scheduleApi';
import type { ReconciliationReport, ReconciliationRow } from '../types/domain';

/**
 * Whether a row counts as an actionable issue: a real (non-pseudo) row that
 * didn't reconcile. Single source of truth for the frontend so the nav
 * badge (useReconciliationBadge.ts) and the page's subtotal bucketing
 * (ReconciliationPage.tsx) can't drift apart from each other - both import
 * this instead of re-declaring the predicate. Mirrors
 * ReconciliationService.isIssue on the backend.
 */
export function isReconciliationIssue(row: ReconciliationRow): boolean {
  return !row.pseudo && row.status !== 'OK';
}

/**
 * How long a cached report is considered fresh enough to reuse without a
 * network round-trip. The nav badge (background, low-stakes) polls at this
 * cadence and tolerates the staleness; the Reconciliation page forces a
 * fresh fetch on every mount instead (see `forceRefreshOnMount` below),
 * because a user who opens the dashboard wants today's numbers, not
 * up-to-TTL-old ones.
 */
export const RECONCILIATION_CACHE_TTL_MS = 30_000;

/**
 * Module-level in-flight + TTL cache so the nav badge (mounted in App.tsx,
 * always present) and the Reconciliation page (mounted alongside it under
 * the same nav) share one /api/reconciliation fetch instead of firing it
 * twice on every page visit. TASK-353 code review (G1): the original cache
 * never expired, so both consumers were frozen at the first-ever fetch for
 * the whole session - this adds a TTL plus an explicit invalidation hook
 * (`invalidateReconciliation`) so the numbers can move again.
 */
let cachedRequest: Promise<ReconciliationReport> | null = null;
let cachedAt = 0;

function fetchReconciliation(): Promise<ReconciliationReport> {
  // Checked inline (rather than via a helper) so TypeScript can narrow
  // cachedRequest to non-null on the `return` below.
  if (cachedRequest === null || Date.now() - cachedAt > RECONCILIATION_CACHE_TTL_MS) {
    cachedAt = Date.now();
    cachedRequest = getReconciliation().catch(err => {
      cachedRequest = null; // don't cache failures - allow retry on next call
      throw err;
    });
  }
  return cachedRequest;
}

/**
 * Forces the next read (by any caller - badge or page) to hit the network
 * again instead of serving the cached promise. Wired to the Reconciliation
 * page's mount and its "Refresh" button today; exported so future mutation
 * sites (course edits, lesson pin/assign, solve/reset) can call it too,
 * closing the gap where the dashboard would otherwise show numbers stale by
 * up to RECONCILIATION_CACHE_TTL_MS after a change that affects
 * assigned/scheduled hours.
 */
export function invalidateReconciliation(): void {
  cachedRequest = null;
  cachedAt = 0;
}

export interface UseReconciliationReportResult {
  report: ReconciliationReport | null;
  loading: boolean;
  error: boolean;
  /** Invalidates the cache and refetches immediately. */
  refresh: () => void;
}

export interface UseReconciliationReportOptions {
  /**
   * Invalidate the cache on mount, ignoring the TTL, so this consumer always
   * starts from a fresh fetch. The page passes this; the badge omits it and
   * accepts up-to-TTL-stale cached data (see module docstring).
   */
  forceRefreshOnMount?: boolean;
}

/**
 * Fetches the reconciliation report (shared across all callers via the
 * TTL + in-flight cache above) and exposes loading/error as plain state, so
 * consumers resolve any error message at render time instead of inside the
 * fetch effect (avoids `t` in a dependency array re-triggering a refetch on
 * every language switch).
 */
export function useReconciliationReport(options?: UseReconciliationReportOptions): UseReconciliationReportResult {
  const [report, setReport] = useState<ReconciliationReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const forceRefreshOnMount = options?.forceRefreshOnMount ?? false;

  useEffect(() => {
    let cancelled = false;
    if (forceRefreshOnMount) invalidateReconciliation();
    setLoading(true);
    setError(false);
    fetchReconciliation()
      .then(r => {
        if (!cancelled) setReport(r);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // forceRefreshOnMount is a mount-time flag (stable per caller), not meant
    // to re-trigger the effect on its own - only reloadToken should do that.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadToken]);

  const refresh = useCallback(() => {
    invalidateReconciliation();
    setReloadToken(t => t + 1);
  }, []);

  return { report, loading, error, refresh };
}
