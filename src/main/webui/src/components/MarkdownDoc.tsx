import { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
// Le tabelle dei due documenti sono in sintassi GitHub, che react-markdown non parsa da solo.
import remarkGfm from 'remark-gfm';

const REMARK_PLUGINS = [remarkGfm];

const CODE_BASE = 'font-mono bg-surface-alt text-content rounded';
const CELL_BASE = 'text-content px-3 py-2 border border-border-light';

/**
 * Stile dei nodi markdown: NON usa il plugin tailwind `typography`, perche' le classi
 * `prose` sono statiche mentre qui i colori vivono in variabili CSS applicate a
 * documentElement (theme.tsx), quindi mapparli sui token esistenti e' l'unico modo di
 * seguire tutti e tre i temi. Definito a livello di modulo: react-markdown ri-parsa il
 * documento a ogni render, quindi non deve dipendere da oggetti ricreati.
 */
const STATIC_COMPONENTS: Components = {
  h1: ({ children }) => (
    <h1 className="text-2xl md:text-3xl font-bold text-content mt-8 mb-4 first:mt-0">{children}</h1>
  ),
  h2: ({ children }) => (
    <h2 className="text-xl md:text-2xl font-semibold text-content mt-10 mb-3 pb-2 border-b border-border-light">
      {children}
    </h2>
  ),
  h3: ({ children }) => <h3 className="text-lg font-semibold text-content mt-6 mb-2">{children}</h3>,
  p: ({ children }) => <p className="text-content leading-relaxed my-3">{children}</p>,
  ul: ({ children }) => <ul className="list-disc pl-6 my-3 space-y-1.5 text-content">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-6 my-3 space-y-1.5 text-content">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  strong: ({ children }) => <strong className="font-semibold text-content">{children}</strong>,
  em: ({ children }) => <em className="italic text-muted">{children}</em>,
  hr: () => <hr className="my-8 border-0 border-t border-border-light" />,
  blockquote: ({ children }) => (
    <blockquote className="border-l-4 border-accent-text pl-4 my-4 text-muted italic">{children}</blockquote>
  ),
  code: ({ children }) => (
    <code className={`${CODE_BASE} text-[0.85em] px-1.5 py-0.5 border border-border-faint`}>{children}</code>
  ),
  pre: ({ children }) => (
    <pre className={`${CODE_BASE} text-sm p-3 my-4 border border-border-light overflow-x-auto`}>{children}</pre>
  ),
  table: ({ children }) => (
    <div className="overflow-x-auto my-5">
      <table className="w-full text-sm border-collapse">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="bg-surface-alt">{children}</thead>,
  th: ({ children }) => <th className={`${CELL_BASE} text-left font-semibold`}>{children}</th>,
  td: ({ children }) => <td className={`${CELL_BASE} align-top`}>{children}</td>,
};

/** Renderizza un documento markdown lungo con i token del design system. */
export default function MarkdownDoc({
  source,
  resolveDocLink,
}: {
  source: string;
  /**
   * Per un link a un altro documento markdown (es. "RIAPPROCCIO.md") ritorna l'azione di
   * navigazione, oppure null se quel documento non e' fra quelli mostrati. Ritornare null
   * evita di rendere un bottone che al click non farebbe nulla.
   */
  resolveDocLink: (target: string) => (() => void) | null;
}) {
  const components = useMemo<Components>(
    () => ({
      ...STATIC_COMPONENTS,
      a: ({ href, children }) => {
        const target = href ?? '';
        const goToDoc = target.endsWith('.md') ? resolveDocLink(target) : null;
        if (goToDoc) {
          return (
            <button
              type="button"
              onClick={goToDoc}
              className="text-accent-text underline bg-transparent border-none p-0 cursor-pointer font-inherit"
            >
              {children}
            </button>
          );
        }
        return (
          <a href={target} target="_blank" rel="noreferrer" className="text-accent-text underline">
            {children}
          </a>
        );
      },
    }),
    [resolveDocLink]
  );

  // react-markdown ri-parsa a ogni render (nessuna cache interna) e i documenti sono lunghi:
  // senza questo memo il polling di App ri-parserebbe il testo invariato ogni 30 secondi.
  return useMemo(
    () => (
      <article className="max-w-[70ch] mx-auto text-content">
        <ReactMarkdown components={components} remarkPlugins={REMARK_PLUGINS}>
          {source}
        </ReactMarkdown>
      </article>
    ),
    [source, components]
  );
}
