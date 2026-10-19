/**
 * Shared parser for the solver's score strings (TASK-538): one place that knows both
 * grammars, so a format change is a one-file edit and no consumer silently reads zeros.
 *
 * - BendableScore bracket format (live since the TASK-538 milestone-1 migration):
 *   "[0/-12]hard/[-500/0]soft" -> { hard: 0, quasi: -12, medium: -500, soft: 0 }
 * - Legacy flat format (pre-migration strings, old exports; k/M suffixes accepted):
 *   "-5hard/-1200medium/-300soft" -> { hard: -5, quasi: 0, medium: -1200, soft: -300 }
 *
 * Returns null when the string matches neither grammar; callers decide their own
 * fallback (raw display, zeros, ...). The suffix-regex legacy parsers this replaces
 * could not see the bracket format (']' precedes "hard") and returned 0 for every
 * live score.
 */

export interface ScoreParts {
  hard: number;
  quasi: number;
  medium: number;
  soft: number;
}

const BENDABLE_SCORE_RE = /\[(-?\d+)\/(-?\d+)\]hard\/\[(-?\d+)\/(-?\d+)\]soft/;
const LEGACY_SCORE_RE = /(-?[\d.]+)([kM]?)hard\/(-?[\d.]+)([kM]?)medium\/(-?[\d.]+)([kM]?)soft/;

function parseLegacyNumber(value: string, suffix: string): number {
  const n = parseFloat(value);
  if (suffix === 'k') return Math.round(n * 1_000);
  if (suffix === 'M') return Math.round(n * 1_000_000);
  return Math.round(n);
}

export function parseScoreParts(scoreStr: string | null | undefined): ScoreParts | null {
  if (!scoreStr) return null;
  const bendable = scoreStr.match(BENDABLE_SCORE_RE);
  if (bendable) {
    return {
      hard: parseInt(bendable[1], 10),
      quasi: parseInt(bendable[2], 10),
      medium: parseInt(bendable[3], 10),
      soft: parseInt(bendable[4], 10),
    };
  }
  const legacy = scoreStr.match(LEGACY_SCORE_RE);
  if (!legacy) return null;
  return {
    hard: parseLegacyNumber(legacy[1], legacy[2]),
    quasi: 0,
    medium: parseLegacyNumber(legacy[3], legacy[4]),
    soft: parseLegacyNumber(legacy[5], legacy[6]),
  };
}
