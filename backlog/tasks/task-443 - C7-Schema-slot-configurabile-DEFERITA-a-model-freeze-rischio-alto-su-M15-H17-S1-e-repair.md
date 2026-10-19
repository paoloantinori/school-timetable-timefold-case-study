---
id: TASK-443
title: >-
  C7 Schema slot configurabile (DEFERITA a model-freeze: rischio alto su
  M15/H17/S1 e repair)
status: To Do
assignee: []
created_date: '2026-07-28 08:23'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: low
ordinal: 266000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C7: SLOT_START_TIMES, pranzo 14:00-14:30, durata 1h x3 siti, packing base-10, FALLBACK_TIME_PERIODS frontend, dayConstants. NON in campagna: tocca le semantiche di adiacenza ovunque. I 2 prerequisiti di correttezza sono nel task HOTFIX.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
