# TASK-643 bozza discussion: exception-safe executeTemporaryMove

STATO (per maintainer): BOZZA DISCUSSION, NON PR. Nessun invio senza il suo via.
Setaccio 23/09: la forma PR era bocciata (la decisione e' deliberata, PR
#2649 del 11/09). ADVERSARIAL REVIEW 23/09: verdetto POST-WITH-EDITS, le 7
cure sono applicate in questa versione. DUE DECISIONI CHE LA REVIEW HA
CHIUSO (e che invertono quanto ti avevo detto a voce): (1) DESTINAZIONE =
GitHub Discussion NUOVA, NON commento su #2649: commentare lì sarebbe
relitigare davanti all'autore una decisione di dodici giorni fa; (2)
FINESTRA = dopo il rilascio del 2.7.0 finale (29/09), così il post non
lega a nessuno e non si legge come pressione su un tag in corso. La
citazione del javadoc e' stata riverificata verbatim su main oggi
(23/09, gh api): il sorgente ha {@link AutoCloseable} che rende come
plain text.

## Testo proposto per la discussion (inglese)

Title: `A use case for the fatal exception path of executeTemporaryMove`

Body:

I read the current contract of `EphemeralMoveDirector` and the history
behind it. PR #2596 introduced undo on the exception path in August. PR
#2649 then removed it again, deliberately, and the javadoc on main states
the reason (quoted verbatim from main today):

> This class deliberately is not AutoCloseable: a try-with-resources
> statement would undo even a move that threw an exception, whose recorded
> actions may be half-applied, causing all sorts of unexpected situations.
> Callers therefore close explicitly, on the paths where the move itself
> succeeded; a move that throws terminates the execution instead

The overload javadoc adds: "If either the move or the postprocessor throws,
the move is not undone; callers should treat all exceptions as fatal".

I am not asking to reopen that decision. I want to share one use case that
pays for it.

My use case. I run a deterministic finisher at delivery time, after local
search has stopped. It walks candidate moves around hard constraint
violations and evaluates each candidate incrementally with
`InnerScoreDirector.executeTemporaryMove(move, false)`. A typical walk is a
few seconds; the largest I have measured exceeded seven hundred thousand
evaluations. The ratified grammar evaluates single-variable moves. An
experimental opt-in extension composes changes with `Moves.compose`, for
example a timeslot change plus a room change; those composites widen the
mid-execution failure surface, since a throw in the second part leaves the
first part applied to the working solution.

To be clear about frequency: no candidate has ever thrown in production for
us. The fatal handling is defensive, not a scar. I follow the javadoc,
treat any throw as fatal, abort the whole walk, and restore the working
solution from a snapshot taken before the walk. If a candidate ever does
throw, every evaluation already spent is lost with it.

I am aware of issue #2666 and its resolution in #2667, which stops
structurally flawed moves from being selected. My case differs in one
respect. The moves are valid. The throw would come from user code reached
mid-execution, for example a variable listener, so selection-time filtering
cannot fully remove the risk.

One request, and one open question.

The request. An explicit opt-in variant with best-effort restore would
serve callers like me without touching the default contract: a separate
method, documented as best-effort on the throw path, for callers that can
re-score after a partial restore. A finisher like mine gets a cheaper
recovery than a full snapshot.

The open question. The javadoc rejects try-with-resources because recorded
actions may be half-applied. Is that concern fundamental to the recording
design, or an implementation artifact that a cancelable recording could
address?

If the opt-in variant does not interest you, the current contract is
workable for me. The snapshot restore is expensive but correct. I am happy
to build a proof of concept for the opt-in variant.

## Contesto nostro (italiano)

- Prima del setaccio leggevamo il bytecode rc-1 (nessuna exception table in
  MoveDirector/executeTemporary) come un gap. Era design documentato: la
  verifica meccanica ora serve solo a confermare che il jar rc-1 contiene
  #2649 (merge 6 giorni prima del tag, quindi si').
- Il costo reale nostro: il finisher TASK-638 e' fatal-by-design
  (IllegalStateException, javadoc in ViolationScopedSteepestFinisher.evaluate)
  e un throw costa il walk intero con restore da snapshot. TASK-641
  (compositi swap+stanza via Moves.compose) allarga la superficie di throw
  parziale: piu' parti per mossa, piu' punti di fallimento mid-move. Nel
  testo inglese i compositi sono dichiarati sperimentali opt-in, perche'
  la 641 non e' ancora ratificata.
- Prima di qualunque invio: /pstack:unslop sul testo, prima persona singolare
  nel nome di maintainer, e ogni risposta upstream gli passa davanti prima di
  partire.
