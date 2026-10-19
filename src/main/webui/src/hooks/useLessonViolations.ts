import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { LessonViolationAnalysis, ViolationInfo } from '../types/domain';
import { analyzeLessonViolations, type DateRange } from '../api/scheduleApi';

interface UseLessonViolationsOptions {
  solverStatus: string | undefined;
  enabled?: boolean;
  dateRange?: DateRange;  // Date range to fetch violations for
}

/**
 * Hook for managing lesson violation data.
 * Builds a Map for O(1) lookup by lesson ID.
 *
 * @param options.solverStatus - Current solver status to trigger refetch
 * @param options.enabled - Whether to fetch violations (skip during solving)
 * @param options.dateRange - Optional date range to filter violations
 */
export function useLessonViolations(options: UseLessonViolationsOptions) {
  const { solverStatus, enabled = true, dateRange } = options;
  const { i18n } = useTranslation();

  const [analysis, setAnalysis] = useState<LessonViolationAnalysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  // Track previous date range to detect changes
  const prevDateRangeRef = useRef<string | null>(null);
  // TASK-664 dedup: the effect re-runs whenever any dep identity changes (a
  // parent re-render creates a fresh dateRange object / fetchViolations
  // callback), which used to fire duplicate GET /analysis/lessons calls for
  // the same key. Track the last key actually fetched (completed) and any
  // in-flight request; identical requests are skipped. The manual refetch()
  // always bypasses this guard.
  const lastKeyRef = useRef<string | null>(null);
  const inFlightRef = useRef(false);
  // A request skipped because one was already in flight is not dropped: its
  // key is parked here and re-fetched when the in-flight one completes.
  const pendingKeyRef = useRef<string | null>(null);
  // TASK-661 stale-response guard: a forced refetch bypasses the in-flight skip
  // and races any request already running; the older response must never
  // overwrite the newer one. Every call gets a sequence number and only the
  // latest one may commit its result (or release the shared in-flight slot).
  const reqSeqRef = useRef(0);

  const fetchViolations = useCallback(async (force = false) => {
    if (!enabled) return;

    const rangeKey = dateRange ? `${dateRange.start}-${dateRange.end}` : 'all';
    const key = `${rangeKey}|${i18n.language}`;
    if (!force) {
      if (inFlightRef.current) {
        pendingKeyRef.current = key;
        return;
      }
      if (lastKeyRef.current === key) return;
    }

    const seq = ++reqSeqRef.current;
    inFlightRef.current = true;
    setLoading(true);
    setError(null);

    try {
      const data = await analyzeLessonViolations(dateRange, i18n.language);
      // Superseded response (a forced refetch started after this one): drop it.
      if (seq !== reqSeqRef.current) return;
      setAnalysis(data);
      lastKeyRef.current = key;
    } catch (e) {
      // 409 CONFLICT means solver is running - expected, not an error
      // Check axios error response status
      const status = (e as any)?.response?.status;
      if (status === 409) {
        return;
      }
      if (seq !== reqSeqRef.current) return;
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      // Release the shared slot only for the latest request: a superseded
      // response returning late must not unlock a newer request's slot early
      // (the newer one releases it in its own finally).
      if (seq === reqSeqRef.current) {
        inFlightRef.current = false;
        setLoading(false);
        // If a skipped request parked a different key while this one ran,
        // re-fetch it through the latest closure (current dateRange/language).
        const pending = pendingKeyRef.current;
        pendingKeyRef.current = null;
        if (pending && pending !== lastKeyRef.current) {
          void fetchViolationsRef.current();
        }
      }
    }
  }, [enabled, dateRange, i18n.language]);

  // Stable identity for consumers: refetch never changes across renders, so
  // effects depending on it do not re-fire on every parent re-render.
  const fetchViolationsRef = useRef(fetchViolations);
  fetchViolationsRef.current = fetchViolations;
  const refetch = useCallback(() => fetchViolationsRef.current(true), []);

  // Fetch when solver stops OR when date range changes
  useEffect(() => {
    const dateRangeKey = dateRange ? `${dateRange.start}-${dateRange.end}` : 'all';
    const dateRangeChanged = prevDateRangeRef.current !== dateRangeKey;

    if ((solverStatus === 'NOT_SOLVING' && enabled) || dateRangeChanged) {
      prevDateRangeRef.current = dateRangeKey;
      fetchViolations();
    }
  }, [solverStatus, enabled, fetchViolations, dateRange]);

  // Build O(1) lookup map - only HARD violations
  const violationMap = useMemo(() => {
    const map = new Map<number, ViolationInfo>();

    for (const item of analysis) {
      const hardViolations = item.violations.filter(v => v.level === 'HARD');
      if (hardViolations.length > 0) {
        map.set(item.lessonId, {
          lessonId: item.lessonId,
          violations: hardViolations,
          hasHard: true,
        });
      }
    }

    return map;
  }, [analysis]);

  // Get violations for a specific lesson (for tooltip)
  const getViolations = useCallback((lessonId: number): ViolationInfo | undefined => {
    return violationMap.get(lessonId);
  }, [violationMap]);

  // Count of distinct lessons with hard violations
  const violationCount = violationMap.size;

  // Get the first lesson with ANY violation (for auto-scroll)
  const firstViolation = useMemo(() => {
    if (analysis.length === 0) return null;
    // Return first item that has violations
    return analysis.find(item => item.violations.length > 0) || null;
  }, [analysis]);

  // Clear all violation data
  const clear = useCallback(() => {
    setAnalysis([]);
    setError(null);
  }, []);

  return {
    violationMap,
    getViolations,
    violationCount,
    firstViolation,
    loading,
    error,
    refetch,
    clear,
  };
}
