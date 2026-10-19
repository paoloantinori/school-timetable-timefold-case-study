# TASK-386 metaheuristic benchmark — laptop results

## Arm A — control (entityTabu 7, acceptedCountLimit 1000)
- final: -1062 hard / -1,452,018 medium / +76,594 soft
- phase-2 end: -2198 hard / -1,449,195 medium / +58,539 soft
- phase-3 end: -1077 hard (ruin-recreate heavy lift -2198 -> -1077)
- solve time: ~78 min (15:42 -> 17:00)
- KPI: full-day 78.0% | unpinned recurrence 37.1% | blocks>=2h 2077
## Arm E — entityTabu 11, acceptedCountLimit 1000
- final: -1001 hard / -1,459,726 medium / +75,796 soft
- phase-2 end: -2019 hard / -1,460,785 medium / +54,820 soft
- phase-3 end: -1020 hard (ruin-recreate -2019 -> -1020)
- solve time: ~71 min (17:05 -> 18:16)
- KPI: full-day 78.4% | unpinned recurrence 36.0% | blocks>=2h 2040
## Arm C — entityTabu 7, acceptedCountLimit 500
- final: -123 hard / -1,486,294 medium / +79,494 soft
- phase-2 end: -1139 hard / -1,484,159 medium / +59,692 soft  (<< A -2198 / E -2019)
- phase-3 end: -138 hard (ruin-recreate -1139 -> -138)
- solve time: ~76 min (18:18 -> 19:34)
- KPI: full-day 78.2% | unpinned recurrence 37.0% | blocks>=2h 2048
- NOTE: hard leader by ~10x; KPIs intact (not degenerate). PRIME RE-RUN candidate (stability).
## Arm D — entityTabu 7, acceptedCountLimit 2000
- final: -302 hard / -1,416,817 medium / +73,823 soft
- phase-2 end: -317 hard / -1,420,107 medium / +50,608 soft  (BEST phase-2 of all arms)
- phase-3 end: -317 (ruin-recreate could NOT improve hard)
- phase-5 end: -302 (room LS -317 -> -302)
- solve time: ~79 min (19:36 -> 20:55)
- KPI: full-day 78.2% | unpinned recurrence 37.8% (best) | blocks>=2h 2024
- NOTE: best phase-2 convergence + best medium + best recurrence. Well-rounded. RE-RUN candidate.
## Arm F — entityTabu 7, acceptedCountLimit 1000, NO pillar moves (change/swap only)
- final: -303 hard / -1,437,493 medium / +80,377 soft (best soft)
- phase-2 end: -1320 hard / -1,435,583 medium / +61,292 soft
- phase-3 end: -321 hard (ruin-recreate -1320 -> -321)
- solve time: ~74 min (20:57 -> 22:11)
- KPI: full-day 77.8% | unpinned recurrence 39.0% (ONLY arm clearing ~38%) | blocks>=2h 2117 (most)
- NOTE: no-pillars ~tied D on hard; best recurrence + best soft. Pillar removal did NOT hurt. RE-RUN candidate.
## Arm B — lateAcceptanceSize 400, acceptedCountLimit 1000
- final: -128 hard / -1,651,738 medium / -88,555 soft (worst medium+soft)
- phase-2 end: -387 hard / -1,731,657 medium / -147,423 soft (2nd-best phase-2!)
- phase-3 end: -192 hard (ruin-recreate -387 -> -192); phase-5: -128
- solve time: ~127 min (22:13 -> 00:20) — LONGEST run
- KPI: full-day 66.9% (BELOW ~74% threshold -> KPI COLLAPSE) | recurrence 45.4% | blocks>=2h 2086
- VERDICT: DISQUALIFIED by criterion #4 (won hard by shredding daily-fill). Late-acceptance family OUT.

## Re-run C (run 2) — entityTabu 7, acceptedCountLimit 500
- final: -1002 hard / -1,418,015 medium / +79,512 soft  (9x worse than run-1's -123!)
- phase-2 end: -2152 hard (run-1 was -1139); phase-3 only reached -1024 (no lucky -138 basin)
- solve time: ~74 min (00:22 -> 01:36)
- VERDICT: C's run-1 -123 was a LUCKY OUTLIER. C distribution {-123, -1002}. acl 500 NOT reliably better than control.

## Re-run D (run 2) — entityTabu 7, acceptedCountLimit 2000
- final: -1541 hard / -1,468,260 medium / +77,316 soft  (5x worse than run-1's -302!)
- phase-2 end: -2624 hard (run-1 was -317); phase-3: -2624 -> -1563
- solve time: ~68 min (01:38 -> 02:46)
- VERDICT: D's run-1 -302 phase-2 was LUCKY. D phase-2 {-317, -2624} high-variance. NOT reproducible.
