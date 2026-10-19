import { useEffect, useState } from 'react';
import type { TimePeriod } from '../components/timeline/types';
import { getSlotSchema } from '../api/scheduleApi';
import { getErrorMessage } from '../utils/apiError';

const FALLBACK_LUNCH_START = '14:00';
const FALLBACK_LUNCH_END = '14:30';

/**
 * Static fallback matching the 2026/27 8-slot/day calendar
 * (TimeslotSequence.SLOT_START_TIMES / lunch break 14:00-14:30, TASK-341).
 * Used before the backend response arrives and by tests/stories that don't
 * mock /api/config/slots. Kept in sync manually with the backend - see
 * SlotConfigResourceTest for the authoritative values. lunchBreakStart/End are
 * schema-wide, not per-period, so they're spread onto every row once here
 * instead of repeating the two literals eight times.
 */
export const FALLBACK_TIME_PERIODS: TimePeriod[] = ([
  { index: 0, startTime: '08:00', endTime: '09:00', label: '08:00 - 09:00', shortLabel: '8–9', isAfternoon: false, lunchBreakBefore: false },
  { index: 1, startTime: '09:00', endTime: '10:00', label: '09:00 - 10:00', shortLabel: '9–10', isAfternoon: false, lunchBreakBefore: false },
  { index: 2, startTime: '10:00', endTime: '11:00', label: '10:00 - 11:00', shortLabel: '10–11', isAfternoon: false, lunchBreakBefore: false },
  { index: 3, startTime: '11:00', endTime: '12:00', label: '11:00 - 12:00', shortLabel: '11–12', isAfternoon: false, lunchBreakBefore: false },
  { index: 4, startTime: '12:00', endTime: '13:00', label: '12:00 - 13:00', shortLabel: '12–13', isAfternoon: false, lunchBreakBefore: false },
  { index: 5, startTime: '13:00', endTime: '14:00', label: '13:00 - 14:00', shortLabel: '13–14', isAfternoon: false, lunchBreakBefore: false },
  { index: 6, startTime: '14:30', endTime: '15:30', label: '14:30 - 15:30', shortLabel: '14:30–15:30', isAfternoon: true, lunchBreakBefore: true },
  { index: 7, startTime: '15:30', endTime: '16:30', label: '15:30 - 16:30', shortLabel: '15:30–16:30', isAfternoon: true, lunchBreakBefore: false },
] as Omit<TimePeriod, 'lunchBreakStart' | 'lunchBreakEnd'>[]).map(p => ({
  ...p,
  lunchBreakStart: FALLBACK_LUNCH_START,
  lunchBreakEnd: FALLBACK_LUNCH_END,
}));

function shortLabelFor(startTime: string, endTime: string): string {
  // Compact form for whole-hour times ("8–9"); keep the ":MM" for half-hour boundaries ("14:30–15:30").
  const compact = (t: string) => (t.endsWith(':00') ? String(Number(t.slice(0, 2))) : t);
  return `${compact(startTime)}–${compact(endTime)}`;
}

export interface SlotSchemaResult {
  periods: TimePeriod[];
  /** Set (and console.error'd) when GET /api/config/slots failed; periods stay on the fallback. */
  error: string | null;
}

/**
 * Fetches the slot schema from /api/config/slots and maps it to TimePeriod rows
 * for the timeline grid (TASK-358). Falls back to FALLBACK_TIME_PERIODS while
 * loading and on fetch failure, so the grid always has something to render.
 */
export function useSlotSchema(): SlotSchemaResult {
  const [periods, setPeriods] = useState<TimePeriod[]>(FALLBACK_TIME_PERIODS);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getSlotSchema().then(schema => {
      if (cancelled) return;
      // lunchBreakBefore is derived from the schema's own lunchBreakEnd (N5) - a period
      // "follows lunch" iff it starts exactly when lunch ends - instead of inferring it
      // from a gap between consecutive endTime/startTime, which broke down for the first
      // slot of the day (no previous slot to compare against) and duplicated the lunch
      // window's boundary logic that the backend already computed.
      const mapped: TimePeriod[] = schema.slots.map(s => ({
        index: s.index,
        startTime: s.startTime,
        endTime: s.endTime,
        label: `${s.startTime} - ${s.endTime}`,
        shortLabel: shortLabelFor(s.startTime, s.endTime),
        isAfternoon: s.isAfternoon,
        lunchBreakBefore: s.startTime === schema.lunchBreakEnd,
        lunchBreakStart: schema.lunchBreakStart,
        lunchBreakEnd: schema.lunchBreakEnd,
      }));
      setPeriods(mapped);
      setError(null);
    }).catch(e => {
      if (cancelled) return;
      // Keep the fallback on failure - better a stale-but-correct-for-most-of-the-year
      // grid than an empty one - but surface the failure instead of swallowing it (N6).
      const message = getErrorMessage(e, 'Failed to load slot schema');
      console.error('Failed to load slot schema:', message);
      setError(message);
    });
    return () => { cancelled = true; };
  }, []);

  return { periods, error };
}
