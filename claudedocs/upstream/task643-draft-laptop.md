# TASK-643 draft: MoveDirector.executeTemporary leaves the working solution mutated on throw (PR da APPROVARE da maintainer: NESSUN invio)

## Problem

`InnerScoreDirector.executeTemporaryMove(move, ...)` delegates to
`MoveDirector.executeTemporary`, which is straight-line code with no
try/finally: apply move -> calculateScore -> postprocessor ->
EphemeralMoveDirector.close() -> restore score. If move execution (or a
variable listener it triggers) throws mid-way, `close()` and the score
restore never run, and the working solution is left HALF-MUTATED while the
exception propagates to the caller.

Verified on timefold-solver-core 2.7.0-rc-1 (jar inspection + decompiled
executeTemporary; our production wrapper hit the exact scenario).

Impact for Neighborhoods-API consumers: any caller that evaluates candidate
moves with executeTemporaryMove and catches the exception (e.g. to skip a
bad candidate and continue the loop) silently keeps evaluating, and possibly
delivers, on a corrupted solution. The only safe pattern today is to treat
ANY throw as fatal and abandon the working solution entirely (what we do in
our ViolationScopedSteepestFinisher: wrap into IllegalStateException and
restore from an external snapshot).

## Repro (minimal, MoveTester-shaped)

[to fill: test su branch locale non pushato - preview.api.move.test MoveTester:
una solution con una variable listener che lancia su un valore specifico;
executeTemporaryMove(move verso quel valore) in try/catch; assert che la
solution dopo il catch coincida con la solution prima (oggi FALLISCE: la
variabile resta mutata)]

## Proposal

Wrap the body of MoveDirector.executeTemporary in try/finally (or
try-with-resources on the EphemeralMoveDirector) so the working solution is
RESTORED before the exception propagates: either undo the partially applied
move, or document + guarantee that close()/restore runs on every path. A
throwing move evaluation should leave the solution exactly as it was, the
same guarantee executeTemporaryMove gives on the success path.

## Impact

- Fix is local to MoveDirector.executeTemporary; no API change.
- Consumers get the intuitive contract ("temporary" = restored, always).
- Alternative (if restore-on-throw is deemed too costly): document the
  current contract loudly on executeTemporaryMove and provide
  executeTemporaryMoveOrRestore for consumers that must continue after a
  throw.
