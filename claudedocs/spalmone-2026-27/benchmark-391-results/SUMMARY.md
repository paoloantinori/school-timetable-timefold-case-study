# TASK-391 phase-3 R&R ruin-count sweep — laptop results (heap 2g)

## A run1 — control (phase-3 ruin 2-5)
- final: -121 hard / -1,410,895 medium / +77,450 soft
- phase ends: p1 -57457, p2 -135, p3 -135 (R&R ruin 2-5 did NOT move hard), p4 -135, p5 -121
- solve time: ~84 min (07:47 -> 09:11)
- KPI: full-day 78.9% | unpinned recurrence 36.7% (below 38%) | blocks (see resemblance)
- HARD RESIDUAL: H15 -120 (1 same-day-same-subject non-consecutive) + H17 -1.
  **H3 (student group conflict) = 0. G05 knot = 0 stacks.** Premise (G05 H3 residual) ABSENT this run.
## R1 run1 — phase-3 ruin 5-20 (Timefold default)
- final: -66 hard / -1,460,130 medium / +75,136 soft
- phase ends: p2 -196, p3 -80 (R&R ruin 5-20 improved hard +116!), p5 -66
- solve time: ~72 min (09:17 -> 10:29)
- KPI: full-day 78.1% | recurrence 36.9% | blocks 2081
- HARD RESIDUAL: H15 -60 (1) + H17 -6 (6). H3=0, G05 knot=0 stacks.
- vs control: R1 phase-3 delta +116 vs control +0. R1 final -66 < control -121.
## R2 run1 — phase-3 ruin 10-40 (aggressive)
- final: -2182 hard / -1,465,158 medium / +79,350 soft  (WORST arm so far)
- phase ends: (see phases.json) — phase-3 ruin 10-40 ineffective, stuck near -2204
- solve time: ~69 min (10:32 -> 11:41)
- KPI: full-day 78.3% | recurrence 37.6% | blocks 2108
- HARD RESIDUAL: H3 -2000 (2 student-group conflicts, NON-G05 groups) + H15 -180 (2) + H17 -2. G05 knot still 0.
- phase-3 ruin 10-40 INERT (-2204 -> -2204, like control). COST confirmed: too-aggressive ruin is counterproductive.
## A run2 — control (phase-3 ruin 2-5)
- final: -1123 hard / -1,395,888 medium / +78,028 soft  (vs run1 -121 -> 10x variance!)
- phase ends: p2 -2138, p3 -1138 (R&R ruin 2-5 improved +1000 here, unlike run1's +0), p5 -1123
- KPI: full-day 78.3% | recurrence 36.2% | blocks 2121
- HARD RESIDUAL: H3 -1000 (1 NON-G05 conflict) + H15 -120 + H17 -3. G05 stacks=0.
- Control distribution {-121, -1123}: phase-3 inert in run1 (+0), productive in run2 (+1000). Unreliable.
## R1 run2 — phase-3 ruin 5-20
- final: -70 hard / -1,463,387 medium / +68,558 soft  (R1 = {-66, -70}: spread 4! RELIABLE)
- phase ends: p2 ~-2201 (bad), p3 rescued to ~-205 then phase jumps to -70. R&R ruin 5-20 RESCUED a bad phase-2.
- HARD RESIDUAL: (above) | G05 stacks: (above)
- VERDICT-signal: R1 reliably low both runs; control & R2 high-variance.
