import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TeacherAvailability, Teacher } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableForm, TableInput, TableSelect,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';

export default function TeacherAvailAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [teacherId, setTeacherId] = useState<number | null>(null);
  const [startDT, setStartDT] = useState('');
  const [endDT, setEndDT] = useState('');
  const [type, setType] = useState<'SICK' | 'VACATION' | 'PREFERENCE'>('SICK');

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
  } = useAdminList<TeacherAvailability>({
    fetchFn: api.getTeacherAvailability,
    filterFn: (item, query) =>
      (item.teacher?.name || '').toLowerCase().includes(query.toLowerCase()) ||
      item.type.toLowerCase().includes(query.toLowerCase()) ||
      item.startDateTime.includes(query) ||
      item.endDateTime.includes(query),
  });

  // Load secondary entity (teachers) separately
  useEffect(() => {
    api.getTeachers().then(setTeachers).catch(e => {
      console.error('Failed to fetch teachers:', getErrorMessage(e));
    });
  }, []);

  const handleAdd = async () => {
    if (!teacherId || !startDT || !endDT) return;

    await execute(() => api.createTeacherAvailability({
      teacherId,
      startDateTime: startDT,
      endDateTime: endDT,
      type,
    }), () => {
      setTeacherId(null);
      setStartDT('');
      setEndDT('');
      load();
    });
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteTeacherAvailability(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:teacherAvailability')}</TableTitle>

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
          value={teacherId ?? ''}
          onChange={e => setTeacherId(e.target.value ? Number(e.target.value) : null)}
          disabled={isLoading}
        >
          <option value="">{t('schedule:teacher')}</option>
          {teachers.map(t => (
            <option key={t.id} value={t.id}>{t.name}</option>
          ))}
        </TableSelect>
        <TableInput
          type="datetime-local"
          value={startDT}
          onChange={e => setStartDT(e.target.value)}
          disabled={isLoading}
        />
        <TableInput
          type="datetime-local"
          value={endDT}
          onChange={e => setEndDT(e.target.value)}
          disabled={isLoading}
        />
        <TableSelect
          value={type}
          onChange={e => setType(e.target.value as 'SICK' | 'VACATION' | 'PREFERENCE')}
          disabled={isLoading}
        >
          <option value="SICK">{t('admin:sick')}</option>
          <option value="VACATION">{t('admin:vacation')}</option>
          <option value="PREFERENCE">{t('admin:preference')}</option>
        </TableSelect>
        <TableButton onClick={handleAdd} disabled={isLoading || !teacherId || !startDT || !endDT}>
          {isLoading ? '...' : t('common:add')}
        </TableButton>
      </TableForm>

      <FilterBar
        placeholder={t('admin:filterTeacherAvailability')}
        value={filter}
        onChange={setFilter}
        totalCount={items.length}
        filteredCount={filteredItems.length}
      />

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>ID</AdminTh>
            <AdminTh>{t('schedule:teacher')}</AdminTh>
            <AdminTh>{t('admin:startDate')}</AdminTh>
            <AdminTh>{t('admin:endDate')}</AdminTh>
            <AdminTh>{t('admin:type')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.teacher?.name || '--'}</AdminTd>
              <AdminTd>{item.startDateTime}</AdminTd>
              <AdminTd>{item.endDateTime}</AdminTd>
              <AdminTd>{item.type}</AdminTd>
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
