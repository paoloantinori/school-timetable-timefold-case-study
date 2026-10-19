import { useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import type { ConstraintAnalysis, LessonViolationAnalysis, AnyJustification } from '../types/domain';
import * as api from '../api/scheduleApi';
import { renderJustification } from '../utils';
import { getConstraintDescription } from './LessonTooltip';

/** Extract pair key from a pair-based justification, or null if not pair-based */
function getPairKey(justification: AnyJustification | undefined): string | null {
  if (!justification) return null;
  const j = justification as unknown as Record<string, unknown>;
  if ('lesson1Id' in j && 'lesson2Id' in j && typeof j.lesson1Id === 'number' && typeof j.lesson2Id === 'number') {
    const a = j.lesson1Id as number;
    const b = j.lesson2Id as number;
    return `${Math.min(a, b)}-${Math.max(a, b)}`;
  }
  return null;
}

interface GroupedViolation {
  type: 'pair' | 'single';
  pairKey: string;
  entries: LessonViolationAnalysis[];
}

interface Props {
  analysis: ConstraintAnalysis;
  onClose: () => void;
}

type FilterType = 'ALL' | 'HARD' | 'QUASI' | 'MEDIUM' | 'SOFT';
type SortField = 'score' | 'matches' | 'constraint';
type SortDir = 'asc' | 'desc';

// Derive constraint type from numeric scores. QUASI (hard level 1, TASK-538) ranks
// between pure hard and medium, mirroring the BendableScore ordering.
function getConstraintType(hard: number, quasi: number, medium: number, soft: number): 'HARD' | 'QUASI' | 'MEDIUM' | 'SOFT' {
  if (hard < 0) return 'HARD';
  if (quasi < 0) return 'QUASI';
  if (medium < 0) return 'MEDIUM';
  return 'SOFT';
}

// Analysis table column widths (percentage + minimum for mobile)
const ANALYSIS_TABLE_COLUMNS = {
  constraint: 'w-[40%] min-w-[180px]',
  type: 'w-[10%] min-w-[80px]',
  score: 'w-[15%] min-w-[100px]',
  matches: 'w-[15%] min-w-[80px]',
  actions: 'w-[20%] min-w-[90px]',
} as const;

/** Renders a single lesson's info + its violations (used inside cards) */
function LessonEntry({ lv }: { lv: LessonViolationAnalysis }) {
  const { t } = useTranslation('schedule');
  return (
    <div className="flex flex-col gap-2 text-sm">
      <div className="flex items-center gap-3 font-mono text-xs text-muted">
        <span>ID: {lv.lessonId}</span>
        <span>{lv.entityType}</span>
        <span>{lv.teacher}</span>
        <span>{lv.subject}</span>
        <span>{lv.studentGroup}</span>
      </div>
      {lv.violations.map((v, vIdx) => (
        <div key={vIdx} className="flex flex-col gap-1 pl-3 border-l-2 border-border-faint">
          <div className="flex items-start gap-2">
            <span className="font-mono font-bold text-xs px-1.5 py-0.5 rounded text-white flex-shrink-0"
                  style={{ background: 'var(--violation-badge-bg)' }}>
              {v.constraint}
            </span>
            <span className="text-muted text-xs">
              {v.message}
            </span>
          </div>
          {/* For single-card entries, show justification inline;
              for paired entries, justification is shown at the bottom of the pair card */}
        </div>
      ))}
    </div>
  );
}

export default function AnalysisPanel({ analysis, onClose }: Props) {
  const { t, i18n } = useTranslation('schedule');
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [sortField, setSortField] = useState<SortField>('score');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [detailsConstraint, setDetailsConstraint] = useState<string | null>(null);
  const [lessonViolations, setLessonViolations] = useState<LessonViolationAnalysis[]>([]);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [detailsError, setDetailsError] = useState<string | null>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // Focus trap and keyboard handling
  useEffect(() => {
    // Focus the close button when panel opens
    closeButtonRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const formatScore = (score: number): string => {
    if (score === 0) return '0';
    const abs = Math.abs(score);
    const sign = score < 0 ? '-' : '+';
    if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1)}M`;
    if (abs >= 1_000) return `${sign}${(abs / 1_000).toFixed(1)}k`;
    return `${sign}${abs}`;
  };

  // Get the primary score for a constraint (non-zero value, prioritizing hard > quasi > medium > soft)
  function getPrimaryScore(c: { hard: number; quasi: number; medium: number; soft: number }): number {
    if (c.hard !== 0) return c.hard;
    if (c.quasi !== 0) return c.quasi;
    if (c.medium !== 0) return c.medium;
    return c.soft;
  }

  // Constraint type priority for sorting: HARD=0, QUASI=1, MEDIUM=2, SOFT=3
  function getTypePriority(type: 'HARD' | 'QUASI' | 'MEDIUM' | 'SOFT'): number {
    switch (type) {
      case 'HARD': return 0;
      case 'QUASI': return 1;
      case 'MEDIUM': return 2;
      case 'SOFT': return 3;
    }
  }

  const filteredAndSorted = useMemo(() => {
    let result = [...analysis.constraints];

    if (filter !== 'ALL') {
      result = result.filter(c => getConstraintType(c.hard, c.quasi ?? 0, c.medium, c.soft) === filter);
    }

    result.sort((a, b) => {
      let cmp = 0;
      if (sortField === 'score') {
        // Sort by constraint type first (HARD > MEDIUM > SOFT), then by magnitude
        const aType = getConstraintType(a.hard, a.quasi ?? 0, a.medium, a.soft);
        const bType = getConstraintType(b.hard, b.quasi ?? 0, b.medium, b.soft);
        const aPriority = getTypePriority(aType);
        const bPriority = getTypePriority(bType);

        if (aPriority !== bPriority) {
          // Different types: sort by type priority (lower = more severe)
          cmp = aPriority - bPriority;
        } else {
          // Same type: sort by absolute magnitude (more violations = more severe)
          const aScore = getPrimaryScore(a);
          const bScore = getPrimaryScore(b);
          // For negative scores, more negative = more severe
          cmp = aScore - bScore;
        }
      } else if (sortField === 'matches') {
        cmp = b.matches - a.matches;
      } else if (sortField === 'constraint') {
        cmp = a.constraint.localeCompare(b.constraint);
      }
      return sortDir === 'desc' ? -cmp : cmp;
    });

    return result;
  }, [analysis.constraints, filter, sortField, sortDir]);

  const handleSort = useCallback((field: SortField) => {
    if (sortField === field) {
      setSortDir(d => d === 'desc' ? 'asc' : 'desc');
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  }, [sortField, sortDir]);

  const getScoreColor = (score: number) => {
    if (score > 0) return 'text-feedback-success-text';
    if (score < 0) return 'text-feedback-error-text';
    return 'text-muted';
  };

  const getTypeBadgeClass = (type: 'HARD' | 'QUASI' | 'MEDIUM' | 'SOFT') => {
    switch (type) {
      case 'HARD': return 'bg-[var(--score-hard-bg)] text-constraint-hard';
      case 'QUASI': return 'bg-[var(--score-hard-bg)] text-constraint-hard';
      case 'MEDIUM': return 'bg-[var(--score-medium-bg)] text-constraint-medium';
      case 'SOFT': return 'bg-[var(--score-soft-bg)] text-constraint-soft';
    }
  };

  const renderSortIcon = (field: SortField) => {
    const isActive = sortField === field;
    const icon = sortDir === 'desc' ? '\u25BC' : '\u25B2';
    return (
      <span className={`ml-1 ${isActive ? 'opacity-100' : 'opacity-50'}`}>
        {isActive ? icon : '\u25BD'}
      </span>
    );
  };

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // Show lesson-level details for a constraint
  const handleShowDetails = useCallback(async (constraintId: string) => {
    setDetailsConstraint(constraintId);
    setLoadingDetails(true);
    setDetailsError(null);
    try {
      // Use backend filtering for better performance
      const data = await api.analyzeLessonViolations(constraintId, i18n.language); // TASK-662: messaggi backend nella lingua attiva
      setLessonViolations(data);
    } catch (err: any) {
      if (err?.response?.status === 409) {
        setDetailsError(t('errorDetailsWhileSolving'));
      } else {
        setDetailsError(t('errorLoadingDetails') || 'Failed to load lesson details');
      }
    } finally {
      setLoadingDetails(false);
    }
  }, [t, i18n]);

  // Group pair-based violations (H1, H2, H3, H-D1) so each conflict pair
  // is shown once with both lessons, instead of two separate cards.
  const groupedViolations: GroupedViolation[] = useMemo(() => {
    if (lessonViolations.length === 0) return [];

    // Check if any violation has pair-based justification
    const hasPair = lessonViolations.some(lv =>
      lv.violations.some(v => getPairKey(v.justification) !== null)
    );

    if (!hasPair) {
      return lessonViolations.map(lv => ({ type: 'single' as const, pairKey: String(lv.lessonId), entries: [lv] }));
    }

    const pairMap = new Map<string, LessonViolationAnalysis[]>();

    for (const lv of lessonViolations) {
      const pairKey = lv.violations
        .map(v => getPairKey(v.justification))
        .find(k => k !== null);

      if (pairKey) {
        if (!pairMap.has(pairKey)) pairMap.set(pairKey, []);
        const group = pairMap.get(pairKey)!;
        if (!group.some(e => e.lessonId === lv.lessonId)) {
          group.push(lv);
        }
      } else {
        // Non-pair violation mixed in — show individually
        const singleKey = `single-${lv.lessonId}`;
        pairMap.set(singleKey, [lv]);
      }
    }

    return Array.from(pairMap.entries()).map(([key, entries]) => ({
      type: entries.length >= 2 ? 'pair' as const : 'single' as const,
      pairKey: key,
      entries,
    }));
  }, [lessonViolations]);

  const handleCloseDetails = useCallback(() => {
    setDetailsConstraint(null);
    setLessonViolations([]);
    setDetailsError(null);
  }, []);

  return (
    <div
      className="fixed inset-0 bg-overlay flex items-center justify-center z-modal"
      onClick={handleOverlayClick}
      role="presentation"
    >
      <div
        ref={panelRef}
        className="bg-surface rounded-xl w-[90%] max-w-[95vw] md:max-w-panel lg:max-w-[1100px] max-h-[85vh]
                   flex flex-col shadow-[0_8px_32px_var(--shadow)]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="analysis-title"
      >
        {/* Header */}
        <header className="flex justify-between items-center px-4 md:px-6 py-3 md:py-4
                           border-b border-border-light">
          <h3 id="analysis-title" className="m-0 text-xl font-semibold text-content">
            {t('analysisTitle')}
          </h3>
          <button
            ref={closeButtonRef}
            className="bg-none border-none text-2xl cursor-pointer text-muted
                       p-1 leading-none"
            onClick={onClose}
            aria-label={t('common:close')}
          >
            &times;
          </button>
        </header>

        {/* Content */}
        <div className="p-4 md:p-6 overflow-y-auto flex-1">
          {/* Score Summary Cards */}
          <div className="grid grid-cols-2 md:grid-cols-[repeat(auto-fit,minmax(140px,1fr))]
                          gap-2 md:gap-4 mb-4 md:mb-6">
            <div className="p-4 rounded-lg text-center
                            bg-[var(--score-hard-bg)]
                            border border-[var(--score-hard-border)] text-constraint-hard">
              <div className="text-xs font-semibold uppercase tracking-wide mb-2">
                {t('sidebar.hardScore')}
              </div>
              <div className="text-2xl font-bold">
                {formatScore(analysis.hard)}
              </div>
            </div>
            <div className="p-4 rounded-lg text-center
                            bg-[var(--score-medium-bg)]
                            border border-[var(--score-medium-border)] text-constraint-medium">
              <div className="text-xs font-semibold uppercase tracking-wide mb-2">
                {t('sidebar.mediumScore')}
              </div>
              <div className="text-2xl font-bold">
                {formatScore(analysis.medium)}
              </div>
            </div>
            <div className="p-4 rounded-lg text-center
                            bg-[var(--score-soft-bg)]
                            border border-[var(--score-soft-border)] text-constraint-soft">
              <div className="text-xs font-semibold uppercase tracking-wide mb-2">
                {t('sidebar.softScore')}
              </div>
              <div className="text-2xl font-bold">
                {formatScore(analysis.soft)}
              </div>
            </div>
            <div className="p-4 rounded-lg text-center
                            bg-feedback-success-bg
                            border border-feedback-success-border text-feedback-success-text">
              <div className="text-xs font-semibold uppercase tracking-wide mb-2">
                {t('totalScore')}
              </div>
              <div className="text-sm font-mono leading-relaxed">
                {analysis.score.split('/').map((part, idx, arr) => (
                  <span key={idx}>
                    {part}
                    {idx < arr.length - 1 && <span className="opacity-60"> / </span>}
                  </span>
                ))}
              </div>
            </div>
          </div>

          {/* Controls */}
          <div className="flex gap-4 mb-4 flex-wrap">
            <div className="flex items-center gap-2">
              <label className="text-sm font-medium text-muted">
                {t('filterByType')}:
              </label>
              <select
                className="px-2 py-2 border border-border rounded-md bg-surface
                           text-content text-sm cursor-pointer"
                value={filter}
                onChange={e => setFilter(e.target.value as FilterType)}
              >
                <option value="ALL">{t('allTypes')}</option>
                <option value="HARD">{t('sidebar.hardScore')}</option>
                <option value="QUASI">{t('scoreQuasi')}</option>
                <option value="MEDIUM">{t('sidebar.mediumScore')}</option>
                <option value="SOFT">{t('sidebar.softScore')}</option>
              </select>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-muted">
                {t('constraintCount', { count: filteredAndSorted.length })}
              </span>
            </div>
          </div>

          {/* Constraint Table */}
          {filteredAndSorted.length > 0 ? (
            <div className="overflow-x-auto rounded-lg border border-border-light -mx-4 px-4">
              <table className="min-w-full border-collapse text-sm table-fixed">
                <colgroup>
                  <col className={ANALYSIS_TABLE_COLUMNS.constraint} />
                  <col className={ANALYSIS_TABLE_COLUMNS.type} />
                  <col className={ANALYSIS_TABLE_COLUMNS.score} />
                  <col className={ANALYSIS_TABLE_COLUMNS.matches} />
                  <col className={ANALYSIS_TABLE_COLUMNS.actions} />
                </colgroup>
                <thead>
                  <tr>
                    <th
                      className={`p-3 text-left bg-surface-alt border-b-2 border-border
                                 font-semibold cursor-pointer select-none
                                 ${sortField === 'constraint' ? 'text-content' : 'text-muted'}`}
                      onClick={() => handleSort('constraint')}
                    >
                      <span className="flex items-center">
                        {t('constraintId')}
                        {renderSortIcon('constraint')}
                      </span>
                    </th>
                    <th className="p-3 text-left bg-surface-alt border-b-2 border-border
                                  font-semibold text-muted">
                      {t('type')}
                    </th>
                    <th
                      className={`p-3 text-left bg-surface-alt border-b-2 border-border
                                 font-semibold cursor-pointer select-none
                                 ${sortField === 'score' ? 'text-content' : 'text-muted'}`}
                      onClick={() => handleSort('score')}
                    >
                      <span className="flex items-center">
                        {t('score')}
                        {renderSortIcon('score')}
                      </span>
                    </th>
                    <th
                      className={`p-3 text-left bg-surface-alt border-b-2 border-border
                                 font-semibold cursor-pointer select-none
                                 ${sortField === 'matches' ? 'text-content' : 'text-muted'}`}
                      onClick={() => handleSort('matches')}
                    >
                      <span className="flex items-center">
                        {t('matches')}
                        {renderSortIcon('matches')}
                      </span>
                    </th>
                    <th className="p-3 text-left bg-surface-alt border-b-2 border-border
                                  font-semibold text-muted">
                      {t('actions')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAndSorted.map((c, idx) => {
                    const constraintType = getConstraintType(c.hard, c.quasi ?? 0, c.medium, c.soft);
                    // Primary non-zero score: hard > quasi > medium > soft
                    const displayScore = getPrimaryScore({ ...c, quasi: c.quasi ?? 0 });
                    return (
                    <tr key={`${c.constraint}-${idx}`}>
                      <td className="p-3 border-b border-border-faint text-content">
                        <span
                          className="block font-mono font-semibold truncate"
                          title={c.constraint}
                        >
                          {c.constraint}
                        </span>
                      </td>
                      <td className="p-3 border-b border-border-faint">
                        <span className={`inline-block px-2 py-0.5 rounded text-xs
                                         font-semibold uppercase whitespace-nowrap
                                         ${getTypeBadgeClass(constraintType)}`}>
                          {constraintType}
                        </span>
                      </td>
                      <td className={`p-3 border-b border-border-faint
                                     font-mono font-semibold whitespace-nowrap
                                     ${getScoreColor(displayScore)}`}>
                        {formatScore(displayScore)}
                      </td>
                      <td className="p-3 border-b border-border-faint text-content whitespace-nowrap">
                        {c.matches.toLocaleString()}
                      </td>
                      <td className="p-3 border-b border-border-faint">
                        <button
                          className="px-2 py-1 text-xs font-semibold bg-action-bg text-action-text
                                     border border-action-border rounded hover:bg-action-hover
                                     transition-colors duration-150"
                          onClick={() => handleShowDetails(c.constraint)}
                        >
                          {t('details') || 'Details'}
                        </button>
                      </td>
                    </tr>
                  );})}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center p-8 text-muted">
              {t('noConstraintsFound')}
            </div>
          )}
        </div>
      </div>

      {/* Lesson Details Modal */}
      {detailsConstraint && (
        <div
          className="fixed inset-0 bg-overlay bg-opacity-75 flex items-center justify-center z-modal-high"
          role="dialog"
          aria-modal="true"
          aria-labelledby="details-title"
        >
          <div className="bg-surface rounded-xl w-[90%] max-w-[95vw] md:max-w-[800px] max-h-[80vh]
                      flex flex-col shadow-[0_8px_32px_var(--shadow)]">
            {/* Header */}
            <header className="flex justify-between items-center px-4 md:px-6 py-3 md:py-4
                               border-b border-border-light">
              <h3 id="details-title" className="m-0 text-lg font-semibold text-content">
                {t('lessonDetailsFor')} {getConstraintDescription(detailsConstraint)}
              </h3>
              <button
                className="bg-none border-none text-2xl cursor-pointer text-muted
                           p-1 leading-none"
                onClick={handleCloseDetails}
                aria-label={t('common:close')}
              >
                &times;
              </button>
            </header>

            {/* Content */}
            <div className="p-4 md:p-6 overflow-y-auto flex-1">
              {loadingDetails ? (
                <div className="text-center p-8 text-muted">
                  {t('loading') || 'Loading...'}
                </div>
              ) : detailsError ? (
                <div className="text-center p-8 text-feedback-error-text">
                  {detailsError}
                </div>
              ) : groupedViolations.length === 0 ? (
                <div className="text-center p-8 text-muted">
                  {t('noViolationsFound') || 'No violations found'}
                </div>
              ) : (
                <div className="flex flex-col gap-3">
                  {groupedViolations.map((group) => (
                    group.type === 'pair' ? (
                      /* Pair-based violation: show both lessons in one card */
                      <div key={group.pairKey}
                           className="rounded-lg border border-border-faint bg-surface-alt overflow-hidden">
                        <div className="flex items-stretch">
                          {/* Lesson A */}
                          <div className="flex-1 p-3">
                            <LessonEntry lv={group.entries[0]} />
                          </div>
                          {/* Visual divider */}
                          <div className="flex items-center px-2 py-1">
                            <div className="flex flex-col items-center gap-1 text-muted">
                              <div className="w-px h-3 bg-border-faint" />
                              <span className="text-[10px] font-bold uppercase tracking-wider opacity-60">
                                {t('vs') || 'VS'}
                              </span>
                              <div className="w-px h-3 bg-border-faint" />
                            </div>
                          </div>
                          {/* Lesson B */}
                          <div className="flex-1 p-3">
                            <LessonEntry lv={group.entries[1]} />
                          </div>
                        </div>
                        {/* Shared constraint details (from first entry's justification) */}
                        {group.entries[0].violations[0]?.justification && (
                          <div className="px-4 py-2 border-t border-border-faint bg-surface text-xs text-content-secondary">
                            <span className="font-medium">{t('details') || 'Details'}:</span>{' '}
                            {renderJustification(group.entries[0].violations[0].justification)}
                          </div>
                        )}
                      </div>
                    ) : (
                      /* Single (non-pair) violation */
                      <div key={`single-${group.entries[0].lessonId}`}
                           className="p-3 rounded-lg border border-border-faint bg-surface-alt">
                        <LessonEntry lv={group.entries[0]} />
                      </div>
                    )
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
