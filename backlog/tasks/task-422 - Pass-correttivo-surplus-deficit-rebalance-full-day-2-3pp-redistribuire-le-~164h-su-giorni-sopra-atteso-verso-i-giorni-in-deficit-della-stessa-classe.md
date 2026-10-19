---
id: TASK-422
title: >-
  Pass correttivo surplus->deficit rebalance (full-day +2-3pp): redistribuire le
  ~164h su giorni sopra-atteso verso i giorni in deficit della stessa classe
status: Done
assignee: []
created_date: '2026-07-26 20:50'
updated_date: '2026-07-27 04:47'
labels:
  - claude-planned
dependencies:
  - TASK-408
priority: medium
ordinal: 245000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Decomposizione train-run1 (2026-07-26 notte, verificata sui raw): deficit in-piano 1.420h = G14 875h (dati, domanda scuola) + FC 318h (tensioni fogli note) + 227h su 6 gruppi, di cui 164h hanno SURPLUS corrispondente su 126 giorni sopra-atteso della stessa classe - l'unico margine solver reale verso il full-day golden 85,4%. M20 punisce il deficit ma niente punisce il surplus, quindi la LS parcheggia ore oltre il piano. Proposta: pass deterministico famiglia HoleRepair/BoxRepair, post-release nel ciclo staged (e/o in CompactionPhase), che sposta un'ora da giorno-surplus a giorno-deficit della stessa classe se i gate passano (H1/H3/H7/H11/H17 + no de-anchor M18); ranking donatori/bersagli gia' pronto con marginalDeficitCost/A4; improve-or-noop con invariante fail-closed come runHoleRepair. Interamente checkpoint-validabile (matrice test localizzati: harness su snapshot train-run1, attesa full-day 75,5->~78 e deficit-days giu' senza nuovi buchi/split M15). Upside oltre le 164h dirette: anche il deficit non-matched cala se il rebalance libera slot che HoleRepair/M20 poi riempiono. NON toccare finche' il gate 419 non e' ratificato.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AGGIORNAMENTO scope (maintainer 2026-07-26 sera): entra nel TRENO DI STANOTTE se i checkpoint promuovono (implementazione lanciata ~23:10). Correzione framing della description: il riferimento 'G14 62%' era deficit assoluto, NON differenziale - il gap reale vs golden e' 197 giorni concentrato su stage-window+FC, e le ~164h con surplus corrispondente ne sono il bersaglio (vedi correzione in TASK-408).

DoD esplicita richiesta da maintainer (23:20): /simplify + /code-review high sul diff 422 PRIMA di chiamarla completa - sequenza: checkpoint chain -> /simplify (applicati) -> /code-review high (confermati corretti) -> suite EXIT=0 -> commit/push -> dispatch run3 laptop.

DIREZIONE v2 (maintainer 2026-07-27 ~00:15, criterio alto livello): (a) 'peggiorare temporaneamente ok SE poi ricostruisce' -> il veto M18 e' corretto solo perche' oggi nulla ri-ancora dopo il pass; v2 = prelievo + RICOMPATTAZIONE GATED del giorno donatore (slide-left dei rimanenti con gate H1/H3/H7/H11), che rende il de-ancoraggio stato intermedio e sblocca il controfattuale 66 mosse/96.5k senza pagare day-start. Checkpoint-testabile. (b) regola human-planner 'mai sopra la capienza del giorno, resto in panchina': in COSTRUZIONE gia' rispettata (stamp cap TASK-385 + box-the-residual TASK-398/406); la viola la LS perche' NIENTE penalizza il surplus. Misurato: golden 70 giorni sopra-atteso, noi 126-136 (2x). Candidato vincolo medium anti-surplus calibrato sul livello golden (70 lecito, mai piu' rigido della pratica), che ridurrebbe a monte il lavoro del rebalance. Peso da benchmark.

v2-SLIDE BOCCIATA (maintainer 2026-07-27 ~00:40): la ricompattazione slide-left del giorno donatore muoverebbe K lezioni (celle template + match settimana-precedente) - deleterea per la ricorrenza, pattern generali si romperebbero. Il veto M18 resta LA protezione; il volume extra si cerca a monte con l'anti-surplus (nuovo task M33), non con cascate.

LIVE x2 (train2): 16h e 33h mosse, surplus interno 79->69 e 99->77, M20 day-fill -12,5k/-36k, fail-closed MAI scattato, 0 DE-ANCHORS, budget mai raggiunto. Come previsto la popolazione e' ristretta da M33 (checkpoint 35 su smoke2 pre-M33 vs 16-33 live post-M33). Il pass fa il suo lavoro; il residuo sopra-golden e' strutturale (nessun ricevente legale) -> TASK-424.

Adottata col treno-2 (ratifica piena 2026-07-27): live x2 pulita, DoD completa. Chiusa; la rimisura post-M33/424 vive nei watch di m-12.
<!-- SECTION:NOTES:END -->
