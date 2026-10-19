import { useMemo, useCallback, useRef, useState, useEffect } from 'react';
import {
  format,
  parseISO,
} from 'date-fns';
import type { SchoolSchedule, Teacher, Lesson, Timeslot, SlotUtilization } from '../types/domain';
import type { CompletionData, GroupByMode, Column, LessonLookup, TimePeriod, ScheduleData, IdealEndData } from '../components/timeline/types';
import { getDisplayName } from '../utils/teacherUtils';
import { getCompletionData, getResolvedGroupCalendar } from '../api/scheduleApi';
import { useSlotSchema } from './useSlotSchema';
import { getErrorMessage } from '../utils/apiError';

// Vibrant categorical palette — 12 distinct hues for subject fallback coloring.
// Matches TEACHER_COLORS in DataGenerator.java.
/** IBM Design Colorblind-safe 5 palette */
const COLORS = [
  '#648fff', '#785ef0', '#dc267f', '#fe6100', '#ffb000',
];

/** Return the week's Monday for a given date */
function weekMonday(d: Date): Date {
  const d2 = new Date(d);
  const day = d2.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d2.setDate(d2.getDate() + diff);
  return d2;
}

/** Format time from ISO string */
function formatTime(isoStr: string): string {
  const d = parseISO(isoStr);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

/**
 * Hook for schedule data computations
 *
 * Handles year range calculation, lesson lookups, column sorting,
 * and stage day computations
 */
export function useScheduleData(
  schedule: SchoolSchedule | null,
  teachers: Teacher[],
  groupBy: GroupByMode,
  filterState: { teachers: Set<number>; subjects: Set<string>; groups: Set<number> },
  hasActiveFilters: boolean,
  isHidden: (id: number) => boolean,
  solverStatus?: string
): ScheduleData {
  // Subject color map (persistent across renders)
  // ---- Year range from timeslot data ----

  const yearRange = useMemo(() => {
    if (!schedule || schedule.timeslots.length === 0) return null;
    let minDate = schedule.timeslots[0].startDateTime.substring(0, 10);
    let maxDate = schedule.timeslots[0].startDateTime.substring(0, 10);
    for (const ts of schedule.timeslots) {
      const d = ts.startDateTime.substring(0, 10);
      if (d < minDate) minDate = d;
      if (d > maxDate) maxDate = d;
    }
    return { start: parseISO(minDate), end: parseISO(maxDate) };
  }, [schedule]);

  // ---- All days in school year (for infinite view) ----

  const allDays = useMemo(() => {
    // Before the schedule's timeslots arrive there is no year to enumerate
    // (TASK-382: no hardcoded school-year fallback - the range is derived
    // from the payload's timeslot span).
    if (!yearRange) return [];
    const { start, end } = yearRange;
    const days: Date[] = [];

    let current = new Date(start);
    while (current <= end) {
      const dayOfWeek = current.getDay();
      if (dayOfWeek >= 1 && dayOfWeek <= 5) { // Mon-Fri only
        days.push(new Date(current));
      }
      current.setDate(current.getDate() + 1);
    }
    return days;
  }, [yearRange]);

  // ---- Holiday map ----

  const holidayMap = useMemo(() => {
    if (!schedule) return new Map<string, string>();
    const map = new Map<string, string>();
    for (const h of schedule.schoolHolidays) {
      map.set(h.date, h.description);
    }
    return map;
  }, [schedule]);

  // ---- Stage days per student group ----

  const stageDaysByGroup = useMemo(() => {
    if (!schedule) return new Map<number, Set<string>>();

    const map = new Map<number, Set<string>>();

    for (const sp of schedule.stagePatterns) {
      if (!sp.studentGroup) continue;
      const groupId = sp.studentGroup.id;

      let existing = map.get(groupId);
      if (!existing) {
        existing = new Set<string>();
        map.set(groupId, existing);
      }

      // Parse day-of-week mask
      const dayNumbers = sp.dayOfWeekMask.map(dayName => {
        const dayMap: Record<string, number> = {
          'MONDAY': 1, 'TUESDAY': 2, 'WEDNESDAY': 3,
          'THURSDAY': 4, 'FRIDAY': 5
        };
        return dayMap[dayName] ?? -1;
      }).filter(d => d >= 0);

      // Iterate through date range
      const startDate = parseISO(sp.startDate);
      const endDate = parseISO(sp.endDate);
      let currentDate = startDate;

      while (currentDate <= endDate) {
        const dayOfWeek = currentDate.getDay();
        if (dayNumbers.includes(dayOfWeek)) {
          existing.add(format(currentDate, 'yyyy-MM-dd'));
        }
        currentDate = new Date(currentDate);
        currentDate.setDate(currentDate.getDate() + 1);
      }
    }

    return map;
  }, [schedule]);

  // ---- Ideal end dates per student group ----
  // Read pre-computed data from server-side (TargetEndDateCalculator)
  // This ensures consistency with the solver's value range filtering

  const idealEndDatesByGroup = useMemo((): Map<number, IdealEndData> => {
    if (!schedule) return new Map();

    const map = new Map<number, IdealEndData>();

    for (const group of schedule.studentGroups) {
      if (group.idealEndDateInfo) {
        map.set(group.id, {
          idealEndDate: parseISO(group.idealEndDateInfo.idealEndDate),
          totalHours: group.idealEndDateInfo.totalHours,
          excludedDays: group.idealEndDateInfo.excludedDays,
          availableDays: group.idealEndDateInfo.availableDays,
        });
      }
    }

    return map;
  }, [schedule]);

  // Create a serializable filter key to force recalculation when filter changes
  const filterKey = useMemo(() => {
    return JSON.stringify({
      teachers: [...filterState.teachers].sort(),
      subjects: [...filterState.subjects].sort(),
      groups: [...filterState.groups].sort(),
    });
  }, [filterState]);

  // ---- Lesson lookup maps ----

  const lessonLookup = useMemo((): LessonLookup => {
    if (!schedule) return { byGroup: new Map(), byRoom: new Map(), byTeacher: new Map() };

    const byGroup = new Map<string, Lesson>();
    const byRoom = new Map<string, Lesson>();
    const byTeacher = new Map<string, Lesson>();

    for (const lesson of schedule.lessons) {
      if (!lesson.timeslot) continue;

      // Inline filter logic for reliable dependency tracking
      const teacherId = lesson.course?.teacher?.id;
      const subject = lesson.course?.subject;
      const groupId = lesson.course?.studentGroup?.id;

      let passes = true;
      if (hasActiveFilters) {
        if (filterState.teachers.size > 0 && (teacherId === undefined || !filterState.teachers.has(teacherId))) {
          passes = false;
        }
        if (passes && filterState.subjects.size > 0 && (subject === undefined || !filterState.subjects.has(subject))) {
          passes = false;
        }
        if (passes && filterState.groups.size > 0 && (groupId === undefined || !filterState.groups.has(groupId))) {
          passes = false;
        }
      }

      if (!passes) continue;
      const tsId = lesson.timeslot.id;

      if (lesson.course?.studentGroup) {
        byGroup.set(`${tsId}-${lesson.course.studentGroup.id}`, lesson);
      }
      if (lesson.room) {
        byRoom.set(`${tsId}-${lesson.room.id}`, lesson);
      }
      if (lesson.course?.teacher) {
        byTeacher.set(`${tsId}-${lesson.course.teacher.id}`, lesson);
      }
    }

    return { byGroup, byRoom, byTeacher };
  }, [schedule, filterKey, hasActiveFilters, filterState]);

  // ---- Student group sort by profile ----

  const studentGroupSortKey = useCallback((name: string): [number, number, string] => {
    const profileOrder: Record<string, number> = {
      'FB': 0, 'FA': 1, 'FD': 2, 'MODA': 3, 'FC': 4
    };
    const match = name.match(/^(\d+)\s+(.+)$/);
    if (match) {
      const year = parseInt(match[1]);
      const profile = match[2];
      const order = profileOrder[profile] ?? 99;
      return [order, year, name];
    }
    return [100, 0, name];
  }, []);

  // ---- All columns sorted ----

  const allColumns = useMemo((): Column[] => {
    if (!schedule) return [];
    switch (groupBy) {
      case 'studentGroup':
        return [...schedule.studentGroups].sort((a, b) => {
          const ka = studentGroupSortKey(a.name);
          const kb = studentGroupSortKey(b.name);
          if (ka[0] !== kb[0]) return ka[0] - kb[0];
          if (ka[1] !== kb[1]) return ka[1] - kb[1];
          return ka[2].localeCompare(kb[2]);
        });
      case 'room':
        return [...schedule.rooms].sort((a, b) => a.name.localeCompare(b.name));
      case 'teacher':
        return [...teachers].sort((a, b) => a.name.localeCompare(b.name));
    }
  }, [schedule, groupBy, teachers, studentGroupSortKey]);

  // Filter columns to exclude hidden groups and respect groups filter
  const columns = useMemo(() => {
    if (groupBy !== 'studentGroup') return allColumns;
    return allColumns.filter(col => {
      // First check if manually hidden via hide button
      if (isHidden(col.id)) return false;
      // If groups filter is active, only show selected groups as columns
      if (filterState.groups.size > 0 && !filterState.groups.has(col.id)) return false;
      return true;
    });
  }, [allColumns, groupBy, isHidden, filterState.groups]);

  // ---- Sub-column labels per group-by mode ----

  const subColumnLabels = useMemo((): string[] => {
    // These will be translated by the component using them
    switch (groupBy) {
      case 'studentGroup':
        return ['subjectCol', 'teacherCol', 'roomCol'];
      case 'room':
        return ['subjectCol', 'studentGroupCol', 'teacherCol'];
      case 'teacher':
        return ['subjectCol', 'studentGroupCol', 'roomCol'];
    }
  }, [groupBy]);

  // ---- Time periods (data-driven, TASK-358) ----
  const { periods: timePeriods, error: slotSchemaError } = useSlotSchema();

  // ---- Group calendar rules (TASK-342/358) ----
  // Resolved server-side (GroupCalendarService.resolveRange via /group-calendar-rules/resolved,
  // TASK-358 code review N2) for the visible school year, instead of fetching every raw rule
  // and re-running the most-specific-rule-wins algorithm per cell on the client. Re-fetched
  // only when the *value* of the date span changes, not on every poll tick: `schedule` (and
  // therefore `yearRange`, itself a fresh object every fetch) is re-fetched every 500ms-2s
  // while solving, but the actual min/max dates rarely move - keying the effect on the
  // stringified span (a primitive, so value-equal across renders) avoids re-hitting the
  // endpoint for every unchanged poll.
  const [groupCalendarByKey, setGroupCalendarByKey] = useState<Map<string, { minSlot: number; maxSlot: number; closed: boolean }>>(new Map());
  const [groupCalendarError, setGroupCalendarError] = useState<string | null>(null);

  const yearRangeKey = useMemo(() => {
    if (!yearRange) return null;
    return `${format(yearRange.start, 'yyyy-MM-dd')}|${format(yearRange.end, 'yyyy-MM-dd')}`;
  }, [yearRange]);

  useEffect(() => {
    if (!yearRangeKey) return;
    const [from, to] = yearRangeKey.split('|');
    let cancelled = false;
    getResolvedGroupCalendar(from, to).then(entries => {
      if (cancelled) return;
      const map = new Map<string, { minSlot: number; maxSlot: number; closed: boolean }>();
      for (const e of entries) {
        map.set(`${e.groupId}|${e.date}`, { minSlot: e.minSlot, maxSlot: e.maxSlot, closed: e.closed });
      }
      setGroupCalendarByKey(map);
      setGroupCalendarError(null);
    }).catch(e => {
      if (cancelled) return;
      // Falls back to "fully open" (backward compatible) but surfaces the failure (N6)
      // instead of swallowing it.
      const message = getErrorMessage(e, 'Failed to load group calendar');
      console.error('Failed to load resolved group calendar:', message);
      setGroupCalendarError(message);
    });
    return () => { cancelled = true; };
  }, [yearRangeKey]);

  const isSlotOpenForGroup = useCallback((day: Date, slotIndex: number, groupId: number): boolean => {
    const dateStr = format(day, 'yyyy-MM-dd');
    const entry = groupCalendarByKey.get(`${groupId}|${dateStr}`);
    if (!entry) return true; // no resolved entry (e.g. weekend, outside fetched range) - default open
    return !entry.closed && slotIndex >= entry.minSlot && slotIndex <= entry.maxSlot;
  }, [groupCalendarByKey]);

  // ---- Lookup lesson for a cell ----

  const getLesson = useCallback((timeslotId: number, columnId: number): Lesson | null => {
    const key = `${timeslotId}-${columnId}`;
    switch (groupBy) {
      case 'studentGroup':
        return lessonLookup.byGroup.get(key) ?? null;
      case 'room':
        return lessonLookup.byRoom.get(key) ?? null;
      case 'teacher':
        return lessonLookup.byTeacher.get(key) ?? null;
    }
  }, [lessonLookup, groupBy]);

  // ---- Completion dates per student group ----
  // Build from studentGroup.completionInfo populated by backend
  const completionDatesByGroup = useMemo((): Map<number, CompletionData> => {
    if (!schedule) return new Map();

    const map = new Map<number, CompletionData>();

    for (const group of schedule.studentGroups) {
      if (group.completionInfo) {
        const info = group.completionInfo;
        map.set(group.id, {
          completionDate: new Date(info.completionDate),
          finalDayLessonCount: info.finalDayLessonCount,
          isPerfectlyCompacted: info.isPerfectlyCompacted,
        });
      }
    }

    return map;
  }, [schedule]);

  // ---- Slot utilization per student group ----
  // Fetched from /api/completion endpoint when solver stops
  const [slotUtilizationByName, setSlotUtilizationByName] = useState<Map<string, SlotUtilization>>(new Map());

  useEffect(() => {
    // Fetch slot utilization when solver stops or on initial load
    const shouldFetch = !solverStatus || solverStatus === 'NOT_SOLVING' || solverStatus === 'SOLVED';
    if (shouldFetch && schedule) {
      getCompletionData().then(data => {
        const map = new Map<string, SlotUtilization>();
        for (const slot of data.slotUtilization) {
          map.set(slot.groupName, slot);
        }
        setSlotUtilizationByName(map);
      }).catch(() => {
        // Silently fail - slot utilization is optional for display
      });
    }
  }, [solverStatus, schedule]);

  return {
    yearRange,
    allDays,
    holidayMap,
    stageDaysByGroup,
    idealEndDatesByGroup,
    completionDatesByGroup,
    lessonLookup,
    allColumns,
    columns,
    subColumnLabels,
    timePeriods,
    getLesson,
    slotUtilizationByName,
    isSlotOpenForGroup,
    slotSchemaError,
    groupCalendarError,
  };
}

/** Find the timeslot ID for a given date + time period */
export function findTimeslotId(
  timeslots: Timeslot[],
  day: Date,
  period: TimePeriod,
): number | null {
  const dayStr = format(day, 'yyyy-MM-dd');
  for (const ts of timeslots) {
    const tsDate = ts.startDateTime.substring(0, 10);
    if (tsDate === dayStr) {
      const tsStart = formatTime(ts.startDateTime);
      const tsEnd = formatTime(ts.endDateTime);
      if (tsStart === period.startTime && tsEnd === period.endTime) {
        return ts.id;
      }
    }
  }
  return null;
}

/**
 * Get a consistent color for a subject
 * Uses a shared color map passed in as a ref
 * If a custom color is provided, it takes precedence
 */
export function getColor(
  colorMap: React.MutableRefObject<Map<string, string>>,
  subject: string,
  customColor?: string | null
): string {
  // If custom color is set, use it directly
  if (customColor) {
    return customColor;
  }
  if (!colorMap.current.has(subject)) {
    colorMap.current.set(
      subject,
      COLORS[colorMap.current.size % COLORS.length],
    );
  }
  return colorMap.current.get(subject)!;
}

/**
 * Pattern type for visual variations on lesson cards
 * - striped: Reserved for future use
 * - dotted: Lab courses - hands-on practical work
 * - hatched: Pinned lessons - locked by scheduler
 * - gradient: Stage periods - transitional internship time
 */
export type LessonPattern = 'striped' | 'dotted' | 'hatched' | 'gradient' | null;

/**
 * Get the background color and pattern for a lesson.
 *
 * Color priority: Teacher color > Course color > Subject fallback
 * Pattern: Based on lesson/course context (lab, pinned)
 */
export function getLessonStyle(
  colorMap: React.MutableRefObject<Map<string, string>>,
  lesson: Lesson
): { bgColor: string; pattern: LessonPattern } {
  const course = lesson.course;
  const teacher = course?.teacher;

  // Priority: Teacher color > Course color > Subject fallback
  const bgColor = teacher?.color
    ?? course?.color
    ?? getColor(colorMap, course?.subject || '?');

  // Use teacher's stored pattern as visual identity
  const teacherPattern = teacher?.pattern;
  const pattern: LessonPattern = (teacherPattern && teacherPattern !== 'plain')
    ? teacherPattern as LessonPattern : null;

  return { bgColor, pattern };
}
