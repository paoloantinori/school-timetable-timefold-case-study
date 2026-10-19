import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { solverPhases, constructionPhases, localSearchPhases } from '../data/solverPhases';
import type { SolverPhase } from '../data/solverPhases';

function PhaseCard({ phase }: { phase: SolverPhase }) {
  const { t } = useTranslation('phases');
  const isCH = phase.name.startsWith('CH -');
  const borderColor = isCH ? 'border-emerald-500' : 'border-indigo-500';
  const badgeBg = isCH ? 'bg-emerald-500/10 text-emerald-700' : 'bg-indigo-500/10 text-indigo-700';
  const badgeLabel = isCH ? t('overview.chLabel') : t('overview.lsLabel');

  return (
    <div className={`bg-surface-alt rounded-lg border-l-4 ${borderColor} p-5 mb-4`}>
      {/* Header */}
      <div className="flex items-start gap-3 mb-4">
        <div className={`flex items-center justify-center w-10 h-10 rounded-full
                        ${isCH ? 'bg-emerald-500' : 'bg-indigo-500'}
                        text-white font-bold text-lg shrink-0`}>
          {phase.number}
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-3 flex-wrap">
            <h3 className="text-xl font-semibold text-content m-0">
              {phase.name}
            </h3>
            <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${badgeBg}`}>
              {badgeLabel}
            </span>
            <span className="text-xs font-mono text-faint ml-auto">
              {phase.duration}
            </span>
          </div>
          <p className="text-sm text-accent font-medium mt-1 mb-0">
            {phase.moveStrategy}
          </p>
        </div>
      </div>

      {/* Description */}
      <p className="text-sm text-muted leading-relaxed mb-4">
        {phase.metaheuristic && (
          <>{t('labels.metaheuristic')}: {phase.metaheuristic}</>
        )}
      </p>

      {/* Details grid */}
      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-3">
        {/* Move palette */}
        {phase.moveWeights && phase.moveWeights.length > 0 && (
          <div className="bg-black/5 rounded-lg p-3">
            <div className="text-xs font-semibold text-faint uppercase tracking-wide mb-2">
              {t('labels.moves')}
            </div>
            <div className="flex flex-col gap-1.5">
              {phase.moveWeights.map((move, i) => (
                <div key={i} className="flex items-center gap-2 text-sm">
                  <span className="font-mono text-xs bg-black/5 px-1.5 py-0.5 rounded">
                    {move.move}
                  </span>
                  {move.weight && (
                    <span className="text-xs text-faint">
                      {move.weight}× {t('labels.weight')}
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Termination */}
        {phase.termination && (phase.termination.secondsSpentLimit || phase.termination.unimprovedSecondsSpentLimit) && (
          <div className="bg-black/5 rounded-lg p-3">
            <div className="text-xs font-semibold text-faint uppercase tracking-wide mb-2">
              {t('labels.termination')}
            </div>
            <div className="flex flex-col gap-1.5 text-sm">
              {phase.termination.unimprovedSecondsSpentLimit && (
                <div className="text-muted">
                  {t('labels.unimproved', { minutes: Math.round(phase.termination.unimprovedSecondsSpentLimit / 60) })}
                </div>
              )}
              {phase.termination.secondsSpentLimit && (
                <div className="text-muted">
                  {t('labels.hardCap', { hours: Math.round(phase.termination.secondsSpentLimit / 3600) })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default function SolverPhasesPage() {
  const { t } = useTranslation('phases');

  return (
    <div className="max-w-[95vw] md:max-w-panel mx-auto px-6 py-8">
      <h1 className="text-3xl font-bold mb-2 text-content">
        {t('title')}
      </h1>
      <p className="text-lg text-muted mb-10 leading-relaxed">
        {t('subtitle')}
      </p>

      {/* The staged cycle the Solve button actually runs (UX audit TASK-512): the 5-phase
          pipeline below is the content of ONE attempt; this section gives the user the
          mental model of what starts when they press Solve. */}
      <section className="mb-12">
        <div className="bg-feedback-warning-bg border border-feedback-warning-border rounded-lg p-5">
          <h2 className="text-xl font-semibold mb-3 text-feedback-warning-text">
            {t('stagedCycleDoc.title')}
          </h2>
          <p className="text-sm text-content leading-relaxed mb-2 font-mono text-xs">
            {t('stagedCycleDoc.stages')}
          </p>
          <p className="text-sm text-content leading-relaxed mb-2">
            {t('stagedCycleDoc.multiStart')}
          </p>
          <p className="text-sm text-muted leading-relaxed mb-2">
            {t('stagedCycleDoc.caveat')}
          </p>
          <p className="text-sm text-muted leading-relaxed m-0 italic">
            {t('stagedCycleDoc.phasesBelow')}
          </p>
        </div>
      </section>

      {/* Visual Pipeline Overview */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border text-content">
          {t('overview.title')}
        </h2>

        {/* Pipeline flow diagram */}
        <div className="bg-surface-alt rounded-lg p-6 border border-border-light mb-4">
          {/* Phase type legend */}
          <div className="flex gap-6 mb-6 flex-wrap">
            <div className="flex items-center gap-2 text-sm">
              <div className="w-3 h-3 rounded-full bg-emerald-500" />
              <span><strong>{t('overview.chLabel')}</strong> — {t('overview.chDescription')}</span>
            </div>
            <div className="flex items-center gap-2 text-sm">
              <div className="w-3 h-3 rounded-full bg-indigo-500" />
              <span><strong>{t('overview.lsLabel')}</strong> — {t('overview.lsDescription')}</span>
            </div>
          </div>

          {/* Flow */}
          <div className="flex items-center gap-2 flex-wrap">
            {solverPhases.map((phase: SolverPhase, i: number) => (
              <div key={phase.id} className="flex items-center gap-2">
                <div className={`flex items-center gap-2 px-4 py-3 rounded-lg border
                  ${phase.name.startsWith('CH -')
                    ? 'bg-emerald-500/10 border-emerald-500/30'
                    : 'bg-indigo-500/10 border-indigo-500/30'
                  }`}>
                  <span className={`inline-flex items-center justify-center w-7 h-7 rounded-full
                    ${phase.name.startsWith('CH -') ? 'bg-emerald-500' : 'bg-indigo-500'}
                    text-white text-sm font-bold`}>
                    {phase.number}
                  </span>
                  <div>
                    <div className="text-sm font-semibold text-content">{phase.name}</div>
                    <div className="text-xs text-faint">{phase.duration}</div>
                  </div>
                </div>
                {i < solverPhases.length - 1 && (
                  <span className="text-muted text-xl">→</span>
                )}
              </div>
            ))}
          </div>

          {/* Notes */}
          <div className="mt-4 pt-4 border-t border-border-light flex flex-col gap-1">
            <p className="text-xs text-faint m-0">{t('overview.preAssignment')}</p>
            <p className="text-xs text-faint m-0">{t('overview.pinsNote')}</p>
            <p className="text-xs text-faint m-0">{t('overview.globalStop')}</p>
          </div>
        </div>
      </section>

      {/* Construction Heuristic Phases */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-2 pb-2 border-b-2 border-emerald-500 text-emerald-600">
          {t('sections.construction')}
        </h2>
        <p className="text-sm text-muted mb-5">
          {t('sections.constructionDesc')}
        </p>
        {constructionPhases.map((phase: SolverPhase) => (
          <PhaseCard key={phase.id} phase={phase} />
        ))}
      </section>

      {/* Local Search Phases */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-2 pb-2 border-b-2 border-indigo-500 text-indigo-600">
          {t('sections.localSearch')}
        </h2>
        <p className="text-sm text-muted mb-5">
          {t('sections.localSearchDesc')}
        </p>
        {localSearchPhases.map((phase: SolverPhase) => (
          <PhaseCard key={phase.id} phase={phase} />
        ))}
      </section>

      {/* Footer */}
      <div className="pt-6 border-t border-border-light text-center">
        <p className="text-sm text-faint font-mono mb-4">
          {t('footer.configFile')}
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
