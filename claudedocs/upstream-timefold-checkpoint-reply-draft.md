# Draft: reply to triceo in discussion #2615

Stato: DRAFT per revisione a piu' riprese di maintainer. Da raffinare prima dell'invio; nessun invio senza approvazione esplicita. Commenti tra parentesi quadre sono note di lavoro DA RIMUOVERE.

---

Thanks for the detailed reply; the event-based shape you describe is exactly what we would hope for, and we agree the `solve(...)` signature has to stay untouched.

On your question about our "solver-internal state that is not derivable from the solution": fair challenge, and the wording in our proposal was misleading. Nothing lives inside a constraint, and nothing is Timefold-internal state. What we serialize alongside the solution is configuration that our own orchestration layer publishes before each solve:

- a set of "promoted pin" lesson ids: our staged pipeline runs several solver instances in sequence, and between two of them we promote some assignments of the previous run's solution to pinned entities for the next one. The pin set is part of how we set up the next problem, not something the solver computed in secret; on restore, we could recompute it from the previous stage's solution, but serializing it makes the checkpoint self-contained.
- a precomputed reference map (course -> set of template timeslot cells) that one of our medium constraints reads as a plain problem fact. It is derived deterministically from problem inputs before the solve starts; we publish it pre-solve because rebuilding it mid-solve would change constraint behavior between steps. Again: recomputable, just expensive relative to serializing.

So the honest rewording of our original sentence is: "state that is recomputable from problem inputs and previous-stage solutions, but that we snapshot to make restores cheap and exact". You are also right to smell something there: the idiomatic home for those maps would be problem facts on the solution itself, where they would be cloned with it and ride along in any export; our static-publishing is a practical shortcut that we have already paid for (the "publish only before the solve starts, never mid-solve, or you corrupt the score" rule exists precisely because the framework does not own that state). Moving them onto the solution is the direction we want to take. Your "cold start from the solution" model covers the solver side completely for us; the extra payload is only our own wiring's bookkeeping, which any user with a staged pipeline built on custom phases will have in some form. That is probably worth one line in the eventual docs: the checkpoint contract ends at the solution plus phase metadata, and orchestration-layer state is the user's responsibility.

One correction on our side: our question about `@PlanningId` was poorly motivated. We conflated the id requirements we met for multi-threaded solving with what a checkpoint would need; on reflection there is no relation, for the same reason you give (a restored solution loads like any other). We are leaving the question in the original post rather than editing it away, in case someone else has the same confusion.

Two small follow-ups, only if useful:

1. For the phase-end event shape, would metadata be limited to the solution and the phase index, or could it include the score at the boundary? We found the boundary score to be the cheapest way to compare two variants resumed from the same checkpoint (paired experiments against run-to-run variance).
2. We are happy to write up our failure modes with restoring from solution-only checkpoints (what had to be rebuilt, what we got wrong the first time) as a docs section or a discussion comment, whichever is more useful to you.

[Nota di lavoro: il punto 2 ripete l'offerta gia' fatta nel post originale; tenerla breve o ometterla se suonna ripetitiva.]
[Nota di lavoro: verificare con maintainer se citare il numero di istanze solver del ciclo staged (7+) o restare generici.]
