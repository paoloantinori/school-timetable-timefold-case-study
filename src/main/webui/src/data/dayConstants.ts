/**
 * Shared constants for day-of-week selection in admin components.
 */

export const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] as const;

export const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mer',
  THURSDAY: 'Gio',
  FRIDAY: 'Ven',
};

export type DayOfWeek = typeof DAYS[number];
