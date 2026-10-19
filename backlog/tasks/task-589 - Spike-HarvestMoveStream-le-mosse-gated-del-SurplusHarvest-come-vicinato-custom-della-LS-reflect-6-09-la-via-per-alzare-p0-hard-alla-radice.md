---
id: TASK-589
title: >-
  Spike HarvestMoveStream: le mosse gated del SurplusHarvest come vicinato
  custom della LS (reflect 6/09, la via per alzare p(0-hard) alla radice)
status: Done
assignee: []
created_date: '2026-09-06 19:51'
updated_date: '2026-09-06 20:14'
labels:
  - claude-planned
  - search-side
  - spike
dependencies: []
references:
  - claudedocs/orchestration-layer-architecture.md
  - claudedocs/spalmone-2026-27/task584-results/
  - claudedocs/spalmone-2026-27/task577-results/v123-6sep/
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal reflect 6/09 sera su domanda di maintainer: le mosse gated dei pass deterministici diventano una stream di vicinato DENTRO la LS (fase 2), cosi' il solver le campiona lui con lo score pieno (medium incluso: i nostri oracoli sono hard-only) e la costruzione dei bacini ne beneficia a monte (p(0-hard) su per costruzione, gate m-13). SPIKE: (1) estrarre l'enumerazione gated di SurplusHarvest (donorCandidates+findEmptyDeclaredDays+firstWindowOpenSlot+gate-chain) in una classe condivisa HarvestNeighborhood; (2) MoveListFactory che genera le triple (donor, cell, landing) come mosse single-lesson; (3) cablare la stream nella fase 2 LS dietro flag config DEFAULT OFF; (4) PROVA al banco live-state (SurplusHarvestLiveStateTest o gemello): LS-with-stream vs LS-without a parita' di budget, delta H34/hard. Base di partenza misurata: la LS non campia MAI il salto lungo-raggio (TASK-577: mossa 16/09->9/06 migliorante mai campionata); i pass chiudono 8 celle quando sparano. SECONDO passo (solo se il banco dice si'): QFR-stream (stessa forma, donor coda/bordo). TERZO: composite (tail-swap/catena) - delicati in LS (undo, tabu per entita'). GUARDIE: fonte unica dei gate (mai duplicare pass<->selector); i pass deterministici RESTANO come chiusura di coda; adaptive-weights TASK-500 thina per stream (peso iniziale da misurare); adozione solo banco + ciclo di validazione (direttiva ITERAZIONE SU SNAPSHOT). Rischio tecnico principale: il move-selector gira nell'HOT PATH: l'Occupancy e' un indice cold ricostruito per pass: nella LS serve un indice live (shadow variables o listener) o la rigenerazione per step va misurata.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
RESEARCH COMPLETATA 6/09 sera (claudedocs/research_timefold-custom-moves_2026-09-06.md, exhaustive, 10 fonti): DUE vie entrambe sulla nostra 2.3.0 (verifica DIRETTA nel jar locale: 65 classi preview.api). RACCOMANDAZIONE: (1) via classica MoveListFactory come figlio dell'unionMoveSelector della fase 2 con fixedProbabilityWeight piccolo: il nostro adaptive-weights TASK-500 eredita la stream senza codice nuovo, tabu-state intatto; EVITARE FairSelectorProbabilityWeightFactory (93 mosse gated vs milioni di change: over-campionamento di 4 ordini); il costo cacheType STEP e' benigno (le gated sono pocuhe). (2) prototipo parallelo nella Neighborhoods API preview (MoveProvider: enumerazione dichiarativa con filtri solutionView e aggiornamento incrementale incluso: risolve il problema dell'indice live; asCachedDataset+buildMoveStream per le chain), che vale anche come credenziale per il feedback PR-2610/#2254. Limiti preview oggi: niente pesi fra provider, sostituisce i selector (rinuncerebbe ai pillar non-merged): non migrabile in toto. BENCHMARK: SI, nativi: MOVE_COUNT_PER_TYPE + PICKED_MOVE_TYPE_BEST_SCORE_DIFF via describe(), e moveListFactoryCustomProperties tweakeabili dal benchmarker: la misura del TASK-589 ha gia' le statistiche pronte.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
