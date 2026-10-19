---
id: TASK-520
title: >-
  Upgrade Timefold Solver 2.3.0 -> 2.5.0 (DIETRO GATE: verifica simboli
  reflection MT-LS prima)
status: Done
assignee: []
created_date: '2026-08-18 09:09'
labels:
  - claude-planned
  - maintenance
  - dependencies
  - multithreaded
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla ricerca dipendenze 18/08: Timefold Solver 2.5.0 porta un fix di race condition nel SolverWorker (potenzialmente rilevante per il nostro MT-LS custom) e constraint-streams piu' efficienti. MA il wiring MT-LS (TASK-480) e' pinnato via reflection agli internals della 2.3.0 (documentato fragile nel javadoc): l'upgrade richiede prima il gate di sopravvivenza dei simboli. Non urgente, dietro al round 9/10 della chain.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GATE preliminare: verifica che tutti i simboli internals di Timefold 2.3.0 usati dal wiring reflection del MT-LS (TASK-480, documentati come fragile/pinned nel javadoc) esistano in 2.5.0: lista dei simboli + esito per ciascuno
- [ ] #2 Se il gate passa: upgrade + suite + A/B wall di un run good-basin per confermare il -25% MT-LS regge
- [ ] #3 Se il gate fallisce: documento dei simboli mancanti e stima del rewiring, NESSUN upgrade
- [ ] #4 Motivazione: fix race SolverWorker #2529 (rilevante per MT-LS) e constraint-streams piu' efficienti (#2553, #2544)
<!-- AC:END -->

## ESECUZIONE 21/09 sera (maintainer: "voglio la 2.6! fai l'upgrade prima"): target corretto a 2.6.0, bump FATTO

RICERCA (verificata su diff dei tag v2.3.0/v2.6.0 + release notes + maven-metadata, agent 21/09): ultima stabile = **2.6.0** (2026-09-01; 2.7.0-rc-1 e' RC del 17/09, non bersaglio). RISCHIO CERTIFICATO BASSO: (1) l'unico simbolo reflection MT-LS `DefaultLocalSearchPhase.decider` sopravvive a 2.6.0 e LocalSearchDecider e' byte-identical; (2) tutte le API che usiamo (LocalSearchPhaseConfig singolo moveSelectorConfig, UnionMoveSelectorConfig, EnvironmentMode, ConstraintMatch, CustomPhase/PhaseCommand, MoveListFactory, pillar XML configs) byte-identical fra i tag; (3) UNICO cambio API reale: `triggerVariableListeners` -> `updateShadowVariables` (2.5.0, deprecato-ma-presente): NESSUN uso nel nostro codice (grep). COMPORTAMENTALI (il motivo vero dell'upgrade): PR 2596 ripristina lo score dopo l'undo (tocca i doMove/undo dei nostri custom phase: repair, banchi, terna) e fix bavet join/ifExists staleness 2.5/2.6 (correttezza score: se eravamo morsi, i pin si sposteranno e la suite lo dira'). OPPORTUNITA' (domanda maintainer "cosa sfruttare"): il paradigma **Neighborhoods iterativo (PR 2582) e' in 2.6.0** ed e' il candidato per la forma B del TASK-638 (move provider custom invece del bridge MoveListFactory); PR 2610 pillar/mass/2opt e' in **2.7.0-rc-1** preview Neighborhoods, sub-pillar a SAMPLER senza custom-key (la nostra segnalazione 2254 non e' approdata nella forma custom-key). ESECUZIONE: pom 2.3.0->2.6.0, main compila pulito, test-compile verde (unico fix: OffCapture638BenchTest all'API vera UnionMoveSelectorConfig), solverConfig.xml valida la XSD 2.6.0, suite completa in volo. NOTA A/B: il gate 636 in volo gira su jar 2.3.0 (verdetto internamente valido: stesso engine entrambe le braccia); il laptop prende la 2.6.0 al primo pull post-verdetto.

2026-10-03 chiuso come FATTO per soprafusione: il salto e' avvenuto a gradini (2.3.0 -> 2.6.0 il 21/09 stesso task, commit 404e873f; -> 2.7.0-rc-1 il 22/09 a6e2bebd; -> 2.7.0 finale il 01/10 bdbfc0ff). I simboli reflection MT-LS verificati ad ogni gradino: la linea pin e' '2.3.0 through 2.7.0'.
