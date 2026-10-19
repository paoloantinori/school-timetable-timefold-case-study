import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { getSchedule } from '../api/scheduleApi';
import type { SchoolSchedule } from '../types/domain';
import { hardConstraints, mediumConstraints, softConstraints } from '../data/constraints';

// Format large numbers with locale-aware separators
function formatNumber(num: number, locale: string = 'en'): string {
  if (num >= 1e12) {
    return `${(num / 1e12).toFixed(1)}T`;
  } else if (num >= 1e9) {
    return `${(num / 1e9).toFixed(1)}B`;
  } else if (num >= 1e6) {
    return `${(num / 1e6).toFixed(1)}M`;
  } else if (num >= 1e3) {
    return `${(num / 1e3).toFixed(1)}K`;
  }
  return num.toLocaleString(locale);
}

// Calculate search space using logarithms to avoid overflow
function calculateSearchSpaceExponent(lessons: number, timeslots: number, rooms: number): number {
  return lessons * Math.log10(timeslots * rooms);
}

// Format the exponent as a display string
function formatSearchSpace(exp: number): string {
  if (!isFinite(exp)) return '10^∞';
  const mantissa = Math.pow(10, exp - Math.floor(exp)).toFixed(2);
  return `~${mantissa} × 10^${Math.floor(exp).toLocaleString()}`;
}

