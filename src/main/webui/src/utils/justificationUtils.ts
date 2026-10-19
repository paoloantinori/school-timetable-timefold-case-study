import type { AnyJustification } from '../types/domain';

/**
 * Render structured justification as human-readable string.
 * NOTE: This displays raw entity IDs for debugging. For production,
 * prefer backend-formatted messages via /api/schedule/analysis/lessons
 * which provide full i18n support and entity name resolution.
 */
export function renderJustification(justification: AnyJustification): string {
  switch (justification['@type']) {
    case 'TeacherConflict':
      return `Teacher ID: ${justification.teacherId}, Lessons: ${justification.lesson1Id} & ${justification.lesson2Id}`;
    case 'RoomConflict':
      return `Room ID: ${justification.roomId}, Lessons: ${justification.lesson1Id} & ${justification.lesson2Id}`;
    case 'StudentGroupConflict':
      return `Group ID: ${justification.groupId}, Lessons: ${justification.lesson1Id} & ${justification.lesson2Id}`;
    case 'TeacherDailyHours':
      return `${justification.lessonCount} lessons on ${justification.date} (max: ${justification.maxHours}h, excess: ${justification.excessHours}h)`;
    case 'StudentGroupDailyHours':
      return `${justification.lessonCount} lessons on ${justification.date} (max: ${justification.maxHours}h, excess: ${justification.excessHours}h)`;
    case 'SparseDay':
      return `Only ${justification.actualHours}h on ${justification.date} (required: ${justification.requiredHours}h)`;
    default:
      // Log unknown types for monitoring
      const unknown = justification as Record<string, unknown>;
      console.warn('[Justification] Unknown type:', unknown['@type'], unknown);
      return `[Unknown: ${unknown['@type']}]`;
  }
}
