import { useTranslation } from 'react-i18next';
import type { ConstraintInfo } from '../../data/constraints';

interface Props {
  constraint: ConstraintInfo;
}

const typeConfig = {
  hard: {
    bgClass: 'bg-constraint-hard/[0.08]',
    borderClass: 'border-l-constraint-hard',
    badgeKey: 'hardBadge',
    badgeClass: 'bg-constraint-hard',
  },
  medium: {
    bgClass: 'bg-constraint-medium/[0.08]',
    borderClass: 'border-l-constraint-medium',
    badgeKey: 'mediumBadge',
    badgeClass: 'bg-constraint-medium',
  },
  soft: {
    bgClass: 'bg-constraint-soft/[0.08]',
    borderClass: 'border-l-constraint-soft',
    badgeKey: 'softBadge',
    badgeClass: 'bg-constraint-soft',
  },
};

export function ConstraintCard({ constraint }: Props) {
  const { t } = useTranslation('docs');
  const config = typeConfig[constraint.type];

  const example = constraint.exampleKey ? t(constraint.exampleKey) : null;
  const hasExample = example && !example.startsWith('['); // i18next returns [key] if not found
  const origin = constraint.originKey ? t(constraint.originKey) : null;
  const hasOrigin = origin && !origin.startsWith('[');

  return (
    <div className={`${config.bgClass} ${config.borderClass} border-l-4 px-5 py-4
                    rounded-r-lg mb-4 transition-all duration-200
                    hover:scale-[1.01] hover:shadow-soft`}>
      <header className="flex items-center gap-3 flex-wrap mb-2">
        <code className="text-sm font-semibold font-mono
                        bg-black/[0.08] px-2 py-0.5 rounded">
          {constraint.id}
        </code>
        <h4 className="m-0 text-base font-semibold text-content">
          {t(constraint.nameKey)}
        </h4>
        <span className={`${config.badgeClass} text-white px-2 py-0.5 rounded
                         text-[0.7rem] font-semibold uppercase tracking-wide`}>
          {t(config.badgeKey)}
        </span>
      </header>
      <p className="my-2 text-muted leading-relaxed">
        {t(constraint.descriptionKey)}
      </p>
      {hasExample && (
        <div className="bg-black/[0.05] px-4 py-3 rounded-md
                        text-sm mt-3 border-l-[3px] border-l-black/10">
          <span className="font-semibold text-content mr-2">
            {t('exampleLabel')}:
          </span>
          {example}
        </div>
      )}
      {hasOrigin && (
        <div className="bg-black/[0.05] px-4 py-3 rounded-md
                        text-sm mt-3 border-l-[3px] border-l-black/10">
          <span className="font-semibold text-content mr-2">
            {t('originLabel')}:
          </span>
          {origin}
        </div>
      )}
    </div>
  );
}
