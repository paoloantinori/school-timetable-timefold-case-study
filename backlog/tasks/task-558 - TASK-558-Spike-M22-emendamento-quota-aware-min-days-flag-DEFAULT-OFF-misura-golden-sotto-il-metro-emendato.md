---
id: TASK-558
title: >-
  TASK-558 - Spike M22 emendamento quota-aware min-days (flag DEFAULT OFF) +
  misura golden sotto il metro emendato
status: Done
assignee: []
created_date: '2026-08-26 05:27'
labels:
  - claude-planned
  - spike
  - task556-family
dependencies: []
priority: high
type: spike
ordinal: 316000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Mandato maintainer 26/08 item 2. Implementare DIETRO FLAG DEFAULT OFF la forma quota-aware min-days per M22 (minDays = ceil(ore / media-quota-dichiarata) invece di ceil(ore/maxHoursPerDay)) e misurare il GOLDEN sotto il metro emendato: predizione PRIMA, conteggio giorni-eccesso golden emendato vs i 262 dell'attuale, confronto col -12hard/-22.026medium attuale. MISURA, NON ADOZIONE (il flag resta OFF; decide maintainer). Contesto: TASK-556 analisi 25/08 ha dimostrato la FINZIONE di M22 (il golden unpinned-equivalent pagherebbe 262 giorni-eccesso vs nostri 240 perche' min-days usa maxHoursPerDay contro quote dichiarate sottili); emendamento proposto nel VERDICT-analisi-giorni-2026-08-25.md, non applicato.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
