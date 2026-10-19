import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { fetchConstraintSummary, type ConstraintSummary, type ConstraintCategory, type ConstraintRow } from '../api/scheduleApi';

/**
 * TASK-528: one page answering "cosa abbiamo detto al solver?" in 30 seconds.
 * Data comes from ONE aggregate call (GET /api/constraints/summary); every
 * category shows its count and provenance, EMPTY categories are declared
 * explicitly (silence is information), and the known input-vs-golden
 * divergences get their own "da confermare con la scuola" section.
 */
export default function ConstraintsPage() {
  const { t } = useTranslation(['common']);
  const [summary, setSummary] = useState<ConstraintSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    fetchConstraintSummary()
      .then((s) => { if (alive) setSummary(s); })
      .catch((e) => { if (alive) setError(String(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  if (loading) return <div className="p-6 text-content">Caricamento inventario vincoli...</div>;
  if (error) return <div className="p-6 text-red-600">Errore caricamento vincoli: {error}</div>;
  if (!summary) return null;

  return (
    <div className="p-4 md:p-6 max-w-5xl mx-auto space-y-6">
      <header>
        <h1 className="text-xl md:text-2xl font-bold text-content">Vincoli in input</h1>
        <p className="text-sm text-faint mt-1">
          Tutto cio' che il solver ha ricevuto, per categoria: conteggio, provenienza e righe di dettaglio.
          Le categorie vuote sono dichiarate esplicitamente. Il silenzio e' informazione.
        </p>
      </header>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        {summary.categories.map((c: ConstraintCategory) => (
          <div key={c.id} className="bg-surface rounded-lg shadow-soft p-3">
            <div className="text-2xl font-bold text-content">{c.count}</div>
            <div className="text-xs text-faint">{c.title}</div>
          </div>
        ))}
      </div>

      {summary.categories.map((c: ConstraintCategory) => (
        <section key={c.id} className="bg-surface rounded-lg shadow-soft overflow-hidden">
          <div className="px-4 py-3 border-b border-border flex items-baseline justify-between gap-2">
            <h2 className="font-semibold text-content">{c.title}</h2>
            <span className="text-xs text-faint">{c.count} regole · {c.provenance}</span>
          </div>
          <div className="p-4 text-sm text-content space-y-2">
            {typeof c.detail === 'string' && <p className="text-faint">{c.detail}</p>}
            {c.source && <p className="text-xs text-faint">fonte: {c.source}</p>}
            {c.emptyMessage && (
              <p className="italic text-amber-600 bg-amber-50 dark:bg-amber-900/20 rounded p-2">{c.emptyMessage}</p>
            )}
            {c.rows && c.rows.length > 0 && (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-faint border-b border-border">
                    <th className="py-1 pr-2">chi</th>
                    <th className="py-1 pr-2">cosa</th>
                    <th className="py-1 pr-2">valore</th>
                    <th className="py-1">fonte</th>
                  </tr>
                </thead>
                <tbody>
                  {c.rows.map((r: ConstraintRow, i: number) => (
                    <tr key={i} className="border-b border-light">
                      <td className="py-1 pr-2 font-medium">{r.who}</td>
                      <td className="py-1 pr-2">{r.what}</td>
                      <td className="py-1 pr-2">{r.value}</td>
                      <td className="py-1 text-faint">{r.source}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
      ))}

      {summary.daConfermare && summary.daConfermare.length > 0 && (
        <section className="bg-amber-50 dark:bg-amber-900/20 rounded-lg shadow-soft overflow-hidden">
          <div className="px-4 py-3 border-b border-amber-300">
            <h2 className="font-semibold text-amber-800 dark:text-amber-300">Da confermare con la scuola</h2>
          </div>
          <div className="p-4">
            <table className="w-full text-sm">
              <tbody>
                {summary.daConfermare.map((r: ConstraintRow, i: number) => (
                  <tr key={i} className="border-b border-amber-200">
                    <td className="py-1 pr-2 font-medium">{r.who}</td>
                    <td className="py-1 pr-2">{r.what}</td>
                    <td className="py-1 pr-2 text-faint">{r.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <p className="text-xs text-faint">{summary.generatedNote}</p>
    </div>
  );
}
