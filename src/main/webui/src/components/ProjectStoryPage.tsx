import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import MarkdownDoc from './MarkdownDoc';
import { toggleButton } from './ui/variants';
// I due documenti vivono nella root del repo: sono la loro unica copia (CLAUDE.md,
// "puntatori, non copie") e Vite li inlinea nel bundle a build time con ?raw. La
// risalita fuori dalla root di Vite e' dichiarata in vite.config.ts (server.fs.allow).
import storiaMd from '../../../../../STORIA.md?raw';
import riapproccioMd from '../../../../../RIAPPROCCIO.md?raw';

const DOCS = [
  { file: 'STORIA.md', source: storiaMd },
  { file: 'RIAPPROCCIO.md', source: riapproccioMd },
] as const;

const slugOf = (file: string) => file.replace(/\.md$/i, '').toLowerCase();

/**
 * Le due narrazioni del progetto (storia cronologica e ri-approccio).
 *
 * Il corpo dei documenti resta in italiano e NON passa da i18n: sono testi narrativi
 * unici, non microcopy riusabile, e spezzettarli in chiavi JSON con una traduzione
 * inglese parallela costerebbe piu' della loro utilita'. Tradotta la sola chrome,
 * eccezione dichiarata sul modello del link /reports in App.tsx.
 */
export default function ProjectStoryPage() {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { doc } = useParams<{ doc?: string }>();

  const active = DOCS.find(d => slugOf(d.file) === doc) ?? DOCS[0];

  // Identita' stabile: MarkdownDoc memoizza il rendering su questa callback.
  const resolveDocLink = useCallback(
    (target: string) => {
      const found = DOCS.find(d => target.endsWith(d.file));
      return found ? () => navigate(`/storia/${slugOf(found.file)}`) : null;
    },
    [navigate]
  );

  // Uno slug sconosciuto mostra il primo documento: riporta anche l'indirizzo su quello
  // canonico, altrimenti la scheda attiva e la barra degli indirizzi si contraddicono.
  if (doc && doc !== slugOf(active.file)) {
    return <Navigate to={`/storia/${slugOf(active.file)}`} replace />;
  }

  return (
    <div className="p-2 md:p-4">
      <div className="flex flex-wrap gap-2 mb-6 border-b border-border-light pb-3">
        {DOCS.map(d => (
          <button
            key={d.file}
            type="button"
            onClick={() => navigate(`/storia/${slugOf(d.file)}`)}
            aria-current={d.file === active.file}
            className={toggleButton({ active: d.file === active.file })}
          >
            {t(`story.${slugOf(d.file)}Tab`)}
          </button>
        ))}
        <span className="ml-auto self-center text-xs text-faint font-mono">{active.file}</span>
      </div>
      <MarkdownDoc source={active.source} resolveDocLink={resolveDocLink} />
    </div>
  );
}
