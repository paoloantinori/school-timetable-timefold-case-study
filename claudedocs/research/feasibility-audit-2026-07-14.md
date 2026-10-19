# Feasibility Audit — 2026-07-14

**Profile:** dev  |  **School year:** 2025-09-10 .. 2026-06-10  |  **Total slots:** 1137  |  **School-day slots (non-holiday):** 1004  |  **Holidays:** 34

Capacity is the **maximum (most generous)** per resource — stage blackouts and resource conflicts are NOT subtracted. So any row where **required > available** is infeasible by construction, and the gap is a lower bound on hours that cannot be placed.

## Verdict: INFEASIBLE — 0 hard / 0 unassigned is NOT achievable by construction

Loose lower bound on unplaceable hours (sum across infeasible rows; dimensions overlap): **33**

## Per teacher (required hours vs available slots)
| Teacher | Required (h) | Available (slots) | Gap | External |
|---|---|---|---|---|
| T28 | 198 | 210 | -12 | True |
| T08 Scilla V. F. | 680 | 1004 | -324 | False |
| T15 | 621 | 1004 | -383 | False |
| T20 | 618 | 1004 | -386 | False |
| T06 | 587 | 1004 | -417 | False |
| T25 | 558 | 1004 | -446 | False |
| T09 | 535 | 1004 | -469 | False |
| T26 | 525 | 1004 | -479 | False |
| T23 | 500 | 1004 | -504 | False |
| T02 | 490 | 1004 | -514 | False |
| T16 | 486 | 1004 | -518 | False |
| T04 | 485 | 1004 | -519 | False |
| T13 | 435 | 1004 | -569 | False |
| T14 | 425 | 1004 | -579 | False |
| T11 | 410 | 1004 | -594 | False |
| T05 | 395 | 1004 | -609 | False |
| T03 | 393 | 1004 | -611 | False |
| T21 | 370 | 1004 | -634 | False |
| T18 | 355 | 1004 | -649 | False |
| T17 | 354 | 1004 | -650 | False |
| T30 Clara Valentina | 320 | 1004 | -684 | False |
| T31 | 235 | 1004 | -769 | False |
| T10 | 162 | 1004 | -842 | False |
| T12 | 140 | 1004 | -864 | False |
| TBD4 | 100 | 1004 | -904 | False |
| T29 | 80 | 1004 | -924 | False |
| T07 | 79 | 1004 | -925 | False |
| TBD2 | 50 | 1004 | -954 | False |
| TBD3 | 40 | 1004 | -964 | False |
| T27 | 30 | 1004 | -974 | False |
| T01 | 0 | 1004 | -1004 | False |

## Per student group (required hours vs available slots, capped by maxHoursPerDay)
| Group | Required (h) | Available (slots) | Gap | Cap/day |
|---|---|---|---|---|
| G04 | 1037 | 1004 | 33 | 6 |
| G03 | 990 | 1004 | -14 | 6 |
| G02 | 990 | 1004 | -14 | 6 |
| G06 | 990 | 1004 | -14 | 6 |
| G01 | 990 | 1004 | -14 | 6 |
| G05 | 990 | 1004 | -14 | 6 |
| G07 | 695 | 865 | -170 | 5 |
| G13 | 683 | 865 | -182 | 5 |
| G11 | 671 | 865 | -194 | 5 |
| G08 | 666 | 865 | -199 | 5 |
| G12 | 657 | 865 | -208 | 5 |
| G10 | 654 | 865 | -211 | 5 |
| G09 | 643 | 1004 | -361 | 6 |

## Per room tag (required lab hours vs rooms × slots)
| Tag | Required (h) | Available (slots) | Gap | # Rooms |
|---|---|---|---|---|
| moda | 898 | 1004 | -106 | 1 |
| acconciatura | 764 | 1004 | -240 | 1 |
| termoidraulica | 641 | 1004 | -363 | 1 |
| meccanica | 520 | 1004 | -484 | 1 |
| informatica | 646 | 2008 | -1362 | 2 |

## Drill-down: infeasible groups (excess over peer median + largest courses)
**G04** — required 1037h vs available 1004 (gap +33); peer median for cap 6/day = 990h; excess over peer median = **47h** (an excess over peer median often pinpoints a data discrepancy — TASK-337).
  - Lavorazioni meccaniche d'officina — 300h (lab)
  - Lingua italiana — 90h
  - Matematica — 70h
  - Lingua inglese — 60h
  - Scienze — 60h
  - Taglio metalli — 60h (lab)
  - Disegno meccanico — 52h
  - Recuperi e approfondimenti — 50h
  - Larsa — 47h
  - Informatica — 40h (lab)

## T28 / G10 self-test
T28 (external): required **198h**, available **210 slots**, gap **-12** -> FEASIBLE at full-year scope — the documented IMPOSSIBLE status (11 blocks / 9 slots) is period-scoped or stale.

_Method: scripts/analyze/feasibility-audit.py via scripts/db/db-query.sh. Data fixes belong to TASK-337 (m-6), not here._
