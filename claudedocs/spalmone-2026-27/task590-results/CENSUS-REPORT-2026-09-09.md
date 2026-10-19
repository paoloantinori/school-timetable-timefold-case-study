# TASK-590 reserve-path rejection census (m-14, 2026-09-09 evening)

Method: the LIVE reserve path (DayClusteredConstructionPhase.reserveDeclaredDayPlacements)
now records a first-walk-stopping classification per declared tail date
(RESERVE_CENSUS) and, at each date's first allowance exhaustion, the full
per-course allowance table (ALLOWANCE_SNAPSHOT). Driver: ReserveCensusBenchTest
(env PLANNER_TEST_RESERVE_CENSUS=1) reproducing the MEASURED protocol's
pre-construction state in-test: real seed, 18 rooms, 2009 Stage pseudo-courses,
production pre-assign (12510 lessons, 274 pinned/placed = the frozen fixture
m14-precon-b / v132 CYCLE_START count; content sha over
group|subject|teacher|date|slot tuples printed for cross-check), and the
pinned-cells static REPUBLISHED after pre-assign exactly as production's
refreshSolverStatics does (a first draft of the bench skipped that republish and
its census measured a protocol variant with the H3 pinned-cell exclusion
disarmed; the code review caught it and the numbers below are from the corrected
bench, EXIT=0; the classification code also fixes a post-cap shadowing bug and
dumps the allowance table at first exhaustion even when a parity class came
first). Two arms: deterministic (attempt-1 equivalent) and GRASP seed=1.

## Census totals (616 declared tail dates per arm, corrected bench)

| outcome | deterministic | GRASP seed=1 |
|---|---|---|
| ALLOWANCE_EXHAUSTED | 125 | 116 |
| PARITY_REJECTION | 186 | 190 |
| VRP_REJECTION | 28 | 28 |
| CONFLICT | 0 | 0 |
| NEVER_ATTEMPTED | 8 | 8 |
| BOOKED_PARTIAL | 268 | 256 |
| BOOKED / BOOKED_CAP_FULL | 0 / 1 | 17 / 1 |

Post-construction (bench, pins+stage bearing, H34-scope empties; NOT the
PoolCapFill population: this count drops the isQuotaFillExcluded groups and runs
with pins): deterministic 7 {G05 2, G06 2, G09 1, G10 1, G12 1},
GRASP 6. The live post-construction in-scope count at the same family was 9 (40
total minus the excluded blocks): comparable, slightly better.

## The four ledger cells (the census verdict)

1. **G06 2027-06-10: ALLOWANCE_EXHAUSTED (Cosmetologia@slot 0, booked=0),
   STABLE across both arms.** Not teacher saturation (CONFLICT = 0 in the whole
   window). The non-cannibalizing claim leaves this date nothing of its leading
   course: Cosmetologia claim=54 vs pool=50.
2. **G09 2027-06-03: ALLOWANCE_EXHAUSTED (Recuperi ed Approfondimenti@slot
   0) deterministic; PARITY_REJECTION (weekday-share, Confezione) under GRASP.**
   Basin-dependent class; the allowance story dominates the deterministic arm.
