# Feasibility Audit — 2026-08-16

**Profile:** dev  |  **School year:** 2026-09-14 .. 2027-06-10  |  **Total slots:** 1368  |  **School-day slots (non-holiday):** 1368  |  **Holidays:** 29

Capacity is the **maximum (most generous)** per resource — stage blackouts and resource conflicts are NOT subtracted. So any row where **required > available** is infeasible by construction, and the gap is a lower bound on hours that cannot be placed.

## Verdict: FEASIBLE on these dimensions — no structural lower-bound violation found

No teacher, group, or room-tag dimension exceeds its maximum capacity. This does NOT prove a 0-hard solution exists (conflicts, stage rules, and block contiguity still bite), but it removes 'infeasible by construction' as an explanation.

## Per teacher (required hours vs available slots)
| Teacher | Required (h) | Available (slots) | Gap | External |
|---|---|---|---|---|
| T01 | 231 | 288 | -57 | False |
| T07 | 72 | 280 | -208 | False |
| T24 | 190 | 528 | -338 | False |
| T19 | 128 | 528 | -400 | False |
| T05 | 496 | 1096 | -600 | False |
| T04 | 485 | 1096 | -611 | False |
| T22 | 178 | 816 | -638 | False |
| T12 | 125 | 800 | -675 | False |
| T21 | 660 | 1368 | -708 | False |
| T17 | 393 | 1104 | -711 | False |
| T08 | 640 | 1368 | -728 | False |
| T06 | 601 | 1368 | -767 | False |
| T25 | 564 | 1368 | -804 | False |
| T09 | 555 | 1368 | -813 | False |
| T02 | 530 | 1368 | -838 | False |
| T15 | 526 | 1368 | -842 | False |
| T20 | 524 | 1368 | -844 | False |
| T23 | 520 | 1368 | -848 | False |
| T16 | 495 | 1368 | -873 | False |
| T13 | 440 | 1368 | -928 | False |
| T11 | 425 | 1368 | -943 | False |
| T14 | 400 | 1368 | -968 | False |
| T18 | 385 | 1368 | -983 | False |
| T03 | 373 | 1368 | -995 | False |
| T26 | 340 | 1368 | -1028 | False |
| T10 | 225 | 1368 | -1143 | False |

## Per student group (required hours vs available slots, capped by maxHoursPerDay)
| Group | Required (h) | Available (slots) | Gap | Cap/day |
|---|---|---|---|---|
| G01 | 990 | 1026 | -36 | 6 |
| G02 | 990 | 1026 | -36 | 6 |
| G03 | 990 | 1026 | -36 | 6 |
| G05 | 990 | 1026 | -36 | 6 |
| G04 | 990 | 1368 | -378 | 8 |
| G06 | 990 | 1368 | -378 | 8 |
| G07 | 990 | 1368 | -378 | 8 |
| G13 | 978 | 1368 | -390 | 8 |
| G10 | 970 | 1368 | -398 | 8 |
| G12 | 970 | 1368 | -398 | 8 |
| G09 | 966 | 1368 | -402 | 8 |
| G11 | 966 | 1368 | -402 | 8 |
| G08 | 926 | 1368 | -442 | 8 |
| G14 | 115 | 855 | -740 | 5 |

## Per room tag (required lab hours vs rooms × slots)
| Tag | Required (h) | Available (slots) | Gap | # Rooms |
|---|---|---|---|---|
| moda | 828 | 1368 | -540 | 1 |
| acconciatura | 659 | 1368 | -709 | 1 |
| meccanica | 402 | 1368 | -966 | 1 |
| termoidraulica | 313 | 1368 | -1055 | 1 |
| saldatura | 40 | 1368 | -1328 | 1 |
| informatica | 594 | 2736 | -2142 | 2 |

## TASK-506 dimension 4: quota vs calendar window per (group, weekday) [H20 family]
INFEASIBLE class: the school's own sheets demand more hours on a weekday than the group's open-slot window.
| Group | Date | Quota (h) | Window (slots) | Excess |
|---|---|---|---|---|
| (none) | - | - | - | - |

## TASK-506 dimension 5: course pool vs calendar plan per group [835-vs-750 family]
QUESTION class (workbook referente): pool > plan means M20/M33 tension (OVERDEMAND note); plan > pool means the solver will underfill the calendar (the G04 shape).
| Group | Pool (incarichi h) | Plan (expected h) | Delta |
|---|---|---|---|
| G04 | 750 | 835 | -85 |
| G08 | 626 | 666 | -40 |
| G03 | 990 | 969 | 21 |
| G07 | 695 | 690 | 5 |
| G14 | 115 | 120 | -5 |
| G09 | 666 | 670 | -4 |
| G10 | 670 | 666 | 4 |

