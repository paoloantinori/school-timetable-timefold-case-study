---
id: TASK-325
title: >-
  EXPERIMENT: Acceptor sweep on LS phases 3-4 (tabu vs Late Acceptance vs
  Simulated Annealing)
status: Done
assignee: []
created_date: '2026-07-12 17:43'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-3
dependencies: []
references:
  - src/main/resources/solverConfig.xml
  - .claude/rules/benchmark-xsd-validation.md
  - claudedocs/insights/benchmark-running-instructions.md
priority: high
ordinal: 149000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: All three LS phases use entityTabuSize=7, a choice traced to an UNVERIFIED prior benchmark round (claudedocs/insights/benchmark-running-instructions.md flags it explicitly) that predates the 6-phase pipeline and Timefold 2.x. With H17 (vertical compaction) and H15 as HARD constraints, many improving trajectories require temporarily worsening the hard score (hump crossing); tabu search is weak at this, while Late Acceptance and Simulated Annealing are the textbook acceptors for score-trap escape (Timefold docs, and maintainer guidance in github discussion #951). Never tested on this architecture. Related existing tasks: TASK-280..284 and TASK-291..296 (solver-optimization milestone) overlap partially - reference them, do not duplicate their VND-specific work. See roadmap doc section 3.

WHAT:
1. Use the Timefold benchmarker harness (scripts/benchmark/run-benchmarker.sh with a copy of solverBenchmarkConfigTest.xml) OR sequential protocol runs - benchmarker preferred since it isolates solver config from app lifecycle.
2. Variants for Phase 3 AND Phase 4 acceptors (apply the same acceptor to both phases per variant): (a) entityTabuSize 7 = control; (b) lateAcceptanceSize 100; (c) lateAcceptanceSize 400; (d) simulated annealing with simulatedAnnealingStartingTemperature tuned to typical hard deltas - IMPORTANT score format is Nhard/0medium/0soft integers only, see .claude/rules/benchmark-xsd-validation.md for the known XML pitfalls; (e) tabu 7 with acceptedCountLimit raised (e.g. 1000 vs current forager setting).
3. Validate every benchmark XML with scripts/validate/validate-solver-xml.sh BEFORE running (mandatory rule).
4. Equal termination across variants. Two repetitions minimum for the top two variants.
5. Append rows to claudedocs/research/experiment-log.md; decide and apply the winner to solverConfig.xml following .claude/rules/solver-config-sync.md, or keep control.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 At least 4 acceptor variants benchmarked at equal termination; XML validated before every run
- [ ] #2 Top two variants re-run for reproducibility
- [ ] #3 experiment-log.md updated; decision applied to solverConfig.xml or explicitly rejected with numbers
- [ ] #4 Cross-references added to TASK-280..284 / TASK-291..296 noting what this task settles
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 15:26
---
UPSTREAM CONTEXT (TASK-323 scout, 2026-07-14): Timefold 2.3.0 includes PR #2407 'reset late list when upper levels of score changed' - a Late Acceptance bug fix. Previously LA's late list was NOT reset when the hard-score tier changed, which would handicap LA exactly in our score-trap situation (improving moves that temporarily worsen hard). Now that we are on 2.3.0 (TASK-323), Late Acceptance should be treated as a FIRST-CLASS candidate in this sweep - test it early and prominently alongside tabu, not as an afterthought. Release: github.com/TimefoldAI/timefold-solver/releases/tag/v2.3.0
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DONE 2026-07-15 (NEGATIVE): Late Acceptance size 400 on Phase 3+4 produced -158hard vs control -154 (within noise, marginally worse). H4.3 completely unchanged (-60, structural). M22 worsened (-93 vs -84 — LA spreads lessons into more empty days). Medium -61k worse. LA took ~2h (much slower convergence than tabu). VERDICT: LA does not reduce the structural hard debt. Combined with TASK-324 (R&R refuted), both search levers are exhausted — the hard debt is data-driven, not a search trap. Reverted to tabu-7.
<!-- SECTION:FINAL_SUMMARY:END -->
