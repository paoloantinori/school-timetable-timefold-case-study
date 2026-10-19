---
id: TASK-355
title: Golden-solution regression test suite (constraint-drift guard)
status: Done
assignee: []
created_date: '2026-07-16 09:37'
updated_date: '2026-07-17 23:01'
labels:
  - claude-planned
  - spalmone-2027
  - coherence
  - test
milestone: m-9
dependencies:
  - TASK-347
  - TASK-348
  - TASK-349
  - TASK-350
priority: high
ordinal: 179000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: the golden timetable gives us permanent ground truth. A JUnit suite that loads it as a fixture and asserts known-good invariants catches TWO failure classes forever: constraint implementations drifting (a constraint suddenly fires on the golden data) and importer/model regressions.

WHAT:
1. Create a compact test fixture from claudedocs/spalmone-2026-27/data/lessons.csv (H2-friendly: load via test resources, not PostgreSQL). If 12k rows are too slow for @QuarkusTest, sample deterministic subsets per assertion (e.g. all of one group + all of one teacher + all stage-window weeks) - state the choice in notes.
2. Assertions (from analysis.md section 3): zero teacher conflicts, zero group double-bookings, zero closure-day lessons, zero morning gaps; plus the CALIBRATED expected counts for recalibrated constraints (H15 count after TASK-349, H7=0 after TASK-348, H17/H11/H18 per TASK-350).
3. Wire into ./scripts/core/test.sh (default run, not a separate profile).
4. The suite must fail loudly with the constraint name and the offending lesson set when drift happens.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Suite runs in the default test run and passes on current calibrated constraints
- [x] #2 Deliberately breaking a constraint (temporary local edit) makes the suite fail naming that constraint - demonstrated in notes
- [x] #3 Runtime overhead documented; kept under about 60s
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Living golden guard in the default suite (GoldenSolutionRegressionTest, 6-15s full fixture, no sampling needed). Pins: zero-tolerance H1/H3/H6/H20/H11/M20; calibrated H4=-12, H15=-9420/98, H7=0, H17=-3/2 (both matches root-caused: the two documented afternoon-contiguity exceptions — TASK-366's prediction was incomplete, corrected in audit+test). Failure UX lists offending tuples; guard demonstrated by inverting H6. M21/M12/S-tier sign-only with TODOs for 349/350/365/372. Full DoD (review high incl. the folded TASK-351 script: RA-filter concern refuted mechanically, template-churn bias shown conservative).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile)
<!-- DOD:END -->
