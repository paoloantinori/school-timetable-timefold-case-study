# VERDICT: VND-FirstImprovement on the STAGED pipeline (2026/27 seed), 2026-08-04

**Question (from the TASK-284 follow-up):** the controlled TabuVsVND benchmark (old March
dataset, LS-only) showed VND-FirstImprovement reaches hard -284 vs Tabu-Size7 -6740 (~25x
better hard). Does that advantage transfer to the real STAGED production pipeline on the
current 2026/27 source=input seed? This is the test that decides whether VND-FI is an adoption
candidate. **NO ADOPTION** (Tabu-Size7 stays ratified; this is a comparison).

**Setup:** `scripts/benchmark/vnd-fi-staged-ab.sh vndfi-ab --setup`. Single variable = the LS
acceptor/forager (both LS phases), everything else fixed:
- `tabu-baseline`: `<entityTabuSize>7</entityTabuSize>` + `<acceptedCountLimit>1000</acceptedCountLimit>` (current production).
- `vnd-fi`: no `<acceptor>` (hill climbing, only improving moves) + `<acceptedCountLimit>1</acceptedCountLimit>` (first improvement).
- Both: source=input, multi-start OFF, GRASP OFF (single deterministic solve), same staged
  pipeline (CH + LS + HoleRepair/AnchorRepair/SurplusRebalance + ExactFinisher + B-ROOM).

## Ex-ante prediction (written BEFORE the run outcome, 2026-08-04, pre-registered)

The controlled-benchmark hard gap (25x) will **only partially transfer**, and may not transfer
to hard at all, because the staged pipeline changes the landscape in three ways:

1. **Repair saturation.** The staged cycle has deterministic repair passes (HoleRepair,
   AnchorRepair, SurplusRebalance) plus ExactFinisher that close residual hard box-by-box. On
   the staged pipeline, hard is largely cleared by REPAIR, not by the LS acceptor. So the LS
   acceptor's hard-clearing advantage (the whole signal in the LS-only benchmark) may be inert
   here: both variants could land at similar hard because repair dominates.
2. **Different seed.** The 2026/27 source=input seed has a different hard structure than the
   March dataset (which had unavoidable hard from predating parts of the constraint set).
3. **Hill-climbing stagnation risk.** VND-FI drops Tabu short-term memory. On the staged LS
   (with the production move set + diminishedReturns), hill climbing + first-improvement could
   cycle or stagnate, hurting medium/full-day/recurrence EVEN IF hard is fine. The controlled
   benchmark could not show this (it had no staged repair and a different objective balance).

**Concrete predictions:**
- P1 (transfer): if the LS acceptor IS binding on hard here, `vnd-fi` hard < `tabu-baseline`
  hard, ideally with medium/full-day not regressed.
- P2 (saturation, more likely): `vnd-fi` hard ~= `tabu-baseline` hard (repair saturates the
  hard-clearing), but `vnd-fi` medium/full-day/recurrence REGRESS (no tabu memory, greedier
  descent lands in a worse soft basin).
- P3 (null): no meaningful difference on any KPI (the acceptor is not the lever on the staged
  pipeline at this seed/budget).

Most likely outcome: **P2** (repair saturates hard; VND-FI trades soft quality for nothing,
because the hard advantage it buys on the LS-only benchmark is already provided by repair on
the staged pipeline). That would be an honest "does not transfer" result and would keep
Tabu-Size7 as the right production acceptor.

## Outcome

Run completed (background `btroqwof5`, fresh `vndfi-ab` profile, source=input, multi-start OFF,
both staged solves single-deterministic). solverConfig.xml reverted (Tabu-Size7 stands).

| variant | hard | medium | soft | wall | full-day | resemblance unpinned | day-start anchored |
|---------|-----:|------:|----:|-----:|---------:|---------------------:|-------------------:|
| tabu-baseline | 0 | -1051561 | +4301 | 1600s | 87.1% | 74.1% | 99.5% |
| vnd-fi | 0 | -1002707 | +3083 | 2356s | 88.0% | 73.5% | 98.6% |

**Verdict: prediction P2 (saturation) CONFIRMED. VND-FI is NOT an adoption candidate;
Tabu-Size7 stays ratified.**

- **Hard is tied at 0 for both.** The 25x hard gap from the controlled LS-only benchmark DOES
  NOT TRANSFER. On the staged pipeline the deterministic repair passes (HoleRepair,
  AnchorRepair, SurplusRebalance, ExactFinisher) clear hard on their own; the LS acceptor is
  inert on hard. The controlled benchmark was misleading specifically because it had no repair,
  so it attributed hard-clearing to the acceptor when repair owns it in production.
- **VND-FI is a mixed trade-off, not dominant.** It is marginally better on medium (-1002707 vs
  -1051561, ~49k / ~4.7%) and full-day (88.0 vs 87.1, +0.9pp), but marginally worse on soft
  (+3083 vs +4301), recurrence unpinned (73.5 vs 74.1, -0.6pp), day-start anchored (98.6 vs 99.5,
  -0.9pp), and **47% slower** (2356s vs 1600s). First-improvement (acl=1) does many more steps.
- **Consistency with prior sweeps.** tabu-baseline medium -1051561 reproduces the known
  production acl=1000 number; vnd-fi -1002707 is close to the acl=100 finding (-1000570). Same
  story from a third angle: greedier / lower-forager acceptance buys a little medium at more wall
  time, and the effect is small (~5% medium) on the staged pipeline.

## Conclusion

On the staged production pipeline the LS acceptor/forager is a **weak lever**: it fine-tunes
medium/soft (~5% medium swing) but does not move hard (repair owns hard to 0). VND-FI's marginal
medium/full-day gains are not worth its soft/recurrence/day-start regressions plus 47% more wall
time, and it brings zero hard benefit. **Tabu-Size7 remains the right production acceptor.**

This also re-frames the metaheuristic-lever story: "metaheuristic is the lever" holds for MEDIUM
and for the LS-only / pre-repair landscape, but on the staged pipeline the orchestration layer
(repair + ExactFinisher + multi-start) is what secures hard=0. The acceptor is a soft-quality
tuning knob, not a feasibility lever. The controlled TabuVsVND benchmark remains useful for
relative metaheuristic ranking, but its hard numbers must not be read as production-feasibility
predictions (it has no repair).

NO ADOPTION. Follow-up closed: VND-FI tested on the staged pipeline and ruled out. The remaining
open hard-0 lever on the staged pipeline is the construction/multi-start basin selection
(TASK-480/481), not the LS acceptor.
