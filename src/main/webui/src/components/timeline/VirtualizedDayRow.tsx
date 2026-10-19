import { memo, useMemo, useState } from 'react';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { renderInlineCell, getViolationCellClass, renderViolationBadge } from './cellRenderer';
import { COLUMN_WIDTHS, CELL_STYLING, getColumnWidth, getGridTemplateColumns, lunchBorderTop, closedSlotStripeBackground, canDropLessonAt, isDragDropViewActive, type TimePeriod, type GroupByMode, type Column, type IdealEndData, type CompletionData } from './types';
import { isCompletionDayForGroup } from './CompletionMarker';
import type { Lesson, ViolationInfo } from '../../types/domain';

/**
 * Lightweight period data computed in Row
 */
export interface PeriodData {
  period: TimePeriod;
  timeslotId: number | null;
}

/**
 * Pre-computed data for a single day row
 */
export interface DayRowData {
  date: Date;
  dateStr: string;
  holiday: string | null;
  dayLabel: string;
  /** Compact label for mobile (e.g. "Mon 08/09") */
  shortDayLabel: string;
  isMonday: boolean;
}

/**
 * Props for VirtualizedDayRow
 */
export interface VirtualizedDayRowProps {
  data: DayRowData;
  periodData: PeriodData[];
  columns: Column[];
  groupBy: GroupByMode;
  stageDaysByGroup: Map<number, Set<string>>;
  idealEndDatesByGroup: Map<number, IdealEndData>;
  completionDatesByGroup: Map<number, CompletionData>;
  getLesson: (timeslotId: number, columnId: number) => Lesson | null;
  getColor: (subject: string, customColor?: string | null) => string;
  onCellClick: (lesson: Lesson) => void;
  violationMap?: Map<number, ViolationInfo>;
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean;
  draggingLesson?: Lesson | null;
  onLessonDragStart?: (lesson: Lesson) => void;
  onLessonDragEnd?: () => void;
  onLessonDrop?: (lesson: Lesson, timeslotId: number) => void;
}

/**
 * Check if a date is the ideal end date for any visible group
 */
function getIdealEndGroupsForDate(
  idealEndDatesByGroup: Map<number, IdealEndData>,
  columnIds: number[],
  dateStr: string
): Array<{ groupId: number; data: IdealEndData }> {
  const result: Array<{ groupId: number; data: IdealEndData }> = [];
  for (const groupId of columnIds) {
    const data = idealEndDatesByGroup.get(groupId);
    if (data) {
      const idealDateStr = format(data.idealEndDate, 'yyyy-MM-dd');
      if (idealDateStr === dateStr) {
        result.push({ groupId, data });
      }
    }
  }
  return result;
}

/**
 * Check if a date is the completion date for any visible group
 */
function getCompletionGroupsForDate(
  completionDatesByGroup: Map<number, CompletionData>,
  columnIds: number[],
  dateStr: string
): Array<{ groupId: number; data: CompletionData }> {
  const result: Array<{ groupId: number; data: CompletionData }> = [];
  for (const groupId of columnIds) {
    const data = completionDatesByGroup.get(groupId);
    if (data) {
      const completionDateStr = format(data.completionDate, 'yyyy-MM-dd');
      if (completionDateStr === dateStr) {
        result.push({ groupId, data });
      }
    }
  }
  return result;
}

/**
 * Renders a single day row with all time periods for virtualization.
 * Receives lightweight periodData and computes cells on-demand.
 */
