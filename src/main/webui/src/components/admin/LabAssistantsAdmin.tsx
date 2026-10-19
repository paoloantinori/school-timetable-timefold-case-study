import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { LabAssistant, Teacher } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableForm, TableInput, TableSelect,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton
} from './styles';

export default function LabAssistantsAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const [items, setItems] = useState<LabAssistant[]>([]);
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [teacherId, setTeacherId] = useState<number | null>(null);
  const [roomTag, setRoomTag] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    try {
      setIsLoading(true);
      const data = await api.getLabAssistants();
      setItems(data);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
    api.getTeachers().then(setTeachers).catch(e => {
      console.error('Failed to fetch teachers:', getErrorMessage(e));
    });
  }, []);

  const handleAdd = async () => {
    if (!teacherId || !roomTag.trim()) return;
    try {
      setIsLoading(true);
      setError(null);
      await api.createLabAssistant({ teacherId, roomTag: roomTag.trim() });
      setTeacherId(null);
      setRoomTag('');
      await load();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      setIsLoading(true);
      setError(null);
      await api.deleteLabAssistant(id);
      await load();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:labAssistants')}</TableTitle>

      {error && (
        <div className="p-2 bg-feedback-error-bg rounded mb-3 text-sm text-feedback-error-text flex justify-between items-center">
          <span>{error}</span>
          <button
            className="bg-none border-none text-feedback-error-text cursor-pointer text-lg leading-none"
            onClick={() => setError(null)}
            aria-label={t('common:dismiss')}
          >
            x
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
          {teachers.map(teacher => (
            <option key={teacher.id} value={teacher.id}>{teacher.name}</option>
          ))}
        </TableSelect>
        <TableInput
          type="text"
          placeholder={t('admin:roomTag')}
          value={roomTag}
          onChange={e => setRoomTag(e.target.value)}
          disabled={isLoading}
        />
        <TableButton onClick={handleAdd} disabled={isLoading || !teacherId || !roomTag.trim()}>
          {isLoading ? '...' : t('common:add')}
        </TableButton>
      </TableForm>

      <AdminTable>
        <thead>
          <tr>
            <AdminTh>ID</AdminTh>
            <AdminTh>{t('schedule:teacher')}</AdminTh>
            <AdminTh>{t('admin:roomTag')}</AdminTh>
            <AdminTh>{t('common:actions')}</AdminTh>
          </tr>
        </thead>
        <tbody>
          {items.map(item => (
            <tr key={item.id}>
              <AdminTd>{item.id}</AdminTd>
              <AdminTd>{item.teacher?.name || '--'}</AdminTd>
              <AdminTd>{item.roomTag}</AdminTd>
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
