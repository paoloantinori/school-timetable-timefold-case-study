import type { SchoolSchedule } from '../types/domain';

/**
 * True when at least one lesson has a placed timeslot, i.e. the schedule holds
 * a real delivery rather than an empty/reset timetable. Shared predicate for
 * the "guard a destructive action on an existing delivery" checks.
 */
export function hasPlacedLessons(schedule?: SchoolSchedule | null): boolean {
  return schedule?.lessons?.some(l => l.timeslot != null) ?? false;
}
