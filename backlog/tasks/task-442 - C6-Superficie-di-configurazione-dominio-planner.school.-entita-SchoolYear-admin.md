---
id: TASK-442
title: >-
  C6 Superficie di configurazione dominio (planner.school.* / entita' SchoolYear
  + admin)
status: To Do
assignee: []
created_date: '2026-07-28 08:23'
labels:
  - claude-planned
milestone: m-13
dependencies: []
ordinal: 265000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C6: application.properties oggi ha ZERO configurazione di dominio e i -D sono inaffidabili via mvn (trappola MAVEN_OPTS). Blocco planner.school.* e/o entita' SchoolYear con admin page. Prerequisito per C1-C3.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
