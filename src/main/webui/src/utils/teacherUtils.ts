import type { Teacher } from '../types/domain';

/**
 * Returns the display name for a teacher.
 * Prefers nickname if available, falls back to name.
 */
export function getDisplayName(teacher: Teacher | null | undefined): string {
  if (!teacher) return '';
  return teacher.nickname || teacher.name;
}
