import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { StudentGroup, SchoolHoliday, StagePattern } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import {
  TableContainer, TableTitle, AdminTable, AdminTh, AdminTd,
  ActionButton
} from './styles';
import { useAdminList } from '../../hooks/useAdminList';

/**
 * School Settings Admin Page (TASK-227)
 *
 * Provides a centralized interface for managing school configuration:
 * - Student group start dates (editable)
 * - Quick links to Holidays and Stage Patterns admin pages
 *
 * This addresses the issue where StudentGroup.startDate was NULL for all groups,
 * causing the solver to place lessons later in the year than necessary.
 */
export default function SchoolSettingsAdmin() {
  const { t } = useTranslation(['admin', 'common']);
  const [editId, setEditId] = useState<number | null>(null);
  const [startDate, setStartDate] = useState('');
  const [saving, setSaving] = useState(false);

  const { items: groups, isLoading, load } = useAdminList<StudentGroup>({
    fetchFn: api.getStudentGroups,
    filterFn: () => true, // No filtering - show all groups
  });

  // Holiday and stage pattern counts (for quick links)
  const [holidayCount, setHolidayCount] = useState(0);
  const [stageCount, setStageCount] = useState(0);

  // Load counts on mount
  useEffect(() => {
    api.getHolidays().then((h: SchoolHoliday[]) => setHolidayCount(h.length));
    api.getStagePatterns().then((s: StagePattern[]) => setStageCount(s.length));
  }, []);

  const handleEdit = (group: StudentGroup) => {
    setEditId(group.id);
    setStartDate(group.startDate || '');
  };

  const handleSave = async (id: number) => {
    setSaving(true);
    try {
      await api.updateStudentGroup(id, {
        startDate: startDate || null
      });
      setEditId(null);
      setStartDate('');
      load();
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setEditId(null);
    setStartDate('');
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:schoolSettings')}</TableTitle>

      {/* Section 1: Student Group Start Dates */}
      <h4 className="text-base font-semibold mb-3 text-content">
        {t('admin:startDatesSection')}
      </h4>
      <p className="text-sm text-muted mb-4">
        {t('admin:startDatesDescription')}
      </p>

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>{t('common:name')}</AdminTh>
            <AdminTh>{t('admin:startDate')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {groups.map(group => (
            <tr key={group.id}>
              <AdminTd>{group.name}</AdminTd>
              <AdminTd>
                {editId === group.id ? (
                  <input
                    type="date"
                    value={startDate}
                    onChange={e => setStartDate(e.target.value)}
                    disabled={saving}
                    className="px-2 py-1 border border-border rounded text-sm
                               bg-surface text-content focus:outline-none focus:ring-2 focus:ring-accent"
                  />
                ) : (
                  group.startDate ? (
                    <span className="text-content">{group.startDate}</span>
                  ) : (
                    <span className="text-muted italic">{t('admin:notSet')}</span>
                  )
                )}
              </AdminTd>
              <AdminTd>
                {editId === group.id ? (
                  <>
                    <ActionButton
                      onClick={() => handleSave(group.id)}
                      disabled={saving || isLoading}
                    >
                      {t('common:save')}
                    </ActionButton>
                    <ActionButton
                      onClick={handleCancel}
                      disabled={saving}
                    >
                      {t('common:cancel')}
                    </ActionButton>
                  </>
                ) : (
                  <ActionButton
                    onClick={() => handleEdit(group)}
                    disabled={isLoading || editId !== null}
                  >
                    {t('common:edit')}
                  </ActionButton>
                )}
              </AdminTd>
            </tr>
          ))}
        </tbody>
      </AdminTable>

      {/* Section 2: Quick Links to Related Settings */}
      <div className="mt-8">
        <h4 className="text-base font-semibold mb-3 text-content">
          {t('admin:relatedSettings')}
        </h4>

        <div className="space-y-3">
          <Link
            to="/admin/holidays"
            className="flex justify-between items-center p-4 bg-gray-50 dark:bg-gray-800
                       rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <span className="font-medium text-content">{t('admin:holidays')}</span>
            <span className="text-muted text-sm">
              {holidayCount} {t('admin:records')}
            </span>
          </Link>

          <Link
            to="/admin/stage-patterns"
            className="flex justify-between items-center p-4 bg-gray-50 dark:bg-gray-800
                       rounded hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <span className="font-medium text-content">{t('admin:stagePatterns')}</span>
            <span className="text-muted text-sm">
              {stageCount} {t('admin:records')}
            </span>
          </Link>
        </div>
      </div>
    </TableContainer>
  );
}
