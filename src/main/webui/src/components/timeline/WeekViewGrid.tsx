import { Fragment, useMemo, useState } from 'react';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import { renderSubColumnContent, getViolationCellClass, renderViolationBadge } from './cellRenderer';
import { getDisplayName } from '../../utils/teacherUtils';
import { COLUMN_WIDTHS, buildTimeslotMap, getGridTemplateColumns, lunchBorderTop, closedSlotStripeBackground, canDropLessonAt, isDragDropViewActive, type TimePeriod, type IdealEndData } from './types';
import type { WeekViewGridProps } from './types';

const COL_WIDTHS = COLUMN_WIDTHS;

/**
 * Fast O(1) timeslot lookup using pre-built map
 */
function getTimeslotId(
  timeslotMap: Map<string, number>,
  day: Date,
  period: TimePeriod
): number | null {
  const dateStr = format(day, 'yyyy-MM-dd');
  const key = `${dateStr}-${period.startTime}`;
  return timeslotMap.get(key) ?? null;
}

/**
 * Pre-format ideal end dates as 'yyyy-MM-dd' strings for efficient comparison
 */
function buildIdealEndStrings(
  idealEndDatesByGroup: Map<number, IdealEndData>
): Map<number, string> {
  const map = new Map<number, string>();
  for (const [id, data] of idealEndDatesByGroup) {
    map.set(id, format(data.idealEndDate, 'yyyy-MM-dd'));
  }
  return map;
}

/**
 * Week view grid component
 *
 * Renders the table-based weekly schedule with days as row groups
 * and periods as sub-rows
 */
