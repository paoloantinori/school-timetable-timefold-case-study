/**
 * Benchmark Results Page
 * Displays solver configuration benchmark results over time
 * Allows comparison of different Timefold local search algorithms
 */

import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { benchmarkData } from '../data/benchmarkData';
import type { BenchmarkResult } from '../types/benchmark';
import { getNotesForResult, getBestPerFamily, FAMILY_COLORS, type AlgorithmFamily } from '../api/benchmarkApi';
import BenchmarkTable from './benchmark/BenchmarkTable';

/** Compact per-family best-score summary */
function FamilyComparisonTable({ results }: { results: BenchmarkResult[] }) {
  const { t } = useTranslation('benchmark');

  const familyBests = useMemo(() => getBestPerFamily(results), [results]);

  const families = Object.keys(familyBests) as AlgorithmFamily[];
  if (families.length === 0) return null;

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b-2 border-border-light">
            <th className="text-left p-2 font-semibold text-muted">{t('table.family')}</th>
            <th className="text-left p-2 font-semibold text-muted">{t('table.configName')}</th>
            <th className="text-left p-2 font-semibold text-muted">{t('table.score')}</th>
          </tr>
        </thead>
        <tbody>
          {families.map(family => {
            const best = familyBests[family];
            if (!best) return null;
            return (
              <tr key={family} className="border-b border-border-faint">
                <td className="p-2">
                  <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium bg-surface-alt border-l-2 ${FAMILY_COLORS[family]}`}>
                    {t(`family.${family}`)}
                  </span>
                </td>
                <td className="p-2 text-content font-medium">{best.configName}</td>
                <td className="p-2">
                  <div className="flex gap-2 font-mono text-xs">
                    <span className="px-1.5 py-0.5 rounded bg-constraint-hard/10 text-constraint-hard">
                      {t('score.hard')}: {best.score.hard}
                    </span>
                    <span className="px-1.5 py-0.5 rounded bg-constraint-medium/10 text-constraint-medium">
                      {t('score.medium')}: {best.score.medium.toLocaleString()}
                    </span>
                    <span className="px-1.5 py-0.5 rounded bg-constraint-soft/10 text-constraint-soft">
                      {t('score.soft')}: {best.score.soft.toLocaleString()}
                    </span>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default function BenchmarkResultsPage() {
  const { t } = useTranslation(['benchmark', 'common']);

  // Merge localStorage notes into static data
  const results = useMemo(() =>
    benchmarkData.map(r => ({
      ...r,
      notes: getNotesForResult(r.id),
    })),
    []
  );

  // Calculate summary metrics
  const summary = useMemo(() => {
    if (results.length === 0) return null;

    const totalTime = results.reduce((sum, r) => sum + r.timeMillisSpent, 0);
    const averageTime = totalTime / results.length;

    // Find best scores (lowest hard/medium penalties, highest soft reward)
    const bestHard = Math.min(...results.map(r => r.score.hard));
    const bestMedium = Math.min(...results.map(r => r.score.medium));
    const bestSoft = Math.max(...results.map(r => r.score.soft));

    return {
      totalConfigs: results.length,
      averageTime,
      bestScores: {
        hard: bestHard,
        medium: bestMedium,
        soft: bestSoft,
      },
      lastRun: results[0]?.dateRun || '-',
    };
  }, [results]);

  const bestScores = summary?.bestScores || {
    hard: null,
    medium: null,
    soft: null,
  };

  const formatTime = (millis: number) => {
    const seconds = Math.floor(millis / 1000);
    const minutes = Math.floor(seconds / 60);
    if (minutes > 0) {
      return `${minutes}m ${seconds % 60}s`;
    }
    return `${seconds}s`;
  };

  const findings = t('analysis.findings', { returnObjects: true }) as string[];
  const recommendations = t('analysis.recommendations', { returnObjects: true }) as string[];

  return (
    <div className="max-w-[95vw] md:max-w-panel mx-auto px-6 py-8">
      <h1 className="text-3xl font-bold mb-2 text-content">
        {t('title')}
      </h1>
      <p className="text-lg text-muted mb-4 leading-relaxed">
        {t('subtitle')}
      </p>
      <p className="mb-10 px-4 py-2 rounded border border-border-faint bg-surface-hover text-sm text-muted">
        {t('common:benchmarksStale')}
      </p>

      {/* Introduction */}
      <div className="bg-surface-alt px-6 py-5 rounded-lg mb-10 border-l-4 border-accent">
        <p className="m-0 text-muted leading-relaxed">
          {t('intro.text')}
        </p>
      </div>

      {results.length === 0 ? (
        <div className="bg-surface-alt px-6 py-8 rounded-lg text-center">
          <p className="text-muted mb-4">{t('noResults')}</p>
          <code className="bg-black/5 px-3 py-2 rounded text-sm block max-w-md mx-auto">
            ./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTest.xml
          </code>
        </div>
      ) : (
        <>
          {/* Summary Cards */}
          {summary && (
            <section className="mb-10">
              <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
                {t('summary.title')}
              </h2>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                  <div className="text-2xl font-bold text-content">{summary.totalConfigs}</div>
                  <div className="text-sm text-muted">{t('summary.totalConfigs')}</div>
                </div>
                <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                  <div className="font-mono text-sm space-y-0.5">
                    <div className="text-constraint-hard font-bold">{summary.bestScores.hard} hard</div>
                    <div className="text-constraint-medium font-bold">{summary.bestScores.medium.toLocaleString()} medium</div>
                    <div className="text-constraint-soft font-bold">{summary.bestScores.soft.toLocaleString()} soft</div>
                  </div>
                  <div className="text-sm text-muted mt-1">{t('summary.bestScore')}</div>
                </div>
                <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                  <div className="text-2xl font-bold text-content">{formatTime(summary.averageTime)}</div>
                  <div className="text-sm text-muted">{t('summary.averageTime')}</div>
                </div>
                <div className="bg-surface-alt rounded-lg p-4 border border-border-light">
                  <div className="text-2xl font-bold text-content">{summary.lastRun}</div>
                  <div className="text-sm text-muted">{t('summary.lastRun')}</div>
                </div>
              </div>
            </section>
          )}

          {/* Results Table */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('results.title')}
            </h2>
            <BenchmarkTable results={results} bestScores={bestScores} />
          </section>

          {/* Algorithm Family Comparison */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('analysis.familyComparison')}
            </h2>
            <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
              <FamilyComparisonTable results={results} />
            </div>
          </section>

          {/* Analysis & Recommendations */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('analysis.title')}
            </h2>
            <div className="grid md:grid-cols-2 gap-6">
              {/* Key Findings */}
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <h3 className="text-lg font-semibold mb-4 text-content">{t('analysis.findingsTitle')}</h3>
                <ul className="space-y-3">
                  {Array.isArray(findings) && findings.map((finding, i) => (
                    <li key={i} className="flex gap-2 text-sm text-muted leading-relaxed">
                      <span className="text-accent font-bold shrink-0">{i + 1}.</span>
                      <span>{finding}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Recommendations */}
              <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
                <h3 className="text-lg font-semibold mb-4 text-content">{t('analysis.recommendationsTitle')}</h3>
                <ol className="space-y-3">
                  {Array.isArray(recommendations) && recommendations.map((rec, i) => (
                    <li key={i} className="flex gap-2 text-sm text-muted leading-relaxed">
                      <span className="bg-accent/10 text-accent font-bold px-1.5 py-0.5 rounded shrink-0">{i + 1}</span>
                      <span>{rec}</span>
                    </li>
                  ))}
                </ol>
              </div>
            </div>
          </section>

          {/* How to Run Benchmarks */}
          <section className="mb-10">
            <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
              {t('howToRun.title')}
            </h2>
            <div className="bg-surface-alt rounded-lg p-5 border border-border-light">
              <p className="text-muted mb-4 leading-relaxed">
                {t('howToRun.description')}
              </p>
              <div className="space-y-3 font-mono text-sm">
                <div className="flex gap-2 items-start">
                  <span className="bg-black/5 px-2 py-1 rounded text-muted text-xs whitespace-nowrap">{t('howToRun.quick')}</span>
                  <code className="bg-black/5 px-2 py-1 rounded flex-1">
                    ./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfigTest.xml
                  </code>
                </div>
                <div className="flex gap-2 items-start">
                  <span className="bg-black/5 px-2 py-1 rounded text-muted text-xs whitespace-nowrap">{t('howToRun.full')}</span>
                  <code className="bg-black/5 px-2 py-1 rounded flex-1">
                    ./scripts/benchmark/run-benchmarker.sh src/main/resources/solverBenchmarkConfig.xml
                  </code>
                </div>
              </div>
            </div>
          </section>
        </>
      )}

      {/* Footer */}
      <div className="pt-6 border-t border-border-light text-center">
        <p className="text-muted text-sm">
          Benchmark results are stored in <code className="bg-black/5 px-1.5 py-0.5 rounded">local/benchmarkReport/</code>
        </p>
      </div>
    </div>
  );
}
