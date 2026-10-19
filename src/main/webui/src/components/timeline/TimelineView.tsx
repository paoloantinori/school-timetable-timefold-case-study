import { createContext, useContext, useMemo, useRef, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { List, type ListImperativeAPI } from 'react-window';
import { format, getDay } from 'date-fns';
import { VirtualizedDayRow, type DayRowData, type PeriodData } from './VirtualizedDayRow';
import { COLUMN_WIDTHS, getColumnWidth, buildTimeslotMap, type GroupByMode, type Column, type TimePeriod, type IdealEndData, type CompletionData } from './types';
import type { Lesson, Timeslot, ViolationInfo } from '../../types/domain';

// Context for row data access
interface RowContextValue {
  allDays: Date[];
  holidayMap: Map<string, string>;
  timePeriods: TimePeriod[];
  columns: Column[];
  groupBy: GroupByMode;
  stageDaysByGroup: Map<number, Set<string>>;
  idealEndDatesByGroup: Map<number, IdealEndData>;
  completionDatesByGroup: Map<number, CompletionData>;
  timeslotMap: Map<string, number>;
  getLesson: (timeslotId: number, columnId: number) => Lesson | null;
  getColor: (subject: string, customColor?: string | null) => string;
  localizedDayName: (d: Date) => string;
  onCellClick: (lesson: Lesson) => void;
  contentWidth: number;
  violationMap?: Map<number, ViolationInfo>;
  slotUtilizationByName?: Map<string, import('../../types/domain').SlotUtilization>;
  isSlotOpenForGroup: (day: Date, slotIndex: number, groupId: number) => boolean;
  draggingLesson?: Lesson | null;
  onLessonDragStart?: (lesson: Lesson) => void;
  onLessonDragEnd?: () => void;
  onLessonDrop?: (lesson: Lesson, timeslotId: number) => void;
}

const RowContext = createContext<RowContextValue | null>(null);

function Row({ index, style, ariaAttributes }: { index: number; style: React.CSSProperties; ariaAttributes: { 'aria-posinset': number; 'aria-setsize': number; role: 'listitem' } }) {
  const ctx = useContext(RowContext);
  if (!ctx) return null;

  const day = ctx.allDays[index];
  if (!day) return null;

  const dateStr = format(day, 'yyyy-MM-dd');
  const holiday = ctx.holidayMap.get(dateStr) ?? null;
  const dayLabel = `${ctx.localizedDayName(day)} ${format(day, 'dd/MM/yyyy')}`;
  const shortDayLabel = `${ctx.localizedDayName(day).substring(0, 3)} ${format(day, 'dd/MM')}`;
  const isMonday = getDay(day) === 1;

  // Simplified rowData
  const rowData: DayRowData = { date: day, dateStr, holiday, dayLabel, shortDayLabel, isMonday };

  // Pre-compute timeslotIds for this day
  const periodData = holiday ? [] : ctx.timePeriods.map((period) => {
    const timeslotId = ctx.timeslotMap.get(`${dateStr}-${period.startTime}`) ?? null;
    return { period, timeslotId };
  });

  return (
    <div style={{ ...style, width: ctx.contentWidth }}>
      <VirtualizedDayRow
        data={rowData}
        periodData={periodData}
        columns={ctx.columns}
        groupBy={ctx.groupBy}
        stageDaysByGroup={ctx.stageDaysByGroup}
        idealEndDatesByGroup={ctx.idealEndDatesByGroup}
        completionDatesByGroup={ctx.completionDatesByGroup}
        getLesson={ctx.getLesson}
        getColor={ctx.getColor}
        onCellClick={ctx.onCellClick}
        violationMap={ctx.violationMap}
        isSlotOpenForGroup={ctx.isSlotOpenForGroup}
        draggingLesson={ctx.draggingLesson}
        onLessonDragStart={ctx.onLessonDragStart}
        onLessonDragEnd={ctx.onLessonDragEnd}
        onLessonDrop={ctx.onLessonDrop}
      />
    </div>
  );
}

export function TimelineView({
  allDays,
  holidayMap,
  timePeriods,
  columns,
  groupBy,
  stageDaysByGroup,
  idealEndDatesByGroup,
  completionDatesByGroup,
  schedule,
  getLesson,
  getColor,
  localizedDayName,
  onCellClick,
  height,
  violationMap,
  slotUtilizationByName,
  isSlotOpenForGroup,
  draggingLesson,
  onLessonDragStart,
  onLessonDragEnd,
  onLessonDrop,
}: {
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
}) {
  const { t } = useTranslation('schedule');
  const listRef = useRef<ListImperativeAPI | null>(null);
  const headerRef = useRef<HTMLDivElement>(null);
  const [listElement, setListElement] = useState<HTMLElement | null>(null);

  const timeslotMap = useMemo(() => buildTimeslotMap(schedule?.timeslots ?? []), [schedule?.timeslots]);

  // Sync header scroll with List's horizontal scroll
  useEffect(() => {
    if (!listElement || !headerRef.current) return;

    const handleScroll = () => {
      if (headerRef.current) {
        headerRef.current.style.transform = `translateX(-${listElement.scrollLeft}px)`;
      }
    };

    listElement.addEventListener('scroll', handleScroll);
    return () => listElement.removeEventListener('scroll', handleScroll);
  }, [listElement]);

  // Callback to capture the list element when mounted
  const handleListRef = useCallback((api: ListImperativeAPI | null) => {
    listRef.current = api;
    setListElement(api?.element ?? null);
  }, []);

  // Content width must be consistent across header and rows (80px time column on desktop + 285px per data column)
  const contentWidth = useMemo(() => 80 + columns.length * 285, [columns.length]);

  // Stable context value with contentWidth for rows
  const contextValue = useMemo<RowContextValue>(() => ({
    allDays,
    holidayMap,
    timePeriods,
    columns,
    groupBy,
    stageDaysByGroup,
    idealEndDatesByGroup,
    completionDatesByGroup,
    timeslotMap,
    getLesson,
    getColor,
    localizedDayName,
    onCellClick,
    contentWidth,
    violationMap,
    slotUtilizationByName,
    isSlotOpenForGroup,
    draggingLesson,
    onLessonDragStart,
    onLessonDragEnd,
    onLessonDrop,
  }), [allDays, holidayMap, timePeriods, columns, groupBy, stageDaysByGroup, idealEndDatesByGroup, completionDatesByGroup, timeslotMap, getLesson, getColor, localizedDayName, onCellClick, contentWidth, violationMap, slotUtilizationByName, isSlotOpenForGroup, draggingLesson, onLessonDragStart, onLessonDragEnd, onLessonDrop]);

  const getRowHeight = useCallback((index: number): number => {
    const day = allDays[index];
    if (!day) return 200;
    const dateStr = format(day, 'yyyy-MM-dd');
    const isHoliday = holidayMap.has(dateStr);

    // TASK-525 round 3: outer container paints a uniform 2px day boundary for
    // EVERY day (single owner; the day-header cell no longer paints its own).
    const outerBorderTop = 2;

    // Day header cell height (measured live 2026-08-21): fixed 34px, border-box,
    // no own top/bottom borders anymore. Holidays expand (beach glyph) to a
    // measured 50px - reserve exactly that.
    const headerHeight = isHoliday ? 50 : 34;

    // Total day header section height (outer border + header itself)
    const dayHeaderHeight = outerBorderTop + headerHeight;

    if (isHoliday) {
      // Holiday: just the day header, no period rows
      return dayHeaderHeight;
    }

    // Period rows: h-[34px] exact height (measured: 34px)
    const periodRowHeight = 34;
    const periodRowsHeight = timePeriods.length * periodRowHeight;

    // Check if any column has an ideal end marker on this day
    let idealEndMarkerHeight = 0;
    if (groupBy === 'studentGroup') {
      for (const col of columns) {
        const idealEndData = idealEndDatesByGroup.get(col.id);
        if (idealEndData) {
          const idealDateStr = format(idealEndData.idealEndDate, 'yyyy-MM-dd');
          if (idealDateStr === dateStr) {
            // Ideal end marker row: py-1 (8px) + content (~20px) + border-t (1px) = 29px
            idealEndMarkerHeight = 29;
            break;
          }
        }
      }
    }

    return dayHeaderHeight + idealEndMarkerHeight + periodRowsHeight;
  }, [allDays, holidayMap, timePeriods, groupBy, columns, idealEndDatesByGroup]);

  // Stable empty object for rowProps to prevent re-renders
  const emptyRowProps = useMemo(() => ({}), []);

  return (
    <RowContext.Provider value={contextValue}>
      {/* Outer container - clips to viewport */}
      <div className="flex-1 bg-surface rounded-lg shadow-soft flex flex-col min-w-0 overflow-hidden"
           style={{ maxHeight: height }}>
        {/* Header - Time column fixed, column headers scroll-synced */}
        <div
          className="border-b-2 border-border flex-shrink-0 flex"
          style={{
            background: 'var(--header-bg)',
            color: 'var(--header-text, var(--text))',
          }}
        >
          {/* Time header - fixed, outside scroll container */}
          <div className="px-1 py-1.5 font-bold text-[0.65rem] md:text-[0.75rem] text-center flex-shrink-0 border-r-2 border-border w-[52px] md:w-[80px]">{t('timeHeader')}</div>
          {/* Column headers - inside scroll-synced container */}
          <div className="overflow-hidden flex-1">
            <div
              ref={headerRef}
              style={{
                display: 'flex',
                width: `calc(${contentWidth} - ${COLUMN_WIDTHS.timeColumn})`,
                minWidth: `calc(${contentWidth} - ${COLUMN_WIDTHS.timeColumn})`,
              }}
            >
              {columns.map((col) => {
                const slotUtil = slotUtilizationByName?.get(col.name);
                return (
                  <div key={col.id} className="px-1.5 py-1.5 text-center font-bold text-[0.75rem] border-l border-light flex-shrink-0 flex flex-col gap-0.5"
                       style={{ minWidth: getColumnWidth(groupBy), maxWidth: getColumnWidth(groupBy) }}>
                    <span>{col.name}</span>
                    {slotUtil && (
                      <span className="text-[0.6rem] font-normal opacity-75"
                            title={t('slotUtilizationHint')}>
                        {slotUtil.assignedSlots}/{slotUtil.totalValidSlots}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
        {/* List container - handles both vertical and horizontal scroll.
            TASK-525 BUG 2: paddingBottom gives the last day row breathing room
            so its calendar glyph is not clipped by the container edge. */}
        <div style={{ flex: 1, minHeight: 0, paddingBottom: '6px' }}>
          <List
            listRef={handleListRef}
            rowHeight={getRowHeight}
            rowCount={allDays.length}
            rowComponent={Row}
            rowProps={emptyRowProps}
            defaultHeight={height - 46}
            overscanCount={1}
          />
        </div>
      </div>
    </RowContext.Provider>
  );
}

export default TimelineView;
