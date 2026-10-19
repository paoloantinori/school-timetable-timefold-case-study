---
id: TASK-675
title: >-
  RICERCA: mosse coarse-grained Neighborhoods (2.7) per il decider MT-LS e i
  selettori pillar, valutare la migrazione
status: To Do
assignee: []
created_date: '2026-10-01'
labels:
  - claude-planned
  - research
  - timefold-2.7
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'analisi features dell'upgrade a Timefold 2.7.0 (01/10, commit bdbfc0ff).
Upstream ha investito nella API Neighborhoods: paradigma iterator-based in 2.6
(#2582, selezione "much less biased") e mosse coarse-grained in 2.7.0 finale
(#2610: pillar change/swap e sublist moves come builtin, verso la parita' con i
move selector). Noi gia' usiamo pillarChange/pillarSwap come SELETTORI classici
in Phase 2 (TASK-381) e la spike TASK-610 gioco' proprio con la PR 2610 quando
era main.

Domanda di ricerca: conviene migrare (in tutto o in parte) la generazione mosse
al paradigma Neighborhoods? Punti da valutare:
1. Il decider MT-LS custom (reflection-pinned, famiglia TASK-543/639) itera
   move iterator di Timefold: un move provider Neighborhoods e' consumabile
   dallo stesso canale o impone la loro pipeline?
2. Fairness: il bias dei selettori classici ci costa qualcosa di misurabile
   sui nostri bacini (A/B: pillar classico vs Neighborhoods pillar)?
3. Deprecation risk: i selettori classici NON sono deprecati (2.7.0 li
   supporta pienamente): la migrazione e' opportunita', non urgenza.

Vincolo: NESUNA adozione senza banco A/B appaiato (metodo consolidato) e senza
verifica che il wiring reflection sopravviva. Se la risposta e' "non conviene
ora", chiudere il task con la motivazione misurata.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Banco A/B: pillar classico vs Neighborhoods pillar su almeno un bacino checkpoint (wall + score curve)
- [ ] #2 Verifica di consumabilita' del move provider Neighborhoods da parte del decider MT-LS (o motivazione del no)
- [ ] #3 Verdetto scritto: migrare / non migrare / rivalutare alla prossima major
<!-- AC:END -->
