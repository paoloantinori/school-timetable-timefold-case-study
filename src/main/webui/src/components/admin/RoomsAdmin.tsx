import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Room } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import {
  TableContainer, TableTitle, TableForm, TableInput,
  TableButton, AdminTable, AdminTh, AdminTd, ActionButton, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';

export default function RoomsAdmin() {
  const { t } = useTranslation(['admin', 'common']);
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState(30);
  const [tags, setTags] = useState('');

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
  } = useAdminList<Room>({
    fetchFn: api.getRooms,
    filterFn: (item, query) =>
      item.name.toLowerCase().includes(query.toLowerCase()) ||
      item.tags.some(tag => tag.toLowerCase().includes(query.toLowerCase())),
  });

  const handleSave = async () => {
    if (!name.trim()) return;

    const tagList = tags.split(',').map(t => t.trim()).filter(Boolean);
    const action = editId
      ? () => api.updateRoom(editId, { name, capacity, tags: tagList })
      : () => api.createRoom({ name, capacity, tags: tagList });

    await execute(action, () => {
      setName('');
      setCapacity(30);
      setTags('');
      setEditId(null);
      load();
    });
  };

  const handleEdit = (item: Room) => {
    setEditId(item.id);
    setName(item.name);
    setCapacity(item.capacity);
    setTags(item.tags.join(', '));
  };

  const handleCancel = () => {
    setEditId(null);
    setName('');
    setCapacity(30);
    setTags('');
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteRoom(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:rooms')}</TableTitle>

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
          placeholder={t('admin:capacity')}
          value={capacity}
          onChange={e => setCapacity(Number(e.target.value))}
          disabled={isLoading}
        />
        <TableInput
          placeholder={t('admin:tags') + ' (comma-separated)'}
          value={tags}
          onChange={e => setTags(e.target.value)}
          disabled={isLoading}
        />
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
        placeholder={t('admin:filterRooms')}
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
            <AdminTh>{t('admin:capacity')}</AdminTh>
            <AdminTh>{t('admin:tags')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.name}</AdminTd>
              <AdminTd>{item.capacity}</AdminTd>
              <AdminTd>{item.tags.join(', ')}</AdminTd>
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
