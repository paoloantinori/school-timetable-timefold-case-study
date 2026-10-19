---
id: TASK-468
title: >-
  Vincolo M 'no 2 ore vicine della stessa materia teorica' (tab Vincoli,
  decisione maintainer: medium, residuo calibrato golden)
status: Done
assignee: []
created_date: '2026-07-31 21:04'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: medium
ordinal: 291000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fonte: tab 'Vincoli DA ANALIZZARE' del golden xlsx ('Non ci devono essere 2 ore vicine della stessa materia teorica; i laboratori, compreso disegno o informatica, possono avere piu' ore vicine'). Decisione maintainer 2026-07-31 sera: e' ASSOLUTAMENTE un vincolo M (medium), non H e non solo-notifica. Il golden la viola (quanto: da misurare), quindi pattern M24/M25: medium a residuo calibrato, mai zero-tolerance. INTERAZIONI DA RISOLVERE IN DESIGN: (a) M15 spinge le ripetizioni same-day alla consecutivita' (medium 20/min-gap): combinata con questa regola l'effetto netto sulla teoria e' 'non ripetere la materia teorica nello stesso giorno', quindi la forma candidata pulita e' M21-style a soglia 1 per le materie teoriche (la soglia M21 era 1 pre-TASK-381, alzata a 2 golden-first) piu' che una penalita' sulla coppia adiacente; (b) S17 premia +5 ogni coppia adiacente TEORIA INCLUSA: valutare esclusione teoria da S17 o peso netto; (c) whitelist M21_EXEMPT_SUBJECTS e lab-exemption riusabili ('compreso disegno o informatica' = i nostri lab-tagged); (d) nota contraria di T06 nello stesso tab ('non 1 ora info e 1 ora gest ma stessa materia'): eccezione per-docente da chiarire con referente. Percorso: (1) misura sul golden del residuo (giorni con 2+ ore stessa materia teorica, coppie adiacenti teoria) per calibrare peso e forma; (2) design breve con scelta forma e pesi; (3) prototipo flag default-OFF; (4) A/B full-solve con guardie su M20/M15/full-day/ricorrenza; (5) adozione = maintainer.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Misura golden del residuo (conteggi per materia/gruppo) committata
- [x] #2 Design con forma scelta (soglia-1 teoria vs coppia adiacente), pesi e risoluzione interazioni M15/S17/M21. FATTO (zai notte4-seguito): `task468-results/AC2-design-2026-07-31.md`. Forma = soglia-1 teorica (M21-style, ONE_MEDIUM x (ore-1), knob default 1 ordine M24/M25). Interazioni M15/S17/M21 risolte (M15 forza adiacenza se repeat, S17 resta teoria-inclusa, M21 convive a soglia 2). Set teoria proposto (borderline Figurino/Personalizzazione/Tecnologia-e-creativita' = teoria di default) + alias T06 come hook. Design-only, NESSUNA implementazione (prototipo = AC#3 dietro flag default-OFF)
- [x] #3 Prototipo flag default-OFF byte-identico a OFF + unit test
- [x] #4 A/B full-solve con KPI standard, nessuna regressione strutturale
- [ ] #5 Nessuna adozione senza maintainer
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
COMPLETO con esito onesto: vincolo M-TR implementato dietro flag planner.constraints.theory-adjacent.enabled DEFAULT OFF (medium, soglia-1 teorica). Evidenza: sweep peso 1-1000 WEIGHT-INERT (residuo 1694->1679, ~1%; diagnosi: i blocchi 2h teoria NASCONO in COSTRUZIONE, 1672 a fine CH vs 1679 finale, unpinned 98,6%) + live A/B NEUTRALE (0hard entrambi, 468-results f48103e3). Il peso e' il lever sbagliato; la pratica teoria e' dichiarata come input nel workbook TASK-471 (foglio 22). Verdetto sweep: task468-results/VERDICT-sweep468-2026-08-01.md. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