export default function ComplexityPage() {
  const { t, i18n } = useTranslation('complexity');
  const [schedule, setSchedule] = useState<SchoolSchedule | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getSchedule()
      .then(setSchedule)
      .finally(() => setLoading(false));
  }, []);

  // Calculate metrics
  const metrics = useMemo(() => {
    if (!schedule) return null;

    const lessons = schedule.lessons.length;
    const timeslots = schedule.timeslots.length;
    const rooms = schedule.rooms.length;
    const studentGroups = schedule.studentGroups.length;

    const teacherIds = new Set<number>();
    schedule.lessons.forEach(l => l.course?.teacher && teacherIds.add(l.course.teacher.id));
    const teachers = teacherIds.size;

    const searchSpaceExponent = calculateSearchSpaceExponent(lessons, timeslots, rooms);

    return {
      lessons,
      timeslots,
      rooms,
      teachers,
      studentGroups,
      constraints: hardConstraints.length + mediumConstraints.length + softConstraints.length,
      searchSpaceExponent
    };
  }, [schedule]);

  const locale = i18n.language === 'it' ? 'it-IT' : 'en-US';

  return (
    <div className="max-w-[95vw] md:max-w-panel mx-auto px-6 py-8">
      <h1 className="text-3xl font-bold mb-2 text-content">
        {t('title')}
      </h1>
      <p className="text-lg text-muted mb-10 leading-relaxed">
        {t('subtitle')}
      </p>

      {/* Introduction */}
      <div className="bg-surface-alt px-6 py-5 rounded-lg mb-10 border-l-4 border-accent">
        <p className="m-0 text-muted leading-relaxed">
          {t('intro')}
        </p>
      </div>

      {loading ? (
        <p className="text-muted">{t('loading', { defaultValue: 'Loading metrics...' })}</p>
      ) : metrics && (
        <>
          {/* Metrics Grid */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('metrics.title')}
            </h2>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{formatNumber(metrics.lessons, locale)}</div>
                <div className="text-sm text-muted">{t('metrics.lessons')}</div>
              </div>
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{formatNumber(metrics.timeslots, locale)}</div>
                <div className="text-sm text-muted">{t('metrics.timeslots')}</div>
              </div>
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{metrics.rooms}</div>
                <div className="text-sm text-muted">{t('metrics.rooms')}</div>
              </div>
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{metrics.teachers}</div>
                <div className="text-sm text-muted">{t('metrics.teachers')}</div>
              </div>
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{metrics.studentGroups}</div>
                <div className="text-sm text-muted">{t('metrics.studentGroups')}</div>
              </div>
              <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                <div className="text-2xl font-bold text-content">{metrics.constraints}</div>
                <div className="text-sm text-muted">{t('metrics.constraints')}</div>
              </div>
            </div>
          </section>

          {/* Search Space */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('searchSpace.title')}
            </h2>
            <p className="text-muted mb-4 leading-relaxed">
              {t('searchSpace.explanation')}
            </p>

            <div className="bg-surface-alt rounded-lg p-5 border border-border-light mb-4">
              <div className="flex flex-wrap items-center gap-2 font-mono text-sm">
                <span className="bg-black/5 px-2 py-1 rounded">{metrics.lessons.toLocaleString(locale)}</span>
                <span className="text-muted">×</span>
                <span className="bg-black/5 px-2 py-1 rounded">{metrics.timeslots.toLocaleString(locale)}</span>
                <span className="text-muted">×</span>
                <span className="bg-black/5 px-2 py-1 rounded">{metrics.rooms}</span>
                <span className="text-muted">=</span>
                <span className="bg-accent/10 text-accent px-2 py-1 rounded font-semibold">
                  {formatSearchSpace(metrics.searchSpaceExponent)}
                </span>
              </div>
            </div>

            <p className="text-sm text-faint italic">
              {t('searchSpace.note')}
            </p>
          </section>

          {/* Comparisons */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('comparisons.title')}
            </h2>
            <div className="grid md:grid-cols-2 gap-4">
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <div className="flex items-center gap-3 mb-2">
                  <span className="text-xl">⚛️</span>
                  <h3 className="font-semibold text-content m-0">{t('comparisons.atoms.title')}</h3>
                </div>
                <div className="text-sm font-mono text-muted mb-2">{t('comparisons.atoms.value')}</div>
                <p className="text-sm text-muted m-0">{t('comparisons.atoms.comparison')}</p>
              </div>
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <div className="flex items-center gap-3 mb-2">
                  <span className="text-xl">♟️</span>
                  <h3 className="font-semibold text-content m-0">{t('comparisons.chess.title')}</h3>
                </div>
                <div className="text-sm font-mono text-muted mb-2">{t('comparisons.chess.value')}</div>
                <p className="text-sm text-muted m-0">{t('comparisons.chess.comparison')}</p>
              </div>
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <div className="flex items-center gap-3 mb-2">
                  <span className="text-xl">🧩</span>
                  <h3 className="font-semibold text-content m-0">{t('comparisons.sudoku.title')}</h3>
                </div>
                <div className="text-sm font-mono text-muted mb-2">{t('comparisons.sudoku.value')}</div>
                <p className="text-sm text-muted m-0">{t('comparisons.sudoku.comparison')}</p>
              </div>
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <div className="flex items-center gap-3 mb-2">
                  <span className="text-xl">⏱️</span>
                  <h3 className="font-semibold text-content m-0">{t('comparisons.bruteForce.title')}</h3>
                </div>
                <div className="text-sm font-mono text-muted mb-2">{t('comparisons.bruteForce.value')}</div>
                <p className="text-sm text-muted m-0">{t('comparisons.bruteForce.comparison')}</p>
              </div>
            </div>
          </section>

          {/* Why Solver */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('whySolver.title')}
            </h2>
            <p className="text-muted mb-4 leading-relaxed">
              {t('whySolver.explanation')}
            </p>
            <div className="flex flex-col gap-4">
              <div className="flex gap-4 items-start px-5 py-4 bg-surface-alt rounded-lg border border-border-light">
                <div className="flex items-center justify-center w-8 h-8 rounded-full bg-accent text-accent-text font-bold text-sm shrink-0">
                  1
                </div>
                <div className="flex-1">
                  <div className="font-semibold mb-1 text-content">{t('whySolver.techniques.constructionHeuristic.name')}</div>
                  <div className="text-muted text-sm leading-normal">{t('whySolver.techniques.constructionHeuristic.description')}</div>
                </div>
              </div>
              <div className="flex gap-4 items-start px-5 py-4 bg-surface-alt rounded-lg border border-border-light">
                <div className="flex items-center justify-center w-8 h-8 rounded-full bg-accent text-accent-text font-bold text-sm shrink-0">
                  2
                </div>
                <div className="flex-1">
                  <div className="font-semibold mb-1 text-content">{t('whySolver.techniques.localSearch.name')}</div>
                  <div className="text-muted text-sm leading-normal">{t('whySolver.techniques.localSearch.description')}</div>
                </div>
              </div>
              <div className="flex gap-4 items-start px-5 py-4 bg-surface-alt rounded-lg border border-border-light">
                <div className="flex items-center justify-center w-8 h-8 rounded-full bg-accent text-accent-text font-bold text-sm shrink-0">
                  3
                </div>
                <div className="flex-1">
                  <div className="font-semibold mb-1 text-content">{t('whySolver.techniques.constraintPropagation.name')}</div>
                  <div className="text-muted text-sm leading-normal">{t('whySolver.techniques.constraintPropagation.description')}</div>
                </div>
              </div>
            </div>
            <div className="mt-4 p-4 bg-surface-alt rounded-lg border border-border-light text-center">
              <span className="font-medium text-content">{t('whySolver.result')}</span>
            </div>
          </section>

          {/* Constraints Summary */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('constraints.title')}
            </h2>
            <p className="text-muted mb-4 leading-relaxed">
              {t('constraints.explanation')}
            </p>
            <div className="flex flex-wrap gap-4">
              <div className="flex items-center gap-2 px-4 py-2 bg-constraint-hard/10 rounded-lg border border-constraint-hard/20">
                <div className="w-3 h-3 rounded-full bg-constraint-hard" />
                <span className="font-semibold text-content">{hardConstraints.length}</span>
                <span className="text-sm text-muted">{t('constraints.hardConstraints')}</span>
                <span className="text-xs text-faint">({t('constraints.mustSatisfy')})</span>
              </div>
              <div className="flex items-center gap-2 px-4 py-2 bg-constraint-medium/10 rounded-lg border border-constraint-medium/20">
                <div className="w-3 h-3 rounded-full bg-constraint-medium" />
                <span className="font-semibold text-content">{mediumConstraints.length}</span>
                <span className="text-sm text-muted">{t('constraints.mediumConstraints')}</span>
                <span className="text-xs text-faint">({t('constraints.shouldAvoid')})</span>
              </div>
              <div className="flex items-center gap-2 px-4 py-2 bg-constraint-soft/10 rounded-lg border border-constraint-soft/20">
                <div className="w-3 h-3 rounded-full bg-constraint-soft" />
                <span className="font-semibold text-content">{softConstraints.length}</span>
                <span className="text-sm text-muted">{t('constraints.softConstraints')}</span>
                <span className="text-xs text-faint">({t('constraints.niceToHave')})</span>
              </div>
            </div>
          </section>
        </>
      )}

      {/* Footer */}
      <div className="pt-6 border-t border-border-light text-center">
        <p className="text-muted mb-4 max-w-xl mx-auto">
          {t('footer.appreciation')}
        </p>
        <Link
          to="/docs"
          className="inline-flex items-center gap-2 text-accent hover:text-accent/80 transition-colors no-underline"
        >
          {t('footer.learnMore')} →
        </Link>
      </div>
    </div>
  );
}
