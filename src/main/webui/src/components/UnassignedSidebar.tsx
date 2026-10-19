import { useTranslation } from 'react-i18next';
import type { Lesson } from '../types/domain';
import { getDisplayName } from '../utils/teacherUtils';

interface Props {
  lessons: Lesson[];
}

export default function UnassignedSidebar({ lessons }: Props) {
  const { t } = useTranslation('schedule');

  const unassigned = lessons.filter(l => !l.timeslot);

  const grouped = unassigned.reduce((acc, lesson) => {
    const key = lesson.course?.subject || '?';
    if (!acc[key]) acc[key] = [];
    acc[key].push(lesson);
    return acc;
  }, {} as Record<string, Lesson[]>);

  return (
    <div className="bg-surface rounded-lg p-4 shadow-soft max-h-[300px] md:max-h-sidebar-scroll overflow-y-auto">
      <div className="text-base font-bold mb-3">{t('unassignedLessons')}</div>
      {Object.keys(grouped).length === 0 ? (
        <div className="text-sm text-faint">{t('common:noData')}</div>
      ) : (
        Object.entries(grouped).map(([subject, items]) => (
          <div key={subject} className="mb-3">
            <div className="flex justify-between items-center text-sm font-semibold mb-1">
              <span>{subject}</span>
              <span className="bg-red-500 text-white rounded-[10px] px-2 py-0.5 text-xs">
                {items.length}
              </span>
            </div>
            {items.slice(0, 3).map(l => (
              <div key={l.id} className="text-xs text-muted py-0.5">
                {getDisplayName(l.course?.teacher) || '?'} - {l.course?.studentGroup?.name || '?'}
              </div>
            ))}
            {items.length > 3 && (
              <div className="text-xs text-muted">
                {t('more', { count: items.length - 3 })}
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}
