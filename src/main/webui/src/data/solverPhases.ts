/**
 * Solver phase configuration for the planner application.
 *
 * This defines the phases in the Timefold solver pipeline.
 * Each phase has a name, duration, and move strategy.
 * The display uses a compact format for the progression panel.
 */

export interface SolverPhase {
  /** Phase number for display (1-indexed) */
  number: number;

  /** Unique identifier (e.g., "phase-1", "phase-2") */
  id: string;

  /** Human-readable name with phase type prefix */
  name: string;

  /** Typical duration for UI display */
  duration: string;

  /** Description of what moves are explored */
  moveStrategy: string;

  /** Termination configuration (secondsSpentLimit, unimprovedSecondsSpentLimit) */
  termination?: {
    secondsSpentLimit?: number;
    unimprovedSecondsSpentLimit?: number;
    /** Human-readable diminished-returns plateau termination (TASK-378 docs sync) */
    diminishedReturns?: string;
  };

  /** Optional: move weights for display (only for LS phases) */
  moveWeights?: { move: string; weight: number }[];

  /** Optional: metaheuristic type (e.g., 'Late Acceptance', or 'Entity Tabu + Late Acceptance') */
  metaheuristic?: string;
}

/**
 * All phases in the solver pipeline.
 *
 * IMPORTANT: Keep this synchronized with:
 * - solverConfig.xml (phase count, termination configs)
 * - SolverConfigParser.java (phase names, timeouts)
 *
 * ORCHESTRATION NOTE (TASK-483, ratified DEFAULT ON since 2026-08-03): the staged solve
 * wraps these 5 phases in a multi-start retry: attempt 1 is the deterministic
 * construction, attempts 2+ re-run the cycle with randomized GRASP constructions,
 * pick-best on (hard, medium), stopping at the first 0-hard attempt (the UI shows
 * "Tentativo X di Y"). So one solve can traverse this pipeline more than once; the
 * phase list below is the content of ONE attempt.
 */
export const SOLVER_PHASES: SolverPhase[] = [
  {
    id: 'phase-1',
    number: 1,
    name: 'CH - Lesson Placement',
    duration: '~10-15 sec',
    moveStrategy: 'Weekly template stamp (TASK-381 L2), day-clustered round-robin repair tail, least-conflict force-place for leftovers, room assignment',
    metaheuristic: 'Custom construction phase (occupancy-map guided, TASK-378/381)',
  },
  {
    id: "phase-2",
    number: 2,
    name: 'LS - Compactness',
    duration: 'Up to 60 min (plateau- or unimproved-terminated)',
    moveStrategy: 'Timeslot changes (5x), Swap (3x), Pillar change (2x), Pillar swap (1x)',
    moveWeights: [
      { move: 'Change timeslot', weight: 5 },
      { move: 'Swap timeslot', weight: 3 },
      { move: 'Pillar change', weight: 2 },
      { move: 'Pillar swap', weight: 1 },
    ],
    termination: {
      secondsSpentLimit: 3600,
      unimprovedSecondsSpentLimit: 600,
      diminishedReturns: 'PT2M window, 0.1 min improvement ratio',
    },
    metaheuristic: 'Entity Tabu (7), acceptedCountLimit 1000',
  },
  {
    id: "phase-3",
    number: 3,
    name: 'Repair + Compaction',
    duration: 'Seconds (no-op if nothing to fix)',
    moveStrategy: 'Deterministic repair chain, post-B-TIMESLOT (staged cycle): HoleRepair (mid-day H17 gaps by import), LabDayConsolidation (TASK-463, cross-group lab-day teacher anchor), AnchorRepair (TASK-433, leading late-start holes), SurplusRebalance (TASK-422, above-plan donor days), ExactFinisher (TASK-478, exact completion of the boxed H0 tail), StudentDayCompaction re-pass (TASK-390/393). All improve-or-noop. A pass that cannot improve leaves the grid untouched. This XML phase is the compaction shell; the repair chain runs in the staged orchestration around the solver stages.',
    metaheuristic: 'Custom deterministic phases (orchestration layer): improve-or-noop, conflict-free, no metaheuristic search',
  },
  {
    id: "phase-4",
    number: 4,
    name: 'CH - Room Assignment',
    duration: '~5-10 min',
    moveStrategy: 'Construction heuristic',
    metaheuristic: 'Queued Entity Placer',
  },
  {
    id: "phase-5",
    number: 5,
    name: 'LS - Room Refinement',
    duration: '2-10 min',
    moveStrategy: 'Room changes only',
    termination: {
      secondsSpentLimit: 600,
      diminishedReturns: 'PT1M window, 0.05 min improvement ratio',
    },
    metaheuristic: 'Entity Tabu (7)',
  },
];

// Legacy exports for SolverPhasesPage compatibility
export const solverPhases = SOLVER_PHASES;
export const constructionPhases = SOLVER_PHASES.filter(p => p.name.startsWith('CH -'));
export const localSearchPhases = SOLVER_PHASES.filter(p => p.name.startsWith('LS -'));