## TASK-506 dimension 6: H30 deadline (Sicurezza vs pre-stage capacity)
INFEASIBLE when the hours cannot fit before the stage starts (generous capacity: no conflicts subtracted).
| Group | Sicurezza (h) | Pre-stage capacity | Stage start | Gap |
|---|---|---|---|---|
| G07 | 12 | 960 | 2027-03-23 | -948 |

## TASK-506 dimension 7: H25 (stage hours vs stage-day capacity, off-grid aware)
OVERDEMAND when IN-GRID demand exceeds in-grid capacity. Off-grid declared stage days (beyond the grid end) are separate: the golden places exactly their hours there (G04: 2 days, 16h, on 2027-06-11/14), expected-unscheduled, not overdemand.
| Group | Stage (h) | # Off-grid d | Off-grid h | # In-grid d | In-grid cap | Gap |
|---|---|---|---|---|---|---|
| G04 | 240 | 2 | 16 | 28 | 224 | 0 |
| G11 | 295 | 0 | 0 | 37 | 296 | -1 |
| G13 | 295 | 0 | 0 | 37 | 296 | -1 |
| G08 | 300 | 0 | 0 | 38 | 304 | -4 |
| G10 | 300 | 0 | 0 | 38 | 304 | -4 |
| G09 | 300 | 0 | 0 | 38 | 304 | -4 |
| G07 | 295 | 0 | 0 | 38 | 304 | -9 |
| G12 | 295 | 0 | 0 | 43 | 344 | -49 |

## TASK-506 dimension 8: lab demand vs lab-quota days [H23/H24]
OVERDEMAND when the group's lab-tagged hours exceed (lab-quota days x cap/day).
| Group | Lab hours | # Lab days | Capacity | Gap |
|---|---|---|---|---|
| G04 | 212 | 53 | 424 | -212 |
| G13 | 210 | 47 | 376 | -166 |
| G06 | 252 | 49 | 392 | -140 |
| G10 | 190 | 31 | 248 | -58 |
| G14 | 30 | 12 | 60 | -30 |
| G02 | 226 | 42 | 252 | -26 |
| G11 | 84 | 11 | 88 | -4 |
| G03 | 285 | 46 | 276 | 9 |
| G12 | 131 | 15 | 120 | 11 |
| G07 | 64 | 6 | 48 | 16 |
| G08 | 224 | 26 | 208 | 16 |
| G01 | 347 | N/A (pin-based) | N/A | N/A (lab demand already pinned, not quota-bound) |
| G05 | 366 | N/A (pin-based) | N/A | N/A (lab demand already pinned, not quota-bound) |
| G09 | 215 | N/A (pin-based) | N/A | N/A (lab demand already pinned, not quota-bound) |

## TASK-506 dimension 9: tag offer per weekday [mono-room saturation]
QUESTION/OVERDEMAND: weekdays where more groups have a lab day than the tag has rooms (grasp-seed-6 family; slot-level packing is the solver's).
| Tag | Weekday | # Groups with lab | # Rooms | Excess |
|---|---|---|---|---|
| informatica | Gio | 9 | 2 | 7 |
| acconciatura | Gio | 4 | 1 | 3 |
| informatica | Lun | 5 | 2 | 3 |
| informatica | Mar | 5 | 2 | 3 |
| informatica | Ven | 5 | 2 | 3 |
| acconciatura | Mar | 3 | 1 | 2 |
| informatica | Mer | 4 | 2 | 2 |
| meccanica | Gio | 3 | 1 | 2 |
| acconciatura | Lun | 2 | 1 | 1 |
| acconciatura | Mer | 2 | 1 | 1 |
| acconciatura | Ven | 2 | 1 | 1 |
| meccanica | Mer | 2 | 1 | 1 |
| meccanica | Ven | 2 | 1 | 1 |
| termoidraulica | Lun | 2 | 1 | 1 |
| termoidraulica | Gio | 2 | 1 | 1 |

## Drill-down: infeasible groups (excess over peer median + largest courses)
(none — every group fits its generous capacity)

_Method: scripts/analyze/feasibility-audit.py via scripts/db/db-query.sh. Data fixes belong to TASK-337 (m-6), not here._
