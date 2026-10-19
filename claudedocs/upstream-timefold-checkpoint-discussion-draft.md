# Draft: Timefold Discussions proposal - Phase-boundary checkpoints for long solves

Stato: DRAFT per revisione di maintainer (nessun invio senza la sua approvazione). Sezione "note per noi" in coda: DA RIMUOVERE prima dell'invio.

---

## Title (proposta)

Phase-boundary checkpoints: a first-class way to snapshot, resume and validate long solves

## Body

### The problem

Long solves (hours) are a black box between start and finish. When something goes wrong at minute 40 of a 60-minute solve, or when you want to validate a change to a late phase, today your options are:

- re-run the whole solve from scratch (hours per iteration), or
- build your own ad-hoc persistence around the solver.

The building blocks all exist in Timefold already: solution persistence (Jackson), warm start from an initialized solution, `ProblemChange` with daemon mode for real-time planning, and custom phases as a documented extension point. What is missing is the notion that the solver itself can snapshot its state at natural boundaries, namely phase transitions, so that a run can be resumed, inspected and replayed from there.

### The pattern we are proposing

At every phase transition, the solver serializes a checkpoint containing:

- the working solution (placements of all planning entities),
- the phase cursor (which phase completed, which is next),
- the current score,
- any solver-internal state that is not derivable from the solution itself (in our case: promoted-pin bookkeeping and a precomputed reference map used by a medium-tier constraint).

A checkpoint is then useful in three ways:

1. **Resume**: continue a solve from the last checkpoint (crash recovery, planned pause/resume across maintenance windows).
2. **Inspection**: load a checkpoint and score it without solving; this is a cheap oracle for validating model changes against real intermediate states.
3. **Replay / paired experiments**: run two variants from the SAME checkpoint with common random seeds, so comparisons measure the change, not the run-to-run variance.

### We run this in production (field report, not speculation)

For context: we operate a school timetabling solver (Quarkus + Timefold 2.3, ~12,500 planning entities, ~1,370 timeslots, a staged multi-phase solve of 45-90 minutes built on custom phases). We have written our own implementation of this idea for our internal use, so this suggestion is based on field execution rather than a thought experiment. Three measured examples of what it bought us:

- **Repairer validation without solving**: we developed a deterministic repair pass and validated it against 20+ archived checkpoints from previous runs, covering defect densities from 12 to 365 violations. Each validation iteration costs minutes instead of a full solve. The same checkpoints let us build a scaling curve (violations closed vs. starting density) before the pass ever ran inside a live solve.
- **Paired determinism experiments**: we ran the same configuration twice from the same construction to test whether our custom multi-threaded local search is deterministic when it should be. Identifying the divergence took two runs from a shared starting state, not a statistical campaign.
- **Fast A/B on late phases**: changes to a late repair phase are validated by resuming from an earlier boundary, cutting experiment wall time by ~60-70% per arm.

### Why phase boundaries and not arbitrary points

Phase transitions are the natural sync points: between phases the solution is consistent, no shadow-variable recalculation is mid-flight, and (in multi-threaded setups) worker state is quiescent. They also map to how practitioners already think about pipelines ("after construction", "after the first local search"), which makes checkpoints self-describing.

### Sketch of an API shape (for discussion)

Not a proposal to implement exactly this; just to anchor the conversation:

```
SolverFactory<Solution> factory = ...
Solver<Solution> solver = factory.createSolver();
// solving emits checkpoints at phase boundaries:
Checkpoints<Solution> checkpoints = solver.solve(problem);
// later, elsewhere:
Solution resumed = factory.resumeFrom(checkpoint, problem);
```

Open questions we would genuinely like the team's view on:

- Should checkpoints capture score-director-internal state, or is re-scoring from the solution sufficient (we found re-scoring sufficient for our purposes)?
- How would this interact with `@PlanningId` requirements that multi-threaded solving already imposes?
- Is there interest in checkpoint-compatible best-solution events (i.e., a listener that fires only at phase boundaries)?

### Closing

We have searched these discussions for similar ideas (checkpoint, snapshot, resume, save solution state, warm start) and found no thread proposing solver-state checkpoints; the keyword hits are all incidental, which we take as a sign the idea has not been discussed yet rather than that it is unwanted. The nearest neighbor in spirit is the thread on profiling support (#747), which shows the same appetite for better visibility into what a long solve is doing. We are happy to contribute a write-up of our implementation constraints and failure modes (what state turned out to be non-derivable, what we got wrong the first time) if there is interest. This matters most, we suspect, for users with hour-plus solves and staged pipelines built on custom phases.

---

## Note per noi (RIMUOVERE prima dell'invio)

- Toneta' volutamente "for discussion": niente pretesa di design definitivo, tre domande aperte genuine.
- I tre casi d'uso sono i nostri misurati: curva PHR su checkpoint (N=12..365), REPRODUCIBLE x2, A/B fase tarda (-60-70% wall per braccio).
- Menzionare la nostra implementazione interna e' richiesto da maintainer: fatto, primo paragrafo "field report".
- Non citare dettagli del dominio scuola oltre il necessario (una riga di contesto); il valore e' il pattern.
- Canale: GitHub Discussions di TimefoldAI/timefold-solver (creazione issue ristretta).
- Secondo pezzo della collezione "upstream ideas" (il primo: finestra fill-then-publish del SimpleFieldTracker Hibernate, documentata in byteman-guide).
