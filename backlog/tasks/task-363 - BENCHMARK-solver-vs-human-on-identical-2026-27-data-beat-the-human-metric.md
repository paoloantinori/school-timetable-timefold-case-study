---
id: TASK-363
title: 'BENCHMARK: solver vs human on identical 2026/27 data (beat-the-human metric)'
status: Done
assignee: []
created_date: '2026-07-16 09:40'
updated_date: '2026-07-25 06:21'
labels:
  - claude-planned
  - spalmone-2027
  - experiment
milestone: m-11
dependencies:
  - TASK-346
  - TASK-350
  - TASK-355
priority: medium
ordinal: 187000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: with real data and the golden human solution imported, we finally have the definitive end-to-end benchmark: run our full pipeline (pre-assignment + 5-phase solve) on the same 2026/27 dataset and compare against the human timetable on the SAME calibrated constraint set. This closes the loop on all m-7/m-8 work and quantifies solver value for the school.

WHAT:
1. Prereq: m-7 import complete, m-8 recalibration merged (golden audit shows the agreed expected counts).
2. Score the golden solution and the solver solution with the identical ConstraintProvider; compare hard/medium/soft per constraint. Also compare human-relevant aggregates: gaps per teacher, sparse days, subject spread, end-date per group.
3. Standard benchmark protocol (scripts/benchmark/benchmark-branch.sh, 2 runs, equal budget); results appended to experiment-log.md and a summary in claudedocs/spalmone-2026-27/.
4. Honest verdict: where the solver wins, where the human wins, and which constraint gaps explain human wins - each becomes a candidate task.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Both solutions scored under the identical calibrated provider; per-constraint table produced
- [ ] #2 2-run benchmark protocol followed; experiment-log.md updated
- [ ] #3 Verdict document lists concrete follow-up candidates for every dimension the human wins
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RUN 3 RESULT (2026-07-18 12:23, first honest solve post-TASK-377): -1,217,029 hard / -1,589,137 medium / +94,914 soft in 67 min. H3 -1,119,000 (1,119 stacked pairs, 1,049 extras in DB), H11 -76,000, H1 -22,000, 0 unassigned. Golden comparison: calibrated preferences all in-band (M21 -117 vs -621, M25 -17 vs -27, S12 +5,159 vs +5,623, S16 0); conflicts catastrophically worse (golden ~0). TWO ROOT CAUSES identified mechanically: (1) DayClustered CH hands LS ~-1.28M hard — construction itself stacks, was built in the blind-provider era; LS recovers only 66k/hour. (2) Structural saturation: 1/G05 demand exceeds calendar capacity (-21/-14) via T16 Confezione +11/+10 and T17 Moulage +15/+9 vs golden's exact 990/group; G02/G03/G06 at 99.5% fill; golden itself needed 575 afternoon hours now forbidden by H21/Q17. Question for referente drafted (experiment-log.md). NEXT: school answer on MODA hours (or extend Orario-wins to shift courses), then CH/solverConfig retune task for the honest landscape, then S13 A/B (meaningless while hard is -1.2M).

RUN 4 RESULT (2026-07-18 14:22, TASK-379 recalibration active, verified JAR): -518,276 hard / -1,558,849 medium / -159,470 soft in 66 min — 57% hard reduction vs run 3. H3 416 pairs (was 1,119), 346 stacks (was 1,049), 0 unassigned, M28 shows 797 afternoon lessons used (golden used 575). Structural fixes confirmed effective; residual is the stacking CH (TASK-378). Watch item: S1 teacher gaps ballooned to -242k with afternoons open — TASK-378 retune must balance M20/M28/S1. Next: TASK-378, then run 5, then S13 A/B.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
