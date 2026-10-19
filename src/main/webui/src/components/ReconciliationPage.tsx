/**
 * Reconciliation dashboard (TASK-353).
 *
 * Replicates the school's own "Classi" Excel sheet: per (teacher, subject,
 * group) course, compares assigned hours (Ore Inc. / Course.totalHours)
 * against scheduled hours (Ore Prg. / lessons with a placed timeslot).
 * See claudedocs/spalmone-2026-27/analysis.md §6 and
 * claudedocs/design/stage-esame-ra-modeling-2026-07-16.md for the source
 * semantics - pseudo (Stage/Esame/RA) courses are included but rendered
 * with a neutral "expected" status instead of the raw MISMATCH/UNSCHEDULED
 * color, since their deltas are expected by design ("A posto"), not bugs
 * ("Analizzare") - unless the shortfall is large (see isPseudoCheck below),
 * in which case it still surfaces as an amber "Check" chip. See the nav
 * badge (useReconciliationBadge.ts) for the same pseudo exclusion applied
 * to the actionable-issue count.
 *
 * The page forces a fresh fetch on mount and exposes a manual Refresh
 * button (the shared cache otherwise tolerates up to
 * RECONCILIATION_CACHE_TTL_MS of staleness - see useReconciliationReport.ts);
 * bucket/rollup severity is driven by issueCount, not the summed delta's
 * sign; the drill-down surfaces fetch failures instead of hanging on
 * "loading"; and expandable rows are keyboard-accessible.
 */
