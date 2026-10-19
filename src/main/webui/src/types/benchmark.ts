/**
 * Benchmark result types for tracking solver configuration performance
 */

export interface BenchmarkScore {
  hard: number;
  medium: number;
  soft: number;
  formatted: string;
}

export interface BenchmarkResult {
  id: string;
  configName: string;
  configNumber: number;
  score: BenchmarkScore;
  timeMillisSpent: number;
  timeFormatted: string;
  dateRun: string;
  directoryName: string; // Original directory name for report links
  notes?: string;
}

export type SortField = 'configNumber' | 'score' | 'time' | 'date';
export type SortOrder = 'asc' | 'desc';
