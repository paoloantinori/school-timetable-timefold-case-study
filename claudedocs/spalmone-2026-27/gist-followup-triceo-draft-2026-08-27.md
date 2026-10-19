# Gist draft: checkpoint format + failure modes for solver-pipeline debugging (SANITIZED)

BOZZA (inglese, sanitzata: zero nomi di docenti, classi, o dati della scuola;
numeri generici). maintainer decide se pubblicarla dopo aver spedito la reply.
Titolo proposto: "Debugging a staged Timefold pipeline: checkpoints, boundary
oracles, and the failure modes that ate our nights".

---

## Checkpointing a staged solver pipeline

We run a school-timetabling solver (about 12.5k planning entities, 2 planning
variables each) as a staged pipeline: a construction phase, several local-search
phases over one variable, a set of deterministic repair passes, then a room
phase. Each stage runs on its own ad-hoc `Solver` instance, so there is no
`SolverManager` progress signal and no way to resume a long run that died at
minute 60 of 90. Two things fixed that for us.

### 1. A checkpoint at every phase boundary

One JSON file per boundary, about 12.5k rows:

```json
{
  "version": 1,
  "cursor": {"boundary": "POST_EXACT_FINISHER", "iteration": 0, ...},
  "score": "-14hard/-1133622medium/30618soft",
  "boxed": 0,
  "timeslotTierHard": -14,
  "placements": [
    {"lessonId": 1, "timeslotId": 13, "roomId": null,
     "pinned": false, "promoted": false},
    ...
  ]
}
```

Design points that mattered:

- **The numbers ride with the grid.** `boxed` (unassigned entities) and
  `timeslotTierHard` (hard score restricted to the variable being optimized at
  that boundary) are recomputed and stored at capture time, so a resumed run
  starts from the same gates the original run saw, and A/B benches on a single
  pass take minutes instead of a full 90-minute pipeline.
- **Room-tier hard is kept out of the tier number on purpose.** Before the
  room phase, every placed entity is roomless; mixing that into the gate would
  blind the timeslot phases to their own regressions. We keep a
  `ROOM_TIER_HARD_FAMILIES` set and subtract.
- **`promoted` is a first-class field.** Our pipeline temporarily pins a slice
  of the schedule between phases and releases it later. A checkpoint that only
  recorded `pinned` could not tell "the school pinned this" from "the pipeline
  pinned this and must release it", and a crash in between would leak pins
  into the next run.

### 2. A boundary oracle in the test suite

The enum of boundaries is derived from declaration order, and a unit test reads
the pipeline's source code and asserts that every `run*Stage` call appears in
the enum's order and has a mapping. Every time someone added a stage pass
without extending the checkpoint census, that test failed with the exact
instruction of what to update. An oracle you have to remember to update is not
an oracle.

## The failure modes (the list we promised)

Roughly in the order they cost us the most:

1. **Solver statics published mid-solve corrupt the incremental score.** We
   publish pre-solved snapshots (quota facts, pin registries, template
   references) into static fields that constraint streams read. Republishing
   them mid-solve produces score corruption that surfaces much later. Rule:
   publish once per solve setup, never mid-solve; a solve-side pass that
   needs different facts gets its own publish-and-restore window.
2. **Promoted pins leaking across a crash.** Same as the checkpoint field
   above: the release of temporary pins must be in a `finally`, and the pin
   count must be asserted unchanged at cycle close (a set that silently grows
   or shrinks across a cycle is a corrupted record even when the count
   survives).
3. **Quiescence signals that lie.** `solverStatus` reads NOT_SOLVING for the
   whole staged run (each stage builds its own solver), and a refinement step
   after the last boundary can hold the real "still working" state. Poll a
   dedicated `stagedStage` field; treat "NOT_SOLVING plus stage==null" as the
   only rest signal. We shipped a `refiningForDelivery` marker so drivers stop
   freezing mid-refinement artifacts.
4. **A resumed run restoring fewer rows than it saved.** Restore is
   fail-loud: every checkpoint row must resolve to a live entity id, or the
   resume aborts. The alternative (best-effort restore) produces a run that
   looks fine and measures nothing.
5. **Saved state mutating under the bench.** Resuming a checkpoint writes the
   delivered grid back to the DB; a probe that then reads the DB sees the
   restored state, not the pass's output. Measurement reads the pass's own
   log line, or the DB after a fresh save.
6. **Value ranges rebuilt from seed disagreeing with the run that produced the
   export.** A fixture that reloads a solution export recomputes per-entity
   value ranges from seed data; a run that used quasi-repair passes can leave
   assignments outside those rebuilt ranges, and the solver rejects the input.
   For those states, resume from a checkpoint (same live statics) instead of
   loading an export through a seed fixture.

## What it bought us

A prediction-verification loop where one hypothesis costs minutes: restore a
boundary checkpoint, re-run the tail, read the gates. Debugging went from
"run the 90-minute pipeline and stare" to red/green on a single pass, and the
boundary oracle kept the census honest while the pipeline grew from 5 to 18
boundaries.

*(Numbers are generic on purpose; the patterns are the content. Happy to share
the JSON schema or the oracle test shape in more detail if useful.)*
