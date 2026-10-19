---
id: TASK-571
title: >-
  Follow-up TASK-567: chiusura per-item delle 6 premesse superate
  (296/292/291/280/281/332) al baseline -12
status: Done
assignee: []
created_date: '2026-08-29 12:48'
updated_date: '2026-08-29 12:48'
labels:
  - claude-planned
  - rivalutazione
  - metaheuristic
milestone: m-13
dependencies: []
priority: medium
type: spike
ordinal: 323000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up TASK-567 richiesto da mandato Fedora 29/08 ~15:00 (maintainer fuori casa): le 6 premesse superate della famiglia metaheuristic, chiuse collettivamente in TASK-569, valutate SINGOLARMENTE al baseline attuale (-12 con v2.1 ratificato). Domanda per ognuna: la conclusione pratica cambia qualcosa? Fonti: task completati (citazioni verificate nella scansione), config attuale letta (tabu-7, acl 1000, unimproved 600s/300s per-fase, pillar attivi, thinning adattivo TASK-500), re-benchmark pulito 29/08 (artefatto Fedora, dati citati dal mandato: curva ACL piatta delta max 37).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Sei valutazioni per-item, 3 righe ciascuna (conclusione originale / cosa cambia oggi / verdetto). ESITO GLOBALE: NESSUNA delle 6 cambia la conclusione pratica al baseline -12; tutte dichiarate e chiuse, nessun nuovo task di rivalutazione.

1. TASK-296 (acceptedCountLimit per-fase). Originale: "RULED OUT, ACL sweep senza sweet spot robusto, state-dependent" (synthesis 05/08). Oggi: la curva ACL e' piatta ANCHE sull'asse pulito (re-benchmark 29/08: delta max 37) e la leva acl moderna e' il thinning adattivo per-stream ratificato (TASK-500, tail-capping misurato), che rende il tuning ACL globale obsoleto per costruzione. Verdetto: CHIUSA (la domanda e' stata misurata pulita ed e' piatta).

2. TASK-292 (cartesianProductMoveSelector). Originale: "RULED-OUT dalla campagna" senza alcuna misura valida (la famiglia joint-moves fu esclusa, mai misurata). Oggi: al baseline -12 il residuo hard e' H34 timeslot-tier (filone import-locked, competenza finisher/multi-start), non room-tier; le mosse congiunte timeslot+room non hanno un bersaglio che B-ROOM + RoomTailFinisher non chiudano gia', e la superficie congiuta e' esplosiva in wall. Verdetto: NON RIAPRIRE; condizione di riapertura = se il rumore room-tier rientra nell'asse hard, banco joint-moves su asse pulito.

3. TASK-291 (subChainSwapMove). Originale: censito non-robusto/inerte (notte 04/08, asse H29). Oggi: mai implementabile nel modello attuale: non esistono chained/planning-list variables (l'unica entita' e' Lesson con timeslot/room; i blocchi sono gia' gestiti dai pillar moves in config e dai pass deterministici di consolidamento). Verdetto: CHIUSA STRUTTURALMENTE, non rivalutabile.

4. TASK-280 (switch a VND, aspetto timeout). Originale: "VND-FI INERTE sul ciclo staged". Oggi: l'inerzia era letta a 0hard-equivalenza, ma anche il vantaggio VND (-242 vs -6690) era massa H29 (pulito: -110): sull'asse pulito non resta ne' danno ne' beneficio dimostrato; il passaggio a phased solving ha assorbito la dimensione timeout (limiti per-fase). Verdetto: OBSOLETO/ASSORBITO, chiuso.

5. TASK-281 (unimprovedSecondsSpentLimit tuning). Originale: "flat sullo staged, limiti gia' al ceiling". Oggi: 600s fase 2 / 300s fase 5 in config; il wall e' governato dal thinning adattivo (tail-capping 932/1583/413 -> 529/669/512), non dai limiti di no-miglioramento; nessuna evidenza pulita che un retune muova qualcosa. Verdetto: CHIUSO (spazio dimostrato zero; il lever wall ratificato e' altro).

6. TASK-332 (pillar clean A/B). Originale: chiusura "il synthesis lo copre" (copertura ora invalida). Oggi: i pillar moves sono attivi in config di produzione (TASK-481 li cita attivi col tabu) e una loro A/B pulita ricadrebbe nella famiglia chiusa da TASK-569; il task era un esperimento-meta senza artefatto consegnato. Verdetto: ARCHIVIABILE, nessuna azione.
<!-- SECTION:FINAL_SUMMARY:END -->
