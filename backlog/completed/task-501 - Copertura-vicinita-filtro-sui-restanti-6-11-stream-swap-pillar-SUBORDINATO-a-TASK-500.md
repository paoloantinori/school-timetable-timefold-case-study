---
id: TASK-501
title: >-
  Copertura vicinita'/filtro sui restanti 6/11 stream (swap/pillar) -
  SUBORDINATO a TASK-500
status: Done
assignee: []
created_date: '2026-08-15 17:11'
updated_date: '2026-08-16 07:05'
labels:
  - claude-planned
  - enterprise-reimpl
  - subordinated
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Estendere il filtro TASK-499 (o il suo successore adattivo TASK-500) agli altri 6/11 stream dell'union move selector: swap/pillarSwap/pillarChange oggi non coperti (il filtro agisce solo sui 5 change-move stream timeslot; l'Enterprise copre tutto via ValueSelector sotto ogni move selector). SUBORDINATO a TASK-500: se i pesi adattivi funzionano, la copertura swap/pillar diventa un parametro del meccanismo adattivo stesso (lo stream impara il suo proprio peso), non un filtro separato; da rivalutare dopo il verdetto di TASK-500. Riferimento design: claudedocs/design/nearby-selection-community.md + finding 6.1 della research 15/08.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Nessuna modifica al codice: verifica a sola lettura (il thinning e' a livello decider, non selector). Esito: copertura completa strutturale; nessuna estensione necessaria.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso con verifica documentata (Filone 2 mandato 16/08): il thinning forma B copre gia' TUTTI gli stream del decider M-T per costruzione. Il punto di strozzamento e' unico: MultiThreadedLocalSearchDecider.decideNextStep chiama AdaptiveMoveWeights.shouldEvaluate(move) su OGNI mossa prodotta dal move repository (sotto ogni figlio dell'union selector) prima del dispatch; record() conta ogni risultato nell'unico loop consumatore. Il buco strutturale di TASK-499 (filtro solo sui 5 change-move stream timeslot via ValueSelector) per forma B NON PUO' esistere. Verifica su codice 16/08: (1) i 6 stream di produzione (B-TIMESLOT change/swap/pillarChange/pillarSwap timeslot + B-ROOM change/swap room) sono tutti classificati dalla MoveTypeClassifier, pinnata da AdaptiveMoveWeightsTest.classifierTable con le classi Timefold REALI (SelectorBased*Move); (2) swap E' nel learning (TS_SWAP/ROOM_SWAP adaptive=true); (3) pillar sono dispatchati attraverso il choke point ma fuori dal learning per scelta con ragioni forma-B-valide (contratto dei pesi ratificati 2/1, evidenza A/B TASK-500 misurata con pillar fuori, costo sub-pillar = leva wall grossa che si merita gate proprio); il motivo PROBABILISTIC nel javadoc enum e' un resto di forma A, la conclusione no; (4) OTHER/unassign-move (allowsUnassigned=true su entrambe le variable) passano dal choke point, dispatchate sempre, fuori dal learning: granularita' benigna. Follow-up facoltativo unico: adaptive=true sui pillar + A/B dedicato. Doc: claudedocs/spalmone-2026-27/task501-results/VERDICT-task501-copertura-thinning-2026-08-16.md
<!-- SECTION:FINAL_SUMMARY:END -->
