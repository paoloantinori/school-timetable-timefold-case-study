---
id: TASK-547
title: >-
  Batch: analisi progetti Timefold community (repo open dalle discussions) con
  lettura commit messages
status: Done
assignee: []
created_date: '2026-08-24 13:42'
updated_date: '2026-08-24 13:50'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da richiesta di maintainer 24/08: molte discussioni Timefold contengono progetti di esempio open condivisi dai contributor; passarli tutti con un batch di subagent per capire se contengono punti interessanti per noi. Include la lettura di TUTTI i commit messages di ogni progetto (i fix e i problemi risolti dagli altri possono indirizzarci sulle parti piu' utili e su trappole gia' incontrate). Primo passo: censimento (in corso, un agent dedicato); secondo: un agent per repo con clone locale in /tmp e analisi; terzo: sintesi. Contesto nostro: Quarkus+Timefold 2.3, timetabling scolastico 12.5k entita', ciclo staged multi-fase con custom phases, single-writer decider MT-LS, checkpoint per fasi.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Censimento completo dei progetti open linkati nelle discussions Timefold (file elenco verificato)
- [ ] #2 Un subagent per progetto: clone, analisi struttura (modello, constraint provider, fasi custom, configurazione solver) + LETTURA COMPLETA dei commit messages (git log) per fixes/problemi risolti
- [ ] #3 Report per progetto: punti interessanti per il planner (pattern riusabili, problemi risolti, config notevoli), con riferimento a commit/thread specifici
- [ ] #4 Sintesi finale: tabella incrociata di cosa adottare/valutare/scartare per il nostro dominio (timetabling scolastico, staged cycle, custom phases)
- [ ] #5 Nessuna adozione: solo analisi e raccomandazioni a maintainer
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
COMPLETATA (24/08): censimento 2 progetti verified su ~232 discussioni; 2 agent di analisi (clone + git log completo); sintesi in claudedocs/spalmone-2026-27/task547-community-projects-analysis.md. Bottino: pattern context-shift al confine (adottare-idea), verifica minimi monotoni su chiavi assenti per M20/H33 (valutare, meccanica), conferma fragilita' custom-phase API dal port TimefoldSharp (dato per upstream #2615). Nessuna adozione.
<!-- SECTION:NOTES:END -->
