import { useState } from 'react';
import { Routes, Route, NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { lazy, Suspense } from 'react';
import ScheduleTimeline from './components/ScheduleTimeline';
import ThemeSelector from './components/ThemeSelector';
import { useReconciliationBadge } from './hooks/useReconciliationBadge';
import ValidationBadge from './components/ValidationBadge';
import { changeLanguagePersisted } from './i18n';

// Lazy load pages to reduce initial bundle size
const AdminLayout = lazy(() => import('./components/admin/AdminLayout'));
const DocsPage = lazy(() => import('./components/DocsPage'));
const ConstraintsPage = lazy(() => import('./components/ConstraintsPage'));
const ComplexityPage = lazy(() => import('./components/ComplexityPage'));
const SolverPhasesPage = lazy(() => import('./components/SolverPhasesPage'));
const BenchmarkResultsPage = lazy(() => import('./components/BenchmarkResultsPage'));
const ReconciliationPage = lazy(() => import('./components/ReconciliationPage'));
const ProjectStoryPage = lazy(() => import('./components/ProjectStoryPage'));

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `text-white/70 no-underline px-3 py-1.5 rounded text-sm transition-all duration-200 ${
    isActive ? 'text-active-link bg-active-link-bg' : 'hover:text-white'
  }`;

/** Dev-tool links: same shape as nav links but visibly secondary (smaller, dimmed). */
const devLinkClass = ({ isActive }: { isActive: boolean }) =>
  `text-white/50 no-underline px-2 py-1 rounded text-xs transition-all duration-200 ${
    isActive ? 'text-active-link bg-active-link-bg' : 'hover:text-white/80'
  }`;

export default function App() {
  const { t, i18n } = useTranslation(['common', 'schedule', 'admin', 'docs', 'complexity', 'phases', 'benchmark', 'reconciliation']);
  const [menuOpen, setMenuOpen] = useState(false);
  const reconciliationBadgeCount = useReconciliationBadge();

  return (
    <>
      <nav className="sticky top-0 z-50 flex flex-wrap items-center gap-2 md:gap-4 px-4 md:px-6 py-3 bg-nav-bg text-nav-text transition-colors duration-300">
        <span className="font-bold text-lg">{t('common:appTitle')}</span>

        {/* Hamburger toggle (mobile only) */}
        <button
          className="md:hidden ml-auto px-2 py-1 text-white/80 hover:text-white cursor-pointer bg-transparent border-none text-xl"
          onClick={() => setMenuOpen(v => !v)}
          aria-label={t('common:navToggle')}
        >
          {menuOpen ? '✕' : '☰'}
        </button>

        {/* Nav links — hidden on mobile until hamburger is toggled */}
        <div className={`${menuOpen ? 'flex' : 'hidden'} md:flex flex-col md:flex-row gap-1 md:gap-2 w-full md:w-auto md:items-center`}>
          <NavLink to="/" className={navLinkClass} onClick={() => setMenuOpen(false)}>
            {t('schedule:title')}
          </NavLink>
          <NavLink to="/admin" className={navLinkClass} onClick={() => setMenuOpen(false)}>
            {t('admin:title')}
          </NavLink>
          <NavLink to="/docs" className={navLinkClass} onClick={() => setMenuOpen(false)}>
            {t('docs:title')}
          </NavLink>
          <NavLink to="/reconciliation" className={navLinkClass} onClick={() => setMenuOpen(false)}>
            {t('reconciliation:navTitle')}
            {reconciliationBadgeCount > 0 && (
              <span className="ml-1.5 inline-flex items-center justify-center min-w-[1.25rem] h-5 px-1 rounded-full bg-feedback-error-bg text-feedback-error-text text-[10px] font-bold">
                {reconciliationBadgeCount}
              </span>
            )}
          </NavLink>

          {/* Development tools: internal KPI/benchmark artifacts, not production data (UX audit
              TASK-514). Kept reachable but visually separated and labelled so nobody mistakes
              stale dev runs for current scheduler output. */}
          <span title={t('common:devSectionTitle')}
                className="text-white/40 text-[11px] uppercase tracking-wider self-center select-none ml-1">
            {t('common:devSection')}
          </span>
          <NavLink to="/benchmarks" className={devLinkClass} onClick={() => setMenuOpen(false)}>
            {t('benchmark:navTitle')}
          </NavLink>
          {/* Report di analisi: HTML generati dagli script KPI, serviti da ReportsResource.
              Link esterno e non NavLink perche' non e' una rotta React ma pagine statiche
              rese dal backend; si apre in una scheda nuova per non perdere lo stato dell'app. */}
          <a href="/reports" target="_blank" rel="noreferrer"
             className={devLinkClass({ isActive: false })}
             title={t('common:devSectionTitle')}
             onClick={() => setMenuOpen(false)}>
            {t('common:reports')}
          </a>
          {/* TASK-665: constraint inventory, complexity, solver phases and project story are
              internals for planners/developers, so they live in the dev section like the
              benchmark artifacts above. The label is i18n (no fixed Italian in the nav). */}
          <NavLink to="/constraints" className={devLinkClass} onClick={() => setMenuOpen(false)}>
            {t('common:constraintsNavTitle')}
          </NavLink>
          <NavLink to="/complexity" className={devLinkClass} onClick={() => setMenuOpen(false)}>
            {t('complexity:navTitle')}
          </NavLink>
          <NavLink to="/phases" className={devLinkClass} onClick={() => setMenuOpen(false)}>
            {t('phases:navTitle')}
          </NavLink>
          <NavLink to="/storia" className={devLinkClass} onClick={() => setMenuOpen(false)}>
            {t('common:story.navTitle')}
          </NavLink>

          {/* Strategy + theme + lang — stacked on mobile */}
          <div className="flex flex-wrap items-center gap-2 md:ml-auto mt-2 md:mt-0">
            <span className="bg-nav-hover text-white px-3 py-1 rounded-xl text-xs font-semibold tracking-wide">
              {t('common:strategy')}
            </span>
            <ValidationBadge />
            <ThemeSelector />
            <div className="flex gap-2">
              {['en', 'it'].map(lng => (
                <button
                  key={lng}
                  onClick={() => changeLanguagePersisted(lng)}
                  className={`px-2 py-1 rounded border text-sm transition-all duration-200 cursor-pointer ${
                    i18n.language === lng
                      ? 'bg-white/20 border-white/60 text-white font-medium'
                      : 'bg-transparent border-white/30 text-white/80 hover:border-white/50'
                  }`}
                  aria-pressed={i18n.language === lng}
                  aria-label={lng === 'en' ? t('common:langSwitchEn') : t('common:langSwitchIt')}
                >
                  {lng === 'en' ? 'English' : 'Italiano'}
                </button>
              ))}
            </div>
          </div>
        </div>
      </nav>
      <main className="p-2 md:p-4">
        <Suspense fallback={<div className="flex items-center justify-center p-8">{t('common:loading')}</div>}>
          <Routes>
            <Route path="/" element={<ScheduleTimeline />} />
            <Route path="/admin/*" element={<AdminLayout />} />
            <Route path="/docs" element={<DocsPage />} />
          <Route path="/constraints" element={<ConstraintsPage />} />
            <Route path="/complexity" element={<ComplexityPage />} />
            <Route path="/phases" element={<SolverPhasesPage />} />
            <Route path="/benchmarks" element={<BenchmarkResultsPage />} />
            <Route path="/reconciliation" element={<ReconciliationPage />} />
            <Route path="/storia" element={<ProjectStoryPage />} />
            <Route path="/storia/:doc" element={<ProjectStoryPage />} />
          </Routes>
        </Suspense>
      </main>
    </>
  );
}
