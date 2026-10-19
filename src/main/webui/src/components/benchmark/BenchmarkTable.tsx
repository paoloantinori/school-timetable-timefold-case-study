/**
 * Benchmark results table component
 * Displays sortable, filterable results with notes editing
 */

import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type {
  BenchmarkResult,
  SortField,
  SortOrder,
} from '../../types/benchmark';
import { compareScores, saveNotesForResult, getAlgorithmFamily, getBestPerFamily, FAMILY_COLORS } from '../../api/benchmarkApi';

interface BenchmarkTableProps {
  results: BenchmarkResult[];
  bestScores: {
    hard: number | null;
    medium: number | null;
    soft: number | null;
  };
}

export default function BenchmarkTable({ results, bestScores }: BenchmarkTableProps) {
  const { t } = useTranslation('benchmark');
  const [sortField, setSortField] = useState<SortField>('configNumber');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');
  const [filterText, setFilterText] = useState('');
  const [editingNotes, setEditingNotes] = useState<string | null>(null);
  const [notesValue, setNotesValue] = useState('');

  // Filter and sort results
  const filteredResults = useMemo(() => {
    return results.filter(r =>
      r.configName.toLowerCase().includes(filterText.toLowerCase()) ||
      r.notes?.toLowerCase().includes(filterText.toLowerCase())
    );
  }, [results, filterText]);

  const sortedResults = useMemo(() => {
    const sorted = [...filteredResults].sort((a, b) => {
      let comparison = 0;

      switch (sortField) {
        case 'configNumber':
          comparison = a.configNumber - b.configNumber;
          break;
        case 'score':
          comparison = compareScores(a.score, b.score);
          break;
        case 'time':
          comparison = a.timeMillisSpent - b.timeMillisSpent;
          break;
        case 'date':
          comparison = a.dateRun.localeCompare(b.dateRun);
          break;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });
    return sorted;
  }, [filteredResults, sortField, sortOrder]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortOrder('desc'); // Default to descending for new fields
    }
  };

  const startEditingNotes = (result: BenchmarkResult) => {
    setEditingNotes(result.id);
    setNotesValue(result.notes || '');
  };

  const saveNotes = (resultId: string) => {
    saveNotesForResult(resultId, notesValue);
    // Update the result in place (the parent will re-fetch)
    setEditingNotes(null);
    setNotesValue('');
  };

  const cancelNotes = () => {
    setEditingNotes(null);
    setNotesValue('');
  };

  const getSortIcon = (field: SortField) => {
    if (sortField !== field) return '↕';
    return sortOrder === 'asc' ? '↑' : '↓';
  };

  const isBestScore = (result: BenchmarkResult, type: 'hard' | 'medium' | 'soft') => {
    return bestScores[type] !== null && result.score[type] === bestScores[type];
  };

  // Best-in-family highlighting
  const bestInFamily = useMemo(() => getBestPerFamily(results), [results]);

  return (
    <div className="space-y-4">
      {/* Search bar */}
      <div className="flex gap-2 items-center">
        <input
          type="text"
          placeholder={t('search.placeholder')}
          value={filterText}
          onChange={e => setFilterText(e.target.value)}
          className="px-3 py-2 border border-border rounded text-sm flex-1 max-w-md
                     bg-surface text-content focus:outline-none focus:ring-2 focus:ring-accent"
        />
        <span className="text-sm text-muted">
          {filteredResults.length} {t('summary.totalConfigs')}
        </span>
      </div>

      {/* Results table */}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b-2 border-border-light">
              <th className="text-left p-2 font-semibold text-muted cursor-pointer hover:text-content"
                  onClick={() => handleSort('configNumber')}>
                {t('table.configNumber')} {getSortIcon('configNumber')}
              </th>
              <th className="text-left p-2 font-semibold text-muted">
                {t('table.family')}
              </th>
              <th className="text-left p-2 font-semibold text-muted cursor-pointer hover:text-content"
                  onClick={() => handleSort('score')}>
                {t('table.score')} {getSortIcon('score')}
              </th>
              <th className="text-left p-2 font-semibold text-muted">
                {t('table.configName')}
              </th>
              <th className="text-left p-2 font-semibold text-muted cursor-pointer hover:text-content"
                  onClick={() => handleSort('time')}>
                {t('table.time')} {getSortIcon('time')}
              </th>
              <th className="text-left p-2 font-semibold text-muted cursor-pointer hover:text-content"
                  onClick={() => handleSort('date')}>
                {t('table.date')} {getSortIcon('date')}
              </th>
              <th className="text-left p-2 font-semibold text-muted">
                {t('table.notes')}
              </th>
            </tr>
          </thead>
          <tbody>
            {sortedResults.map(result => {
              const family = getAlgorithmFamily(result.configName);
              return (
              <tr key={result.id} className="border-b border-border-faint hover:bg-surface-alt">
                <td className="p-2 font-mono text-content">#{result.configNumber}</td>
                <td className="p-2">
                  <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium bg-surface-alt border-l-2 ${FAMILY_COLORS[family]} ${bestInFamily[family]?.id === result.id ? 'ring-1 ring-accent/50' : ''}`}>
                    {t(`family.${family}`)}
                  </span>
                </td>
                <td className="p-2">
                  <div className="flex gap-2 font-mono text-xs">
                    <span className={`px-1.5 py-0.5 rounded ${isBestScore(result, 'hard') ? 'bg-constraint-hard/20 text-constraint-hard font-bold' : 'bg-constraint-hard/10 text-constraint-hard'}`}>
                      {t('score.hard')}: {result.score.hard}
                    </span>
                    <span className={`px-1.5 py-0.5 rounded ${isBestScore(result, 'medium') ? 'bg-constraint-medium/20 text-constraint-medium font-bold' : 'bg-constraint-medium/10 text-constraint-medium'}`}>
                      {t('score.medium')}: {result.score.medium.toLocaleString()}
                    </span>
                    <span className={`px-1.5 py-0.5 rounded ${isBestScore(result, 'soft') ? 'bg-constraint-soft/20 text-constraint-soft font-bold' : 'bg-constraint-soft/10 text-constraint-soft'}`}>
                      {t('score.soft')}: {result.score.soft.toLocaleString()}
                    </span>
                  </div>
                </td>
                <td className="p-2 text-content font-medium">{result.configName}</td>
                <td className="p-2 text-muted font-mono">{result.timeFormatted}</td>
                <td className="p-2 text-muted">{result.dateRun}</td>
                <td className="p-2">
                  {editingNotes === result.id ? (
                    <div className="flex gap-1">
                      <input
                        type="text"
                        value={notesValue}
                        onChange={e => setNotesValue(e.target.value)}
                        placeholder={t('table.notesPlaceholder')}
                        className="px-2 py-1 border border-border rounded text-sm w-40
                                   bg-surface text-content focus:outline-none focus:ring-1 focus:ring-accent"
                      />
                      <button
                        onClick={() => saveNotes(result.id)}
                        className="px-2 py-1 bg-green-500 text-white rounded text-xs hover:bg-green-600 cursor-pointer"
                      >
                        {t('table.saveNotes')}
                      </button>
                      <button
                        onClick={cancelNotes}
                        className="px-2 py-1 bg-gray-500 text-white rounded text-xs hover:bg-gray-600 cursor-pointer"
                      >
                        {t('table.cancelNotes')}
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => startEditingNotes(result)}
                      className="text-left text-muted hover:text-accent cursor-pointer text-sm max-w-[150px] truncate"
                      title={result.notes || t('table.addNotes')}
                    >
                      {result.notes || t('table.noNotes')}
                    </button>
                  )}
                </td>
                <td className="p-2 text-muted text-xs font-mono">
                  {result.directoryName}
                </td>
              </tr>
            );
            })}
          </tbody>
        </table>

        {filteredResults.length === 0 && (
          <div className="text-center py-8 text-muted">
            {filterText ? t('search.noResults') : t('noResults')}
          </div>
        )}
      </div>
    </div>
  );
}
