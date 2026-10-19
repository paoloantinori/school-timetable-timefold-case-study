import type { Lesson, Timeslot, ViolationInfo } from '../../types/domain';

/**
 * Column widths for the timeline grid
 */
export const COLUMN_WIDTHS = {
  timeColumn: '80px',  // Desktop width (mobile uses w-[52px] responsive class)
  subjectCol: '85px',
  teacherCol: '120px',
  roomCol: '80px',
  groupCol: '100px',
} as const;

/**
 * Shared cell styling constants for Week and Timeline views
 * Ensures consistent visual appearance across both views
 */
export const CELL_STYLING = {
  /** Cell padding (matches Tailwind py-1 px-1 = 4px) */
  paddingClass: 'py-1 px-1',
  /** Exact cell height for Timeline view (33px content + 1px border = 34px total with border-box) */
  minHeight: 'h-[34px]',
  /** Border between sub-columns */
  subColumnBorderClass: 'border-r border-faint',
  /** Last sub-column border (thicker) */
  lastSubColumnBorder: 'border-r-2 border-border-light',
} as const;

/** Width/style of the lunch-break separator border, shared by every rendering context (table row, flex row, admin matrix row) so they stay visually consistent (TASK-358). Only the color var varies by context. */
const LUNCH_SEPARATOR_WIDTH = '3px double';

/**
 * Top-border style for the lunch-break separator, or undefined when this period
 * doesn't follow the lunch break. `colorVar` lets holiday/admin contexts use a
 * different border color while keeping the width/style uniform.
 */
export function lunchBorderTop(lunchBreakBefore: boolean, colorVar: string = 'var(--border)'): string | undefined {
  return lunchBreakBefore ? `${LUNCH_SEPARATOR_WIDTH} ${colorVar}` : undefined;
}

/**
 * Diagonal stripe background for a slot closed by the group calendar, shared by
 * every rendering context (Week grid, Timeline virtualized rows) so the visual
 * treatment stays identical wherever it's used (TASK-358 code review N11 - this
 * literal used to be duplicated verbatim in both places).
 */
export function closedSlotStripeBackground(): string {
  return 'repeating-linear-gradient(45deg, var(--surface-hover), var(--surface-hover) 5px, var(--border-light) 5px, var(--border-light) 10px)';
}

/**
 * Time period definition for grid rows. Fetched from /api/config/slots
 * (see hooks/useSlotSchema.ts) instead of hardcoded, so afternoon slots and
 * schema changes render without a frontend deploy (TASK-358).
 */
export interface TimePeriod {
  /** Canonical slot index (0-7), matching TimeslotSequence.SLOT_START_TIMES / GroupCalendarRule.minSlot-maxSlot. */
  index: number;
  startTime: string;
  endTime: string;
  label: string;
  /** Compact label for mobile (e.g. "8–9") */
  shortLabel: string;
  /** True for slots after the lunch break (14:30, 15:30 in the 2026/27 calendar). */
  isAfternoon: boolean;
  /** True if there's a schedule gap (the lunch break) between the previous period and this one. */
  lunchBreakBefore: boolean;
  /** Lunch-break window, denormalized onto every period from the schema (for the tooltip, N5). */
  lunchBreakStart: string;
  lunchBreakEnd: string;
}

/**
 * Afternoon-slot legality for manual placement. TASK-379 demoted the server
 * rule from hard H21 to the medium preference M28 (the school's own golden
 * timetable uses 575 real afternoon lessons as capacity relief), so a human
 * planner may place ANY lesson in an open afternoon slot - the placement just
 * carries a medium penalty, like any other preference the coordinator
 * overrides on purpose. Kept as a named hook (rather than deleted) so a
 * future "warn on afternoon drop" affordance has one obvious seam.
 */
export function isH21LegalForLesson(lesson: Lesson, period: TimePeriod): boolean {
  return true;
}