export function WeekViewGrid({
  schedule,
  timePeriods,
  days,
  columns,
  holidayMap,
  stageDaysByGroup,
  groupBy,
  subColumnLabels,
  getLesson,
  getColor,
  localizedDayName,
  onCellClick,
  violationMap,
  idealEndDatesByGroup,
  slotUtilizationByName,
  isSlotOpenForGroup,
  draggingLesson,
  onLessonDragStart,
  onLessonDragEnd,
  onLessonDrop,
}: WeekViewGridProps) {
  const { t } = useTranslation('schedule');
  const totalSubCols = columns.length * subColumnLabels.length;
  const dragDropActive = isDragDropViewActive(groupBy, onLessonDragStart);
  // Cell currently under the pointer during a drag - purely a rendering
  // concern local to this grid, unlike `draggingLesson` (shared app-wide so
  // the UnassignedSidebar can be a drag source into either grid).
  const [hoveredDropKey, setHoveredDropKey] = useState<string | null>(null);

  // Build timeslot lookup map once for O(1) access
  const timeslotMap = useMemo(
    () => buildTimeslotMap(schedule?.timeslots || []),
    [schedule?.timeslots]
  );

  // Pre-format ideal end dates to avoid calling format() in hot render loops
  const idealEndStrings = useMemo(
    () => buildIdealEndStrings(idealEndDatesByGroup),
    [idealEndDatesByGroup]
  );

  // Every legal (day-period-column) drop target for the lesson currently being
  // dragged. Legality only depends on `draggingLesson`, not on which cell is
  // hovered, so this must be memoized on the drag itself - recomputing
  // canDropLessonAt (a Map lookup plus a date-fns format() call) for every
  // empty cell on every hoveredDropKey change would be pure waste.
  const legalDropKeys = useMemo(() => {
    if (!dragDropActive || !draggingLesson) return null;
    const set = new Set<string>();
    for (const day of days) {
      const dateStr = format(day, 'yyyy-MM-dd');
      for (const period of timePeriods) {
        for (const col of columns) {
          if (canDropLessonAt(draggingLesson, col.id, day, period, isSlotOpenForGroup)) {
            set.add(`${dateStr}-${period.startTime}-${col.id}`);
          }
        }
      }
    }
    return set;
  }, [dragDropActive, draggingLesson, days, timePeriods, columns, isSlotOpenForGroup]);

  return (
    <div className="flex-1 bg-surface rounded-lg shadow-soft overflow-auto relative max-h-[70vh] md:max-h-[calc(100vh-180px)]">
      <table className="border-collapse table-fixed w-max min-w-full text-[0.7rem]">
        {/* ---------- HEADER ---------- */}
        <thead>
          {/* Row 1: corner + group names spanning sub-columns */}
          <tr>
            <th
              className="sticky left-0 top-0 z-30 bg-surface-alt border-b-2 border-r-2 border-border px-1 md:px-2 py-1 text-center font-bold text-[0.65rem] md:text-[0.75rem] w-[52px] md:w-[95px] min-w-[52px] md:min-w-[95px] max-w-[52px] md:max-w-[95px]"
              rowSpan={2}
            >
              {t('timeHeader')}
            </th>
            {columns.map(col => {
              const slotUtil = slotUtilizationByName?.get(col.name);
              return (
                <th
                  key={col.id}
                  className={`sticky top-0 z-20 bg-header border-b-2 border-border px-1.5 py-1 text-center font-bold text-[0.75rem] text-header-text ${groupBy === 'studentGroup' ? 'relative' : ''}`}
                  colSpan={subColumnLabels.length}
                >
                  <div className="flex flex-col items-center gap-0.5">
                    <span>{col.name}</span>
                    {slotUtil && (
                      <span className="text-[0.6rem] text-faint font-normal"
                            title={t('slotUtilizationHint')}>
                        {slotUtil.assignedSlots}/{slotUtil.totalValidSlots}
                      </span>
                    )}
                  </div>
                </th>
              );
            })}
          </tr>
          {/* Row 2: sub-column labels under each group */}
          <tr>
            {columns.map(col =>
              subColumnLabels.map((labelKey, si) => (
                <th
                  key={`${col.id}-${si}`}
                  className="sticky top-[30px] z-20 bg-surface-alt px-1 py-0.5 text-center font-semibold text-[0.65rem] text-faint overflow-hidden text-ellipsis whitespace-nowrap"
                  style={{ minWidth: COL_WIDTHS.subjectCol, maxWidth: COL_WIDTHS.subjectCol }}
                >
                  {t(labelKey)}
                </th>
              ))
            )}
          </tr>
        </thead>

        {/* ---------- BODY ---------- */}
        <tbody>
          {days.map(day => {
            const dateStr = format(day, 'yyyy-MM-dd');
            const holiday = holidayMap.get(dateStr);
            const dayLabel = `${localizedDayName(day)} ${format(day, 'dd/MM/yyyy')}`;

            return (
              <Fragment key={dateStr}>
                {/* Day header row */}
                <tr>
                  <td
                    className="sticky left-0 z-10 bg-surface-alt font-semibold text-[0.6rem] md:text-[0.7rem] text-content px-1 border-b border-border overflow-hidden whitespace-nowrap text-center"
                    style={{ maxWidth: '95px', height: '26px', maxHeight: '26px' }}
                  >
                    <span className="hidden md:inline" style={{ fontSize: '0.7rem', lineHeight: '1' }}>{localizedDayName(day).substring(0, 3)} {format(day, 'dd/MM')}</span>
                  </td>
                  {holiday ? (
                    <td
                      colSpan={totalSubCols}
                      className="border-b border-border px-2 text-center"
                      style={{
                        background: 'var(--holiday-bg)',
                        borderLeft: '3px solid var(--holiday-border)',
                        borderRight: '3px solid var(--holiday-border)',
                        height: '26px',
                        maxHeight: '26px',
                      }}
                    >
                      <span
                        className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[0.7rem] font-bold cursor-help"
                        style={{
                          background: 'var(--holiday-accent)',
                          color: 'white',
                        }}
                        title={holiday}
                        aria-label={`${t('common:holiday')}: ${holiday}`}
                      >
                        <span>🏖️</span>
                        <span>{holiday}</span>
                      </span>
                    </td>
                  ) : (
                    // Render per-column header cells to show stage day badges and ideal end highlighting
                    columns.map(col => {
                      const isStageDay = groupBy === 'studentGroup' &&
                        stageDaysByGroup.get(col.id)?.has(dateStr);
                      const isAfterIdealEnd = groupBy === 'studentGroup' &&
                        dateStr > (idealEndStrings.get(col.id) ?? '9999');

                      return subColumnLabels.map((_, si) => (
                        <td
                          key={`${col.id}-${si}`}
                          className="border-b border-light px-1 text-[0.6rem] text-center"
                          style={{
                            minWidth: COL_WIDTHS.subjectCol,
                            maxWidth: COL_WIDTHS.subjectCol,
                            height: '26px',
                            maxHeight: '26px',
                            ...(isAfterIdealEnd ? {
                              background: 'var(--completion-bg)',
                              borderTop: '2px solid var(--completion-border)',
                              borderBottom: '1px solid var(--completion-border)',
                            } : isStageDay ? {
                              background: 'var(--stage-bg)',
                              borderTop: '2px solid var(--stage-border)',
                              borderBottom: '1px solid var(--stage-border)',
                            } : {
                              background: 'var(--day-header-bg)',
                              borderBottom: '1px solid var(--border)',
                            })
                          }}
                        >
                          {si === 0 && isStageDay && (
                            <span className="text-[0.6rem] bg-amber-500 text-white px-1 py-[0.1rem] rounded ml-1 font-semibold">
                              STAGE
                            </span>
                          )}
                        </td>
                      ));
                    })
                  )}
                </tr>

                {/* Period rows */}
                {!holiday && timePeriods.map(period => {
                  const timeslotId = getTimeslotId(timeslotMap, day, period);
                  const lunchBorder = lunchBorderTop(period.lunchBreakBefore);

                  return (
                    <tr key={`${dateStr}-${period.startTime}`}>
                      <td
                        className="sticky left-0 z-[5] bg-surface-alt border-r-2 border-border border-b border-light px-1 font-semibold text-[0.6rem] md:text-[0.7rem] text-content whitespace-nowrap text-center w-[52px] md:w-[95px] min-w-[52px] md:min-w-[95px] max-w-[52px] md:max-w-[95px]"
                        style={{
                          height: '26px',
                          maxHeight: '26px',
                          borderTop: lunchBorder,
                        }}
                        title={period.lunchBreakBefore
                          ? t('lunchBreak', { start: period.lunchBreakStart, end: period.lunchBreakEnd })
                          : undefined}
                      >
                        <span className="hidden md:inline">{period.label}</span>
                        <span className="md:hidden">{period.shortLabel}</span>
                      </td>
                      {columns.map(col => {
                        const lesson = timeslotId != null ? getLesson(timeslotId, col.id) : null;
                        const isStageDay = groupBy === 'studentGroup' &&
                          stageDaysByGroup.get(col.id)?.has(dateStr);
                        const isAfterIdealEnd = groupBy === 'studentGroup' &&
                          dateStr > (idealEndStrings.get(col.id) ?? '9999');
                        // Closed slot per GroupCalendarRule resolution (only meaningful when columns ARE groups)
                        const slotAvailable = groupBy !== 'studentGroup' || isSlotOpenForGroup(day, period.index, col.id);

                        if (!slotAvailable) {
                          return subColumnLabels.map((_, si) => (
                            <td
                              key={`${col.id}-${si}`}
                              className="border-b border-light px-1 text-center cursor-not-allowed"
                              style={{
                                borderRight: si === subColumnLabels.length - 1
                                  ? undefined  /* TASK-525: single owner - the next group's divider draws this boundary */
                                  : '1px solid var(--border-faint)',
                                background: closedSlotStripeBackground(),
                                height: '26px',
                                maxHeight: '26px',
                                borderTop: lunchBorder,
                              }}
                              title={t('closedByCalendar')}
                            >
                              {si === 0 && <span className="text-gray-400 text-[0.6rem] font-medium">-</span>}
                            </td>
                          ));
                        }

                        // Manual placement (TASK-357): draggable lesson cells / droppable empty cells.
                        const cellKey = `${dateStr}-${period.startTime}-${col.id}`;
                        const canDrag = dragDropActive && !!lesson && lesson.id > 0;
                        const isDropTarget = dragDropActive && !lesson && !!draggingLesson;
                        const dropLegal = isDropTarget && (legalDropKeys?.has(cellKey) ?? false);
                        const isHoveredDrop = isDropTarget && hoveredDropKey === cellKey;

                        return subColumnLabels.map((_, si) => {
                          const violationInfo = lesson && violationMap ? violationMap.get(lesson.id) : undefined;
                          // Only apply violation styling to the first sub-column to avoid triple rendering
                          const violationClass = si === 0 ? getViolationCellClass(violationInfo) : '';

                          return (
                            <td
                              key={`${col.id}-${si}`}
                              data-testid={`cell-${cellKey}`}
                              className={`border-b border-light px-1 text-center min-w-0 ${lesson ? 'cursor-pointer' : 'cursor-default'} ${violationClass} ${isDropTarget && !dropLegal ? 'cursor-not-allowed' : ''}`}
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
                                if (dropLegal && draggingLesson && timeslotId != null) {
                                  onLessonDrop?.(draggingLesson, timeslotId);
                                }
                              } : undefined}
                              style={{
                                height: '26px',
                                maxHeight: '26px',
                                background: isDropTarget
                                  ? (dropLegal ? 'var(--feedback-success-bg)' : 'var(--feedback-error-bg)')
                                  : isAfterIdealEnd ? 'var(--completion-bg)' : (isStageDay ? 'var(--stage-bg)' : 'transparent'),
                                // Stronger ring on the specific cell under the pointer, on top of the
                                // "every legal target" pre-highlight above - outline (not border) so it
                                // doesn't disturb the ideal-end/stage border logic below.
                                outline: isHoveredDrop ? '2px solid var(--feedback-success-border)' : undefined,
                                outlineOffset: isHoveredDrop ? '-2px' : undefined,
                                borderLeft: isAfterIdealEnd && si === 0 ? '2px solid var(--completion-border)' : (isStageDay && si === 0 ? '2px solid var(--stage-border)' : undefined),
                                // TASK-525 single-owner: semantic boundary marks
                                // (completion/stage) STAY (they are informational,
                                // not the grid's column boundary); the plain last
                                // sub-column boundary is dropped: the next group's
                                // divider owns it.
                                borderRight: isAfterIdealEnd && si === subColumnLabels.length - 1
                                  ? '2px solid var(--completion-border)'
                                  : isStageDay && si === subColumnLabels.length - 1
                                    ? '2px solid var(--stage-border)'
                                    : si === subColumnLabels.length - 1
                                      ? undefined  /* TASK-525: single owner */
                                      : '1px solid var(--border-faint)',
                                borderTop: lunchBorder,
                              }}
                              onClick={() => {
                                if (lesson) onCellClick(lesson);
                              }}
                              title={
                                lesson
                                  ? `${lesson.course?.subject || ''} | ${getDisplayName(lesson.course?.teacher)} | ${lesson.course?.studentGroup?.name || ''} | ${lesson.room?.name || ''}`
                                  : isAfterIdealEnd ? 'After ideal end date' : (isStageDay ? 'Stage day - professionalizzanti only' : undefined)
                              }
                            >
                              {renderSubColumnContent(lesson, si, groupBy, getColor)}
                              {/* Only render badge in first sub-column to avoid duplicates */}
                              {si === 0 && renderViolationBadge(violationInfo)}
                            </td>
                          );
                        });
                      })}
                    </tr>
                  );
                })}

                {/* Holiday greyed-out rows */}
                {holiday && timePeriods.map(period => {
                  const lunchBorder = lunchBorderTop(period.lunchBreakBefore, 'var(--holiday-border)');
                  return (
                    <tr key={`${dateStr}-${period.startTime}-holiday`}>
                      <td
                        className="sticky left-0 z-[5] border-r-2 border-border border-b border-light px-1 font-semibold text-[0.6rem] md:text-[0.7rem] text-content whitespace-nowrap text-center italic w-[52px] md:w-[95px] min-w-[52px] md:min-w-[95px] max-w-[52px] md:max-w-[95px]"
                        style={{
                          background: 'var(--holiday-bg)',
                          color: 'var(--holiday-accent)',
                          height: '26px',
                          maxHeight: '26px',
                          borderTop: lunchBorder,
                        }}
                      >
                        <span className="hidden md:inline">{period.label}</span>
                        <span className="md:hidden">{period.shortLabel}</span>
                      </td>
                      {columns.map(col =>
                        subColumnLabels.map((_, si) => (
                          <td
                            key={`${col.id}-${si}`}
                            className="border-b border-light px-1 text-center italic"
                            style={{
                              background: 'var(--holiday-bg)',
                              color: 'var(--holiday-accent)',
                              borderLeft: si === 0 ? '1px solid var(--holiday-border)' : undefined,
                              borderRight: si === subColumnLabels.length - 1
                                ? undefined  /* TASK-525: single owner */
                                : '1px solid var(--border-faint)',
                              height: '26px',
                              maxHeight: '26px',
                              borderTop: lunchBorder,
                            }}
                          />
                        ))
                      )}
                    </tr>
                  );
                })}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default WeekViewGrid;
