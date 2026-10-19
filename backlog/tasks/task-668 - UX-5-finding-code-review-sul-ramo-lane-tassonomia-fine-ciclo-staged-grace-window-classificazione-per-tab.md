---
id: TASK-668
title: >-
  UX: 5 finding code-review sul ramo lane (tassonomia fine-ciclo staged, grace
  window, classificazione per-tab)
status: Done
assignee: []
created_date: '2026-09-24 22:50'
labels:
  - claude-planned
  - ux
  - bug
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cinque finding confermati dal /code-review high del ramo feature/ux-planner-lane (25/09, notte), tutti con scenario di fallimento concreto e nessuno bloccante per classe: (1) UnifiedSolverControls ~624: handleStop arma stopRequestedRef PRIMA della chiamata e non lo scarica se l'API fallisce: uno Stop fallito etichetta 'Ciclo interrotto' una consegna naturale successiva e sopprime il toast di completamento. (2) ~458: lo score del toast di completamento e' catturato alla transizione e riusato verbatim ai re-arm del debounce: lo score consegnato che arriva un tick dopo non lo aggiorna mai (toast col punteggio pre-ciclo). (3) ~462: un ciclo morto senza Stop (crash/OOM in costruzione) passa le gate stagedStage==null && refining==false e prende il toast di successo su griglia rotta: classificato strutturalmente in TASK-667 ma il falso-successo spedisce ora. (4) ~424: la classificazione e' per-tab: Stop da un altro tab/client appare qui come completamento naturale (e viceversa). (5) ScheduleTimeline ~431: stagedEndGraceOnly esclude awaitingScoreRestore, quindi nel caso post-Stop con score null la grace window 90s viene azzerata a ogni run dell'effetto e non scade mai: inerte proprio nel caso per cui e' nata (benigno oggi solo perche' l'altra deadline tiene il poll aperto).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Stop fallito (errore API) NON arma piu' la classificazione 'interrotto': il flag si arma solo su stop confermato o si resetta sull'errore
- [x] #2 Il toast di completamento porta lo score consegnato, non quello pre-ciclo catturato alla transizione
- [x] #3 Un ciclo morto senza Stop (crash backend) NON produce toast di successo (finche' TASK-667 non porta stagedOutcome, la forma minima e' astenersi dal toast se nessun attempt ha mai prodotto refining o stage noto)
- [x] #4 La classificazione fine-ciclo sopravvive a un secondo tab/client (flag di stop condiviso via localStorage+timestamp o derivata dal server, non solo da ref locale)
- [x] #5 La grace window 90s conta davvero anche quando il poll e' anche in awaitingScoreRestore (post-Stop con score null)
<!-- AC:END -->

2026-09-25 notte lane UX laptop (commit 16322459 + report 38708ef8): tutti e 5 i finding curati su feature/ux-planner-lane. Gates del mandato: ui-typecheck EXIT=0 + reasoning scritto per (3) e (5) nella sezione TASK-668 del REPORT ux-review-2409. Nessun nuovo ciclo solve (casi-limite, comportamenti in shape gia' certificati live sui cicli 4-5).
