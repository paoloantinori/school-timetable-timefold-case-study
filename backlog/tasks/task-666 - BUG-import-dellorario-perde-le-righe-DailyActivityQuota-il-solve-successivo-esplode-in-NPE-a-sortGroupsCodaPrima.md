---
id: TASK-666
title: >-
  BUG: import dell'orario perde le righe DailyActivityQuota; il solve successivo
  esplode in NPE a sortGroupsCodaPrima
status: Done
assignee: []
created_date: '2026-09-24 12:39'
updated_date: '2026-09-25 08:07'
labels:
  - claude-planned
  - bug
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Trovata live dalla lane UX del 24/09 (commit 1b379410, REPORT ux-review-2409): il giro export→import fra istanze PERDE le 2.257 righe DailyActivityQuota (presenti nell'export dell'istanza servita, ZERO nel payload lane dopo import). Il ciclo solve-staged successivo sul profilo importato esplode: NullPointerException '"db" is null' a DayClusteredConstructionPhase.sortGroupsCodaPrima (riga 753): latest=null messo in mappa e getOrDefault non sostituisce i presenti-ma-null, su dati senza righe quota. Impatto prodotto: chiunque importi un orario (il bottone Importa dell'UI, i flussi A/B) si ritrova un profilo il cui solve successivo è rotto. Nota: la NPE in sortGroupsCodaPrima merita anche un guard proprio (presenti-ma-null), ma la causa radice è la perdita delle righe quota.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Export→import→export preserva il conteggio delle righe DailyActivityQuota (test con conteggio esplicito)
- [x] #2 Un ciclo solve-staged su un profilo importato arriva a consegna senza NPE
- [x] #3 La cura distinguere: righe quota perse nell'export vs scartate dall'import (la lane ha misurato export ricco, lane post-import a zero: sospetto lato import, da confermare)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-25 Fedora, diagnosi completa dal codice: il TRUNCATE di ImportService.truncateAllTables e' 'TRUNCATE ... RESTART IDENTITY CASCADE' e la cascata PostgreSQL spazza OGNI tabella con FK verso le 10 truncate: dailyactivityquota, professionalizzantiday, groupcalendarrule, recurringavailabilityrule (e chiunque altro referenzi teacher/studentgroup). insertAllWithJdbc re-inserisce SOLO le 10 della lista: dailyactivityquota e professionalizzantiday sono NEL payload SchoolSchedule (campi persistenti alle righe 1642/1645) ma non hanno insert; groupcalendarrule e recurringavailabilityrule NON sono nel payload: perse senza recupero dall'import (famiglia della trappola LabRotation documentata in CLAUDE.md). La NPE sortGroupsCodaPrima e' il sintomo: latestEmptyCell.put(group, null) + getOrDefault non sostituisce i presenti-ma-null, comparatore NPE.

SCOPE della cura: (a) insertDailyActivityQuotas + insertProfessionalizzantiDays in ImportService (payload-carried, insert puro); (b) guard NPE in sortGroupsCodaPrima (non mettere null nella mappa: getOrDefault torna a funzionare); (c) test export->import->export con conteggio quota preservato; (d) decisione da prendere a parte: groupcalendarrule/recurringavailabilityrule perse (estendere il payload o re-seed deterministico post-import): task separato se non si chiude qui.

2026-09-25 07:35 Fedora: cura landed 735599a5 (insert x2 + parita' payload-vs-DB che chiude la classe + warn payload-stanco + guard NPE + sequenze + test 2/2). Gates complete sul diff (/simplify 4-angle con 3 fix applicati, /code-review high 5-verifier con 3 cure applicate). AC#3 CHIUSA dalla diagnosi: righe presenti nell'export, scartate dall'import (nessun insert); AC#2 attesa dal round-trip vivo; AC#1 attesa dal round-trip vivo su lane PG (export servita -> import -> conteggio). Nota review: GroupCalendarRule/RecurringAvailabilityRule restano perse (TASK-454 traccia); finding direzione coda-prima filed TASK-669.

2026-09-25 08:25 round-trip VIVO su lane (log /tmp/uxlane-roundtrip.log): export servita 21.479.270 byte con 2257 righe quota -> POST import 8081 http 200 -> conteggio DB lane 0 -> 2257 (match esatto) -> log app 'Inserted 2257 DailyActivityQuota entities' + 'Payload parity verified for 12 carried tables'. AC#1 CHIUSA con verifica meccanica. AC#2: ciclo solve-staged di verifica lanciato sulla lane (profilo importato col jar 735599a5): esito al check.

2026-09-25 10:08 AC#2 CHIUSA (verifica viva, log /tmp/planner-uxlane-roundtrip.log + status): ciclo solve-staged sul profilo importato partito 08:53, ZERO NullPointerException (il bug del task e' morto), ciclo completato e chiuso pulito ('cycle closed; authoritative pins 274 -> 274', saveSchedule, campi wrapper resettati). La qualita' della consegna resta doomed-basin ([-13149/0]hard, 234-320 buchi H17 in costruzione, predictor che scarta e reseeda): FIRMA ATTESA della perdita GroupCalendarRule (TASK-454, non nel payload): senza calendari di gruppo H20/VRP-calendar sono ciechi. Non e' una regressione del 666: e' il gap noto tracciato da TASK-454, che con il 666 chiuso diventa il prossimo anello per rendere un profilo importato realmente ri-solvibile. TASK CHIUSO: 3/3 AC.
<!-- SECTION:NOTES:END -->
