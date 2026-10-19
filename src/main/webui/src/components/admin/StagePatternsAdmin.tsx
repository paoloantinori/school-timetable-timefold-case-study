import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { StagePattern, StudentGroup } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableForm, TableInput, TableSelect,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';
import { DAYS } from '../../data/dayConstants';

export default function StagePatternsAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [selectedDays, setSelectedDays] = useState<string[]>([]);

  const {
    items,
    filteredItems,
    filter,
    setFilter,
    isLoading,
    error,
    clearError,
    load,
    execute,
  } = useAdminList<StagePattern>({
    fetchFn: api.getStagePatterns,
    filterFn: (item, query) =>
      (item.studentGroup?.name || '').toLowerCase().includes(query.toLowerCase()) ||
      item.startDate.includes(query) ||
      item.endDate.includes(query),
  });

  // Load secondary entity (groups) separately
  useEffect(() => {
    api.getStudentGroups().then(setGroups).catch(e => {
      console.error('Failed to fetch student groups:', getErrorMessage(e));
    });
  }, []);

  const toggleDay = (day: string) => {
    setSelectedDays(prev =>
      prev.includes(day) ? prev.filter(d => d !== day) : [...prev, day]
    );
  };

  const handleAdd = async () => {
    if (!groupId || !startDate || !endDate) return;

    await execute(() => api.createStagePattern({
      studentGroupId: groupId,
      startDate,
      endDate,
      dayOfWeekMask: selectedDays,
    }), () => {
      setGroupId(null);
      setStartDate('');
      setEndDate('');
      setSelectedDays([]);
      load();
    });
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteStagePattern(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:stagePatterns')}</TableTitle>

      {error && (
        <div className="p-2 bg-feedback-error-bg rounded mb-3 text-sm text-feedback-error-text flex justify-between items-center">
          <span>{error}</span>
          <button
            className="bg-none border-none text-feedback-error-text cursor-pointer text-lg leading-none"
            onClick={clearError}
            aria-label={t('common:dismiss')}
          >
            ×
          </button>
        </div>
      )}

      <TableForm>
        <TableSelect
          value={groupId ?? ''}
          onChange={e => setGroupId(e.target.value ? Number(e.target.value) : null)}
          disabled={isLoading}
        >
          <option value="">{t('schedule:studentGroup')}</option>
          {groups.map(g => (
            <option key={g.id} value={g.id}>{g.name}</option>
          ))}
        </TableSelect>
        <TableInput
          type="date"
          placeholder={t('admin:startDate')}
          value={startDate}
          onChange={e => setStartDate(e.target.value)}
          disabled={isLoading}
        />
        <TableInput
          type="date"
          placeholder={t('admin:endDate')}
          value={endDate}
          onChange={e => setEndDate(e.target.value)}
          disabled={isLoading}
        />
      </TableForm>

      <div className="flex gap-2 mb-4 items-center">
        {DAYS.map(day => (
          <label key={day} className="text-sm flex items-center gap-1">
            <input
              type="checkbox"
              checked={selectedDays.includes(day)}
              onChange={() => toggleDay(day)}
              disabled={isLoading}
            />
            {day.slice(0, 3)}
          </label>
        ))}
        <TableButton onClick={handleAdd} disabled={isLoading || !groupId || !startDate || !endDate}>
          {isLoading ? '...' : t('common:add')}
        </TableButton>
      </div>

      <FilterBar
        placeholder={t('admin:filterStagePatterns')}
        value={filter}
        onChange={setFilter}
        totalCount={items.length}
        filteredCount={filteredItems.length}
      />

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>ID</AdminTh>
            <AdminTh>{t('schedule:studentGroup')}</AdminTh>
            <AdminTh>{t('admin:startDate')}</AdminTh>
            <AdminTh>{t('admin:endDate')}</AdminTh>
            <AdminTh>{t('admin:dayOfWeekMask')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.studentGroup?.name || '--'}</AdminTd>
              <AdminTd>{item.startDate}</AdminTd>
              <AdminTd>{item.endDate}</AdminTd>
              <AdminTd>{item.dayOfWeekMask.join(', ')}</AdminTd>
              <AdminTd>
                <DeleteButton onClick={() => handleDelete(item.id)} disabled={isLoading}>
                  {t('common:delete')}
                </DeleteButton>
              </AdminTd>
            </tr>
          ))}
        </tbody>
      </AdminTable>
    </TableContainer>
  );
}
