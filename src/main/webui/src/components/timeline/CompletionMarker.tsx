import { memo } from 'react';
import { format } from 'date-fns';
import { useTranslation } from 'react-i18next';
import type { CompletionData } from './types';

/**
 * Props for CompletionMarker component
 */
export interface CompletionMarkerProps {
  /** The completion data for a student group */
  data: CompletionData;
  /** Whether this is the day matching the completion date */
  isCompletionDay: boolean;
  /** Compact mode for header display */
  compact?: boolean;
}

/**
 * Visual marker indicating the theoretical first day after all lessons
 * assuming full horizontal compaction.
 *
 * Design: Blue/cyan "completion" aesthetic.
 * - Uses a subtle vertical band effect on the completion day
 * - Tooltip shows calculation details
 */
function CompletionMarkerComponent({ data, isCompletionDay, compact = false }: CompletionMarkerProps) {
  const { t } = useTranslation('schedule');

  if (!isCompletionDay) return null;

  const formattedDate = format(data.completionDate, 'dd/MM/yyyy');

  // Tooltip content with details
  const tooltipContent = [
    `${t('completion.lastDay')}: ${formattedDate}`,
    `${t('completion.lessons')}: ${data.finalDayLessonCount}`,
    data.isPerfectlyCompacted ? `${t('completion.compacted')}` : `${t('completion.partial')}`,
  ].join(' | ');

  if (compact) {
    // Compact mode: just a small indicator in the header
    return (
      <span
        className="inline-flex items-center justify-center ml-1.5 px-1.5 py-0.5 rounded text-[0.55rem] font-bold"
        style={{
          background: 'linear-gradient(135deg, var(--completion-accent, #0891b2) 0%, var(--completion-border, #67e8f9) 100%)',
          color: 'white',
          boxShadow: '0 1px 2px rgba(0,0,0,0.15)',
        }}
        title={tooltipContent}
      >
        ⚡
      </span>
    );
  }

  // Full mode: visual band effect
  return (
    <div
      className="absolute inset-0 pointer-events-none"
      style={{
        background: 'linear-gradient(90deg, transparent 0%, rgba(8, 145, 178, 0.08) 50%, transparent 100%)',
        borderLeft: '3px solid var(--completion-border)',
        borderRight: '3px solid var(--completion-border)',
      }}
      title={tooltipContent}
    >
      <div
        className="absolute top-0 left-1/2 transform -translate-x-1/2 px-2 py-0.5 rounded-b text-[0.6rem] font-bold whitespace-nowrap"
        style={{
          background: 'linear-gradient(135deg, #0891b2 0%, #67e8f9 100%)',
          color: 'white',
          boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
        }}
      >
        {data.isPerfectlyCompacted ? t('completion.perfect') : t('completion.done')}
      </div>
    </div>
  );
}

export const CompletionMarker = memo(CompletionMarkerComponent);

/**
 * Check if a date matches the completion date for any group
 */
export function isCompletionDayForGroup(
  completionDatesByGroup: Map<number, CompletionData>,
  groupId: number,
  dateStr: string
): CompletionData | null {
  const data = completionDatesByGroup.get(groupId);
  if (!data) return null;

  const completionDateStr = format(data.completionDate, 'yyyy-MM-dd');
  return dateStr === completionDateStr ? data : null;
}

export default CompletionMarker;