/**
 * Whether `lesson` may be manually placed into (day, period) under `columnId`
 * (TASK-357). Mirrors H20 (group calendar) client-side for drag-drop
 * dragover feedback and the LessonTooltip Move picker - both call this so
 * the two surfaces can't silently drift. Afternoon slots are allowed since
 * TASK-379 (M28 is a medium preference, not a hard rule - see
 * isH21LegalForLesson). A
 * lesson may only land in its OWN student group's column: Course.studentGroup
 * is fixed, so a drop under a different column can never be legal regardless
 * of calendar openness. Every other hard constraint (teacher/room conflicts,
 * H1/H2/H7...) is NOT checked here - the server-side re-validation
 * (POST /schedule/validation/run) is the source of truth for those.
 */
export function canDropLessonAt(
  lesson: Lesson,
  columnId: number,
  day: Date,
  period: TimePeriod,
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean
): boolean {
  const groupId = lesson.course?.studentGroup?.id;
  if (groupId == null || groupId !== columnId) return false;
  if (!isSlotOpenForGroup(day, period.index, groupId)) return false;
  return isH21LegalForLesson(lesson, period);
}

/**
 * Whether drag-drop should be wired up at all for the current view (TASK-357).
 * Single source of truth for the `groupBy === 'studentGroup'` restriction -
 * WeekViewGrid and VirtualizedDayRow both call this instead of duplicating the
 * check, so the two views can't drift on it. Only meaningful when columns ARE
 * student groups: `canDropLessonAt`'s own `groupId !== columnId` guard already
 * rejects a mismatched column, but that relies on comparing IDs that live in
 * different entity spaces (teacher/room vs. student group) when groupBy isn't
 * 'studentGroup' - gating the feature off entirely here is the correct fix,
 * not a coincidence of non-colliding ID ranges.
 */
export function isDragDropViewActive(
  groupBy: GroupByMode,
  onLessonDragStart: ((lesson: Lesson) => void) | undefined
): boolean {
  return groupBy === 'studentGroup' && !!onLessonDragStart;
}

/**
 * Calculate total column width based on groupBy mode
 * @param groupBy - The grouping mode
 * @returns CSS calc() string for total width
 */
export function getColumnWidth(groupBy: GroupByMode): string {
  switch (groupBy) {
    case 'studentGroup':
      return `calc(${COLUMN_WIDTHS.subjectCol} + ${COLUMN_WIDTHS.teacherCol} + ${COLUMN_WIDTHS.roomCol})`;
    case 'room':
      return `calc(${COLUMN_WIDTHS.subjectCol} + ${COLUMN_WIDTHS.groupCol} + ${COLUMN_WIDTHS.teacherCol})`;
    case 'teacher':
      return `calc(${COLUMN_WIDTHS.subjectCol} + ${COLUMN_WIDTHS.groupCol} + ${COLUMN_WIDTHS.roomCol})`;
  }
}

/**
 * Get grid template columns based on groupBy mode
 * @param groupBy - The grouping mode
 * @returns CSS grid-template-columns string
 */
export function getGridTemplateColumns(groupBy: GroupByMode): string {
  switch (groupBy) {
    case 'studentGroup':
      return `${COLUMN_WIDTHS.subjectCol} ${COLUMN_WIDTHS.teacherCol} ${COLUMN_WIDTHS.roomCol}`;
    case 'room':
      return `${COLUMN_WIDTHS.subjectCol} ${COLUMN_WIDTHS.groupCol} ${COLUMN_WIDTHS.teacherCol}`;
    case 'teacher':
      return `${COLUMN_WIDTHS.subjectCol} ${COLUMN_WIDTHS.groupCol} ${COLUMN_WIDTHS.roomCol}`;
  }
}

/**
 * Build a map from timeslot key (date-startTime) to timeslot ID
 * @param timeslots - Array of timeslots
 * @returns Map with keys like "2026-03-18-08:00" -> timeslotId
 */
