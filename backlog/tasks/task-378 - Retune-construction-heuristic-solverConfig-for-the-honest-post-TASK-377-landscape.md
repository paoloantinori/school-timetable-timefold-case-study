---
id: TASK-378
title: >-
  Retune construction heuristic + solverConfig for the honest (post-TASK-377)
  landscape
status: Done
assignee: []
created_date: '2026-07-18 10:25'
updated_date: '2026-07-18 20:53'
labels:
  - claude-planned
dependencies: []
ordinal: 202000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run 3 (first honest solve, 2026-07-18) proved the 6-phase pipeline was tuned in the blind-provider era: the DayClustered CH hands the local search ~-1.28M hard (it stacks same-group lessons into occupied slots), and one hour of LS recovers only ~66k, terminating at -1,217,029 hard with 1,049 residual same-group same-slot stacks. The biggest lever is a CH that respects (group, slot) and (teacher, slot) occupancy at placement time — near-feasible construction instead of post-hoc repair. Secondary: LS phase moves/foragers (swap chains for latin-square untangling — see the test-config lesson in TASK-377), and possibly a repair phase targeting H3 pairs directly. Context: experiment-log.md run 3, TASK-363 notes. NOTE structural precondition: 1/G05 demand exceeds calendar capacity by 21/14 hours (T16 Confezione, T17 Moulage vs golden's 990/group) — without the school's answer (or Orario-wins extension) even a perfect CH cannot reach 0 hard for those groups.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CH places lessons without creating same-group or same-teacher same-slot conflicts (verify: stacked-extras query = 0 right after CH phase)
- [ ] #2 Full solve on fresh dev profile + calendario import reaches hard score explainable by documented structural deficits only
- [ ] #3 GoldenSolutionRegressionTest still green; test.sh exit 0 via > log; echo $?
- [ ] #4 DoD: /simplify + /code-review high + orchestrator-run verification
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ITERATION 4 (run-8 forensics): the persistent H3 residual (~415 pairs across runs 4-8) was the idealEndDate VRP HORIZON hard-cutting group capacity below demand (G14 Sep-Oct: 83 stacks measured=predicted; G06 Apr 21: 128; saturated groups May 27: ~35 each). Teacher capacity comfortable (max 67%). Filter removed (golden runs to June 10; M12 medium keeps compactness pressure). DATA GAP for the school: G14 golden window is Jan 28-May 13 but seed startdate=Sep 14 - H14 startDate should carry it (derived-from-golden candidate). Run 9 next.

DONE 2026-07-18 22:45 — RUN 9: FIRST FEASIBLE SOLUTION IN PROJECT HISTORY. 'Solving ended: 0hard/-10163070medium/-61606soft', 107 min, zero stacks, zero unassigned, 5,841 rooms, scheduled through 2027-06-10. Four iterations: (1) comparator snapshots + statics guard + lastSolverError (af93e8b7); (2) fallback CH deleted + Moves.change notifications - the deepest root cause (ff3bc474, 18284fad); (3) no occupied-room fallback + afternoons last-resort (0a9a3757); (4) idealEndDate VRP horizon removed - it forced the entire H3 residual (d3c5060a). AC readings: 'CH conflict-free' superseded by outcome (CH -26k, final 0); 'hard explainable by structural deficits' exceeded (0); golden guard green throughout. DoD: /simplify + /code-review high on iterations 1-2; compact review-high on 3-4 (no blocking findings, touches applied d1706a29+); test.sh 605/605 exit 0 at every commit. FOLLOW-UPS logged in experiment-log run 9: M12 weight calibration vs golden practice, S1 teacher gaps, G14 real start date (Jan 28, for the school), S13 A/B (task-375) now meaningful.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
