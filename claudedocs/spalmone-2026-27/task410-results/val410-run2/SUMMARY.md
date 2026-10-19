# val410-run2 — fresh dev solve, cumulative stack (Q22 + 406 + 409 + 410)
Score: -12hard / -1,798,089medium / +58,777soft  (solve time ~67min, full 6-phase pipeline)

## Gate verdict: FAIL (same levers as run1 — systematic, not variance)
| metric | run2 | gate | run1 | 406 baseline | verdict |
|---|---|---|---|---|---|
| hard | -12 (H0 box 11 + H17 1) | <=-1 H17-only | -10 | 0/-1 | FAIL |
| box (H0) | 11 (G06 10, G01 1) | 0 | 9 | 0 | FAIL |
| full-day | 77.6% (1858/2395) | >=~79% | 78.3% | 79.1-79.3% | marginal FAIL |
| day-start | 94.8% (2102/2217) | >=~95% | 94.0% | 95.8-96.1% | marginal FAIL |
| M20 | -958k / 424 | toward -786k | -938k/408 | -854/-878k | FAIL |
| M20.2 empty-expected-day | -167k / 31 | ~0 (golden 2) | -177k/32 | (new) | FAIL |
| M20+M20.2 | -1,125k | toward -786k | -1,115k | — | FAIL |
| recurrence unpinned | 36.5% | ~35 floor | 36.8% | 33-35% | PASS |
| G05 knot | 0 (PASS) | 0 | 0 | — | PASS |

## Per-lever diagnostic (empty expected days by group)
M20.2 = 31 empty days: **G04 18**, G12 6, G08 4, G09 2, G13 1.
Dominated by G04 (quota group, NO Stage course -> TASK-410 does not touch it; pre-existing
structural floor per CLAUDE.md: bound demand 222h < quota 245h).
Box (H0=11): G06 10, G01 1 (G06 is non-stage -> TASK-410 does not touch it).

## Teacher fingerprint (Q22 working)
T01 100% Thu (222h, golden 231h=100% Thu). T07 100% Wed (72h, golden 72h=100% Wed).
T19 Mon+Tue, T17 Tue/Wed/Thu — all match the golden's concentration pattern.
