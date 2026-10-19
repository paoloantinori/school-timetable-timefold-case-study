import type { PreAssignmentResult } from '../types/domain';

export interface FormattedPreAssignResult {
  summary: string;
  details: string;
  fullReport: string;
  validation?: null; // Kept for backwards compatibility
}

/**
 * Formats pre-assignment API responses into a user-friendly structure.
 * Handles the new simplified PreAssignmentStatusResponse type.
 */
export function formatPreAssignResult(result: PreAssignmentResult): FormattedPreAssignResult {
  const lines: string[] = [];
  lines.push(`Pre-Assignment Status`);
  lines.push(`======================`);
  lines.push(`Status: ${result.status}`);

  if (result.currentPhase) {
    lines.push(`Current Phase: ${result.currentPhase}`);
  }

  lines.push(`Candidates Tried: ${result.candidatesTried}`);

  if (result.error) {
    lines.push(`Error: ${result.error}`);
  }

  const summary = result.message || `Status: ${result.status}`;
  const details = result.error || (result.currentPhase ? `Phase: ${result.currentPhase}` : '');

  return {
    summary,
    details,
    fullReport: lines.join('\n'),
  };
}
