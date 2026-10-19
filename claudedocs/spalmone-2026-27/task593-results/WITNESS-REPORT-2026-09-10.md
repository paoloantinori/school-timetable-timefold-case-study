# TASK-593 witness report: legal repairs for the delivered residual (m-14, 2026-09-10)

Substrate: two CONTENT-ADDRESSED delivery fixtures (id-independent: lesson-id
seeding is not deterministic cross-machine, TASK-588), derived from the frozen
exports on the laptop:
- fixture A = the slotretry-9sep delivery (sha 8172fc6086af6dd0; 12509 placed,
  the G14 Confezione/T16 lesson unplaced = the ledger's H0 unit);
  provenance chain: export 454ff42a -> content fixture.
- fixture B = the v132-7sep delivery (sha e1746ba751fed8b2; 12510 placed; that
  same lesson IS placed = the existence witness for closability).
The raw winner checkpoints are committed alongside (c24fc8c5, 811f3027) as
provenance; the bench loads the content form.
Driver: WitnessBenchTest (PLANNER_TEST_WITNESS_BENCH=1, EXIT=0).

Oracle (v3, after two oracle bugs found and fixed mid-task): full provider
recount over TWENTY-THREE hard families (every canonical hard ID incl.
H-M2/H-COPRESENCE), measured in HARD POINTS per family (ScoreCompat.hardOf
sums, never match counts: groupBy constraints emit zero-score matches for
compliant groups and match-count semantics read the clean delivery as
H7=3316), matched by EXACT ID token (prefix matching absorbed H30-H34 into
H3 and mangled the vector), with the lesson's own VRP value range as a
pre-filter (a slot outside it is illegal by construction, not a candidate).
Seed fidelity: SubjectRegistry course classification (DataGenerator path),
stage patterns, school holidays, golden co-presenza depth, pool-cap targets.
ENTRY GATES: fixture A reproduces the delivered vector EXACTLY (H28=4, H33=3,
H34=3, H0=2 points=1 match x weight 2, H32=1, H29=0, every other family 0);
fixture B reproduces the v132 vector (H28=0, H33=2, H34=3, H29=1, rest 0).
Acceptance = target family improves AND no family regresses vs the RUNNING
vector, over all 23 families. Bound: 54 recounts (cap 56), per-unit tries
recorded.

> HARNESS HISTORY (three oracle generations, each invalidated by its own
> evidence): v1 = 6 families, match counts - accepted H11/H1/H2/H3-violating
> "witnesses" (7/8 illegal, found by the code review's fixture cross-check).
> v2 = 11 families, match counts - still match-count blind and prefix-mangled.
> v3 = this one. The lesson is structural: a legality oracle must cover EVERY
> hard family and measure points, and its first act must be to reproduce the
> known-clean delivery on ALL of them - the v1/v2 gates passed on six families
> and lied by omission.

## Verdicts (AC#1: every unit scoped, under the full oracle)

| unit | verdict | evidence |
|---|---|---|
| H28 T03/T22, 2026-10-01 | **WITNESS** | 4-lesson day rotation (B@13->10, T shift): H28 4 -> 2, every other family unchanged (0), day stays fully packed (H17-safe by construction) |
| H28 same, 2026-09-24 | LOCAL_NO_WITNESS at depth 1 (the one rotation candidate) | the rotation lands T03 at 10:00 where he already teaches G11 (H1=1000): the day has an external teacher commitment the reorder cannot avoid |
| H0 G14 Confezione/T16 unplaced | LOCAL_NO_WITNESS at depth 1 (17 slot-room pairs over 3 Fridays, VRP-legal slots, ALL assignable rooms incl. the v132-origin R03) | every candidate closes H0 AND H33 (3->2) simultaneously but trips H1=1000: T16 already teaches G05 in those same slots (fixture A lines; the earlier 'G01' in this report was a transcription error caught by review). The blocker is a single teacher-conflict pair: a depth-2 compound (move T16's G05 lesson first) is the recorded escalation |
| H33 G14 Fridays x3 | LOCAL_NO_WITNESS at depth 1 (6 donor moves each) | after the VRP pre-filter, no single-hour import passes all 23 families |
| H34 G06 2027-06-10, G09 2027-06-03 | LOCAL_NO_WITNESS at depth 1 (6 donor moves each) | donor imports open the donor's own deficit: the census's allowance accounting, now observed from the delivery side |
| H34 G12 2027-04-16 | LOCAL_NO_WITNESS at depth 1 (6 donor moves into the free slots) | free slots exist but every import trips a conflict or opens a donor hole |
| H32 G13 2026-11-06 | LOCAL_NO_WITNESS at depth 1 (0 donor moves: budget exhausted by the preceding units) | same shape, unexplored within this run's bound |
| H29 | none in this delivery (0 points) | AC#2 vacuously satisfied |

Running vector after the depth-2 exploration: **H28 4->2, all else unchanged**
= from [-13/0] to [-11/0]. The DEPTH-2 phase explored the recorded escalations
with the full oracle: H0 compound (move the occupying blocker to its own legal
slot, then place the G14 lesson; 12 compounds over 3 Fridays) and June swap
(import + give-back; 8 compounds) - ALL ORACLE-REJECTED. The depth-2
neighborhood is also exhausted at these bounds; the next escalation level is
deeper chains or the referente answers (RA/G14 programme semantics).

## Cross-fixture (AC#5)

- The H28 10-01 witness: replayed on fixture B is n/a (v132 has H28=0).
- G14 donor pattern on B's own Fridays (03-12, 04-30): ran with the reserved
  replay budget - LOCAL_NO_WITNESS at depth 1 (6+6 donor moves) on both.
- H0's origin is fixture B (v132 places the course Thursdays 11-12) but in
  fixture A's basin those slots belong to T16's G01 commitments:
  closability is basin-dependent, consistent with the ledger.

## The structural finding (feeds TASK-595)

The delivery's depth-1 legal neighborhood is NEARLY EMPTY: one rotation
survives out of ~50 probed single moves; every other unit needs compound
moves (swap-shaped, with identified blockers: T16's H1 pair for H0;
donor-hole-repair for the June cells - where TASK-590 already proved
construction-side closability by non-greedy substitution in both basins).
The remedy family for TASK-595 is confirmed as bounded COMPOUND finishers
(depth-2 swaps), not single-move passes; no single-move finisher is supported
by this evidence.

## Bounds and unexplored escalations (AC#4)

Depth-1 only for all negatives; each negative names its blocker and the
depth-2 escalation (never explored here, never called global infeasibility):
deeper chains (depth-3+), wider compound sets, and the referente answers on the
RA/G14 programme semantics; G12 remains template-side (no Friday cells),
out of scope.

## Remedy selection (AC#6)

Smallest supported remedy: the H28 day-rotation family (one witness; the
09-24 twin needs its external commitment moved first = compound). NOT
supported: any single-move finisher for H0/H33/H34/H32. No production
finisher was added by this task.
