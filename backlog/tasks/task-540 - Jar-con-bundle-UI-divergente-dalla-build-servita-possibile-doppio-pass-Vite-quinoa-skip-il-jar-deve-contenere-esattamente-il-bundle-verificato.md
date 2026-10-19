---
id: TASK-540
title: >-
  Jar con bundle UI divergente dalla build servita (possibile doppio pass Vite /
  quinoa skip): il jar deve contenere esattamente il bundle verificato
status: To Do
assignee: []
created_date: '2026-08-21 13:54'
labels:
  - claude-planned
  - build
  - packaging
  - verify-what-ships
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Osservazione zai 21/08 pomeriggio: il jar planner-1.0-SNAPSHOT.jar contiene index-WrPfL2Qs.js mentre il processo serve index-hxKoRxRo.js. L'hash Vite e' content-based: hash diverso = contenuto DIVERSO, quindi il jar ha dentro un bundle non verificato (probabile build stantia sopravvissuta in target/quinoa/build con quinoa che salta il rebuild). Rischio: ogni deploy java -jar servirebbe una UI non verificata (classe verify-what-ships). Indagare il pipeline (doppio pass vite? skip-if-unchanged di quinoa? clean mancato) e fixare.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Riprodotto il meccanismo: quale pass scrive cosa nel jar vs build dir (mvn package con quinoa, target/quinoa/build, eventuale skip-if-unchanged)
- [ ] #2 Fix: il jar non puo' contenere un bundle diverso da quello del build verificato (clean del build dir pre-package, o quinoa always-build, o verifica post-package jar-content == build-dir)
- [ ] #3 Verifica meccanica aggiunta: script/check che confronti l'hash del bundle NEL JAR con quello servito/della build dir, fallendo loud se divergono (regola verify-what-ships)
- [ ] #4 DoD standard
<!-- AC:END -->
