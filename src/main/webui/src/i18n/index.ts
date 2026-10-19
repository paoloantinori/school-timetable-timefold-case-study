import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import enCommon from './locales/en/common.json';
import enSchedule from './locales/en/schedule.json';
import enAdmin from './locales/en/admin.json';
import enDocs from './locales/en/docs.json';
import enComplexity from './locales/en/complexity.json';
import enPhases from './locales/en/phases.json';
import enBenchmark from './locales/en/benchmark.json';
import enReconciliation from './locales/en/reconciliation.json';
import enValidation from './locales/en/validation.json';
import itCommon from './locales/it/common.json';
import itSchedule from './locales/it/schedule.json';
import itAdmin from './locales/it/admin.json';
import itDocs from './locales/it/docs.json';
import itComplexity from './locales/it/complexity.json';
import itPhases from './locales/it/phases.json';
import itBenchmark from './locales/it/benchmark.json';
import itReconciliation from './locales/it/reconciliation.json';
import itValidation from './locales/it/validation.json';

// TASK-662: italiano di default esplicito; la scelta manuale persistita vince se presente.
// Read ONCE here, before init.
// TASK-661: window.localStorage can throw a SecurityError in storage-blocked
// contexts (sandboxed iframes, strict privacy policies) - module top level, so
// an uncaught throw would keep the whole app from mounting. Fall back silently
// to the default language instead.
function readSavedLang(): string | null {
  try {
    return window.localStorage.getItem('planner-lang');
  } catch {
    return null;
  }
}

const savedLang = readSavedLang();

i18n.use(initReactI18next).init({
  resources: {
    en: { common: enCommon, schedule: enSchedule, admin: enAdmin, docs: enDocs, complexity: enComplexity, phases: enPhases, benchmark: enBenchmark, reconciliation: enReconciliation, validation: enValidation },
    it: { common: itCommon, schedule: itSchedule, admin: itAdmin, docs: itDocs, complexity: itComplexity, phases: itPhases, benchmark: itBenchmark, reconciliation: itReconciliation, validation: itValidation },
  },
  lng: savedLang === 'en' || savedLang === 'it' ? savedLang : 'it',
  fallbackLng: 'en',
  ns: ['common', 'schedule', 'admin', 'docs', 'complexity', 'phases', 'benchmark', 'reconciliation', 'validation'],
  defaultNS: 'common',
  interpolation: { escapeValue: false },
});

/**
 * TASK-662: switch language AND persist the choice. Single write path for the
 * 'planner-lang' key: UI components call this instead of touching localStorage
 * directly, so writes and the read-once init above stay in sync.
 */
export function changeLanguagePersisted(lng: string) {
  i18n.changeLanguage(lng);
  // TASK-661: same storage-blocked guard as the init read above; the language
  // still switches, only the persistence is skipped.
  try {
    window.localStorage.setItem('planner-lang', lng);
  } catch {
    // Storage unavailable: silent fallback.
  }
}

export default i18n;
