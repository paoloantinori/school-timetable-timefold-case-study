---
id: TASK-545
title: >-
  EVENTUALE: pattern alternativi di multithreading per la fase medium (multi-bet
  raccomandato, snapshot/actor deferito, partitioned scartato)
status: To Do
assignee: []
created_date: '2026-08-24 06:24'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla reflect del 24/08 su "single-thread fase medium: buona decisione?" (degraded reflect exhaustive). L'opzione 1 (per-copia/sync/rebase single-writer) e' GIA' TASK-543 fase 2, approvata: qui solo puntatore. Questo task copre le alternative EVENTUALI: (2) multi-bet solving dentro la fase medium, il pattern upstream: 2-4 rifiniture medium indipendenti in parallelo, ognuna single-thread sul proprio stato, pick-best; zero stato condiviso = immune per costruzione alla famiglia di race MT-LS (tre difetti catalogati); core occupati; la fase e' una rifinitura terminabile quindi scartarne 3 su 4 costa poco; coerente col multi-start a livello di ciclo. RACCOMANDATA se il wall o l'esplorazione single-thread della fase medium deludono. (3) partitioned search: SCARTATO (constraint cross-boundary: conflitti docente tra gruppi; upstream dichiara che il partizionamento esclude soluzioni ottime nei modelli con constraint che attraversano i confini). (4) valutazione snapshot/actor (copie per batch di mosse): correttezza massima ma costo implementativo maggiore del single-writer senza beneficio aggiuntivo alla nostra scala: solo se il multi-bet non bastasse. Fonte: research MT-LS del 23/08 (claudedocs/research_mt-ls-variance-race_2026-08-23.md) + blog De Smet 2018.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SOLO EVENTUALE: attivare solo se la misura single-thread della fase medium (in corso) o la FASE 2 di TASK-543 deludono su wall o qualita'
- [ ] #2 Opzione multi-bet medium: 2-4 rifiniture indipendenti in parallelo (ognuna single-thread sul proprio stato, pick-best), zero stato condiviso, prototipo dietro flag default-OFF con TDD
- [ ] #3 Opzione snapshot/actor (valutazione su copie per batch di mosse): solo valutazione documentata del costo/beneficio vs single-writer, implementazione solo se il multi-bet non bastasse
- [ ] #4 Partitioned search: registrato come SCARTATO con motivazione (constraint cross-boundary docente-gruppi: il partizionamento esclude soluzioni ottime, upstream lo dichiara per i modelli con constraint che attraversano i confini)
- [ ] #5 Nessun lavoro su questo task prima che TASK-543 fase 2 e la misura medium chiudano i rispettivi verdetti
<!-- AC:END -->
