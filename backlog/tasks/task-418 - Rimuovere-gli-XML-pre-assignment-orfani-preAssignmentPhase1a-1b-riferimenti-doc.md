---
id: TASK-418
title: >-
  Rimuovere gli XML pre-assignment orfani (preAssignmentPhase1a/1b) +
  riferimenti doc
status: Done
assignee: []
created_date: '2026-07-25 05:55'
updated_date: '2026-07-25 07:31'
labels:
  - cleanup
dependencies: []
priority: low
ordinal: 242000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Emerso dal giro di pulizia 2026-07-25: preAssignmentPhase1a-labblocks.xml e preAssignmentPhase1b-lessons.xml in src/main/resources non sono referenziati da nessun codice Java (grep vuoto su src/main e src/test) - scaffolding storico superato dal CSP HybridPhase1aService. Rimuoverli insieme ai riferimenti in PROJECT_INDEX.md/claudedocs (dove citati come config attive). Attenzione: NON toccare la doc storica in claudedocs/research.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
IMPLEMENTATA 2026-07-25 (treno): XML rimossi, PROJECT_INDEX/validate-solver-xml.sh puliti, banner stale su solver-configurations.md, guidelines aggiornate al passato. Cross-ref registrato su TASK-322.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
