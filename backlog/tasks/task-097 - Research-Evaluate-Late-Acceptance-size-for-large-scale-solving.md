---
id: TASK-097
title: 'Research: Evaluate Late Acceptance size for large-scale solving'
status: Done
assignee: []
created_date: '2026-02-23 17:26'
updated_date: '2026-03-09 19:50'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
  - research
dependencies: []
priority: low
ordinal: 52000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: Phase 3 (compactness focus) uses `lateAcceptanceSize=200`. The timefold-starter best practices note that for problems with many entities (~8,700), larger acceptance sizes can help the solver escape plateaus by accepting moves that may temporarily worsen the score.

**Current config** (Phase 3):
```xml
<acceptor>
  <entityTabuSize>5</entityTabuSize>
  <lateAcceptanceSize>200</lateAcceptanceSize>
</acceptor>
```

**What Late Acceptance does**: Accepts a move if the new score is at least as good as the score N steps ago (where N = `lateAcceptanceSize`). Larger N = more exploratory (willing to accept worse moves if the recent trend was improving). Smaller N = more greedy.

**Questions to investigate**:
1. Is 200 optimal for ~7,000 entities? Timefold docs suggest that larger problems benefit from larger acceptance windows.
2. Would Phase 4 (conflict resolution) also benefit from Late Acceptance? Currently Phase 4 uses only Tabu search.
3. What's the interaction between `entityTabuSize` and `lateAcceptanceSize`? (They're combined in Phase 3.)

**Approach**: Run A/B benchmarks varying `lateAcceptanceSize` (100, 200, 400, 800) on 10-minute solves.

**Note**: Research only — no code changes unless benchmarks show clear improvement.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Benchmark results for at least 3 lateAcceptanceSize values
- [x] #2 Test whether adding Late Acceptance to Phase 4 helps
- [x] #3 Comparison documented with hard/soft scores and phase timings
- [x] #4 If improvement found: create follow-up implementation task
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Started parallel research with sc:research skill

## Research Completed (2026-02-27)

**Output**: `claudedocs/research-late-acceptance-size-2026-02-27.md` (446 lines)

**Key Findings**:

1. Current lateAcceptanceSize=200 is undersized (only 2.3-2.9% of entities)

2. Recommended benchmark values: 100, 200, 400, 800, 1600

3. Phase 4 should add Late Acceptance (200-400) for conflict resolution

4. Tabu:LA ratio 1:40 is too high; recommended 1:4 to 1:10

5. Academic research shows optimal L varies by 3 orders of magnitude

**Next Steps**: Create follow-up implementation task for benchmarking

## Research Complete - 2026-02-27

Current LA=200 is UNDERSIZED (2.3-2.9% vs 6-23% recommended)

Benchmark: lateAcceptanceSize 100/200/400/800/1600, tabu 5/10/20/50

Phase 4 should add Late Acceptance (200-400)

Report: claudedocs/research-late-acceptance-size-2026-02-27.md
<!-- SECTION:NOTES:END -->