export function buildTimeslotMap(timeslots: Timeslot[]): Map<string, number> {
  const map = new Map<string, number>();
  for (const ts of timeslots) {
    const date = ts.startDateTime.substring(0, 10);
    const startTime = ts.startDateTime.substring(11, 16);
    map.set(`${date}-${startTime}`, ts.id);
  }
  return map;
}

/**
 * Group-by modes for the schedule view
 */
export type GroupByMode = 'studentGroup' | 'room' | 'teacher';

/**
 * View modes for the timeline
 */
export type ViewMode = 'week' | 'infinite';

/**
 * Column entity for the grid
 * When groupBy is 'studentGroup', this is a StudentGroup object with maxHoursPerDay.
 * When groupBy is 'room' or 'teacher', maxHoursPerDay is undefined.
 */
export interface Column {
  id: number;
  name: string;
  maxHoursPerDay?: number | null;
}

/**
 * Lookup maps for lessons by different groupings
 */
export interface LessonLookup {
  byGroup: Map<string, Lesson>;
  byRoom: Map<string, Lesson>;
  byTeacher: Map<string, Lesson>;
}

/**
 * Type for CSS variable styling with subject color
 */
export type CSSVarsWithSubjectColor = React.CSSProperties & {
  '--subject-color'?: string;
};

/**
 * Props for the TimelineHeader component
 */
export interface TimelineHeaderProps {
  viewMode: ViewMode;
  setViewMode: (mode: ViewMode) => void;
  groupBy: GroupByMode;
  setGroupBy: (mode: GroupByMode) => void;
  currentWeekStart: Date;
  weekLabel: string;
  atStart: boolean;
  atEnd: boolean;
  goPrev: () => void;
  goNext: () => void;
  goToday: () => void;
  yearRangeLabel: string | null;
  hiddenCount: number;
  onShowAll: () => void;
  allDaysCount: number;
  yearRange: { start: Date; end: Date } | null;
  onJumpToDate: (date: Date) => void;
  /** TASK-659: null when the current grouping+filter has no single printable
   *  subject (e.g. no teacher/class selected, or the room grouping). */
  printHref: string | null;
  printHint: string;
}

/**
 * Props for the WeekViewGrid component
 */
export interface WeekViewGridProps {
  schedule: {
    timeslots: Timeslot[];
    schoolHolidays: Array<{ date: string; description: string }>;
  } | null;
  timePeriods: TimePeriod[];
  days: Date[];
  columns: Column[];
  holidayMap: Map<string, string>;
  stageDaysByGroup: Map<number, Set<string>>;
  idealEndDatesByGroup: Map<number, IdealEndData>;
  completionDatesByGroup: Map<number, CompletionData>;
  groupBy: GroupByMode;
  subColumnLabels: string[];
  getLesson: (timeslotId: number, columnId: number) => Lesson | null;
  getColor: (subject: string) => string;
  localizedDayName: (d: Date) => string;
  onCellClick: (lesson: Lesson) => void;
  violationMap?: Map<number, ViolationInfo>;
  slotUtilizationByName?: Map<string, import('../../types/domain').SlotUtilization>;
  /** True if the given group has this slot open on this date (GroupCalendarRule resolution, TASK-358). */
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean;
  /** Manual placement (TASK-357): the lesson currently being dragged, or null. Drag-drop is only
   *  wired up when groupBy === 'studentGroup' - columns are student groups there, so drop legality
   *  (canDropLessonAt) can be evaluated directly against the column being hovered. */
  draggingLesson?: Lesson | null;
  onLessonDragStart?: (lesson: Lesson) => void;
  onLessonDragEnd?: () => void;
  /** Called on a legal drop onto an empty cell with the target cell's timeslot id. */
  onLessonDrop?: (lesson: Lesson, timeslotId: number) => void;
}

/**
 * Props for the TimelineView component
 */
