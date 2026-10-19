import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  startOfWeek,
  addWeeks,
  subWeeks,
  format,
  parseISO,
  getDay,
  isSameDay,
} from 'date-fns';
import type {
  ViewMode,
  ScheduleNavigation,
} from '../components/timeline/types';

/** Return the week's Monday for a given date */
function weekMonday(d: Date): Date {
  return startOfWeek(d, { weekStartsOn: 1 });
}

/** Return the week days Mon-Fri for a given week start (Monday) */
function weekDays(monday: Date): Date[] {
  const days: Date[] = [];
  for (let i = 0; i < 5; i++) {
    const day = new Date(monday);
    day.setDate(monday.getDate() + i);
    days.push(day);
  }
  return days;
}

/**
 * Hook for schedule navigation logic
 *
 * Handles week navigation, view mode persistence, and date localization
 */
export function useScheduleNavigation(
  yearRange: { start: Date; end: Date } | null,
  allDays: Date[],
  listRef?: React.MutableRefObject<{
    scrollToRow?: (config: {
      index: number;
      align?: 'auto' | 'center' | 'end' | 'start' | 'smart';
      behavior?: 'auto' | 'smooth' | 'instant';
    }) => void;
  } | undefined>
): ScheduleNavigation {
  const { t } = useTranslation('schedule');

  // Today's week is a neutral placeholder until the schedule's timeslots
  // arrive (TASK-382: no hardcoded school-year constants; the range is
  // API-derived via yearRange). The init effect below snaps to the year's
  // first week once yearRange is known.
  const [currentWeekStart, setCurrentWeekStart] = useState<Date>(
    () => weekMonday(new Date())
  );
  const [viewMode, setViewMode] = useState<ViewMode>(() =>
    (localStorage.getItem('scheduleViewMode') as ViewMode) || 'week'
  );

  // Snap to the school year's first week exactly ONCE, when yearRange first
  // becomes available. Guarded by a ref so schedule refetches (polling, a
  // solve) that re-derive the same yearRange don't reset the user's chosen
  // week back to the start.
  const snappedToYearStart = useRef(false);
  useEffect(() => {
    if (!snappedToYearStart.current && yearRange) {
      snappedToYearStart.current = true;
      setCurrentWeekStart(weekMonday(yearRange.start));
    }
  }, [yearRange]);

  // Persist view mode preference
  useEffect(() => {
    localStorage.setItem('scheduleViewMode', viewMode);
  }, [viewMode]);

  // ---- Localized date helpers ----

  const localizedDate = useCallback((d: Date): string => {
    const day = d.getDate();
    const months = t('months', { returnObjects: true }) as string[];
    const month = months[d.getMonth()];
    const year = d.getFullYear();
    return `${day} ${month} ${year}`;
  }, [t]);

  const localizedDayName = useCallback((d: Date): string => {
    const days = t('days', { returnObjects: true }) as string[];
    return days[getDay(d)];
  }, [t]);

  // ---- Week navigation ----

  const goToday = useCallback(() => {
    const now = new Date();
    // Clamp "today" into the school year when we know its bounds; before the
    // schedule loads (yearRange null) just go to the real current week.
    if (yearRange && (now < yearRange.start || now > yearRange.end)) {
      setCurrentWeekStart(weekMonday(yearRange.start));
    } else {
      setCurrentWeekStart(weekMonday(now));
    }
  }, [yearRange]);

  const goPrev = useCallback(() => {
    setCurrentWeekStart(prev => {
      const candidate = subWeeks(prev, 1);
      const earliest = yearRange ? weekMonday(yearRange.start) : null;
      return earliest && candidate < earliest ? prev : candidate;
    });
  }, [yearRange]);

  const goNext = useCallback(() => {
    setCurrentWeekStart(prev => {
      const candidate = addWeeks(prev, 1);
      const latest = yearRange ? weekMonday(yearRange.end) : null;
      return latest && candidate > latest ? prev : candidate;
    });
  }, [yearRange]);

  const atStart = useMemo(() => {
    if (!yearRange) return false;
    return currentWeekStart <= weekMonday(yearRange.start);
  }, [currentWeekStart, yearRange]);

  const atEnd = useMemo(() => {
    if (!yearRange) return false;
    return currentWeekStart >= weekMonday(yearRange.end);
  }, [currentWeekStart, yearRange]);

  const days = useMemo(() => weekDays(currentWeekStart), [currentWeekStart]);

  const weekEnd = useMemo(() => {
    const fri = new Date(currentWeekStart);
    fri.setDate(currentWeekStart.getDate() + 4);
    return fri;
  }, [currentWeekStart]);

  const weekLabel = `${localizedDate(currentWeekStart)} \u2013 ${localizedDate(weekEnd)}`;

  // ---- Jump to date (for infinite view) ----

  const jumpToDate = useCallback((date: Date) => {
    const index = allDays.findIndex(d => isSameDay(d, date));
    if (index >= 0 && listRef?.current?.scrollToRow) {
      listRef.current.scrollToRow({ index, align: 'center' });
    }
  }, [allDays, listRef]);

  return {
    currentWeekStart,
    setCurrentWeekStart,
    viewMode,
    setViewMode,
    atStart,
    atEnd,
    weekLabel,
    weekEnd,
    days,
    goPrev,
    goNext,
    goToday,
    localizedDate,
    localizedDayName,
    jumpToDate,
  };
}

export { weekMonday };
