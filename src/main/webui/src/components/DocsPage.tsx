import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { ConstraintCard } from './docs/ConstraintCard';
import { hardConstraints, mediumConstraints, softConstraints } from '../data/constraints';

/** A numbered card list, e.g. "1. Do X" → "2. Do Y" → ... Shared by the
 * 2026/27 Process and Pre-Assignment Pipeline sections below. */
function NumberedStepList({ count, tKeyPrefix }: { count: number; tKeyPrefix: string }) {
  const { t } = useTranslation('docs');
  return (
    <div className="flex flex-col gap-3 mb-6">
      {Array.from({ length: count }, (_, i) => i + 1).map(step => (
        <div key={step}
             className="flex gap-4 items-start px-5 py-4
                        bg-surface-alt rounded-lg border border-border-light">
          <div className="flex items-center justify-center w-8 h-8 rounded-full
                          bg-accent text-accent-text font-bold text-sm shrink-0">
            {step}
          </div>
          <div className="flex-1">
            <div className="font-semibold mb-1 text-content">
              {t(`${tKeyPrefix}${step}.title`)}
            </div>
            <div className="text-muted text-sm leading-normal">
              {t(`${tKeyPrefix}${step}.description`)}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/** A bordered title+description box. Shared by the 2026/27 Process section's
 * four narrative blocks below. */
function InfoBox({ titleKey, descriptionKey, borderClass = 'border-accent' }: {
  titleKey: string;
  descriptionKey: string;
  borderClass?: string;
}) {
  const { t } = useTranslation('docs');
  return (
    <div className={`bg-surface-alt px-6 py-5 rounded-lg mb-6 border-l-4 ${borderClass}`}>
      <h3 className="font-semibold text-content mb-2">{t(titleKey)}</h3>
      <p className="m-0 text-muted leading-relaxed">{t(descriptionKey)}</p>
    </div>
  );
}

export default function DocsPage() {
  const { t } = useTranslation('docs');

  return (
    <div className="max-w-[95vw] md:max-w-panel mx-auto px-6 py-8">
      <h1 className="text-3xl font-bold mb-2 text-content">
        {t('title')}
      </h1>
      <p className="text-lg text-muted mb-10 leading-relaxed">
        {t('subtitle')}
      </p>

      {/* Link to Complexity Page */}
      <div className="mb-8 p-4 bg-surface-alt rounded-lg border border-border-light">
        <Link to="/complexity"
              className="flex items-center gap-3 text-accent hover:text-accent/80
                         transition-colors no-underline">
          <span className="text-2xl">🔢</span>
          <div>
            <div className="font-semibold">{t('complexityLink')}</div>
            <div className="text-sm text-muted">
              {t('complexityLinkDesc')}
            </div>
          </div>
        </Link>
      </div>

      {/* Introduction */}
      <div className="bg-surface-alt px-6 py-5 rounded-lg mb-10
                      border-l-4 border-accent">
        <p className="m-0 text-muted leading-relaxed">
          {t('intro')}
        </p>
      </div>

      {/* Constraint Type Legend */}
      <div className="flex gap-6 flex-wrap mb-8 p-4 bg-surface-alt rounded-lg">
        <div className="flex items-center gap-2 text-sm">
          <div className="w-3 h-3 rounded-full bg-constraint-hard" />
          <span><strong>{t('hardLabel')}</strong> - {t('hardDesc')}</span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <div className="w-3 h-3 rounded-full bg-constraint-medium" />
          <span><strong>{t('mediumLabel')}</strong> - {t('mediumDesc')}</span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <div className="w-3 h-3 rounded-full bg-constraint-soft" />
          <span><strong>{t('softLabel')}</strong> - {t('softDesc')}</span>
        </div>
      </div>

      {/* The 2026/27 Process */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-2 pb-2 border-b-2 border-border
                       text-content">
          {t('process.title')}
        </h2>
        <p className="text-muted mb-6 leading-relaxed">
          {t('process.intro')}
        </p>

        {/* (a) School workflow */}
        <InfoBox titleKey="process.schoolTitle" descriptionKey="process.schoolDescription" />

        {/* (b) How the app mirrors it */}
        <h3 className="text-lg font-semibold mb-3 text-content">{t('process.mirrorTitle')}</h3>
        <NumberedStepList count={6} tKeyPrefix="process.mirrorStep" />

        {/* (TASK-665) The former "Golden calibration" box was removed: the golden timetable is
            an internal calibration artifact being retired (TASK-441), never something the
            school-facing docs should advertise. */}

        {/* (d) Manual placement */}
        <InfoBox titleKey="process.manualTitle" descriptionKey="process.manualDescription" />

        {/* Shared pinned/pseudo-teacher exemption note */}
        <InfoBox titleKey="process.exemptTitle" descriptionKey="process.exemptDescription"
                 borderClass="border-constraint-medium" />
      </section>

      {/* Pre-Assignment Pipeline */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-border
                       text-content">
          {t('pipeline.title')}
        </h2>
        <p className="text-sm text-faint mb-4 leading-relaxed">
          {t('pipeline.legacyNote')}
        </p>
        <NumberedStepList count={5} tKeyPrefix="pipeline.step" />
      </section>

      {/* Hard Constraints */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-constraint-hard
                       text-constraint-hard">
          {t('hardConstraints')}
        </h2>
        <p className="text-sm text-faint mb-4">
          {t('constraintCount', { count: hardConstraints.length })}
        </p>
        {hardConstraints.map(c => (
          <ConstraintCard key={c.id} constraint={c} />
        ))}
      </section>

      {/* Medium Constraints */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-constraint-medium
                       text-constraint-medium">
          {t('mediumConstraints')}
        </h2>
        <p className="text-sm text-faint mb-4">
          {t('constraintCount', { count: mediumConstraints.length })}
        </p>
        {mediumConstraints.map(c => (
          <ConstraintCard key={c.id} constraint={c} />
        ))}
      </section>

      {/* Soft Constraints */}
      <section className="mb-12">
        <h2 className="text-2xl font-semibold mb-5 pb-2 border-b-2 border-constraint-soft
                       text-constraint-soft">
          {t('softConstraints')}
        </h2>
        <p className="text-sm text-faint mb-4">
          {t('constraintCount', { count: softConstraints.length })}
        </p>
        {softConstraints.map(c => (
          <ConstraintCard key={c.id} constraint={c} />
        ))}
      </section>
    </div>
  );
}
