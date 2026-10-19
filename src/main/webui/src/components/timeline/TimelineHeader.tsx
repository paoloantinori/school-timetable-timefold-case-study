import { useTranslation } from 'react-i18next';
import { format, parseISO } from 'date-fns';
import { toggleButton } from '../ui/variants';
import type { TimelineHeaderProps } from './types';

/**
 * Timeline header with navigation controls
 *
 * Includes view mode toggle, week navigation, group-by toggle,
 * and hidden groups badge
 */
export function TimelineHeader({
  viewMode,
  setViewMode,
  groupBy,
  setGroupBy,
  weekLabel,
  atStart,
  atEnd,
  goPrev,
  goNext,
  goToday,
  yearRangeLabel,
  hiddenCount,
  onShowAll,
  allDaysCount,
  yearRange,
  onJumpToDate,
  printHref,
  printHint,
}: TimelineHeaderProps) {
  const { t } = useTranslation('schedule');

  return (
    <div className="flex items-center gap-3 flex-wrap">
      {/* View mode toggle */}
      <div className="flex border border-input-border rounded overflow-hidden">
        <button
          className={toggleButton({ active: viewMode === 'week' })}
          onClick={() => setViewMode('week')}
        >
          {t('weekView')}
        </button>
        <button
          className={toggleButton({ active: viewMode === 'infinite' })}
          onClick={() => setViewMode('infinite')}
        >
          {t('timelineView')}
        </button>
      </div>

      {/* Week navigation (only in week mode) */}
      {viewMode === 'week' && (
        <>
          <button
            className={`px-3 py-2.5 md:py-1.5 border border-border rounded text-[0.8rem] bg-surface font-semibold text-content min-h-[44px] md:min-h-0 ${atStart ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}
            onClick={goPrev}
            disabled={atStart}
          >
            {'\u25C0'} {t('prev')}
          </button>
          <button
            className="px-3 py-2.5 md:py-1.5 border border-border rounded cursor-pointer text-[0.8rem] bg-surface font-semibold text-content min-h-[44px] md:min-h-0"
            onClick={goToday}
          >
            {t('today')}
          </button>
          <button
            className={`px-3 py-2.5 md:py-1.5 border border-border rounded text-[0.8rem] bg-surface font-semibold text-content min-h-[44px] md:min-h-0 ${atEnd ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}
            onClick={goNext}
            disabled={atEnd}
          >
            {t('next')} {'\u25B6'}
          </button>
          <span className="text-[0.8rem] md:text-[0.9rem] font-semibold text-content md:min-w-[200px] text-center">
            {weekLabel}
          </span>
        </>
      )}

      {/* Jump to date (only in infinite mode) */}
      {viewMode === 'infinite' && (
        <>
          <input
            type="date"
            onChange={(e) => {
              if (e.target.value) {
                onJumpToDate(parseISO(e.target.value));
              }
            }}
            className="px-2 py-1.5 border border-input-border rounded text-[0.75rem] bg-surface text-content cursor-pointer"
            title={t('jumpToDate')}
            min={yearRange ? format(yearRange.start, 'yyyy-MM-dd') : undefined}
            max={yearRange ? format(yearRange.end, 'yyyy-MM-dd') : undefined}
          />
          <span className="text-[0.8rem] text-muted min-w-[200px] text-center font-semibold">
            {allDaysCount} {t('daysLabel')}
          </span>
        </>
      )}

      {yearRangeLabel && (
        <span className="text-[0.75rem] font-medium text-header-text px-2.5 py-1 bg-header rounded whitespace-nowrap">
          {t('year')}: {yearRangeLabel}
        </span>
      )}

      <div className="flex gap-1 md:ml-auto">
        <button
          className={toggleButton({ active: groupBy === 'studentGroup' })}
          onClick={() => setGroupBy('studentGroup')}
        >
          {t('byStudentGroup')}
        </button>
        <button
          className={toggleButton({ active: groupBy === 'room' })}
          onClick={() => setGroupBy('room')}
        >
          {t('byRoom')}
        </button>
        <button
          className={toggleButton({ active: groupBy === 'teacher' })}
          onClick={() => setGroupBy('teacher')}
        >
          {t('byTeacher')}
        </button>
        {/* TASK-659: opens the server-rendered print sheet (b) in a new tab; the
            sheet itself links the server-side PDF (c). Disabled until the current
            grouping has exactly one selected subject to print. */}
        <a
          href={printHref ?? undefined}
          target="_blank"
          rel="noopener"
          aria-disabled={!printHref}
          title={printHref ? undefined : printHint}
          className={`px-2.5 py-1 rounded text-[0.75rem] font-medium border ${
            printHref
              ? 'border-input-border bg-primary text-white cursor-pointer no-underline'
              : 'border-input-border bg-disabled text-muted cursor-not-allowed no-underline opacity-60'
          }`}
        >
          {t('print')}
        </a>
      </div>

      {/* Hidden groups badge */}
      {hiddenCount > 0 && (
        <div className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-teal-500 text-white rounded text-[0.75rem] font-semibold">
          <span>{hiddenCount} hidden</span>
          <button
            className="bg-transparent border-none text-white cursor-pointer underline text-[0.75rem] p-0 ml-1"
            onClick={onShowAll}
          >
            Show all
          </button>
        </div>
      )}
    </div>
  );
}

export default TimelineHeader;
