/**
 * Benchmark results utilities
 * Provides score comparison, algorithm family classification, and notes helpers
 */

import type { BenchmarkResult, BenchmarkScore } from '../types/benchmark';

const LOCAL_STORAGE_KEY = 'benchmarkNotes';

// ── Algorithm family classification ──

export type AlgorithmFamily =
  | 'tabu'
  | 'sa'
  | 'late_acceptance'
  | 'great_deluge'
  | 'step_counting_hc'
  | 'vnd';

/** Tailwind border-left color classes per algorithm family */
export const FAMILY_COLORS: Record<AlgorithmFamily, string> = {
  tabu: 'border-l-blue-500',
  sa: 'border-l-orange-400',
  late_acceptance: 'border-l-purple-500',
  great_deluge: 'border-l-yellow-500',
  step_counting_hc: 'border-l-green-500',
  vnd: 'border-l-accent',
};

/**
 * Classify a solver config name into its algorithm family.
 * Uses prefix matching on the config name from the benchmark XML.
 */
export function getAlgorithmFamily(configName: string): AlgorithmFamily {
  if (configName.startsWith('Tabu-')) return 'tabu';
  if (configName.startsWith('SA-')) return 'sa';
  if (configName.startsWith('LateAcceptance-') || configName.startsWith('Late-Acceptance') || configName.startsWith('LA-')) return 'late_acceptance';
  if (configName.startsWith('GreatDeluge-')) return 'great_deluge';
  if (configName.startsWith('StepCountingHillClimbing')) return 'step_counting_hc';
  if (configName.startsWith('VND-') || configName.startsWith('VariableNeighborhoodDescent')) return 'vnd';
  return 'tabu'; // fallback
}

/**
 * Find the best-scoring result per algorithm family.
 */
export function getBestPerFamily(results: BenchmarkResult[]): Partial<Record<AlgorithmFamily, BenchmarkResult>> {
  const bests: Partial<Record<AlgorithmFamily, BenchmarkResult>> = {};
  for (const r of results) {
    const family = getAlgorithmFamily(r.configName);
    const current = bests[family];
    if (!current || compareScores(r.score, current.score) < 0) {
      bests[family] = r;
    }
  }
  return bests;
}

/**
 * Compare two scores for sorting
 * Returns negative if a is better (lower penalties), positive if b is better
 */
export function compareScores(a: BenchmarkScore, b: BenchmarkScore): number {
  if (a.hard !== b.hard) return b.hard - a.hard;
  if (a.medium !== b.medium) return b.medium - a.medium;
  return b.soft - a.soft;
}

/**
 * Get saved notes for a benchmark result from localStorage
 */
export function getNotesForResult(resultId: string): string | undefined {
  try {
    const notesJson = localStorage.getItem(LOCAL_STORAGE_KEY);
    if (notesJson) {
      const allNotes = JSON.parse(notesJson);
      return allNotes[resultId];
    }
  } catch (error) {
    console.error('Error reading notes from localStorage:', error);
  }
  return undefined;
}

/**
 * Save notes for a benchmark result to localStorage
 */
export function saveNotesForResult(resultId: string, notesText: string): void {
  try {
    const notesJson = localStorage.getItem(LOCAL_STORAGE_KEY);
    const allNotes = notesJson ? JSON.parse(notesJson) : {};
    allNotes[resultId] = notesText;
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(allNotes));
  } catch (error) {
    console.error('Error saving notes to localStorage:', error);
  }
}
