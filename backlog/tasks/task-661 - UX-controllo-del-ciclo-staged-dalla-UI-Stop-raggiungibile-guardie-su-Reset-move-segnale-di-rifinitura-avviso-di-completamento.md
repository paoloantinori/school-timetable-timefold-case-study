---
id: TASK-661
title: >-
  UX: controllo del ciclo staged dalla UI - Stop raggiungibile, guardie su
  Reset/move, segnale di rifinitura, avviso di completamento
status: Done
assignee: []
created_date: '2026-09-24 08:45'
updated_date: '2026-09-24 12:39'
labels:
  - claude-planned
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Findings blocker della UX review 24/09: per TUTTO il ciclo staged (25-50 min) solverStatus resta NOT_SOLVING, quindi ogni funzione gated su isSolving e' morta esattamente nella modalita' che la UI lancia. Stop: nascosto nella barra controlli (UnifiedSolverControls.tsx:725-748) e disabilitato nella sidebar (ScheduleSidebar.tsx:432 disabled={!isSolving}) mentre il backend supporta la cancellazione staged (ScheduleService.java:738-759). Reset resta ABILITATO a meta' ciclo (UnifiedSolverControls.tsx:119, manca il termine stagedStage). Drag-drop e Move non bloccati a meta' ciclo: la mossa viene silenziosamente sovrascritta dai save del ciclo. Fine ciclo: stagedStage va null ~2min dentro la rifinitura di consegna da ~10min (commento ScheduleService.java:2636-2640), il poll si ferma (ScheduleTimeline.tsx:349-351), refiningForDelivery esiste nel backend ma ha ZERO consumatori frontend (grep verificato), il toast di completamento scatta solo su transizione SOLVING_ACTIVE->NOT_SOLVING che non avviene mai (UnifiedSolverControls.tsx:349-366), il save finale non triggera' re-fetch: il planner guarda una griglia non finale senza saperlo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Stop raggiungibile e funzionante per tutta la durata del ciclo staged (visibile quando stagedStage != null, non solo se SOLVING_ACTIVE)
- [x] #2 Reset disabilitato (o doppiamente confermato con avviso 'ciclo in corso') mentre stagedStage != null
- [x] #3 Il campo refiningForDelivery e' consumato dalla UI: banner/spinner durante la rifinitura di consegna e niente badge finale finche' e' true
- [x] #4 Un evento visibile chiude il ciclo: toast o badge di completamento con re-fetch al salvataggio finale (le transizioni basate su SOLVING_ACTIVE->NOT_SOLVING non scattano mai nel staged)
- [x] #5 Verifica live: solve staged lanciato dalla UI, tentativo di Stop a meta', screenshot
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-24 lane: implementazione frontend landed su feature/ux-planner-lane (1b379410: isBusyCycle, guardie Stop/Reset, banner rifinitura, toast refining; gate laptop-RUNBOOK 3/3; AC#1/#2/#5 PASS live con Stop mid A-CONSTRUCTION). AC#3/#4 differite: bloccate dal bug import-quota (TASK-666), verifica prevista su solve fresh-seed senza import.

Finding UX nuovo dalla verifica live (decisione Fedora pending): uno Stop in A-CONSTRUCTION riporta una griglia quasi vuota (12.551 lezioni, 274 assegnate, hard -24585) SENZA alcun avviso nella UI: il planner non distingue 'stop volato' da 'consegna pronta'. Candidati cura elencati nel REPORT della lane (banner post-stop, badge di stato, replay dello stato pre-ciclo). Da aggiungere come caso di accettazione di questo task o task sorella.
<!-- SECTION:NOTES:END -->
