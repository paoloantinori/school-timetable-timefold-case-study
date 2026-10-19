---
id: TASK-526
title: >-
  BUG bloccante: run completato 0hard ma DB persiste 2859 lezioni senza stanza
  (consegna CH-only, famiglia TASK-509 all'hard)
status: Done
assignee: []
created_date: '2026-08-19 06:01'
updated_date: '2026-08-19 06:30'
labels:
  - claude-planned
  - solver
  - bug
  - persistence
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
BUG BLOCCANTE trovato da maintainer in ispezione 19/08: run golden-free completato (NOT_SOLVING, score 0hard in-memory) ma il DB persiste solo 9635/12494 stanze (verificato con query diretta: 2859 lezioni collocate senza room_id). La UI mostra correttamente H29 perche' legge il DB. Meccanismo candidato (gia' diagnosticato al round 3 della chain): il tentativo vincente era doomed (skip-doomed-room salta la LS stanze), la corner-b refinement del wrapper doveva rifinire prima della consegna ma qualcosa non ha completato/persistito. Famiglia TASK-509 (score-vs-DB divergence) ora all'hard tier. E' la stessa firma della consegna CH-only.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Ricostruire dal log del run (app.log del re-solve 19/08 mattina) la catena esatta: tentativi girati, verdetto doomed per tentativo, corner-b refinement partita/completata, timing del salvataggio finale
- [ ] #2 Identificare perche' il DB ha 9635/12494 stanze (firma CH-only?) mentre lo score in-memory e' 0hard con stanze complete: skip-doomed-room ha saltato la refinement del vincitore? La corner-b e' partita e non ha salvato? Il save ha tagliato?
- [ ] #3 Cura identificata e implementata (o workaround documentato se la cura e' search-side)
- [ ] #4 Riproduzione verificata: dopo il fix, un run completo persiste 12494/12494 stanze nel DB e la UI non mostra H29
- [ ] #5 Collega e chiudi il finding UI nel TASK-525 (i contorni rossi erano il sintomo)
<!-- AC:END -->

## Implementation Notes

<!-- NOTES:BEGIN -->
SECONDA META' CONSEGNATA (5/09 zai, cdc6844c): il metro di fiducia score-vs-DB al confine di consegna del wrapper multi-start. verifyDeliveryHardConcordance riconta la famiglia H29 nei due mondi (griglia consegnata in memoria vs query DB fresca, LEFT JOIN espliciti per le righe course/teacher-null) subito dopo la save di consegna; reportDeliveryConcordance rende la divergenza LOUD (LOG.error + lastSolverError in APPEND, cosi' l'eventuale eccezione della restore-save sopravvive come CAUSA) e non blocca MAI (try/catch anche del check stesso: thread demone). Predicato H29 ESTRATTO condiviso (Lesson.isH29Roomless: provider + riconteggio non possono driftare). TDD 3/3 H2, /simplify, code-review high, suite 1266/0/0/40. LA RIGA OK "delivery concordance OK (H29 room-null memory=N db=N)" appare ora a ogni consegna: la metrica diventa di routine.

RACCOMANDAZIONE DEFERITA (code-review F1, area della cura primaria fd5872d4/169fba74): sul path in cui il winner e' l'ULTIMO tentativo rifinito IN CICLO (non room-skipped, restoreNeeded false), NESSUNA save del wrapper gira dopo le mutazioni FCC post-pick-best: la memoria post-FCC viene confrontata con un DB pre-FCC e le mosse FCC restano solo nel log. La metrica H29 non lo vede (FCC muove timeslot, non room), ma e' la stessa famiglia di perdita-persistenza della cura primaria: valutare l'estensione della save incondizionata a quel confine (o una persistTail dopo FCC).

SCOPO DICHIARATO (code-review B, by design): il check vive SOLO nel wrapper multi-start (percorso di campagna, DEFAULT ON); il /solve ordinario e l'opt-out multistart consegnano senza concordanza.
<!-- NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 06:30
---
VERDETTE MECCANICO (19/08 mattina, run 0hard in ispezione): NON e' un bug di persistenza. DB: 2859 lezioni senza stanza, ma la query di scomposizione dà 0 (ZERO) lezioni senza stanza tra le unpinned non-pseudo: tutte le 2859 sono pin di import-calendario (MODA) o pseudo-Stage, che NON hanno stanza PER PROGETTO (H29 le esente, come da ratifica TASK-447). Le 9.635 con stanza = TUTTE le lezioni schedulabili. L'in-memory 0hard e il DB coincidono esattamente (contratto TASK-509: DB = verita'). L'ipotesi CH-only/corner-b persa NON regge su questo run: la room-LS del vincitore e' stata persistita per intero.
---

created: 2026-08-19 06:30
---
Il 9635/12494 della segnalazione e' la frazione attesa (12494 - 2859 pin/pseudo = 9635). Chiusura proposta: no-fix; se la UI vuole mostrare stanze anche per i pin lab, e' un item cosmetico separato.
---
<!-- COMMENTS:END -->


## DIAGNOSI MECCANICA (Fedora, 5/09, dal codice a mano)

IL CANDIDATO E' IL GATE `restoreNeeded` SULLA SAVE POST-RIFINITURA (ScheduleService ~2626):

1. Quando il winner e' un condannato, il wrapper lo rifinisce (corner-b TASK-498):
   `refined = runRoomSolveStage(result.best().solution(), false)` e poi SOSTITUISCE
   `result.best().solution()` col refined (~2614-2618). Il commento li' dice
   "fall through to the restore path with the improved solution, WHATEVER
   restoreNeeded said (re-saving is cheap and always correct here)".
2. MA la save subito sotto resta GATE-ATA su `if (result.restoreNeeded() && ...)`:
   il commento dichiara l'intento incondizionato, il codice lo condiziona.
3. `restoreNeeded()` = `best.attempt() != attemptsRun`. Se il winner condannato e'
   L'ULTIMO tentativo (budget-break / all-doomed con best=ultimo), restoreNeeded e'
   FALSE -> LA SAVE NON PARTE -> la griglia RIFINITA (stanze ricostruite, eventuale
   0hard in-memory) NON PERSISTE. Il DB resta allo stato che il room-stage CH-only
   del tentativo stesso aveva salvato durante il suo solve (degli event): le stanze
   NULL del CH-only. Esattamente la firma ispezionata da maintainer il 19/08.

LA CURA (una riga + TDD): dopo il blocco rifinitura, salvare SEMPRE quando la
rifinitura e' corsa (o semplicemente togliere il gate su quella save: il commente
stesso dice che e' sempre corretta). Test discriminante: winner = ultimo tentativo
condannato con stanze CH-only -> dopo il wrapper il DB deve contenere le stanze
rifinite (room_id non-null), non le CH-only.

LA SECONDA META' (dalla roadmap esterna): check automatico di concordanza
hard score-vs-DB al confine di consegna (riconteggio H29 da DB vs score in-memory:
divergenza = WARN loud + lastSolverError), stesso spirito del doctor TASK-579.
Da implementare insieme: la cure chiude il canale noto, il check rende loud
qualsiasi canale futuro della famiglia.

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
