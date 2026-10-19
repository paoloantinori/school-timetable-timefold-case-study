import { memo } from 'react';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import type { IdealEndData } from './types';

/**
 * Props for IdealEndMarker component
 */
export interface IdealEndMarkerProps {
  /** The ideal end data for a student group */
  data: IdealEndData;
  /** Whether this is the day matching the ideal end date */
  isIdealEndDay: boolean;
  /** Compact mode for header display */
  compact?: boolean;
}

/**
 * Visual marker indicating the ideal last school day for a student group.
 *
 * Design: Refined utilitarian with a distinctive "finish line" aesthetic.
 * - Uses a subtle vertical band effect on the ideal end day
 * - Tooltip shows calculation breakdown
 */
function IdealEndMarkerComponent({ data, isIdealEndDay, compact = false }: IdealEndMarkerProps) {
  const { t } = useTranslation('schedule');

  if (!isIdealEndDay) return null;

  const formattedDate = format(data.idealEndDate, 'dd/MM/yyyy');

  // Tooltip content with calculation breakdown
  const tooltipContent = [
    `${t('idealEnd.target')}: ${formattedDate}`,
    `${t('idealEnd.hours')}: ${data.totalHours}`,
    `${t('idealEnd.available')}: ${data.availableDays}`,
    `${t('idealEnd.excluded')}: ${data.excludedDays}`,
  ].join(' | ');

  if (compact) {
    // Compact mode: just a small indicator in the header
    return (
      <span
        className="inline-flex items-center justify-center ml-1.5 px-1.5 py-0.5 rounded text-[0.55rem] font-bold"
        style={{
          background: 'linear-gradient(135deg, var(--ideal-end-start, #10b981) 0%, var(--ideal-end-end, #059669) 100%)',
          color: 'white',
          boxShadow: '0 1px 2px rgba(0,0,0,0.15)',
        }}
        title={tooltipContent}
      >
        ✓
      </span>
    );
  }

  // Full mode: visual band effect
  return (
    <div
      className="absolute inset-0 pointer-events-none"
      style={{
        background: 'linear-gradient(90deg, transparent 0%, rgba(16, 185, 129, 0.08) 50%, transparent 100%)',
        borderLeft: '3px solid rgba(16, 185, 129, 0.6)',
        borderRight: '3px solid rgba(16, 185, 129, 0.6)',
      }}
      title={tooltipContent}
    >
      <div
        className="absolute top-0 left-1/2 transform -translate-x-1/2 px-2 py-0.5 rounded-b text-[0.6rem] font-bold whitespace-nowrap"
        style={{
          background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
          color: 'white',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      >
        {t('idealEnd.finishLine')}
      </div>
    </div>
  );
}

export const IdealEndMarker = memo(IdealEndMarkerComponent);

/**
 * Check if a date matches the ideal end date for any group
 */
export function isIdealEndDayForGroup(
  idealEndDatesByGroup: Map<number, IdealEndData>,
  groupId: number,
  dateStr: string
): IdealEndData | null {
  const data = idealEndDatesByGroup.get(groupId);
  if (!data) return null;

  const idealDateStr = format(data.idealEndDate, 'yyyy-MM-dd');
  return dateStr === idealDateStr ? data : null;
}

export default IdealEndMarker;
