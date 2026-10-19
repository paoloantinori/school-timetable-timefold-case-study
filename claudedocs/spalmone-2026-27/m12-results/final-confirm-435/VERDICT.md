# TASK-435 final-confirm-435, VERDICT (checkpoint, 2026-07-28 17:07)

Modalita': CHECKPOINT, non adoption-gate. Il cert live (staged completo ~2h) era lo
strumento sbagliato per TASK-435: il verdetto (boxed-T01) e' una questione da
checkpoint (harness fine-costruzione step 4 + stato DB). Il solve live e' fermato a
B-TIMESLOT-1 perche' paralizzato dall'H16b pre-esistente (room-based, vedi TASK-446),
non da TASK-435. Il solve completo (adoption gate) si rifara' sull'architettura veloce
post-TASK-446-B.

## Colonna vs final-confirm (-41hard baseline)

| metrica | final-confirm (baseline) | final-confirm-435 (TASK-435, flag ON) |
|---|---|---|
| T01 boxed (H0) | ~41 (il residual -41hard) | 0 (PINNED 231h, 0 boxed) |
| T01 pinned con room | 0 | 231/231 (room-fix: nessuna room-less) |
| on-non-Thursday | n/a | 0 (tutti giovedi', H5 rispettato) |
| overall hard (mid-solve B-TIMESLOT-1) | -41 (final, post-B-ROOM) | -928 (H16b -963 dominante, pre-esistente; B-ROOM la fisserebbe a fine) |
| full-day rate | (baseline) | 78.2% (mid-solve, gia' sul target ~78) |
| buchi interni | (baseline) | 15 giorni / 15 slot (mid-solve) |

## VERDICT

TASK-435 elimina il residual sistemico alla fonte: T01 boxed 41 -> 0.
Il pin constrained-teacher (231h sui 35 giovedi' golden, one-group-per-Thursday
disgiunti, room assegnata) riproduce il golden ed e' verificato in produzione
(Phase 0: 231 pinned, 0 skipped, 0 null-room). T07 72 skippati (conflitto MODA
con pin calendario, corretto, il solver lo gestisce; non e' il nodo sistemico).

L'overall hard -928 (mid-solve) NON e' TASK-435: e' la H16b paralysis pre-esistente
(consecutive same-subject different-room; la LS timeslot non puo' muovere le room, le
fissa solo B-ROOM ultimo stage). Il baseline final-confirm faceva lo stesso (raggiungeva
-41 solo post-B-ROOM). TASK-446-B (alternativa B: costruttore room=null ai non-pinnati)
elimina questa paralysis e permette il re-cert full-solve veloce.

## Cattura

./scripts/benchmark/capture-run.sh claudedocs/spalmone-2026-27/m12-results/final-confirm-435
artefatti: analysis.json, KPI (fill/daystart/cadence/holes/resemblance/moda-knot),
confronto-final-confirm-435.html, export.json.gz.

## Da fare (post-TASK-446-B)

Re-cert TASK-435 full-solve sull'architettura veloce (room-decoupled): hard 0 veloce +
KPI tenute (full-day ~78, day-start >=99.3, emptySpan 0, ricorrenza >=72). La ratifica
di adozione resta a maintainer; Fedora verifica sui raw.
