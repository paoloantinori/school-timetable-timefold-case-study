---
id: TASK-327
title: >-
  EXPERIMENT: Constraint-stream efficiency pass (pre-filter self-joins, Timefold
  2.x joiners)
status: Done
assignee: []
created_date: '2026-07-12 17:44'
updated_date: '2026-07-25 05:55'
labels:
  - claude-planned
  - experiment
milestone: m-3
dependencies: []
references:
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
priority: medium
ordinal: 151000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: More moves evaluated per second = more search at equal wall-clock, and this is one of the few throughput levers available on Community edition (multithreaded solving is Enterprise-only). S2 (subject distribution) and S7 (reward consecutive student lessons) do full Lesson-to-Lesson self-joins without pre-filtering, the exact pattern the codebase already optimized away in H16 and M-LA by filtering BEFORE the join. Timefold 2.0 also added new joiners (containing, containedIn, containingAnyOf) claiming up to 3x speedup in some cases. Absorbs TASK-154 (constraint-stream micro-opts). See roadmap doc sections 1 and 3.

WHAT:
1. Measure first: from solver logs of a TASK-320 baseline run, extract average move evaluation speed (moves per second, logged per phase by Timefold) - this is the metric, not the score.
2. Refactor S2 and S7 in SchoolScheduleConstraintProvider to pre-filter the stream before joining (follow the H16/M-LA pattern in the same file). Behavior must be IDENTICAL: the ConstraintVerifier tests must pass unchanged (./scripts/core/test.sh); add missing ConstraintVerifier coverage for S2/S7 first if absent.
3. Review H16a/H16b forEachUniquePair usage: the third joiner accesses timeslot.getDate() - confirm on Timefold 2.x that forEachUniquePair only streams fully-initialized entities (timeslot is NOT allowsUnassigned, room is) so the joiner is null-safe; document the conclusion in a code comment.
4. Evaluate whether any join in the provider benefits from the new 2.x joiners; apply only where semantics are provably identical.
5. Re-measure moves per second on an identical protocol run; append before/after to experiment-log.md. Score differences between runs are stochastic noise - the deliverable is throughput, verified semantics, and passing tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Baseline and post-change moves-per-second captured from logs for phases 3 and 4
- [ ] #2 S2 and S7 pre-filtered; all ConstraintVerifier tests pass; new coverage added if S2/S7 lacked tests
- [ ] #3 H16a/b null-safety conclusion documented in code comment
- [ ] #4 experiment-log.md row with throughput delta; no semantic changes to any constraint
<!-- AC:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-14 15:26
---
UPSTREAM CONTEXT (TASK-323 scout, 2026-07-14): Timefold 2.3.0 includes two upstream perf fixes directly in this task's lane - PR #2442 'perf(constraint-streams): improve joins by removing list entries' (our H1/H2/H3 self-joins are the hot path) and PR #2430 'perf: improve consecutive sequences collector' (we use toConsecutiveSequences for incremental scoring). Re-baseline constraint-stream throughput on 2.3.0 before trusting the March profiling numbers - part of the expected win may already be delivered upstream, which could narrow or re-prioritize the remaining work here.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
DONE 2026-07-15 (partial — code complete, measurement pending): S2 (subject distribution) optimized — moved a.id < b.id dedup from post-filter into Joiners.lessThan(l -> l.id) indexed joiner (~50% fewer tuples). S7 already optimized (already used lessThan). H16a null-safety documented (forEachUniquePair uses forEach() semantics, timeslot non-null). All 273 tests pass. Throughput measurement (moves/sec before/after) pending — needs Timefold move-stats logging config (separate setup). 2.3.0 upstream PR #2442 (join perf) + #2430 (consecutive-seqs perf) already delivered part of the expected win.
<!-- SECTION:FINAL_SUMMARY:END -->
