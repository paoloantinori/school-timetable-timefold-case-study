---
id: TASK-671
title: >-
  Consolidare pool-cap coherence: accessor unico per 7 siti HRF/EDF + fidelity
  nei banchi + census
status: Done
assignee: []
created_date: '2026-09-27 05:43'
updated_date: '2026-09-27 19:12'
labels:
  - claude-planned
  - solver
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up del code-review TASK-656 (97e32d84, 27/09): F1 = il fix pool-cap-aware e' cablato solo in 2 dei ~7 siti paralleli quota-foglio in HoleRelayFinisher (findTailOnlyTolerantDonor 506, needsBackfill 636, hostShort 401, landing-overshoot 356/578, EmptyDayFinisher.donorDayYields 616/631 valutano ancora contro la quota foglio che il commit stesso dichiara obsoleta dal TASK-577: per pool groups i bracci ratificati 620/621 restano morti). F2 = tre flag/due discipline per un concetto: il gate usa live isM20CapPoolEnabled (497) mentre expectedHoursCapFor usa live poolCapFillEnabled (577) e capSupply usa captured isM20CapPoolArmed. F3 = DeliveryTrioBenchTest (l'harness di evidenza standard per HRF) carica fixture SENZA pubblicare la mappa pool-cap: ogni replay gira con gate non-armato e produce 'regression identica' per costruzione. F4 = OffCapture638BenchTest pubblica statics senza teardown (stale-inheritance per il prossimo harness nel fork). F6 = nessun census osservabile della nuova branch. La cura naturale: un accessor SchoolSchedule.poolCappedQuota(groupId, date, sheet) che decide la politica flag una volta, consumato da tutti e tre i siti.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 poolAwareYieldLine (o equivalente accessor centrale) consultato da TUTTI i 7 siti quota-foglio in HoleRelayFinisher e EmptyDayFinisher
- [x] #2 Un solo accessor decide la politica del flag (armed vs live, 497 vs 577) per tutti i consumatori: capSupply, expectedHoursCapFor, gate HRF
- [x] #3 DeliveryTrioBenchTest pubblica la mappa pool-cap nei suoi fixture (oggi gira con gate disarmato)
- [x] #4 Census/banner osservabile: HRF-IMP-SOURCE-GATE distingue donatori ammessi dalla linea pool-cap da quelli ammessi dalla linea foglio
- [x] #5 Banco: replay di un bacino pool-cap (1/G08) attraverso il banco verificato
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-27 19:15 TUTTE LE AC VERIFICATE: (1) accessor unico poolCappedQuotaForDate cablato nei 7 siti (b3a6aec7), (2) poolAwareYieldLine delega all'accessor centrale, (3) DeliveryTrioBenchTest pubblica la mappa pool-cap dal proprio export (0524087b), (4) census HRF-IMP-POOL-CAP-LINE nel SOURCE-GATE (636fe67e), (5) banco OffCapture638BenchTest PASSA con fidelity attiva e -Dhrf.debug=true (nessun regresso). Suite completa verde 1391/0. TASK CHIUSO.
<!-- SECTION:NOTES:END -->
