# m-14 residual ledger: slotretry-9sep delivery, [-13/0]hard (TASK-539.01, 2026-09-09)

Every hard unit of the current delivered residual, named and classified.
Identity verified TWO ways: provider analysis.json (aggregate per family) plus
an independent Python recount (`recount-residual.py` in this dir) over the
delivered lessons.txt with the run's own quota rows and timeslot dates dumped
from the live profile DB. Recount = provider on every family (3/3/1 cells,
deficits 3/3/1; H0 and H28 identity from DB drill-down and lessons.txt).
Evidence class is marked per row: MEASURED (mechanically verified today),
DOCUMENTED (cited from retained run analyses), OPEN (owned by a later m-14 task).

Scope note: the 13 hard points come from 12 violation units in THREE clusters of
four (H0's single lesson carries 2 points): four year-end/declared-day units,
four G14 units, four units of one repeated co-teaching pattern. There is no
scattered residual.

## Cluster 1: declared-day family (H34 x3, H32 x1) = 4 units, 4 hard points

| # | Family | Cell (quota) | First rejection / mechanism | Blocker class | Status |
|---|--------|--------------|------------------------------|---------------|--------|
| 1 | H34 | G06 2027-06-10 (q4) | Year-end tail; donor/slide dead ends enumerated in twin-tail-a1 (2026-09-07); June-slot teacher saturation analysis 9/09 (docenti del gruppo bersaglio occupati da ALTRI gruppi negli slot giugno) | Movable in principle (occupancy is other groups' unpinned lessons) but NO local witness found by twin-tail search; PERSISTENT across v132+slotretry basins | DOCUMENTED; witness search = TASK-593 |
| 2 | H34 | G09 2027-06-03 (q5) | Same year-end family; same twin-tail dead ends | Same as #1; PERSISTENT across basins | DOCUMENTED; TASK-593 |
| 3 | H34 | G12 2027-04-16 (q5) | NOT persistent (v132's third cell was 2027-06-04). Morning analysis 9/09: slot 13 of that date COMPLETELY FREE; pre-booking never tries it because it only uses the template slot | Movable candidate: template-slot lock; the slot-retry v1 cure was BOUNCED by bench (consumed unplacedByCourse pool, H34 22->68) | MEASURED (cell identity); rejection census = TASK-590 |
| 4 | H32 | G13 2026-11-06 (q5, placed 4) | Partial underfill 1h; first rejection not yet traced | Movable candidate (1h donor move within G13's own programme) | MEASURED (cell identity); witness = TASK-593 |

## Cluster 2: G14 family (H33 x3, H0 x1 lesson) = 4 units, 5 hard points

| # | Family | Cell / lesson | First rejection / mechanism | Blocker class | Status |
|---|--------|---------------|------------------------------|---------------|--------|
| 5 | H33 | G14 2027-01-29 (q3, placed 2, floor 3) | G14 programme floors: floor 3h, several Fridays delivered at 2h. Dates MOVE with the basin (v132: 03-12, 04-30), so not one fixed impossible date | Input-question class (G14 calendars/availability = TASK-594 scope); NOT assumed search-infeasible | MEASURED |
| 6 | H33 | G14 2027-02-05 (q3, placed 2, floor 3) | same | same | MEASURED |
| 7 | H33 | G14 2027-03-05 (q3, placed 2, floor 3) | same | same | MEASURED |
| 8 | H0 (2 pts) | lesson 8244, Confezione, T16, G14: unassigned | Unplaced through the ENTIRE winner tail (checkpoint POST_FINAL_SURPLUS_HARVEST already shows unassigned=1; ExactFinisher and room-tail could not place it in this basin) | MOVABLE: v132 placed the SAME lesson on the SAME input (v132 score has no H0). Existence witness = fixture B. Basin-dependent placement starvation, not global impossibility | MEASURED (both fixtures) |

## Cluster 3: co-teaching return pattern (H28 x4) = 4 units, 4 hard points

| # | Family | Day / lessons | First rejection / mechanism | Blocker class | Status |
|---|--------|---------------|------------------------------|---------------|--------|
| 9-12 | H28 | G03, Laboratorio Termoidraulico, 2026-09-24: T03 slots 8,9 then T22 10,11,12 then T03 13. Penalized pairs (8,13) and (9,13). SAME pattern 2026-10-01 (lessons 578,579,607 + T22 block) | The day is an A..B..A teacher return, which the golden NEVER does (TASK-448 crystallized the invariant at delta zero because runs were clean). New to this basin (v132: zero H28) | Movable candidate: day-local reorder into T03-block + T22-block (whole-block swap keeps H17 contiguity and per-day counts H32/H33 invariant) | MEASURED (lessons.txt + drill-down) |

## Sanity-only reds (NOT hard units; recorded so they are not re-chased)

These appear in the sanity report but carry NO hard penalty (provider = 0 on
their families), because their days have quota aula+lab = 0 (stage/no-session
days the H33/H32 scopes skip by design):
- G07 2027-03-31 and 2027-05-28 (1h each), G09 2027-02-24 (1h),
  G10 2027-03-04 (1h): one non-Stage hour placed on a zero-aula-lab-quota day.
- G08 2027-04-01 internal-hole red (sanity hole definition; H17 provider = 0).
- G14 sanity reds (2027-01-29, 02-05, 03-05) are the SAME cells as H33 #5-7.
- G04 busta-vs-weekday notes (120 days out of golden band): known H33/pool-cap
  family, question to referente pending (short-days 1/G08), pre-existing.

## Corrections to retained narratives (fed to TASK-579.01 evidence)

1. VERDICT-raw.md and commit 5b406056 claim "H32=3 celle (-3)"; provider and
   recount both say H32 = 1 cell, -1. The -13 total only reconciles with -1.
2. VERDICT-raw lists the third H34 cell as "G12 04/06"; it is 2027-04-16
   (April 16), confirmed by the recount and by the 9/09 morning analysis note.
3. "niente GRASP in questo ciclo" is true only of the WINNER (attempt 1,
   deterministic): GRASP attempts 2-5 ran and lost.
4. v132-7sep's VERDICT-raw.md dates its own cycle "6/09 pomeriggio"; the anchor
   commit 89e505d1 was authored 2026-09-07 12:42 and the cycle ran 12:44-19:37
   on 7/09 (jar 12:43:33 cannot precede its own source commit). One-day offset;
   relevant when correlating laptop logs or the same-day cure commit.

## Enumeration scope (what this ledger does NOT claim)

- WHY each empty day failed at pre-booking time (allowance exhausted vs no
  candidate vs VRP rejection vs conflict vs parity vs never attempted) is the
  LIVE-path rejection census owned by TASK-590 (its AC #2/#3); the reserve
  journal records successes only and is debug-gated.
- Whether legal repairs EXIST for each unit is TASK-593's witness work; this
  ledger records the one existence proof already in hand (v132 placed lesson
  8244) and classifies blocker type, nothing stronger.
- No global infeasibility is asserted anywhere; LOCAL_NO_WITNESS in prior
  searches (twin-tail) is recorded as exactly that.
