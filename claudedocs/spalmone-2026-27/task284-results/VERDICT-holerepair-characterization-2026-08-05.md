# VERDICT: HoleRepair failure characterization (bad basin), 2026-08-05

**Question (from the synthesis, option 2):** is HoleRepair's ~10-hard contribution on the bad
basin its true ceiling, or does it miss closeable holes that refinement could recover? This
read-only analysis of the already-captured bad-basin hole data answers it. **NO ADOPTION**, no
new run (reuses the repair-ablation-badbasin captures).

**Data:** the bad-basin repair ablation (VERDICT-repair-ablation-badbasin-2026-08-05) captured
hole topology for baseline (HoleRepair ON, hard=-12) and hole-repair-off (HoleRepair OFF,
hard=-22). Comparing the two isolates exactly what HoleRepair closes vs where it is inert.

## What HoleRepair CLOSES (its ~10 contribution)

hole-repair-off has 22 internal holes; baseline has 12. The 10-hole difference is:
- **G08, 9 holes at noon (slot 12), Wednesdays 2026-11-10 through 2027-02-16.** These appear
  ONLY when HoleRepair is OFF (absent in baseline). HoleRepair closes them. They are plain
  mid-day breaks (occupied 8-11 + 13, hole at 12), the closeable kind HoleRepair is built for.
- 1 extra G07 hole at 10am (3 in hole-repair-off vs 2 in baseline), an LS-trajectory wobble.

So HoleRepair's ~10-hard contribution is essentially the **G08 noon holes** (closeable mid-day
breaks). It is doing its job on the closeable holes.

## Where HoleRepair FAILS (the -12 structural residual)

The baseline's 12 holes (present with HoleRepair ON, AND still present with HoleRepair OFF, i.e.
HoleRepair is INERT on them):
- **G11, 10 holes at 11am (slot 11), on 10 consecutive Wednesdays 2026-09-16 through
  2026-11-18.** occupied [8,9,10,12,13], hole at 11.
- **G07, 2 holes at 10am (slot 10), Wednesdays 2026-09-16, 09-23.** occupied [8,9,11,12,13],
  hole at 10.

These are EXACTLY the "G11 Wednesday @10/@11" structural shell-game of TASK-481: a mid-run
saturated slot whose blocker is edge-removable only by relocating the hole to another group (a
cascade HoleRepair and CompositionHoleCloser cannot close without shelling). They are in BOTH
runs, so HoleRepair does not touch them.

## Verdict: HoleRepair's ~10 is its ceiling; refinement is futile for the structural residual

HoleRepair already closes every CLOSEABLE mid-day hole (the G08 noon set). The remaining -12 is
the structural @10/@11 Wednesday shell-game, which NO local repair can close (TASK-481:
CompositionHoleCloser was built for it and is inert; the cascade is a shell-game). Refining
HoleRepair cannot recover the structural 12, because they are not a HoleRepair-class problem:
they are a CONSTRUCTION/basin problem (the GRASP seed-1 basin produces a Wednesday where the
11am slot for G11 is structurally unfillable without shelling the hole elsewhere).

**Implication: "refine HoleRepair" is RULED OUT as a hard-tuning direction.** HoleRepair is at
its ceiling. The structural residual is cured only by multi-start (basin selection, TASK-483
RATIFIED), which avoids the seed-1 basin rather than repairing it.

## What this removes from the synthesis

The synthesis (metaheuristic-campaign-synthesis-2026-08-05) listed "HoleRepair failure
characterization (read-only)" as recommended next step #2 and left open whether HoleRepair
refinement pays. This characterization CLOSES that question: it does not pay (ceiling confirmed).
The hard side is now fully characterized:
- structural residual (12 hard, the @10/@11 shell-game): multi-start, DONE (ratified).
- closeable residual (10 hard, the G08 noon holes): HoleRepair, DONE (at ceiling, default ON).

Remaining productive intervention for schedule QUALITY (not hard=0, which is solved):
acceptedCountLimit / TASK-296 (~5% medium, the one lever that moved the needle). Or declare the
metaheuristic + repair investigation complete.
