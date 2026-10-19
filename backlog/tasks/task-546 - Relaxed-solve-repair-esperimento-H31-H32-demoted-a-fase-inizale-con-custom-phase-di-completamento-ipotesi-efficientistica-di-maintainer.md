---
id: TASK-546
title: >-
  Relaxed-solve + repair: esperimento H31/H32 demoted a fase inizale con custom
  phase di completamento (ipotesi efficientistica di maintainer)
status: To Do
assignee: []
created_date: '2026-08-24 08:24'
updated_date: '2026-08-24 08:31'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla discussione 24/08 con maintainer (riformulazione efficientistica della sua ipotesi "barattare PHR con i vincoli"): la domanda non e' qualita' finale ma EFFICIENZA DI RICERCA: piu' vincoli hard rendono piu' difficile trovare la soluzione (fattibilita' piu' rara, bacini peggiori, LS lessicografica cieca al medium finche' hard>0)? Conviene risolvere una versione RELAXED (H31/H32/H33 demoted) e completare con custom phase? La risposta tecnica: e' un pattern noto (relaxation/fix-and-optimize, strategic oscillation) e il nostro ciclo lo pratica gia' a pezzi (fasi sequenziali, ExactFinisher sui boxed, PHR sui buchi). La condizione critica: i riparatori per le famiglie rilassate devono esistere ed essere completi. Oggi H17/H0 hanno riparatore; H32 no (QuotaFillRepair chiude 26%), H31/H33 no. Precedenti rilevanti: l'era pre-H31/H32/H33 consegnava 0hard routine (asta piu' bassa), dal 19-20/08 mai piu'. Confronto letteratura: blog De Smet 2018 + research_mt-ls 23/08. La versione QUALITA' della stessa idea (togliere i vincoli perche' dannosi) e' stata bocciata con evidenza (golden 0hard su H31/H32/H33, iter-18): i vincoli sono satisfabili, la difficolta' e' della ricerca; qui si cambia solo l'ORDINE di incontro, non il traguardo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CONDIZIONE PRELIMINARE: costruire i riparatori mancanti prima del run demoted (H32 quota: estensione QuotaFillRepair oltre il 26% misurato; H31 day-start: riparatore dedicato); senza di essi il run demoted consegnerebbe violazioni scoperte
- [ ] #2 Esperimento dietro flag default-OFF: run staged con H31/H32 (e valutare H33) demoted a fase iniziale, ripristinate e riparate a valle con le custom phase
- [ ] #3 Misure: pick-best hard/medium del run demoted+repair vs run standard (stesso protocollo predizione-verifica, n>=2)
- [ ] #4 Verdetto esplicito: la fattibilita' intermedia piu' larga migliora o peggiora la ricerca? (ipotesi di maintainer: la versione con meno vincoli si risolve piu' facilmente e il repair custom completa)
- [ ] #5 I vincoli restano nel verdetto finale: si cambia solo l'ordine con cui la ricerca li incontra, nessun cambio di modello senza maintainer
- [ ] #6 Nessun lavoro prima che TASK-543 fase 2 (single-writer) chiuda
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DISEGNO SPARAGGIUNTO (24/08, da domanda di maintainer): la meta' RIPARATORI si valida su snapshot (checkpoint POST_RELEASE/post-B-ROOM di run standard, H31/H32 riattivate sullo stato, lancio riparatori: minuti per iterazione, stesso metodo della curva PHR); la meta' BACINO richiede run veri ma accorciabili al confine post-RELEASE (~20min/braccio invece di ~1h, CRN appaiate, tier hard timeslot come asse comparabile).
<!-- SECTION:NOTES:END -->
