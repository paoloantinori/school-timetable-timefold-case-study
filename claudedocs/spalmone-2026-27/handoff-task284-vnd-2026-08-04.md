# Handoff - TASK-284 (VND benchmark) - 2026-08-04

**Why this file:** context was at ~491k (near the ~500k limit) when TASK-284 started. Resume
here after `/clear`. Shell processes (background sweeps) survive `/clear`. HEAD: `725ac45f`.

## Session so far (all committed + pushed to feature/cartesian-block-entity)

Doc audits + knob-sweep series. Commits in order:
- `9dd6bc20` branch-families.md accuracy audit
- `794010fc` solver-configurations.md (stale preAssignment pointer removed)
- `a201b6f5` benchmark-xsd-validation.md (dead preAssignment trigger)
- `7f7e61f5` m32.numerator sweep -> FLAT
- `a8a1df2b` m34.weight sweep -> FLAT
- `9c6b1e01` m35.weight sweep -> FLAT (cleanest)
- `834b62dd` tag-capacity.weight (M-TC) sweep -> FLAT (non-binding on good seed)
- `a16129df` knob-sweep-synthesis (5 medium weights flat; pivot to metaheuristic)
- `725ac45f` acceptedCountLimit live sweep -> **MOVES THE NEEDLE** (first non-flat knob)

## The key finding (metaheuristic IS the lever)

5 medium-constraint WEIGHT knobs are FLAT (score moves, timetable does not). But
acceptedCountLimit (forager breadth, metaheuristic) MOVES THE NEEDLE: acl=100 beats default
1000 (medium -1000570 vs -1051618, broad, faster 1314s vs 1592s, feasible); acl=10000 is
broken (-7498 H29 room-null). Validates the strategic pivot. Saved as memories
`medium-weight-knobs-flat` and `metaheuristic-is-the-lever`.

## TASK-284 work (the next task) + BLOCKER

TASK-284 (MEDIUM, backlog, To Do, depends TASK-280): "Benchmark VND variants - neighborhood
orderings, VND+Tabu hybrid". User wants to compare VND vs Tabu vs Late Acceptance via
`src/main/resources/solverBenchmarkConfig.xml` (15 configs after TASK-283 trim; config #15 is
"VariableNeighborhoodDescent", plus Tabu-Entity variants + LateAcceptance-Size variants).

**BLOCKER: the benchmark harness is BROKEN.** `run-benchmarker.sh` runs
`mvn test -Dtest=ComprehensiveLocalSearchBenchmark`, but pom.xml:172 has
`<testExclude>**/benchmark/**</testExclude>` because the benchmark test classes do NOT
compile under Timefold 2.3.0: `cannot find symbol method benchmarkAndShowReportInBrowser()`
(API drift) + the Jackson 3.x conflict in the comment. Harness last worked in March (Timefold
1.x). So `solverBenchmarkConfig.xml` CANNOT be run via run-benchmarker.sh as-is. (This is the
same blocker that forced the acceptedCountLimit sweep to run LIVE instead of via benchmark.)

## Options for TASK-284 (DECIDE on resume)

- **A. Live VND staged-sweep** (like acceptedCountLimit-sweep.sh): edit solverConfig.xml's
  `<acceptor>`/`<localSearch>` per variant (Tabu -> VND -> LateAcceptance), rebuild per
  variant, staged solve, capture, revert. MORE COMPLEX than acceptedCountLimit (VND/LA are
  acceptor-strategy changes, not a number; VND needs ordered move selectors). Pattern:
  `scripts/benchmark/acceptedCountLimit-sweep.sh` (portable sed + revert-trap + bake/mechanism
  verification). The 3 LS-phase acceptor in solverConfig.xml is `<entityTabuSize>7</entityTabuSize>`.
- **B. Fix the benchmark harness first** (code task: update ~8 benchmark test classes to
  Timefold 2.3.0 API `benchmarkAndShowReportInBrowser` -> `benchmark`; resolve Jackson 3.x;
  remove `<testExclude>`). Unblocks the proper benchmark vehicle for TASK-296 + TASK-284. Code
  change, not read-only.
- **C. Defer TASK-284.** The acceptedCountLimit result already confirms the metaheuristic
  pivot; VND is a secondary confirmation.

My recommendation (pre-/clear): **A (live VND sweep)** for consistency with acceptedCountLimit
+ production-representative, UNLESS the user prefers to fix the harness (B) as the proper
long-term vehicle. Confirm with the user on resume.

## Sweep driver pattern (reusable)

`scripts/benchmark/medium-knob-sweep.sh` (-D knobs, no rebuild) and
`scripts/benchmark/acceptedCountLimit-sweep.sh` (solverConfig.xml element, rebuild per value,
portable sed, revert-trap). Both: source=input, multi-start OFF (`-Dplanner.multistart.enabled=false`),
GRASP off, profile `m32-sweep` reused `--no-setup` (source=input pins persist), boot via
`PLANNER_JAVA_OPTS=... ./planner.sh prod m32-sweep`, capture via `./scripts/benchmark/capture-run.sh`.
Verify -D arrived via "Multi-start staged retry: enabled=false" + "grasp=false" banners.

## DoD (eternal)

capture raw (capture-run.sh), `/simplify` + `/code-review high` on any new script (skeptic
agent was UNAVAILABLE all session - GLM auto-mode classifier down - so reviews ran inline +
were empirically validated by the runs; retry skeptic when classifier recovers), test.sh N/A
for bash/docs. commit+push raw + VERDICT. NO ADOPTION.

## Resume steps
1. `/clear`, read this file.
2. Decide A/B/C with the user (or proceed with A if "Vai").
3. For A: the VND acceptor config is the work - check how solverBenchmarkConfig.xml config
   #15 "VariableNeighborhoodDescent" defines its `<localSearch>` (the VND pattern to mirror
   into solverConfig.xml), then write a live driver (copy acceptedCountLimit-sweep.sh shape,
   edit the acceptor block per variant instead of a number).
