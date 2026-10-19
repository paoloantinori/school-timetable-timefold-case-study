# VERDICT - final-confirm (post-m12 certification run, 2026-07-28)

Fresh dev profile, staged solve, config as-is: TASK-433 AnchorRepair ON (unconditional,
adopted this morning commit 1962b6f6), TASK-430/432 OFF (default), TASK-434 fix-16 applied
(commit fdcf3d35: G04 16h beyond-horizon stage routed to expectedUnscheduledHours).
No extra -D flags. Solve wall-clock ~1h54m (08:49 -> ~10:43).

## Score
-41hard / -1,094,242medium / -773soft

## ATTESA vs actual

| metric | ATTESA | actual | verdict |
|---|---|---|---|
| hard | 0 | -41 | NOT MET - residual = T01 (TASK-435) |
| buchi interni | 0 | 0 | MET |
| day-start | >=99.4% | 99.3% (2197/2213, 16 late) | ~golden-parity, 3 late-days over the bar |
| emptySpan G04 | <=1 | 0/0 | MET (better than gate2's 1) |
| full-day | ~78.5% | 77.2% (1849/2395) | -1.3pp |
| ricorrenza unpinned | >=72% | 72.7% (6977/9594) | MET |

## APPLICABILITY scoreboard (run/golden, per group)
- holes: 0 everywhere (TOTAL 0/1). buchi interni = 0 days / 0 slots.
- lateViol: TOTAL 16/0 (G11 13, G13 3, rest 0). The 16 late = the day-start 99.3%.
- emptySpan: 0 everywhere (G04 0/0). The fix-16 + 433 drove G04 emptySpan 9 -> 1 (gate2) -> 0 here.
- lateLegit: TOTAL 14/17 (G08 2/5, rest 1/1; G14 n/d).

## Certification (honest)
The adopted config (433 + fix-16) HOLDS the applicability / soft-medium legs: buchi 0,
emptySpan G04 0, day-start ~99.3% (golden ~99.3-100%), ricorrenza 72.7%, full-day 77.2%.
The hard-zero target is NOT met: the 41 H0 boxed are T01's "Servizio tecnico e
stilistico" (231h, Thursday-only, 4 groups) - the structural contention the per-group
construction cannot resolve and the LS cannot repair (demotion was ineffective, gate2).
This is consistent with the morning prediction (fix-16 removed 16 of the -56 baseline ->
~-40 residual). The residual is exactly TASK-435 territory.

## Implication for TASK-435
Confirmed root cause + sharpened fix. The confirm run proves the -41 is construction-bound
(metaheuristics will not crack it - the LS already failed, demotion ineffective), and the
boxed are one teacher's course across 4 contending groups. TASK-435 (redesigned this morning
as pre-assignment PINNING per the human-planner "pin them early" principle, human-planner-
heuristics.md line 68) is the targeted lever: pin T01 one-group-per-Thursday before the
solver runs. Acceptance gate for 435 includes this run's -41hard -> 0.

## Artifacts
score.txt, analysis.json, phases.json, export.json.gz, lessons.txt, kpi-{fill,daystart,
resemblance,holes,moda-knot,cadence,sharpness}.txt, confronto-final-confirm.html (golden-vs-
run viz + APPLICABILITY scoreboard), teacher-fingerprint.txt, phase-markers-log.txt.
