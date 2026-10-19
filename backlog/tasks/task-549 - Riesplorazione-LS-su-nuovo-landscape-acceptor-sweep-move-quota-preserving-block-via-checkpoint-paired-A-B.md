---
id: TASK-549
title: >-
  Riesplorazione LS su nuovo landscape: acceptor sweep + move
  quota-preserving/block via checkpoint-paired A/B
status: To Do
assignee: []
created_date: '2026-08-24 18:24'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da discussione con maintainer 24/08 sera: riesplorare metauristiche e move-set ora che (1) il landscape e' cambiato (H31/H32/H33 aggiunte DOPO il verdetto di esaurimento del 05/08), (2) lo strumento di misura e' finalmente pulito (single-writer: le A/B post-14/08 passavano dal decider malato, validita ignota), (3) esistono famiglie di mosse coerenti col nuovo landscape mai provate (quota-preserving swaps, block-shift), (4) l'infrastruttura checkpoint rende ogni braccio ~20min invece di 1.5h, con pairing naturale sulla stessa costruzione. Nota storica: il verdetto 'metaheuristics exhausted' del 05/08 fu misurato su landscape senza i tre hard nuovi e su decider single-thread (pulito ma vecchio terrain).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Protocollo checkpoint-paired: ogni braccio riparte dallo STESSO checkpoint POST_CONSTRUCTION, varia solo config B-TIMESLOT, confronto sul gate di fine fase; ~20min/braccio
- [ ] #2 Famiglia (a) acceptor: repplica dello sweep tabu/late-acceptance sul NUOVO landscape (H31/H32/H33): 3-4 bracci
- [ ] #3 Famiglia (b) move quota-preserving: swap di ore uguali tra giorni dello stesso gruppo (H32 invariante per costruzione), prototipo dietro flag, 2-3 bracci
- [ ] #4 Famiglia (c) block moves: block-shift che preservano la compattezza per costruzione, 2 bracci
- [ ] #5 Predizione scritta PRIMA di ogni braccio; verdetto CONFERMATA/SMENTITA registrato; REPRODUCIBLE-sanity su almeno un braccio (strumento pulito)
- [ ] #6 Raccomandazione finale a maintainer: quali acceptor/move meritano un ciclo completo; nessuna adozione senza gate
<!-- AC:END -->