export interface TimelineViewProps {
  allDays: Date[];
  holidayMap: Map<string, string>;
  timePeriods: TimePeriod[];
  columns: Column[];
  groupBy: GroupByMode;
  stageDaysByGroup: Map<number, Set<string>>;
  idealEndDatesByGroup: Map<number, IdealEndData>;
  completionDatesByGroup: Map<number, CompletionData>;
  schedule: { timeslots: Timeslot[] } | null;
  getLesson: (timeslotId: number, columnId: number) => Lesson | null;
  getColor: (subject: string, customColor?: string | null) => string;
  localizedDayName: (d: Date) => string;
  onCellClick: (lesson: Lesson) => void;
  height: number;
  violationMap?: Map<number, ViolationInfo>;
  slotUtilizationByName?: Map<string, import('../../types/domain').SlotUtilization>;
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean;
  draggingLesson?: Lesson | null;
  onLessonDragStart?: (lesson: Lesson) => void;
  onLessonDragEnd?: () => void;
  onLessonDrop?: (lesson: Lesson, timeslotId: number) => void;
}

/**
 * Pre-computed data for a single period row (for virtualization)
 */
export interface PeriodRowData {
  period: TimePeriod;
  timeslotId: number | null;
  slotAvailable: boolean;
  cells: Array<{
    columnId: number;
    lesson: Lesson | null;
    isStageDay: boolean;
  }>;
}

/**
 * Pre-computed data for a single day row (for virtualization)
 */
export interface DayRowData {
  date: Date;
  dateStr: string;
  holiday: string | null;
  dayLabel: string;
  isMonday: boolean;
  periodRows: PeriodRowData[];
}

/**
 * Navigation hook return type
 */
export interface ScheduleNavigation {
  currentWeekStart: Date;
  setCurrentWeekStart: (date: Date) => void;
  viewMode: ViewMode;
  setViewMode: (mode: ViewMode) => void;
  atStart: boolean;
  atEnd: boolean;
  weekLabel: string;
  weekEnd: Date;
  days: Date[];
  goPrev: () => void;
  goNext: () => void;
  goToday: () => void;
  localizedDate: (d: Date) => string;
  localizedDayName: (d: Date) => string;
  jumpToDate: (date: Date) => void;
}

/**
 * Ideal end date data for a student group
 */
export interface IdealEndData {
  /** The calculated ideal last day of school for this group */
  idealEndDate: Date;
  /** Total required hours for this group */
  totalHours: number;
  /** Number of school days excluded (holidays, weekends, stage days) */
  excludedDays: number;
  /** Number of available school days (used in calculation) */
  availableDays: number;
}

/**
 * Completion date data for a student group
 * The theoretical first day after all lessons assuming full horizontal compaction
 */
export interface CompletionData {
  /** The last day any lesson exists for this group */
  completionDate: Date;
  /** Number of lessons on the final day */
  finalDayLessonCount: number;
  /** Whether this represents perfect horizontal compaction (no sparse days) */
  isPerfectlyCompacted: boolean;
}

/**
 * Data hook return type
 */
export interface ScheduleData {
  yearRange: { start: Date; end: Date } | null;
  allDays: Date[];
  holidayMap: Map<string, string>;
  stageDaysByGroup: Map<number, Set<string>>;
  idealEndDatesByGroup: Map<number, IdealEndData>;
  completionDatesByGroup: Map<number, CompletionData>;
  lessonLookup: LessonLookup;
  allColumns: Column[];
  columns: Column[];
  subColumnLabels: string[];
  timePeriods: TimePeriod[];
  getLesson: (timeslotId: number, columnId: number) => Lesson | null;
  slotUtilizationByName: Map<string, import('../../types/domain').SlotUtilization>;
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean;
  /** Non-blocking: set when GET /api/config/slots failed (falls back to FALLBACK_TIME_PERIODS). */
  slotSchemaError: string | null;
  /** Non-blocking: set when GET /api/group-calendar-rules/resolved failed (falls back to fully-open). */
  groupCalendarError: string | null;
}
