# TASK-595 candidate selection: only the discriminator-supported remedy (m-14, 2026-09-11)

## The decision (AC#1)

ONE mechanism integrated: the **H28 rotation finisher**
(`src/main/java/com/school/planner/solver/H28RotationFinisher.java`, flag
`planner.construction.h28-finisher.enabled`, opt-in DEFAULT OFF). It reorders the lessons
of an A..B..A (group, subject, day) cell into teacher-contiguous blocks over the cell's
OWN slot set, trying every block ordering in lexicographic teacher order.

- **Supporting witness**: TASK-593 unit 2, fixture slotretry-9sep, cell G03 /
  Laboratorio Termoidraulico 2026-10-01 running T03,T03,T22,T22,
  T22,T03; the rotation to B,B,B,T,T,T moved H28 4 -> 2 with every other hard
  family unchanged. The finisher GENERALIZES the single cyclic rotation to all k!
  orderings (k <= 4 teachers per cell).
- **Affected boundary**: post-room, after `runOneOffFinisher`, before `saveFinalSolution`
  (the last deterministic sibling). Improve-or-noop on the FULL hard levels (lessons keep
  their rooms, so H2 is in play and the timeslot-tier oracle the earlier siblings use
  would be blind to it).
- **Why this shape is safe by construction**: the move permutes a cell's lessons over the
  cell's own slots, so the (group, day) slot multiset is unchanged (H17/H32/H33/H34/H7
  day counts cannot move) and every lesson keeps its date (all date-keyed families inert).
  In play: H1/H3/H5/VRP (shared `PureHoleRepair.Grid.cellOk` chain), H2 (the mover's own
  room), H28 itself (removed by block contiguity) and H-COPRESENCE (dedicated in-pass
  depth gate, see the review round); the caller's FULL-hard snapshot oracle is the
  aggregate backstop.
- **Single source of A..B..A semantics**: the H28 stream's pair/between filtering
  lambdas were extracted into package-visible statics
  (`SchoolScheduleConstraintProvider.h28ReturnPair` / `h28Between`, the
  `isQuotaFillExcluded` precedent) consumed by BOTH the constraint stream and the
  pass's detection, so a future semantics change cannot strand the finisher's mirror.
  Behavior-neutrality of the extraction pinned by `GoldenSolutionRegressionTest` (EXIT 0
  post-refactor).

## Integration evidence (AC#2/#3/#4)

- **Unit layer** (`H28RotationFinisherTest`, 10/10 EXIT 0): flag-off inertness, the witness
  pattern closing to A,A,A,B,B,B, pin-in-cell -> honest `pinBlocked` with nothing moved,
  H1-blocked day -> `locked` with a TOTAL revert (the cell still occupies exactly its
  original slot set), the second ordering winning when the first dies on H1, and the pure
  predicates (detection mirroring the constraint shape, lexicographic ordering
  enumeration).