function VirtualizedDayRowComponent({
  data,
  periodData,
  columns,
  groupBy,
  stageDaysByGroup,
  idealEndDatesByGroup,
  completionDatesByGroup,
  getLesson,
  getColor,
  onCellClick,
  violationMap,
  isSlotOpenForGroup,
  draggingLesson,
  onLessonDragStart,
  onLessonDragEnd,
  onLessonDrop,
}: VirtualizedDayRowProps) {
  const { t } = useTranslation('schedule');
  const { dateStr, holiday, dayLabel, shortDayLabel, isMonday } = data;
  const dragDropActive = isDragDropViewActive(groupBy, onLessonDragStart);
  const [hoveredDropKey, setHoveredDropKey] = useState<string | null>(null);

  // Every legal (period-column) drop target for this row's day - see
  // WeekViewGrid's identical memo for the full rationale (legality only
  // depends on `draggingLesson`, not on which cell is hovered).
  const legalDropKeys = useMemo(() => {
    if (!dragDropActive || !draggingLesson || holiday) return null;
    const set = new Set<string>();
    for (const pd of periodData) {
      for (const col of columns) {
        if (canDropLessonAt(draggingLesson, col.id, data.date, pd.period, isSlotOpenForGroup)) {
          set.add(`${dateStr}-${pd.period.startTime}-${col.id}`);
        }
      }
    }
    return set;
  }, [dragDropActive, draggingLesson, holiday, periodData, columns, data.date, dateStr, isSlotOpenForGroup]);

  // Check if any visible group has its ideal end date on this day
  const columnIds = columns.map(c => c.id);
  const idealEndGroups = groupBy === 'studentGroup'
    ? getIdealEndGroupsForDate(idealEndDatesByGroup, columnIds, dateStr)
    : [];

  // Check if any visible group has its completion date on this day
  const completionGroups = groupBy === 'studentGroup'
    ? getCompletionGroupsForDate(completionDatesByGroup, columnIds, dateStr)
    : [];

  // Create a map for quick lookup of ideal end data by group ID
  const idealEndByGroupId = new Map(idealEndGroups.map(g => [g.groupId, g.data]));
  const completionByGroupId = new Map(completionGroups.map(g => [g.groupId, g.data]));

  // TASK-525 round 4: the zone (stage/completion) 2px marker goes only on the
  // OUTER edges of the zone: borderLeft on the FIRST zone column, borderRight on
  // the LAST. Painting both sides on every zone cell made internal group-column
  // separators 2px inside the zone but 1px outside (thickness changing mid-day),
  // and adjacent zone columns could stack up to 4px on the shared separator.
  // Boundary ownership: when two DIFFERENT zones are adjacent, the left zone
  // keeps its right border and the right zone suppresses its left border on the
  // shared separator, so no vertical ever sums past 2px.
  const zoneBorders = useMemo(() => {
    const n = columns.length;
    const zone: Array<'stage' | 'completion' | null> = new Array(n).fill(null);
    columns.forEach((col, i) => {
      if (groupBy !== 'studentGroup') return;
      const ideal = idealEndDatesByGroup.get(col.id);
      if (ideal && dateStr > format(ideal.idealEndDate, 'yyyy-MM-dd')) {
        zone[i] = 'completion';
      } else if (stageDaysByGroup.get(col.id)?.has(dateStr)) {
        zone[i] = 'stage';
      }
    });
    // left border: column starts a zone AND the previous column's zone (if any)
    // is not itself ending here with its own right border.
    const left = zone.map((z, i) => {
      if (!z || (i > 0 && zone[i - 1] === z)) return false;
      return !(i > 0 && zone[i - 1] !== null);
    });
    // right border: column ends its zone.
    const right = zone.map((z, i) => {
      if (!z || (i < n - 1 && zone[i + 1] === z)) return false;
      return true;
    });
    return { zone, left, right };
  }, [columns, groupBy, stageDaysByGroup, idealEndDatesByGroup, dateStr]);

  return (
    <div
      data-testid={`day-row-${dateStr}`}
      style={{
        // TASK-525 round 3: SINGLE owner of the day boundary. The outer container
        // paints a uniform 2px top border for every day (Monday, first day, holidays);
        // the day-header cell below no longer paints its own top border, so the
        // separator never stacks (previously Monday = outer 3px + header 3px).
        borderTop: '2px solid var(--border)',
        background: holiday ? 'var(--holiday-bg)' : undefined,
        borderLeft: holiday ? '3px solid var(--holiday-border)' : undefined,
        borderRight: holiday ? '3px solid var(--holiday-border)' : undefined,
      }}
    >
      {/* Day header - stacked layout for better date visibility */}
      <div
        className="flex flex-col items-center justify-center px-1 sticky left-0 z-[5] border-r-2 border-border w-[52px] md:w-[80px] overflow-hidden"
        style={{
          background: holiday ? 'var(--holiday-bg)' : 'var(--surface)',
          // TASK-525 round 3: no borderTop here (the day container owns the day
          // boundary) and no borderBottom (the first period row below, or the
          // next day container, owns the separator under the time column).
          minHeight: '34px', // Match CELL_STYLING.minHeight for alignment
          height: holiday ? 'auto' : '34px', // Allow expansion for holidays
        }}
      >
        {/* Mobile: Single line compact */}
        <span className="md:hidden font-bold text-[0.65rem] text-center leading-tight whitespace-nowrap overflow-hidden text-ellipsis w-full">
          {shortDayLabel}
        </span>

        {/* Desktop: Stacked two-line layout */}
        <div className="hidden md:flex flex-col items-center leading-tight gap-0.5">
          <span className="font-bold text-[0.6rem] uppercase tracking-wider whitespace-nowrap">
            {dayLabel.split(' ')[0].substring(0, 3)}
          </span>
          <span className="text-[0.7rem] tabular-nums whitespace-nowrap" style={{ color: 'var(--text-secondary)' }}>
            {format(data.date, 'dd/MM')}
          </span>
        </div>

        {/* Holiday indicator - compact, no wrapping */}
        {holiday && (
          <span
            className="mt-0.5 px-1 py-0.5 rounded text-[0.65rem] font-bold whitespace-nowrap overflow-hidden text-ellipsis max-w-full"
            style={{
              background: 'var(--holiday-accent)',
              color: 'white',
            }}
            title={holiday}
            aria-label={`Holiday: ${holiday}`}
          >
            🏖️
          </span>
        )}
      </div>

      {/* Ideal end marker row - markers appear in their respective columns.
          TASK-525 round 3: border-t (not border-b) - each row owns the separator
          ABOVE itself, so the last period row never doubles with the next day's
          2px container border. */}
      {!holiday && idealEndGroups.length > 0 && (
        <div className="flex border-t border-light">
          {/* Spacer for time column */}
          <div className="shrink-0 w-[52px] md:w-[80px]" />
          {/* Markers in their respective columns */}
          {columns.map((col) => {
            const idealEndData = idealEndByGroupId.get(col.id);
            if (!idealEndData) {
              // Empty cell for columns without a marker
              return (
                <div
                  key={`marker-spacer-${col.id}`}
                  className="flex-shrink-0"
                  style={{ minWidth: getColumnWidth(groupBy), maxWidth: getColumnWidth(groupBy) }}
                />
              );
            }
            return (
              <div
                key={`ideal-end-${col.id}`}
                className="flex-shrink-0 flex items-center justify-center py-1"
                style={{ minWidth: getColumnWidth(groupBy), maxWidth: getColumnWidth(groupBy) }}
              >
                <span
                  className="inline-flex items-center px-2 py-0.5 rounded text-[0.7rem] font-semibold"
                  style={{
                    background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                    color: 'white',
                  }}
                  title={`Ideal end: ${format(idealEndData.idealEndDate, 'dd/MM/yyyy')} | ${idealEndData.totalHours}h | ${idealEndData.availableDays} available days`}
                >
                  🏁 {col.name}
                </span>
              </div>
            );
          })}
        </div>
      )}

      {/* Completion marker row - markers appear in their respective columns */}
      {!holiday && completionGroups.length > 0 && (
        <div className="flex border-t border-light">
          {/* Spacer for time column */}
          <div className="shrink-0 w-[52px] md:w-[80px]" />
          {/* Markers in their respective columns */}
          {columns.map((col) => {
            const completionData = completionByGroupId.get(col.id);
            if (!completionData) {
              // Empty cell for columns without a marker
              return (
                <div
                  key={`completion-spacer-${col.id}`}
                  className="flex-shrink-0"
                  style={{ minWidth: getColumnWidth(groupBy), maxWidth: getColumnWidth(groupBy) }}
                />
              );
            }
            return (
              <div
                key={`completion-${col.id}`}
                className="flex-shrink-0 flex items-center justify-center py-1"
                style={{ minWidth: getColumnWidth(groupBy), maxWidth: getColumnWidth(groupBy) }}
              >
                <span
                  className="inline-flex items-center px-2 py-0.5 rounded text-[0.7rem] font-semibold"
                  style={{
                    background: 'linear-gradient(135deg, #0891b2 0%, #67e8f9 100%)',
                    color: 'white',
                  }}
                  title={`Last lesson day: ${format(completionData.completionDate, 'dd/MM/yyyy')} | ${completionData.finalDayLessonCount} lessons | ${completionData.isPerfectlyCompacted ? 'Perfectly compacted' : 'Partial compaction'}`}
                >
                  ⚡ {col.name}
                </span>
              </div>
            );
          })}
        </div>
      )}

      {/* Period rows - skip if holiday */}
      {!holiday && (
        <div className="flex flex-col">
          {periodData.map((pd) => (
            <div
              key={`${dateStr}-${pd.period.startTime}`}
              data-testid={`period-row-${dateStr}-${pd.period.startTime}`}
              className={`flex border-t border-light ${CELL_STYLING.minHeight}`}
              style={{
                background: 'var(--surface)',
                borderTop: lunchBorderTop(pd.period.lunchBreakBefore),
              }}
            >
              {/* Time label - sticky */}
              <div
                className="font-semibold text-[0.6rem] md:text-[0.7rem] text-content whitespace-nowrap text-center align-middle px-1 py-[0.25rem] border-r-2 border-border sticky left-0 z-[5] w-[52px] md:w-[80px] flex items-center justify-center"
                style={{ background: 'var(--surface)' }}
                title={pd.period.lunchBreakBefore
                  ? t('lunchBreak', { start: pd.period.lunchBreakStart, end: pd.period.lunchBreakEnd })
                  : undefined}
              >
                <span className="hidden md:inline">{pd.period.label}</span>
                <span className="md:hidden">{pd.period.shortLabel}</span>
              </div>

              {/* Cells - compute on demand */}
              {columns.map((col, colIdx) => {
                // Closed slot per GroupCalendarRule resolution (only meaningful when columns ARE groups)
                const slotAvailable = groupBy !== 'studentGroup' || isSlotOpenForGroup(data.date, pd.period.index, col.id);
                if (!slotAvailable) {
                  return (
                    <div
                      key={`${col.id}-unavail`}
                      className="flex items-center justify-center flex-shrink-0 cursor-not-allowed"
                      style={{
                        background: closedSlotStripeBackground(),
                        minWidth: getColumnWidth(groupBy),
                        maxWidth: getColumnWidth(groupBy),
                      }}
                      title={t('closedByCalendar')}
                    >
                      <span className="text-gray-400 text-[0.6rem] font-medium">-</span>
                    </div>
                  );
                }

                // Compute cell data inline
                const lesson = pd.timeslotId != null ? getLesson(pd.timeslotId, col.id) : null;
                const isStageDay = (groupBy === 'studentGroup' && stageDaysByGroup.get(col.id)?.has(dateStr)) ?? false;
                const violationInfo = lesson && violationMap ? violationMap.get(lesson.id) : undefined;
                const violationClass = getViolationCellClass(violationInfo);

                // Check if this date is AFTER the ideal end date for this group
                let isAfterIdealEnd = false;
                if (groupBy === 'studentGroup') {
                  const idealEndData = idealEndDatesByGroup.get(col.id);
                  if (idealEndData) {
                    const idealDateStr = format(idealEndData.idealEndDate, 'yyyy-MM-dd');
                    isAfterIdealEnd = dateStr > idealDateStr;
                  }
                }

                // Manual placement (TASK-357): draggable lesson cells / droppable empty cells.
                const cellKey = `${dateStr}-${pd.period.startTime}-${col.id}`;
                const canDrag = dragDropActive && !!lesson && lesson.id > 0;
                const isDropTarget = dragDropActive && !lesson && !!draggingLesson;
                const dropLegal = isDropTarget && (legalDropKeys?.has(cellKey) ?? false);
                const isHoveredDrop = isDropTarget && hoveredDropKey === cellKey;

                return (
                  <div
                    key={col.id}
                    data-testid={`cell-${cellKey}`}
                    className={`grid items-center flex-shrink-0 min-w-0 ${lesson ? 'cursor-pointer' : 'cursor-default'} ${violationClass} ${isDropTarget && !dropLegal ? 'cursor-not-allowed' : ''}`}
                    draggable={canDrag}
                    onDragStart={canDrag ? (e) => {
                      e.dataTransfer.effectAllowed = 'move';
                      onLessonDragStart!(lesson!);
                    } : undefined}
                    onDragEnd={canDrag ? () => { setHoveredDropKey(null); onLessonDragEnd?.(); } : undefined}
                    onDragOver={isDropTarget ? (e) => {
                      if (dropLegal) {
                        e.preventDefault();
                        e.dataTransfer.dropEffect = 'move';
                      }
                    } : undefined}
                    onDragEnter={isDropTarget && dropLegal ? () => setHoveredDropKey(cellKey) : undefined}
                    onDragLeave={isDropTarget && dropLegal ? () => setHoveredDropKey(k => k === cellKey ? null : k) : undefined}
                    onDrop={isDropTarget ? (e) => {
                      e.preventDefault();
                      setHoveredDropKey(null);
                      if (dropLegal && draggingLesson && pd.timeslotId != null) {
                        onLessonDrop?.(draggingLesson, pd.timeslotId);
                      }
                    } : undefined}
                    style={{
                      background: isDropTarget
                        ? (dropLegal ? 'var(--feedback-success-bg)' : 'var(--feedback-error-bg)')
                        : isAfterIdealEnd ? 'var(--completion-bg)' : (isStageDay ? 'var(--stage-bg)' : 'transparent'),
                      // Stronger ring on the specific cell under the pointer - see WeekViewGrid's
                      // identical treatment.
                      outline: isHoveredDrop ? '2px solid var(--feedback-success-border)' : undefined,
                      outlineOffset: isHoveredDrop ? '-2px' : undefined,
                      gridTemplateColumns: getGridTemplateColumns(groupBy),
                      minWidth: getColumnWidth(groupBy),
                      maxWidth: getColumnWidth(groupBy),
                      gap: 0,
                      // TASK-525 round 4: zone edges only (see zoneBorders above) - internal
                      // separators stay the base uniform divider, so vertical thickness
                      // never changes along its length except at real zone boundaries.
                      borderLeft: zoneBorders.zone[colIdx] && zoneBorders.left[colIdx]
                        ? `2px solid var(--${zoneBorders.zone[colIdx] === 'completion' ? 'completion' : 'stage'}-border)`
                        : undefined,
                      borderRight: zoneBorders.zone[colIdx] && zoneBorders.right[colIdx]
                        ? `2px solid var(--${zoneBorders.zone[colIdx] === 'completion' ? 'completion' : 'stage'}-border)`
                        : undefined,
                    }}
                    onClick={() => lesson && onCellClick(lesson)}
                    title={
                      lesson
                        ? `${lesson.course?.subject || ''} | ${lesson.course?.teacher?.nickname || lesson.course?.teacher?.name || ''} | ${lesson.course?.studentGroup?.name || ''} | ${lesson.room?.name || ''}`
                        : isAfterIdealEnd ? 'After ideal end date' : (isStageDay ? 'Stage day - professionalizzanti only' : undefined)
                    }
                  >
                    {/* Inline cell rendering — seamless grid with no padding */}
                    {lesson ? renderInlineCell(lesson, groupBy, getColor) : (
                      <>
                        <div className="text-center min-w-0" />
                        <div className="text-center min-w-0" />
                        <div className="text-center min-w-0" />
                      </>
                    )}
                    {renderViolationBadge(violationInfo)}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Memoized day row component for virtualization.
 * Only re-renders when its data actually changes.
 */
export const VirtualizedDayRow = memo(VirtualizedDayRowComponent);

export default VirtualizedDayRow;
