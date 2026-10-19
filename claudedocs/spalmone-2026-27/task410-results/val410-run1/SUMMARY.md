# val410-run1 — fresh dev solve, cumulative stack (Q22 + 406 + 409 + 410)
Score: -10hard / -1,782,813medium / +60,889soft  (solve time ~70min)

## Gate verdict: FAIL (most levers)
| metric | run1 | gate | 406 baseline | verdict |
|---|---|---|---|---|
| hard | -10 (H0 box 9 + H17 1) | <=-1 H17-only | 0/-1 | FAIL |
| box (H0) | 9 | 0 | 0 | FAIL |
| full-day | 78.3% (1875/2395) | >=~79% | 79.1-79.3% | marginal FAIL |
| day-start | 94.0% (2081/2213) | >=~95% | 95.8-96.1% | marginal FAIL |
| M20 | -938k / 408 | toward -786k | -854/-878k | FAIL |
| M20.2 empty-expected-day | -177k / **32 days** | ~0 (golden 2) | (409 target ~0) | FAIL |
| M20+M20.2 | -1,115k | toward -786k | — | FAIL |
| recurrence unpinned | 36.8% (3538/9626) | ~35 floor (dip ok, flag <34) | 33-35% | PASS |
| blocks >=2h | 2167 | toward ~2100 | ~2000 | OK |
| G05 knot | 0 (PASS) | 0 | — | PASS |

## Medium breakdown (full)
M20 -938k/408, M22 -576k/13 (TASK-408 lever, unchanged), M20.2 -177k/32, M15 -67k/21,
M28 -9.4k/94, M18 -8.5k/132, M12 -6.5k/222, M30.1 -54/40, M30.2 -33/8, M31 -39/36,
M21 -33/33, M25 -20/20, M24 -15/13, M29 -15/15, M27 -5/5, M26 -1/1, M5 +19/40.

## Key signal
M20.2 = 32 EMPTY expected days (golden has 2; TASK-409 target ~0). These are zero-slack
unfillable days (M20.2 makes 0h cost MORE than 1h, so the solver would put 1h if it could) ->
strong over-constraint signal from the cumulative stack (Q22 teacher-day blocks + 410 stage
concentration). Box=9 (H0) confirms placement pressure: 9 lessons unplaceable, BoxRepair
phase-4 could not clear them (task406 cleared box to 0).

NOTE: n=1 only. laptop-RUNBOOK warns ~9x hard variance; run 2 is mandatory before verdict.
