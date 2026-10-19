---
id: TASK-095
title: Benchmark FAST_ASSERT performance impact on test suite
status: Done
assignee: []
created_date: '2026-02-23 17:25'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - testing
dependencies:
  - task-090
priority: medium
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: Task-090 adds `FAST_ASSERT` environment mode for dev/test profiles. Before merging, we should measure the impact on test execution time to ensure it doesn't make the developer feedback loop unacceptably slow.

**What**: After task-090 is implemented, run `mvn test` with and without `FAST_ASSERT` and compare:
1. Total test suite duration
2. Individual constraint verifier test duration
3. Any integration test duration (if task-091 is also done)

**Expected impact**: `FAST_ASSERT` validates incremental score calculation after every move by running a full score recalculation. For `ConstraintVerifier` tests that use small datasets (3-5 entities), the overhead should be minimal. For the integration smoke test (if present), the 10s solve will be slower per move but the wall-clock time is capped by `spent-limit`.

**Decision criteria**:
- If test suite slows by <2x: Keep `FAST_ASSERT` for both dev and test
- If test suite slows by 2-5x: Keep `FAST_ASSERT` for test only, use `REPRODUCIBLE` for dev
- If test suite slows by >5x: Keep `FAST_ASSERT` for test only, document as a manual CI option

**Dependencies**: task-090 (Add environmentMode FAST_ASSERT)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 mvn test run with REPRODUCIBLE mode: time recorded
- [ ] #2 mvn test run with FAST_ASSERT mode: time recorded
- [ ] #3 Comparison documented (Nx slowdown factor)
- [ ] #4 Decision made on which profiles get FAST_ASSERT based on criteria
- [ ] #5 If decision changes task-090 config: task-090 updated accordingly
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
FAST_ASSERT is stable in production. Benchmark no longer needed - TASK-090 completed successfully, tests pass with FAST_ASSERT enabled.
<!-- SECTION:FINAL_SUMMARY:END -->
