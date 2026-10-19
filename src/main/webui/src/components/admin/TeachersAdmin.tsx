import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Teacher } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import {
  TableContainer, TableTitle, TableForm, TableInput,
  TableButton, AdminTable, AdminTh, AdminTd, ActionButton, DeleteButton
} from './styles';
import { FilterBar } from './FilterBar';
import { useAdminList } from '../../hooks/useAdminList';

export default function TeachersAdmin() {
  const { t } = useTranslation(['admin', 'common']);
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [color, setColor] = useState<string>('');
  const [maxDailyHours, setMaxDailyHours] = useState<string>('');

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
  } = useAdminList<Teacher>({
    fetchFn: api.getTeachers,
    filterFn: (item, query) =>
      item.name.toLowerCase().includes(query.toLowerCase()) ||
      (item.nickname?.toLowerCase().includes(query.toLowerCase()) ?? false),
  });

  const handleSave = async () => {
    if (!name.trim()) return;

    const colorValue = color.trim() || null;
    // TASK-348 code review (D1): a blank input only sends the 0 clear
    // sentinel when the teacher being edited currently HAS an override
    // (looked up from the already-loaded list) - otherwise there is nothing
    // to clear, so it sends null (omit/keep, same as before).
    const editingHadOverride = editId != null && items.some(i => i.id === editId && i.maxDailyHours != null);
    const maxDailyHoursValue = maxDailyHours.trim()
      ? Number(maxDailyHours)
      : (editingHadOverride ? 0 : null);
    const action = editId
      ? () => api.updateTeacher(editId, { name, nickname: nickname || null, color: colorValue, maxDailyHours: maxDailyHoursValue })
      : () => api.createTeacher({ name, nickname: nickname || null, color: colorValue, maxDailyHours: maxDailyHoursValue });

    await execute(action, () => {
      setName('');
      setNickname('');
      setColor('');
      setMaxDailyHours('');
      setEditId(null);
      load();
    });
  };

  const handleEdit = (item: Teacher) => {
    setEditId(item.id);
    setName(item.name);
    setNickname(item.nickname || '');
    setColor(item.color || '');
    setMaxDailyHours(item.maxDailyHours != null ? String(item.maxDailyHours) : '');
  };

  const handleCancel = () => {
    setEditId(null);
    setName('');
    setNickname('');
    setColor('');
    setMaxDailyHours('');
  };

  const handleDelete = async (id: number) => {
    await execute(() => api.deleteTeacher(id), load);
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:teachers')}</TableTitle>

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
          className="max-w-[150px]"
          placeholder={t('admin:nickname')}
          value={nickname}
          onChange={e => setNickname(e.target.value.slice(0, 20))}
          maxLength={20}
          title={t('admin:nicknameHint')}
          disabled={isLoading}
        />
        <div className="flex items-center gap-1">
          <input
            type="color"
            value={color || '#3b82f6'}
            onChange={e => setColor(e.target.value)}
            disabled={isLoading}
            className="w-8 h-8 border border-border rounded cursor-pointer"
            title={t('admin:teacherColor')}
          />
          <span className="text-xs text-muted">{t('admin:color')}</span>
        </div>
        <TableInput
          type="number"
          className="max-w-[80px]"
          placeholder={t('admin:maxDailyHours')}
          min={1}
          max={8}
          value={maxDailyHours}
          onChange={e => setMaxDailyHours(e.target.value)}
          title={t('admin:maxDailyHoursHint')}
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
        placeholder={t('admin:filterTeachers')}
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
            <AdminTh><span className="max-w-[100px] inline-block">{t('admin:nickname')}</span></AdminTh>
            <AdminTh>{t('admin:color')}</AdminTh>
            <AdminTh>{t('admin:maxDailyHours')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {filteredItems.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.name}</AdminTd>
              <AdminTd>
                <span className={item.nickname ? 'text-content' : 'text-faint italic'}>
                  {item.nickname || '—'}
                </span>
              </AdminTd>
              <AdminTd>
                {item.color ? (
                  <div className="flex items-center gap-1">
                    {([
                      { key: 'plain', label: 'plain', bg: undefined, bgSize: undefined },
                      { key: 'dotted', label: 'dotted',
                        bg: 'radial-gradient(circle, rgba(255,255,255,0.45) 1.2px, transparent 1.2px)' ,
                        bgSize: '4px 4px' },
                      { key: 'hatched', label: 'hatched',
                        bg: 'repeating-linear-gradient(45deg,transparent,transparent 2px,rgba(255,255,255,0.4) 2px,rgba(255,255,255,0.4) 3.5px),repeating-linear-gradient(-45deg,transparent,transparent 2px,rgba(255,255,255,0.4) 2px,rgba(255,255,255,0.4) 3.5px)', bgSize: undefined },
                      { key: 'striped', label: 'striped',
                        bg: 'repeating-linear-gradient(135deg,transparent,transparent 3px,rgba(255,255,255,0.45) 3px,rgba(255,255,255,0.45) 5px)', bgSize: undefined },
                    ] as const).map(p => (
                      <div key={p.key}
                        className={`w-4 h-4 rounded-sm border ${item.pattern === p.key ? 'border-white ring-2 ring-offset-1 ring-blue-500' : 'border-border opacity-40'}`}
                        style={{
                          backgroundColor: item.color!,
                          backgroundImage: p.bg,
                          backgroundSize: p.bgSize,
                        }}
                        title={p.label} />
                    ))}
                  </div>
                ) : (
                  <span className="text-xs text-muted">auto</span>
                )}
              </AdminTd>
              <AdminTd>
                <span className={item.maxDailyHours != null ? 'text-content' : 'text-faint italic'}>
                  {item.maxDailyHours != null ? item.maxDailyHours : '6'}
                </span>
              </AdminTd>
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
