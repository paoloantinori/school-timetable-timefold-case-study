import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SchoolHoliday } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import {
  TableContainer, TableTitle, TableForm, TableInput,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';

export default function HolidaysAdmin() {
  const { t } = useTranslation(['admin', 'common']);
  const [date, setDate] = useState('');
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
  } = useAdminList<SchoolHoliday>({
    fetchFn: api.getHolidays,
    filterFn: (item, query) =>
      item.date.includes(query) ||
      (item.description || '').toLowerCase().includes(query.toLowerCase()),
  });

  const handleAdd = async () => {
    if (!date) return;

    await execute(() => api.createHoliday({ date, description }), () => {
      setDate('');
      setDescription('');
      load();
    });
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteHoliday(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:holidays')}</TableTitle>

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
        <TableInput
          type="date"
          value={date}
          onChange={e => setDate(e.target.value)}
          disabled={isLoading}
        />
        <TableInput
          placeholder={t('admin:description')}
          value={description}
          onChange={e => setDescription(e.target.value)}
          disabled={isLoading}
        />
        <TableButton onClick={handleAdd} disabled={isLoading || !date}>
          {isLoading ? '...' : t('common:add')}
        </TableButton>
      </TableForm>

      <FilterBar
        placeholder={t('admin:filterHolidays')}
        value={filter}
        onChange={setFilter}
        totalCount={items.length}
        filteredCount={filteredItems.length}
      />

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>ID</AdminTh>
            <AdminTh>{t('schedule:date')}</AdminTh>
            <AdminTh>{t('admin:description')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.date}</AdminTd>
              <AdminTd>{item.description}</AdminTd>
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
