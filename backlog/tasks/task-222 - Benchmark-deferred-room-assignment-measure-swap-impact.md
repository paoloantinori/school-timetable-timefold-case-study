---
id: TASK-222
title: Benchmark deferred room assignment - measure swap impact
status: Done
assignee: []
created_date: '2026-03-10 06:10'
updated_date: '2026-03-10 06:11'
labels: []
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Benchmark the current TASK-183 implementation to measure the real-world impact of swap moves changing rooms during Phase 4 and 4b.

**Baseline**: Run `./scripts/benchmark.sh noroom 600` to establish performance with deferred room assignment.

**Metrics to track**:
- Phase 2 CH completion time (target: ~30-60s, was ~90s)
- Phase 3 LS moves/sec (should be faster with timeslot-only moves)
- Phase 4 LS moves/sec (swap moves may slow down due to room changes)
- Phase 5 duration (if >30s, indicates significant room degradation)
- Final hard/soft score quality vs baseline

**Acceptance Criteria**:
- [ ] Benchmark completes successfully
- [ ] Phase 2 CH time reduced by ~40%+ (from ~90s to ~30-60s)
- [ ] Overall solve time is comparable or better
- [ ] Final score quality is within 5% of baseline

**Outcome**: Based on results, decide whether to accept current config or implement Option 2/3.
<!-- SECTION:DESCRIPTION:END -->
