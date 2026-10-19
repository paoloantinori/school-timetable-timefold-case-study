---
id: TASK-541
title: >-
  planner.sh run droppa il DB a ogni restart (-D update non raggiunge il fork):
  gli stati locali non sopravvivono
status: Done
assignee: []
created_date: '2026-08-21 14:21'
labels:
  - claude-planned
  - bug
  - local-dev
  - data-safety
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding critico TASK-525 round-4 (21/08 sera): l'app locale avviata con planner.sh run (mvn quarkus:run fork) DROPPA il DB a ogni restart nonostante il -D update, perche' il -D non raggiunge il processo forkato (famiglia trappola MAVEN_OPTS gia' documentata): il main application.properties ha drop-and-create come default. Conseguenza: import e assegnazioni locali spariscono a ogni riavvio (l'ispezione -118 di maintainer era sparita per questo). Il path prod (java -jar col -D in cmdline) non e' toccato.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Root cause confermata: il -D di planner.sh run non raggiunge l'app forkata da mvn quarkus:run (stessa famiglia della trappola MAVEN_OPTS), quindi il default drop-and-create del main application.properties vince e ogni avvio locale droppa il DB e re-seeda (verificato empiricamente 2x dall'agente round-4)
- [ ] #2 Fix scelto e applicato (candidati: default update nel main application.properties con drop-and-create solo nel test properties; oppure planner.sh che passa la property via meccanismo che raggiunge il fork)
- [ ] #3 Verifica: import di un export, restart dell'app con planner.sh run, i dati sopravvivono
- [ ] #4 Aggiornare CLAUDE.md se la sezione How to Run cambia comportamento
- [ ] #5 DoD standard
<!-- AC:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