import { Fragment, useMemo, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { getReconciliationLessons } from '../api/scheduleApi';
import { isReconciliationIssue, useReconciliationReport } from '../hooks/useReconciliationReport';
import type { ReconciliationRow, ReconciliationRollup, CourseLessonSummary } from '../types/domain';
import { FilterBar } from './admin/FilterBar';
import { StatusBadge } from './ui/StatusBadge';

type GroupByMode = 'teacher' | 'group';

function matchesFilter(row: ReconciliationRow, filter: string): boolean {
  if (!filter) return true;
  const needle = filter.toLowerCase();
  return (
    (row.teacherName ?? '').toLowerCase().includes(needle) ||
    row.subject.toLowerCase().includes(needle) ||
    (row.groupName ?? '').toLowerCase().includes(needle)
  );
}

/**
 * Pseudo (Stage/Esame/RA) rows render as neutral "Expected" by default, but
 * a shortfall this large is still worth a human glance even though it's
 * expected by design - so it gets an amber "Check" chip instead. Threshold
 * chosen to flag gross errors (e.g. a stage period barely started) without
 * flagging the routine partial-shortfall case documented in analysis.md §6.
 *
 * Judged on the ACTIONABLE delta (`delta - expectedUnscheduledHours`), not
 * the raw delta (TASK-373 code review H1): the golden Stage course's own
 * delta is, by construction, exactly its closure-day + beyond-horizon hours
 * (see ReconciliationService/GoldenScheduleImportService#reconcileStageCourseHours) -
 * a structurally expected shortfall, not a real one. Using the raw delta
 * here used to misfire on groups where that shortfall exceeds 50% of
 * assigned hours (e.g. G12, 183 of 295h on the Easter closure) even
 * though nothing is actually wrong; the actionable delta is zero for any
 * row whose entire delta is already accounted for.
 */
function isPseudoCheck(row: ReconciliationRow): boolean {
  if (!row.pseudo || row.status !== 'MISMATCH' || row.assignedHours <= 0) return false;
  const actionableDelta = row.delta - row.expectedUnscheduledHours;
  return Math.abs(actionableDelta) > row.assignedHours * 0.5;
}

interface Bucket {
  key: string;
  rows: ReconciliationRow[];
  assignedHours: number;
  scheduledHours: number;
  delta: number;
  issueCount: number;
}

/**
 * Buckets the (already filtered) rows for the subtotal header rows. When no
 * text/issue filter narrows the row set, the subtotal numbers are taken
 * straight from the backend's teacherRollups/groupRollups (computed over
 * the whole dataset) instead of being recomputed client-side - closing the
 * drift risk between the two implementations by usage rather than by a
 * dedicated equality test (this webui package has no unit test runner
 * configured yet, so adding one just for this comparison would be a bigger
 * lift than eliminating the second implementation). When a filter is
 * active, the backend rollups don't reflect it, so we fall back to
 * recomputing from the filtered rows - and do the same for any bucket key
 * absent from the backend rollups (e.g. "—" for courses without a teacher).
 */
function bucketRows(rows: ReconciliationRow[], mode: GroupByMode, backendRollups: ReconciliationRollup[] | null): Bucket[] {
  const grouped = new Map<string, ReconciliationRow[]>();
  for (const row of rows) {
    const key = (mode === 'teacher' ? row.teacherName : row.groupName) ?? '—';
    const list = grouped.get(key);
    if (list) {
      list.push(row);
    } else {
      grouped.set(key, [row]);
    }
  }
  const rollupByName = new Map((backendRollups ?? []).map(r => [r.name, r]));

  const buckets: Bucket[] = [];
  for (const [key, bucketRowList] of grouped) {
    const rollup = rollupByName.get(key);
    if (rollup) {
      buckets.push({
        key,
        rows: bucketRowList,
        assignedHours: rollup.assignedHours,
        scheduledHours: rollup.scheduledHours,
        delta: rollup.delta,
        issueCount: rollup.issueCount,
      });
    } else {
      let assignedHours = 0;
      let scheduledHours = 0;
      let issueCount = 0;
      for (const r of bucketRowList) {
        assignedHours += r.assignedHours;
        scheduledHours += r.scheduledHours;
        if (isReconciliationIssue(r)) issueCount++;
      }
      buckets.push({
        key,
        rows: bucketRowList,
        assignedHours,
        scheduledHours,
        delta: assignedHours - scheduledHours,
        issueCount,
      });
    }
  }
  return buckets.sort((a, b) => a.key.localeCompare(b.key));
}

function formatDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

export default function ReconciliationPage() {
  const { t } = useTranslation('reconciliation');
  const { report, loading, error, refresh } = useReconciliationReport({ forceRefreshOnMount: true });
  const [filter, setFilter] = useState('');
  const [groupBy, setGroupBy] = useState<GroupByMode>('teacher');
  const [showOnlyIssues, setShowOnlyIssues] = useState(false);
  const [expandedCourseId, setExpandedCourseId] = useState<number | null>(null);
  const [lessonsByCourse, setLessonsByCourse] = useState<Record<number, CourseLessonSummary[]>>({});
  const [lessonErrors, setLessonErrors] = useState<Record<number, boolean>>({});

  // Derived, not a separate state var: "loading" is exactly "expanded, not cached, no error yet".
  const isLoadingLessons =
    expandedCourseId !== null &&
    lessonsByCourse[expandedCourseId] === undefined &&
    !lessonErrors[expandedCourseId];

  const filteredRows = useMemo(() => {
    if (!report) return [];
    return report.rows.filter(r => matchesFilter(r, filter) && (!showOnlyIssues || r.status !== 'OK'));
  }, [report, filter, showOnlyIssues]);

  // Backend rollups only reflect the whole dataset, so only hand them to
  // bucketRows when neither the text filter nor "show only issues" narrows
  // the row set - see bucketRows' own doc comment.
  const filterActive = filter.trim() !== '' || showOnlyIssues;
  const buckets = useMemo(() => {
    if (!report) return [];
    const backendRollups = filterActive ? null : (groupBy === 'teacher' ? report.teacherRollups : report.groupRollups);
    return bucketRows(filteredRows, groupBy, backendRollups);
  }, [report, filteredRows, groupBy, filterActive]);

  const pseudoIssueCount = useMemo(() => {
    if (!report) return 0;
    return report.rows.filter(r => r.pseudo && r.status !== 'OK').length;
  }, [report]);

  function loadLessons(courseId: number) {
    setLessonErrors(prev => {
      if (!prev[courseId]) return prev;
      const next = { ...prev };
      delete next[courseId];
      return next;
    });
    getReconciliationLessons(courseId)
      .then(lessons => setLessonsByCourse(prev => ({ ...prev, [courseId]: lessons })))
      .catch(() => setLessonErrors(prev => ({ ...prev, [courseId]: true })));
  }

  function toggleExpand(row: ReconciliationRow) {
    if (expandedCourseId === row.courseId) {
      setExpandedCourseId(null);
      return;
    }
    setExpandedCourseId(row.courseId);
    if (!lessonsByCourse[row.courseId] && !lessonErrors[row.courseId]) {
      loadLessons(row.courseId);
    }
  }

  function handleRowKeyDown(e: KeyboardEvent<HTMLTableRowElement>, row: ReconciliationRow) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      toggleExpand(row);
    }
  }

  return (
    <div className="max-w-[95vw] md:max-w-panel mx-auto px-6 py-8">
      <h1 className="text-3xl font-bold mb-2 text-content">{t('title')}</h1>
      <p className="text-lg text-muted mb-6 leading-relaxed">{t('subtitle')}</p>

      {loading ? (
        <p className="text-muted">{t('loading')}</p>
      ) : error ? (
        <p className="text-feedback-error-text">{t('loadError')}</p>
      ) : report && (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-2">
            <div className="bg-feedback-success-bg border border-feedback-success-border rounded-lg p-4">
              <div className="text-2xl font-bold text-feedback-success-text">{report.summary.okCount}</div>
              <div className="text-sm text-feedback-success-text">{t('summary.ok')}</div>
            </div>
            <div className="bg-feedback-warning-bg border border-feedback-warning-border rounded-lg p-4">
              <div className="text-2xl font-bold text-feedback-warning-text">{report.summary.mismatchCount}</div>
              <div className="text-sm text-feedback-warning-text">{t('summary.mismatch')}</div>
            </div>
            <div className="bg-feedback-error-bg border border-feedback-error-border rounded-lg p-4">
              <div className="text-2xl font-bold text-feedback-error-text">{report.summary.unscheduledCount}</div>
              <div className="text-sm text-feedback-error-text">{t('summary.unscheduled')}</div>
            </div>
            <div className="bg-surface-alt border border-border-light rounded-lg p-4">
              <div className="text-2xl font-bold text-content">{report.summary.totalCourses}</div>
              <div className="text-sm text-muted">{t('summary.total')}</div>
            </div>
          </div>
          {pseudoIssueCount > 0 && (
            <label className="flex items-center gap-2 text-xs text-faint italic mb-4 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={showOnlyIssues}
                onChange={e => setShowOnlyIssues(e.target.checked)}
                className="cursor-pointer"
              />
              <span>{t('summary.pseudoNote', { count: pseudoIssueCount })}</span>
              <span className="not-italic font-semibold text-content">{t('summary.showOnlyIssuesToggle')}</span>
            </label>
          )}

          {/* Controls */}
          <div className="flex flex-wrap items-center gap-4 mb-4">
            <FilterBar
              placeholder={t('filterPlaceholder')}
              value={filter}
              onChange={setFilter}
              totalCount={report.rows.length}
              filteredCount={filteredRows.length}
            />
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted">{t('groupBy.label')}</span>
              {(['teacher', 'group'] as GroupByMode[]).map(mode => (
                <button
                  key={mode}
                  onClick={() => setGroupBy(mode)}
                  className={`px-3 py-1.5 rounded-md text-sm border transition-colors cursor-pointer ${
                    groupBy === mode
                      ? 'bg-accent text-accent-text border-accent'
                      : 'bg-surface-alt text-muted border-border-light hover:text-content'
                  }`}
                >
                  {t(`groupBy.${mode}`)}
                </button>
              ))}
            </div>
            <button
              onClick={refresh}
              disabled={loading}
              className="px-3 py-1.5 rounded-md text-sm border bg-surface-alt text-muted border-border-light
                         hover:text-content transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {t('refresh')}
            </button>
          </div>

          {/* Matrix table */}
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b-2 border-border-light">
                  <th className="text-left p-2 font-semibold text-muted">{t('table.teacher')}</th>
                  <th className="text-left p-2 font-semibold text-muted">{t('table.subject')}</th>
                  <th className="text-left p-2 font-semibold text-muted">{t('table.group')}</th>
                  <th className="text-right p-2 font-semibold text-muted">{t('table.assigned')}</th>
                  <th className="text-right p-2 font-semibold text-muted">{t('table.scheduled')}</th>
                  <th className="text-right p-2 font-semibold text-muted">{t('table.delta')}</th>
                  <th className="text-left p-2 font-semibold text-muted">{t('table.status')}</th>
                </tr>
              </thead>
              <tbody>
                {buckets.length === 0 && (
                  <tr>
                    <td colSpan={7} className="p-4 text-center text-muted">{t('noResults')}</td>
                  </tr>
                )}
                {buckets.map(bucket => (
                  <Fragment key={bucket.key}>
                    <tr className="bg-surface-alt border-b border-border-light">
                      <td colSpan={3} className="p-2 font-semibold text-content">{bucket.key}</td>
                      <td className="p-2 text-right font-mono text-content">{bucket.assignedHours}</td>
                      <td className="p-2 text-right font-mono text-content">{bucket.scheduledHours}</td>
                      <td className={`p-2 text-right font-mono font-semibold ${bucket.issueCount === 0 ? 'text-content' : 'text-feedback-warning-text'}`}>
                        {formatDelta(bucket.delta)}
                        {bucket.issueCount > 0 && (
                          <span className="ml-2 text-xs font-normal">{t('bucketIssues', { count: bucket.issueCount })}</span>
                        )}
                      </td>
                      <td className="p-2" />
                    </tr>
                    {bucket.rows.map(row => (
                      <Fragment key={row.courseId}>
                        <tr
                          onClick={() => toggleExpand(row)}
                          onKeyDown={e => handleRowKeyDown(e, row)}
                          role="button"
                          tabIndex={0}
                          aria-expanded={expandedCourseId === row.courseId}
                          className="border-b border-border-faint hover:bg-surface-hover cursor-pointer focus:outline-none focus:ring-2 focus:ring-accent focus:ring-inset"
                        >
                          <td className="p-2 text-content">{row.teacherName ?? '—'}</td>
                          <td className="p-2 text-content">
                            {row.subject}
                            {row.pseudo && (
                              <span className="ml-2 text-xs italic text-faint" title={t('pseudoHint')}>
                                {t('pseudoBadge')}
                              </span>
                            )}
                          </td>
                          <td className="p-2 text-content">{row.groupName ?? '—'}</td>
                          <td className="p-2 text-right font-mono text-content">{row.assignedHours}</td>
                          <td className="p-2 text-right font-mono text-content">
                            {row.scheduledHours}
                            {row.pinnedHours > 0 && (
                              <span className="ml-1 text-xs text-faint">({row.pinnedHours} {t('table.pinnedSuffix')})</span>
                            )}
                          </td>
                          <td className="p-2 text-right font-mono text-content">{formatDelta(row.delta)}</td>
                          <td className="p-2">
                            {row.pseudo ? (
                              isPseudoCheck(row) ? (
                                <StatusBadge status="MISMATCH" label={t('status.check')} />
                              ) : (
                                <StatusBadge status="idle" label={t('status.expected')} />
                              )
                            ) : (
                              <StatusBadge status={row.status} label={t(`status.${row.status}`)} />
                            )}
                          </td>
                        </tr>
                        {expandedCourseId === row.courseId && (
                          <tr className="bg-surface-alt border-b border-border-light">
                            <td colSpan={7} className="p-3">
                              {isLoadingLessons ? (
                                <span className="text-muted text-sm">{t('drillDown.loading')}</span>
                              ) : lessonErrors[row.courseId] ? (
                                <span className="text-feedback-error-text text-sm">
                                  {t('drillDown.error')}{' '}
                                  <button
                                    onClick={() => loadLessons(row.courseId)}
                                    className="underline cursor-pointer font-semibold"
                                  >
                                    {t('drillDown.retry')}
                                  </button>
                                </span>
                              ) : (lessonsByCourse[row.courseId]?.length ?? 0) === 0 ? (
                                <span className="text-muted text-sm">{t('drillDown.empty')}</span>
                              ) : (
                                <div className="flex flex-wrap gap-2">
                                  {lessonsByCourse[row.courseId].map(lesson => (
                                    <span
                                      key={lesson.lessonId}
                                      className={`px-2 py-1 rounded text-xs font-mono border ${
                                        lesson.pinned
                                          ? 'bg-accent/10 border-accent/30 text-accent-text'
                                          : 'bg-surface border-border-light text-muted'
                                      }`}
                                    >
                                      {lesson.date} · {lesson.roomName ?? t('drillDown.noRoom')}
                                    </span>
                                  ))}
                                </div>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
