---
id: TASK-296
title: Tune acceptedCountLimit per LS phase
status: Done
assignee: []
created_date: '2026-03-22 05:39'
updated_date: '2026-08-05 06:13'
labels:
  - claude-planned
  - ls-optimization
  - solver
milestone: solver-optimization
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Tune `acceptedCountLimit` in the `<forager>` element for each LS phase in `solverConfig.xml`, replacing the current uniform value of 1000.

## Why This Matters

The `acceptedCountLimit` controls how many candidate moves the forager evaluates before accepting one. It's the primary knob for exploration vs exploitation:
- **Low values (1-4)**: Greedy — accept the first improving move found. Fast per step, but may miss better moves.
- **Medium values (100-1000)**: Balanced — evaluate many candidates, accept the best. Slower per step but better exploration.
- **High values (1000+)**: Extensive — evaluate many candidates. Very slow per step.

Currently all phases use `acceptedCountLimit=1000`. The old `benchmark/solverConfig-quality.xml` used different values per phase:
- Phase 3 (Compactness): `acceptedCountLimit=1` (greedy)
- Phase 4 (Conflict): `acceptedCountLimit=4`
- Phase 5 (Room Refinement): `acceptedCountLimit=1` (greedy)

**Hypothesis**: For Phase 3 (compactness), greedy acceptance may work better because:
1. Compactness has clear local gradients — the first improving move is usually good enough
2. With 1000 evaluations per step, most are wasted on moves that won't improve compactness
3. The extra time spent evaluating 1000 moves could instead be used for more steps

For Phase 5 (room refinement), greedy may also work well since room options are limited (tag filtering reduces the set significantly).

## Implementation

Update `<forager>` in each LS phase in `solverConfig.xml`:

```xml
<!-- Phase 3: Greedy for compactness -->
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>

<!-- Phase 5: Greedy for room refinement -->
<forager>
  <acceptedCountLimit>1</acceptedCountLimit>
</forager>
```

## Verification

Run a benchmark comparing the current uniform `1000` against per-phase tuned values. Measure:
- Final score (hard/medium/soft)
- Moves per second (should increase significantly with lower acceptedCountLimit)
- Total improvement trajectory over time

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Forager configuration best practices
- Historical: `src/main/resources/benchmark/solverConfig-quality.xml` — Previous config with per-phase acceptedCountLimit values (1, 4, 1)
- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — First-improvement vs best-improvement strategies
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 3 forager acceptedCountLimit tuned (recommended: 1, greedy)
- [ ] #2 Phase 5 forager acceptedCountLimit tuned (recommended: 1, greedy)
- [ ] #3 solverConfig.xml validates successfully (validate-solver-xml.sh)
- [ ] #4 Benchmark: compare final scores with current acceptedCountLimit=1000 vs per-phase tuned values
- [ ] #5 Benchmark: measure moves-per-second improvement (expected 2-10x increase)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED OUT (no adoption), 2026-08-05. The finer ACL sweep (50/200/500 + reused 100/1000) found NO robust sweet spot: the medium-vs-ACL curve is state-dependent and non-monotonic at n=1 (3090-pin state: lower ACL better; 2907-pin state: higher ACL better), and the ~5% medium range ACL spans sits at the ~6% pre-assign run-to-run variance floor, so the effect is not robust. ACL does NOT move hard (all feasible 50-1000 ~0; only 10000 breaks, -7498 H29). ACL is not a reliable tuning lever and not an adoption candidate. Default acceptedCountLimit=1000 stands. The per-phase tuning hypothesis (greedy acl=1 for compactness/room) is subsumed: VND-FirstImprovement (acl=1, no acceptor) crushes Tabu on the controlled LS-only benchmark but is INERT on the staged production pipeline (staged hard=0 = Tabu), so the gain does not transfer. Verdict: claudedocs/spalmone-2026-27/task296-results/VERDICT-task296-2026-08-05.md. Synthesis addendum: metaheuristic-campaign-synthesis-2026-08-05.md (commit 4ca98324).
<!-- SECTION:FINAL_SUMMARY:END -->
