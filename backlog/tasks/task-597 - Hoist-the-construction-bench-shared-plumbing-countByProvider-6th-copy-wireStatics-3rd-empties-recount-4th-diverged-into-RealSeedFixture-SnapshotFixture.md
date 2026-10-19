---
id: TASK-597
title: >-
  Hoist the construction-bench shared plumbing (countByProvider 6th copy,
  wireStatics 3rd, empties recount 4th diverged) into
  RealSeedFixture/SnapshotFixture
status: Done
assignee:
  - fable-fedora
created_date: '2026-09-09 16:54'
updated_date: '2026-09-10 21:14'
labels:
  - refactor
  - test-infrastructure
  - bench-family
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task590-results/CENSUS-REPORT-2026-09-09.md
  - TASK-590
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
From the TASK-590 census review (2026-09-09 notte): the construction-bench family carries divergent verbatim copies of its shared plumbing, and the wiring IS the bench's validity (the review's top finding, the disarmed pinned-cells static, was born in exactly this duplication). Hoist into RealSeedFixture/SnapshotFixture: (1) countByProvider/providerCounts, 6th verbatim copy across PoolCapFillBenchTest, PinDrainBenchTest, BoxDeficitOnlyBenchTest, EdfH33LiveStateTest, TeacherPairMajorBenchTest, ReserveCensusBenchTest; (2) wireStatics + the incarichi course-generation loop, 3rd near-verbatim copy (material diffs: rooms published or not, seedTeachersAndAvailabilityRules extraction) - a shared wireConstructionStatics that ALWAYS republishes setPinnedLessonTimeslotsByGroupStatic after pre-assign would have prevented finding 1; (3) the empty-declared-cells recount, 4th copy, ALREADY DIVERGED (ReserveCensusBenchTest adds isQuotaFillExcluded scope; PoolCapFill is pin-less and unfiltered; WeeklyTemplateConstructionRealSeedTest and KempeCellNeighborhoodDiscriminatorTest carry their own). Do it when TASK-590's Phase E lands (the bench will be touched again) or as a standalone hygiene pass; each hoist keeps behavior byte-identical per bench (the count-check is the guard).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every bench that consumed a private copy of providerCounts/countByProvider, wireStatics+seed loop, empties recount, or solveConstruction now consumes the shared helper from RealSeedFixture/SnapshotFixture (grep: zero verbatim private copies remain under src/test)
- [x] #2 Behavior byte-identical per bench: each touched bench re-run green with its existing count-check assertions (the guard named in the task)
- [x] #3 The shared wireConstructionStatics ALWAYS republishes setPinnedLessonTimeslotsByGroupStatic after any pre-assign (the shape that would have prevented the TASK-590 review finding 1), documented in its javadoc
- [x] #4 Full test.sh suite EXIT 0 on the final tree
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
[2026-09-09 notte, final simplify pass] APPEND to the hoist list: (4) solveConstruction construction-only harness, 4th near-verbatim copy (TeacherPairMajorBenchTest:191, PoolCapFillBenchTest:144, BoxDeficitOnlyBenchTest:117, ReserveCensusBenchTest:279); its graspSeed/keep-pins variants are exactly this task's divergence pattern. Skipped-as-settled this round (recorded for completeness): gate-for-gate copy in attemptSubstitution (frozen rejected evidence, break-vs-continue divergence documented); census format emit/parse helpers (format is consumed by committed frozen artifacts); null-course defensive checks at 3 sites (cheap insurance, dispositioned twice).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Bench-family plumbing hoisted into SnapshotFixture/RealSeedFixture (commit bc2792b1, net -181 lines across 11 files). FIVE shared helpers now live in SnapshotFixture: providerCount (historical match-count/prefix form), providerHardPoints (oracle-v3 hard-points/exact-token/fail-loud), providerMatchCounts (multi-family match counts, one rescore - the day-units the committed census artifacts print), wireConstructionStatics + BenchWires record (every static from explicit inputs; the divergent axes are parameters so each bench keeps its exact prior behavior; javadoc carries the anti-finding-1 invariant: re-publish pinned statics after any pre-assign), seedCoursesFromIncarichi (SubjectRegistry-aware, null=legacy-inert printed), solveConstructionOnly (keepPins/graspSeed, GRASP flags in try/finally, no LS by design), emptyDeclaredCellsByGroup (the censused divergence became an explicit parameter). The code review caught a REAL behavior flip in my first pass (pool-cap published in Box/PinDrain where their original wiring never did, plus ReserveCensus holidays/stage armed): fixed BEFORE commit, restoring byte-identity. Verification: seven benches individually re-run EXIT 0 (EdfH33, TeacherPair, PinDrain, BoxDeficit, ReserveCensus, PoolCapFill, WitnessBench), full suite 1292/0/0 EXIT 0, and the byte-identity guard held (PoolCapFill ON-default reproduced the TASK-577 ratification numbers exactly). Documented skips: WeeklyTemplate's per-solution empties tally (different instrument), the Wires-record re-wrap (conversion beyond scope). Bonus fix en route: the test-port-conflict-guard hook had lost its execute bit (silently disarmed since 06:05); chmod +x committed (0281ef48).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 /simplify on the hoist diff
- [x] #2 code-review high on the hoist diff
- [x] #3 Each touched bench re-run individually with EXIT 0 before the full suite
<!-- DOD:END -->
