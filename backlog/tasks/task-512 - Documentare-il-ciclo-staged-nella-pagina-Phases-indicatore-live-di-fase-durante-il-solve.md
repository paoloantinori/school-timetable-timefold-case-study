---
id: TASK-512
title: >-
  Documentare il ciclo staged nella pagina Phases + indicatore live di fase
  durante il solve
status: Done
assignee: []
created_date: '2026-08-17 06:51'
labels:
  - claude-planned
  - ui
  - docs
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla valutazione UX 17/08: la pagina Phases descrive la pipeline a 5 fasi del singolo tentativo ma NON il ciclo staged che il pulsante Solve lancia davvero (dal 2026-08-15 la UI chiama solve-staged con ensurePreAssignmentForStagedCycle, e dal TASK-483 tutto e' wrappato nel multi-start). Chi guarda Progression durante un solve vede fasi non documentate. Il dato solverPhases.ts ha gia' la nota di orchestrazione ma solo come commento, non renderizzato. Questa e' l'opportunita' a piu' alto impatto della valutazione: l'utente non ha modello mentale di cosa parte quando preme Solve.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Sezione 'ciclo staged' nella pagina Phases (it e en): il pulsante Solve lancia PRE-ASSIGN garantito -> A-CONSTRUCTION -> PROMOTION -> B-TIMESLOT (con demozioni) -> RELEASE -> repair pass -> B-ROOM, con wrapper multi-start (tentativi, pick-best, stop al primo 0-hard)
- [ ] #2 Indicatore live durante il solve: lo stagedStage corrente e il 'Tentativo X di Y' visibili in modo evidente (esiste gia' l'indicatore tentativi: verificarne la visibilita' e prominenza)
- [ ] #3 Nota esplicita del trabocchetto solverStatus=NOT_SOLVING durante il ciclo staged (documento per l'utente, non solo per noi)
- [ ] #4 Sincronizzato con solverPhases.ts secondo la regola esistente (.claude/rules/solver-config-sync.md)
- [ ] #5 Typecheck e build UI verdi
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
