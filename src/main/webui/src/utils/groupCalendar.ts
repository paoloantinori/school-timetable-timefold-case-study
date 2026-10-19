import type { GroupCalendarRule } from '../types/domain';
import { DAYS, type DayOfWeek } from '../data/dayConstants';
import i18n from '../i18n';

/**
 * Editor helpers for GroupCalendarAdmin.tsx (TASK-342/358). The per-group,
 * per-date resolution algorithm itself (most-specific-rule-wins) lives only
 * on the backend now (GroupCalendarService.resolveRange, served via
 * GET /api/group-calendar-rules/resolved) - this file used to also carry a
 * client-side port of that algorithm, which was a cross-language drift risk
 * with no tests of its own (TASK-358 code review N2). useScheduleData now
 * consumes the resolved endpoint directly instead.
 *
 * What's left here only builds/reads the "base rule" checkbox matrix; it
 * needs no resolution logic since the editor only ever manages unscoped
 * (effectiveFrom/effectiveTo both null) rules.
 */

/** True if a rule is a "base" rule (applies to every date, no override window). */
export function isBaseRule(rule: GroupCalendarRule): boolean {
  return rule.effectiveFrom == null && rule.effectiveTo == null;
}

/** Boolean open/closed matrix: matrix[dayIndex (0=Mon..4=Fri)][slotIndex]. */
export type CalendarMatrix = boolean[][];

/** All-open matrix, used when a group has no base rules yet (backward-compatible default). */
export function emptyOpenMatrix(slotCount: number): CalendarMatrix {
  return DAYS.map(() => Array(slotCount).fill(true));
}

/** Builds the checkbox matrix for one group from its base rules (ignores date-scoped overrides). */
export function baseRulesToMatrix(rules: GroupCalendarRule[], groupId: number, slotCount: number): CalendarMatrix {
  const matrix = emptyOpenMatrix(slotCount);
  const baseRules = rules.filter(r => r.studentGroup?.id === groupId && isBaseRule(r));
  DAYS.forEach((day, dayIndex) => {
    const rule = baseRules.find(r => r.dayOfWeek === day);
    if (!rule) return; // no rule for this weekday = fully open (backward compatible)
    for (let slot = 0; slot < slotCount; slot++) {
      matrix[dayIndex][slot] = slot >= rule.minSlot && slot <= rule.maxSlot;
    }
  });
  return matrix;
}

/** One resolved weekday range, ready to POST/PUT as a base GroupCalendarRule. */
export interface WeekdayRange {
  dayOfWeek: DayOfWeek;
  minSlot: number;
  maxSlot: number;
}

/**
 * Converts one weekday's checkbox row into a contiguous [minSlot, maxSlot] range
 * (or the closed-day sentinel if every slot is unchecked). Throws if the open
 * slots aren't contiguous - the backend model only supports a single range per
 * weekday, so a gapped selection (e.g. slots 0-2 and 5-7 open, 3-4 closed)
 * cannot be represented and must be rejected with a clear message.
 */
export function rowToRange(row: boolean[], dayOfWeek: DayOfWeek): WeekdayRange {
  const openIndices = row.reduce<number[]>((acc, open, i) => {
    if (open) acc.push(i);
    return acc;
  }, []);

  if (openIndices.length === 0) {
    return { dayOfWeek, minSlot: 0, maxSlot: -1 }; // closed-day sentinel (maxSlot < minSlot)
  }

  const min = openIndices[0];
  const max = openIndices[openIndices.length - 1];
  for (let slot = min; slot <= max; slot++) {
    if (!row[slot]) {
      // i18n.t (not the useTranslation hook) - this is a plain utility function,
      // not a component, so it can't call hooks (N7: this error is surfaced
      // straight to the user via the same StatusBanner as an API failure).
      throw new Error(
        i18n.t('admin:calendarNonContiguous', {
          day: DAY_LABEL(dayOfWeek),
          closedSlot: slot + 1,
          minSlot: min + 1,
          maxSlot: max + 1,
        })
      );
    }
  }
  return { dayOfWeek, minSlot: min, maxSlot: max };
}

function DAY_LABEL(day: DayOfWeek): string {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

/** Converts a full matrix into one WeekdayRange per weekday. Throws on the first non-contiguous row. */
export function matrixToRanges(matrix: CalendarMatrix): WeekdayRange[] {
  return matrix.map((row, dayIndex) => rowToRange(row, DAYS[dayIndex]));
}
