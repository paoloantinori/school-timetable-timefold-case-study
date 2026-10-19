---
id: TASK-554
title: >-
  Sweep pesi medium nel regime 0hard + RIMISURA del golden sotto il metro
  attuale (il -49 e' obsoleto)
status: Done
assignee: []
created_date: '2026-08-25 17:55'
updated_date: '2026-08-25 20:29'
labels:
  - claude-planned
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Da approvazione maintainer 25/08 sera (post-0hard): ora che lo 0hard e raggiungibile e la fase medium-pura (TASK-553) libera i medium dalla competizione con gli hard, ha senso RISCALDARE i pesi medium. I pesi attuali sono calibrati per il regime lessicografico (famiglia M20/M15/M22 1000-1200); nel regime 0hard-incollato le proporzioni relative vanno ricalibrate. PUNTO 0 CRITICO (sfiducia di maintainer, fondata): il -49 come medium del golden viene dall'era bendable con un set di vincoli DIVERSO (pre-H31/H32/H33, pre-M32-overlay, pre-M20-pool-cap) e non e' mai stato ricalcolato: il golden sotto il metro ATTUALE va rimisurato (analyzeScore sul golden import, come GoldenSolutionRegressionTest). Il dato che alimenta il sospetto: un run di luglio (m12-run2) faceva -1.319.054 medium, suggerendo che il golden sotto il metro attuale paghi molto piu' di -49; se cosi', il gap vero nostro-vs-golden e molto piu' piccolo del raccontato e il 'costo del metro' va ridimensionato.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 MISURA PRIMA (bloccante): golden analizzato col set medium COMPLETO attuale (post-merge, post-cure) con breakdown per famiglia; il -49 storico viene dall'era bendable con set diverso e NON e' piu' valido
- [ ] #2 Sweep checkpoint-paired (1200s/braccio): dallo stato 0hard col braccio medium-pura ON, varia un gruppo di pesi per braccio partendo dalle famiglie piu' divergenti nel dataviz (M32, M18, M34/M35, S13)
- [ ] #3 Predizione PRIMA per ogni braccio: direzione e magnitudine attesa
- [ ] #4 Verdetto: quali pesi riscaldare migliora il medium senza regressioni; raccomandazione a maintainer
- [ ] #5 Cautela dichiarata: il riscaldamento sposta priorita RELATIVE, non crea capacita'; tetto = parte del gap golden che e' trade-off tra medium, non struttura input
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CHIUSO (5a57a47). Verdetto rimisura: golden -12hard/-22k (non -49, non -1,2/1,4M: predizione SMENTITA). Gap vero ~0,9M concentrato al ~90% su GIORNI (M20.2 51-vs-3, M22 14-vs-0, M33 -27.700, M34/M35 -17.900 vs zero golden): filone piano/capacita', sweep-pesi NON giustificato, medium-pura marginale. Lezione di processo registrata: rimisurare il golden a ogni cambio di metro (trappola Q19-family: il -49 dell era bendable circolava pur essendo misurato su un set obsoleto).

CONSOLIDAMENTO 26/08 (assorbito dal duplicato task-555 del checkout principale, stessa misura registrata due volte con id diversi): METODO ripetibile ~2min = profilo dedicato (dci-554gold) + import-golden --force + UPDATE lesson SET room_id=NULL (forma canonica del regression test: il golden non ha aule, H2/H29 vacue sui pinned) + analysis senza solve. Decomposizione del confronto: il golden e' 100% PINNATO, quindi le famiglie pinned-exempt leggono zero per costruzione e M32 e' vacua nell'import (0 corsi templated). Dettaglio medium: M20.2 -21.500 (3 celle), il resto minore. Artefatti: claudedocs/spalmone-2026-27/task550-roomtail/golden-remisura-metro-attuale.json + registro iterations-542.md voce 25/08 22:20. Il file task-555 nel checkout principale e' ora REDONDANTE (questo task e' il canonico remoto): da eliminare manualmente li' insieme agli altri untracked non rinominabili dal worktree (vedi quadro-risveglio 26/08).
<!-- SECTION:NOTES:END -->
