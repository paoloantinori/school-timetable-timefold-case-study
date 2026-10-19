---
id: TASK-543
title: 'MT-LS: test determinismo REPRODUCIBLE x2 e refactor single-writer condizionato'
status: Done
assignee: []
created_date: '2026-08-23 19:49'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla research del 23/08 (claudedocs/research_mt-ls-variance-race_2026-08-23.md): la forbice -6..-715 della LS multi-thread sulla stessa costruzione e' ambigua tra varianza intrinseca (NON_REPRODUCIBLE con semi) e difetto del nostro decider community (valutazioni su stato condiviso in mutazione, race SimpleFieldTracker 22/08). Il design upstream Timefold/OptaPlanner e' riproducibile col modello per-copia/sync-per-step/rebasing. FASE 1 (decisiva, prima di ogni refactor): due run REPRODUCIBLE con identica configurazione. Se identici, la macchina e' deterministica-capace e la forbice e' intrinseca: single-writer NON ridurrebbe la varianza. Se divergono, non-determinismo patologico: refactor giustificato con evidenza. FASE 2 (condizionata): refactor single-writer del decider (solver/multithreaded/) verso il modello upstream. Fonte ricerca: docs Timefold Enterprise MT + blog De Smet 2018. Worktree day-compact-542 o branch dedicato.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Run REPRODUCIBLE x2 (stessa config, stesso moveThreadCount) documentati con score completi e verdetto IDENTICI/DIVERGENTI
- [ ] #2 Se DIVERGENTI: refactor single-writer del decider MT-LS (modello upstream: copie per thread, sync per step, rebasing) con TDD e suite verde
- [ ] #3 Se IDENTICI: verdetto scritto che la varianza e' intrinseca e il single-writer resta solo come fix delle race (priorita' abbassata, decisione a maintainer)
- [ ] #4 A/B prestazione (wall) del nuovo decider vs attuale se il refactor viene fatto
- [ ] #5 DoD completa: /simplify + /code-review high + test.sh EXIT 0 da orchestratore
<!-- AC:END -->


GUARDIE BYTEMAN AL LAND (27/08 01:0x, rilancio 3): le due regole differite eseguite sul ramo campagna mergiato (6ee4fa87): BytemanRaceInjectionTest 2/2 VERDE + SimpleFieldTrackerRaceTest 2/2 VERDE sotto -Pbyteman (agente attivo: il verde e' la prova dell'aggancio, i test sono disabilitati senza agente). DoD della ratifica completa anche post-merge. Verdetto differito CHIUSO.

## Final Summary

CHIUSURA FINALE (27/08): guardie Byteman differite eseguite al land del ramo campagna (6ee4fa87): BytemanRaceInjectionTest 2/2 + SimpleFieldTrackerRaceTest 2/2 VERDI sotto -Pbyteman. DoD della ratifica single-writer completa anche post-merge. Catena: divergenti FASE 1, single-writer DEFAULT ON 24/08, REPRODUCIBLE x2 (dimezzata), suite 1157 EXIT 0.
