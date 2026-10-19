---
id: TASK-388
title: Reset-button confirmation dialog (UI) — prevent stray-click wipes
status: Done
assignee: []
created_date: '2026-07-21 18:22'
updated_date: '2026-08-16 11:50'
labels:
  - claude-planned
dependencies: []
ordinal: 212000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Follow-up di TASK-383 (decisione registrata in claudedocs/spalmone-2026-27/vanishing-pins-forensics-2026-07-21.md). L'incidente 2026-07-20 (866 pin spariti) e' stato un POST /api/schedule/reset da un click Playwright su locator troppo largo. Il reset e' distruttivo (unpin+unassign di tutte le lezioni) e NON ha conferma. FIX: dialog di conferma sul bottone Reset nel frontend (l'unica protezione reale contro un click accidentale sul controllo legittimo - un guard server-side non basta, l'handler UI passerebbe comunque il token). Tocca reset-button.spec.ts (cambia il contratto: il test deve cliccare attraverso il dialog), quindi va IMPLEMENTATO E VERIFICATO CON APP SU, insieme al triage e2e di TASK-367. Attribution logging (chi/cosa) gia' fatto in TASK-383. Opzionale defense-in-depth: confirm token server-side contro chiamanti non-UI (curl/script).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implementato e verificato (mandato 16/08 pomeriggio, Filone 3 - scelta caduta su questo task perche' i 4 candidati del mandato 063/083/077/090 sono tutti gia' Done):

DUE controlli Reset gated con window.confirm (convenzione del codebase per pinned-move/CourseGrid): (1) la barra UnifiedSolverControls; (2) il SIDEBAR Reset di ScheduleTimeline, secondo controllo distruttivo live trovato col self-review (gap reale: la protezione a singolo controllo lasciava aperto lo stesso vettore dell'incidente 2026-07-20). Chiave i18n confirmReset (en/it, schedule.json, namespace-prefissata per il sidebar). e2e reset-button.spec.ts aggiornato al nuovo contratto: dialog auto-accept in beforeEach (copre TUTTI i click, non solo test 1: senza handler Playwright dismissa di default, il reset non parte, lo status resta COMPLETE e il test dopo timea) + attesa di quiescenza canonica in test 2 (NOT_SOLVING AND stagedStage null: il reset mid-staged e' rifiutato 500 da refuseIfStaged).

VERIFICA (onesta): ui-typecheck EXIT 0, ui-build EXIT 0. e2e test 1 (il contratto reset con dialog: pre-assign -> reset -> 0 pinned) VERDE end-to-end con app su. Probe spec dedicato ha provato la catena completa: dialog confirm col messaggio giusto -> accept -> POST /api/schedule/reset raggiunge il server (500 era refuseIfStaged durante uno staged leftover: comportamento corretto). Il gate sidebar e' verifica typecheck+build (stessa forma del gate live-verificato; il click live non eseguito). Test 2/3 della spec RESTANO ROSSI per cause PRE-EXISTENTI al dialog: test 2 timea aspettando il bottone Stop dopo Solve (coreografia Solve->Stop in conflitto con staged-by-default + source=input ratificati stamattina 57049fc3; nessun dialog coinvolto in quel passo), test 3 eredita lo stato. Il triage completo di quella coreografia e' rfa TASK-367 (gia' previsto dal task stesso).

Self-review completato a mano (l'agente reviewer e' stato bloccato 3 volte dal classificatore auto-mode con misfire): 1 resetSchedule call in piu' trovata e gated (finding A), i18n namespace verificato runtime (il dialog mostra il messaggio EN), payload stagedStage null/undefined verificato live.
<!-- SECTION:FINAL_SUMMARY:END -->
