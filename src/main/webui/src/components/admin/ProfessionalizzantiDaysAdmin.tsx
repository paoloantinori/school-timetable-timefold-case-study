import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { ProfessionalizzantiDay, StudentGroup, CreateProfessionalizzantiDayInput } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableForm, TableInput, TableSelect,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';
import { DAYS, DAY_LABELS } from '../../data/dayConstants';

export default function ProfessionalizzantiDaysAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [date, setDate] = useState('');
  const [dayOfWeek, setDayOfWeek] = useState('');
  const [description, setDescription] = useState('');

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
  } = useAdminList<ProfessionalizzantiDay>({
    fetchFn: api.getProfessionalizzantiDays,
    filterFn: (item, query) =>
      (item.studentGroup?.name || '').toLowerCase().includes(query.toLowerCase()) ||
      item.date.includes(query) ||
      (item.description || '').toLowerCase().includes(query.toLowerCase()),
  });

  // Load student groups
  useEffect(() => {
    api.getStudentGroups().then(setGroups).catch(e => {
      console.error('Failed to fetch student groups:', getErrorMessage(e));
    });
  }, []);

  const handleAdd = async () => {
    if (!groupId || !date || !dayOfWeek) return;

    const input: CreateProfessionalizzantiDayInput = {
      studentGroupId: groupId,
      date,
      dayOfWeek,
      description,
    };

    await execute(() => api.createProfessionalizzantiDay(input), () => {
      setGroupId(null);
      setDate('');
      setDayOfWeek('');
      setDescription('');
      load();
    });
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteProfessionalizzantiDay(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:professionalizzantiDays')}</TableTitle>

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
          placeholder={t('admin:date')}
          value={date}
          onChange={e => setDate(e.target.value)}
          disabled={isLoading}
        />
        <TableSelect
          value={dayOfWeek}
          onChange={e => setDayOfWeek(e.target.value)}
          disabled={isLoading}
        >
          <option value="">{t('admin:dayOfWeek')}</option>
          {DAYS.map(day => (
            <option key={day} value={day}>{DAY_LABELS[day]}</option>
          ))}
        </TableSelect>
        <TableInput
          placeholder={t('admin:description')}
          value={description}
          onChange={e => setDescription(e.target.value)}
          disabled={isLoading}
        />
        <TableButton onClick={handleAdd} disabled={isLoading || !groupId || !date || !dayOfWeek}>
          {isLoading ? '...' : t('common:add')}
        </TableButton>
      </TableForm>

      <FilterBar
        placeholder={t('admin:filterProfessionalizzantiDays')}
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
            <AdminTh>{t('admin:date')}</AdminTh>
            <AdminTh>{t('admin:dayOfWeek')}</AdminTh>
            <AdminTh>{t('admin:description')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.studentGroup?.name || '--'}</AdminTd>
              <AdminTd>{item.date}</AdminTd>
              <AdminTd>{DAY_LABELS[item.dayOfWeek] || item.dayOfWeek}</AdminTd>
              <AdminTd>{item.description || '--'}</AdminTd>
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
