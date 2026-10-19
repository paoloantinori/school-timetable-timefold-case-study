---
id: TASK-180
title: Benchmark upgraded solver against pre-upgrade baseline
status: Done
assignee: []
created_date: '2026-03-05 15:28'
updated_date: '2026-07-14 05:31'
labels:
  - benchmark
  - performance
  - claude-planned
milestone: Timefold Upgrade
dependencies:
  - TASK-178
references:
  - claudedocs/research-timefold-rr-upgrade-2026-03-05.md
  - scripts/benchmark.sh
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

After completing the Timefold 1.31 + Quarkus 3.30 upgrade with R&R and precompute(), run a full benchmark comparing performance against the pre-upgrade baseline.

## Approach

1. Record pre-upgrade baseline (current score after 3h run): `-3,592hard/-1,265,042medium/-320,027soft`
2. Run fresh profile with upgraded solver for same duration
3. Compare: hard score convergence speed, final hard score, move evaluation speed, CH duration

## Benchmark Protocol

```bash
# Fresh profile to eliminate state bias
./planner.sh stop
yes | ./planner.sh delete bench-upgrade
mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true
./planner.sh start bench-upgrade
./planner.sh wait

# Run pre-assignment
./scripts/api/api.sh pre-assign
# Wait for completion
./scripts/api/api.sh pre-assign-status

# Run solver for comparable duration
./scripts/api/api.sh solve
# Monitor progress
./scripts/api/api.sh score
```

## Metrics to Compare

| Metric | Pre-Upgrade Baseline | Post-Upgrade |
|--------|---------------------|-------------|
| CH duration | ~90s | ? |
| Hard score at 10 min | -75,058 (old benchmark) | ? |
| Hard score at 1 hour | ? | ? |
| Hard score at 3 hours | -3,592 | ? |
| Move eval speed (steps/s) | ? | ? |
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 10-minute benchmark completed on fresh profile
- [ ] #2 Hard score improvement over pre-upgrade baseline documented
- [ ] #3 Move evaluation speed measured and compared
- [ ] #4 Results saved to claudedocs/ with timestamp
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED by TASK-320 (baseline) + TASK-323 (Timefold 2.3.0 upgrade A/B). The "pre-upgrade baseline" framing is obsolete — per the 2026-07-12 critical assessment, no current baseline exists at all; every prior benchmark predates the 6-phase pipeline, RuinRecreate, diminishedReturns, and Timefold 2.x. TASK-320 establishes the first valid baseline; TASK-323 does the 2.1->2.3 upgrade A/B properly.
<!-- SECTION:FINAL_SUMMARY:END -->
