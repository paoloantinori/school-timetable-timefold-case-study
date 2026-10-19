import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '../../api/scheduleApi';
import { TableContainer, TableTitle, TableForm, TableSelect, TableButton } from './styles';
import { getErrorMessage } from '../../utils/apiError';

const ENTITY_TYPES = ['teachers', 'groups', 'courses', 'holidays'];

export default function CsvImportWizard() {
  const { t } = useTranslation(['admin', 'common']);
  const [entityType, setEntityType] = useState('teachers');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleImport = async () => {
    if (!file) return;
    // Confirm before touching UI state: declining must not leave a spinner render behind
    // (simplify/efficiency review 2026-08-17).
    if (!window.confirm(t('common:confirmImport'))) return;
    setResult('');
    setError('');
    setIsLoading(true);
    try {
      const res = await api.importCsv(entityType, file);
      setResult(t('admin:importResult', { count: res.imported, type: res.entityType }));
      setFile(null);
    } catch (e) {
      setError(getErrorMessage(e, 'Import failed'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:csvImport')}</TableTitle>
      {result && (
        <div className="p-2 bg-feedback-success-bg rounded mb-3 text-sm text-feedback-success-text flex justify-between items-center">
          <span>{result}</span>
          <button
            className="bg-none border-none text-feedback-success-text cursor-pointer text-lg leading-none"
            onClick={() => setResult('')}
            aria-label={t('common:dismiss')}
          >
            ×
          </button>
        </div>
      )}
      {error && (
        <div className="p-2 bg-feedback-error-bg rounded mb-3 text-sm text-feedback-error-text flex justify-between items-center">
          <span>{error}</span>
          <button
            className="bg-none border-none text-feedback-error-text cursor-pointer text-lg leading-none"
            onClick={() => setError('')}
            aria-label={t('common:dismiss')}
          >
            ×
          </button>
        </div>
      )}
      <TableForm>
        <TableSelect value={entityType} onChange={e => setEntityType(e.target.value)} disabled={isLoading}>
          {ENTITY_TYPES.map(et => (
            <option key={et} value={et}>{et}</option>
          ))}
        </TableSelect>
        <input
          type="file"
          accept=".csv"
          onChange={e => setFile(e.target.files?.[0] || null)}
          disabled={isLoading}
        />
        <TableButton onClick={handleImport} disabled={!file || isLoading}>
          {isLoading ? '...' : t('admin:uploadCsv')}
        </TableButton>
      </TableForm>
      <div className="mt-4 text-sm text-muted">
        <h3 className="text-base font-semibold mb-2 text-content">CSV Templates</h3>
        <p><strong>teachers:</strong> name</p>
        <p><strong>groups:</strong> name,size</p>
        <p><strong>courses:</strong> subject,teacherName,studentGroupName,totalHours</p>
        <p><strong>holidays:</strong> date(YYYY-MM-DD),description</p>
      </div>
    </TableContainer>
  );
}