3. **G12 2027-04-16: NEVER_ATTEMPTED - no template cells for FRIDAY, stable
   across arms.** The 9/09 morning hypothesis ("slot 13 free, pre-booking only
   tries the template slot") is REFUTED as mechanism: there is no cell to try.
   The reserve is template-driven by design; with no Friday cells the walk
   cannot even start. Cure direction is template-side (Friday cells for G12)
   or template-independent booking: both OUTSIDE this task's substitution
   prototype scope (no cell to substitute into).
4. **G13 2026-11-06 (H32 1h): NOT IN CENSUS.** A real in-grid Friday, but
   mid-year and outside the 56-day tail window: the reserve never targets it.
   Its cure belongs to the repair tail (QuotaFillRepair family), not
   construction ordering. (An earlier draft of this report probed a mistyped
   2027 date; the review caught the typo and the corrected probe confirms the
   same verdict for the true cell.)

## AC#4 gate: does a legal alternative with non-cannibalizing allowance exist?

At each date's first allowance exhaustion the full table (deterministic arm):

- G06 2027-06-10: Cosmetologia 0, Consulenza d'immagine allow=3,
  Approfondimenti tecnica operativa allow=3, Gestione aziendale allow=8,
  **Servizio stilistico allow=23**. (GRASP arm: **Scienze allow=6**.)
- G09 2027-06-03: RA 0, Economia e Diritto del lavoro 0, Comportamento
  Sociale 0, **Educazione Civica allow=2**.

GATE PASSED for BOTH persistent June cells in the deterministic arm
(non-cannibalizing allowance exists on the date; the GRASP arm corroborates for
G06, while its G09 cell classifies as parity-rejected there). CAVEAT for
the prototype design: under blockGuard the leading cell's failure stops the
whole date, so those alternatives are never tried today; the substitution must
take the LEADING position to respect H31/H17 contiguity, and must still pass
VRP legality and parity at booking time (the snapshot proves allowance only).

## Hypothesis scorecard (pre-registered in the task)

- "Chronological consumption / teacher June saturation explains the H34 tail":
  REFUTED for the ledger cells (allowance accounting; CONFLICT = 0/616 in the
  corrected bench).
- "G12 16/04 has a free slot the pre-booking never tries": REFUTED as
  mechanism (no template Friday cells at all).
- Coda-prima (reorder so tail cells place before the pool drops): the census
  shows the reserve's claim system ALREADY refuses to cannibalize earlier
  dates; the starvation is in the POOL DEMAND (claim 54 > pool 50), which
  ordering inside the reserve cannot fix. The supported cure at the reserve
  level is ALTERNATIVE-COURSE SUBSTITUTION at allowance-dead leading cells.

## Phase E: first-fit substitution prototype (default OFF) - MEASURED AND REJECTED

Flag planner.construction.prebooking.substitution.enabled (opt-in, only literal
"true"). At an allowance-dead cell the reserve tries the FIRST later same-date
template course with non-cannibalizing allowance (own allowance decremented per
booking) at the same slot range under every gate the original faces. Four bench
arms, EXIT=0:

| arm | empties H34-scope | closed vs OFF | CREATED vs OFF |
|---|---|---|---|
| deterministic OFF | 7 | - | - |
| GRASP seed=1 OFF | 6 | - | - |
| deterministic ON | 3 | 5: G06 2027-06-10, G09 2027-06-03, G05 06-07, G05 06-09, G10 06-08 | **1: G12 2027-05-07** |
| GRASP seed=1 ON | - | 4: G06 2027-06-10, G10 05-25, G10 06-08, G10 06-09 | **2: G09 2027-06-03 itself, G13 2027-06-07** |

15 substitutions fired in the deterministic arm; pins preserved (274 asserted).
Construction-stage H32/H33 worsened in the ON arm (38->43 / 39->44
deterministic): the mechanism REDISTRIBUTES hours, and the redistribution
cannibalizes through the pool even when every booking stays within its own
allowance (the claim model only protects claimed dates, not the stamp's own
later consumption).

VERDICT (pre-registered kill criteria, AC#5/#6/#7): KILL CRITERION TRIPPED IN
BOTH BASINS. First-fit substitution is REJECTED in this form; the flag stays
default OFF and no adoption is proposed. The rejected-but-correct scaffold stays
committed (CompositionHoleCloser precedent).

UNCONFOUNDED BY THE CODE REVIEW: the first measurement ran with three
implementation defects the high review caught (alternatives scanned from cell 0,
re-booking an earlier cell's course later the same day = hard H28; partial fills
continuing above in-cell gaps = hard H17; mid-cell death retries at an occupied
slot = guaranteed-zero scan). All three are fixed (later-cells-only scan,
leading-slot gate, partial-fill stops the date; journal + census parity kept) and
the four arms re-run: SAME closed/created sets, same kill verdict. The
created-cell cannibalization is therefore INHERENT to first-fit redistribution,
not an artifact of the selection bugs. Remaining known costs of the mechanism,
recorded with the rejection: every substituted hour deviates from its own course
template (M32) and the substitution+slot-retry combination is untested (moot
while the flag is OFF).

THE ASSET the rejection leaves behind: both June ledger cells are CONSTRUCTION-
CLOSEABLE (G06 2027-06-10 closed in BOTH basins; G09 2027-06-03 closed in
the deterministic basin) - the cells are not infeasible, the first-fit GREED is
what fails. TASK-593's witness search should use this as the existence proof and
look for non-greedy moves (e.g. a swap-shaped or claim-aware selection that
protects the cannibalized dates), which is repair-side machinery, not reserve
ordering.

## Status

Census (AC#1/#2/#3) DONE; AC#4 gate evaluated (PASSED for the June cells) and
the conditional prototype run through AC#5/#6/#7 with the pre-registered kill
criteria: REJECTED in first-fit form, flag OFF, scaffold kept. G12 (no
Friday template cells) and G13 (outside the reserve window) remain with their
census-recorded dispositions: template-side and repair-tail families
respectively. Raw censuses: reserve-census-*.txt (616 entries each) +
bench-log-excerpt.txt in this dir.
