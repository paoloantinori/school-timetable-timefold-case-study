# Gist v2 (per pubblicazione): failure modes prima, formato dopo

Ristrutturato il 27/08 su approvazione maintainer ("via") dopo la risposta di triceo
("That would be good to know"): le failure modes aprono il gist perche' e' la
parte che ha chiesto esplicitamente, il formato checkpoint segue come dettaglio.
Sanitizzazione verificata meccanicamente (30 docenti + 14 gruppi del seed
confrontati col testo: zero riscontri; "stage" e' la parola inglese).

Titolo proposto: "Debugging a staged Timefold pipeline: the failure modes that
ate our nights (and the checkpoints that fixed them)".

---

## The failure modes (the list we promised)

Roughly in the order they cost us the most:

1. **Solver statics published mid-solve corrupt the incremental score.** We
   publish pre-solved snapshots (quota facts, pin registries, template
   references) into static fields that constraint streams read. Republishing
   them mid-solve produces score corruption that surfaces much later. Rule:
   publish once per solve setup, never mid-solve; a solve-side pass that
   needs different facts gets its own publish-and-restore window.
2. **Promoted pins leaking across a crash.** Our pipeline temporarily pins a
   slice of the schedule between phases and releases it later. The release
   must be in a `finally`, and the pin set must be asserted unchanged at
   cycle close (a set that silently grows or shrinks is a corrupted record
   even when the count survives). A checkpoint that only recorded `pinned`
   could not tell "the school pinned this" from "the pipeline pinned this and
   must release it", so `promoted` is a first-class field.
3. **Quiescence signals that lie.** `solverStatus` reads NOT_SOLVING for the
   whole staged run (each stage builds its own ad-hoc solver), and a
   refinement step after the last boundary can hold the real still-working
   state. Poll a dedicated stage field; treat "NOT_SOLVING plus stage==null"
   as the only rest signal. We shipped a `refiningForDelivery` marker so
   drivers stop freezing mid-refinement artifacts.
4. **A resumed run restoring fewer rows than it saved.** Restore is
   fail-loud: every checkpoint row must resolve to a live entity id, or the
   resume aborts. The alternative (best-effort restore) produces a run that
   looks fine and measures nothing.
5. **Saved state mutating under the bench.** Resuming a checkpoint writes the
   delivered grid back to the DB; a probe that then reads the DB sees the
   restored state, not the pass's output. Measurement reads the pass's own
   log line, or the DB after a fresh save.
6. **Value ranges rebuilt from seed disagreeing with the run that produced
   the export.** A fixture that reloads a solution export recomputes
   per-entity value ranges from seed data; a run that used quasi-repair
   passes can leave assignments outside those rebuilt ranges, and the solver
   rejects the input. For those states, resume from a checkpoint (same live
   statics) instead of loading an export through a seed fixture.

## The checkpoints that fixed them

We run a school-timetabling solver (about 12.5k planning entities, 2 planning
variables each) as a staged pipeline: a construction phase, several
local-search phases over one variable, a set of deterministic repair passes,
then a room phase. Each stage runs on its own ad-hoc `Solver` instance, so
there is no `SolverManager` progress signal and no way to resume a long run
that died at minute 60 of 90. One JSON file per boundary, about 12.5k rows. The full schema (every field we
write, with the design decision it encodes):

```json
{
  "version": 1,
  "cursor": {
    "boundary": "POST_EXACT_FINISHER",
    "iteration": 0,
    "attempt": 2,
    "graspSeed": 2
  },
  "score": "-14hard/-1133622medium/30618soft",
  "boxed": 0,
  "timeslotTierHard": -14,
  "placements": [
    {"lessonId": 1, "timeslotId": 13, "roomId": null,
     "pinned": false, "promoted": false}
  ]
}
```

Field notes:

- `cursor.boundary` + `cursor.iteration`: which boundary, and which loop round
  (our timeslot-solve and demotion stages iterate). `cursor.attempt` +
  `cursor.graspSeed`: the multi-start context, so a resumed segment reproduces
  the attempt's seeding contract and not just its grid.
- `placements[].roomId: null` encodes "roomless" exactly like the domain does
  before the room phase.
- `pinned` vs `promoted`: the school's pins vs the pipeline's temporary pins.
  The promoted set is the ONLY cycle-derived static we carry, and it rides as
  a flag on the placement instead of a separate section: single source, no
  drift between the grid and the pin registry.
- Everything else the cycle needs (calendars, quotas, availability, template
  references) is recomputed from inputs and RE-PUBLISHED at resume, not
  carried: a checkpoint that shipped recomputable state would go stale the
  moment an input changes.

Design points that mattered:

- **The numbers ride with the grid.** `boxed` (unassigned entities) and
  `timeslotTierHard` (hard score restricted to the variable being optimized
  at that boundary) are recomputed and stored at capture time, so a resumed
  run starts from the same gates the original run saw.
- **Room-tier hard is kept out of the tier number on purpose.** Before the
  room phase, every placed entity is roomless; mixing that into the gate
  would blind the timeslot phases to their own regressions. We keep a
  room-tier family set and subtract.

### The boundary oracle in the test suite

The enum of boundaries is derived from declaration order, and a unit test
reads the pipeline's source code and asserts that every `run*` stage call
appears in the enum's order and has a mapping. Every time someone added a
stage pass without extending the checkpoint census, that test failed with
the exact instruction of what to update. An oracle you have to remember to
update is not an oracle.

The shape of it, so it can be copied:

```java
// the boundary-relevant stage calls, in the order the cycle method makes
// them, each mapped to the boundary AFTER it
private static final Map<String, StagedPhaseBoundary> CYCLE_CALL_TO_BOUNDARY = Map.ofEntries(
        Map.entry("loadSchedule(true)",            StagedPhaseBoundary.CYCLE_START),
        Map.entry("runConstructionStage(",         StagedPhaseBoundary.POST_CONSTRUCTION),
        Map.entry("promoteTemplatePins(",          StagedPhaseBoundary.POST_PROMOTION),
        Map.entry("runTimeslotSolveStage(",        StagedPhaseBoundary.POST_B_TIMESLOT),
        Map.entry("releasePromotedPinsAndRepublish(", StagedPhaseBoundary.POST_RELEASE),
        // ... one entry per stage pass ...
        Map.entry("runRoomSolveStage(",            StagedPhaseBoundary.POST_B_ROOM));

@Test
void cycleSourceIsTheBoundaryOracle() throws IOException {
    String source = Files.readString(Path.of(
            "src/main/java/.../ScheduleService.java"));
    String body = extractCycleMethodBody(source);
    List<StagedPhaseBoundary> sourceOrder = boundaryCallsInSourceOrder(body);

    // negative half: a NEW run* pass added to the cycle without a mapping here
    // would be invisible to the order assertion (it only matches known calls),
    // so every run* call in the body must be tracked
    Matcher m = Pattern.compile("run[A-Z]\\w*\\(").matcher(body);
    while (m.find()) {
        assertTrue(CYCLE_CALL_TO_BOUNDARY.containsKey(m.group()),
                "the cycle calls " + m.group() + " but it has no boundary mapping:"
                        + " a new stage pass was added without extending the"
                        + " checkpoint census");
    }
    // positive half: the order the source makes those calls IS the census
    assertEquals(StagedPhaseBoundary.linearOrder(),
            projectOntoLinearOrder(sourceOrder),
            "a pass was added, removed or moved without updating the boundary enum");
}
```

The two halves are the point: the order assertion alone would silently ignore
an unmapped new pass, and the mapping alone would not catch a reordering.

## What it bought us

A prediction-verification loop where one hypothesis costs minutes: restore a
boundary checkpoint, re-run the tail, read the gates. Debugging went from
"run the 90-minute pipeline and stare" to red/green on a single pass, and the
boundary oracle kept the census honest while the pipeline grew from 5 to 18
boundaries.

*(Numbers are generic on purpose; the patterns are the content.)*
