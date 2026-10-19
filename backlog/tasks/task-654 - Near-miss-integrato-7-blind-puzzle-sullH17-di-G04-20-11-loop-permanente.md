---
id: TASK-654
title: 'Near-miss integrato-7: blind puzzle sull''H17 di G04 20/11 (loop permanente)'
status: Done
assignee: []
created_date: '2026-09-23 09:30'
updated_date: '2026-09-27 05:29'
labels:
  - claude-planned
  - zero-hard-loop
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PRIMO CASO del loop permanente near-miss->blind-puzzle (rinforzo maintainer 23/09 "di continuo", memoria near-miss-blind-puzzle): integrato-7 ha consegnato [-1/0], residuo = 1 H17 (buco interno 1h) su G04 20/11, classificato dal laptop come fuori grammatica 638 (timeslot-only) e fuori target 641 (room-aware): famiglia HRF (hole). MA HRF e' nella catena di consegna PRIMA del 638 e il buco e' rimasto: o la grammatica HRF ha mancato la cura (classe mancante da scoprire col puzzle) o il buco e' strutturale in quello stato. PROTOCOLLO (memoria near-miss-blind-puzzle + blind-puzzle-full-constraints): (1) recuperare l'export integrato-7 dal laptop (~106MB, gia' in catalogo room-faithful); (2) decomposizione + anatomy della cella G04 20/11 (residual_decomposition.py + day_anatomy_viz.py); (3) puzzle NEUTRO con TUTTI i vincoli hard e il COST MODEL REALE, goal = delta punteggio (mai condizione visiva, lezione integrato-6); (4) cieco zero-context; (5) arbitrato Java/meccanico sul capture; (6) VERDETTO DI CLASSE: mossa nuova = meccanizzare; cura esistente ma non trovata = gap di ricerca nella famiglia (HRF); NO SOLUTION con surplus verificato = strutturale. Zero-compute sulla macchina: il solve della macchina non serve, il banco di arbitrato si gira sul capture esistente.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-27 PROTOCOLLO COMPLETO (eseguito 23/09, chiusura differita): v1 cieco illegale (T24 VAC ven), v2 regole incomplete, v3 catena 3-mosse trovata MA pure illegale (T22 VAC ven, prova meccanica forced657). VERDETTO DEFINITIVO: STRUTTURALE con prova meccanica (ogni catena muore su Vacation ven-di' o occupazione @11 dei partner). La cura richiede input dalla scuola (altro docente o calendario diverso). Derivati: TASK-656 (SOURCE-GATE pool-cap, coherence fix) e TASK-657 (cross-group unblock, RATIFICATO come bounded-no-op insurance). TASK CHIUSO.
<!-- SECTION:NOTES:END -->