- **Near-delivery layer** (`WitnessBenchTest#h28FinisherGate`, a STANDALONE env-gated
  test so iterating on the finisher does not re-pay the ~10-minute full bench; 78 s,
  EXIT 0, 2026-09-11, final form with the H-COPRESENCE gate armed): fixture A loaded pristine reproduces the
  delivered vector exactly (H28=4, H33=3, H34=3, H0=2 points, H32=1, rest 0), then
  `Result[cellsViolating=2, cellsRepaired=1, pinBlocked=0, locked=1]`: **H28 4 -> 2,
  every other family byte-identical before/after** (H17 stays 0: no hole transfer).
  Fixture B (v132, H28=0): inert, no family moved (the AC#4 cross-fixture gate). The
  same numbers were first produced inside the full bench run (133 recounts, EXIT 0,
  2026-09-11 00:13) before the section was split out; `bench-log-excerpt.txt` carries
  that run's verbatim lines.
- **The 09-24 twin is now ENUMERATION-confirmed compound-blocked**: the witness report's
  claim ("the day has an external teacher commitment the reorder cannot avoid") was
  checked mechanically by trying every ordering: both T03-first and T22-first
  die on H1 (the external commitment), so the cell reads `locked`. Closing it needs the
  compound depth-2 move (move the external commitment first), which TASK-593's depth-2/3
  exploration frames but does not support with a witness.

## Rejected alternatives (AC#1, all with recorded evidence)

| candidate | verdict | where |
|---|---|---|
| Construction-side substitution (TASK-590 Phase E) | MEASURED-REJECTED (kill criteria tripped in both basins; flag stays OFF) | task590-results/CENSUS-REPORT-2026-09-09.md |
| Donor import, depth 1 (H33/H34/H32 cells) | LOCAL_NO_WITNESS (all cells, named blockers) | task593-results/WITNESS-REPORT-2026-09-10.md |
| Depth-2 compounds (H0 blocker-first, June swap) | ALL ORACLE-REJECTED | same |
| Depth-3 chains (H0 occupant-free chain, June widened) | ALL LOCAL_NO_WITNESS | commit 3d0c15bb |
| Any single-move finisher for H0/H33/H34/H32 | NOT SUPPORTED by the evidence; not built | same |

The unremedied residual (H0=1 lesson, H33 G14 Fridays, H34 June cells, H32 G13, H28
09-24) waits on the referente answers (TASK-592 RA 40h, TASK-594 G14 semantics) or deeper
compounds; no mechanism for them is integrated.

## Review round (11/09, high-effort, two independent verifiers)

Confirmed findings, all FIXED before commit:
1. **H-COPRESENCE was the one timeslot-tier family a within-cell permutation can move
   that neither the Grid chain nor the net-hard improve-or-noop oracle sees**
   (both weights are 1: the oracle could trade an H28 point for a new H-COPRESENCE
   point). Fixed with a dedicated in-pass depth gate mirroring `coPresenceCap`
   exactly ((tag, startDateTime) count deltas vs golden depth, armed only when
   co-presenza is enabled; master counts maintained across repairs). Pinned by two
   new unit tests (first-ordering rejected / every-ordering locks) and re-validated on
   the gate: the witness repair passes the depth gate, H28 4 -> 2 with
   H-COPRESENCE=0 before and after.
2. **Duplicate-slot entry cells** (an H3-violating shape) would degenerate the
   permutation into two lessons on one slot, which the Grid maps silently overwrite.
   Such cells are now skipped and counted locked; pinned by a unit test.
3. **Wrapper javadoc inverted its own H2 coverage** (H2 IS in-pass-gated via cellOk's
   room-cell check); both wiring comments rewritten with the corrected in-play list.

Also verified and cleared by the reviewers: the provider extraction is
character-identical to the previous inline lambdas (golden regression EXIT 0), the
multi-start attempt isolation is complete (fresh `loadSchedule(true)` per attempt, the
pass is stateless), the boundary order (post-OneOff) is right because OneOff's
time-moves can CREATE A..B..A patterns while H28's cannot undo OneOff's fixes, and
`conflictsAdded=0` is the correct claim (whole-cell deindexing means H1/H3 cannot be
added by a move). One pre-existing defect OUTSIDE this diff was found and filed the
same turn: **TASK-598** (statics pollution between GoldenSolutionRegressionTest and
SchoolScheduleConstraintProviderTest in one JVM, M20 reads 0, reproduced on clean
HEAD).

## Flag manifest (AC#5/#6)

- `planner.construction.h28-finisher.enabled`: opt-in, DEFAULT OFF, only a truthy
  value arms. Banner `H28 rotation finisher (TASK-595, prototype): enabled=%b` in
  `refreshSolverStatics`. Knobs registry regenerated: 129 entries (claudedocs/knobs.md).
- Reproduction: `PLANNER_TEST_WITNESS_BENCH=1 ./scripts/core/test.sh -c WitnessBenchTest`
  (the finisher section reloads fixture A pristine and asserts the full 23-family
  comparison), and `./scripts/core/test.sh -c H28RotationFinisherTest` for the unit
  layer. Source: this working tree (commit to follow). No default, no protected input,
  no school-input change.
- Delivery math on fixture A: [-13/0]hard -> [-11/0] with the flag ON; delivery
  unchanged with the flag OFF (the default).
