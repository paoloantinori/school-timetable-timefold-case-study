# Roads not taken

Index of the significant dead ends, demotions, and reversals, each pointing
at the evidence that closed it. The full constraint-level history is
[constraint-recalibration-history.md](claudedocs/constraint-recalibration-history.md);
this file indexes the strategic ones. A rejection with evidence is a
deliverable here, not an embarrassment: most of these cost days of work and
most of them taught the rule that replaced them.

## Architecture

- **Shadow (L-F) and Block (L-B) planning entities.** The solver once had
  two extra entity families splitting the constraint set per entity tier.
  Removed by TASK-228/267 in favor of a single `Lesson` planning entity;
  the branch names survive only as history
  ([branch-families.md](claudedocs/branch-families.md)).

## Constraints: demoted, promoted, removed

- **H15 -> M15** (same-day same-subject adjacency): the golden itself splits
  such blocks 98 times; hard was stricter than the school's practice.
- **H16 -> M37/S16** (teacher keeps the room across a block): at hard tier
  the search preferred leaving rooms empty rather than breaking a block.
- **H18 -> M18** (5-hour groups must start from slot 0): demoted in March,
  re-promoted five months later as H31 with a near-miss protocol once the
  golden anchoring was measured properly.
- **H21 -> M28** (afternoon slots for stage only) and **H22 -> M29**
  (professionalizzanti at day edges): the golden uses afternoons as capacity
  relief and never followed the edge shape.
- **M36 -> H29** (every scheduled lesson needs a room): promoted with a
  paired re-weighting of H0 after the empty-the-schedule arbitrage was
  measured.
- **S2, S4, S5, S6, S9, M4, M17** and the **H11 external-teacher exception**:
  removed (S9 disabled) for causes ranging from golden-incompatibility to
  the exception pointing at the wrong teacher in the current seed. Full
  table in [CLAUDE.md](CLAUDE.md), "Removed Constraints".

## Pre-assignment plans stricter than the oracle

- **TASK-430** (slot-0 balancing) and **TASK-432** (profile-following):
  both rejected at their adoption gate; both were plans derived from the
  golden applied with more rigidity than the golden itself. Their rejection
  produced the placement-parity rule (see STORY.md, section 2) and the
  mechanical check
  [placement-parity-check.py](scripts/analyze/placement-parity-check.py).

## Search: what the finishers could not fix

- **CompositionHoleCloser** (local hole closure on the known bad basin):
  implemented correctly, kept default-off for months. The cascade that
  looks like a fix relocates the hole (the shell game); the falsification
  probe measured 6/6 shell-games and 0/6 sustainable closures. The real
  cure was basin selection (multi-start), not a smarter local move. It was
  later adopted default-on under the endgame bundle, purely as bounded
  no-op insurance (the oracle guarantees it cannot worsen a delivered
  schedule). Evidence:
  [task481-results](claudedocs/spalmone-2026-27/task481-results/).
- **M-TC flat** (lab tag capacity as a flat default): rejected at flat
  default because it doubled wall-clock on every run; resurrected in the
  per-retry form (armed only on retry attempts) which kept the cure and
  killed the cost
  ([verdict](claudedocs/spalmone-2026-27/task455-results/VERDICT-goodseed-2026-08-03.md)).
- **Adaptive move weights, form A** (Timefold probability weight factory):
  statoricized after it materialized per-step move maps and quadrupled wall
  with heap pressure; the adopted form B thins the dispatch before the
  queue at zero config cost
  ([verdict](claudedocs/spalmone-2026-27/task500-results/ab-2026-08-15/VERDICT.md)).
- **Metaheuristics as the lever**: a dedicated benchmark campaign (tabu,
  late acceptance, move-weight sweeps) concluded the configuration space
  was exhausted; the only durable speedup came from the model pivot and the
  multi-threaded decider. The pivot decision and its evidence close the
  [campaign status of August](claudedocs/spalmone-2026-27/campaign-status-2026-08-05.md).
- **Constraint weight sweeps as a schedule lever**: sweeping the weights of
  M32, M34/M35, and M37 moved the score, not the timetable: the residual
  was flat across 1000x weight ranges because the real drivers were model
  facts and construction, not score shaping. The lesson ("weights move the
  score, not the schedule", referenced by [STORY.md](STORY.md)) is pinned
  here because it kills a whole family of tuning dead ends.

## Predictions and oracles that lied

- **Early-doom predictor** (discard constructions with >150 holes):
  falsified by a paired experiment in which a discarded 209-hole
  construction delivered [-3 hard] in 72 minutes, better than the
  deterministic control. It had silently degenerated the multi-start to a
  single attempt. Default-off; the falsification is recorded in
  [CLAUDE.md](CLAUDE.md) and TASK-677's registry.
- **golden-monthly pacing family**: retracted the same night it was built;
  the "pause months" phenomenon was an artifact of a boolean-parsing bug
  ('t' vs 'true'), and the corrected census dissolved it. The retraction
  verdict is kept as a parsing-hygiene precedent
  ([verdict](claudedocs/spalmone-2026-27/task577-results/VERDICT-poolcapfill-retrazione-goldenmonthly-2026-09-09.md)).
- **Blind puzzle without a cost model**: the first attempt, specified with
  only a visual post-condition, produced a legal one-move solution that was
  poison under the real scoring (it cured one violation and created a worse
  one). The corrected specification, carrying the real cost model, returned
  NO SOLUTION with an independent proof: the state was genuinely stuck, and
  only the model-aware spec could see it
  ([verdict](claudedocs/spalmone-2026-27/task636-results/ab-adoption/VERDICT.md)).
  Earlier puzzles had succeeded on inspection alone; this pair proved the
  intuition insufficient both ways.

## Tested and closed on evidence

- **Kempe chains** (TASK-482): the plan-B chain-swap family for the
  bad-basin holes. Executed and measured twice on its discriminators:
  zero sustainable closures both times (0/6, then 0/10 on the newer model),
  closed NO with the verdicts recorded; a narrow cross-teacher revival
  stays conceivable for one residual family but was never reopened.

## Deferred, not dead

- **Lab rotation seeding**: exact rotation assignments wait for the
  school's confirmation; the seeding function is intentionally empty.
