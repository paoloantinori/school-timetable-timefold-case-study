import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { StudentGroup } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import {
  TableContainer, TableTitle, TableForm, TableInput,
  TableButton, AdminTable, AdminTh, AdminTd, ActionButton, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';

export default function StudentGroupsAdmin() {
  const { t } = useTranslation(['admin', 'common']);
  const [name, setName] = useState('');
  const [size, setSize] = useState(20);
  const [allowProfessionalizzantiDuringStage, setAllowProfessionalizzantiDuringStage] = useState(true);

  const {
    items,
    filteredItems,
    filter,
    setFilter,
    editId,
    setEditId,
    isLoading,
    error,
    clearError,
    load,
    execute,
  } = useAdminList<StudentGroup>({
    fetchFn: api.getStudentGroups,
    filterFn: (item, query) => item.name.toLowerCase().includes(query.toLowerCase()),
  });

  const handleSave = async () => {
    if (!name.trim()) return;

    const data = { name, size, allowProfessionalizzantiDuringStage };

    const action = editId
      ? () => api.updateStudentGroup(editId, data)
      : () => api.createStudentGroup(data);

    await execute(action, () => {
      setName('');
      setSize(20);
      setAllowProfessionalizzantiDuringStage(true);
      setEditId(null);
      load();
    });
  };

  const handleEdit = (item: StudentGroup) => {
    setEditId(item.id);
    setName(item.name);
    setSize(item.size);
    setAllowProfessionalizzantiDuringStage(item.allowProfessionalizzantiDuringStage);
  };

  const handleCancel = () => {
    setEditId(null);
    setName('');
    setSize(20);
    setAllowProfessionalizzantiDuringStage(true);
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteStudentGroup(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:studentGroups')}</TableTitle>

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
          placeholder={t('common:name')}
          value={name}
          onChange={e => setName(e.target.value)}
          disabled={isLoading}
        />
        <TableInput
          className="w-20"
          type="number"
          placeholder={t('admin:size')}
          value={size}
          onChange={e => setSize(Number(e.target.value))}
          disabled={isLoading}
        />
        <label className="flex items-center gap-1 text-sm whitespace-nowrap">
          <input
            type="checkbox"
            checked={allowProfessionalizzantiDuringStage}
            onChange={e => setAllowProfessionalizzantiDuringStage(e.target.checked)}
            disabled={isLoading}
          />
          {t('admin:allowProfessionalizzantiDuringStage')}
        </label>
        <TableButton onClick={handleSave} disabled={isLoading || !name.trim()}>
          {isLoading ? '...' : (editId ? t('common:save') : t('common:add'))}
        </TableButton>
        {editId && (
          <TableButton variant="cancel" onClick={handleCancel} disabled={isLoading}>
            {t('common:cancel')}
          </TableButton>
        )}
      </TableForm>

      <FilterBar
        placeholder={t('admin:filterStudentGroups')}
        value={filter}
        onChange={setFilter}
        totalCount={items.length}
        filteredCount={filteredItems.length}
      />

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>ID</AdminTh>
            <AdminTh>{t('common:name')}</AdminTh>
            <AdminTh>{t('admin:size')}</AdminTh>
            <AdminTh>{t('admin:allowProfessionalizzantiDuringStage')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.name}</AdminTd>
              <AdminTd>{item.size}</AdminTd>
              <AdminTd>{item.allowProfessionalizzantiDuringStage ? t('common:yes') : t('common:no')}</AdminTd>
              <AdminTd>
                <ActionButton onClick={() => handleEdit(item)} disabled={isLoading}>
                  {t('common:edit')}
                </ActionButton>
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
