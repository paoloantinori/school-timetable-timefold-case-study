---
id: TASK-515
title: >-
  Rimuovere le etichette morte LabBlock da LessonTooltip (ID vincoli dell'era
  Shadow/Block)
status: Done
assignee: []
created_date: '2026-08-17 06:52'
labels:
  - claude-planned
  - ui
  - cleanup
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla valutazione UX 17/08 (audit fonte): LessonTooltip.tsx porta ancora le etichette fallback per gli ID dei vincoli dell'era Shadow/Block (H1.2, H1.3, H2.2, H2.3 ecc.) rimossi da TASK-228/267: codice morto innocuo ma che confonde chi legge e suggerisce un'architettura che non esiste piu'. Stessa famiglia dell'etichetta 'Strategy: Cartesian + Block Entity' (gestita nel TASK-514). Pulizia da fare in worktree insieme agli altri task UI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Le etichette morte LabBlock rimosse da LessonTooltip.tsx (H1.2/H1.3 'Lesson-LabBlock', H2.2/H2.3, e ogni altro ID dell'era Shadow/Block: quegli ID non vengono piu' prodotti da TASK-228/267)
- [ ] #2 Il commento TASK-357 che cita i 'LabBlock-virtual lessons' aggiornato o rimosso
- [ ] #3 grep di conferma: zero riferimenti funzionali a LabBlock residui nella webui (i riferimenti storici nei commenti di documentazione sono accettabili se corretti)
- [ ] #4 Typecheck e build UI verdi
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
