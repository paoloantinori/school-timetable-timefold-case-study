# VERDICT: TASK-284 TabuVsVND benchmark (proper vehicle, parallel), 2026-08-04

**Task:** TASK-284 (benchmark VND variants: neighborhood orderings, VND+Tabu hybrid).
**Vehicle:** the now-repaired Timefold 2.3.0 benchmark harness (see
`VERDICT-harness-repair-2026-08-04.md`), config `solverBenchmarkConfigTabuVsVND.xml`,
9 metaheuristic configs, run via `test.sh -c TabuVsVNDBenchmark`.
**Outcome:** benchmark completed (BUILD SUCCESS, 1463s wall with `<parallelBenchmarkCount>6`).
Clear ranking. **NO ADOPTION** (controlled LS benchmark on the old March dataset; relative
metaheuristic signal, not a production KPI).

## Run

- Parallel pool 6 (added `<parallelBenchmarkCount>6</parallelBenchmarkCount>` to the config;
  sequential was ~108 min, this is ~one wave). 9 single-threaded solvers, ~722-723s each,
  24 min wall. Memory safe: 6 x ~2g peaks under the 16g surefire heap (AUTO, all 9 at once,
  would risk OOM).
- Input: `local/data/schedule-production.json` (the static benchmark dataset, March 2025/26
  export, 10496 lessons). All 9 configs share the SAME construction (production
  `DayClusteredConstructionPhase`), differ ONLY in the LS acceptor/forager/move selectors.
- Single run (n=1). `succeeded=true` for all 9.

## Result (best-first; HardMediumSoftScore, higher is better)

| rank | config | hard | medium | soft | note |
|------|--------|-----:|-------:|-----:|------|
| 1 | VND-DiminishedReturns | -242 | -1568795 | -68038 | BEST hard |
| 2 | VND-CartesianProduct | -258 | -2366975 | -112587 | |
| 3 | VND-FirstImprovement | -284 | -1491159 | -65309 | best medium of the VNDs |
| 4 | Late-Acceptance | -290 | -1547637 | -61930 | |
| 5 | SA-Tabu-Hybrid | -322 | -1506785 | +28506 | |
| 6 | Tabu-Entity-Size5 | -6699 | -1487081 | +25471 | production-baseline cluster |
| 7 | Tabu-Entity-Size7 | -6740 | -1520623 | +25058 | current production acceptor |
| 8 | LA-Phase3-Tabu-Phase5 | -6756 | -1485356 | -32742 | |
| 9 | VND-HillClimbing | -7783 | -2404444 | -87179 | WORST hard |

## Findings

1. **Proper VND crushes Tabu on hard feasibility.** The three first-improvement VND
   variants (DiminishedReturns -242, CartesianProduct -258, FirstImprovement -284) and
   Late-Acceptance (-290) / SA-Tabu (-322) reach hard in the low hundreds, while the Tabu
   baselines (Size5/7) and the multi-algorithm LA+Tabu sit near -6700. That is roughly a
   25x difference in residual hard. On this problem the LS neighborhood-descent structure
   clears residual hard far better than Tabu short-term memory.

2. **Best-improvement is not VND; first-improvement is.** `VND-HillClimbing` (no acceptor,
   `acceptedCountLimit=1000`, i.e. BEST improvement) is the WORST config (-7783 hard),
   while `VND-FirstImprovement` (`acceptedCountLimit=1`, FIRST improvement) is #3. This
   matches the config's own research note and **consolidates the acceptedCountLimit finding**
   from the live sweep (`memory:metaheuristic-is-the-lever`): greedy / first-improvement /
   low-forager acceptance does more moves in the fixed budget and clears hard better. TASK-284
   and the ACL sweep tell the same story from two vehicles.

3. **The hard lever and the medium/soft levers are decoupled.** The VND variants win hard but
   several lose medium/soft (negative soft vs the Tabu baselines' positive soft rewards). On
   medium, VND-FirstImprovement (-1491159) is competitive with Tabu-Size5 (-1487081); on soft,
   SA-Tabu (+28506) and Tabu-Size5 (+25471) are best. So the metaheuristic trades hard-feasibility
   against medium/soft quality.

## Caveats (read before any adoption)

- **Old dataset.** `schedule-production.json` is the March 2025/26 export, not the current
  2026/27 campaign seed. Hard is nonzero for every config because the data predates parts of
  the current constraint set. These are RELATIVE metaheuristic numbers, not campaign KPIs.
- **Controlled LS benchmark, not the staged production pipeline.** The config runs the
  production construction then a combined timeslot+room LS, NOT the 5-phase staged cycle
  (B-TIMESLOT, demotions, HoleRepair, AnchorRepair, SurplusRebalance, B-ROOM, ExactFinisher,
  multi-start). So this does NOT contradict the campaign's ratification of Tabu-Entity-Size7
  in the staged pipeline (different setup, different dataset, different objective).
- **n=1.** Ranking direction is robust (the hard gap is huge), but per-config magnitudes are at
  the LS-variance floor.

## Follow-up (not adoption)

The signal "first-improvement / VND-style descent clears residual hard far better than Tabu"
is directly relevant to the campaign's open hard-0 struggle (the H17 bad-seed residual,
`TASK-480`/`TASK-481`). A controlled test of VND-FirstImprovement or a lower
`acceptedCountLimit` on the CURRENT staged pipeline + 2026/27 seed would test whether the
benchmark's hard advantage transfers to production. That is a new task, not this one.

## Artifacts

- Report: `local/benchmarkReport/2026-08-04_212831/` (`index.html`, `plannerBenchmarkResult.xml`).
- Config change: `<parallelBenchmarkCount>6</parallelBenchmarkCount>` added to
  `solverBenchmarkConfigTabuVsVND.xml` (makes the vehicle practical; reusable for any future
  metaheuristic benchmark).
