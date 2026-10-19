import type { ReactNode } from 'react';
import { getDisplayName } from '../../utils/teacherUtils';
import { autoTextColor, hexToRgba } from '../../utils/colorUtils';
import type { GroupByMode, CSSVarsWithSubjectColor } from './types';
import type { Lesson, ViolationInfo } from '../../types/domain';
import type { LessonPattern } from '../../hooks/useScheduleData';

/** TASK-530b/c: display-only room name. Pseudo-Stage slots are out of school
 *  by nature: label them STAGE instead of an empty (red-looking) cell. */
function displayRoomName(lesson: any): string {
  if (lesson?.room?.name) return lesson.room.name;
  const t = lesson?.course?.teacher;
  if (t?.name === 'Stage' || t?.pseudo) return 'STAGE';
  return '';
}

/**
 * Get CSS class for a lesson pattern
 */
function getPatternClass(pattern: LessonPattern): string {
  if (!pattern) return '';
  return `pattern-${pattern}`;
}

/**
 * Renders subject badge with color + optional pattern + pin indicator.
 * Shared between both render functions below.
 */
function subjectBadge(
  lesson: Lesson,
  getColor: (subject: string, customColor?: string | null) => string
): ReactNode {
  const subject = lesson.course?.subject || '?';
  const course = lesson.course;

  // Priority: Teacher color > Course color > Subject fallback
  const bgColor = course?.teacher?.color
    ?? course?.color
    ?? getColor(subject);

  // Use teacher's stored pattern as visual identity
  const teacherPattern = course?.teacher?.pattern;
  const pattern: LessonPattern = (teacherPattern && teacherPattern !== 'plain')
    ? teacherPattern as LessonPattern : null;

  const pinned = lesson.pinned ? '\u{1F512}' : '';
  const patternClass = getPatternClass(pattern);
  const textColor = autoTextColor(bgColor);
  const bgRgba = hexToRgba(bgColor, 0.7);

  // Dark shadow for white text, light shadow for dark text
  const shadowColor = textColor === '#ffffff'
    ? 'rgba(0,0,0,0.45)'
    : 'rgba(255,255,255,0.6)';

  return (
    <span
      className={`inline-block rounded-full px-[0.5rem] whitespace-nowrap overflow-hidden text-ellipsis text-center w-full ${patternClass}`}
      style={{
        '--subject-color': bgColor,
        backgroundColor: bgRgba,
        color: textColor,
        fontSize: '14px',
        fontWeight: 700,
        height: '24px',
        lineHeight: '24px',
        textShadow: `0 1px 2px ${shadowColor}`,
      } as CSSVarsWithSubjectColor & React.CSSProperties}
    >
      {pinned}{subject}
    </span>
  );
}

/** Muted text span used for teacher/group/room secondary info */
function mutedSpan(text: string): ReactNode {
  return (
    <span className="text-[0.65rem] text-muted whitespace-nowrap overflow-hidden text-ellipsis text-center inline-block w-full">
      {text}
    </span>
  );
}

/** Faint text span used for the third column */
function faintSpan(text: string): ReactNode {
  return (
    <span className="text-[0.65rem] text-faint whitespace-nowrap overflow-hidden text-ellipsis text-center inline-block w-full">
      {text}
    </span>
  );
}

// --- Sub-column content by groupBy mode ---

type CellTriple = [ReactNode, ReactNode, ReactNode];

function tripleFor(lesson: Lesson, groupBy: GroupByMode, getColor: (s: string, c?: string | null) => string): CellTriple {
  const badge = subjectBadge(lesson, getColor);
  switch (groupBy) {
    case 'studentGroup':
      return [badge, mutedSpan(getDisplayName(lesson.course?.teacher)), faintSpan(displayRoomName(lesson))];
    case 'room':
      return [badge, mutedSpan(lesson.course?.studentGroup?.name || ''), faintSpan(getDisplayName(lesson.course?.teacher))];
    case 'teacher':
      return [badge, mutedSpan(lesson.course?.studentGroup?.name || ''), faintSpan(displayRoomName(lesson))];
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Returns content for a single `<td>` sub-column in WeekViewGrid.
 * Plain function — zero React component / memo overhead.
 */
export function renderSubColumnContent(
  lesson: Lesson | null,
  subIdx: number,
  groupBy: GroupByMode,
  getColor: (subject: string, customColor?: string | null) => string
): ReactNode {
  if (!lesson) return null;
  return tripleFor(lesson, groupBy, getColor)[subIdx];
}

/**
 * Returns all 3 sub-columns as `<div>` elements (Fragment) for VirtualizedDayRow.
 * Plain function — zero React component / memo overhead.
 */
export function renderInlineCell(
  lesson: Lesson,
  groupBy: GroupByMode,
  getColor: (subject: string, customColor?: string | null) => string
): ReactNode {
  const [col0, col1, col2] = tripleFor(lesson, groupBy, getColor);
  return (
    <>
      <div className="text-center min-w-0">{col0}</div>
      <div className="text-center min-w-0">{col1}</div>
      <div className="text-center min-w-0">{col2}</div>
    </>
  );
}

// ---------------------------------------------------------------------------
// Violation highlighting helpers
// ---------------------------------------------------------------------------

/**
 * Returns CSS classes for cell wrapper based on violation status.
 */
export function getViolationCellClass(violationInfo?: ViolationInfo): string {
  if (!violationInfo || !violationInfo.hasHard) return '';
  return 'violation-cell';
}

/**
 * Returns violation badge element for cell wrapper.
 */
export function renderViolationBadge(violationInfo?: ViolationInfo): ReactNode {
  if (!violationInfo || !violationInfo.hasHard) return null;

  return (
    <span
      className="violation-badge"
      data-count={violationInfo.violations.length}
      aria-label={`${violationInfo.violations.length} hard constraint violation${violationInfo.violations.length > 1 ? 's' : ''}`}
    />
  );
}
