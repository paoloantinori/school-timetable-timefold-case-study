---
id: TASK-583
title: >-
  Guardia value-range sulla quinta via OOF relieveOverloads (blocker mosse senza
  check fresco)
status: Done
assignee: []
created_date: '2026-09-05 06:02'
labels:
  - claude-planned
  - solver
  - bug
  - vrp
milestone: m-13
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Igiene di stabilita': elimina i 2-3 assertValueRange NON fatali per ciclo (famiglia "quinta via OneOffFinisher").

DIAGNOSI (4/09 mattina, vedi session-checkpoint): le eccezioni del tentativo 4 del ciclo v11 vengono SUBITO DOPO il summary OOF "overload relief 12". La QUINTA VIA e' `relieveOverloads` (12 mosse riuscite: la vittima Tecnologia Meccanica 10121 al 17/09 10:00 e' in zona donatori settembre). Il relief passa per `tryMove` (range fresco) MA la vittima e' probabilmente mossa come BLOCKER collateral: verificare la firma `tryMove(l, idx, moveTimeslot, moveRoom, null)`: il quinto argomento null = il blocker da spostare insieme. QUALE range consulta per il blocker?

CONTESTO: le 4 vie precedenti sono gia' coperte dalla migrazione TASK-578 (`Lesson.isSlotLegalFresh` O(1), commit 6ad3767e) + guardia su chainFreeTheTagRoom (quarta via, commit 6ce59070). Questa e' la quinta e ultima via nota di piazzamento fuori value-range.

DA FARE: (1) leggere il path del blocker in tryMove da relieveOverloads; (2) aggiungere guardia per-cell `isSlotLegalFresh` sul blocker, identica alle guardie gia' migrate; (3) test di regressione (famiglia LessonSlotLegalityTest); (4) verifica che il ciclo non produca piu' assertValueRange da questa famiglia.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lettura di OneOffFinisher.relieveOverloads/tryMove: documentato quale value range consulta per il blocker (quinto argomento), con riga citata
- [ ] #2 Guardia per-cell isSlotLegalFresh aggiunta sul path del blocker in relieveOverloads, stesso idioma delle guardie TASK-578
- [ ] #3 Test di regressione che riproduce la violazione (blocker mosso fuori range) e verde dopo la guardia
- [ ] #4 Ciclo completo sul laptop: zero assertValueRange dalla famiglia relieveOverloads
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
